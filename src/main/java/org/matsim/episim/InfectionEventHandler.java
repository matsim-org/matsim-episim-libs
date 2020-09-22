/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.episim;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.typesafe.config.ConfigFactory;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.model.ContactModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.policy.ShutdownPolicy;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.Vehicle;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.matsim.episim.EpisimUtils.readChars;
import static org.matsim.episim.EpisimUtils.writeChars;

/**
 * Main event handler of episim.
 * It consumes the events of a standard MATSim run and puts {@link EpisimPerson}s into {@link EpisimContainer}s during their activity.
 * At the end of activities an {@link ContactModel} is executed and also a {@link ProgressionModel} at the end of the day.
 * See {@link EpisimModule} for which components may be substituted.
 */
public final class InfectionEventHandler implements ActivityEndEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, ActivityStartEventHandler,
		Externalizable {
	// Some notes:

	// * Especially if we repeat the same events file, then we do not have complete mixing.  So it may happen that only some subpopulations gets infected.

	// * However, if with infection proba=1 almost everybody gets infected, then in our current setup (where infected people remain in the iterations),
	// this will also happen with lower probabilities, albeit slower.  This is presumably the case that we want to investigate.

	// * We seem to be getting two different exponential spreading rates.  With infection proba=1, the crossover is (currently) around 15h.

	// TODO

	// * yyyyyy There are now some things that depend on ID conventions.  We should try to replace them.  This presumably would mean to interpret
	//  additional events.  Those would need to be prepared for the "reduced" files.  kai, mar'20


	private static final Logger log = LogManager.getLogger(InfectionEventHandler.class);

	private final Map<Id<Person>, EpisimPerson> personMap = new IdMap<>(Person.class);
	private final Map<Id<Vehicle>, EpisimVehicle> vehicleMap = new IdMap<>(Vehicle.class);
	private final Map<Id<ActivityFacility>, EpisimFacility> pseudoFacilityMap = new IdMap<>(ActivityFacility.class,
			// the number of facility ids is not known beforehand, so we use this as initial estimate
			(int) (Id.getNumberOfIds(Vehicle.class) * 1.3));

	/**
	 * Maps activity type to its parameter.
	 * This can be an identity map because the strings are canonicalized by the {@link ReplayHandler}.
	 */
	private final Map<String, EpisimPerson.Activity> paramsMap = new IdentityHashMap<>();

	/**
	 * Holds the current restrictions in place for all the activities.
	 */
	private final Map<String, Restriction> restrictions;

	/**
	 * Policy that will be enforced at the end of each day.
	 */
	private final ShutdownPolicy policy;

	/**
	 * Progress of the sickness at the end of the day.
	 */
	private final ProgressionModel progressionModel;

	/**
	 * Models the process of persons infecting each other during activities.
	 */
	private final ContactModel contactModel;

	/**
	 * Scenario with population information.
	 */
	private final Scenario scenario;

	private final Config config;
	private final EpisimConfigGroup episimConfig;
	private final TracingConfigGroup tracingConfig;
	private final EpisimReporting reporting;
	private final SplittableRandom rnd;

	/**
	 * Local random, e.g. used for person initialization.
	 */
	private final SplittableRandom localRnd;

	private boolean init = false;
	private int iteration = 0;
	private int initialInfectionsLeft;

	/**
	 * Most recent infection report for all persons.
	 */
	private EpisimReporting.InfectionReport report;

	@Inject
	public InfectionEventHandler(Config config, Scenario scenario, ProgressionModel progressionModel,
								 EpisimReporting reporting, ContactModel contactModel, SplittableRandom rnd) {
		this.config = config;
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		this.scenario = scenario;
		this.policy = episimConfig.createPolicyInstance();
		this.restrictions = episimConfig.createInitialRestrictions();
		this.reporting = reporting;
		this.rnd = rnd;
		this.localRnd = new SplittableRandom(config.global().getRandomSeed() + 65536);
		this.progressionModel = progressionModel;
		this.contactModel = contactModel;
		this.initialInfectionsLeft = episimConfig.getInitialInfections();
	}

	/**
	 * Whether {@code event} should be handled.
	 *
	 * @param actType activity type
	 */
	public static boolean shouldHandleActivityEvent(HasPersonId event, String actType) {
		// ignore drt and stage activities
		return !event.getPersonId().toString().startsWith("drt") && !event.getPersonId().toString().startsWith("rt")
				&& !TripStructureUtils.isStageActivityType(actType);
	}

	/**
	 * Whether a Person event (e.g. {@link PersonEntersVehicleEvent} should be handled.
	 */
	public static boolean shouldHandlePersonEvent(HasPersonId event) {
		// ignore pt drivers and drt
		String id = event.getPersonId().toString();
		return !id.startsWith("pt_pt") && !id.startsWith("pt_tr") && !id.startsWith("drt") && !id.startsWith("rt");
	}

	/**
	 * Returns the last {@link EpisimReporting.InfectionReport}.
	 */
	public EpisimReporting.InfectionReport getReport() {
		return report;
	}

	/**
	 * Returns true if more iterations won't change the results anymore and the simulation is finished.
	 */
	public boolean isFinished() {
		return iteration > 0 && !progressionModel.canProgress(report);
	}

	/**
	 * Initializes all needed data structures before the simulation can start.
	 * This *always* needs to be called before starting.
	 *
	 * @param events All events in the simulation
	 */
	public void init(Map<DayOfWeek, List<Event>> events) {

		iteration = 0;

		Object2IntMap<EpisimContainer<?>> groupSize = new Object2IntOpenHashMap<>();
		Object2IntMap<EpisimContainer<?>> totalUsers = new Object2IntOpenHashMap<>();
		Object2IntMap<EpisimContainer<?>> maxGroupSize = new Object2IntOpenHashMap<>();

		Map<EpisimContainer<?>, Object2IntMap<String>> activityUsage = new HashMap<>();

		Map<List<Event>, DayOfWeek> sameDay = new IdentityHashMap<>(7);

		for (Map.Entry<DayOfWeek, List<Event>> entry : events.entrySet()) {

			DayOfWeek day = entry.getKey();
			List<Event> eventsForDay = entry.getValue();

			if (sameDay.containsKey(eventsForDay)) {
				DayOfWeek same = sameDay.get(eventsForDay);
				log.info("Init Day {} same as {}", day, same);
				this.personMap.values().forEach(p -> p.duplicateDay(day, same));
				continue;
			}

			log.info("Init day {}", day);

			this.personMap.values().forEach(p -> p.setStartOfDay(day, p.getCurrentPositionInTrajectory()));

			for (Event event : eventsForDay) {

				EpisimPerson person = null;
				EpisimFacility facility = null;

				// Add all person and facilities
				if (event instanceof HasPersonId) {
					if (!shouldHandlePersonEvent((HasPersonId) event)) continue;

					person = this.personMap.computeIfAbsent(((HasPersonId) event).getPersonId(), this::createPerson);

					// If a person was added late, previous days are initialized at home
					for (int i = 1; i < day.getValue(); i++) {
						DayOfWeek it = DayOfWeek.of(i);
						if (person.getFirstFacilityId(it) == null) {
							person.setStartOfDay(it, person.getCurrentPositionInTrajectory());
							person.setEndOfDay(it, person.getCurrentPositionInTrajectory());
							person.setFirstFacilityId(createHomeFacility(person).getContainerId(), it);
							EpisimPerson.Activity home = paramsMap.computeIfAbsent("home", this::createActivityType);
							person.addToTrajectory(home);
						}
					}
				}

				if (event instanceof HasFacilityId) {
					Id<ActivityFacility> episimFacilityId = createEpisimFacilityId((HasFacilityId) event);
					facility = this.pseudoFacilityMap.computeIfAbsent(episimFacilityId, EpisimFacility::new);
				}

				if (event instanceof ActivityStartEvent) {

					String actType = ((ActivityStartEvent) event).getActType();
					if (!shouldHandleActivityEvent((HasPersonId) event, actType))
						continue;

					EpisimPerson.Activity act = paramsMap.computeIfAbsent(actType, this::createActivityType);
					totalUsers.mergeInt(facility, 1, Integer::sum);

					handleEvent((ActivityStartEvent) event);

				} else if (event instanceof ActivityEndEvent) {
					String actType = ((ActivityEndEvent) event).getActType();
					if (!shouldHandleActivityEvent((HasPersonId) event, actType))
						continue;

					EpisimPerson.Activity act = paramsMap.computeIfAbsent(actType, this::createActivityType);
					activityUsage.computeIfAbsent(facility, k -> new Object2IntOpenHashMap<>()).mergeInt(actType, 1, Integer::sum);

					// Add person to container if it starts its day with end activity
					if (person.getFirstFacilityId(day) == null) {
						// person may already be there because of previous day
						if (person.getCurrentContainer() != facility) {

							// remove from old
							if (person.getCurrentContainer() != null)
								person.getCurrentContainer().removePerson(person);

							facility.addPerson(person, 0);
						}

						person.setFirstFacilityId(facility.getContainerId(), day);
					}

					handleEvent((ActivityEndEvent) event);
				}

				if (event instanceof PersonEntersVehicleEvent) {
					if (!shouldHandlePersonEvent((HasPersonId) event)) continue;

					EpisimVehicle vehicle = this.vehicleMap.computeIfAbsent(((PersonEntersVehicleEvent) event).getVehicleId(), EpisimVehicle::new);

					maxGroupSize.mergeInt(vehicle, groupSize.mergeInt(vehicle, 1, Integer::sum), Integer::max);
					totalUsers.mergeInt(vehicle, 1, Integer::sum);

					handleEvent((PersonEntersVehicleEvent) event);

				} else if (event instanceof PersonLeavesVehicleEvent) {
					if (!shouldHandlePersonEvent((HasPersonId) event)) continue;

					EpisimVehicle vehicle = this.vehicleMap.computeIfAbsent(((PersonLeavesVehicleEvent) event).getVehicleId(), EpisimVehicle::new);
					groupSize.mergeInt(vehicle, -1, Integer::sum);
					activityUsage.computeIfAbsent(vehicle, k -> new Object2IntOpenHashMap<>()).mergeInt("tr", 1, Integer::sum);

					handleEvent((PersonLeavesVehicleEvent) event);
				}
			}

			int cnt = 0;
			for (EpisimPerson person : this.personMap.values()) {
				List<EpisimPerson.Activity> tj = person.getTrajectory();

				// person that didn't move will be put at home the whole day
				if (person.getFirstFacilityId(day) == null && person.getCurrentPositionInTrajectory() == person.getStartOfDay(day)) {
					EpisimPerson.Activity home = paramsMap.computeIfAbsent("home", this::createActivityType);
					person.addToTrajectory(home);
					person.incrementCurrentPositionInTrajectory();
					EpisimFacility facility = createHomeFacility(person);
					person.setFirstFacilityId(facility.getContainerId(), day);
					cnt++;
				}

				// close open trajectories by repeating last element
				if (tj.size() == person.getCurrentPositionInTrajectory()) {
					person.addToTrajectory(tj.get(tj.size() - 1));
					person.incrementCurrentPositionInTrajectory();

					if (person.getFirstFacilityId(day) == null)
						person.setFirstFacilityId(createHomeFacility(person).getContainerId(), day);
				}

				person.setEndOfDay(day, tj.size() - 1);
			}

			log.info("Persons stationary on {}: {} ({}%)", day, cnt, cnt * 100.0 / personMap.size());

			sameDay.put(eventsForDay, day);
		}

		insertStationaryAgents();

		// Add missing facilities, with only stationary agents
		for (EpisimFacility facility : pseudoFacilityMap.values()) {
			if (!maxGroupSize.containsKey(facility)) {
				totalUsers.put(facility, facility.getPersons().size());
				maxGroupSize.put(facility, facility.getPersons().size());

				// there may be facilities with only "end" events, thus no group size, but correct activity usage
				if (!activityUsage.containsKey(facility)) {
					Object2IntOpenHashMap<String> act = new Object2IntOpenHashMap<>();
					act.put("home", facility.getPersons().size());
					activityUsage.put(facility, act);
				}
			}
		}

		// Go through each day again to compute max group sizes
		sameDay.clear();
		for (Map.Entry<DayOfWeek, List<Event>> entry : events.entrySet()) {

			DayOfWeek day = entry.getKey();
			List<Event> eventsForDay = entry.getValue();

			if (sameDay.containsKey(eventsForDay)) {
				continue;
			}

			personMap.values().forEach(p -> {
				checkAndHandleEndOfNonCircularTrajectory(p, day);
				p.resetCurrentPositionInTrajectory(day);
			});

			pseudoFacilityMap.forEach((k, v) -> maxGroupSize.mergeInt(v, v.getPersons().size(), Integer::max));

			for (Event event : eventsForDay) {
				if (event instanceof HasFacilityId && event instanceof HasPersonId) {
					EpisimFacility facility = pseudoFacilityMap.get(((HasFacilityId) event).getFacilityId());

					// happens on filtered events that are not relevant
					if (facility == null)
						continue;

					if (event instanceof ActivityStartEvent) {
						handleEvent((ActivityStartEvent) event);
						maxGroupSize.mergeInt(facility, facility.getPersons().size(), Integer::max);
					} else if (event instanceof ActivityEndEvent) {
						handleEvent((ActivityEndEvent) event);
					}
				}
			}

			sameDay.put(eventsForDay, day);
		}

		log.info("Computed max group sizes");

		reporting.reportContainerUsage(maxGroupSize, totalUsers, activityUsage);

		boolean useVehicles = !scenario.getVehicles().getVehicles().isEmpty();

		log.info("Using capacity from vehicles file: {}", useVehicles);

		// these always needs to be present
		paramsMap.computeIfAbsent("tr", this::createActivityType);
		paramsMap.computeIfAbsent("home", this::createActivityType);

		// entry for undefined activity type
		AbstractObject2IntMap.BasicEntry<String> undefined = new AbstractObject2IntMap.BasicEntry<>("undefined", -1);

		for (Object2IntMap.Entry<EpisimContainer<?>> kv : maxGroupSize.object2IntEntrySet()) {

			EpisimContainer<?> container = kv.getKey();
			double scale = 1 / episimConfig.getSampleSize();

			container.setTotalUsers((int) (totalUsers.getInt(container) * scale));
			container.setMaxGroupSize((int) (kv.getIntValue() * scale));

			Object2IntMap<String> usage = activityUsage.get(kv.getKey());
			if (usage != null) {
				Object2IntMap.Entry<String> max = usage.object2IntEntrySet().stream()
						.reduce(undefined, (s1, s2) -> s1.getIntValue() > s2.getIntValue() ? s1 : s2);

				if (max != undefined) {
					// set container spaces to spaces of most used activity
					EpisimPerson.Activity act = paramsMap.get(max.getKey());
					if (act == null)
						log.warn("No activity found for {}", max.getKey());
					else
						container.setNumSpaces(act.params.getSpacesPerFacility());
				}
			}

			if (useVehicles && container instanceof EpisimVehicle) {

				Id<Vehicle> vehicleId = Id.createVehicleId(container.getContainerId().toString());
				Vehicle vehicle = scenario.getVehicles().getVehicles().get(vehicleId);

				if (vehicle == null) {
					log.warn("No type found for vehicleId={}; using capacity of 150.", vehicleId);
					container.setTypicalCapacity(150 );
				} else {
					int capacity = vehicle.getType().getCapacity().getStandingRoom() + vehicle.getType().getCapacity().getSeats();
					container.setTypicalCapacity(capacity);
				}
			}
		}

		policy.init(episimConfig.getStartDate(), ImmutableMap.copyOf(this.restrictions));

		// Clear time-use after first iteration
		personMap.values().forEach(p -> p.getSpentTime().clear());
		init = true;
	}


	@Override
	public void handleEvent(ActivityStartEvent activityStartEvent) {
//		double now = activityStartEvent.getTime();
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), activityStartEvent.getTime(), iteration);

		if (!shouldHandleActivityEvent(activityStartEvent, activityStartEvent.getActType())) {
			return;
		}

		// find the person:
		EpisimPerson episimPerson = this.personMap.get(activityStartEvent.getPersonId());

		// create pseudo facility id that includes the activity type:
		Id<ActivityFacility> episimFacilityId = createEpisimFacilityId(activityStartEvent);

		// find the facility
		EpisimFacility episimFacility = this.pseudoFacilityMap.get(episimFacilityId);

		// add person to facility
		episimFacility.addPerson(episimPerson, now);

		handlePersonTrajectory(episimPerson.getPersonId(), activityStartEvent.getActType());

		contactModel.notifyEnterFacility( episimPerson, episimFacility, now, activityStartEvent.getActType() );
	}

	@Override
	public void handleEvent(ActivityEndEvent activityEndEvent) {
//		double now = activityEndEvent.getTime();
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), activityEndEvent.getTime(), iteration);


		if (!shouldHandleActivityEvent(activityEndEvent, activityEndEvent.getActType())) {
			return;
		}

		EpisimPerson episimPerson = this.personMap.get(activityEndEvent.getPersonId());
		Id<ActivityFacility> episimFacilityId = createEpisimFacilityId(activityEndEvent);

		EpisimFacility episimFacility = (EpisimFacility) episimPerson.getCurrentContainer();
		if (!episimFacility.equals(pseudoFacilityMap.get(episimFacilityId))) {
			throw new IllegalStateException("Person=" + episimPerson.getPersonId().toString() + " has activity end event at facility=" + episimFacilityId + " but actually is at facility=" + episimFacility.getContainerId().toString());
		}

		contactModel.infectionDynamicsFacility(episimPerson, episimFacility, now, activityEndEvent.getActType());

		double timeSpent = now - episimFacility.getContainerEnteringTime(episimPerson.getPersonId());
		episimPerson.addSpentTime(activityEndEvent.getActType(), timeSpent);

		episimFacility.removePerson(episimPerson);

		handlePersonTrajectory(episimPerson.getPersonId(), activityEndEvent.getActType());

	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent entersVehicleEvent) {
//		double now = entersVehicleEvent.getTime();
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), entersVehicleEvent.getTime(), iteration);


		if (!shouldHandlePersonEvent(entersVehicleEvent)) {
			return;
		}

		// find the person:
		EpisimPerson episimPerson = this.personMap.get(entersVehicleEvent.getPersonId());

		// find the vehicle:
		EpisimVehicle episimVehicle = this.vehicleMap.get(entersVehicleEvent.getVehicleId());

		// add person to vehicle and memorize entering time:
		episimVehicle.addPerson(episimPerson, now);

		contactModel.notifyEnterVehicle( episimPerson, episimVehicle, now );
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent leavesVehicleEvent) {
//		double now = leavesVehicleEvent.getTime();
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), leavesVehicleEvent.getTime(), iteration);


		if (!shouldHandlePersonEvent(leavesVehicleEvent)) {
			return;
		}

		// find vehicle:
		EpisimVehicle episimVehicle = this.vehicleMap.get(leavesVehicleEvent.getVehicleId());

		EpisimPerson episimPerson = this.personMap.get(leavesVehicleEvent.getPersonId());

		contactModel.infectionDynamicsVehicle(episimPerson, episimVehicle, now);

		double timeSpent = now - episimVehicle.getContainerEnteringTime(episimPerson.getPersonId());

		// This type depends on the params defined in the scenario
		episimPerson.addSpentTime("pt", timeSpent);

		// remove person from vehicle:
		episimVehicle.removePerson(episimPerson);
	}


	/**
	 * Create a new person and lookup attributes from scenario.
	 */
	private EpisimPerson createPerson(Id<Person> id) {

		Person person = scenario.getPopulation().getPersons().get(id);
		Attributes attrs;
		if (person != null) {
			attrs = person.getAttributes();
		} else {
			attrs = new Attributes();
		}

		boolean traceable = localRnd.nextDouble() < tracingConfig.getEquipmentRate();

		return new EpisimPerson(id, attrs, traceable, reporting);
	}

	/**
	 * Creates the home facility of a person.
	 */
	private EpisimFacility createHomeFacility(EpisimPerson person) {
		String homeId = (String) person.getAttributes().getAttribute("homeId");
		if (homeId == null)
			homeId = "home_of_" + person.getPersonId().toString();

		Id<ActivityFacility> facilityId = Id.create(homeId, ActivityFacility.class);
		// add facility that might not exist yet
		return this.pseudoFacilityMap.computeIfAbsent(facilityId, EpisimFacility::new);
	}

	private EpisimPerson.Activity createActivityType(String actType) {
		return new EpisimPerson.Activity(actType, episimConfig.selectInfectionParams(actType));
	}

	private Id<ActivityFacility> createEpisimFacilityId(HasFacilityId event) {
		if (episimConfig.getFacilitiesHandling() == EpisimConfigGroup.FacilitiesHandling.snz) {
			Id<ActivityFacility> id = event.getFacilityId();
			if (id == null)
				throw new IllegalStateException("No facility id present. Please switch to episimConfig.setFacilitiesHandling( EpisimConfigGroup.FacilitiesHandling.bln ) ");

			return id;
		} else if (episimConfig.getFacilitiesHandling() == EpisimConfigGroup.FacilitiesHandling.bln) {
			// TODO: this has poor performance and should be preprocessing...
			if (event instanceof ActivityStartEvent) {
				ActivityStartEvent theEvent = (ActivityStartEvent) event;
				return Id.create(theEvent.getActType().split("_")[0] + "_" + theEvent.getLinkId().toString(), ActivityFacility.class);
			} else if (event instanceof ActivityEndEvent) {
				ActivityEndEvent theEvent = (ActivityEndEvent) event;
				return Id.create(theEvent.getActType().split("_")[0] + "_" + theEvent.getLinkId().toString(), ActivityFacility.class);
			} else {
				throw new IllegalStateException("unexpected event type=" + ((Event) event).getEventType());
			}
		} else {
			throw new NotImplementedException(Gbl.NOT_IMPLEMENTED);
		}

	}

	private void handlePersonTrajectory(Id<Person> personId, String trajectoryElement) {
		EpisimPerson person = personMap.get(personId);

		if (person.getCurrentPositionInTrajectory() + 1 == person.getTrajectory().size()) {
			return;
		}
		person.incrementCurrentPositionInTrajectory();
		if (iteration > 0) {
			return;
		}

		EpisimPerson.Activity act = paramsMap.get(trajectoryElement);
		person.addToTrajectory(act);
	}

	/**
	 * Create one infection every day until initialInfections is 0.
	 */
	private void handleInitialInfections() {

		if (initialInfectionsLeft == 0) return;

		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, iteration);

		String district = episimConfig.getInitialInfectionDistrict();

		int lowerAgeBoundaryForInitInfections = episimConfig.getLowerAgeBoundaryForInitInfections();
		int upperAgeBoundaryForInitInfections = episimConfig.getUpperAgeBoundaryForInitInfections();

		LocalDate date = episimConfig.getStartDate().plusDays(iteration - 1);

		int numInfections = EpisimUtils.findValidEntry(episimConfig.getInfections_pers_per_day(), 1, date);

		List<EpisimPerson> candidates = this.personMap.values().stream()
				.filter(p -> district == null || district.equals(p.getAttributes().getAttribute("district")))
				.filter(p -> lowerAgeBoundaryForInitInfections == -1 || (int) p.getAttributes().getAttribute("microm:modeled:age") >= lowerAgeBoundaryForInitInfections)
				.filter(p -> upperAgeBoundaryForInitInfections == -1 || (int) p.getAttributes().getAttribute("microm:modeled:age") <= upperAgeBoundaryForInitInfections)
				.filter(p -> p.getDiseaseStatus() == DiseaseStatus.susceptible)
				.collect(Collectors.toList());

		if (candidates.size() < numInfections) {
			log.warn("Not enough persons match the initial infection requirement, using whole population...");
			candidates = Lists.newArrayList(this.personMap.values());
		}

		while (numInfections > 0 && initialInfectionsLeft > 0) {
			EpisimPerson randomPerson = candidates.get(rnd.nextInt(candidates.size()));
			if (randomPerson.getDiseaseStatus() == DiseaseStatus.susceptible) {
				randomPerson.setDiseaseStatus(now, DiseaseStatus.infectedButNotContagious);
				log.warn("Person {} has initial infection.", randomPerson.getPersonId());
				initialInfectionsLeft--;
				numInfections--;
			}
		}

	}

	/**
	 * Insert agents that appear in the population, but not in the event file, into their home container.
	 */
	private void insertStationaryAgents() {

		int inserted = 0;
		int skipped = 0;
		for (Person p : scenario.getPopulation().getPersons().values()) {

			if (!personMap.containsKey(p.getId())) {
				String homeId = (String) p.getAttributes().getAttribute("homeId");

				if (homeId != null) {

					Id<ActivityFacility> facilityId = Id.create(homeId, ActivityFacility.class);
					EpisimFacility facility = pseudoFacilityMap.computeIfAbsent(facilityId, EpisimFacility::new);
					EpisimPerson episimPerson = personMap.computeIfAbsent(p.getId(), this::createPerson);

					// Person stays here the whole week
					for (DayOfWeek day : DayOfWeek.values()) {
						episimPerson.setFirstFacilityId(facilityId, day);
					}

					episimPerson.addToTrajectory(new EpisimPerson.Activity("home", paramsMap.get("home").params));

					facility.addPerson(episimPerson, 0);

					inserted++;
				} else
					skipped++;
			}
		}

		if (skipped > 0)
			log.warn("Ignored {} stationary agents, because of missing home ids", skipped);

		log.info("Inserted {} stationary agents, total = {}", inserted, personMap.size());
	}

	@Override
	public void reset(int iteration) {

		// safety checks
		if (!init)
			throw new IllegalStateException(".init() was not called!");
		if (iteration <= 0)
			throw new IllegalArgumentException("Iteration must be larger 1!");
		if (paramsMap.size() > 1000)
			log.warn("Params map contains many entries. Activity types may not be .intern() Strings");

		DayOfWeek day = EpisimUtils.getDayOfWeek(episimConfig.getStartDate(), iteration);

		progressionModel.setIteration(iteration);
		progressionModel.beforeStateUpdates(personMap, iteration);
		for (EpisimPerson person : personMap.values()) {
			checkAndHandleEndOfNonCircularTrajectory(person, day);
			person.resetCurrentPositionInTrajectory(day);
			progressionModel.updateState(person, iteration);
		}

		this.iteration = iteration;

		handleInitialInfections();

		Map<String, EpisimReporting.InfectionReport> reports = reporting.createReports(personMap.values(), iteration);
		this.report = reports.get("total");

		reporting.reporting(reports, iteration, report.date);
		reporting.reportTimeUse(restrictions.keySet(), personMap.values(), iteration, report.date);

		ImmutableMap<String, Restriction> im = ImmutableMap.copyOf(this.restrictions);
		policy.updateRestrictions(report, im);
		contactModel.setRestrictionsForIteration(iteration, im);
		reporting.reportRestrictions(restrictions, iteration, report.date);

	}

	/**
	 * Handle plans with "holes" in their trajectory.
	 *
	 * @param day day that is about to start
	 */
	private void checkAndHandleEndOfNonCircularTrajectory(EpisimPerson person, DayOfWeek day) {
		Id<ActivityFacility> firstFacilityId = person.getFirstFacilityId(day);

		// now is the start of current day, when this is called iteration still has the value of the last day
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, iteration + 1);

		if (person.isInContainer()) {
			EpisimContainer<?> container = person.getCurrentContainer();
			Id<?> lastFacilityId = container.getContainerId();

			if (container instanceof EpisimFacility && this.pseudoFacilityMap.containsKey(lastFacilityId) && !firstFacilityId.equals(lastFacilityId)) {
				EpisimFacility lastFacility = this.pseudoFacilityMap.get(lastFacilityId);

				// index of last activity at previous day
				int index = person.getEndOfDay(day.minus(1));
				String actType = person.getTrajectory().get(index).actType;

				contactModel.infectionDynamicsFacility(person, lastFacility, now, actType);
				double timeSpent = now - lastFacility.getContainerEnteringTime(person.getPersonId());
				person.addSpentTime(actType, timeSpent);

				if (iteration > 1 && timeSpent > 86400 && !actType.equals("home")) {
					// there might be some implausible trajectories
					log.trace("{} spent {} outside home", person, timeSpent);
				}

				lastFacility.removePerson(person);
				EpisimFacility firstFacility = this.pseudoFacilityMap.get(firstFacilityId);
				firstFacility.addPerson(person, now);
			} else if (container instanceof EpisimVehicle && this.vehicleMap.containsKey(lastFacilityId)) {
				EpisimVehicle lastVehicle = this.vehicleMap.get(lastFacilityId);
				contactModel.infectionDynamicsVehicle(person, lastVehicle, now);
				person.addSpentTime("pt", now - lastVehicle.getContainerEnteringTime(person.getPersonId()));

				lastVehicle.removePerson(person);
				EpisimFacility firstFacility = this.pseudoFacilityMap.get(firstFacilityId);
				firstFacility.addPerson(person, now);
			}
		} else {
			EpisimFacility firstFacility = this.pseudoFacilityMap.get(firstFacilityId);
			firstFacility.addPerson(person, now);
		}
	}

	public Collection<EpisimPerson> getPersons() {
		return Collections.unmodifiableCollection(personMap.values());
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {

		out.writeLong(EpisimUtils.getSeed(rnd));
		out.writeInt(initialInfectionsLeft);
		out.writeInt(iteration);

		out.writeInt(restrictions.size());
		for (Map.Entry<String, Restriction> e : restrictions.entrySet()) {
			writeChars(out, e.getKey());
			writeChars(out, e.getValue().asMap().toString());
		}

		out.writeInt(personMap.size());
		for (Map.Entry<Id<Person>, EpisimPerson> e : personMap.entrySet()) {
			writeChars(out, e.getKey().toString());
			e.getValue().write(out);
		}

		out.writeInt(vehicleMap.size());
		for (Map.Entry<Id<Vehicle>, EpisimVehicle> e : vehicleMap.entrySet()) {
			writeChars(out, e.getKey().toString());
			e.getValue().write(out);
		}

		out.writeInt(pseudoFacilityMap.size());
		for (Map.Entry<Id<ActivityFacility>, EpisimFacility> e : pseudoFacilityMap.entrySet()) {
			writeChars(out, e.getKey().toString());
			e.getValue().write(out);
		}

	}

	@Override
	public void readExternal(ObjectInput in) throws IOException {

		long storedSeed = in.readLong();
		if (episimConfig.getSnapshotSeed() == EpisimConfigGroup.SnapshotSeed.restore) {
			EpisimUtils.setSeed(rnd, storedSeed);
		} else if (episimConfig.getSnapshotSeed() == EpisimConfigGroup.SnapshotSeed.reseed) {
			log.info("Reseeding snapshot with {}", config.global().getRandomSeed());
			EpisimUtils.setSeed(rnd, config.global().getRandomSeed());
		}

		initialInfectionsLeft = in.readInt();
		iteration = in.readInt();

		int r = in.readInt();
		for (int i = 0; i < r; i++) {
			String act = readChars(in);
			restrictions.put(act, Restriction.fromConfig(ConfigFactory.parseString(readChars(in))));
		}

		int persons = in.readInt();
		for (int i = 0; i < persons; i++) {
			Id<Person> id = Id.create(readChars(in), Person.class);
			personMap.get(id).read(in, personMap, pseudoFacilityMap, vehicleMap);
		}

		int vehicles = in.readInt();
		for (int i = 0; i < vehicles; i++) {
			Id<Vehicle> id = Id.create(readChars(in), Vehicle.class);
			vehicleMap.get(id).read(in, personMap);
		}

		int container = in.readInt();
		for (int i = 0; i < container; i++) {
			Id<ActivityFacility> id = Id.create(readChars(in), ActivityFacility.class);
			pseudoFacilityMap.get(id).read(in, personMap);
		}
	}

	/**
	 * Container that is always a vehicle.
	 */
	public static final class EpisimVehicle extends EpisimContainer<Vehicle> {
		EpisimVehicle(Id<Vehicle> vehicleId) {
			super(vehicleId);
		}
	}

	/**
	 * Container that is a facility and occurred during an activity.
	 */
	public static final class EpisimFacility extends EpisimContainer<ActivityFacility> {
		EpisimFacility(Id<ActivityFacility> facilityId) {
			super(facilityId);
		}
	}
}


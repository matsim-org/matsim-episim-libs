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
import org.matsim.episim.model.InfectionModel;
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
import java.util.*;
import java.util.stream.Collectors;

import static org.matsim.episim.EpisimUtils.readChars;
import static org.matsim.episim.EpisimUtils.writeChars;

/**
 * Main event handler of episim.
 * It consumes the events of a standard MATSim run and puts {@link EpisimPerson}s into {@link EpisimContainer}s during their activity.
 * At the end of activities an {@link InfectionModel} is executed and also a {@link ProgressionModel} at the end of the day.
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
	private final InfectionModel infectionModel;

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
	private int initialStartInfectionsLeft;

	/**
	 * Most recent infection report for all persons.
	 */
	private EpisimReporting.InfectionReport report;

	@Inject
	public InfectionEventHandler(Config config, Scenario scenario, ProgressionModel progressionModel,
								 EpisimReporting reporting, InfectionModel infectionModel, SplittableRandom rnd) {
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
		this.infectionModel = infectionModel;
		this.initialInfectionsLeft = episimConfig.getInitialInfections();
		this.initialStartInfectionsLeft = episimConfig.getInitialStartInfection();
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
	public void init(List<Event> events) {

		iteration = 0;

		for (Event event : events) {

			EpisimPerson person = null;
			EpisimFacility facility = null;

			// Add all person and facilities
			if (event instanceof HasPersonId) {
				if (!shouldHandlePersonEvent((HasPersonId) event)) continue;

				person = this.personMap.computeIfAbsent(((HasPersonId) event).getPersonId(), this::createPerson);
			}
			if (event instanceof HasFacilityId) {
				Id<ActivityFacility> episimFacilityId = createEpisimFacilityId((HasFacilityId) event);
				facility = this.pseudoFacilityMap.computeIfAbsent(episimFacilityId, EpisimFacility::new);
			}

			if (event instanceof ActivityStartEvent) {

				String actType = ((ActivityStartEvent) event).getActType();
				if (!shouldHandleActivityEvent((HasPersonId) event, actType))
					continue;

				paramsMap.computeIfAbsent(actType, k -> new EpisimPerson.Activity(k, episimConfig.selectInfectionParams(k)));
				handleEvent((ActivityStartEvent) event);
			} else if (event instanceof ActivityEndEvent) {
				String actType = ((ActivityEndEvent) event).getActType();
				if (!shouldHandleActivityEvent((HasPersonId) event, actType))
					continue;

				paramsMap.computeIfAbsent(actType, k -> new EpisimPerson.Activity(k, episimConfig.selectInfectionParams(k)));

				// Add person to container if it starts its day with end activity
				if (person.getFirstFacilityId() == null) {
					facility.addPerson(person, 0);
				}

				handleEvent((ActivityEndEvent) event);
			}

			if (event instanceof PersonEntersVehicleEvent) {
				if (!shouldHandlePersonEvent((HasPersonId) event)) continue;


				this.vehicleMap.computeIfAbsent(((PersonEntersVehicleEvent) event).getVehicleId(), EpisimVehicle::new);
				handleEvent((PersonEntersVehicleEvent) event);

			} else if (event instanceof PersonLeavesVehicleEvent) {
				if (!shouldHandlePersonEvent((HasPersonId) event)) continue;


				this.vehicleMap.computeIfAbsent(((PersonLeavesVehicleEvent) event).getVehicleId(), EpisimVehicle::new);
				handleEvent((PersonLeavesVehicleEvent) event);
			}

		}

		insertStationaryAgents();

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

		episimPerson.setLastFacilityId(episimFacilityId.toString());

		handlePersonTrajectory(episimPerson.getPersonId(), activityStartEvent.getActType());

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
			throw new IllegalStateException("Something went wrong ...");
		}

		infectionModel.infectionDynamicsFacility(episimPerson, episimFacility, now, activityEndEvent.getActType());
		double timeSpent = now - episimFacility.getContainerEnteringTime(episimPerson.getPersonId());
		episimPerson.addSpentTime(activityEndEvent.getActType(), timeSpent);

		episimFacility.removePerson(episimPerson);
		if (episimPerson.getCurrentPositionInTrajectory() == 0) {
			episimPerson.setFirstFacilityId(episimFacilityId.toString());
		}

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

		infectionModel.infectionDynamicsVehicle(episimPerson, episimVehicle, now);

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
		person.setCurrentPositionInTrajectory(person.getCurrentPositionInTrajectory() + 1);
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

		// TODO: initial infections now have to be one day later?
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, iteration);

		String district = episimConfig.getInitialInfectionDistrict();

		List<EpisimPerson> candidates = this.personMap.values().stream()
				.filter(p -> district == null || district.equals(p.getAttributes().getAttribute("district")))
				.filter(p -> p.getDiseaseStatus() == DiseaseStatus.susceptible)
				.collect(Collectors.toList());

		if (candidates.size() < initialInfectionsLeft) {
			log.warn("Not enough persons match the initial infection requirement, using whole population...");
			candidates = Lists.newArrayList(this.personMap.values());
		}

		while (true) {
			if (initialStartInfectionsLeft > 0) {
				while (initialStartInfectionsLeft > 0) {
					EpisimPerson randomPerson = candidates.get(rnd.nextInt(candidates.size()));
					if (randomPerson.getDiseaseStatus() == DiseaseStatus.susceptible) {
						randomPerson.setDiseaseStatus(now, DiseaseStatus.infectedButNotContagious);
						log.warn("Person {} has initial infection", randomPerson.getPersonId());
						initialStartInfectionsLeft--;
						initialInfectionsLeft--;
					}
				}
				break;
			}
			EpisimPerson randomPerson = candidates.get(rnd.nextInt(candidates.size()));
			if (randomPerson.getDiseaseStatus() == DiseaseStatus.susceptible) {
				randomPerson.setDiseaseStatus(now, DiseaseStatus.infectedButNotContagious);
				log.warn("Person {} has initial infection", randomPerson.getPersonId());
				initialInfectionsLeft--;
				break;
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

					episimPerson.setFirstFacilityId(facilityId.toString());
					episimPerson.setLastFacilityId(facilityId.toString());
					episimPerson.addToTrajectory(new EpisimPerson.Activity("home", episimConfig.selectInfectionParams("home")));

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

		progressionModel.setIteration(iteration);
		for (EpisimPerson person : personMap.values()) {
			checkAndHandleEndOfNonCircularTrajectory(person);
			person.setCurrentPositionInTrajectory(0);
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
		infectionModel.setRestrictionsForIteration(iteration, im);
		reporting.reportRestrictions(restrictions, iteration, report.date);

	}

	private void checkAndHandleEndOfNonCircularTrajectory(EpisimPerson person) {
		Id<ActivityFacility> firstFacilityId = Id.create(person.getFirstFacilityId(), ActivityFacility.class);

		// now is the next day
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, iteration + 1);

		if (person.isInContainer()) {
			EpisimContainer<?> container = person.getCurrentContainer();
			Id<?> lastFacilityId = container.getContainerId();

			if (container instanceof EpisimFacility && this.pseudoFacilityMap.containsKey(lastFacilityId) && !firstFacilityId.equals(lastFacilityId)) {
				EpisimFacility lastFacility = this.pseudoFacilityMap.get(lastFacilityId);
				String actType = person.getTrajectory().get(person.getTrajectory().size() - 1).actType;

				infectionModel.infectionDynamicsFacility(person, lastFacility, now, actType);
				person.addSpentTime(actType, now - lastFacility.getContainerEnteringTime(person.getPersonId()));


				lastFacility.removePerson(person);
				EpisimFacility firstFacility = this.pseudoFacilityMap.get(firstFacilityId);
				firstFacility.addPerson(person, now);
			} else if (container instanceof EpisimVehicle && this.vehicleMap.containsKey(lastFacilityId)) {
				EpisimVehicle lastVehicle = this.vehicleMap.get(lastFacilityId);
				infectionModel.infectionDynamicsVehicle(person, lastVehicle, now);
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
		out.writeInt(initialStartInfectionsLeft);
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
		initialStartInfectionsLeft = in.readInt();
		iteration = in.readInt();

		int r = in.readInt();
		for (int i = 0; i < r; i++) {
			String act = readChars(in);
			restrictions.put(act, Restriction.fromConfig(ConfigFactory.parseString(readChars(in))));
		}

		int persons = in.readInt();
		for (int i = 0; i < persons; i++) {
			Id<Person> id = Id.create(readChars(in), Person.class);
			personMap.get(id).read(in, paramsMap, personMap, pseudoFacilityMap, vehicleMap);
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


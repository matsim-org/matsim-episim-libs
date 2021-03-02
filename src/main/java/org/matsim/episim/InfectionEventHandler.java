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
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.typesafe.config.ConfigFactory;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.ContactModel;
import org.matsim.episim.model.InitialInfectionHandler;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.model.VaccinationModel;
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
import java.util.concurrent.CompletableFuture;

import static org.matsim.episim.EpisimUtils.readChars;
import static org.matsim.episim.EpisimUtils.writeChars;

/**
 * Main event handler of episim.
 * It consumes the events of a standard MATSim run and puts {@link EpisimPerson}s into {@link EpisimContainer}s during their activity.
 * At the end of activities an {@link ContactModel} is executed and also a {@link ProgressionModel} at the end of the day.
 * See {@link EpisimModule} for which components may be substituted.
 * <p>
 * This handler should be used in conjunction with a {@link ReplayHandler}, which filters and preprocesses events.
 * For performance reasons it is not used with the {@link org.matsim.core.api.experimental.events.EventsManager}.
 */
public final class InfectionEventHandler implements Externalizable {
	// Some notes:

	// * Especially if we repeat the same events file, then we do not have complete mixing.  So it may happen that only some subpopulations gets infected.

	// * However, if with infection proba=1 almost everybody gets infected, then in our current setup (where infected people remain in the iterations),
	// this will also happen with lower probabilities, albeit slower.  This is presumably the case that we want to investigate.

	// * We seem to be getting two different exponential spreading rates.  With infection proba=1, the crossover is (currently) around 15h.

	// TODO

	// * yyyyyy There are now some things that depend on ID conventions.  We should try to replace them.  This presumably would mean to interpret
	//  additional events.  Those would need to be prepared for the "reduced" files.  kai, mar'20


	private static final Logger log = LogManager.getLogger(InfectionEventHandler.class);

	/**
	 * Injector instance.
	 */
	private final Injector injector;

	/**
	 * List of trajectory handlers that can be run in parallel.
	 */
	private final List<TrajectoryHandler> handlers = new ArrayList<>();

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
	 * Handle initial infections.
	 */
	private final InitialInfectionHandler initialInfections;

	/**
	 * Handle vaccinations.
	 */
	private final VaccinationModel vaccinationModel;

	/**
	 * Scenario with population information.
	 */
	private final Scenario scenario;

	private final Config config;
	private final EpisimConfigGroup episimConfig;
	private final TracingConfigGroup tracingConfig;
	private final VaccinationConfigGroup vaccinationConfig;
	private final EpisimReporting reporting;
	private final SplittableRandom rnd;

	/**
	 * Local random, e.g. used for person initialization.
	 */
	private final SplittableRandom localRnd;

	private boolean init = false;
	private int iteration = 0;

	/**
	 * Most recent infection report for all persons.
	 */
	private EpisimReporting.InfectionReport report;

	@Inject
	public InfectionEventHandler(Injector injector, SplittableRandom rnd) {
		this.injector = injector;
		this.rnd = rnd;

		this.config = injector.getInstance(Config.class);
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		this.vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		this.scenario = injector.getInstance(Scenario.class);
		this.policy = episimConfig.createPolicyInstance();
		this.restrictions = episimConfig.createInitialRestrictions();
		this.reporting = injector.getInstance(EpisimReporting.class);
		this.localRnd = new SplittableRandom(config.global().getRandomSeed() + 65536);
		this.progressionModel = injector.getInstance(ProgressionModel.class);
		this.initialInfections = injector.getInstance(InitialInfectionHandler.class);
		this.initialInfections.setInfectionsLeft(episimConfig.getInitialInfections());
		this.vaccinationModel = injector.getInstance(VaccinationModel.class);
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

		// TODO: don't rely on handleEvent anymore

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
							//person.incrementCurrentPositionInTrajectory();
							// start of current day also needs to be shifted
							//person.setStartOfDay(day, person.getCurrentPositionInTrajectory());
						}
					}
				}

				if (event instanceof HasFacilityId) {
					Id<ActivityFacility> episimFacilityId = ((HasFacilityId) event).getFacilityId();
					facility = this.pseudoFacilityMap.computeIfAbsent(episimFacilityId, EpisimFacility::new);
				}

				if (event instanceof ActivityStartEvent) {

					String actType = ((ActivityStartEvent) event).getActType();

					EpisimPerson.Activity act = paramsMap.computeIfAbsent(actType, this::createActivityType);
					totalUsers.mergeInt(facility, 1, Integer::sum);

					//handleEvent((ActivityStartEvent) event);

				} else if (event instanceof ActivityEndEvent) {
					String actType = ((ActivityEndEvent) event).getActType();

					EpisimPerson.Activity act = paramsMap.computeIfAbsent(actType, this::createActivityType);
					activityUsage.computeIfAbsent(facility, k -> new Object2IntOpenHashMap<>()).mergeInt(actType, 1, Integer::sum);

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
					// TODO
					//handleEvent((ActivityEndEvent) event);
				}

				if (event instanceof PersonEntersVehicleEvent) {
					EpisimVehicle vehicle = this.vehicleMap.computeIfAbsent(((PersonEntersVehicleEvent) event).getVehicleId(), EpisimVehicle::new);

					maxGroupSize.mergeInt(vehicle, groupSize.mergeInt(vehicle, 1, Integer::sum), Integer::max);
					totalUsers.mergeInt(vehicle, 1, Integer::sum);

					// TODO
					//handleEvent((PersonEntersVehicleEvent) event);

				} else if (event instanceof PersonLeavesVehicleEvent) {
					EpisimVehicle vehicle = this.vehicleMap.computeIfAbsent(((PersonLeavesVehicleEvent) event).getVehicleId(), EpisimVehicle::new);
					groupSize.mergeInt(vehicle, -1, Integer::sum);
					activityUsage.computeIfAbsent(vehicle, k -> new Object2IntOpenHashMap<>()).mergeInt("tr", 1, Integer::sum);

					// TODO
					//handleEvent((PersonLeavesVehicleEvent) event);
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
				totalUsers.mergeInt(facility, facility.getPersons().size(), Integer::max);
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
				// TODO
				//checkAndHandleEndOfNonCircularTrajectory(p, day);
				p.resetCurrentPositionInTrajectory(day);
			});

			pseudoFacilityMap.forEach((k, v) -> maxGroupSize.mergeInt(v, v.getPersons().size(), Integer::max));

			for (Event event : eventsForDay) {
				if (event instanceof HasFacilityId && event instanceof HasPersonId) {
					Id<ActivityFacility> episimFacilityId = ((HasFacilityId) event).getFacilityId();
					EpisimFacility facility = pseudoFacilityMap.get(episimFacilityId);

					// happens on filtered events that are not relevant
					if (facility == null)
						continue;

					if (event instanceof ActivityStartEvent) {
						// TODO
						//handleEvent((ActivityStartEvent) event);
						maxGroupSize.mergeInt(facility, facility.getPersons().size(), Integer::max);
					} else if (event instanceof ActivityEndEvent) {
						// TODO
						//handleEvent((ActivityEndEvent) event);
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
					container.setTypicalCapacity(150);
				} else {
					int capacity = vehicle.getType().getCapacity().getStandingRoom() + vehicle.getType().getCapacity().getSeats();
					container.setTypicalCapacity(capacity);
				}
			}
		}

		policy.init(episimConfig.getStartDate(), ImmutableMap.copyOf(this.restrictions));

		// Clear time-use after first iteration
		personMap.values().forEach(p -> p.getSpentTime().clear());

		createTrajectoryHandlers();

		init = true;
	}


	/**
	 * Create handlers for executing th
	 */
	protected void createTrajectoryHandlers() {

		AbstractModule childModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(SplittableRandom.class);
				bind(Map.class).annotatedWith(Names.named("personMap")).toInstance(personMap);
				bind(Map.class).annotatedWith(Names.named("vehicleMap")).toInstance(vehicleMap);
				bind(Map.class).annotatedWith(Names.named("pseudoFacilityMap")).toInstance(pseudoFacilityMap);
				bind(Map.class).annotatedWith(Names.named("paramsMap")).toInstance(paramsMap);
			}
		};

		// NUM_THREADS = 4
		for (int i = 0; i < 4; i++) {

			Injector inj = injector.createChildInjector(childModule);
			TrajectoryHandler handler = inj.getInstance(TrajectoryHandler.class);
			handlers.add(handler);
		}

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

	public void reset(int iteration) {

		// safety checks
		if (!init)
			throw new IllegalStateException(".init() was not called!");
		if (iteration <= 0)
			throw new IllegalArgumentException("Iteration must be larger 1!");
		if (paramsMap.size() > 1000)
			log.warn("Params map contains many entries. Activity types may not be .intern() Strings");

		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, iteration);
		LocalDate date = episimConfig.getStartDate().plusDays(iteration - 1);
		DayOfWeek day = EpisimUtils.getDayOfWeek(episimConfig, iteration);

		for (EpisimPerson person : personMap.values()) {
			// stf: I think this must be done before the "beforeStateUpdates" call
			person.checkInfection();
		}

		progressionModel.setIteration(iteration);
		progressionModel.beforeStateUpdates(personMap, iteration, this.report);
		for (EpisimPerson person : personMap.values()) {
			// TODO handle in trajectory?
			// checkAndHandleEndOfNonCircularTrajectory(person, day);
			person.resetCurrentPositionInTrajectory(day);
			progressionModel.updateState(person, iteration);
		}

		int available = EpisimUtils.findValidEntry(vaccinationConfig.getVaccinationCapacity(), 0, date);
		vaccinationModel.handleVaccination(personMap, (int) (available * episimConfig.getSampleSize()), iteration, now);

		this.iteration = iteration;

		int infected = this.initialInfections.handleInfections(personMap, iteration);

		Map<String, EpisimReporting.InfectionReport> reports = reporting.createReports(personMap.values(), iteration);
		this.report = reports.get("total");

		reporting.reporting(reports, iteration, report.date);
		reporting.reportTimeUse(restrictions.keySet(), personMap.values(), iteration, report.date);
		reporting.reportDiseaseImport(infected, iteration, report.date);

		ImmutableMap<String, Restriction> im = ImmutableMap.copyOf(this.restrictions);
		policy.updateRestrictions(report, im);

		handlers.forEach(h -> h.setRestrictionsForIteration(iteration, im));

		reporting.reportRestrictions(restrictions, iteration, report.date);

	}

	public Collection<EpisimPerson> getPersons() {
		return Collections.unmodifiableCollection(personMap.values());
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {

		out.writeLong(EpisimUtils.getSeed(rnd));
		out.writeInt(initialInfections.getInfectionsLeft());
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

		initialInfections.setInfectionsLeft(in.readInt());
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


		ImmutableMap<String, Restriction> im = ImmutableMap.copyOf(this.restrictions);

		policy.restore(episimConfig.getStartDate().plusDays(iteration), im);
		handlers.forEach(h -> h.setRestrictionsForIteration(iteration, im));
	}

	/**
	 * Execute trajectory events.
	 *
	 * @param day    current day
	 * @param events events to execute
	 */
	void handleEvents(DayOfWeek day, List<Event> events) {

		if (handlers.size() > 1) {
			var futures = new CompletableFuture[handlers.size()];
			for (int i = 0; i < handlers.size(); i++) {
				ReplayEventsTask task = new ReplayEventsTask(handlers.get(i), events, i, handlers.size());
				futures[i] = CompletableFuture.runAsync(task);
			}

			CompletableFuture.allOf(futures).join();
		} else {

			// single threaded task is run directly
			ReplayEventsTask task = new ReplayEventsTask(handlers.get(0), events, 0, 1);
			task.run();

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


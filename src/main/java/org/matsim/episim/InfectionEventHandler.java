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
import com.google.inject.*;
import com.google.inject.name.Names;
import com.google.inject.util.Types;
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
import org.matsim.core.utils.collections.Tuple;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.model.*;
import org.matsim.episim.model.activity.ActivityParticipationModel;
import org.matsim.episim.model.testing.TestingModel;
import org.matsim.episim.model.vaccination.VaccinationModel;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

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
	private final Map<String, EpisimConfigGroup.InfectionParams> paramsMap = new IdentityHashMap<>();

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
	 * Activity participation.
	 */
	private final ActivityParticipationModel activityParticipationModel;

	private final TestingModel testingModel;

	/**
	 * Scenario with population information.
	 */
	private final Scenario scenario;

	/**
	 * Executors for trajectories.
	 */
	private final ExecutorService executor;

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

	/**
	 * Installed simulation listeners.
	 */
	private Set<SimulationListener> listener;

	@Inject
	public InfectionEventHandler(Injector injector, SplittableRandom rnd) {
		this.injector = injector;
		this.rnd = rnd;

		this.config = injector.getInstance(Config.class);
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		this.vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		this.scenario = injector.getInstance(Scenario.class);
		this.policy = injector.getInstance(ShutdownPolicy.class);
		this.restrictions = episimConfig.createInitialRestrictions();
		this.reporting = injector.getInstance(EpisimReporting.class);
		this.localRnd = new SplittableRandom( 65536); // fixed seed, because it should not change between snapshots
		this.progressionModel = injector.getInstance(ProgressionModel.class);
		this.initialInfections = injector.getInstance(InitialInfectionHandler.class);
		this.initialInfections.setInfectionsLeft(episimConfig.getInitialInfections());
		this.vaccinationModel = injector.getInstance(VaccinationModel.class);
		this.activityParticipationModel = injector.getInstance(ActivityParticipationModel.class);
		this.testingModel = injector.getInstance(TestingModel.class);
		this.executor = injector.getInstance(ExecutorService.class);
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

	public void finish() {
		executor.shutdown();
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

		// This is used to distribute the containers to the different ReplayEventTasks
		List<Tuple<EpisimContainer<?>, Double>> estimatedLoad = new LinkedList<>();

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

			this.personMap.values().forEach(p -> p.setStartOfDay(day));

			for (Event event : eventsForDay) {

				EpisimPerson person = null;
				EpisimFacility facility = null;

				// Add all person and facilities
				if (event instanceof HasPersonId) {
					person = this.personMap.computeIfAbsent(((HasPersonId) event).getPersonId(), this::createPerson);

					// If a person was added late, previous days are initialized at home
					for (int i = 1; i < day.getValue(); i++) {
						DayOfWeek it = DayOfWeek.of(i);
						if (!person.hasActivity(it)) {
							person.setStartOfDay(it);
							Id<ActivityFacility> homeId = createHomeFacility(person).getContainerId();

							person.setFirstFacilityId(homeId, it);
							person.setLastFacilityId(homeId, it, true);

							EpisimConfigGroup.InfectionParams home = paramsMap.computeIfAbsent("home", this::createActivityType);
							person.addToTrajectory(0, home, homeId);
							person.setEndOfDay(it);
							person.setStartOfDay(it.plus(1));
						}
					}
				}

				if (event instanceof HasFacilityId) {
					Id<ActivityFacility> episimFacilityId = ((HasFacilityId) event).getFacilityId();
					facility = this.pseudoFacilityMap.computeIfAbsent(episimFacilityId, EpisimFacility::new);
				}

				if (event instanceof ActivityStartEvent) {

					String actType = ((ActivityStartEvent) event).getActType();

					EpisimConfigGroup.InfectionParams act = paramsMap.computeIfAbsent(actType, this::createActivityType);
					totalUsers.mergeInt(facility, 1, Integer::sum);

					Id<ActivityFacility> facilityId = ((ActivityStartEvent) event).getFacilityId();
					person.addToTrajectory(event.getTime(), act, facilityId);

					person.setLastFacilityId(facility.getContainerId(), day, true);

				} else if (event instanceof ActivityEndEvent) {
					String actType = ((ActivityEndEvent) event).getActType();

					EpisimConfigGroup.InfectionParams act = paramsMap.computeIfAbsent(actType, this::createActivityType);
					activityUsage.computeIfAbsent(facility, k -> new Object2IntOpenHashMap<>()).mergeInt(actType, 1, Integer::sum);

					// if this is the first event, container is saved and trajectory element created
					if (!person.hasActivity(day)) {
						Id<ActivityFacility> facilityId = ((ActivityEndEvent) event).getFacilityId();
						person.addToTrajectory(0, act, facilityId);
						person.setFirstFacilityId(facility.getContainerId(), day);
					}

					// person is not in this container anymore
					person.setLastFacilityId(facility.getContainerId(), day, false);
				}

				if (event instanceof PersonEntersVehicleEvent) {
					EpisimVehicle vehicle = this.vehicleMap.computeIfAbsent(((PersonEntersVehicleEvent) event).getVehicleId(), EpisimVehicle::new);

					maxGroupSize.mergeInt(vehicle, groupSize.mergeInt(vehicle, 1, Integer::sum), Integer::max);
					totalUsers.mergeInt(vehicle, 1, Integer::sum);

					person.setStaysInContainer(day, false);

				} else if (event instanceof PersonLeavesVehicleEvent) {
					EpisimVehicle vehicle = this.vehicleMap.computeIfAbsent(((PersonLeavesVehicleEvent) event).getVehicleId(), EpisimVehicle::new);
					groupSize.mergeInt(vehicle, -1, Integer::sum);
					activityUsage.computeIfAbsent(vehicle, k -> new Object2IntOpenHashMap<>()).mergeInt("tr", 1, Integer::sum);

					// vehicle don't count as end of day containers
					person.setStaysInContainer(day, false);
				}
			}

			int cnt = 0;
			for (EpisimPerson person : this.personMap.values()) {

				// person that didn't move will be put at home the whole day
				if (!person.hasActivity(day)) {
					person.setStartOfDay(day);
					EpisimConfigGroup.InfectionParams home = paramsMap.computeIfAbsent("home", this::createActivityType);
					EpisimFacility facility = createHomeFacility(person);
					person.setFirstFacilityId(facility.getContainerId(), day);
					person.setLastFacilityId(facility.getContainerId(), day, true);
					person.addToTrajectory(0, home, facility.getContainerId());
					cnt++;
				}

				person.setEndOfDay(day);
			}

			log.info("Persons stationary on {}: {} ({}%)", day, cnt, cnt * 100.0 / personMap.size());

			sameDay.put(eventsForDay, day);
		}

		insertStationaryAgents();

		// Add missing facilities, with only stationary agents
		for (EpisimFacility facility : pseudoFacilityMap.values()) {
			if (!activityUsage.containsKey(facility)) {
				Object2IntOpenHashMap<String> act = new Object2IntOpenHashMap<>();
				act.put("home", facility.getPersons().size());
				activityUsage.put(facility, act);
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

			// Simulate the behaviour for unclosed trajectories
			for (EpisimPerson person : personMap.values()) {
				Id<ActivityFacility> first = person.getFirstFacilityId(day);
				Id<ActivityFacility> last = person.getLastFacilityId(day.minus(1));

				if (person.getStaysInContainer(day.minus(1))) {

					if (pseudoFacilityMap.get(last).containsPerson(person))
						pseudoFacilityMap.get(last).removePerson(person);

					if (!pseudoFacilityMap.get(first).containsPerson(person))
						pseudoFacilityMap.get(first).addPerson(person, 0, person.getFirstActivity(day));

				} else {
					if (!pseudoFacilityMap.get(first).containsPerson(person))
						pseudoFacilityMap.get(first).addPerson(person, 0, person.getFirstActivity(day));
				}
			}

			pseudoFacilityMap.forEach((k, v) -> maxGroupSize.mergeInt(v, v.getPersons().size(), Integer::max));

			for (Event event : eventsForDay) {
				if (event instanceof HasFacilityId && event instanceof HasPersonId) {
					Id<ActivityFacility> episimFacilityId = ((HasFacilityId) event).getFacilityId();
					EpisimFacility facility = pseudoFacilityMap.get(episimFacilityId);
					EpisimPerson person = this.personMap.get(((HasPersonId) event).getPersonId());

					// happens on filtered events that are not relevant
					if (facility == null)
						continue;

					if (event instanceof ActivityStartEvent) {
						if (!facility.containsPerson(person))
							facility.addPerson(person, 0.0, person.getActivity(day, event.getTime()));

						maxGroupSize.mergeInt(facility, facility.getPersons().size(), Integer::max);
					} else if (event instanceof ActivityEndEvent) {
						if (facility.containsPerson(person))
							facility.removePerson(person);
					}
				}
			}

			sameDay.put(eventsForDay, day);
		}

		pseudoFacilityMap.values().forEach(EpisimContainer::clearPersons);

		// Put persons into their correct initial container
		DayOfWeek startDay = EpisimUtils.getDayOfWeek(episimConfig, 0);
		for (EpisimPerson person : personMap.values()) {
			if (person.getStaysInContainer(startDay)) {
				EpisimFacility facility = pseudoFacilityMap.get(person.getLastFacilityId(startDay));
				facility.addPerson(person, 0, person.getLastActivity(startDay));
			}
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

			final int numUsers = totalUsers.getInt(container);
			container.setTotalUsers((int) (numUsers * scale));
			container.setMaxGroupSize((int) (kv.getIntValue() * scale));
			estimatedLoad.add(Tuple.of(container, (double) numUsers * kv.getIntValue()));

			Object2IntMap<String> usage = activityUsage.get(kv.getKey());
			if (usage != null) {
				Object2IntMap.Entry<String> max = usage.object2IntEntrySet().stream()
						.reduce(undefined, (s1, s2) -> s1.getIntValue() > s2.getIntValue() ? s1 : s2);

				if (max != undefined) {
					// set container spaces to spaces of most used activity
					EpisimConfigGroup.InfectionParams act = paramsMap.get(max.getKey());
					if (act == null)
						log.warn("No activity found for {}", max.getKey());
					else
						container.setNumSpaces(act.getSpacesPerFacility());
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
		personMap.values().forEach(EpisimPerson::initParticipation);

		// init person vaccination compliance sorted by age descending
		personMap.values().stream()
				.sorted(Comparator.comparingInt(p -> ((EpisimPerson) p).getAgeOrDefault(-1)).reversed()
						.thenComparing(p -> ((EpisimPerson) p).getPersonId()))
				.forEach(p -> {
			Double compliance = EpisimUtils.findValidEntry(vaccinationConfig.getCompliancePerAge(), 1.0, p.getAgeOrDefault(-1));
			p.setVaccinable(localRnd.nextDouble() < compliance);
		});

		listener = (Set<SimulationListener>) injector.getInstance(Key.get(Types.setOf(SimulationListener.class)));

		for (SimulationListener s : listener) {

			log.info("Executing simulation start listener {}", s.toString());

			s.init(localRnd, personMap, pseudoFacilityMap, vehicleMap);
		}

		vaccinationModel.init(localRnd, personMap, pseudoFacilityMap, vehicleMap);

		balanceContainersByLoad(estimatedLoad);

		createTrajectoryHandlers();

		init = true;
	}

	/**
	 * Distribute the containers to the different ReplayEventTasks, by setting
	 * the taskId attribute of the containers to values between 0 and episimConfig.getThreds() - 1,
     * so that the sum of numUsers * maxGroupSize has an even distribution
	 */
	private void balanceContainersByLoad(List<Tuple<EpisimContainer<?>, Double>> estimatedLoad) {
		// We need the containers sorted by the load, with the highest load first.
		// To get a deterministic distribution, we use the containerId for
		// sorting the containers with the same estimatedLoad.
		Comparator<Tuple<EpisimContainer<?>, Double>> loadComperator =
			Comparator.<Tuple<EpisimContainer<?>, Double>,Double>comparing(
						  t -> t.getSecond(), Comparator.reverseOrder()).
			thenComparing(t -> t.getFirst().getContainerId().toString());
		Collections.sort(estimatedLoad, loadComperator);

		final int numThreads = episimConfig.getThreads();
		// the overall load of the containers assigned to the thread/taskId
		final Double[] loadPerThread = new Double[numThreads];
		for (int i = 0; i < numThreads; i++)
			loadPerThread[i] = 0.0;

		for(Tuple<EpisimContainer<?>, Double> tuple : estimatedLoad) {
			// search for the thread/taskId with the minimal load
			int useThread = 0;
			Double minLoad = loadPerThread[0];
			for (int i = 1; i < numThreads; i++) {
				if (loadPerThread[i] < minLoad) {
					useThread = i;
					minLoad = loadPerThread[i];
				}
			}
			// add the load to this thread and set the taskId for the container
			loadPerThread[useThread] += tuple.getSecond();
			tuple.getFirst().setTaskId(useThread);
		}
	}


	/**
	 * Distribute the containers to the different ReplayEventTasks, using
	 * the hashCode of the containerId (the original distribution schema)
	 */
	private void balanceContainersByHash(List<Tuple<EpisimContainer<?>, Double>> estimatedLoad) {
		for (Tuple<EpisimContainer<?>, Double> tuple : estimatedLoad) {
		    final EpisimContainer<?> container = tuple.getFirst();
			final int useThread = Math.abs(container.getContainerId().hashCode()) % episimConfig.getThreads();		     container.setTaskId(useThread);
		}
	}

	/**
	 * Create handlers for executing th
	 */
	protected void createTrajectoryHandlers() {

		log.info("Initializing {} trajectory handlers", episimConfig.getThreads());

		for (int i = 0; i < episimConfig.getThreads(); i++) {

			AbstractModule childModule = new AbstractModule() {
				@Override
				protected void configure() {
					// the seed state is set later by this class
					bind(SplittableRandom.class).toInstance(new SplittableRandom(rnd.nextLong()));
					bind(TrajectoryHandler.class);

					TypeLiteral<Map<Id<Person>, EpisimPerson>> pMap = new TypeLiteral<>() {
					};
					TypeLiteral<Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle>> vMap = new TypeLiteral<>() {
					};
					TypeLiteral<Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility>> fMap = new TypeLiteral<>() {
					};

					bind(pMap).annotatedWith(Names.named("personMap")).toInstance(personMap);
					bind(vMap).annotatedWith(Names.named("vehicleMap")).toInstance(vehicleMap);
					bind(fMap).annotatedWith(Names.named("pseudoFacilityMap")).toInstance(pseudoFacilityMap);
				}
			};

			// create child injector with separate instance of models
			Injector inj = GuiceUtils.createCopiedInjector(injector, List.of(childModule), ContactModel.class, InfectionModel.class, FaceMaskModel.class);

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

	private EpisimConfigGroup.InfectionParams createActivityType(String actType) {
		return episimConfig.selectInfectionParams(actType);
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
						episimPerson.setLastFacilityId(facilityId, day, true);
						episimPerson.setStartOfDay(day);
					}

					EpisimPerson.PerformedActivity home = episimPerson.addToTrajectory(0, paramsMap.get("home"), facilityId);
					facility.addPerson(episimPerson, 0, home);

					// set end index
					for (DayOfWeek day : DayOfWeek.values()) {
						episimPerson.setEndOfDay(day);
					}

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

		reporting.reportCpuTime(iteration, "ProgressionModel", "start", -1);
		progressionModel.setIteration(iteration);
		progressionModel.beforeStateUpdates(personMap, iteration, this.report);

		for (EpisimPerson person : personMap.values()) {
			progressionModel.updateState(person, iteration);
		}
		reporting.reportCpuTime(iteration, "ProgressionModelParallel", "start", -2);
		progressionModel.afterStateUpdates(personMap, iteration);
		reporting.reportCpuTime(iteration, "ProgressionModelParallel", "finished", -2);
		reporting.reportCpuTime(iteration, "ProgressionModel", "finished", -1);

		reporting.reportCpuTime(iteration, "VaccinationModel", "start", -1);
		int available = EpisimUtils.findValidEntry(vaccinationConfig.getVaccinationCapacity(), -1, date);
		vaccinationModel.handleVaccination(personMap, false, available > 0 ? (int) (available * episimConfig.getSampleSize()) : -1, date, iteration, now);

		available = EpisimUtils.findValidEntry(vaccinationConfig.getReVaccinationCapacity(), -1, date);
		vaccinationModel.handleVaccination(personMap, true, available > 0 ? (int) (available * episimConfig.getSampleSize()) : -1, date, iteration, now);
		reporting.reportCpuTime(iteration, "VaccinationModel", "finished", -1);

		this.iteration = iteration;

		reporting.reportCpuTime(iteration, "HandleInfections", "start", -1);
		int infected = this.initialInfections.handleInfections(personMap, iteration);
		reporting.reportCpuTime(iteration, "HandleInfections", "finished", -1);

		reporting.reportCpuTime(iteration, "Reporting", "start", -1);
		Map<String, EpisimReporting.InfectionReport> reports = reporting.createReports(personMap.values(), iteration);
		this.report = reports.get("total");

		reporting.reporting(reports, iteration, report.date);
		reporting.reportCpuTime(iteration, "ReportTimeUse", "start", -2);
		reporting.reportTimeUse(restrictions.keySet(), personMap.values(), iteration, report.date);
		reporting.reportCpuTime(iteration, "ReportTimeUse", "finished", -2);
		reporting.reportDiseaseImport(infected, iteration, report.date);

		ImmutableMap<String, Restriction> im = ImmutableMap.copyOf(this.restrictions);
		policy.updateRestrictions(report, im);

		reporting.reportCpuTime(iteration, "TestingModel", "start", -1);
		DayOfWeek day = EpisimUtils.getDayOfWeek(episimConfig, iteration);
		testingModel.setIteration(iteration);
		testingModel.beforeStateUpdates(personMap, iteration, this.report);

		activityParticipationModel.setRestrictionsForIteration(iteration, im);

		for (EpisimPerson person : personMap.values()) {
			// update person activity participation for the day
			activityParticipationModel.updateParticipation(person, person.getActivityParticipation(),
					person.getStartOfDay(day), person.getActivities(day));

			testingModel.performTesting(person, iteration);

			activityParticipationModel.applyQuarantine(person, person.getActivityParticipation(), person.getStartOfDay(day), person.getActivities(day));

		}
		reporting.reportCpuTime(iteration, "TestingModel", "finished", -1);

		handlers.forEach(h -> {
			h.setRestrictionsForIteration(iteration, im);
			EpisimUtils.setSeed(h.getRnd(), rnd.nextLong());
		});

		reporting.reportRestrictions(restrictions, iteration, report.date);
		reporting.reportCpuTime(iteration, "Reporting", "finished", -1);

		for (SimulationListener l : listener) {
			l.onIterationStart(iteration, date);
		}
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
			personMap.get(id).read(in, personMap);
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
				futures[i] = CompletableFuture.runAsync(task, executor);
			}

			try {
				CompletableFuture.allOf(futures).join();
			} catch (CompletionException e) {
				log.error("A TrajectoryHandler caused the exception: ", e.getCause());
				executor.shutdown();
				throw e;
			}
		} else {

			// single threaded task is run directly
			ReplayEventsTask task = new ReplayEventsTask(handlers.get(0), events, 0, 1);
			task.run();

		}

		// store the infections for a day
		List<EpisimInfectionEvent> infections = new ArrayList<>();

		// "execute" collected infections
		for (EpisimPerson person : personMap.values()) {
			EpisimInfectionEvent e;
			if ((e = person.checkInfection()) != null)
				infections.add(e);
		}

		// report infections in order
		infections.stream().sorted()
				.forEach(reporting::reportInfection);

		for (SimulationListener l : listener) {
			l.onIterationEnd(iteration, episimConfig.getStartDate().plusDays(iteration - 1));
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


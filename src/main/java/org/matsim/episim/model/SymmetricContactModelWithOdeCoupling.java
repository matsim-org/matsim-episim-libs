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
package org.matsim.episim.model;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.episim.*;
import org.matsim.facilities.*;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.matsim.episim.EpisimPerson.DiseaseStatus;

/**
 * Variant of the {@link DefaultContactModel} with symmetric interactions.
 */
// TODO: should this instead extend SymmetricContactModel to avoid code duplication. Currently not possible as SymmetricContactModel is final.
public final class SymmetricContactModelWithOdeCoupling extends AbstractContactModel {

	private static final Logger log = LogManager.getLogger(SymmetricContactModelWithOdeCoupling.class);

	/**
	 * Flag to enable tracking, which is considerably slower.
	 */
	private final int trackingAfterDay;

	/**
	 * Whether to trace susceptible persons.
	 */
	private final boolean traceSusceptible;

	/**
	 * This buffer is used to store the infection type.
	 */
	private final StringBuilder buffer = new StringBuilder();


	private final ActivityFacilities facilities;

	private final ProgressionModel progressionModel;
	private final EpisimConfigGroup episimConfigGroup;
	private Int2DoubleMap dayToInfectionShareMap;

	private EpisimContainer<ActivityFacility> containerFake;

	private Map<Id<Person>, EpisimPerson> fakePersonPool;
	private Long odeDiseaseImportCount;

//	private Long unknownCnt;


	private final List<String> odeDistricts;

	boolean odeInfTargetDistrictActive;

	@Inject
		/* package */
	SymmetricContactModelWithOdeCoupling(SplittableRandom rnd, Config config, TracingConfigGroup tracingConfig,
										 EpisimReporting reporting, InfectionModel infectionModel, ProgressionModel progressionModel,
										 EpisimConfigGroup episimConfigGroup, Scenario scenario) {
		// (make injected constructor non-public so that arguments can be changed without repercussions.  kai, jun'20)
		super(rnd, config, infectionModel, reporting, scenario);

		fakePersonPool = new LinkedHashMap<>();

		this.episimConfigGroup = episimConfigGroup;
		this.progressionModel = progressionModel;
		this.trackingAfterDay = tracingConfig.getPutTraceablePersonsInQuarantineAfterDay();
		this.traceSusceptible = tracingConfig.getTraceSusceptible();

		this.containerFake = new InfectionEventHandler.EpisimFacility(Id.create("fake_facility", ActivityFacility.class));

		odeDiseaseImportCount = 0L;

//		unknownCnt = 0L;


		String odeResultsFilename = episimConfig.getOdeIncidenceFile();
		dayToInfectionShareMap = readOdeInfectionsFile(odeResultsFilename);

		this.facilities = scenario.getActivityFacilities();


		odeDistricts = episimConfig.getOdeDistricts();
		if(odeDistricts == null || odeDistricts.isEmpty() ){
			throw new RuntimeException("There need to be districts defined");
		}

		this.odeInfTargetDistrictActive = episimConfig.getOdeInfTargetDistrict() != null && !episimConfig.getOdeInfTargetDistrict().equals("");

	}

	private static final ThreadLocal<Deque<EpisimPerson>> personPool = ThreadLocal.withInitial(ArrayDeque::new);
	public static final AtomicInteger personCounter = new AtomicInteger(0);  // Thread-safe counter




	public Long getOdeDiseaseImportCount() {
		return odeDiseaseImportCount;
	}

	public void resetOdeDiseaseImportCount() {
		odeDiseaseImportCount = 0L;
	}

//	public Long getUnknownCnt() {
//		return unknownCnt;
//	}
//
//	public void resetUnknownCnt() {
//		unknownCnt = 0L;
//	}

	private EpisimPerson borrowPerson(Attributes sharedAttributes, EpisimReporting reporting) {
		Deque<EpisimPerson> pool = personPool.get();
		EpisimPerson person = pool.poll();
		if (person == null) {
			// No available objects in the pool — create a new one
			Id<Person> personId = Id.createPersonId("fake_" + personCounter.getAndIncrement());
			person = new EpisimPerson(personId, sharedAttributes, reporting);
		} else {
			// Reuse the existing object, resetting its state
			person.getAttributes().putAttribute("age", sharedAttributes.getAttribute("age"));
			person.setDiseaseStatus(0, DiseaseStatus.susceptible);
//			person.getActivities().clear();  // Clear any leftover activities
		}
		return person;
	}

	private void returnPerson(EpisimPerson person) {
		// Optional: sanity check before returning to the pool
		personPool.get().offer(person);
	}


	private Int2DoubleMap readOdeInfectionsFile(String odeResultsFilename) {
		String csvSplitBy = ",";  // Adjust the delimiter if needed

		LocalDate startDate = episimConfig.getStartDate();
		Int2DoubleMap dayToInfectionShareMap = new Int2DoubleOpenHashMap();

		try (BufferedReader br = new BufferedReader(new FileReader(odeResultsFilename))) {
			// Read header and determine the column index for "infectious"
			String headerLine = br.readLine();
			if (headerLine == null) {
				throw new IOException("CSV file is empty or missing a header row.");
			}

			String[] headers = headerLine.split(csvSplitBy);
			int dateIndex = -1;
			int infectiousIndex = -1;

			for (int i = 0; i < headers.length; i++) {
				if ("time".equalsIgnoreCase(headers[i].trim())) {
					dateIndex = i;
				} else if ("infectious".equalsIgnoreCase(headers[i].trim())) {
					infectiousIndex = i;
				}
			}

			if (dateIndex == -1) {
				throw new IOException("Column 'date' not found in CSV file.");
			}

			if (infectiousIndex == -1) {
				throw new IOException("Column 'infectious' not found in CSV file.");
			}


			String line;
			while ((line = br.readLine()) != null) {
				String[] columns = line.split(csvSplitBy);

				if (columns.length <= infectiousIndex) {
					System.err.println("Skipping malformed line: " + line);
					continue;
				}

				try {
					LocalDate date = LocalDate.parse(columns[dateIndex].trim());
					int day = (int) startDate.until(date, ChronoUnit.DAYS);
					double value = Double.parseDouble(columns[infectiousIndex].trim());
					dayToInfectionShareMap.put(day, value);
				} catch (Exception e) {
					System.err.println("Error parsing line: " + line + " - " + e.getMessage());
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to read ODE infections file", e);
		}

		if (dayToInfectionShareMap.size() == 0) {
			throw new RuntimeException("ODE Infection Map has size 0.");
		}

		return dayToInfectionShareMap;
	}

	@Override
	public void infectionDynamicsVehicle(EpisimPerson personLeavingVehicle, InfectionEventHandler.EpisimVehicle vehicle, double now) {
		infectionDynamicsGeneralized(personLeavingVehicle, vehicle, now);
	}

	@Override
	public void infectionDynamicsFacility(EpisimPerson personLeavingFacility, InfectionEventHandler.EpisimFacility facility, double now) {
		infectionDynamicsGeneralized(personLeavingFacility, facility, now);
	}


	//todo: what to do about agents living in berlin moving into brandenburg?
	//todo: what to do about home facilities? So far, they are excluded.
	//todo: what to do about pt vehicles?
	private void infectionDynamicsGeneralized(EpisimPerson personLeavingContainer, EpisimContainer<?> containerReal, double now) {

		// no infection possible if there is only one person
		// not the case anymore --jr 10'24
		// todo should we use "typicalCapacity" or "numSpaces" or "maxGroupSize" or "totalUsers"
		if (iteration == 0) { // || container.getPersons().size() == 1) {
			return;
		}

		if (!personRelevantForTrackingOrInfectionDynamics(now, personLeavingContainer, containerReal, getRestrictions(), rnd)) {
			return;
		}

		// start tracking late as possible because of computational costs
		boolean trackingEnabled = iteration >= trackingAfterDay;


		if (containerReal.getInOdeRegion().equals(EpisimContainer.InOdeRegion.unknown)) {
			determineWhetherInOdeRegion(containerReal);
//			unknownCnt++;
		}


		EpisimContainer<?> container;
		// todo activityFacility != null is an important assumption, excludes trains and homes.

		boolean actInOdeRegion = containerReal.getInOdeRegion().equals(EpisimContainer.InOdeRegion.yes);
		if (actInOdeRegion) {


			if (odeInfTargetDistrictActive) {
				if (!personLeavingContainer.getAttributes().getAttribute("district").equals(episimConfig.getOdeInfTargetDistrict())) {
					return;
				}
			}

			// we are only interested in susceptible commuters being infected by berliners. At this point, there is no 'Rückkopplung' from ABM to ODE
			if (!personLeavingContainer.getDiseaseStatus().equals(DiseaseStatus.susceptible)) {
				return;
			}
			int taskId = containerReal.getTaskId();

			containerFake.setNumSpaces(containerReal.getNumSpaces());
			containerFake.setTaskId(taskId);


			double infShare = dayToInfectionShareMap.getOrDefault(iteration, 0) * episimConfig.getOdeCouplingFactor();

			//share of berlin agents infectious but not yet showing symptoms

			int maxGroupSize = 0;


			// we scale down the capacity by sample size todo: does this give us problems. how should I deal with sample size. Should I just reduce the capacity by the sample?
			//todo: include RF in the future
			// todo: for now, I'm taking the unscaled capacity. Because I don't know if we should really reduce the capacity or remove
//				double numContactPeople = actTypeToCapacityMap.getValue() * episimConfig.getSampleSize();

			int dayCounter = (int) (now / 60. / 60. / 24.);



			Attributes sharedAttributes = new Attributes();
			sharedAttributes.putAttribute("age", personLeavingContainer.getAge());

			for(Map.Entry<String,Double> actTypeToCap : containerReal.getActToOdeContacts().object2DoubleEntrySet()){
				String actType = actTypeToCap.getKey();
				double capacity = actTypeToCap.getValue();

//				if (capacity > 1000) {
//					System.out.println("Warning: unusually high actToOdeContacts capacity: " + capacity);
//					System.out.println(containerReal.getActToOdeContacts());
//
//					continue;
//				}

				for (int i = 0; i < capacity; i++) {
					maxGroupSize++;
					if (rnd.nextDouble() < infShare) {

						//place infected fake berliner agent in container w/ susceptible brandenburger agent
						// assumption: all contact agents have same age as brandenburger agent. // todo: should we put a distribution on this




//						Id<Person> personId = Id.createPersonId("fake_task" + taskId + "_" + i);
						EpisimPerson person = borrowPerson(sharedAttributes, reporting);
//
//						EpisimPerson person = new EpisimPerson(personId, sharedAttributes, reporting);

						containerFake.addPerson(person, 0, new EpisimPerson.PerformedActivity(0, episimConfig.getOrAddContainerParams(actType), null));

						// this do-while loop will add a person, check if they are actually contagious; if not, they'll add another person.
						do {
							progressionModel.removeAgent(person.getPersonId());
							person.setDiseaseStatus(now, DiseaseStatus.infectedButNotContagious);
							//todo: talk to kai: should we this be a distribution of when they become infectious, because infectivity depends on how long they've been infectious.
							progressionModel.updateState(person, dayCounter);
						} while (!person.getDiseaseStatus().equals(DiseaseStatus.contagious));


					}
				}
			}
			//todo: check personLeavingContainer.getActivity(day, now) is correct
			containerFake.addPerson(personLeavingContainer, containerReal.getContainerEnteringTime(personLeavingContainer.getPersonId()), personLeavingContainer.getActivity(day, now));
			//todo we assume everyone is there at the same time, meaning the max group size is all the people.
			double scale = 1 / episimConfig.getSampleSize();
			int maxGroupSizeScaled = (int) (maxGroupSize * scale / containerFake.getNumSpaces());
			containerFake.setMaxGroupSize(maxGroupSizeScaled);
			container = containerFake;

		} else {
			container = containerReal;
		}

		for (EpisimPerson contactPerson : container.getPersons()) {

			// no contact with self, especially no tracing
			if (personLeavingContainer == contactPerson) {
				continue;
			}

			//todo: I assume this will be incorrect for the berlin facilities wherein berlins have been deleted.
			// todo: this gets rounded down, meaning if there are if there are less than 10 agents (in 10% scenario) or 4 agents (in 25% scenario), maxPersonsInContainer is 0.0, meaning the contact intensity is infinity, meaning the infection probability is 0.
			int maxPersonsInContainer = (int) (container.getMaxGroupSize() * episimConfig.getSampleSize());
			// typical size is undefined if no vehicle file is used
			if (container instanceof InfectionEventHandler.EpisimVehicle && container.getTypicalCapacity() > -1) {
				maxPersonsInContainer = (int) (container.getTypicalCapacity() * episimConfig.getSampleSize());
//				maxPersonsInContainer = container.getTypicalCapacity();
//				if ( container.getMaxGroupSize() > container.getTypicalCapacity() ) {
//					log.warn("yyyyyy: vehicleId={}: maxGroupSize={} is larger than typicalCapacity={}; need to find organized answer to this.",
//							container.getContainerId(), container.getMaxGroupSize(), container.getTypicalCapacity() );
//				}
//				log.warn("containerId={}; typical capacity={}; maxPersonsInContainer={}" , container.getContainerId(), container.getTypicalCapacity(), maxPersonsInContainer );
			}

			// it may happen that persons enter and leave an container at the same time
			// effectively they have a joint time of 0 and will not count towards maximum group size
			// still the size of the list of persons in the container may be larger than max group size
			if (maxPersonsInContainer <= 1) {
				log.debug("maxPersonsInContainer is={} even though there are {} persons in container={}", maxPersonsInContainer, container.getPersons().size(), container.getContainerId());
				// maxPersonsInContainer = container.getPersons().size();
			}

			/*
			if (ReplayEventsTask.getThreadRnd(rnd).nextDouble() >= episimConfig.getMaxContacts()/(maxPersonsInContainer-1) ) {
				continue;
			}
			// since every pair of persons interacts only once, there is now a constant interaction probability per pair
			// if we want superspreading events, then maxInteractions needs to be much larger than 3 or 10.

			*/

			double nSpacesPerFacility = container.getNumSpaces();
			if (rnd.nextDouble() > 1. / nSpacesPerFacility) { // i.e. other person is in other space
				continue;
			}

			if (!personRelevantForTrackingOrInfectionDynamics(now, contactPerson, container, getRestrictions(), rnd)) {
				continue;
			}

			// counted as contact
			numContacts++;

			// we have thrown the random numbers, so we can bail out in some cases if we are not tracking:
			if (!trackingEnabled) {
				if (personLeavingContainer.getDiseaseStatus() == DiseaseStatus.infectedButNotContagious) {
					continue;
				}
				if (contactPerson.getDiseaseStatus() == DiseaseStatus.infectedButNotContagious) {
					continue;
				}
				if (personLeavingContainer.getDiseaseStatus() == contactPerson.getDiseaseStatus()) {
					continue;
				}
			} else if (!traceSusceptible && personLeavingContainer.getDiseaseStatus() == DiseaseStatus.susceptible
				&& contactPerson.getDiseaseStatus() == DiseaseStatus.susceptible)
				continue;

			// activity params of the contact person and leaving person
			EpisimConfigGroup.InfectionParams leavingParams = getInfectionParams(container, personLeavingContainer, container.getPerformedActivity(personLeavingContainer.getPersonId()));
			EpisimConfigGroup.InfectionParams contactParams = getInfectionParams(container, contactPerson, container.getPerformedActivity(contactPerson.getPersonId()));

			String leavingPersonsActivity = leavingParams == qhParams ? "home" : leavingParams.getContainerName();
			String otherPersonsActivity = contactParams == qhParams ? "home" : contactParams.getContainerName();

			StringBuilder infectionType = getInfectionType(buffer, container, leavingPersonsActivity, otherPersonsActivity);

			double containerEnterTimeOfPersonLeaving = container.getContainerEnteringTime(personLeavingContainer.getPersonId());
			double containerEnterTimeOfOtherPerson = container.getContainerEnteringTime(contactPerson.getPersonId());
			double jointTimeInContainer = calculateJointTimeInContainer(now, leavingParams, containerEnterTimeOfPersonLeaving, containerEnterTimeOfOtherPerson);

			//forbid certain cross-activity interactions, keep track of contacts
			if (container instanceof InfectionEventHandler.EpisimFacility) {
				//home can only interact with home, leisure or work
				if (infectionType.indexOf("home") >= 0 && infectionType.indexOf("leis") == -1 && infectionType.indexOf("work") == -1
					&& !(leavingPersonsActivity.startsWith("home") && otherPersonsActivity.startsWith("home"))) {
					// yyyyyy we need to move out of these string convention based rules in code.  kai, aug'20
					continue;
				} else if (infectionType.indexOf("edu") >= 0 && infectionType.indexOf("work") == -1 && !(leavingPersonsActivity.startsWith("edu") && otherPersonsActivity.startsWith("edu"))) {
					//edu can only interact with work or edu
					// yyyyyy we need to move out of these string convention based rules in code.  kai, aug'20
					continue;
				}
				if (trackingEnabled) {
					trackContactPerson(personLeavingContainer, contactPerson, now, jointTimeInContainer, infectionType);
				}

				// Only a subset of contacts are reported at the moment
				// tracking has to be enabled to report more contacts
				reporting.reportContact(now, personLeavingContainer, contactPerson, container, infectionType, jointTimeInContainer);
			}

			if (!AbstractContactModel.personsCanInfectEachOther(personLeavingContainer, contactPerson)) {
				continue;
			}

			// person can only infect others x days after being contagious
			if ((personLeavingContainer.hadDiseaseStatus(DiseaseStatus.contagious) &&
				personLeavingContainer.daysSince(DiseaseStatus.contagious, iteration) > episimConfig.getDaysInfectious())
				|| (contactPerson.hadDiseaseStatus(DiseaseStatus.contagious) &&
				contactPerson.daysSince(DiseaseStatus.contagious, iteration) > episimConfig.getDaysInfectious()))
				continue;

			// persons leaving their first-ever activity have no starting time for that activity.  Need to hedge against that.  Since all persons
			// start healthy (the first seeds are set at enterVehicle), we can make some assumptions.
			if (containerEnterTimeOfPersonLeaving < 0 && containerEnterTimeOfOtherPerson < 0) {
				throw new IllegalStateException("should not happen");
				// should only happen at first activity.  However, at first activity all persons are susceptible.  So the only way we
				// can get here is if an infected person entered the container and is now leaving again, while the other person has been in the
				// container from the beginning.  ????  kai, mar'20
			}

			if (jointTimeInContainer < 0 || jointTimeInContainer > 86400 * 18) {
				log.warn(containerEnterTimeOfPersonLeaving);
				log.warn(containerEnterTimeOfOtherPerson);
				log.warn(now);
				throw new IllegalStateException("joint time in container is not plausible for personLeavingContainer=" + personLeavingContainer.getPersonId() + " and contactPerson=" + contactPerson.getPersonId() + ". Joint time is=" + jointTimeInContainer);
			}

			// (same computation as above; could just memorize)
			// this is currently 1 / (sqmPerPerson * airExchangeRate).  Need to multiply sqmPerPerson with maxPersonsInSpace to obtain room size:
			//todo: talk to KN
			double contactIntensity = Math.min(
				leavingParams.getContactIntensity() / (maxPersonsInContainer / leavingParams.getSpacesPerFacility()),
				contactParams.getContactIntensity() / (maxPersonsInContainer / nSpacesPerFacility)
			);

			// need to differentiate which person might be the infector
			if (personLeavingContainer.getDiseaseStatus() == DiseaseStatus.susceptible) {

				double prob = infectionModel.calcInfectionProbability(personLeavingContainer, contactPerson, getRestrictions(),
					leavingParams, contactParams, contactIntensity, jointTimeInContainer);

				double probUnVac = infectionModel.getLastUnVacInfectionProbability();

				double dbl = rnd.nextDouble();

//				testDayToActualInfChanceSum.merge(iteration, dbl, Double::sum);
//				testDayToActualInfChanceCnt.merge(iteration, 1, Integer::sum);

				// todo, should this only apply to non-berlin infections? Or also to the initial infections in the ODE Area?
				potentialInfection(personLeavingContainer, contactPerson, now, infectionType, prob, container, probUnVac, dbl);

				if (dbl < prob) {

					infectPerson(personLeavingContainer, contactPerson, now, infectionType, prob, container);

					if (actInOdeRegion)
						odeDiseaseImportCount++;

				}


			} else {

				double prob = infectionModel.calcInfectionProbability(contactPerson, personLeavingContainer, getRestrictions(),
					contactParams, leavingParams, contactIntensity, jointTimeInContainer);

				double probUnVac = infectionModel.getLastUnVacInfectionProbability();

				double dbl = rnd.nextDouble();

				potentialInfection(contactPerson, personLeavingContainer, now, infectionType, prob, container, probUnVac, dbl);

				if (dbl < prob)
					infectPerson(contactPerson, personLeavingContainer, now, infectionType, prob, container);
			}
		}

		if (actInOdeRegion) {


			Iterator<EpisimPerson> iterator = container.getPersons().iterator();
			while (iterator.hasNext()) {
				EpisimPerson person = iterator.next();
				if (person.getPersonId().toString().startsWith("fake")) {
					returnPerson(person);
					((AbstractProgressionModel) progressionModel).removeAgent(person.getPersonId());
					iterator.remove();  // Properly remove from the list
				}
			}
			containerFake.getPersons().clear();
			containerFake.setMaxGroupSize(0);  // Optional, in case scaling persists
			container = null;

		}

	}

	private void determineWhetherInOdeRegion(EpisimContainer<?> containerReal) {
		String idString = containerReal.getContainerId().toString();

		ActivityFacility activityFacility = facilities.getFacilities().get(Id.create(idString, ActivityFacility.class));
		// this doesn't include home facilities that only show up in the population file and not in the events files...
		if (activityFacility == null) {
			if (idString.startsWith("home_")) {
				idString = idString.replaceFirst("home_", "");
				idString = idString.replaceFirst("_split\\d*", "");
				activityFacility = facilities.getFacilities().get(Id.create(idString, ActivityFacility.class));
				if (activityFacility == null) {
//					throw new RuntimeException("we have a home facility that is not found in facilities file:" + idString );
				}
			} else {
				if (!idString.startsWith("tr_")) {
//					throw new RuntimeException("we have a unidentifiable facility that is not a train");
				}
			}
		}


		if (activityFacility == null || activityFacility.getAttributes() == null || !activityFacility.getAttributes().getAsMap().containsKey("district")) {
			containerReal.setInOdeRegion(EpisimContainer.InOdeRegion.no);
			return;
		}

		String district = (String) activityFacility.getAttributes().getAttribute("district");
		if (district != null && odeDistricts.contains(district)) {
			containerReal.setInOdeRegion(EpisimContainer.InOdeRegion.yes);

			Object2DoubleMap<String> actTypeToCapacityMap = new Object2DoubleOpenHashMap<>();

			for (ActivityOption activityOption : activityFacility.getActivityOptions().values()) {
				String actType = activityOption.getType();
				if (Objects.equals(actType, "shopping")) {
					actType = "shop_other";
				} else if (Objects.equals(actType, "restaurant")) {
					actType = "leisure";
				}

				actTypeToCapacityMap.put(actType, activityOption.getCapacity());

			}

			containerReal.setActToOdeContacts(actTypeToCapacityMap);

		} else {
			containerReal.setInOdeRegion(EpisimContainer.InOdeRegion.no);
		}

	}

}

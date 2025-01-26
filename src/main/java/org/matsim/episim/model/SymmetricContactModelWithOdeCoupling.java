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
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

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

//	private final Map<String, Map<String, Double>> facilityToCapacityMap;
//
//	private final Map<String,Boolean> facilityToBerlinMap;
	private final ProgressionModel progressionModel;
	private final EpisimConfigGroup episimConfigGroup;
	private Int2DoubleMap dayToInfectionShareMap;

	public static final Path INPUT = EpisimUtils.resolveInputPath("../shared-svn/projects/episim/matsim-files/snz/Brandenburg/episim-input");
	private EpisimContainer<ActivityFacility> containerFake;

	private Map<Id<Person>, EpisimPerson> fakePersonPool;
	private Long odeDiseaseImportCount;

//	public Int2IntMap testDayToTotalCapacityMap = new Int2IntAVLTreeMap();
//	public Int2DoubleMap testDayToInfChanceSum = new Int2DoubleAVLTreeMap();
//	public Int2IntMap testDayToInfChanceCnt = new Int2IntAVLTreeMap();
//	public Int2DoubleMap testDayToActualInfChanceSum = new Int2DoubleAVLTreeMap();
//	public Int2IntMap testDayToActualInfChanceCnt = new Int2IntAVLTreeMap();
//




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
		dayToInfectionShareMap = new Int2DoubleOpenHashMap();
		facilities = scenario.getActivityFacilities();

		odeDiseaseImportCount = 0L;
		// Reads in Disease Import from ODE
		//TODO: shouldn't be hardcoded
		{
			String odeResultsFilename = INPUT.resolve("ode_inputs/left_s.csv").toString();
			String line;
			String csvSplitBy = ",";  // Change this to the appropriate delimiter

			// so far, the ode gives a share of all infected agents. What we actually need is contagiousButNotShowingSymptoms. Assuming the infectious also includes those quarentined at home

			LocalDate startDate = episimConfig.getStartDate();
			try (BufferedReader br = new BufferedReader(new FileReader(odeResultsFilename))) {
				br.readLine();
				while ((line = br.readLine()) != null) {
					// Split line into columns
					String[] columns = line.split(csvSplitBy);

					// Assuming you want to map column 1 as key and column 2 as value
					LocalDate date = LocalDate.parse(columns[0]);
					int day = (int) startDate.until(date, ChronoUnit.DAYS);
					double value = Double.parseDouble(columns[3]);
					dayToInfectionShareMap.put(day, value);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}



//		facilities = new ActivityFacilitiesImpl();
//		episimConfig.getFacilitiesFile();
//		new MatsimFacilitiesReader("EPSG:25833", "EPSG:25833", facilities).readFile(episimConfig.getFacilitiesFile());//"../shared-svn/projects/episim/matsim-files/snz/Brandenburg/episim-input/samples/br_2020-week_snz_episim_facilities_withDistricts_10pt.xml.gz");

//
//		this.facilityToCapacityMap = new HashMap<>();
//		this.facilityToBerlinMap = new HashMap<>();
//

//		for (ActivityFacility facility : facilities.getFacilities().values()) {
//			Map<String, Double> facilityMap = new HashMap<>();
//			for (ActivityOption value : facility.getActivityOptions().values()) {
//				String actType = value.getType();
//
//				// todo: should they all be shop_daily or also shop_other
//				if (Objects.equals(actType, "shopping")) {
//					actType = "shop_other";
//				} else if (Objects.equals(actType, "restaurant")) {
//					actType = "leisure";
//				}
//
//				double capacity = value.getCapacity();
//				facilityMap.put(actType, capacity);
//			}
//			String idString = facility.getId().toString();
//			facilityToCapacityMap.put(idString, facilityMap);
//			facilityToBerlinMap.put(idString, (boolean) facility.getAttributes().getAttribute("berlin"));
//		}


	}

	public Long getOdeDiseaseImportCount() {
		return odeDiseaseImportCount;
	}

	public void resetOdeDiseaseImportCount() {
		odeDiseaseImportCount = 0L;
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

		String idString = containerReal.getContainerId().toString();

		ActivityFacility activityFacility = facilities.getFacilities().get(Id.create(idString, ActivityFacility.class));

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


		EpisimContainer<?> container;
		// todo activityFacility != null is an important assumption, excludes trains and homes.

		boolean berlin = activityFacility != null &&
			activityFacility.getAttributes().getAsMap().containsKey("district") && //todo check this assumption
			activityFacility.getAttributes().getAttribute("district").equals("Berlin");
		if (berlin) {

			if (episimConfig.getOdeCouplingDistrict() != null && !episimConfig.getOdeCouplingDistrict().equals("")) {
				if (!personLeavingContainer.getAttributes().getAttribute("district").equals(episimConfig.getOdeCouplingDistrict())) {
					return;
				}
			}

//			personLeavingContainer.getAttributes().putAttribute("berlin", true);

				// we are only interested in susceptible commuters being infected by berliners. At this point, there is no 'Rückkopplung' from ABM to ODE
			if (!personLeavingContainer.getDiseaseStatus().equals(DiseaseStatus.susceptible)) {
				return;
			}
			int taskId = containerReal.getTaskId();

			containerFake.setNumSpaces(containerReal.getNumSpaces());
			containerFake.setTaskId(taskId);

			//share of berlin agents infectious but not yet showing symptoms

			int maxGroupSize = 0;
			// todo: how should I deal with sample size. Should I just reduce the capacity by the sample?
//			for (Map.Entry<String, Double> actTypeToCapacityMap : facilityToCapacityMap.get(idString).entrySet()) {

			for (ActivityOption activityOption : facilities.getFacilities().get(Id.create(idString, ActivityFacility.class)).getActivityOptions().values()) {

				String actType = activityOption.getType();
				if (Objects.equals(actType, "shopping")) {
					actType = "shop_other";
				} else if (Objects.equals(actType, "restaurant")) {
					actType = "leisure";
				}
				// we scale down the capacity by sample size todo: does this give us problems
				//todo: include RF in the future
				// todo: for now, I'm taking the unscaled capacity. Because I don't know if we should really reduce the capacity or remove
//				double numContactPeople = actTypeToCapacityMap.getValue() * episimConfig.getSampleSize();
				double numContactPeople = activityOption.getCapacity();
//				System.out.println(fakePersonPool.size());
				int dayCounter = (int) (now / 60. / 60. / 24.);

				double infShare = dayToInfectionShareMap.getOrDefault(iteration, 0) * episimConfig.getOdeCouplingFactor();

//
//				testDayToTotalCapacityMap.merge(iteration, (int) numContactPeople, Integer::sum);
//
//				testDayToInfChanceSum.merge(iteration, infShare, Double::sum);
//				testDayToInfChanceCnt.merge(iteration, 1, Integer::sum);
//

				for (int i = 0; i < numContactPeople; i++) {
					maxGroupSize++;
					if (rnd.nextDouble() < infShare) {

						//place infected fake berliner agent in container w/ susceptible brandenburger agent


						// assumption: all contact agents have same age as brandenburger agent. // todo: should we put a distribution on this


						// this do-while loop will add a person, check if they are actually contagious; if not, they'll add another person.
						//						do { //todo

						Attributes attributes = new Attributes();
						attributes.putAttribute("age", personLeavingContainer.getAge());

						Id<Person> personId = Id.createPersonId("fake_task" + taskId + "_" + i);
//						EpisimPerson person = fakePersonPool.getOrDefault(personId, new EpisimPerson(personId, attributes, reporting));
//						person.getAttributes().putAttribute("age", personLeavingContainer.getAge());
//						fakePersonPool.putIfAbsent(personId, person);

						EpisimPerson person = new EpisimPerson(personId, attributes, reporting);

						containerFake.addPerson(person, 0, new EpisimPerson.PerformedActivity(0, episimConfig.getOrAddContainerParams(actType), activityFacility.getId()));

						do {
//							if (progressionModel.containsAgent(person.getPersonId())) {
							progressionModel.removeAgent(person.getPersonId());
//							}
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

		} else{
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
			EpisimConfigGroup.InfectionParams leavingParams = getInfectionParams(container, personLeavingContainer,  container.getPerformedActivity(personLeavingContainer.getPersonId()));
			EpisimConfigGroup.InfectionParams contactParams = getInfectionParams(container, contactPerson,  container.getPerformedActivity(contactPerson.getPersonId()));

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

					if(berlin)
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

		if(berlin){


			for (EpisimPerson person : container.getPersons()) {

				if (person.getPersonId().toString().startsWith("fake")) {
					((AbstractProgressionModel) progressionModel).removeAgent(person.getPersonId());
					person = null;
				}
			}
//			if(((ConfigurableProgressionModel) progressionModel).nextStateAndDay.size() > 0){
//				System.out.println("heh????");
//			}
			containerFake.getPersons().clear();
			container = null;
		}

	}

}

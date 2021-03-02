package org.matsim.episim;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.model.ContactModel;
import org.matsim.episim.policy.Restriction;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import javax.inject.Named;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * Executes trajectory of a person using the events.
 */
final class TrajectoryHandler {

	private static final Logger log = LogManager.getLogger(TrajectoryHandler.class);

	private final EpisimConfigGroup episimConfig;
	private final EpisimReporting reporting;
	private final ContactModel contactModel;
	private final Map<Id<Person>, EpisimPerson> personMap;
	private final Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicleMap;
	private final Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> pseudoFacilityMap;

	/**
	 * The "local" random instance, used for all submodels.
	 */
	private final SplittableRandom rnd;

	private int iteration = 0;

	@Inject
	public TrajectoryHandler(EpisimConfigGroup episimConfig, EpisimReporting reporting, ContactModel model, SplittableRandom rnd,
							 @Named("personMap") Map<Id<Person>, EpisimPerson> personMap,
							 @Named("vehicleMap") Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicleMap,
							 @Named("pseudoFacilityMap") Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> pseudoFacilityMap) {
		this.rnd = rnd;
		this.episimConfig = episimConfig;
		this.reporting = reporting;
		this.contactModel = model;
		this.personMap = personMap;
		this.vehicleMap = vehicleMap;
		this.pseudoFacilityMap = pseudoFacilityMap;
	}

	SplittableRandom getRnd() {
		return rnd;
	}

	void setRestrictionsForIteration(int iteration, ImmutableMap<String, Restriction> im) {
		this.iteration = iteration;
		contactModel.setRestrictionsForIteration(iteration, im);
	}

	/**
	 * Handle plans with "holes" in their trajectory.
	 *
	 * @param day day that is about to start
	 * @param responsible used for partitioning of trajectory handlers
	 */
	void checkAndHandleEndOfNonCircularTrajectory(EpisimPerson person, DayOfWeek day, Predicate<Id<?>> responsible) {

		Id<ActivityFacility> lastFacilityId = person.getLastFacilityId(day.minus(1));
		Id<ActivityFacility> firstFacilityId = person.getFirstFacilityId(day);

		// now is the start of current day, when this is called iteration still has the value of the last day
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, iteration + 1);

		// TODO: are unclosed trajectories with PT possible?

		if (!lastFacilityId.equals(firstFacilityId)) {
			InfectionEventHandler.EpisimFacility lastFacility = this.pseudoFacilityMap.get(lastFacilityId);

			// index of last activity at previous day
			String actType = person.getActivity(day.minus(1), 24 * 3600.).actType;
			double timeSpent = now - lastFacility.getContainerEnteringTime(person.getPersonId());
			person.addSpentTime(actType, timeSpent);

			if (iteration > 1 && timeSpent > 86400 && !actType.equals("home")) {
				// there might be some implausible trajectories
				log.trace("{} spent {} outside home", person, timeSpent);
			}

			if (responsible.test(lastFacilityId)) {
				contactModel.infectionDynamicsFacility(person, lastFacility, now, actType);
				lastFacility.removePerson(person);
			}

			if (responsible.test(firstFacilityId)) {
				InfectionEventHandler.EpisimFacility firstFacility = this.pseudoFacilityMap.get(firstFacilityId);
				firstFacility.addPerson(person, now);

				contactModel.notifyEnterFacility(person, firstFacility, now);
			}


		} else {
			// TODO: check if still needed
			InfectionEventHandler.EpisimFacility firstFacility = this.pseudoFacilityMap.get(firstFacilityId);

			if (responsible.test(firstFacility.getContainerId())) {
				firstFacility.addPerson(person, now);
				contactModel.notifyEnterFacility(person, firstFacility, now);
			}
		}
	}

	/**
	 * Called of start of day before any handleEvent method.
	 * @param responsible predicate for checking if the handler is responsible for a certain facility
	 */
	public void onStartDay(Predicate<Id<?>> responsible) {

		DayOfWeek day = EpisimUtils.getDayOfWeek(episimConfig, iteration);

		for (EpisimPerson person : personMap.values()) {

			checkAndHandleEndOfNonCircularTrajectory(person, day, responsible);
		}
	}

	public void handleEvent(ActivityStartEvent activityStartEvent) {
//		double now = activityStartEvent.getTime();
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), activityStartEvent.getTime(), iteration);

		reporting.handleEvent(activityStartEvent);

		// find the person:
		EpisimPerson episimPerson = this.personMap.get(activityStartEvent.getPersonId());

		// create pseudo facility id that includes the activity type:
		Id<ActivityFacility> episimFacilityId = activityStartEvent.getFacilityId();

		// find the facility
		InfectionEventHandler.EpisimFacility episimFacility = this.pseudoFacilityMap.get(episimFacilityId);

		// add person to facility
		episimFacility.addPerson(episimPerson, now);

		contactModel.notifyEnterFacility(episimPerson, episimFacility, now);
	}

	public void handleEvent(ActivityEndEvent activityEndEvent) {
//		double now = activityEndEvent.getTime();
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), activityEndEvent.getTime(), iteration);

		reporting.handleEvent(activityEndEvent);

		EpisimPerson episimPerson = this.personMap.get(activityEndEvent.getPersonId());
		// create pseudo facility id that includes the activity type:
		Id<ActivityFacility> episimFacilityId = activityEndEvent.getFacilityId();

		// find the facility
		InfectionEventHandler.EpisimFacility episimFacility = this.pseudoFacilityMap.get(episimFacilityId);

		contactModel.infectionDynamicsFacility(episimPerson, episimFacility, now, activityEndEvent.getActType());

		double timeSpent = now - episimFacility.getContainerEnteringTime(episimPerson.getPersonId());
		episimPerson.addSpentTime(activityEndEvent.getActType(), timeSpent);

		episimFacility.removePerson(episimPerson);
	}

	public void handleEvent(PersonEntersVehicleEvent entersVehicleEvent) {
//		double now = entersVehicleEvent.getTime();
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), entersVehicleEvent.getTime(), iteration);

		reporting.handleEvent(entersVehicleEvent);

		// find the person:
		EpisimPerson episimPerson = this.personMap.get(entersVehicleEvent.getPersonId());

		// find the vehicle:
		InfectionEventHandler.EpisimVehicle episimVehicle = this.vehicleMap.get(entersVehicleEvent.getVehicleId());

		// add person to vehicle and memorize entering time:
		episimVehicle.addPerson(episimPerson, now);

		contactModel.notifyEnterVehicle(episimPerson, episimVehicle, now);
	}

	public void handleEvent(PersonLeavesVehicleEvent leavesVehicleEvent) {
//		double now = leavesVehicleEvent.getTime();
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), leavesVehicleEvent.getTime(), iteration);

		reporting.handleEvent(leavesVehicleEvent);

		// find vehicle:
		InfectionEventHandler.EpisimVehicle episimVehicle = this.vehicleMap.get(leavesVehicleEvent.getVehicleId());

		EpisimPerson episimPerson = this.personMap.get(leavesVehicleEvent.getPersonId());

		contactModel.infectionDynamicsVehicle(episimPerson, episimVehicle, now);

		double timeSpent = now - episimVehicle.getContainerEnteringTime(episimPerson.getPersonId());

		// This type depends on the params defined in the scenario
		episimPerson.addSpentTime("pt", timeSpent);

		// remove person from vehicle:
		episimVehicle.removePerson(episimPerson);
	}
}

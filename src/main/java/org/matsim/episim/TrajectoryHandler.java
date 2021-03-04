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
import java.util.Iterator;
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
	 * <p>
	 * In the data, activity start events at the beginning can be missing.
	 * Likewise, activity end events at the end of a day might be missing.
	 * This leads to implicitly given first and last containers of the day.
	 *
	 * @param day         day that is about to start
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
	 *
	 * @param responsible predicate for checking if the handler is responsible for a certain facility
	 */
	public void onStartDay(Predicate<Id<?>> responsible) {

		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, iteration);
		DayOfWeek day = EpisimUtils.getDayOfWeek(episimConfig, iteration);
		DayOfWeek prevDay = day.minus(1);

		// remove person from facilities that
		for (InfectionEventHandler.EpisimFacility facility : pseudoFacilityMap.values()) {
			if (!responsible.test(facility.getContainerId()))
				continue;

			Iterator<EpisimPerson> it = facility.getPersons().iterator();

			while (it.hasNext()) {

				EpisimPerson person = it.next();

				// person needs to be at a different container and is removed here
				if (person.getFirstFacilityId(day) != person.getLastFacilityId(prevDay)) {

					// get the last activity of the day
					EpisimPerson.Activity lastActivity = person.getActivity(prevDay, 86400);

					double timeSpent = now - facility.getContainerEnteringTime(person.getPersonId());
					person.addSpentTime(lastActivity.actType, timeSpent);

					contactModel.infectionDynamicsFacility(person, facility, now, lastActivity.actType);
					facility.removePerson(person, it);
				}
			}
		}

		// all persons still in vehicles are removed at the end of the day
		for (InfectionEventHandler.EpisimVehicle vehicle : vehicleMap.values()) {
			if (!responsible.test(vehicle.getContainerId()))
				continue;

			Iterator<EpisimPerson> it = vehicle.getPersons().iterator();
			while (it.hasNext()) {
				EpisimPerson person = it.next();
				contactModel.infectionDynamicsVehicle(person, vehicle, now);
				vehicle.removePerson(person, it);
			}
		}


		for (EpisimPerson person : personMap.values()) {

			if (person.getFirstFacilityId(day) == null)
				continue;

			Id<ActivityFacility> firstFacilityId = person.getFirstFacilityId(day);
			InfectionEventHandler.EpisimFacility facility = pseudoFacilityMap.get(firstFacilityId);

			if (!responsible.test(firstFacilityId))
				continue;

			if (!facility.containsPerson(person)) {

				facility.addPerson(person, now);
				contactModel.notifyEnterFacility(person, facility, now);

			}
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

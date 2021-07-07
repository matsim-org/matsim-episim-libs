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
	private DayOfWeek day;

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
		this.day = EpisimUtils.getDayOfWeek(episimConfig, iteration);
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
	@Deprecated
	void checkAndHandleEndOfNonCircularTrajectory(EpisimPerson person, DayOfWeek day, Predicate<Id<?>> responsible) {

		Id<ActivityFacility> lastFacilityId = person.getLastFacilityId(day.minus(1));
		Id<ActivityFacility> firstFacilityId = person.getFirstFacilityId(day);

		// now is the start of current day, when this is called iteration still has the value of the last day
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, iteration + 1);

		// TODO: are unclosed trajectories with PT possible?

		if (!lastFacilityId.equals(firstFacilityId)) {
			InfectionEventHandler.EpisimFacility lastFacility = this.pseudoFacilityMap.get(lastFacilityId);

			// index of last activity at previous day
			String actType = person.getActivity(day.minus(1), 24 * 3600.).actType();
			double timeSpent = now - lastFacility.getContainerEnteringTime(person.getPersonId());
			person.addSpentTime(actType, timeSpent);

			if (iteration > 1 && timeSpent > 86400 && !actType.equals("home")) {
				// there might be some implausible trajectories
				log.trace("{} spent {} outside home", person, timeSpent);
			}

			if (responsible.test(lastFacilityId)) {
				contactModel.infectionDynamicsFacility(person, lastFacility, now);
				lastFacility.removePerson(person);
			}

			if (responsible.test(firstFacilityId)) {
				InfectionEventHandler.EpisimFacility firstFacility = this.pseudoFacilityMap.get(firstFacilityId);
				firstFacility.addPerson(person, now, person.getFirstActivity(day));

				contactModel.notifyEnterFacility(person, firstFacility, now);
			}


		} else {
			// TODO: check if still needed
			InfectionEventHandler.EpisimFacility firstFacility = this.pseudoFacilityMap.get(firstFacilityId);

			if (responsible.test(firstFacility.getContainerId())) {
				firstFacility.addPerson(person, now, person.getFirstActivity(day));
				contactModel.notifyEnterFacility(person, firstFacility, now);
			}
		}
	}

	/**
	 * Called of start of day before any handleEvent method.
	 *
	 * @param responsible predicate for checking if the handler is responsible for a certain facility
	 */
	public void onStartDay(Predicate<Id<ActivityFacility>> responsibleFacility,
						   Predicate<Id<Vehicle>> responsibleVehicle) {

		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, iteration);
		DayOfWeek day = EpisimUtils.getDayOfWeek(episimConfig, iteration);

		// need to use previous as in config
		DayOfWeek prevDay = EpisimUtils.getDayOfWeek(episimConfig, iteration - 1);

		for (InfectionEventHandler.EpisimFacility facility : pseudoFacilityMap.values()) {
			if (!responsibleFacility.test(facility.getContainerId()))
				continue;

			facility.resetContagiousCounter();

			Iterator<EpisimPerson> it = facility.getPersons().iterator();

			while (it.hasNext()) {

				EpisimPerson person = it.next();

				assert facility.getContainerId().equals(person.getLastFacilityId(prevDay)) :
						String.format("Person %s needs to be in its last facility (%s) at the end of the day, but is in %s",
								person.getPersonId(), person.getLastFacilityId(prevDay), facility.getContainerId());

				// person needs to be at a different container and is removed here
				if (person.getStaysInContainer(prevDay) && !person.getLastFacilityId(prevDay).equals(person.getFirstFacilityId(day))) {

					EpisimPerson.PerformedActivity lastActivity = facility.getPerformedActivity(person.getPersonId());

					double timeSpent = now - facility.getContainerEnteringTime(person.getPersonId());
					person.addSpentTime(lastActivity.actType(), timeSpent);

					contactModel.infectionDynamicsFacility(person, facility, now);
					facility.removePerson(person, it);
				} else if (person.infectedButNotSerious())
					facility.countContagious(1);
			}
		}

		// all persons still in vehicles are removed at the end of the day
		for (InfectionEventHandler.EpisimVehicle vehicle : vehicleMap.values()) {
			if (!responsibleVehicle.test(vehicle.getContainerId()))
				continue;

			Iterator<EpisimPerson> it = vehicle.getPersons().iterator();
			while (it.hasNext()) {
				EpisimPerson person = it.next();
				contactModel.infectionDynamicsVehicle(person, vehicle, now);
				vehicle.removePerson(person, it);
			}
		}


		for (EpisimPerson person : personMap.values()) {

			Id<ActivityFacility> firstFacilityId = person.getFirstFacilityId(day);
			InfectionEventHandler.EpisimFacility firstFacility = pseudoFacilityMap.get(firstFacilityId);

			if (!responsibleFacility.test(firstFacilityId))
				continue;

			if (!person.checkFirstActivity(day, 0))
				continue;

			if (!person.getStaysInContainer(prevDay) || !person.getLastFacilityId(prevDay).equals(firstFacilityId)) {
				firstFacility.addPerson(person, now, person.getFirstActivity(day));
				contactModel.notifyEnterFacility(person, firstFacility, now);
			}
		}
	}

	/**
	 * Checks whether this person does perform the activity at {@code time}
	 */
	private boolean checkParticipation(EpisimPerson person, double time) {
		if (episimConfig.getActivityHandling() == EpisimConfigGroup.ActivityHandling.duringContact)
			return true;

		return person.checkActivity(day, time);
	}

	private boolean checkVehicleUsage(EpisimPerson person, double time) {
		if (episimConfig.getActivityHandling() == EpisimConfigGroup.ActivityHandling.duringContact)
			return true;

		return person.checkActivity(day, time) && person.checkNextActivity(day, time);
	}

	public void handleEvent(ActivityStartEvent activityStartEvent) {
//		double now = activityStartEvent.getTime();
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), activityStartEvent.getTime(), iteration);

		// find the person:
		EpisimPerson episimPerson = this.personMap.get(activityStartEvent.getPersonId());

		if (!checkParticipation(episimPerson, activityStartEvent.getTime()))
			return;

		reporting.handleEvent(activityStartEvent);

		// create pseudo facility id that includes the activity type:
		Id<ActivityFacility> episimFacilityId = activityStartEvent.getFacilityId();

		// find the facility
		InfectionEventHandler.EpisimFacility episimFacility = this.pseudoFacilityMap.get(episimFacilityId);

		// add person to facility
		episimFacility.addPerson(episimPerson, now, episimPerson.getActivity(day, activityStartEvent.getTime()));

		contactModel.notifyEnterFacility(episimPerson, episimFacility, now);
	}

	public void handleEvent(ActivityEndEvent activityEndEvent) {
//		double now = activityEndEvent.getTime();
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), activityEndEvent.getTime(), iteration);

		EpisimPerson episimPerson = this.personMap.get(activityEndEvent.getPersonId());

		// create pseudo facility id that includes the activity type:
		Id<ActivityFacility> episimFacilityId = activityEndEvent.getFacilityId();

		// find the facility
		InfectionEventHandler.EpisimFacility episimFacility = this.pseudoFacilityMap.get(episimFacilityId);

		// person did not perform this activity
		if (episimConfig.getActivityHandling() == EpisimConfigGroup.ActivityHandling.startOfDay && !episimFacility.containsPerson(episimPerson))
			return;

		reporting.handleEvent(activityEndEvent);

		if (episimConfig.getContagiousOptimization() == EpisimConfigGroup.ContagiousOptimization.no ||
		    episimFacility.containsContagious()) {
			contactModel.infectionDynamicsFacility(episimPerson, episimFacility, now);
		}

		if (episimConfig.getReportTimeUse() == EpisimConfigGroup.ReportTimeUse.yes) {
			double timeSpent = now - episimFacility.getContainerEnteringTime(episimPerson.getPersonId());
			episimPerson.addSpentTime(activityEndEvent.getActType(), timeSpent);
		}

		episimFacility.removePerson(episimPerson);
	}

	public void handleEvent(PersonEntersVehicleEvent entersVehicleEvent) {
//		double now = entersVehicleEvent.getTime();
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), entersVehicleEvent.getTime(), iteration);

		// find the person:
		EpisimPerson episimPerson = this.personMap.get(entersVehicleEvent.getPersonId());

		if (!checkVehicleUsage(episimPerson, entersVehicleEvent.getTime()))
			return;

		reporting.handleEvent(entersVehicleEvent);

		// find the vehicle:
		InfectionEventHandler.EpisimVehicle episimVehicle = this.vehicleMap.get(entersVehicleEvent.getVehicleId());

		// add person to vehicle and memorize entering time:
		episimVehicle.addPerson(episimPerson, now, EpisimPerson.UNSPECIFIC_ACTIVITY);

		contactModel.notifyEnterVehicle(episimPerson, episimVehicle, now);
	}

	public void handleEvent(PersonLeavesVehicleEvent leavesVehicleEvent) {
//		double now = leavesVehicleEvent.getTime();
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), leavesVehicleEvent.getTime(), iteration);

		// find vehicle:
		InfectionEventHandler.EpisimVehicle episimVehicle = this.vehicleMap.get(leavesVehicleEvent.getVehicleId());

		EpisimPerson episimPerson = this.personMap.get(leavesVehicleEvent.getPersonId());

		// person did not enter the vehicle
		if (episimConfig.getActivityHandling() == EpisimConfigGroup.ActivityHandling.startOfDay && !episimVehicle.containsPerson(episimPerson))
			return;

		reporting.handleEvent(leavesVehicleEvent);

		if (episimConfig.getContagiousOptimization() == EpisimConfigGroup.ContagiousOptimization.no || 
			episimVehicle.containsContagious()) {
			contactModel.infectionDynamicsVehicle(episimPerson, episimVehicle, now);
		}


		if (episimConfig.getReportTimeUse() == EpisimConfigGroup.ReportTimeUse.yes) {
			double timeSpent = now - episimVehicle.getContainerEnteringTime(episimPerson.getPersonId());

			episimPerson.addSpentTime("pt", timeSpent);
		}

		// remove person from vehicle:
		episimVehicle.removePerson(episimPerson);
	}

	public void reportCpuTime(String what, int taskId) {
		reporting.reportCpuTime(iteration, "TrajectoryHandler", what, taskId);
	}

	public InfectionEventHandler.EpisimFacility getEpisimFacility(Id<ActivityFacility> id) {
		return this.pseudoFacilityMap.get(id);
	}

	public InfectionEventHandler.EpisimVehicle getEpisimVehicle(Id<Vehicle> id) {
		return this.vehicleMap.get(id);
	}
}


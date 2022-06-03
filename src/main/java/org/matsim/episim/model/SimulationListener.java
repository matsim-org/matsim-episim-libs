package org.matsim.episim.model;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import java.time.LocalDate;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Listener to interact with the simulation before starting
 */
public interface SimulationListener {

	/**
	 * Called after init before the simulation starts.
	 *
	 * @param rnd        local random instance of the event handler
	 * @param persons    all persons in the simulation
	 * @param facilities all facilities
	 * @param vehicles   all vehicles
	 */
	default void init(SplittableRandom rnd, Map<Id<Person>, EpisimPerson> persons, Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> facilities, Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicles) {
	}

	/**
	 * Called after a snapshot has been loaded.
	 *
	 * @param iteration restored iteration
	 * @see #init(SplittableRandom, Map, Map, Map)
	 */
	default void onSnapshotLoaded(int iteration, SplittableRandom rnd, Map<Id<Person>, EpisimPerson> persons, Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> facilities, Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicles) {
	}

	/**
	 * Called just before iteration is about to start and events for the day will be handled.
	 */
	default void onIterationStart(int iteration, LocalDate date) {
	}

	/**
	 * Called after an iteration has finished and all trajectories and infection events have been handled.
	 */
	default void onIterationEnd(int iteration, LocalDate date) {
	}

}

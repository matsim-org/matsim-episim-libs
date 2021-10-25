package org.matsim.episim.model;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import java.util.Map;
import java.util.SplittableRandom;

/**
 * Listener to interact with the simulation before starting
 */
public interface SimulationStartListener {

	/**
	 * Called after init before the simulation starts.
	 *
	 * @param rnd        local random instance of the event handler
	 * @param persons    all persons in the simulation
	 * @param facilities all facilities
	 * @param vehicles   all vehicles
	 */
	void init(SplittableRandom rnd, Map<Id<Person>, EpisimPerson> persons, Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> facilities, Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicles);
}

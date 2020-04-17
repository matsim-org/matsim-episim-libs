package org.matsim.episim.model;

import org.matsim.episim.EpisimPerson;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.policy.Restriction;

import java.util.Map;

/**
 * This class models the infection dynamics of persons staying in the same place for a certain time.
 */
public interface InfectionModel {

	/**
	 * This method is called when a persons leave a vehicle at {@code now}.
	 */
	void infectionDynamicsVehicle(EpisimPerson personLeavingVehicle, InfectionEventHandler.EpisimVehicle vehicle, double now);

	/**
	 * This method is called when a persons leaves a facility at {@code now.}
	 */
	void infectionDynamicsFacility(EpisimPerson personLeavingFacility, InfectionEventHandler.EpisimFacility facility, double now, String actType);

	/**
	 * Set the current iteration and restrictions in place.
	 */
	void setRestrictionsForIteration(int iteration, Map<String, Restriction> restrictions);
}

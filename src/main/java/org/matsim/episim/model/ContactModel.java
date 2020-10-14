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

import org.matsim.episim.EpisimPerson;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.policy.Restriction;

import java.util.Map;

/**
 * This class models the contacts of persons staying in the same place for a certain time.
 */
public interface ContactModel {

	/**
	 * This method is called when a persons leave a vehicle at {@code now}.
	 */
	void infectionDynamicsVehicle(EpisimPerson personLeavingVehicle, InfectionEventHandler.EpisimVehicle vehicle, double now);
	void notifyEnterVehicle(EpisimPerson personEnteringVehicle, InfectionEventHandler.EpisimVehicle vehicle, double now);

	/**
	 * This method is called when a persons leaves a facility at {@code now}.
	 */
	void infectionDynamicsFacility(EpisimPerson personLeavingFacility, InfectionEventHandler.EpisimFacility facility, double now, String actType);
	void notifyEnterFacility(EpisimPerson personEnteringFacility, InfectionEventHandler.EpisimFacility facility, double now);

	/**
	 * Set the current iteration and restrictions in place.
	 */
	void setRestrictionsForIteration(int iteration, Map<String, Restriction> restrictions);

}

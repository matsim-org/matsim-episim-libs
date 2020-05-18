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
import org.matsim.episim.EpisimReporting;

import java.util.SplittableRandom;

/**
 * This class models the {@link org.matsim.episim.EpisimPerson.DiseaseStatus} state transitions at the end of the day.
 * The model should also update the {@link org.matsim.episim.EpisimPerson.QuarantineStatus} of affected persons.
 */
public interface ProgressionModel {

	/**
	 * Called at the start of the day to update the state of a person.
	 */
	void updateState(EpisimPerson person, int day);

	/**
	 * Checks whether any state transitions are possible. Otherwise the simulation will end.
	 */
	boolean canProgress(EpisimReporting.InfectionReport report);

}

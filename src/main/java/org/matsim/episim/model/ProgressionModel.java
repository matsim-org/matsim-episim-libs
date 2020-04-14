package org.matsim.episim.model;

import com.google.inject.ImplementedBy;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;

/**
 * This class models the {@link org.matsim.episim.EpisimPerson.DiseaseStatus} state transitions at the end of the day.
 * The model should also update the {@link org.matsim.episim.EpisimPerson.QuarantineStatus} of affected persons.
 */
@ImplementedBy(DefaultProgressionModel.class)
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

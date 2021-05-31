package org.matsim.episim.model.progression;

import org.matsim.episim.EpisimPerson;

/**
 * Model to calculate the next disease status of a person. Duration will be drawn from a different model.
 */
@FunctionalInterface
public interface DiseaseStatusTransitionModel {

	/**
	 * Calculate the next disease status of a person.
	 * @param person the person
	 * @param status current disease status
	 * @param day current day (iteration number)
	 * @return the next disease status.
	 */
	EpisimPerson.DiseaseStatus decideNextState(EpisimPerson person, EpisimPerson.DiseaseStatus status, int day);

}

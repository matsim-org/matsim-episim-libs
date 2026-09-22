package org.matsim.episim.model.progression;

import org.matsim.episim.EpisimPerson;

/**
 * Model to calculate the next disease status of a person. Duration will be drawn from a different model.
 *
 * <p>How much a person's immunity reduces the probability of a transition is not decided here. It is asked from
 * {@link org.matsim.episim.model.ImmunityModel}, which used to live in the default methods of this interface and
 * therefore had to be replaced by overriding, could not be reached again from a subclass, and ended up differing
 * between this side and the infection model.</p>
 */
@FunctionalInterface
public interface DiseaseStatusTransitionModel {

	/**
	 * Calculate the next disease status of a person.
	 *
	 * @param person the person
	 * @param status current disease status
	 * @param day    current day (iteration number)
	 * @return the next disease status.
	 */
	EpisimPerson.DiseaseStatus decideNextState(EpisimPerson person, EpisimPerson.DiseaseStatus status, int day);

}

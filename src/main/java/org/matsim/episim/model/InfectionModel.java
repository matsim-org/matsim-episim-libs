package org.matsim.episim.model;

import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.policy.Restriction;

import java.util.Map;

public interface InfectionModel {


	/**
	 * Called at the start of an iteration.
	 *
	 * @param iteration current iteration / day
	 */
	default void setIteration(int iteration) {
	}

	/**
	 * Calculates the probability that person {@code infector} infects {@code target}.
	 *
	 * @param target               The potentially infected person
	 * @param infector             The infectious person
	 * @param restrictions         Restrictions currently in place
	 * @param act1                 Activity of target
	 * @param act2                 Activity of infector
	 * @param contactIntensity     Contact intensity of this activity
	 * @param jointTimeInContainer joint time doing these activity in seconds
	 * @return probability between 0 and 1
	 */
	double calcInfectionProbability(EpisimPerson target, EpisimPerson infector, Map<String, Restriction> restrictions,
									EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2,
									double contactIntensity, double jointTimeInContainer);

}

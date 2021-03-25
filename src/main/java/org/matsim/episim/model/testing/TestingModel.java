package org.matsim.episim.model.testing;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;

import java.util.Map;

/**
 * Model to determine which persons are tested at the start of the day.
 */
public interface TestingModel {

	/**
	 * Called at the start of an iteration before executing the progression.
	 */
	default void setIteration(int day) {}

	/**
	 * Perform testing on the person and update state if necessary.
	 */
	void performTesting(EpisimPerson person, int day);

	/**
	 * Called before {@link #performTesting(EpisimPerson, int)}.
	 */
	void beforeStateUpdates(Map<Id<Person>, EpisimPerson> personMap, int iteration, EpisimReporting.InfectionReport report);
}

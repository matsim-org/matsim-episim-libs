package org.matsim.episim.model.testing;

import org.matsim.episim.EpisimPerson;

/**
 * Model to determine which persons are tested at the start of the day.
 */
public interface TestingModel {

	/**
	 * Called at the start of an iteration before executing the progression.
	 */
	default void setIteration(int day) {}

	void performTesting(EpisimPerson person, int day);


}

package org.matsim.episim.model;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;

import java.util.Map;

/**
 * Class that is responsible for modelling disease import and initial infections.
 */
public interface InitialInfectionHandler {

	/**
	 * Called at the start of every iteration. This class should set the disease state of persons as necessary.
	 */
	void handleInfections(Map<Id<Person>, EpisimPerson> persons, int iteration);

	/**
	 * Number of initial infections left that will also be persisted. Might be relevant for certain models to stop disease import.
	 */
	default int getInfectionsLeft() {
		return 0;
	}

	/**
	 * Set the number of initial infections left.
	 */
	default void setInfectionsLeft(int num) {}
}

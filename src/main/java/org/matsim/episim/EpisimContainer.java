package org.matsim.episim;

import org.eclipse.collections.api.map.primitive.MutableIntDoubleMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntDoubleHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.gbl.Gbl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Wrapper class for a specific location that keeps track of currently contained agents and entering times.
 *
 * @param <T> the type where the agents are located in, e.g {@link org.matsim.vehicles.Vehicle} or {@link org.matsim.facilities.Facility}.
 */
public class EpisimContainer<T> {
	private final Id<T> containerId;

	/**
	 * Persons currently in this container.
	 */
	private MutableIntObjectMap<EpisimPerson> persons = new IntObjectHashMap<>(4);

	/**
	 * Person list needed to draw random persons within container.
	 */
	private List<EpisimPerson> personsAsList = new ArrayList<>();

	private MutableIntDoubleMap containerEnterTimes = new IntDoubleHashMap(4);

	EpisimContainer(Id<T> containerId) {
		this.containerId = containerId;
	}

	void addPerson(EpisimPerson person, double now) {
		if (persons.containsKey(person.getPersonId().index()))
			throw new IllegalStateException("Person already contained in this container.");

		persons.put(person.getPersonId().index(), person);
		personsAsList.add(person);
		containerEnterTimes.put(person.getPersonId().index(), now);
		person.setCurrentContainer(this);
	}

	/**
	 * @noinspection UnusedReturnValue
	 */
	EpisimPerson removePerson(Id<Person> personId) {
		containerEnterTimes.remove(personId.index());
		EpisimPerson personWrapper = persons.remove(personId.index());
		personWrapper.removeCurrentContainer(this);
		boolean wasRemoved = personsAsList.remove(personWrapper);
		Gbl.assertIf(wasRemoved);
		return personWrapper;
	}

	public Id<T> getContainerId() {
		return containerId;
	}

	void clearPersons() {
		this.persons.clear();
		this.personsAsList.clear();
		this.containerEnterTimes.clear();
	}

	/**
	 * Returns the time the person entered the container, or {@link Double#NEGATIVE_INFINITY} if it never entered.
	 */
	public double getContainerEnteringTime(Id<Person> personId) {
		return containerEnterTimes.getIfAbsent(personId.index(), Double.NEGATIVE_INFINITY);
	}

	EpisimPerson getPerson(Id<Person> personId) {
		return persons.get(personId.index());
	}

	public List<EpisimPerson> getPersons() {
		return Collections.unmodifiableList(personsAsList);
	}
}

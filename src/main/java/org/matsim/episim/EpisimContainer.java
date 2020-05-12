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
package org.matsim.episim;

import org.eclipse.collections.api.map.primitive.MutableIntDoubleMap;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.map.mutable.primitive.IntDoubleHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.gbl.Gbl;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Wrapper class for a specific location that keeps track of currently contained agents and entering times.
 *
 * @param <T> the type where the agents are located in, e.g {@link org.matsim.vehicles.Vehicle} or {@link org.matsim.facilities.Facility}.
 */
public class EpisimContainer<T> {
	private final Id<T> containerId;

	/**
	 * Persons currently in this container. Stored only as Ids.
	 */
	private final MutableIntSet persons = new IntHashSet(4);

	/**
	 * Person list needed to draw random persons within container.
	 */
	private final List<EpisimPerson> personsAsList = new ArrayList<>();

	private final MutableIntDoubleMap containerEnterTimes = new IntDoubleHashMap(4);

	EpisimContainer(Id<T> containerId) {
		this.containerId = containerId;
	}

	/**
	 * Reads containers state from stream.
	 */
	void read(ObjectInput in, Map<Id<Person>, EpisimPerson> persons) {

	}

	/**
	 * Writes state to stream.
	 */
	void write(ObjectOutput out) {

	}

	void addPerson(EpisimPerson person, double now) {
		final int index = person.getPersonId().index();

		if (persons.contains(index))
			throw new IllegalStateException("Person already contained in this container.");

		persons.add(index);
		personsAsList.add(person);
		containerEnterTimes.put(index, now);
		person.setCurrentContainer(this);
	}

	/**
	 * Removes a person from this container.
	 * @throws RuntimeException if the person was not in the container.
	 */
	void removePerson(EpisimPerson person) {
		int index = person.getPersonId().index();

		containerEnterTimes.remove(index);
		persons.remove(index);
		person.removeCurrentContainer(this);
		boolean wasRemoved = personsAsList.remove(person);
		Gbl.assertIf(wasRemoved);
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

	public List<EpisimPerson> getPersons() {
		// Using Collections.unmodifiableList(...) puts huge pressure on the GC if its called hundred thousand times per second
		return personsAsList;
	}
}

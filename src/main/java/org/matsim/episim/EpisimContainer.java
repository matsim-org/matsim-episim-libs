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

import it.unimi.dsi.fastutil.ints.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.matsim.episim.EpisimUtils.*;

/**
 * Wrapper class for a specific location that keeps track of currently contained agents and entering times.
 *
 * @param <T> the type where the agents are located in, e.g {@link org.matsim.vehicles.Vehicle} or {@link org.matsim.facilities.Facility}.
 */
public class EpisimContainer<T> {
	private final Id<T> containerId;

	private static final Logger log = LogManager.getLogger(EpisimContainer.class);

	/**
	 * Persons currently in this container. Stored only as Ids.
	 */
	private final IntSet persons = new IntOpenHashSet(4);

	/**
	 * Person list needed to draw random persons within container.
	 */
	private final List<EpisimPerson> personsAsList = new ArrayList<>();

	private final Int2DoubleMap containerEnterTimes = new Int2DoubleOpenHashMap(4);

	/**
	 * Activities of persons in the container.
	 */
	private final Int2ObjectMap<EpisimPerson.PerformedActivity> personActivities = new Int2ObjectArrayMap<>(4);

	/**
	 * The maximum number of persons simultaneously in this container. Negative if unknown.
	 * Already scaled with sampleSize.
	 */
	private int maxGroupSize = -1;

	/**
	 * The number of persons using this container over all days.
	 */
	private int totalUsers = -1;

	/**
	 * Typical number of persons that can fit into this container.
	 */
	private int typicalCapacity = -1;

	/**
	 * Number of distinct spaces in this facility. May be relevant for certain contact models.
	 */
	private double numSpaces = 1;

	/**
	 * The id of the ReplayEventTask that handles the events for this container
	 */
	private int taskId = 0;

	/**
	 * This counts the number of persons in this container
	 * which have the DiseaseStatus contagious or showingSymptoms. 	
	 */
 	private int contagiousCounter = 0;

	EpisimContainer(Id<T> containerId) {
		this.containerId = containerId;
	}

	/**
	 * Reads containers state from stream.
	 */
	void read(ObjectInput in, Map<Id<Person>, EpisimPerson> persons) throws IOException {

		this.persons.clear();
		this.personsAsList.clear();
		this.containerEnterTimes.clear();

		int n = in.readInt();
		for (int i = 0; i < n; i++) {
			Id<Person> id = Id.create(readChars(in), Person.class);
			this.persons.add(id.index());
			personsAsList.add(persons.get(id));
			containerEnterTimes.put(id.index(), in.readDouble());
		}
	}

	/**
	 * Writes state to stream.
	 */
	void write(ObjectOutput out) throws IOException {

		out.writeInt(containerEnterTimes.size());
		for (EpisimPerson p : personsAsList) {
			writeChars(out, p.getPersonId().toString());
			out.writeDouble(containerEnterTimes.get(p.getPersonId().index()));
		}
	}

	boolean containsPerson(EpisimPerson person) {
		final int index = person.getPersonId().index();
		return persons.contains(index);
	}

	void addPerson(EpisimPerson person, double now, EpisimPerson.PerformedActivity act) {
		final int index = person.getPersonId().index();

		//assert !persons.contains(index) : "Person already contained in this container.";
		assert !persons.contains(index) : String.format("Person %s was already in container %s", person.getPersonId(), containerId);

		persons.add(index);
		personsAsList.add(person);
		containerEnterTimes.put(index, now);
		personActivities.put(index, act);
	}

	/**
	 * Removes a person from this container.
	 *
	 * @throws RuntimeException if the person was not in the container.
	 */
	void removePerson(EpisimPerson person) {
		int index = person.getPersonId().index();

		containerEnterTimes.remove(index);
		personActivities.remove(index);
		persons.remove(index);
		boolean wasRemoved = personsAsList.remove(person);
		if (!wasRemoved)
			log.warn( "Person {} was not in container {}", person.getPersonId(), containerId);

		if (person.infectedButNotSerious())
			contagiousCounter -= 1;
	}

	/**
	 * Remove person using the iterator from {@link #getPersons()}.
	 * This allows to remove persons while iterating through them.
	 */
	void removePerson(EpisimPerson person, Iterator<EpisimPerson> it) {
		int index = person.getPersonId().index();

		containerEnterTimes.remove(index);
		personActivities.remove(index);
		persons.remove(index);
		it.remove();
	}

	public Id<T> getContainerId() {
		return containerId;
	}

	/**
	 * @return maximum group size in container.
	 */
	public int getMaxGroupSize() {
		return maxGroupSize;
	}

	/**
	 * @return number of people using container.  May be larger than {@link #getMaxGroupSize()}.
	 */
	public int getTotalUsers() {
		return totalUsers;
	}

	public int getTypicalCapacity() {
		return typicalCapacity;
	}

	public double getNumSpaces() {
		return numSpaces;
	}

	/**
	 * Sets the max group size this container has during a day.
	 *
	 * @param num number of persons
	 */
	void setMaxGroupSize(int num) {
		maxGroupSize = num;
	}

	/**
	 * Sets the total number of persons using this container.
	 *
	 * @param num number of persons
	 */
	void setTotalUsers(int num) {
		totalUsers = num;
	}

	void setTypicalCapacity(int typicalCapacity) {
		this.typicalCapacity = typicalCapacity;
	}

	public void setNumSpaces(double numSpaces) {
		this.numSpaces = numSpaces;
	}

	public void setTaskId(int taskId) {
		this.taskId = taskId;
	}

	public int getTaskId() {
		return taskId;
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
		return containerEnterTimes.getOrDefault(personId.index(), Double.NEGATIVE_INFINITY);
	}

	/**
	 * Return the activity that a person is performing in this container.
	 */
	public EpisimPerson.PerformedActivity getPerformedActivity(Id<Person> personId) {
		return personActivities.get(personId.index());
	}

	public List<EpisimPerson> getPersons() {
		// Using Collections.unmodifiableList(...) puts huge pressure on the GC if its called hundred thousand times per second
		return personsAsList;
	}


	public void countContagious(int add) {
		contagiousCounter += add;
		assert contagiousCounter >= 0 : "We can not have a negative number of contagious persons"; 
	}

	public void resetContagiousCounter() {
		contagiousCounter = 0;
	}
	
	public boolean containsContagious() {
		return contagiousCounter > 0;
	}
}

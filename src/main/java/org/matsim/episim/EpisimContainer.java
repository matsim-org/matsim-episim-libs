package org.matsim.episim;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.gbl.Gbl;

import java.util.*;

/**
 * Wrapper class for a specific location that keeps track of currently contained agents and entering times.
 *
 * @param <T> the type where the agents are located in, e.g {@link org.matsim.vehicles.Vehicle} or {@link org.matsim.facilities.Facility}.
 */
class EpisimContainer<T> {
    private final Id<T> containerId;

    /**
     * Persons currently in this container.
     */
    private Map<Id<Person>, EpisimPerson> persons = new LinkedHashMap<>();

    /**
     * Person list needed to draw random persons within container.
     */
    private List<EpisimPerson> personsAsList = new ArrayList<>();

    private Map<Id<Person>, Double> containerEnterTimes = new LinkedHashMap<>();

    EpisimContainer(Id<T> containerId) {
        this.containerId = containerId;
    }

    void addPerson(EpisimPerson person, double now) {
        if (persons.containsKey(person.getPersonId()))
            throw new IllegalStateException("Person already contained in this container.");

        persons.put(person.getPersonId(), person);
        personsAsList.add(person);
        containerEnterTimes.put(person.getPersonId(), now);
        person.setCurrentContainer(this);
    }

    /**
     * @noinspection UnusedReturnValue
     */
    EpisimPerson removePerson(Id<Person> personId) {
        containerEnterTimes.remove(personId);
        EpisimPerson personWrapper = persons.remove(personId);
        personWrapper.removeCurrentContainer(this);
        boolean wasRemoved = personsAsList.remove(personWrapper);
        Gbl.assertIf(wasRemoved);
        return personWrapper;
    }

    Id<T> getContainerId() {
        return containerId;
    }


    void clearPersons() {
        this.persons.clear();
        this.personsAsList.clear();
        this.containerEnterTimes.clear();
    }

    Double getContainerEnteringTime(Id<Person> personId) {
        return containerEnterTimes.get(personId);
    }

    EpisimPerson getPerson(Id<Person> personId) {
        return persons.get(personId);
    }

    public List<EpisimPerson> getPersons() {
        return Collections.unmodifiableList(personsAsList);
    }
}

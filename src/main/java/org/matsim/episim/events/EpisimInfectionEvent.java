package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.internal.HasPersonId;

import java.util.Map;


/**
 * Notifies when a person got infected by another person.
 */
public final class EpisimInfectionEvent extends Event implements HasPersonId {

	// TODO: hasLink or hasCoord?

	private static final String EVENT_TYPE = "episimInfection";
	private static final String INFECTOR = "infector";
	private static final String CONTAINER = "container";
	private static final String INFECTION_TYPE = "infectionType";

	private final Id<Person> personId;
	private final Id<Person> infectorId;
	private final Id<?> containerId;
	private final String infectionType;

	public EpisimInfectionEvent(double time, Id<Person> personId, Id<Person> infectorId, Id<?> containerId, String infectionType) {
		super(time);

		this.personId = personId;
		this.infectorId = infectorId;
		this.containerId = containerId;
		this.infectionType = infectionType;
	}

	@Override
	public String getEventType() {
		return EVENT_TYPE;
	}

	@Override
	public Id<Person> getPersonId() {
		return personId;
	}

	public Id<?> getContainerId() {
		return containerId;
	}

	/**
	 * Person who infected {@code #getPersonId}.
	 */
	public Id<Person> getInfectorId() {
		return infectorId;
	}

	/**
	 * How this infection happened. Activity of person and infector separated by underscore.
	 */
	public String getInfectionType() {
		return infectionType;
	}

	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();

		attr.put(CONTAINER, containerId.toString());
		attr.put(INFECTOR, infectorId.toString());
		attr.put(INFECTION_TYPE, infectionType);

		return attr;
	}
}

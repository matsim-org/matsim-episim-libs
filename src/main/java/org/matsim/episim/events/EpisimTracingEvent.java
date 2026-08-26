package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.events.HasPersonId;

import java.util.Map;



/**
 * Event that informs that tracing has happened between two persons.
 */
public final class EpisimTracingEvent extends Event implements HasPersonId {

	private static final String EVENT_TYPE = "episimTracing";
	private static final String CONTACT_PERSON = "contactPerson";

	private final Id<Person> personId;
	private final Id<Person> contactPersonId;

	/**
	 * Constructor.
	 */
	public EpisimTracingEvent(double time, Id<Person> personId, Id<Person> contactPersonId) {
		super(time);
		this.personId = personId;
		this.contactPersonId = contactPersonId;
	}

	@Override
	public String getEventType() {
		return EVENT_TYPE;
	}

	@Override
	public Id<Person> getPersonId() {
		return personId;
	}

	/**
	 * The contact person.
	 */
	public Id<Person> getContactPersonId() {
		return contactPersonId;
	}


	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();
		attr.put(CONTACT_PERSON, contactPersonId.toString());
		return attr;
	}
}

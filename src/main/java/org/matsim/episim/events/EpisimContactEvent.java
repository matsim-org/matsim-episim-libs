package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.internal.HasPersonId;

import java.util.Map;

import static org.matsim.api.core.v01.events.ActivityEndEvent.ATTRIBUTE_ACTTYPE;


/**
 * Notifies when a person had contact with another one during its performed activity.
 */
public final class EpisimContactEvent extends Event implements HasPersonId {

	private static final String EVENT_TYPE = "episimContact";
	private static final String CONTACT_PERSON = "contactPerson";
	private static final String DURATION = "duration";
	private static final String CONTAINER = "container";
	private static final String INFECTION_PROB = "infectionProb";

	private final Id<Person> personId;
	private final Id<Person> contactPersonId;
	private final Id<?> containerId;
	private final String actType;
	private final double duration;
	private final double infectionProb;

	/**
	 * Constructor.
	 */
	public EpisimContactEvent(double time, Id<Person> personId, Id<Person> contactPersonId, Id<?> containerId, String actType, double duration,
							  double infectionProb) {
		super(time);
		this.personId = personId;
		this.contactPersonId = contactPersonId;
		this.containerId = containerId;
		this.actType = actType;
		this.duration = duration;
		this.infectionProb = infectionProb;
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

	/**
	 * Length of the contact in seconds.
	 */
	public double getDuration() {
		return duration;
	}

	/**
	 * Infection probability if infection is possible, -1 otherwise.
	 */
	public double getInfectionProb() {
		return infectionProb;
	}

	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();

		attr.put(ATTRIBUTE_ACTTYPE, actType);
		attr.put(CONTACT_PERSON, contactPersonId.toString());
		attr.put(DURATION, String.valueOf(duration));
		attr.put(CONTAINER, containerId.toString());
		attr.put(INFECTION_PROB, String.valueOf(infectionProb));

		return attr;
	}
}

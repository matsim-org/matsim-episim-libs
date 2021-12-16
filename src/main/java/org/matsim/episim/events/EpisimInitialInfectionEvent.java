package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.episim.model.VirusStrain;

import java.util.Map;


/**
 * Event for an initial infection.
 */
public class EpisimInitialInfectionEvent extends Event implements HasPersonId, Comparable<Event> {

	static final String EVENT_TYPE = "episimInitialInfection";

	private final Id<Person> personId;
	private final VirusStrain virusStrain;


	/**
	 * Constructor.
	 */
	public EpisimInitialInfectionEvent(double time, Id<Person> personId, VirusStrain strain) {
		super(time);

		this.personId = personId;
		this.virusStrain = strain;
	}

	@Override
	public String getEventType() {
		return EVENT_TYPE;
	}

	@Override
	public Id<Person> getPersonId() {
		return personId;
	}

	public VirusStrain getVirusStrain() {
		return virusStrain;
	}

	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();
		attr.put(EpisimInfectionEvent.VIRUS_STRAIN, virusStrain.toString());

		return attr;
	}

	@Override
	public int compareTo(Event obj) {
		// Defines a stable ordering for events
		return Double.compare(getTime(), obj.getTime());
	}
}

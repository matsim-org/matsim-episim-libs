package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.episim.model.VaccinationType;

import java.util.Map;

/**
 * Notifies about performed vaccinations.
 */
public class EpisimVaccinationEvent extends Event implements HasPersonId {

	public static final String EVENT_TYPE = "episimVaccination";
	public static final String TYPE = "vaccinationType";
	public static final String N = "n";

	private final Id<Person> personId;
	private final VaccinationType type;
	private final int n;

	public EpisimVaccinationEvent(double time, Id<Person> personId, VaccinationType type, int n) {
		super(time);
		this.personId = personId;
		this.type = type;
		this.n = n;
	}


	@Override
	public String getEventType() {
		return EVENT_TYPE;
	}

	@Override
	public Id<Person> getPersonId() {
		return personId;
	}

	public VaccinationType getVaccinationType() {
		return type;
	}

	@Deprecated
	public boolean getReVaccination() {
		return n > 1;
	}

	/**
	 * Number of vaccination. Starts at 1.
	 */
	public int getN() {
		return n;
	}

	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();
		attr.put(N, String.valueOf(n));
		attr.put(TYPE, type.toString());

		return attr;
	}
}

package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.model.VaccinationType;

import java.time.LocalDate;
import java.util.Map;

/**
 * Notifies begin of episim simulation
 */
public class EpisimStartEvent extends Event {

	public static final String EVENT_TYPE = "episimStart";
	public static final String START_DATE = "startDate";
	public static final String IMMUNIZATION = "immunization";

	private final LocalDate startDate;
	private final String immunization;

	public EpisimStartEvent(LocalDate startDate, String immunization) {
		super(0);
		this.startDate = startDate;
		this.immunization = immunization;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	@Override
	public String getEventType() {
		return EVENT_TYPE;
	}



	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();

		attr.put(START_DATE, startDate.toString());
		attr.put(IMMUNIZATION, String.valueOf(immunization));

		return attr;
	}
}

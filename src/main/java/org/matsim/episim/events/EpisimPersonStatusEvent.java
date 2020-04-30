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
package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.episim.EpisimPerson;

import java.util.Map;

/**
 * Notifies about the disease status of a person.
 */
public final class EpisimPersonStatusEvent extends Event implements HasPersonId {
	private static final String EVENT_TYPE = "episimPersonStatus";
	private static final String DISEASE_STATUS = "diseaseStatus";
	private final EpisimPerson.DiseaseStatus diseaseStatus;
	private final Id<Person> personId;

	/**
	 * Constructor.
	 */
	public EpisimPersonStatusEvent(double time, Id<Person> personId, EpisimPerson.DiseaseStatus diseaseStatus) {
		super(time);
		this.diseaseStatus = diseaseStatus;
		this.personId = personId;
	}

	@Override
	public String getEventType() {
		return EVENT_TYPE;
	}

	@Override
	public Id<Person> getPersonId() {
		return personId;
	}

	public EpisimPerson.DiseaseStatus getDiseaseStatus() {
		return diseaseStatus;
	}

	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();
		// person, link, facility done by superclass
		attr.put(DISEASE_STATUS, this.diseaseStatus.name());
		return attr;
	}

}

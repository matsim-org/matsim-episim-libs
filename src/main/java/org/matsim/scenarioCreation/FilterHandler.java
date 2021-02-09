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
package org.matsim.scenarioCreation;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.episim.ReplayHandler;
import org.matsim.facilities.ActivityFacility;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Filters events needed for the {@link InfectionEventHandler}.
 * Either by population or personIds list.
 */
public class FilterHandler implements ActivityEndEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, ActivityStartEventHandler {

	final Population population;
	final Set<Id<Person>> personIds;

	final List<Event> events = new ArrayList<>();

	/**
	 * Facilities that have been visited by the filtered persons.
	 */
	final Set<Id<ActivityFacility>> facilities = new HashSet<>();
	private final Map<Id<ActivityFacility>, Id<ActivityFacility>> facilityReplacements;

	private int counter = 0;


	/**
	 * Constructor.
	 */
	public FilterHandler(@Nullable Population population, @Nullable Set<String> personIds, @Nullable Map<Id<ActivityFacility>, Id<ActivityFacility>> facilityReplacements) {
		this.population = population;
		this.personIds = personIds != null ? personIds.stream().map(Id::createPersonId).collect(Collectors.toSet()) : null;
		this.facilityReplacements = facilityReplacements;
	}

	@Override
	public void handleEvent(ActivityEndEvent activityEndEvent) {
		counter++;

		if (!ReplayHandler.shouldHandleActivityEvent(activityEndEvent, activityEndEvent.getActType()))
			return;
		if (population != null && !population.getPersons().containsKey(activityEndEvent.getPersonId()))
			return;
		if (personIds != null && !personIds.contains(activityEndEvent.getPersonId()))
			return;

		if (this.facilityReplacements != null && this.facilityReplacements.containsKey(activityEndEvent.getFacilityId())) {
			Id<ActivityFacility> replacingId = this.facilityReplacements.get(activityEndEvent.getFacilityId());
			activityEndEvent = new ActivityEndEvent(activityEndEvent.getTime(),
					activityEndEvent.getPersonId(),
					activityEndEvent.getLinkId(),
					replacingId,
					activityEndEvent.getActType());
		}

		facilities.add(activityEndEvent.getFacilityId());
		events.add(activityEndEvent);
	}

	@Override
	public void handleEvent(ActivityStartEvent activityStartEvent) {
		counter++;

		if (!ReplayHandler.shouldHandleActivityEvent(activityStartEvent, activityStartEvent.getActType()))
			return;
		if (population != null && !population.getPersons().containsKey(activityStartEvent.getPersonId()))
			return;
		if (personIds != null && !personIds.contains(activityStartEvent.getPersonId()))
			return;

		if (this.facilityReplacements != null && this.facilityReplacements.containsKey(activityStartEvent.getFacilityId())) {
			Id<ActivityFacility> replacingId = this.facilityReplacements.get(activityStartEvent.getFacilityId());
			activityStartEvent = new ActivityStartEvent(activityStartEvent.getTime(),
					activityStartEvent.getPersonId(),
					activityStartEvent.getLinkId(),
					replacingId,
					activityStartEvent.getActType(),
					activityStartEvent.getCoord());
		}

		facilities.add(activityStartEvent.getFacilityId());
		events.add(activityStartEvent);
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent personEntersVehicleEvent) {
		counter++;

		if (!ReplayHandler.shouldHandlePersonEvent(personEntersVehicleEvent))
			return;
		if (population != null && !population.getPersons().containsKey(personEntersVehicleEvent.getPersonId()))
			return;
		if (personIds != null && !personIds.contains(personEntersVehicleEvent.getPersonId()))
			return;

		events.add(personEntersVehicleEvent);
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent personLeavesVehicleEvent) {
		counter++;

		if (!ReplayHandler.shouldHandlePersonEvent(personLeavesVehicleEvent))
			return;
		if (population != null && !population.getPersons().containsKey(personLeavesVehicleEvent.getPersonId()))
			return;
		if (personIds != null && !personIds.contains(personLeavesVehicleEvent.getPersonId()))
			return;

		events.add(personLeavesVehicleEvent);
	}

	public int getCounter() {
		return counter;
	}


	public List<Event> getEvents() {
		return events;
	}

	public Set<Id<ActivityFacility>> getFacilities() {
		return facilities;
	}
}

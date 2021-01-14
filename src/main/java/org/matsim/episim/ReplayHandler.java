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

import com.google.common.annotations.Beta;
import com.google.inject.Inject;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.HasFacilityId;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.facilities.ActivityFacility;

import javax.annotation.Nullable;
import java.time.DayOfWeek;
import java.util.*;


/**
 * Handler that replays events from {@link EpisimConfigGroup#getInputEventsFile()} with corrected time and attributes.
 */
public final class ReplayHandler {

	private static final Logger log = LogManager.getLogger(ReplayHandler.class);

	/**
	 * Experimental flag to adjust persons starting with leisure time during reading of events.
	 * @implNote don't set to true
	 */
	@Beta
	private static final boolean ADJUST_LEISURE = false;
	/**
	 * Used when adjust leisure is true. Mark persons that already started their day.
	 */
	private final Set<Id<Person>> started = Collections.newSetFromMap(new IdentityHashMap<>());
	/**
	 * Rng for leisure adjustment.
	 */
	private SplittableRandom rnd;
	/**
	 * Number of adjusted leisure activities.
	 */
	private int adjusted = 0;
	/**
	 * Needed in createEpisimFacilityId.
	 */
	private final EpisimConfigGroup episimConfig;


	private final Scenario scenario;
	private final Map<DayOfWeek, List<Event>> events = new EnumMap<>(DayOfWeek.class);

	/**
	 * Constructor with optional scenario. Events will be read from given {@link EpisimConfigGroup#getInputEventsFiles()}.
	 */
	@Inject
	public ReplayHandler(EpisimConfigGroup config, @Nullable Scenario scenario) {
		this.scenario = scenario;
		this.episimConfig = config;

		for (EpisimConfigGroup.EventFileParams input : config.getInputEventsFiles()) {

			rnd = new SplittableRandom(0);

			List<Event> eventsForDay = new ArrayList<>();
			EventsManager manager = EventsUtils.createEventsManager();
			manager.addHandler(new EventReader(eventsForDay));
			EventsUtils.readEvents(manager, input.getPath());
			manager.finishProcessing();

			log.info("Read in {} events for {}, with time range {} - {}", eventsForDay.size(), input.getDays(), eventsForDay.get(0).getTime(),
					eventsForDay.get(eventsForDay.size() - 1).getTime());

			for (DayOfWeek day : input.getDays()) {
				if (events.containsKey(day))
					throw new IllegalStateException("Events for day " + day + " already defined!");

				events.put(day, eventsForDay);
			}

			started.clear();
		}

		if (ADJUST_LEISURE) {
			log.warn("Adjusted {} leisure activities", adjusted);
		}

		if (events.size() != 7) {
			EnumSet<DayOfWeek> missing = EnumSet.complementOf(EnumSet.copyOf(events.keySet()));
			throw new IllegalStateException("Event definition missing for days: " + missing);
		}
	}

	/**
	 * Constructor for using pre-defined events. A list of events for all weekdays needs to be present.
	 * Events also have to ordered by time.
	 *
	 * @param events ordered events for all weekdays
	 */
	public ReplayHandler(Map<DayOfWeek, List<Event>> events) {
		this.events.putAll(events);
		this.scenario = null;
		this.episimConfig = null;
	}

	/**
	 * Replays event add modifies attributes based on current iteration.
	 */
	public void replayEvents(final InfectionEventHandler infectionHandler, DayOfWeek day) {
		for (final Event e : events.get(day)) {
			if (e instanceof ActivityStartEvent) {
				infectionHandler.handleEvent((ActivityStartEvent) e);
			} else if (e instanceof ActivityEndEvent) {
				infectionHandler.handleEvent((ActivityEndEvent) e);
			} else if (e instanceof PersonEntersVehicleEvent) {
				infectionHandler.handleEvent((PersonEntersVehicleEvent) e);
			} else {
				infectionHandler.handleEvent((PersonLeavesVehicleEvent) e);
			}
		}
	}

	/**
	 * All available events.
	 */
	public Map<DayOfWeek, List<Event>> getEvents() {
		return new EnumMap<>(events);
	}

	/**
	 * Helper class to read events one time.
	 */
	private final class EventReader implements BasicEventHandler {

		private final List<Event> events;

		private EventReader(List<Event> events) {
			this.events = events;
		}

		@Override
		public void handleEvent(Event event) {

			// Add coordinate information if not present
			if (event instanceof ActivityStartEvent) {
				ActivityStartEvent e = (ActivityStartEvent) event;

				if (!shouldHandleActivityEvent(e, e.getActType())) {
					return;
				}

				Coord coord = e.getCoord();
				if (coord == null && scenario != null && scenario.getNetwork().getLinks().containsKey(e.getLinkId())) {
					Link link = scenario.getNetwork().getLinks().get(e.getLinkId());
					coord = link.getToNode().getCoord();
				}

				if (ADJUST_LEISURE) {
					started.add(e.getPersonId());
				}

				event = new ActivityStartEvent(e.getTime(), e.getPersonId(), e.getLinkId(),
						                       createEpisimFacilityId(e),
						                       e.getActType().intern(), coord);
			} else if (event instanceof ActivityEndEvent) {
				ActivityEndEvent e = (ActivityEndEvent) event;

				if (!shouldHandleActivityEvent(e, e.getActType())) {
					return;
				}

				String actType = e.getActType().intern();
				double time = e.getTime();

				// only persons starting their day with end leisure will be adjusted.
				if (ADJUST_LEISURE && time < 13000 && !started.contains(e.getPersonId())) {
					started.add(e.getPersonId());

					// adjust person during the first 3.6h
					// valid comparison because of .intern()
					if (time < 13000 && actType.equals("leisure")) {
						time -= Math.min(time - 1, 13000 * rnd.nextDouble());
						adjusted++;
					}
				}

				event = new ActivityEndEvent(time, e.getPersonId(), e.getLinkId(),
						createEpisimFacilityId(e),
						actType);
			} else if (event instanceof PersonEntersVehicleEvent) {
				if (!shouldHandlePersonEvent((PersonEntersVehicleEvent) event)) {
					return;
				}
			} else if (event instanceof PersonLeavesVehicleEvent) {
				if (!shouldHandlePersonEvent((PersonLeavesVehicleEvent) event)) {
					return;
				}
			}

			events.add(event);
		}


	}

	/**
	 * Whether {@code event} should be handled.
	 *
	 * @param actType activity type
	 */
	public static boolean shouldHandleActivityEvent(HasPersonId event, String actType) {
		// ignore drt and stage activities
		return !event.getPersonId().toString().startsWith("drt") && !event.getPersonId().toString().startsWith("rt")
				&& !TripStructureUtils.isStageActivityType(actType);
	}

	/**
	 * Whether a Person event (e.g. {@link PersonEntersVehicleEvent} should be handled.
	 */
	public static boolean shouldHandlePersonEvent(HasPersonId event) {
		// ignore pt drivers and drt
		String id = event.getPersonId().toString();
		return !id.startsWith("pt_pt") && !id.startsWith("pt_tr") && !id.startsWith("drt") && !id.startsWith("rt");
	}

	private Id<ActivityFacility> createEpisimFacilityId(HasFacilityId event) {
		if (episimConfig.getFacilitiesHandling() == EpisimConfigGroup.FacilitiesHandling.snz) {
			Id<ActivityFacility> id = event.getFacilityId();
			if (id == null)
				throw new IllegalStateException("No facility id present. Please switch to episimConfig.setFacilitiesHandling( EpisimConfigGroup.FacilitiesHandling.bln ) ");

			return id;
		} else if (episimConfig.getFacilitiesHandling() == EpisimConfigGroup.FacilitiesHandling.bln) {
			if (event instanceof ActivityStartEvent) {
				ActivityStartEvent theEvent = (ActivityStartEvent) event;
				return Id.create(theEvent.getActType().split("_")[0] + "_" + theEvent.getLinkId().toString(), ActivityFacility.class);
			} else if (event instanceof ActivityEndEvent) {
				ActivityEndEvent theEvent = (ActivityEndEvent) event;
				return Id.create(theEvent.getActType().split("_")[0] + "_" + theEvent.getLinkId().toString(), ActivityFacility.class);
			} else {
				throw new IllegalStateException("unexpected event type=" + ((Event) event).getEventType());
			}
		} else {
			throw new NotImplementedException(Gbl.NOT_IMPLEMENTED);
		}

	}
}

package org.matsim.episim;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler that replays events from {@link EpisimConfigGroup#getInputEventsFile()} with corrected time and attributes.
 */
public final class ReplayHandler {

	private static final Logger log = LogManager.getLogger(ReplayHandler.class);

	private final Scenario scenario;
	private final List<Event> events = new ArrayList<>();

	/**
	 * Constructor with optional scenario.
	 */
	@Inject
	public ReplayHandler(EpisimConfigGroup config, @Nullable Scenario scenario) {
		this.scenario = scenario;

		EventsManager manager = EventsUtils.createEventsManager();
		manager.addHandler(new EventReader());
		EventsUtils.readEvents(manager, config.getInputEventsFile());
		manager.finishProcessing();

		log.info("Read in {} events, with time range {} - {}", events.size(), events.get(0).getTime(),
				events.get(events.size() - 1).getTime());
	}

	/**
	 * Replays event add modifies attributes based on current iteration.
	 */
	public void replayEvents(final EventsManager manager, final int iteration) {
		for (final Event e : events) {

			if (e instanceof ActivityStartEvent) {
				ActivityStartEvent ev = (ActivityStartEvent) e;
				manager.processEvent(
						new ActivityStartEvent(EpisimUtils.getCorrectedTime(ev.getTime(), iteration), ev.getPersonId(),
								ev.getLinkId(), ev.getFacilityId(), ev.getActType(), ev.getCoord())
				);

			} else if (e instanceof ActivityEndEvent) {
				ActivityEndEvent ev = (ActivityEndEvent) e;
				manager.processEvent(
						new ActivityEndEvent(EpisimUtils.getCorrectedTime(ev.getTime(), iteration), ev.getPersonId(),
								ev.getLinkId(), ev.getFacilityId(), ev.getActType())
				);

			} else if (e instanceof PersonEntersVehicleEvent) {
				PersonEntersVehicleEvent ev = (PersonEntersVehicleEvent) e;
				manager.processEvent(
						new PersonEntersVehicleEvent(EpisimUtils.getCorrectedTime(e.getTime(), iteration), ev.getPersonId(), ev.getVehicleId())
				);

			} else if (e instanceof PersonLeavesVehicleEvent) {
				PersonLeavesVehicleEvent ev = (PersonLeavesVehicleEvent) e;
				manager.processEvent(
						new PersonLeavesVehicleEvent(EpisimUtils.getCorrectedTime(e.getTime(), iteration), ev.getPersonId(), ev.getVehicleId())
				);

			} else
				manager.processEvent(e);
		}
	}

	/**
	 * Helper class to read events one time.
	 */
	private final class EventReader implements BasicEventHandler {
		@Override
		public void handleEvent(Event event) {

			// Add coordinate information if not present
			if (event instanceof ActivityStartEvent) {
				ActivityStartEvent e = (ActivityStartEvent) event;
				Coord coord = e.getCoord();
				if (coord == null && scenario != null && scenario.getNetwork().getLinks().containsKey(e.getLinkId())) {
					Link link = scenario.getNetwork().getLinks().get(e.getLinkId());
					coord = link.getToNode().getCoord();
				}

				event = new ActivityStartEvent(e.getTime(), e.getPersonId(), e.getLinkId(), e.getFacilityId(), e.getActType(), coord);
			}

			events.add(event);
		}
	}

}

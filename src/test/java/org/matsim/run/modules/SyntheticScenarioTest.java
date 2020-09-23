package org.matsim.run.modules;

import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.HasFacilityId;
import org.matsim.episim.ReplayHandler;
import org.matsim.facilities.ActivityFacility;
import org.matsim.run.batch.SyntheticModel;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;


public class SyntheticScenarioTest {

	private static final Predicate<Id<ActivityFacility>> isOutside = e -> e.toString().startsWith("outside");
	private static final Predicate<Id<ActivityFacility>> isHome = e -> e.toString().startsWith("home");

	private Id<ActivityFacility> getFacility(Event e) {
		return ((HasFacilityId) e).getFacilityId();
	}

	@Test
	public void length() {

		ReplayHandler handler = new SyntheticScenario(
				new SyntheticModel.Params(1, 1, 1, 2,
						null, 1)).replayHandler();
		List<Event> events = handler.getEvents().get(DayOfWeek.MONDAY);

		assertThat(events)
				.hasSize(2 + 4);

		Set<Id<ActivityFacility>> facilities = events.stream().map(this::getFacility).collect(Collectors.toSet());

		assertThat(facilities)
				.filteredOn(isOutside)
				.hasSize(1);
	}


	@Test
	public void events() {

		ReplayHandler handler = new SyntheticScenario(
				new SyntheticModel.Params(10, 2, 2, 1,
						null, 1)).replayHandler();
		List<Event> events = handler.getEvents().get(DayOfWeek.MONDAY);

		assertThat(events)
				.hasSize(10 * 4);

		Set<Id<ActivityFacility>> facilities = events.stream().map(this::getFacility).collect(Collectors.toSet());

		assertThat(facilities)
				.filteredOn(isOutside)
				.hasSize(2);

		assertThat(facilities)
				.filteredOn(isHome)
				.hasSize(5);

	}

}

package org.matsim.episim.policy;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.ReplayHandler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class AdjustedPolicyTest {

	private ImmutableMap<String, Restriction> r;

	private AdjustedPolicy.ConfigBuilder createConfig() {

		AdjustedPolicy.ConfigBuilder configBuilder = AdjustedPolicy.config()
				.activityDurations(LocalDate.parse("2020-02-01"), Map.of(
						"home", 1000.0,
						"edu", 500.0,
						"leisure", 250.0
				))
				.activityDurations(LocalDate.parse("2020-02-02"), Map.of(
						"home", 1000.0,
						"edu", 200.0,
						"leisure", 250.0
				))
				.baseDays(Map.of(
						DayOfWeek.MONDAY, LocalDate.parse("2020-02-01")
				))
				.administrativePeriod("edu", LocalDate.MIN, LocalDate.MAX)
				.administrativePolicy(FixedPolicy.config()
						.restrict("2020-02-01", Restriction.of(0.0), "edu")
				);


		return configBuilder;

	}

	@Before
	public void setUp() throws Exception {
		r = ImmutableMap.of(
				"home", Restriction.none(),
				"edu", Restriction.none(),
				"leisure", Restriction.none()
		);
	}

	@Test
	public void config() {
		createConfig();
	}


	@Test
	public void restriction() {

		// hard coded events
		List<Event> events = List.of(
				// home
				new ActivityStartEvent(0, Id.createPersonId(1), null, null, "home", new Coord(0, 0)),
				new ActivityEndEvent(1000, Id.createPersonId(1), null, null, "home"),
				// edu
				new ActivityStartEvent(0, Id.createPersonId(1), null, null, "edu", new Coord(0, 0)),
				new ActivityEndEvent(500, Id.createPersonId(1), null, null, "edu"),
				// leisure
				new ActivityStartEvent(0, Id.createPersonId(1), null, null, "leisure", new Coord(0, 0)),
				new ActivityEndEvent(250, Id.createPersonId(1), null, null, "leisure")
		);

		ReplayHandler handler = new ReplayHandler(Map.of(
				DayOfWeek.MONDAY, events
		));

		AdjustedPolicy policy = new AdjustedPolicy(createConfig().build(), handler);

		policy.init(LocalDate.parse("2020-02-01"), r);

		// update
		policy.updateRestrictions(EpisimTestUtils.createReport("2020-02-02", 2), r);

		// TODO: assertions

	}
}

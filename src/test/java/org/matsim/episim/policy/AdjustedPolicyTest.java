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

import static org.assertj.core.api.Assertions.assertThat;

public class AdjustedPolicyTest {

	private ImmutableMap<String, Restriction> r;

	private AdjustedPolicy.ConfigBuilder createConfig() {
		return AdjustedPolicy.config()
				.outOfHomeDurations(Map.of(
						LocalDate.parse("2020-02-01"), 2000.0,
						LocalDate.parse("2020-02-02"), 400.0
				))
				.baseDays(Map.of(
						DayOfWeek.SUNDAY, LocalDate.parse("2020-02-01"),
						DayOfWeek.MONDAY, LocalDate.parse("2020-02-01"),
						DayOfWeek.TUESDAY, LocalDate.parse("2020-02-01")
				))
				.administrativePeriod("edu", LocalDate.MIN, LocalDate.MAX)
				.administrativePolicy(FixedPolicy.config()
						.restrict("2020-02-02", Restriction.of(0.0), "edu")
				);

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
				new ActivityEndEvent(1500, Id.createPersonId(1), null, null, "edu"),
				// leisure
				new ActivityStartEvent(0, Id.createPersonId(1), null, null, "leisure", new Coord(0, 0)),
				new ActivityEndEvent(500, Id.createPersonId(1), null, null, "leisure")
		);

		ReplayHandler handler = new ReplayHandler(Map.of(
				DayOfWeek.SUNDAY, events,
				DayOfWeek.MONDAY, events
		));

		AdjustedPolicy policy = new AdjustedPolicy(createConfig().build(), handler);

		policy.init(LocalDate.parse("2020-02-01"), r);

		// update
		policy.updateRestrictions(EpisimTestUtils.createReport("2020-02-02", 2), r);


		assertThat(r.get("home").getRemainingFraction()).isEqualTo(1.0);
		assertThat(r.get("edu").getRemainingFraction()).isEqualTo(0);
		assertThat(r.get("leisure").getRemainingFraction()).isEqualTo(0.8);

	}
}

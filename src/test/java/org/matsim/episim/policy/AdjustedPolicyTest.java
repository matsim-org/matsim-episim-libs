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
				.outOfHomeFractions(Map.of(
						LocalDate.parse("2020-02-01"), 1.0,
						LocalDate.parse("2020-02-02"), 0.2
				))
				.administrativePeriod("edu", LocalDate.MIN, LocalDate.MAX)
				.administrativePeriod("leisure", LocalDate.parse("2020-03-01"), LocalDate.parse("2020-03-05"))
				.administrativePolicy(FixedPolicy.config()
						.restrict("2020-02-02", Restriction.of(0.0), "edu")
						.restrict("2020-03-01", Restriction.of(0.4), "leisure")
				);

	}

	private ReplayHandler createHandler() {

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

		return new ReplayHandler(Map.of(
				DayOfWeek.MONDAY, events,
				DayOfWeek.TUESDAY, events,
				DayOfWeek.WEDNESDAY, events,
				DayOfWeek.THURSDAY, events,
				DayOfWeek.FRIDAY, events,
				DayOfWeek.SATURDAY, events,
				DayOfWeek.SUNDAY, events
		));
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

		ReplayHandler handler = createHandler();

		AdjustedPolicy policy = new AdjustedPolicy(createConfig().build(), handler);

		policy.init(LocalDate.parse("2020-02-01"), r);

		// update
		policy.updateRestrictions(EpisimTestUtils.createReport("2020-02-02", 2), r);

		// the total out of home duration is 2000
		// according to data 0.2, it should be 400 on the next day
		// 1500 is already "consumed" by edu closure, meaning 100 are left
		// these are put into leisure by applying a fraction of 0.8 (x 500)

		assertThat(r.get("home").getRemainingFraction()).isEqualTo(1.0);
		assertThat(r.get("edu").getRemainingFraction()).isEqualTo(0);
		assertThat(r.get("leisure").getRemainingFraction()).isEqualTo(0.8);

	}

	@Test
	public void adminInterval() {

		ReplayHandler handler = createHandler();

		AdjustedPolicy policy = new AdjustedPolicy(createConfig().build(), handler);

		policy.init(LocalDate.parse("2020-02-01"), r);

		assertThat(r.get("leisure").getRemainingFraction()).isEqualTo(1.0);

		policy.updateRestrictions(EpisimTestUtils.createReport("2020-02-28", 1), r);

		assertThat(r.get("leisure").getRemainingFraction()).isEqualTo(1.0);

		policy.updateRestrictions(EpisimTestUtils.createReport("2020-03-01", 1), r);

		assertThat(r.get("leisure").getRemainingFraction()).isEqualTo(0.4);

		policy.updateRestrictions(EpisimTestUtils.createReport("2020-03-05", 1), r);

		assertThat(r.get("leisure").getRemainingFraction()).isEqualTo(1.0);


	}
}

package org.matsim.episim.policy;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import org.junit.Before;
import org.junit.Test;
import org.matsim.episim.EpisimTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

public class AdaptivePolicyTest {

	private ImmutableMap<String, Restriction> r;

	@Before
	public void setUp() {
		r = ImmutableMap.of(
				"home", Restriction.none(),
				"work", Restriction.none(),
				"pt", Restriction.none()
		);
	}

	@Test
	public void policy() {

		Config config = AdaptivePolicy.config()
				.lockdownAt(50)
				.openAt(35)
				.lockdownPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.of(0.2), "work")
				)
				.openPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.of(0.8), "work"))
				.build();

		AdaptivePolicy policy = new AdaptivePolicy(config);
		LocalDate date = LocalDate.now();
		int day = 0;

		policy.init(date, r);

		policy.updateRestrictions(EpisimTestUtils.createReport(date, day, 40), r);

		assertThat(r.get("home").getRemainingFraction()).isEqualTo(1.0);
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(1.0);

		day++;

		policy.updateRestrictions(EpisimTestUtils.createReport(date.plusDays(day), day, 50), r);

		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.2);

		for (int i = 1; i < 15; i++) {
			policy.updateRestrictions(EpisimTestUtils.createReport(date.plusDays(day + i), day + i, 20), r);
		}

		// open after 14 days of lockdown
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.8);

	}
}

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
				"edu", Restriction.none(),
				"pt", Restriction.none()
		);
	}

	@Test
	public void policy() {

		Config config = AdaptivePolicy.config()
				.incidenceTrigger(35, 50, "work")
				.incidenceTrigger(50, 50, "edu")
				.initialPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.of(0.9), "work")
				)
				.restrictedPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.of(0.2), "work")
						.restrict(LocalDate.MIN, Restriction.of(0.0), "edu")
				)
				.openPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.of(0.8), "work")
						.restrict(LocalDate.MIN, Restriction.of(1.0), "edu")
				)
				.build();

		AdaptivePolicy policy = new AdaptivePolicy(config);
		LocalDate date = LocalDate.now();
		int day = 0;
		int showingSymptoms = 0;

		policy.init(date, r);

		assertThat(r.get("home").getRemainingFraction()).isEqualTo(1.0);
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.9);

		for (; day < 8; day++) {
			showingSymptoms += 60 / 7;
			policy.updateRestrictions(EpisimTestUtils.createReport(date.plusDays(day), day, showingSymptoms), r);
		}

		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.2);
		assertThat(r.get("edu").getRemainingFraction()).isEqualTo(0.0);

		for (; day < 30; day++) {
			showingSymptoms += 23 / 7;
			policy.updateRestrictions(EpisimTestUtils.createReport(date.plusDays(day), day, showingSymptoms), r);
		}

		// open after 14 days of lockdown
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.8);
		assertThat(r.get("edu").getRemainingFraction()).isEqualTo(1.0);

	}
}

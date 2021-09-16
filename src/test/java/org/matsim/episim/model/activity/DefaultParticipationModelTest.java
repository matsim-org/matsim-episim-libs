package org.matsim.episim.model.activity;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.policy.RestrictionTest;

import java.time.DayOfWeek;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;


public class DefaultParticipationModelTest {

	private DefaultParticipationModel model;
	private ImmutableMap<String, Restriction> r;

	@Before
	public void setUp() {

		Config config = EpisimTestUtils.createTestConfig();
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay);

		model = new DefaultParticipationModel(new SplittableRandom(0), episimConfig, ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class));
		r = ImmutableMap.of(
				"home", Restriction.none(),
				"work", Restriction.none(),
				"pt", Restriction.none()
		);

		model.setRestrictionsForIteration(0, r);
	}

	@Test
	public void susceptible() {

		EpisimPerson p = EpisimTestUtils.createPerson("home", "work", "home");

		model.updateParticipation(p, p.getActivityParticipation(), 0, p.getActivities(DayOfWeek.MONDAY));

		assertThat(p.getActivityParticipation().get(1))
				.isEqualTo(true);

		RestrictionTest.update(r.get("work"), Restriction.ofSusceptibleRf(0.0));

		model.updateParticipation(p, p.getActivityParticipation(), 0, p.getActivities(DayOfWeek.MONDAY));

		assertThat(p.getActivityParticipation().get(1))
				.isEqualTo(false);


		p = EpisimTestUtils.createPerson("home", "work", "home");
		p.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.generic, 0);
		model.setRestrictionsForIteration(30, r);

		model.updateParticipation(p, p.getActivityParticipation(), 0, p.getActivities(DayOfWeek.MONDAY));

		assertThat(p.getActivityParticipation().get(1))
				.isEqualTo(true);

	}
}

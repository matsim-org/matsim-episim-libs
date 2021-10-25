package org.matsim.episim.model;

import org.junit.Before;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Map;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class InfectionModelWithSeasonalityTest {

	private InfectionModel model;
	private EpisimReporting reporting;
	private Map<String, Restriction> restrictions;
	private EpisimConfigGroup.InfectionParams act;

	@Before
	public void setUp() throws Exception {

		SplittableRandom rnd = new SplittableRandom(0);
		Config config = EpisimTestUtils.createTestConfig();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		act = episimConfig.getInfectionParam("c10");
		restrictions = episimConfig.createInitialRestrictions();
		reporting = Mockito.mock(EpisimReporting.class);
		model = new InfectionModelWithSeasonality(new DefaultFaceMaskModel(rnd), rnd, config, reporting);
	}

	@Test
	public void vaccination() {

		EpisimPerson infector = EpisimTestUtils.createPerson(reporting);
		infector.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.contagious);

		EpisimPerson target = EpisimTestUtils.createPerson(reporting);
		target.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.generic, 0);

		model.setIteration(1);
		double prob = model.calcInfectionProbability(target, infector, restrictions, act, act,
				act.getContactIntensity(), Duration.ofHours(1).getSeconds());

		model.setIteration(7);
		double prob2 = model.calcInfectionProbability(target, infector, restrictions, act, act,
				act.getContactIntensity(), Duration.ofHours(1).getSeconds());

		assertThat(prob2)
				.isLessThan(prob);

		model.setIteration(40);
		double prob3 = model.calcInfectionProbability(target, infector, restrictions, act, act,
				act.getContactIntensity(), Duration.ofHours(1).getSeconds());

		assertThat(prob3)
				.isLessThan(prob2);

		model.setIteration(45);
		double prob4 = model.calcInfectionProbability(target, infector, restrictions, act, act,
				act.getContactIntensity(), Duration.ofHours(1).getSeconds());

		assertThat(prob4)
				.isEqualTo(prob3);


	}
}

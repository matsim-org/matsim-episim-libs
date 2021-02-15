package org.matsim.episim.model;

import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class AgeAndProgressionDependentInfectionModelWithSeasonalityTest {

	private ConfigurableProgressionModel progression;
	private AgeAndProgressionDependentInfectionModelWithSeasonality model;
	private EpisimReporting reporting;

	@Before
	public void setUp() throws Exception {
		SplittableRandom rnd = new SplittableRandom(0);
		Config config = EpisimTestUtils.createTestConfig();
		progression = new ConfigurableProgressionModel(rnd,
				ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class),
				ConfigUtils.addOrGetModule(config, TracingConfigGroup.class));
		reporting = EpisimTestUtils.getReporting();
		model = new AgeAndProgressionDependentInfectionModelWithSeasonality(
				new DefaultFaceMaskModel(rnd),
				progression,
				config,
				reporting,
				rnd
		);
	}

	@Test
	public void infectivity() {

		EpisimPerson p = drawPersonWithState(EpisimPerson.DiseaseStatus.contagious, EpisimPerson.DiseaseStatus.showingSymptoms);

		model.setIteration(1);
		assertThat(model.getInfectivity(p))
				.isCloseTo(0.98, Offset.offset(0.01));

		model.setIteration(2);
		assertThat(model.getInfectivity(p))
				.isCloseTo(1, Offset.offset(0.01));

		progression.updateState(p, 2);

		model.setIteration(3);
		assertThat(model.getInfectivity(p))
				.isCloseTo(0.98, Offset.offset(0.01));

		model.setIteration(4);
		assertThat(model.getInfectivity(p))
				.isCloseTo(0.92, Offset.offset(0.01));

	}


	private EpisimPerson drawPersonWithState(EpisimPerson.DiseaseStatus state, EpisimPerson.DiseaseStatus nextState) {
		// Draw person with showing symptoms
		while (true) {
			EpisimPerson p = EpisimTestUtils.createPerson(reporting);
			p.setDiseaseStatus(0, state);
			progression.updateState(p, 1);
			if (progression.getNextDiseaseStatus(p.getPersonId()) == nextState)
				return p;
		}
	}

}

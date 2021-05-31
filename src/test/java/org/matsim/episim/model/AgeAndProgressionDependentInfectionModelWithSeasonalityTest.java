package org.matsim.episim.model;

import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.model.progression.DefaultDiseaseStatusTransitionModel;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.matsim.episim.model.Transition.to;

public class AgeAndProgressionDependentInfectionModelWithSeasonalityTest {

	private ConfigurableProgressionModel progression;
	private AgeAndProgressionDependentInfectionModelWithSeasonality model;
	private EpisimReporting reporting;

	@Before
	public void setUp() throws Exception {
		SplittableRandom rnd = new SplittableRandom(0);
		Config config = EpisimTestUtils.createTestConfig();

		ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class)
				.setProgressionConfig(Transition.config()
						.from(DiseaseStatus.infectedButNotContagious,
								to(DiseaseStatus.contagious, Transition.fixed(0)
								))
						.from(DiseaseStatus.contagious,
								to(DiseaseStatus.showingSymptoms, Transition.fixed(4)),
								to(DiseaseStatus.recovered, Transition.fixed(10))
						)
						.from(DiseaseStatus.showingSymptoms,
								to(DiseaseStatus.recovered, Transition.fixed(7))
						)
						.build());

		progression = new ConfigurableProgressionModel(rnd,
				ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class),
				ConfigUtils.addOrGetModule(config, TracingConfigGroup.class),
				new DefaultDiseaseStatusTransitionModel(rnd,
						ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class),
						ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class)
				)
		);
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
	public void showingSymptoms() {

		EpisimPerson p = drawPersonWithState(DiseaseStatus.infectedButNotContagious, DiseaseStatus.showingSymptoms);

		model.setIteration(1);

		Offset<Double> offset = Offset.offset(0.01);

		assertThat(model.getInfectivity(p))
				.isCloseTo(0.40, offset);

		model.setIteration(3);
		assertThat(model.getInfectivity(p))
				.isCloseTo(0.85, offset);

		model.setIteration(5);
		assertThat(model.getInfectivity(p))
				.isCloseTo(0.98, offset);

		progression.updateState(p, 4);

		model.setIteration(7);
		assertThat(model.getInfectivity(p))
				.isCloseTo(0.62, offset);

		model.setIteration(9);
		assertThat(model.getInfectivity(p))
				.isCloseTo(0.22, offset);

	}

	@Test
	public void nonSymptomatic() {

		EpisimPerson p = drawPersonWithState(DiseaseStatus.contagious, DiseaseStatus.recovered);

		model.setIteration(1);

		Offset<Double> offset = Offset.offset(0.01);

		assertThat(model.getInfectivity(p))
				.isCloseTo(0.22, offset);

		model.setIteration(4);
		assertThat(model.getInfectivity(p))
				.isCloseTo(0.84, offset);

		model.setIteration(5);
		assertThat(model.getInfectivity(p))
				.isCloseTo(0.98, offset);

		model.setIteration(6);
		assertThat(model.getInfectivity(p))
				.isCloseTo(0.98, offset);

		model.setIteration(9);
		assertThat(model.getInfectivity(p))
				.isCloseTo(0.40, offset);

	}


	private EpisimPerson drawPersonWithState(DiseaseStatus state, DiseaseStatus nextState) {
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

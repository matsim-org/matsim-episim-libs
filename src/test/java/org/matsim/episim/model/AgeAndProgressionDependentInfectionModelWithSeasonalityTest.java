package org.matsim.episim.model;

import org.matsim.episim.EpisimTestUtils;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.model.progression.DefaultDiseaseStatusTransitionModel;

import org.matsim.episim.util.EpisimSplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.matsim.episim.model.Transition.to;

public class AgeAndProgressionDependentInfectionModelWithSeasonalityTest {

	private ConfigurableProgressionModel progression;
	private AgeAndProgressionDependentInfectionModelWithSeasonality model;
	private EpisimReporting reporting;
	private Config config;

	@Before
	public void setUp() throws Exception {
		EpisimSplittableRandom rnd = new EpisimSplittableRandom(0);
		config = EpisimTestUtils.createTestConfig();

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
								to(DiseaseStatus.seriouslySick, Transition.fixed(7)),
								to(DiseaseStatus.recovered, Transition.fixed(7))
						)
						.build());

		progression = new ConfigurableProgressionModel(rnd,
				ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class),
				ConfigUtils.addOrGetModule(config, TracingConfigGroup.class),
				ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class),
				ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class),
				new DefaultDiseaseStatusTransitionModel(rnd, EpisimTestUtils.noImmunity(),
						ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class),
						ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class),
						ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class)
				)
		);
		reporting = EpisimTestUtils.getReporting();
		model = new AgeAndProgressionDependentInfectionModelWithSeasonality(
				new DefaultFaceMaskModel(rnd),
				progression,
				EpisimTestUtils.noImmunity(),
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

	/**
	 * A configured profile replaces the built-in curve and is read with a signed offset: negative before symptom onset
	 * (the built-in curve is read mirrored there), relative to the middle of the contagious period without symptoms.
	 */
	@Test
	public void configuredInfectivityProfile() {
		ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class).getParams(Pathogen.SARS_COV_2)
				.setInfectivityProfile(java.util.Map.of(-4, 0.1, -2, 0.3, 0, 1.0, 3, 0.6, 5, 0.2));
		Offset<Double> offset = Offset.offset(1e-9);

		// contagious from day 1, symptoms 4 days later on day 5, see setUp
		EpisimPerson symptomatic = drawPersonWithState(DiseaseStatus.infectedButNotContagious, DiseaseStatus.showingSymptoms);
		model.setIteration(1);
		assertThat(model.getInfectivity(symptomatic)).isCloseTo(0.1, offset);
		model.setIteration(3);
		assertThat(model.getInfectivity(symptomatic)).isCloseTo(0.3, offset);

		progression.updateState(symptomatic, 5);
		assertThat(symptomatic.getDiseaseStatus()).isEqualTo(DiseaseStatus.showingSymptoms);
		model.setIteration(5);
		assertThat(model.getInfectivity(symptomatic)).isCloseTo(1.0, offset);
		model.setIteration(8);
		assertThat(model.getInfectivity(symptomatic)).isCloseTo(0.6, offset);
		model.setIteration(9);
		assertThat(model.getInfectivity(symptomatic)).as("linear between days 3 and 5").isCloseTo(0.4, offset);
		model.setIteration(11);
		assertThat(model.getInfectivity(symptomatic)).as("outside the profile").isZero();

		// contagious for 10 days without symptoms: offsets are relative to day 5
		EpisimPerson asymptomatic = drawPersonWithState(DiseaseStatus.contagious, DiseaseStatus.recovered);
		model.setIteration(1);
		assertThat(model.getInfectivity(asymptomatic)).isCloseTo(0.1, offset);
		model.setIteration(5);
		assertThat(model.getInfectivity(asymptomatic)).isCloseTo(1.0, offset);
		model.setIteration(8);
		assertThat(model.getInfectivity(asymptomatic)).isCloseTo(0.6, offset);
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

package org.matsim.episim.model;

import org.junit.Before;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.util.EpisimSplittableRandom;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class InfectionModelWithSeasonalityTest {

	private InfectionModel model;
	private Config config;
	private EpisimReporting reporting;
	private Map<String, Restriction> restrictions;
	private EpisimConfigGroup.InfectionParams act;

	@Before
	public void setUp() throws Exception {

		EpisimSplittableRandom rnd = new EpisimSplittableRandom(0);
		config = EpisimTestUtils.createTestConfig();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		act = episimConfig.getInfectionParam("c10");
		restrictions = episimConfig.createInitialRestrictions();
		reporting = Mockito.mock(EpisimReporting.class);
		model = new InfectionModelWithSeasonality(new DefaultFaceMaskModel(rnd),
			new LegacyCurveImmunityModel(ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class)), rnd, config, reporting);
	}

	@Test
	public void vaccination() {

		EpisimPerson infector = EpisimTestUtils.createPerson(reporting);
		infector.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.contagious);

		EpisimPerson target = EpisimTestUtils.createPerson(reporting);
		target.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.generic, 0);

		model.setIteration(1);
		double prob = model.calcInfectionProbability(target, infector, restrictions, act, act,
				TransmissionWeights.RESPIRATORY_ONLY,
				act.getContactIntensity(), Duration.ofHours(1).getSeconds());

		model.setIteration(7);
		double prob2 = model.calcInfectionProbability(target, infector, restrictions, act, act,
			TransmissionWeights.RESPIRATORY_ONLY,
				act.getContactIntensity(), Duration.ofHours(1).getSeconds());

		assertThat(prob2)
				.isLessThan(prob);

		model.setIteration(40);
		double prob3 = model.calcInfectionProbability(target, infector, restrictions, act, act,
			TransmissionWeights.RESPIRATORY_ONLY,
				act.getContactIntensity(), Duration.ofHours(1).getSeconds());

		assertThat(prob3)
				.isLessThan(prob2);

		model.setIteration(45);
		double prob4 = model.calcInfectionProbability(target, infector, restrictions, act, act,
			TransmissionWeights.RESPIRATORY_ONLY,
				act.getContactIntensity(), Duration.ofHours(1).getSeconds());

		assertThat(prob4)
				.isEqualTo(prob3);


	}

	@Test
	public void routeWeightsScaleProbabilityByPathogenTransmissibility() {

		EpisimPerson infector = EpisimTestUtils.createPerson(reporting);
		infector.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.contagious);
		EpisimPerson target = EpisimTestUtils.createPerson(reporting);

		model.setIteration(1);
		long seconds = Duration.ofHours(1).getSeconds();

		double respiratoryOnly = model.calcInfectionProbability(target, infector, restrictions, act, act,
				TransmissionWeights.RESPIRATORY_ONLY, act.getContactIntensity(), seconds);
		assertThat(respiratoryOnly).isPositive();

		// half the contact's exposure goes through direct contact, which SARS-CoV-2 does not use -> strictly lower
		double halfViaDirectContact = model.calcInfectionProbability(target, infector, restrictions, act, act,
				TransmissionWeights.parse("respiratory=0.5;directContact=0.5"), act.getContactIntensity(), seconds);
		assertThat(halfViaDirectContact).isPositive().isLessThan(respiratoryOnly);

		// all exposure through direct contact -> pathogen cannot transmit -> no infection
		double directContactOnly = model.calcInfectionProbability(target, infector, restrictions, act, act,
				TransmissionWeights.parse("directContact=1.0"), act.getContactIntensity(), seconds);
		assertThat(directContactOnly).isZero();
	}

	@Test
	public void directContactCapablePathogenTransmitsThroughDirectContact() {

		ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class)
				.getParams(Pathogen.SARS_COV_2)
				.setRouteTransmissibility(TransmissionWeights.parse("respiratory=0.5;directContact=0.5"));

		EpisimPerson infector = EpisimTestUtils.createPerson(reporting);
		infector.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.contagious);
		EpisimPerson target = EpisimTestUtils.createPerson(reporting);

		model.setIteration(1);
		long seconds = Duration.ofHours(1).getSeconds();

		double directContactOnly = model.calcInfectionProbability(target, infector, restrictions, act, act,
				TransmissionWeights.parse("directContact=1.0"), act.getContactIntensity(), seconds);
		assertThat(directContactOnly).isPositive();
	}
}

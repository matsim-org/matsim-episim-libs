package org.matsim.episim;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.PathogenConfigGroup.PathogenParams;
import org.matsim.episim.model.ContactTransmissionType;
import org.matsim.episim.model.Pathogen;
import org.matsim.episim.model.TransmissionWeights;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
import org.matsim.episim.model.progression.AntibodyDependentTransitionModel;
import org.matsim.episim.util.EpisimSplittableRandom;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PathogenConfigGroupTest {

	private static final Pathogen INFLUENZA = new Pathogen("INFLUENZA");

	/**
	 * (1) The auto-created SARS-CoV-2 defaults carry exactly the values that used to be hard-coded in the
	 * transition models.
	 */
	@Test
	public void sarsCov2DefaultsMatchLegacyHardcodedValues() {
		PathogenConfigGroup group = new PathogenConfigGroup();

		// age-independent variant -> DefaultDiseaseStatusTransitionModel / AntibodyDependentTransitionModel
		PathogenParams flat = group.getParams(Pathogen.SARS_COV_2, false);
		for (int age : new int[]{0, 20, 42, 80, 120}) {
			assertThat(flat.getShowingSymptomsProbability(age)).isEqualTo(0.8);
			assertThat(flat.getSeriouslySickProbability(age)).isEqualTo(0.05625);
			assertThat(flat.getCriticalProbability(age)).isEqualTo(0.25);
			assertThat(flat.getDeathProbability(age)).isEqualTo(0.0);
		}

		// age-dependent variant -> AgeDependentDiseaseStatusTransitionModel (pre-hospitalFactor)
		PathogenParams byAge = group.getParams(Pathogen.SARS_COV_2, true);
		assertThat(byAge.getShowingSymptomsProbability(30)).isEqualTo(0.8);
		assertThat(byAge.getDeathProbability(30)).isEqualTo(0.0);

		assertThat(byAge.getSeriouslySickProbability(4)).isEqualTo(4.0 / 100);
		assertThat(byAge.getSeriouslySickProbability(5)).isEqualTo(1.1 / 100);
		assertThat(byAge.getSeriouslySickProbability(14)).isEqualTo(1.1 / 100);
		assertThat(byAge.getSeriouslySickProbability(15)).isEqualTo(2.4 / 100);
		assertThat(byAge.getSeriouslySickProbability(34)).isEqualTo(2.4 / 100);
		assertThat(byAge.getSeriouslySickProbability(35)).isEqualTo(5.6 / 100);
		assertThat(byAge.getSeriouslySickProbability(59)).isEqualTo(5.6 / 100);
		assertThat(byAge.getSeriouslySickProbability(60)).isEqualTo(23. / 100);
		assertThat(byAge.getSeriouslySickProbability(79)).isEqualTo(23. / 100);
		assertThat(byAge.getSeriouslySickProbability(80)).isEqualTo(36. / 100);
		assertThat(byAge.getSeriouslySickProbability(95)).isEqualTo(36. / 100);

		assertThat(byAge.getCriticalProbability(4)).isEqualTo(7.0 / 100);
		assertThat(byAge.getCriticalProbability(5)).isEqualTo(0.0 / 100);
		assertThat(byAge.getCriticalProbability(15)).isEqualTo(15.0 / 100);
		assertThat(byAge.getCriticalProbability(35)).isEqualTo(30.0 / 100);
		assertThat(byAge.getCriticalProbability(60)).isEqualTo(41. / 100);
		assertThat(byAge.getCriticalProbability(80)).isEqualTo(27. / 100);
		assertThat(byAge.getCriticalProbability(90)).isEqualTo(27. / 100);
	}

	/**
	 * (2) A value applies from its age bucket up to the next boundary; a single entry is a constant.
	 */
	@Test
	public void ageBucketSelectionIsAStepFunction() {
		PathogenParams p = new PathogenParams();
		p.setPathogen(INFLUENZA);
		p.setSeriouslySickProbabilityByAge(Map.of(0, 0.5, 5, 0.6, 18, 0.7, 65, 0.8));

		assertThat(p.getSeriouslySickProbability(0)).isEqualTo(0.5);
		assertThat(p.getSeriouslySickProbability(4)).isEqualTo(0.5);
		assertThat(p.getSeriouslySickProbability(5)).isEqualTo(0.6);
		assertThat(p.getSeriouslySickProbability(17)).isEqualTo(0.6);
		assertThat(p.getSeriouslySickProbability(18)).isEqualTo(0.7);
		assertThat(p.getSeriouslySickProbability(64)).isEqualTo(0.7);
		assertThat(p.getSeriouslySickProbability(65)).isEqualTo(0.8);
		assertThat(p.getSeriouslySickProbability(200)).isEqualTo(0.8);

		// constant probability for all ages
		p.setCriticalProbabilityByAge(Map.of(0, 0.42));
		assertThat(p.getCriticalProbability(0)).isEqualTo(0.42);
		assertThat(p.getCriticalProbability(37)).isEqualTo(0.42);
		assertThat(p.getCriticalProbability(130)).isEqualTo(0.42);
	}

	/**
	 * (3) Probabilities outside [0, 1] are rejected, both via the string setter and the map setter.
	 */
	@Test
	public void probabilitiesOutsideUnitIntervalAreRejected() {
		PathogenParams p = new PathogenParams();

		assertThatThrownBy(() -> p.setCriticalProbabilityByAge("0=1.5"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> p.setCriticalProbabilityByAge("0=-0.1"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> p.setSeriouslySickProbabilityByAge("0=0.2;40=2.0"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> p.setDeathProbabilityByAge(Map.of(0, -0.0001)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> p.setShowingSymptomsProbabilityByAge(Map.of(0, 1.0001)))
				.isInstanceOf(IllegalArgumentException.class);

		// boundary values are accepted
		p.setDeathProbabilityByAge(Map.of(0, 0.0));
		p.setShowingSymptomsProbabilityByAge(Map.of(0, 1.0));
		assertThat(p.getDeathProbability(10)).isEqualTo(0.0);
		assertThat(p.getShowingSymptomsProbability(10)).isEqualTo(1.0);
	}

	/**
	 * (4) The configuration survives an XML round-trip without loss.
	 */
	@Test
	public void serializesToXmlAndBack() throws IOException {
		PathogenConfigGroup group = new PathogenConfigGroup();
		Config config = ConfigUtils.createConfig(group);

		PathogenParams flu = group.getOrAddParams(INFLUENZA, false);
		flu.setShowingSymptomsProbabilityByAge(Map.of(0, 0.4, 60, 0.55));
		flu.setSeriouslySickProbabilityByAge(Map.of(0, 0.01, 70, 0.2));
		flu.setCriticalProbabilityByAge(Map.of(0, 0.05));
		flu.setDeathProbabilityByAge(Map.of(0, 0.0, 80, 0.1));

		File tmp = File.createTempFile("matsim", "config");
		tmp.deleteOnExit();
		ConfigUtils.writeConfig(config, tmp.toString());

		PathogenConfigGroup copy = new PathogenConfigGroup();
		ConfigUtils.loadConfig(tmp.toString(), copy);

		// SARS-CoV-2 defaults still there, both variants
		assertThat(copy.getParams(Pathogen.SARS_COV_2, false).getSeriouslySickProbability(30)).isEqualTo(0.05625);
		assertThat(copy.getParams(Pathogen.SARS_COV_2, true).getSeriouslySickProbability(80)).isEqualTo(36. / 100);

		PathogenParams reloaded = copy.getParams(INFLUENZA, false);
		assertThat(reloaded.getShowingSymptomsProbability(10)).isEqualTo(0.4);
		assertThat(reloaded.getShowingSymptomsProbability(60)).isEqualTo(0.55);
		assertThat(reloaded.getSeriouslySickProbability(69)).isEqualTo(0.01);
		assertThat(reloaded.getSeriouslySickProbability(70)).isEqualTo(0.2);
		assertThat(reloaded.getCriticalProbability(50)).isEqualTo(0.05);
		assertThat(reloaded.getDeathProbability(80)).isEqualTo(0.1);
	}

	/**
	 * (5) A brand new pathogen can be introduced through configuration alone.
	 */
	@Test
	public void newPathogenCanBeAddedThroughConfigurationOnly() {
		PathogenConfigGroup group = new PathogenConfigGroup();

		assertThat(group.hasParams(INFLUENZA, false)).isFalse();

		group.getOrAddParams(INFLUENZA, false)
				.setShowingSymptomsProbabilityByAge(Map.of(0, 0.3, 60, 0.6));

		assertThat(group.hasParams(INFLUENZA, false)).isTrue();
		assertThat(group.getParams(INFLUENZA).getShowingSymptomsProbability(15)).isEqualTo(0.3);
		assertThat(group.getParams(INFLUENZA).getShowingSymptomsProbability(70)).isEqualTo(0.6);
	}

	/**
	 * (6) The transition model looks up the config of the pathogen of the person's current strain, and the
	 * age-dependent model uses the age-dependent variant.
	 */
	@Test
	public void transitionModelUsesPathogenOfCurrentStrain() {
		EpisimTestUtils.resetIds();
		EpisimSplittableRandom rnd = new EpisimSplittableRandom(1);

		PathogenConfigGroup pathogenConfig = new PathogenConfigGroup();
		pathogenConfig.getOrAddParams(INFLUENZA, false).setSeriouslySickProbabilityByAge(Map.of(0, 0.42));
		pathogenConfig.getOrAddParams(INFLUENZA, true).setSeriouslySickProbabilityByAge(Map.of(0, 0.1, 60, 0.9));

		VirusStrainConfigGroup strainConfig = new VirusStrainConfigGroup();
		VaccinationConfigGroup vaccinationConfig = new VaccinationConfigGroup();
		EpisimConfigGroup episimConfig = new EpisimConfigGroup();

		AntibodyDependentTransitionModel flat =
				new AntibodyDependentTransitionModel(rnd, vaccinationConfig, strainConfig, pathogenConfig);
		AgeDependentDiseaseStatusTransitionModel byAge =
				new AgeDependentDiseaseStatusTransitionModel(rnd, episimConfig, vaccinationConfig, strainConfig, pathogenConfig);

		VirusStrain fluStrain = VirusStrain.of(INFLUENZA, "FLU_H1N1");

		EpisimPerson covidPerson = EpisimTestUtils.createPerson(true, 70);
		covidPerson.setInitialInfection(0, VirusStrain.SARS_CoV_2);
		EpisimPerson fluPerson = EpisimTestUtils.createPerson(true, 70);
		fluPerson.setInitialInfection(0, fluStrain);

		assertThat(flat.getProbaOfTransitioningToSeriouslySick(covidPerson)).isEqualTo(0.05625);
		assertThat(flat.getProbaOfTransitioningToSeriouslySick(fluPerson)).isEqualTo(0.42);

		// age-dependent model: influenza age table entry for age 70 is 0.9, hospitalFactor defaults to 1.0
		assertThat(byAge.getProbaOfTransitioningToSeriouslySick(fluPerson)).isEqualTo(0.9);
		// SARS-CoV-2 age table entry for age 70 is 0.23
		assertThat(byAge.getProbaOfTransitioningToSeriouslySick(covidPerson)).isEqualTo(23. / 100);
	}

	/**
	 * (7) An unknown pathogen without configuration fails with a clear message instead of silently reusing
	 * the SARS-CoV-2 values.
	 */
	@Test
	public void unknownPathogenWithoutConfigFailsClearly() {
		EpisimTestUtils.resetIds();
		EpisimSplittableRandom rnd = new EpisimSplittableRandom(1);

		PathogenConfigGroup pathogenConfig = new PathogenConfigGroup();
		AntibodyDependentTransitionModel model = new AntibodyDependentTransitionModel(
				rnd, new VaccinationConfigGroup(), new VirusStrainConfigGroup(), pathogenConfig);

		VirusStrain rsvStrain = VirusStrain.of(new Pathogen("RSV"), "RSV_A");
		EpisimPerson person = EpisimTestUtils.createPerson(true, 40);
		person.setInitialInfection(0, rsvStrain);

		assertThatThrownBy(() -> model.getProbaOfTransitioningToSeriouslySick(person))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("RSV");

		assertThatThrownBy(() -> pathogenConfig.getParams(new Pathogen("RSV")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("RSV");
	}

	/**
	 * (8) Strain / vaccination / antibody modifiers still apply on top of the configured base probability.
	 */
	@Test
	public void strainAndImmunityModifiersStillApply() {
		EpisimTestUtils.resetIds();
		EpisimSplittableRandom rnd = new EpisimSplittableRandom(4);

		PathogenConfigGroup pathogenConfig = new PathogenConfigGroup();
		VaccinationConfigGroup vaccinationConfig = new VaccinationConfigGroup();
		EpisimConfigGroup episimConfig = new EpisimConfigGroup();

		int seriousWithFullFactor = countSeriouslySick(rnd, pathogenConfig, vaccinationConfig, episimConfig, 1.0);
		int seriousWithZeroFactor = countSeriouslySick(rnd, pathogenConfig, vaccinationConfig, episimConfig, 0.0);

		// base probability is 0.05625 -> a few percent of 20k people
		assertThat(seriousWithFullFactor).isBetween(900, 1400);
		// strain factorSeriouslySick == 0 wipes the transition out entirely
		assertThat(seriousWithZeroFactor).isZero();
	}

	/**
	 * (9) Route transmissibility defaults to respiratory-only, for the SARS-CoV-2 defaults and for a
	 * pathogen added through configuration.
	 */
	@Test
	public void routeTransmissibilityDefaultsToRespiratoryOnly() {
		PathogenConfigGroup group = new PathogenConfigGroup();

		for (boolean ageDependent : new boolean[]{false, true}) {
			TransmissionWeights w = group.getParams(Pathogen.SARS_COV_2, ageDependent).getRouteTransmissibility();
			assertThat(w.get(ContactTransmissionType.RESPIRATORY)).isEqualTo(1.0);
			assertThat(w.get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.0);
			assertThat(w.get(ContactTransmissionType.FOMITE)).isEqualTo(0.0);
		}

		TransmissionWeights fresh = group.getOrAddParams(INFLUENZA, false).getRouteTransmissibility();
		assertThat(fresh.get(ContactTransmissionType.RESPIRATORY)).isEqualTo(1.0);
		assertThat(fresh.sum()).isEqualTo(1.0);
	}

	/**
	 * (10) A non-default route mix survives an XML round-trip.
	 */
	@Test
	public void routeTransmissibilitySurvivesXmlRoundTrip() throws IOException {
		PathogenConfigGroup group = new PathogenConfigGroup();
		Config config = ConfigUtils.createConfig(group);

		PathogenParams flu = group.getOrAddParams(INFLUENZA, false);
		flu.setShowingSymptomsProbabilityByAge(Map.of(0, 0.4));
		flu.setSeriouslySickProbabilityByAge(Map.of(0, 0.01));
		flu.setCriticalProbabilityByAge(Map.of(0, 0.05));
		flu.setDeathProbabilityByAge(Map.of(0, 0.0));
		flu.setRouteTransmissibility(TransmissionWeights.parse("respiratory=0.6;directContact=0.2;fomite=0.2"));

		File tmp = File.createTempFile("matsim", "config");
		tmp.deleteOnExit();
		ConfigUtils.writeConfig(config, tmp.toString());

		PathogenConfigGroup copy = new PathogenConfigGroup();
		ConfigUtils.loadConfig(tmp.toString(), copy);

		// SARS-CoV-2 default unchanged and not duplicated
		assertThat(copy.getParams(Pathogen.SARS_COV_2, false).getRouteTransmissibility().toToken())
				.isEqualTo("respiratory=1.0;directContact=0.0;fomite=0.0");

		TransmissionWeights reloaded = copy.getParams(INFLUENZA, false).getRouteTransmissibility();
		assertThat(reloaded.get(ContactTransmissionType.RESPIRATORY)).isEqualTo(0.6);
		assertThat(reloaded.get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.2);
		assertThat(reloaded.get(ContactTransmissionType.FOMITE)).isEqualTo(0.2);
	}

	/**
	 * (11) A route mix whose weights sum above 1 is rejected on assignment.
	 */
	@Test
	public void routeTransmissibilityRejectsInvalidWeights() {
		PathogenParams p = new PathogenParams();

		assertThatThrownBy(() -> p.setRouteTransmissibilityString("respiratory=0.8;fomite=0.5"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> p.setRouteTransmissibilityString("respiratory=-0.1"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * (12) A pathogen that cannot transmit by any route is rejected on config finalisation.
	 */
	@Test
	public void allZeroRouteTransmissibilityIsRejected() {
		PathogenParams p = new PathogenParams();
		p.setPathogen(INFLUENZA);
		p.setShowingSymptomsProbabilityByAge(Map.of(0, 0.4));
		p.setSeriouslySickProbabilityByAge(Map.of(0, 0.01));
		p.setCriticalProbabilityByAge(Map.of(0, 0.05));
		p.setDeathProbabilityByAge(Map.of(0, 0.0));
		p.setRouteTransmissibility(TransmissionWeights.ZERO);

		assertThatThrownBy(() -> p.validateComplete("pathogen"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("routeTransmissibility");
	}

	private static int countSeriouslySick(EpisimSplittableRandom rnd, PathogenConfigGroup pathogenConfig,
	                                      VaccinationConfigGroup vaccinationConfig, EpisimConfigGroup episimConfig,
	                                      double strainFactorSeriouslySick) {
		VirusStrainConfigGroup strainConfig = new VirusStrainConfigGroup();
		strainConfig.getParams(VirusStrain.SARS_CoV_2).setFactorSeriouslySick(strainFactorSeriouslySick);

		AntibodyDependentTransitionModel model =
				new AntibodyDependentTransitionModel(rnd, vaccinationConfig, strainConfig, pathogenConfig);

		int serious = 0;
		for (int i = 0; i < 20_000; i++) {
			EpisimPerson p = EpisimTestUtils.createPerson(true, 40);
			p.setInitialInfection(0, VirusStrain.SARS_CoV_2);
			p.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.showingSymptoms);
			if (model.decideNextState(p, EpisimPerson.DiseaseStatus.showingSymptoms, 1) == EpisimPerson.DiseaseStatus.seriouslySick)
				serious++;
		}
		return serious;
	}
}

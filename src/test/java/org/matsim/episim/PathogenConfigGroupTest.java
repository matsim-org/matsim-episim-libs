package org.matsim.episim;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.PathogenConfigGroup.PathogenParams;
import org.matsim.episim.PathogenConfigGroup.ProgressionParams;
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
		ProgressionParams flat = group.getProgressionParams(Pathogen.SARS_COV_2, false);
		for (int age : new int[]{0, 20, 42, 80, 120}) {
			assertThat(flat.getShowingSymptomsProbability(age)).isEqualTo(0.8);
			assertThat(flat.getSeriouslySickProbability(age)).isEqualTo(0.05625);
			assertThat(flat.getCriticalProbability(age)).isEqualTo(0.25);
			assertThat(flat.getDeathProbability(age)).isEqualTo(0.0);
		}

		// age-dependent variant -> AgeDependentDiseaseStatusTransitionModel (pre-hospitalFactor)
		ProgressionParams byAge = group.getProgressionParams(Pathogen.SARS_COV_2, true);
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
		ProgressionParams p = new ProgressionParams();
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
		ProgressionParams p = new ProgressionParams();

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

		ProgressionParams flu = group.getOrAddParams(INFLUENZA).getOrAddProgressionParams(false);
		flu.setShowingSymptomsProbabilityByAge(Map.of(0, 0.4, 60, 0.55));
		flu.setSeriouslySickProbabilityByAge(Map.of(0, 0.01, 70, 0.2));
		flu.setCriticalProbabilityByAge(Map.of(0, 0.05));
		flu.setDeathProbabilityByAge(Map.of(0, 0.0, 80, 0.1));

		PathogenConfigGroup copy = roundTrip(config);

		// SARS-CoV-2 defaults still there, both variants
		assertThat(copy.getProgressionParams(Pathogen.SARS_COV_2, false).getSeriouslySickProbability(30)).isEqualTo(0.05625);
		assertThat(copy.getProgressionParams(Pathogen.SARS_COV_2, true).getSeriouslySickProbability(80)).isEqualTo(36. / 100);

		ProgressionParams reloaded = copy.getProgressionParams(INFLUENZA, false);
		assertThat(reloaded.getShowingSymptomsProbability(10)).isEqualTo(0.4);
		assertThat(reloaded.getShowingSymptomsProbability(60)).isEqualTo(0.55);
		assertThat(reloaded.getSeriouslySickProbability(69)).isEqualTo(0.01);
		assertThat(reloaded.getSeriouslySickProbability(70)).isEqualTo(0.2);
		assertThat(reloaded.getCriticalProbability(50)).isEqualTo(0.05);
		assertThat(reloaded.getDeathProbability(80)).isEqualTo(0.1);
		assertThat(copy.getParams(INFLUENZA).hasProgressionParams(true)).isFalse();
	}

	/**
	 * (5) A brand new pathogen can be introduced through configuration alone.
	 */
	@Test
	public void newPathogenCanBeAddedThroughConfigurationOnly() {
		PathogenConfigGroup group = new PathogenConfigGroup();

		assertThat(group.hasParams(INFLUENZA)).isFalse();

		group.getOrAddParams(INFLUENZA).getOrAddProgressionParams(false)
				.setShowingSymptomsProbabilityByAge(Map.of(0, 0.3, 60, 0.6));

		assertThat(group.hasParams(INFLUENZA)).isTrue();
		assertThat(group.getProgressionParams(INFLUENZA, false).getShowingSymptomsProbability(15)).isEqualTo(0.3);
		assertThat(group.getProgressionParams(INFLUENZA, false).getShowingSymptomsProbability(70)).isEqualTo(0.6);
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
		PathogenParams influenza = pathogenConfig.getOrAddParams(INFLUENZA);
		influenza.getOrAddProgressionParams(false).setSeriouslySickProbabilityByAge(Map.of(0, 0.42));
		influenza.getOrAddProgressionParams(true).setSeriouslySickProbabilityByAge(Map.of(0, 0.1, 60, 0.9));

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
	 * (8) A pathogen configured without the variant a transition model needs fails with a message naming the variant.
	 */
	@Test
	public void missingProgressionVariantFailsClearly() {
		PathogenConfigGroup group = new PathogenConfigGroup();
		group.getOrAddParams(INFLUENZA).getOrAddProgressionParams(true);

		assertThatThrownBy(() -> group.getProgressionParams(INFLUENZA, false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("INFLUENZA")
				.hasMessageContaining("ageDependent=false");
	}

	/**
	 * (9) Strain / vaccination / antibody modifiers still apply on top of the configured base probability.
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
	 * (10) Route transmissibility defaults to respiratory-only, for the SARS-CoV-2 defaults and for a
	 * pathogen added through configuration.
	 */
	@Test
	public void routeTransmissibilityDefaultsToRespiratoryOnly() {
		PathogenConfigGroup group = new PathogenConfigGroup();

		TransmissionWeights w = group.getParams(Pathogen.SARS_COV_2).getRouteTransmissibility();
		assertThat(w.get(ContactTransmissionType.RESPIRATORY)).isEqualTo(1.0);
		assertThat(w.get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.0);

		TransmissionWeights fresh = group.getOrAddParams(INFLUENZA).getRouteTransmissibility();
		assertThat(fresh.get(ContactTransmissionType.RESPIRATORY)).isEqualTo(1.0);
		assertThat(fresh.sum()).isEqualTo(1.0);
	}

	/**
	 * (11) A non-default route mix survives an XML round-trip.
	 */
	@Test
	public void routeTransmissibilitySurvivesXmlRoundTrip() throws IOException {
		PathogenConfigGroup group = new PathogenConfigGroup();
		Config config = ConfigUtils.createConfig(group);

		PathogenParams flu = group.getOrAddParams(INFLUENZA);
		completeProgression(flu.getOrAddProgressionParams(false));
		flu.setRouteTransmissibility(TransmissionWeights.parse("respiratory=0.6;directContact=0.2"));

		PathogenConfigGroup copy = roundTrip(config);

		// SARS-CoV-2 default unchanged
		assertThat(copy.getParams(Pathogen.SARS_COV_2).getRouteTransmissibility().toToken())
				.isEqualTo("respiratory=1.0;directContact=0.0");

		TransmissionWeights reloaded = copy.getParams(INFLUENZA).getRouteTransmissibility();
		assertThat(reloaded.get(ContactTransmissionType.RESPIRATORY)).isEqualTo(0.6);
		assertThat(reloaded.get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.2);
	}

	/**
	 * (12) Reloading a written config replaces the auto-added SARS-CoV-2 default instead of duplicating it, on both
	 * levels of parameter sets.
	 */
	@Test
	public void xmlRoundTripDoesNotDuplicateParameterSets() throws IOException {
		PathogenConfigGroup group = new PathogenConfigGroup();
		Config config = ConfigUtils.createConfig(group);

		PathogenConfigGroup copy = roundTrip(config);

		assertThat(copy.getParameterSets(PathogenParams.SET_TYPE)).hasSize(1);
		assertThat(copy.getParams(Pathogen.SARS_COV_2).getParameterSets(ProgressionParams.SET_TYPE)).hasSize(2);
	}

	/**
	 * (13) Route weights are relative and additive: a sum above 1 is accepted; only non-finite or
	 * negative weights are rejected.
	 */
	@Test
	public void routeTransmissibilityAcceptsSumAboveOneRejectsNegative() {
		PathogenParams p = new PathogenParams();

		p.setRouteTransmissibilityString("respiratory=1.0;directContact=0.5");
		assertThat(p.getRouteTransmissibility().sum()).isEqualTo(1.5);

		assertThatThrownBy(() -> p.setRouteTransmissibilityString("respiratory=-0.1"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * (14) A pathogen that cannot transmit by any route is rejected on config finalisation.
	 */
	@Test
	public void allZeroRouteTransmissibilityIsRejected() {
		PathogenParams p = new PathogenParams();
		p.setPathogen(INFLUENZA);
		completeProgression(p.getOrAddProgressionParams(false));
		p.setRouteTransmissibility(TransmissionWeights.ZERO);

		assertThatThrownBy(() -> p.validateComplete("pathogen"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("routeTransmissibility");
	}

	/**
	 * (15) A pathogen needs at least one progression variant, and each present variant must be complete.
	 */
	@Test
	public void incompleteProgressionIsRejected() {
		PathogenParams p = new PathogenParams();
		p.setPathogen(INFLUENZA);

		assertThatThrownBy(() -> p.validateComplete("pathogen"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("progressionParams");

		p.getOrAddProgressionParams(true).setShowingSymptomsProbabilityByAge(Map.of(0, 0.5));

		assertThatThrownBy(() -> p.validateComplete("pathogen"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ageDependent=true")
				.hasMessageContaining("seriouslySickProbabilityByAge");
	}

	/**
	 * (16) Isolation at symptom onset defaults to the previously hard-coded behaviour: everybody, full quarantine.
	 */
	@Test
	public void symptomaticIsolationDefaultsToFullForEveryone() {
		PathogenConfigGroup group = new PathogenConfigGroup();

		PathogenParams sars = group.getParams(Pathogen.SARS_COV_2);
		assertThat(sars.getSymptomaticIsolationProbability(30)).isEqualTo(1.0);
		assertThat(sars.getSymptomaticIsolationStatus()).isEqualTo(EpisimPerson.QuarantineStatus.full);

		PathogenParams fresh = group.getOrAddParams(INFLUENZA);
		assertThat(fresh.getSymptomaticIsolationProbability(5)).isEqualTo(1.0);
		assertThat(fresh.getSymptomaticIsolationStatus()).isEqualTo(EpisimPerson.QuarantineStatus.full);
	}

	/**
	 * (17) Isolation probabilities are age buckets within [0, 1]; only 'full' and 'atHome' are valid statuses.
	 */
	@Test
	public void symptomaticIsolationIsValidated() {
		PathogenParams p = new PathogenParams();

		assertThatThrownBy(() -> p.setSymptomaticIsolationProbabilityByAge(Map.of(0, 1.2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> p.setSymptomaticIsolationProbabilityByAge("0=-0.1"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> p.setSymptomaticIsolationStatus(EpisimPerson.QuarantineStatus.testing))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> p.setSymptomaticIsolationStatus(EpisimPerson.QuarantineStatus.no))
				.isInstanceOf(IllegalArgumentException.class);

		p.setSymptomaticIsolationProbabilityByAge(Map.of(0, 0.5, 18, 0.25));
		assertThat(p.getSymptomaticIsolationProbability(10)).isEqualTo(0.5);
		assertThat(p.getSymptomaticIsolationProbability(18)).isEqualTo(0.25);
		assertThat(p.getSymptomaticIsolationProbability(70)).isEqualTo(0.25);
	}

	/**
	 * (18) The isolation settings survive an XML round-trip; the SARS-CoV-2 defaults stay untouched.
	 */
	@Test
	public void symptomaticIsolationSurvivesXmlRoundTrip() throws IOException {
		PathogenConfigGroup group = new PathogenConfigGroup();
		Config config = ConfigUtils.createConfig(group);

		PathogenParams flu = group.getOrAddParams(INFLUENZA);
		completeProgression(flu.getOrAddProgressionParams(false));
		flu.setSymptomaticIsolationProbabilityByAge(Map.of(0, 0.5, 18, 0.25));
		flu.setSymptomaticIsolationStatus(EpisimPerson.QuarantineStatus.atHome);

		PathogenConfigGroup copy = roundTrip(config);

		PathogenParams reloaded = copy.getParams(INFLUENZA);
		assertThat(reloaded.getSymptomaticIsolationProbability(10)).isEqualTo(0.5);
		assertThat(reloaded.getSymptomaticIsolationProbability(40)).isEqualTo(0.25);
		assertThat(reloaded.getSymptomaticIsolationStatus()).isEqualTo(EpisimPerson.QuarantineStatus.atHome);

		assertThat(copy.getParams(Pathogen.SARS_COV_2).getSymptomaticIsolationProbability(40)).isEqualTo(1.0);
		assertThat(copy.getParams(Pathogen.SARS_COV_2).getSymptomaticIsolationStatus())
				.isEqualTo(EpisimPerson.QuarantineStatus.full);
	}

	private static PathogenConfigGroup roundTrip(Config config) throws IOException {
		File tmp = File.createTempFile("matsim", "config");
		tmp.deleteOnExit();
		ConfigUtils.writeConfig(config, tmp.toString());

		PathogenConfigGroup copy = new PathogenConfigGroup();
		ConfigUtils.loadConfig(tmp.toString(), copy);
		return copy;
	}

	private static void completeProgression(ProgressionParams p) {
		p.setShowingSymptomsProbabilityByAge(Map.of(0, 0.4));
		p.setSeriouslySickProbabilityByAge(Map.of(0, 0.01));
		p.setCriticalProbabilityByAge(Map.of(0, 0.05));
		p.setDeathProbabilityByAge(Map.of(0, 0.0));
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

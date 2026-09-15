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
import org.matsim.episim.model.progression.DefaultDiseaseStatusTransitionModel;
import org.matsim.episim.util.EpisimSplittableRandom;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.matsim.episim.EpisimTestUtils.*;

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
	 * (3) Probabilities outside [0, 1] are rejected, both when read from a config file and via the map setter.
	 */
	@Test
	public void probabilitiesOutsideUnitIntervalAreRejected() {

		assertThatThrownBy(() -> loadProgression(xmlParam("criticalProbabilityByAge", "0=1.5")))
				.hasStackTraceContaining("must be within [0, 1] but was 1.5");
		assertThatThrownBy(() -> loadProgression(xmlParam("criticalProbabilityByAge", "0=-0.1")))
				.hasStackTraceContaining("must be within [0, 1] but was -0.1");
		assertThatThrownBy(() -> loadProgression(xmlParam("seriouslySickProbabilityByAge", "0=0.2;40=2.0")))
				.hasStackTraceContaining("must be within [0, 1] but was 2.0");

		ProgressionParams p = new ProgressionParams();
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
	 * (5) A brand new pathogen can be introduced through a config file alone.
	 */
	@Test
	public void newPathogenCanBeAddedThroughConfigurationOnly() throws IOException {
		assertThat(new PathogenConfigGroup().hasParams(INFLUENZA)).isFalse();

		PathogenConfigGroup group = new PathogenConfigGroup();
		loadConfigXml(xmlModule("pathogen",
				xmlSet(PathogenParams.SET_TYPE,
						xmlParam("pathogen", INFLUENZA.getName()),
						xmlSet(ProgressionParams.SET_TYPE,
								xmlParam("ageDependent", "false"),
								xmlParam("showingSymptomsProbabilityByAge", "0=0.3;60=0.6")))), group);

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
	public void missingProgressionVariantFailsClearly() throws IOException {
		PathogenConfigGroup group = new PathogenConfigGroup();
		loadProgression(group, xmlParam("ageDependent", "true"));

		assertThat(group.getParams(INFLUENZA).hasProgressionParams(true)).isTrue();
		assertThatThrownBy(() -> group.getProgressionParams(INFLUENZA, false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("INFLUENZA")
				.hasMessageContaining("ageDependent=false");
	}

	/**
	 * (8b) A transition model refuses to be created when a configured strain lacks the progression variant it needs, so the
	 * simulation does not fail at the first transition of that strain.
	 */
	@Test
	public void transitionModelRejectsMissingVariantWhenCreated() throws IOException {
		EpisimSplittableRandom rnd = new EpisimSplittableRandom(1);

		// influenza is configured with the age-independent variant only
		PathogenConfigGroup pathogenConfig = new PathogenConfigGroup();
		loadProgression(pathogenConfig, xmlParam("ageDependent", "false"),
				xmlParam("showingSymptomsProbabilityByAge", "0=0.4"),
				xmlParam("seriouslySickProbabilityByAge", "0=0.01"),
				xmlParam("criticalProbabilityByAge", "0=0.05"),
				xmlParam("deathProbabilityByAge", "0=0.0"));

		VirusStrainConfigGroup strainConfig = new VirusStrainConfigGroup();
		strainConfig.getOrAddParams(VirusStrain.of(INFLUENZA, "FLU_VARIANT_CHECK"));

		VaccinationConfigGroup vaccinationConfig = new VaccinationConfigGroup();

		assertThatThrownBy(() -> new AgeDependentDiseaseStatusTransitionModel(rnd, new EpisimConfigGroup(), vaccinationConfig, strainConfig, pathogenConfig))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AgeDependentDiseaseStatusTransitionModel")
				.hasMessageContaining("pathogen 'INFLUENZA' of strains [FLU_VARIANT_CHECK]")
				.hasMessageContaining("ageDependent=true");

		// the age-independent models find their variant
		new AntibodyDependentTransitionModel(rnd, vaccinationConfig, strainConfig, pathogenConfig);
		new DefaultDiseaseStatusTransitionModel(rnd, vaccinationConfig, strainConfig, pathogenConfig);

		// a strain of a pathogen without any parameter set
		VirusStrainConfigGroup unknownPathogen = new VirusStrainConfigGroup();
		unknownPathogen.getOrAddParams(VirusStrain.of(new Pathogen("UNKNOWN_VARIANT_CHECK"), "UNKNOWN_VARIANT_CHECK_STRAIN"));

		assertThatThrownBy(() -> new DefaultDiseaseStatusTransitionModel(rnd, vaccinationConfig, unknownPathogen, pathogenConfig))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("pathogen 'UNKNOWN_VARIANT_CHECK' of strains [UNKNOWN_VARIANT_CHECK_STRAIN] has no 'pathogenParams'");
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
	public void routeTransmissibilityAcceptsSumAboveOneRejectsNegative() throws IOException {
		PathogenConfigGroup group = new PathogenConfigGroup();
		loadConfigXml(xmlModule("pathogen",
				xmlSet(PathogenParams.SET_TYPE,
						xmlParam("pathogen", INFLUENZA.getName()),
						xmlParam("routeTransmissibility", "respiratory=1.0;directContact=0.5"))), group);

		assertThat(group.getParams(INFLUENZA).getRouteTransmissibility().sum()).isEqualTo(1.5);

		assertThatThrownBy(() -> loadConfigXml(xmlModule("pathogen",
				xmlSet(PathogenParams.SET_TYPE,
						xmlParam("pathogen", INFLUENZA.getName()),
						xmlParam("routeTransmissibility", "respiratory=-0.1"))), new PathogenConfigGroup()))
				.hasStackTraceContaining("Invalid respiratory weight");
	}

	/**
	 * (14) A pathogen that cannot transmit by any route is rejected on config finalisation.
	 */
	@Test
	public void allZeroRouteTransmissibilityIsRejected() throws IOException {
		PathogenConfigGroup written = new PathogenConfigGroup();
		PathogenParams p = written.getOrAddParams(INFLUENZA);
		completeProgression(p.getOrAddProgressionParams(false));
		p.setRouteTransmissibility(TransmissionWeights.ZERO);

		// an all-zero value can be written and read, it is rejected when the config is checked
		PathogenConfigGroup group = new PathogenConfigGroup();
		Config config = roundTrip(ConfigUtils.createConfig(written), group);

		assertThatThrownBy(() -> group.checkConsistency(config))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("routeTransmissibility");
	}

	/**
	 * (15) A pathogen needs at least one progression variant, and each present variant must be complete.
	 */
	@Test
	public void incompleteProgressionIsRejected() throws IOException {
		PathogenConfigGroup withoutProgression = new PathogenConfigGroup();
		Config config = loadConfigXml(xmlModule("pathogen",
				xmlSet(PathogenParams.SET_TYPE,
						xmlParam("pathogen", INFLUENZA.getName()))), withoutProgression);

		assertThatThrownBy(() -> withoutProgression.checkConsistency(config))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("progressionParams");

		PathogenConfigGroup incomplete = new PathogenConfigGroup();
		Config config2 = loadProgression(incomplete, xmlParam("ageDependent", "true"),
				xmlParam("showingSymptomsProbabilityByAge", "0=0.5"));

		assertThatThrownBy(() -> incomplete.checkConsistency(config2))
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
	public void symptomaticIsolationIsValidated() throws IOException {
		PathogenParams p = new PathogenParams();

		assertThatThrownBy(() -> p.setSymptomaticIsolationProbabilityByAge(Map.of(0, 1.2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> p.setSymptomaticIsolationStatus(EpisimPerson.QuarantineStatus.testing))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> p.setSymptomaticIsolationStatus(EpisimPerson.QuarantineStatus.no))
				.isInstanceOf(IllegalArgumentException.class);

		// the same values in a config file
		assertThatThrownBy(() -> loadIsolation(xmlParam("symptomaticIsolationProbabilityByAge", "0=-0.1")))
				.hasStackTraceContaining("must be within [0, 1] but was -0.1");
		assertThatThrownBy(() -> loadIsolation(xmlParam("symptomaticIsolationStatus", "testing")))
				.hasStackTraceContaining("must be 'full' or 'atHome'");
		assertThatThrownBy(() -> loadIsolation(xmlParam("symptomaticIsolationStatus", "hospital")))
				.hasStackTraceContaining("hospital");

		PathogenConfigGroup group = loadIsolation(
				xmlParam("symptomaticIsolationProbabilityByAge", "0=0.5;18=0.25"),
				xmlParam("symptomaticIsolationStatus", "atHome"));

		PathogenParams loaded = group.getParams(INFLUENZA);
		assertThat(loaded.getSymptomaticIsolationProbability(10)).isEqualTo(0.5);
		assertThat(loaded.getSymptomaticIsolationProbability(18)).isEqualTo(0.25);
		assertThat(loaded.getSymptomaticIsolationProbability(70)).isEqualTo(0.25);
		assertThat(loaded.getSymptomaticIsolationStatus()).isEqualTo(EpisimPerson.QuarantineStatus.atHome);
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
		PathogenConfigGroup copy = new PathogenConfigGroup();
		roundTrip(config, copy);
		return copy;
	}

	/**
	 * Writes the config to a file and loads it into the given group.
	 *
	 * @return the loaded config
	 */
	private static Config roundTrip(Config config, PathogenConfigGroup copy) throws IOException {
		File tmp = File.createTempFile("matsim", "config");
		tmp.deleteOnExit();
		ConfigUtils.writeConfig(config, tmp.toString());

		return ConfigUtils.loadConfig(tmp.toString(), copy);
	}

	/**
	 * Loads a config file with an influenza progression variant consisting of the given params.
	 */
	private static Config loadProgression(String... params) throws IOException {
		return loadProgression(new PathogenConfigGroup(), params);
	}

	private static Config loadProgression(PathogenConfigGroup group, String... params) throws IOException {
		return loadConfigXml(xmlModule("pathogen",
				xmlSet(PathogenParams.SET_TYPE,
						xmlParam("pathogen", INFLUENZA.getName()),
						xmlSet(ProgressionParams.SET_TYPE, params))), group);
	}

	/**
	 * Loads a config file with influenza params consisting of the given params.
	 */
	private static PathogenConfigGroup loadIsolation(String... params) throws IOException {
		PathogenConfigGroup group = new PathogenConfigGroup();
		String[] content = new String[params.length + 1];
		content[0] = xmlParam("pathogen", INFLUENZA.getName());
		System.arraycopy(params, 0, content, 1, params.length);

		loadConfigXml(xmlModule("pathogen", xmlSet(PathogenParams.SET_TYPE, content)), group);
		return group;
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

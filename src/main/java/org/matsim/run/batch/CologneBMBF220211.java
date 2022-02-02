package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.EpisimPerson.QuarantineStatus;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.InfectionModelWithAntibodies;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.annotation.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * Batch for Bmbf runs
 */
public class CologneBMBF220211 implements BatchRun<CologneBMBF220211.Params> {

	@Override
	public SnzCologneProductionScenario getBindings(int id, @Nullable Params params) {

		double pHousehold = 0.0;

//		if (params != null)
//			pHousehold = params.pHousehold;

		return new SnzCologneProductionScenario.Builder()
				.setScale(1.3)
				.setHouseholdSusc(pHousehold)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
				.setInfectionModel(InfectionModelWithAntibodies.class)
				.build();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("cologne", "calibration");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		LocalDate restrictionDate = LocalDate.parse("2022-01-24");

		SnzCologneProductionScenario module = getBindings(id, params);

		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 0.96);

//		episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-cologne-20210917/" + params.seed + "-270-2020-11-20.zip");
//		episimConfig.setSnapshotSeed(SnapshotSeed.restore);

		// age susceptibility increases by 28% every 10 years
//		if (params.ageDep.equals("yes")) {
//			episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() / 3.5);
//			Map<Integer, Double> map = new HashMap<>();
//			for (int i = 0; i<120; i++) map.put(i, Math.pow(1.02499323, i));
//			episimConfig.setAgeSusceptibility(map);
//		}

		//restrictions
		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());


		builder.restrict(restrictionDate, Restriction.ofVaccinatedRf(params.leis), "leisure");

		//2G
		builder.restrict(LocalDate.parse("2021-11-22"), Restriction.ofSusceptibleRf(0.75), "leisure");
		builder.restrict(restrictionDate, Restriction.ofSusceptibleRf(params.leisUnv), "leisure");

		double schoolFac = 0.5;
		builder.restrict(LocalDate.parse("2021-08-17"), Restriction.ofCiCorrection(1 - (0.5 * schoolFac)), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");

		builder.restrict(LocalDate.parse("2021-08-17"), Restriction.ofMask(FaceMask.N95, 0.9 * schoolFac), "educ_primary", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-11-02"), Restriction.ofMask(FaceMask.N95, 0.0), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-12-02"), Restriction.ofMask(FaceMask.N95, 0.9 * schoolFac), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");

		if (params.school.equals("protected")) {
			builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.9), "educ_primary", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
			builder.restrict(restrictionDate, Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		}

		episimConfig.setPolicy(builder.build());

		Map<LocalDate, DayOfWeek> inputDays = new HashMap<>();
		inputDays.put(LocalDate.parse("2021-11-01"), DayOfWeek.SUNDAY);
		//christmas
		inputDays.put(LocalDate.parse("2021-12-24"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2021-12-25"), DayOfWeek.SUNDAY);
		inputDays.put(LocalDate.parse("2021-12-26"), DayOfWeek.SUNDAY);
		inputDays.put(LocalDate.parse("2021-12-27"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2021-12-28"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2021-12-29"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2021-12-30"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2021-12-31"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2022-01-01"), DayOfWeek.SUNDAY);
		episimConfig.setInputDays(inputDays);

		//mutations
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		Map<LocalDate, Integer> infPerDayB117 = new HashMap<>();
		infPerDayB117.put(LocalDate.parse("2020-01-01"), 0);

		infPerDayB117.put(LocalDate.parse("2021-01-16"), 20);
		infPerDayB117.put(LocalDate.parse("2021-01-16").plusDays(1), 1);
		infPerDayB117.put(LocalDate.parse("2020-12-31"), 1);

		episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayB117);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(1.55);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySick(1.0);

		Map<LocalDate, Integer> infPerDayMUTB = new HashMap<>();
		infPerDayMUTB.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayMUTB.put(LocalDate.parse("2021-06-21"), 10);
		infPerDayMUTB.put(LocalDate.parse("2021-06-21").plusDays(1), 1);


		//disease import 2021
		double impFacSum = 5.0;
		int imp = 16;
		double cologneFactor = 0.5;
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, cologneFactor * impFacSum, LocalDate.parse("2021-07-03").plusDays(0),
				LocalDate.parse("2021-07-25").plusDays(0), 1, 48);
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, cologneFactor * impFacSum, LocalDate.parse("2021-07-26").plusDays(0),
				LocalDate.parse("2021-08-17").plusDays(0), 48, imp);

		infPerDayMUTB.put(LocalDate.parse("2021-08-18"), imp);

		double impFacOct = 2.0;
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, cologneFactor * impFacOct, LocalDate.parse("2021-10-09").plusDays(0),
				LocalDate.parse("2021-10-16").plusDays(0), imp, imp);
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, cologneFactor * impFacOct, LocalDate.parse("2021-10-17").plusDays(0),
				LocalDate.parse("2021-10-24").plusDays(0), imp, 1);
		infPerDayMUTB.put(LocalDate.parse("2021-10-25"), 1);
		;
		episimConfig.setInfections_pers_per_day(VirusStrain.DELTA, infPerDayMUTB);
		double deltaInf = 2.0;
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setInfectiousness(deltaInf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySick(1.25);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySickVaccinated(1.25);


		//omicron
		Map<LocalDate, Integer> infPerDayOmicron = new HashMap<>();
		infPerDayOmicron.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayOmicron.put(LocalDate.parse("2021-11-28"), 4);
		infPerDayOmicron.put(LocalDate.parse("2021-12-03").plusDays(1), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA1, infPerDayOmicron);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setInfectiousness(deltaInf * params.oInf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySick(0.5 * 1.25);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySickVaccinated(0.5 * 1.25);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorCritical(0.5);

		//BA.2
//		Map<LocalDate, Integer> infPerDayBA2 = new HashMap<>();
//		infPerDayBA2.put(LocalDate.parse("2020-01-01"), 0);
//		infPerDayBA2.put(LocalDate.parse(params.ba2Date), 4);
//		infPerDayBA2.put(LocalDate.parse(params.ba2Date).plusDays(6), 1);
//		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA2, infPerDayBA2);
//		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setInfectiousness(deltaInf * params.oInf * params.ba2Inf);
//		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorSeriouslySick(0.5 * 1.25);
//		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorSeriouslySickVaccinated(0.5 * 1.25);
//		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorCritical(0.5);


		//vaccinations
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

		Map<Integer, Double> vaccinationCompliance = new HashMap<>();
		for (int i = 0; i < 5; i++) vaccinationCompliance.put(i, 0.0);
		for (int i = 5; i <= 11; i++) vaccinationCompliance.put(i, 0.4);
		for (int i = 12; i <= 120; i++) vaccinationCompliance.put(i, 1.0);
		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);

		Map<LocalDate, Integer> vaccinations = new HashMap<>();
		double population = 2_352_480;
		vaccinations.put(LocalDate.parse("2022-01-17"), (int) (0.0035 * population / 7));
		vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);
		vaccinationConfig.setDaysValid(params.daysImmune);
		vaccinationConfig.setValidDeadline(restrictionDate);

		adaptVacinationEffectiveness(vaccinationConfig);

		Map<VirusStrain, Double> ak50PerStrain = new HashMap<>();

		ak50PerStrain.put(VirusStrain.SARS_CoV_2, params.aAk50);
		ak50PerStrain.put(VirusStrain.ALPHA, params.aAk50);
		ak50PerStrain.put(VirusStrain.DELTA, params.dAk50);
		ak50PerStrain.put(VirusStrain.OMICRON_BA1, params.oAk50);
//		ak50PerStrain.put(VirusStrain.OMICRON_BA2, params.ak50ba2);


		vaccinationConfig.setAk50PerStrain(ak50PerStrain);

		vaccinationConfig.setBeta(params.beta);

		configureBooster(vaccinationConfig, 1.0, 3);

		//testing
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);

 		testingConfigGroup.setTestAllPersonsAfter(LocalDate.parse("2021-10-01"));

		TestingConfigGroup.TestingParams rapidTest = testingConfigGroup.getOrAddParams(TestType.RAPID_TEST);
		TestingConfigGroup.TestingParams pcrTest = testingConfigGroup.getOrAddParams(TestType.PCR);

		testingConfigGroup.setStrategy(TestingConfigGroup.Strategy.ACTIVITIES);

		List<String> actsList = new ArrayList<String>();
		actsList.add("leisure");
		actsList.add("work");
		actsList.add("business");
		actsList.add("educ_kiga");
		actsList.add("educ_primary");
		actsList.add("educ_secondary");
		actsList.add("educ_tertiary");
		actsList.add("educ_other");
		actsList.add("educ_higher");
		testingConfigGroup.setActivities(actsList);

		rapidTest.setFalseNegativeRate(0.3);
		rapidTest.setFalsePositiveRate(0.03);

		pcrTest.setFalseNegativeRate(0.1);
		pcrTest.setFalsePositiveRate(0.01);

		testingConfigGroup.setHouseholdCompliance(1.0);

		LocalDate testingStartDate = LocalDate.parse("2021-03-19");

		Map<LocalDate, Double> leisureTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> kigaPrimaryTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> uniTests = new HashMap<LocalDate, Double>();
		leisureTests.put(LocalDate.parse("2020-01-01"), 0.);
		workTests.put(LocalDate.parse("2020-01-01"), 0.);
		eduTests.put(LocalDate.parse("2020-01-01"), 0.);
		kigaPrimaryTests.put(LocalDate.parse("2020-01-01"), 0.);
		uniTests.put(LocalDate.parse("2020-01-01"), 0.);

		for (int i = 1; i <= 31; i++) {
			leisureTests.put(testingStartDate.plusDays(i),  0.1 * i / 31.);
			workTests.put(testingStartDate.plusDays(i), 0.1 * i / 31.);
			eduTests.put(testingStartDate.plusDays(i), 0.4 * i / 31.);
			kigaPrimaryTests.put(testingStartDate.plusDays(i), 0.4 * i / 31.);
			uniTests.put(testingStartDate.plusDays(i), 0.8 * i / 31.);

		}

		kigaPrimaryTests.put(LocalDate.parse("2021-05-10"), 0.0);

		workTests.put(LocalDate.parse("2021-06-04"), 0.05);

		workTests.put(LocalDate.parse("2021-11-24"), 0.5);

		leisureTests.put(LocalDate.parse("2021-06-04"), 0.05);
		leisureTests.put(LocalDate.parse("2021-08-23"), 0.2);

		eduTests.put(LocalDate.parse("2021-09-20"), 0.6);

		rapidTest.setTestingRatePerActivityAndDate((Map.of(
				"leisure", leisureTests,
				"work", workTests,
				"business", workTests,
				"educ_kiga", eduTests,
				"educ_primary", eduTests,
				"educ_secondary", eduTests,
				"educ_tertiary", eduTests,
				"educ_higher", uniTests,
				"educ_other", eduTests
		)));

		Map<LocalDate, Double> leisureTestsVaccinated = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTestsVaccinated = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTestsVaccinated = new HashMap<LocalDate, Double>();

		leisureTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
		workTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
		eduTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);

		leisureTestsVaccinated.put(LocalDate.parse("2021-08-23"), 0.2);

		rapidTest.setTestingRatePerActivityAndDateVaccinated((Map.of(
				"leisure", leisureTestsVaccinated,
				"work", workTestsVaccinated,
				"business", workTestsVaccinated,
				"educ_kiga", eduTestsVaccinated,
				"educ_primary", eduTestsVaccinated,
				"educ_secondary", eduTestsVaccinated,
				"educ_tertiary", eduTestsVaccinated,
				"educ_higher", eduTestsVaccinated,
				"educ_other", eduTestsVaccinated
		)));


		Map<LocalDate, Double> leisureTestsPCR = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTestsPCR = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> kigaPramaryTestsPCR = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTestsPCR = new HashMap<LocalDate, Double>();

		leisureTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		workTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		kigaPramaryTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		eduTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);

		kigaPramaryTestsPCR.put(LocalDate.parse("2021-05-10"), 0.4);

		pcrTest.setTestingRatePerActivityAndDate((Map.of(
				"leisure", leisureTestsPCR,
				"work", workTestsPCR,
				"business", workTestsPCR,
				"educ_kiga", kigaPramaryTestsPCR,
				"educ_primary", kigaPramaryTestsPCR,
				"educ_secondary", eduTestsPCR,
				"educ_tertiary", eduTestsPCR,
				"educ_higher", eduTestsPCR,
				"educ_other", eduTestsPCR
		)));

		Map<LocalDate, Double> leisureTestsPCRVaccinated = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTestsPCRVaccinated = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTestsPCRVaccinated = new HashMap<LocalDate, Double>();
		leisureTestsPCRVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
		workTestsPCRVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
		eduTestsPCRVaccinated.put(LocalDate.parse("2020-01-01"), 0.);

		pcrTest.setTestingRatePerActivityAndDateVaccinated((Map.of(
				"leisure", leisureTestsPCRVaccinated,
				"work", workTestsPCRVaccinated,
				"business", workTestsPCRVaccinated,
				"educ_kiga", eduTestsPCRVaccinated,
				"educ_primary", eduTestsPCRVaccinated,
				"educ_secondary", eduTestsPCRVaccinated,
				"educ_tertiary", eduTestsPCRVaccinated,
				"educ_higher", eduTestsPCRVaccinated,
				"educ_other", eduTestsPCRVaccinated
		)));

		rapidTest.setTestingCapacity_pers_per_day(Map.of(
				LocalDate.of(1970, 1, 1), 0,
				testingStartDate, Integer.MAX_VALUE));

		pcrTest.setTestingCapacity_pers_per_day(Map.of(
				LocalDate.of(1970, 1, 1), 0,
				testingStartDate, Integer.MAX_VALUE));

		//tracing
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		boolean qv = false;
//		if (params.qV.equals("yes")) {
//			qv = true;
//		}
		tracingConfig.setQuarantineVaccinated((Map.of(
				episimConfig.getStartDate(), false,
				restrictionDate, qv
		)));

		if (params.tr.equals("improved")) {
			int tracingCapacity = (int) (200 * 0.5);
			tracingConfig.setTracingDelay_days(Map.of(
					episimConfig.getStartDate(), 5,
					restrictionDate, 1
			));

			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					LocalDate.of(2020, 4, 1), (int) (tracingCapacity * 0.2),
					LocalDate.of(2020, 6, 15), tracingCapacity,
					restrictionDate, Integer.MAX_VALUE
			));
		}
		if (params.tr.equals("no")) {
			int tracingCapacity = (int) (200 * 0.5);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					LocalDate.of(2020, 4, 1), (int) (tracingCapacity * 0.2),
					LocalDate.of(2020, 6, 15), tracingCapacity,
					restrictionDate, 0
			));
		}

		tracingConfig.setQuarantineDuration(Map.of(
				episimConfig.getStartDate(), 14,
				restrictionDate, params.q
			));

		int greenPassValid = 90;
		int greenPassValidBoostered = Integer.MAX_VALUE;

		if (!params.daysImmuneQ.equals("current")) {
			greenPassValid = Integer.parseInt(params.daysImmuneQ.split("-")[0]);
			greenPassValidBoostered = Integer.parseInt(params.daysImmuneQ.split("-")[1]);
		}

		tracingConfig.setGreenPassValidDays(greenPassValid);
		tracingConfig.setGreenPassBoosterValidDays(greenPassValidBoostered);

		QuarantineStatus qs = QuarantineStatus.atHome;

		if (params.qs.equals("testing")) {
			qs = QuarantineStatus.testing;
		}

		tracingConfig.setQuarantineStatus(Map.of(
					episimConfig.getStartDate(), QuarantineStatus.atHome,
					restrictionDate, qs
			));





		return config;
	}

	private void adaptVacinationEffectiveness(VaccinationConfigGroup vaccinationConfig) {

		double factorSymptomsMRNA = 0.4;
		double factorSymptomsVector = 0.76;

		double factorSeriouslySickMRNA = 0.63;
		double factorSeriouslySickVector = 0.19;

		int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot

		vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
				.setDaysBeforeFullEffect(fullEffectMRNA)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
						.atFullEffect(factorSeriouslySickMRNA)
				)
		;

		int fullEffectVector = 10 * 7; //second shot after 9 weeks, full effect one week after second shot

		vaccinationConfig.getOrAddParams(VaccinationType.vector)
				.setDaysBeforeFullEffect(fullEffectVector)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSymptomsVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
						.atFullEffect(factorSymptomsVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
						.atFullEffect(factorSymptomsVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
						.atFullEffect(factorSymptomsVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
						.atFullEffect(factorSymptomsVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSeriouslySickVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
						.atFullEffect(factorSeriouslySickVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
						.atFullEffect(factorSeriouslySickVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
						.atFullEffect(factorSeriouslySickVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
						.atFullEffect(factorSeriouslySickVector)
				)
		;

		int fullEffectNatural = 2;
		vaccinationConfig.getOrAddParams(VaccinationType.natural)
				.setDaysBeforeFullEffect(fullEffectNatural)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
						.atFullEffect(factorSeriouslySickMRNA)
				)
		;


	}

	private void configureBooster(VaccinationConfigGroup vaccinationConfig, double boosterSpeed, int boostAfter) {

		Map<LocalDate, Integer> boosterVaccinations = new HashMap<>();

		boosterVaccinations.put(LocalDate.parse("2020-01-01"), 0);

		boosterVaccinations.put(LocalDate.parse("2022-01-17"), (int) (2_352_480 * 0.04 * boosterSpeed / 7));

		vaccinationConfig.setReVaccinationCapacity_pers_per_day(boosterVaccinations);


		vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
				.setBoostWaitPeriod(boostAfter * 30 + 6 * 7);
		;

		vaccinationConfig.getOrAddParams(VaccinationType.vector)
				.setBoostWaitPeriod(boostAfter * 30 + 9 * 7);
		;
	}

	public static final class Params {

		@GenerateSeeds(5)
		public long seed;

		@Parameter({2.0})
		double oInf;
		
		@Parameter({0.3, 0.5})
		double aAk50;
		
		@Parameter({0.4, 0.6})
		double dAk50;
		
		@Parameter({2.4, 3.0})
		double oAk50;

//		@Parameter({1.0, 4.0})
//		double ba2Inf;
//		
//		@Parameter({3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0})
//		double ak50ba2;
//
//		@StringParameter({"2022-01-24", "2022-01-10"})
//		String ba2Date;

		@IntParameter({270})
		int daysImmune;

		@StringParameter({"current"})
		String daysImmuneQ;

		@IntParameter({10})
		int q;

//		@StringParameter({"yes", "no"})
//		String qV;

		@StringParameter({"home"})
		String qs;

		@StringParameter({"current"})
		String tr;

		@Parameter({0.75})
		double leisUnv;

		@Parameter({0.75})
		double leis;

		@Parameter({1.0, 3.0})
		double beta;

		@StringParameter({"current"})
		String school;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneBMBF220211.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


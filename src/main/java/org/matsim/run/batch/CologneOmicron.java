package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.BatchRun.StringParameter;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.Transition;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.annotation.Nullable;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.matsim.episim.model.Transition.to;


/**
 * Batch for bmbf report
 */
public class CologneOmicron implements BatchRun<CologneOmicron.Params> {

	@Override
	public SnzCologneProductionScenario getBindings(int id, @Nullable Params params) {

		double pHousehold = 0.0;
		
//		if (params != null) 
//			pHousehold = params.pHousehold;
		
		return new SnzCologneProductionScenario.Builder()
				.setScale(1.3)
				.setHouseholdSusc(pHousehold)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
				.build();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("cologne", "calibration");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		LocalDate restrictionDate = LocalDate.parse("2021-11-29");

		SnzCologneProductionScenario module = getBindings(id, params);

		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

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

		//2G
		builder.restrict(restrictionDate, Restriction.ofSusceptibleRf(params.leisUnv), "leisure");
		if (params.liftRestr.equals("yes")) {
			builder.restrict(LocalDate.parse("2022-01-10"), Restriction.ofSusceptibleRf(1.0), "leisure");
			builder.restrict(LocalDate.parse("2022-01-10"), 0.8, "work", "leisure", "shop_daily", "shop_other", "visit", "errands", "business");
		}
			
		if (params.schools.equals("unprotected") || params.schools.equals("noMasks")) 
			builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.0), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		
		if (params.schools.equals("unprotected"))
			builder.restrict(restrictionDate, Restriction.ofCiCorrection(1.0), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
					
		episimConfig.setPolicy(builder.build());
		
		Map<LocalDate, DayOfWeek> inputDays = new HashMap<>();
		inputDays.put(LocalDate.parse("2021-11-01"), DayOfWeek.SUNDAY);
		episimConfig.setInputDays(inputDays);

		//mutations
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		Map<LocalDate, Integer> infPerDayB117 = new HashMap<>();
		infPerDayB117.put(LocalDate.parse("2020-01-01"), 0);

		infPerDayB117.put(LocalDate.parse("2020-12-30"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayB117);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(1.8);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySick(1.0);

		Map<LocalDate, Integer> infPerDayMUTB = new HashMap<>();
		infPerDayMUTB.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayMUTB.put(LocalDate.parse("2021-06-14"), 1);
		
		//disease import 2021
		double impFacSum = 3.0;
		int imp = 16;
		double cologneFactor = 0.5;
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, cologneFactor * impFacSum, LocalDate.parse("2021-07-03").plusDays(0),
				LocalDate.parse("2021-07-25").plusDays(0), 1, 48);
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, cologneFactor * impFacSum, LocalDate.parse("2021-07-26").plusDays(0),
				LocalDate.parse("2021-08-17").plusDays(0), 48, imp);
		
		infPerDayMUTB.put(LocalDate.parse("2021-08-18"), imp);
		
		double impFacOct = 2.0;
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, cologneFactor * impFacOct, LocalDate.parse("2021-10-09").plusDays(0),
				LocalDate.parse("2021-10-16").plusDays(0), imp, 16);
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, cologneFactor * impFacOct, LocalDate.parse("2021-10-17").plusDays(0),
				LocalDate.parse("2021-10-24").plusDays(0), 16, 1);
		infPerDayMUTB.put(LocalDate.parse("2021-10-25"), 1);
		;
		episimConfig.setInfections_pers_per_day(VirusStrain.MUTB, infPerDayMUTB);
		
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setInfectiousness(3.8);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setFactorSeriouslySick(2.0);
		
		//omicron
		Map<LocalDate, Integer> infPerDayOmicron = new HashMap<>();
		infPerDayOmicron.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayOmicron.put(LocalDate.parse("2021-11-22"), 10);
		infPerDayOmicron.put(LocalDate.parse("2021-11-29"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON, infPerDayOmicron);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON).setInfectiousness(3.8 * Double.parseDouble(params.omicron.split("-")[0]));
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON).setFactorSeriouslySick(1.0);
		
		//vaccinations
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

		Map<Integer, Double> vaccinationCompliance = new HashMap<>();
		for (int i = 0; i < 12; i++) vaccinationCompliance.put(i, 0.0);
		for (int i = 12; i <= 120; i++) vaccinationCompliance.put(i, 1.0);
		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);
		
		Map<LocalDate, Integer> vaccinations = new HashMap<>();
		double population = 2_352_480;
		vaccinations.put(restrictionDate , (int) (0.01 * params.vacSp * population / 7));
		vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);
		
//		if (!params.vaccine.equals("cur")) {
//			Map<LocalDate, Map<VaccinationType, Double>> share = new HashMap<>();
//			if(params.vaccine.equals("mRNA"))
//				share.put(LocalDate.parse("2020-01-01"), Map.of(VaccinationType.mRNA, 1d, VaccinationType.vector, 0d));
//			if(params.vaccine.equals("vector"))
//				share.put(LocalDate.parse("2020-01-01"), Map.of(VaccinationType.mRNA, 0d, VaccinationType.vector, 1d));
//			vaccinationConfig.setVaccinationShare(share);
//		}
		
		if(params.vacRate.equals("DRS")) 
				vaccinationConfig.setFromFile(SnzCologneProductionScenario.INPUT.resolve("dresdemVaccinations.csv").toString());
		
		adaptVacinationEffectiveness(vaccinationConfig, 0.4, Double.parseDouble(params.omicron.split("-")[1]));
		
		configureBooster(vaccinationConfig, 0.9, params.boostSp * 0.01, params.boostAfter, 0.4, restrictionDate);
			
		//testing
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);
		
 		testingConfigGroup.setTestAllPersonsAfter(restrictionDate);

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
		Map<LocalDate, Double> uniTests = new HashMap<LocalDate, Double>();
		leisureTests.put(LocalDate.parse("2020-01-01"), 0.);
		workTests.put(LocalDate.parse("2020-01-01"), 0.);
		eduTests.put(LocalDate.parse("2020-01-01"), 0.);
		uniTests.put(LocalDate.parse("2020-01-01"), 0.);

		for (int i = 1; i <= 31; i++) {
			leisureTests.put(testingStartDate.plusDays(i),  0.25 * i / 31.);
			workTests.put(testingStartDate.plusDays(i), 0.25 * i / 31.);
			eduTests.put(testingStartDate.plusDays(i), 0.8 * i / 31.);
			uniTests.put(testingStartDate.plusDays(i), 0.8 * i / 31.);

		}


		eduTests.put(LocalDate.parse("2021-06-24"), 0.0);
		uniTests.put(LocalDate.parse("2021-06-24"), 0.0);
		workTests.put(LocalDate.parse("2021-06-04"), 0.05);

		if (params.wTest.startsWith("0.5")) {
			workTests.put(restrictionDate, 0.5);
		}
		if (params.liftRestr.equals("yes")) {
			workTests.put(LocalDate.parse("2022-01-10"), 0.05);
		}
			
		leisureTests.put(LocalDate.parse("2021-06-04"), 0.05);
		leisureTests.put(restrictionDate, params.lTestUnVac);
		
		if (params.liftRestr.equals("yes")) {
			leisureTests.put(LocalDate.parse("2022-01-10"), 0.05);
		}

		eduTests.put(LocalDate.parse("2021-08-06"), 0.6);
		eduTests.put(LocalDate.parse("2021-08-30"), 0.4);
		uniTests.put(LocalDate.parse("2021-08-06"), 0.6);
		uniTests.put(LocalDate.parse("2021-08-30"), 0.4);
		if (params.schools.equals("unprotected")) {
			eduTests.put(restrictionDate,  0.0);
		}
		
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
		leisureTestsVaccinated.put(restrictionDate, params.lTestVac);
		if (params.liftRestr.equals("yes")) {
			leisureTestsVaccinated.put(LocalDate.parse("2022-01-10"), 0.);
		}

		
		if (params.wTest.equals("0.5-all")) {
			workTestsVaccinated.put(restrictionDate, 0.5);
		} else {
			workTestsVaccinated.put(restrictionDate, 0.);
		}
		if (params.liftRestr.equals("yes")) {
			workTestsVaccinated.put(LocalDate.parse("2022-01-10"), 0.);
		}
		
		eduTestsVaccinated.put(restrictionDate, 0.);
		
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
		Map<LocalDate, Double> eduTestsPCR = new HashMap<LocalDate, Double>();
		leisureTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		workTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		eduTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
//		leisureTestsPCR.put(restrictionDate, Double.parseDouble(params.lTestUnVac.split("-")[1]));

		pcrTest.setTestingRatePerActivityAndDate((Map.of(
				"leisure", leisureTestsPCR,
				"work", workTestsPCR,
				"business", workTestsPCR,
				"educ_kiga", eduTestsPCR,
				"educ_primary", eduTestsPCR,
				"educ_secondary", eduTestsPCR,
				"educ_tertiary", eduTestsPCR,
				"educ_higher", eduTestsPCR,
				"educ_other", eduTestsPCR
		)));
		
		Map<LocalDate, Double> leisureTestsPCRVaccinated = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTestsPCRVaccinated = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTestsPCRVaccinated = new HashMap<LocalDate, Double>();
		leisureTestsPCRVaccinated.put(restrictionDate, 0.);
		workTestsPCRVaccinated.put(restrictionDate, 0.);
		eduTestsPCRVaccinated.put(restrictionDate, 0.);
		
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


		return config;
	}

	private void adaptVacinationEffectiveness(VaccinationConfigGroup vaccinationConfig, double vacMult, double omicronVac) {
		
		double effectivnessAlphaMRNA =  0.85;
		double factorShowingSymptomsAlphaMRNA = 0.06 / (1 - effectivnessAlphaMRNA);
		double factorSeriouslySickAlphaMRNA = 0.02 / ((1 - effectivnessAlphaMRNA) * factorShowingSymptomsAlphaMRNA);
		
		double effectivnessDeltaMRNA = 0.8;
		double factorShowingSymptomsDeltaMRNA = 0.15 / (1 - effectivnessDeltaMRNA);
		double factorSeriouslySickDeltaMRNA = 0.09 / ((1 - effectivnessDeltaMRNA) * factorShowingSymptomsDeltaMRNA);
		
		double infectivityAlphaMRNA = 0.32;
		double infectivityDeltaMRNA = 0.5;
		
		int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot
		vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
				.setDaysBeforeFullEffect(fullEffectMRNA)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 0.0)
						.atFullEffect(1.0 - ((1.0 - effectivnessAlphaMRNA) * vacMult))
						.atDay(fullEffectMRNA + 14, 1.0 - ((1.0 - 0.78) * vacMult))
						.atDay(fullEffectMRNA + 98, 1.0 - ((1.0 - 0.64) * vacMult))
//						.atDay(fullEffectMRNA + 482, 0.0)
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 0.0)
						.atFullEffect(1.0 - ((1.0 - effectivnessAlphaMRNA) * vacMult))
						.atDay(fullEffectMRNA + 14, 1.0 - ((1.0 - 0.78) * vacMult))
						.atDay(fullEffectMRNA + 98, 1.0 - ((1.0 - 0.64) * vacMult))
//						.atDay(fullEffectMRNA + 482, 0.0)
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 0.0)
						.atFullEffect(1.0 - ((1.0 - effectivnessDeltaMRNA) * vacMult))
						.atDay(fullEffectMRNA + 14, 1.0 - ((1.0 - 0.72) * vacMult))
						.atDay(fullEffectMRNA + 98, 1.0 - ((1.0 - 0.55) * vacMult))
//						.atDay(fullEffectMRNA + 370, 0.0)
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 0.0)
						.atFullEffect(1.0 - ((1.0 - effectivnessDeltaMRNA) * vacMult * omicronVac))
						.atDay(fullEffectMRNA + 14, 1.0 - ((1.0 - 0.72) * vacMult * omicronVac))
						.atDay(fullEffectMRNA + 98, 1.0 - ((1.0 - 0.55) * vacMult * omicronVac))
//						.atDay(fullEffectMRNA + 370, 0.0)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsAlphaMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsAlphaMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsDeltaMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsDeltaMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickAlphaMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickAlphaMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickDeltaMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickDeltaMRNA)
				)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(infectivityAlphaMRNA)
						.atDay(fullEffectMRNA + 14, 0.38)
						.atDay(fullEffectMRNA + 98, 0.5)
//						.atDay(fullEffectMRNA + 448, 1.0)
				)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(infectivityAlphaMRNA)
						.atDay(fullEffectMRNA + 14, 0.38)
						.atDay(fullEffectMRNA + 98, 0.5)
//						.atDay(fullEffectMRNA + 448, 1.0)
				)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(infectivityDeltaMRNA)
						.atDay(fullEffectMRNA + 14, 0.6)
						.atDay(fullEffectMRNA + 98, 0.78)
//						.atDay(fullEffectMRNA + 201, 1.0)
				)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 1.0)
						.atFullEffect(infectivityDeltaMRNA)
						.atDay(fullEffectMRNA + 14, Math.min(1.0, 0.6 * omicronVac))
						.atDay(fullEffectMRNA + 98, Math.min(1.0, 0.78 * omicronVac))
//						.atDay(fullEffectMRNA + 201, 1.0)
				)
		;
		
		
		double effectivnessAlphaVector = 0.6;
		double factorShowingSymptomsAlphaVector = 0.25 / (1 - effectivnessAlphaVector);
		double factorSeriouslySickAlphaVector = 0.02 / ((1 - effectivnessAlphaVector) * factorShowingSymptomsAlphaVector);
		
		double effectivnessDeltaVector = 0.58;
		double factorShowingSymptomsDeltaVector = 0.35 / (1 - effectivnessDeltaVector);
		double factorSeriouslySickDeltaVector = 0.09 / ((1 - effectivnessDeltaVector) * factorShowingSymptomsDeltaVector);
		
		double infectivityAlphaVector = 0.48;
		double infectivityDeltaVector = 0.78;
	
		int fullEffectVector = 10 * 7; //second shot after 9 weeks, full effect one week after second shot
		vaccinationConfig.getOrAddParams(VaccinationType.vector)
				.setDaysBeforeFullEffect(fullEffectVector)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 0.0)
						.atFullEffect(1.0 - ((1.0 - effectivnessAlphaVector) * vacMult))
						.atDay(fullEffectVector + 14, 1.0 - ((1.0 - 0.52) * vacMult))
						.atDay(fullEffectVector + 98, 1.0 - ((1.0 - 0.38) * vacMult))
//						.atDay(fullEffectVector + 326, 0.0)
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 0.0)
						.atFullEffect(1.0 - ((1.0 - effectivnessAlphaVector) * vacMult))
						.atDay(fullEffectVector + 14, 1.0 - ((1.0 - 0.52) * vacMult))
						.atDay(fullEffectVector + 98, 1.0 - ((1.0 - 0.38) * vacMult))
//						.atDay(fullEffectVector + 326, 0.0)
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 0.0)
						.atFullEffect(1.0 - ((1.0 - effectivnessDeltaVector) * vacMult))
						.atDay(fullEffectVector + 14, 1.0 - ((1.0 - 0.5) * vacMult))
						.atDay(fullEffectVector + 98, 1.0 - ((1.0 - 0.35) * vacMult))
//						.atDay(fullEffectVector + 294, 0.0)
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 0.0)
						.atFullEffect(1.0 - ((1.0 - effectivnessDeltaVector) * vacMult * omicronVac))
						.atDay(fullEffectVector + 14, 1.0 - ((1.0 - 0.5) * vacMult * omicronVac))
						.atDay(fullEffectVector + 98, 1.0 - ((1.0 - 0.35) * vacMult * omicronVac))
//						.atDay(fullEffectVector + 294, 0.0)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsAlphaVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsAlphaVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsDeltaVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsDeltaVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickAlphaVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickAlphaVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickDeltaVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickDeltaVector)
				)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(infectivityAlphaVector)
						.atDay(fullEffectMRNA + 14, 0.54)
						.atDay(fullEffectMRNA + 98, 0.64)
//						.atDay(fullEffectMRNA + 400, 1.0)
				)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(infectivityAlphaVector)
						.atDay(fullEffectMRNA + 14, 0.54)
						.atDay(fullEffectMRNA + 98, 0.64)
//						.atDay(fullEffectMRNA + 400, 1.0)

				)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(infectivityDeltaVector)
						.atDay(fullEffectMRNA + 14, 0.85)
						.atDay(fullEffectMRNA + 98, 1.0)
				)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 1.0)
						.atFullEffect(infectivityDeltaVector)
						.atDay(fullEffectMRNA + 14, Math.min(1.0, 0.85 * omicronVac))
						.atDay(fullEffectMRNA + 98, Math.min(1.0, 1.0 * omicronVac))
				)
		;
		
		double effectivnessAlphaNatural = 0.95;
		double effectivnessDeltaNatural = 0.9;

		double factorShowingSymptomsNatural = 0.5;
		double factorSeriouslySickNatural = 0.5;
		double infectivityNatural = 0.5;
		
		int fullEffectNatural = 2;
		vaccinationConfig.getOrAddParams(VaccinationType.natural)
				.setDaysBeforeFullEffect(fullEffectNatural)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 0.0)
						.atFullEffect(1.0 - ((1.0 - effectivnessAlphaNatural) * vacMult))
						.atDay(100, 1.0 - ((1.0 - (effectivnessAlphaNatural - 0.02)) * vacMult))
						.atDay(400, 1.0 - ((1.0 - (effectivnessAlphaNatural - 0.07)) * vacMult))
						.atDay(700, 1.0 - ((1.0 - (effectivnessAlphaNatural - 0.12)) * vacMult))
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 0.0)
						.atFullEffect(1.0 - ((1.0 - effectivnessAlphaNatural) * vacMult))
						.atDay(100, 1.0 - ((1.0 - (effectivnessAlphaNatural - 0.02)) * vacMult))
						.atDay(400, 1.0 - ((1.0 - (effectivnessAlphaNatural - 0.07)) * vacMult))
						.atDay(700, 1.0 - ((1.0 - (effectivnessAlphaNatural - 0.12)) * vacMult))
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 0.0)
						.atFullEffect(1.0 - ((1.0 - effectivnessDeltaNatural) * vacMult))
						.atDay(100, 1.0 - ((1.0 - (effectivnessDeltaNatural - 0.06)) * vacMult))
						.atDay(400, 1.0 - ((1.0 - (effectivnessDeltaNatural - 0.14)) * vacMult))
						.atDay(700, 1.0 - ((1.0 - (effectivnessDeltaNatural - 0.22)) * vacMult))
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 0.0)
						.atFullEffect(1.0 - ((1.0 - effectivnessDeltaNatural) * vacMult * omicronVac))
						.atDay(100, 1.0 - ((1.0 - (effectivnessDeltaNatural - 0.06)) * vacMult * omicronVac))
						.atDay(400, 1.0 - ((1.0 - (effectivnessDeltaNatural - 0.14)) * vacMult * omicronVac))
						.atDay(700, 1.0 - ((1.0 - (effectivnessDeltaNatural - 0.22)) * vacMult * omicronVac))
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsNatural)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsNatural)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsNatural)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsNatural)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickNatural)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickNatural)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickNatural)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickNatural)
				)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(infectivityNatural)
				)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(infectivityNatural)
				)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(infectivityNatural)
				)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 1.0)
						.atFullEffect(Math.min(1.0, infectivityNatural * omicronVac))
				)
		;
		
		
	}
	
	private void configureBooster(VaccinationConfigGroup vaccinationConfig, double boosterEff, double boosterSpeed, int boostAfter, double vacMult, LocalDate restrictionDate) {
		
		Map<LocalDate, Integer> boosterVaccinations = new HashMap<>();
				
		boosterVaccinations.put(LocalDate.parse("2020-01-01"), 0);

		boosterVaccinations.put(restrictionDate, (int) (2_352_480 * boosterSpeed / 7));
		
		vaccinationConfig.setReVaccinationCapacity_pers_per_day(boosterVaccinations);
		 
		double boostEffectiveness = 1.0 - ((1.0 - boosterEff) * vacMult);
						
		vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
				.setBoostEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 0.0)
						.atDay(7, boostEffectiveness)
				)
				.setBoostEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 0.0)
						.atDay(7, boostEffectiveness)
				)
				.setBoostEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 0.0)
						.atDay(7, boostEffectiveness)
				)
				.setBoostEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 0.0)
						.atDay(7, boostEffectiveness)
				)
				.setBoostWaitPeriod(boostAfter * 30 + 6 * 7);
		;
				
		vaccinationConfig.getOrAddParams(VaccinationType.vector)
				.setBoostEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 0.0)
						.atDay(7, boostEffectiveness)
				)
				.setBoostEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 0.0)
						.atDay(7, boostEffectiveness)
				)
				.setBoostEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 0.0)
						.atDay(7, boostEffectiveness)
				)
				.setBoostEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atDay(1, 0.0)
						.atDay(7, boostEffectiveness)
				)
				.setBoostWaitPeriod(boostAfter * 30 + 9 * 7);
		;
	}

	public static final class Params {

		@GenerateSeeds(5)
		public long seed;

		
		@Parameter({7.0, 14.0})
		double boostSp;
		
		@Parameter({0.5, 7.0})
		double vacSp;
		
		@IntParameter({5})
		int boostAfter;
		
		@Parameter({1.0, 0.5, 0.0})
		double leisUnv;
		
		@Parameter({0.0, 0.5})
		double lTestVac;
		
//		@Parameter({0.05, 0.5})
		@Parameter({0.05})
		double lTestUnVac;
		
		@StringParameter({"0.5-unvaccinated", "0.5-all"})
		String wTest;
		
		@StringParameter({"protected", "noMasks"})
		String schools;
		
		@StringParameter({"no"})
		String liftRestr;
		
		@StringParameter({"CGN"})
		String vacRate;
		
		@StringParameter({"1.0-1.0", "3.0-1.0", "1.0-3.0"})
		String omicron;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneOmicron.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


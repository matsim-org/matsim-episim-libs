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
public class CologneBMBF211219 implements BatchRun<CologneBMBF211219.Params> {

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

		LocalDate restrictionDate = LocalDate.parse("2022-01-10");

		SnzCologneProductionScenario module = getBindings(id, params);

		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 1.02);

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
		
		if (params.work.equals("protected")) {
			builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.9), "work", "business");
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

		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayB117);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(1.7);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySick(1.0);

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
		episimConfig.setInfections_pers_per_day(VirusStrain.MUTB, infPerDayMUTB);
		double deltaInf = 3.0;
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setInfectiousness(deltaInf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setFactorSeriouslySick(2.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setFactorSeriouslySickVaccinated(2.0);

		
		//omicron
		Map<LocalDate, Integer> infPerDayOmicron = new HashMap<>();
		infPerDayOmicron.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayOmicron.put(LocalDate.parse("2021-12-03"), 4);
		infPerDayOmicron.put(LocalDate.parse("2021-12-08").plusDays(1), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON, infPerDayOmicron);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON).setInfectiousness(deltaInf * 3.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON).setFactorSeriouslySick(params.oHosF);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON).setFactorSeriouslySickVaccinated(params.oHosF);

		
		//vaccinations
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

		Map<Integer, Double> vaccinationCompliance = new HashMap<>();
		for (int i = 0; i < 12; i++) vaccinationCompliance.put(i, 0.0);
		for (int i = 12; i <= 120; i++) vaccinationCompliance.put(i, 1.0);
		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);
		
		Map<LocalDate, Integer> vaccinations = new HashMap<>();
		double population = 2_352_480;
		vaccinations.put(LocalDate.parse("2021-12-14"), (int) (0.01 * 0.25 * population / 7));
		vaccinations.put(LocalDate.parse("2021-12-22"), (int) (0.5 * 0.01 * 0.25 * population / 7));
		vaccinations.put(LocalDate.parse("2022-01-03"), (int) (params.vacSp * 0.01 * 0.25 * population / 7));
		vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);
		
		adaptVacinationEffectiveness2(vaccinationConfig, 0.6);
		
		configureBooster(vaccinationConfig, 0.9, 7.0 * 0.01, 3, 0.6, LocalDate.parse("2021-12-14"), 0.7, params.vacSp);
			
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
		leisureTests.put(restrictionDate, params.leisT);

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

		leisureTestsVaccinated.put(restrictionDate, params.leisT);
		if (params.work.equals("testAll")) {
			workTestsVaccinated.put(restrictionDate, 0.5);
		}
				
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


		return config;
	}
	
private void adaptVacinationEffectiveness2(VaccinationConfigGroup vaccinationConfig, double vacMult) {
		
		double effectivnessAlphaMRNA =  0.85;
		double factorShowingSymptomsAlphaMRNA = 0.06 / (1 - effectivnessAlphaMRNA);
		double factorSeriouslySickAlphaMRNA = 0.02 / ((1 - effectivnessAlphaMRNA) * factorShowingSymptomsAlphaMRNA);
		
		double effectivnessDeltaMRNA = 0.8;
		double factorShowingSymptomsDeltaMRNA = 0.15 / (1 - effectivnessDeltaMRNA);
		double factorSeriouslySickDeltaMRNA = 0.09 / ((1 - effectivnessDeltaMRNA) * factorShowingSymptomsDeltaMRNA);
		
		double infectivityAlphaMRNA = 0.32;
		double infectivityDeltaMRNA = 0.5;
		
		int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot
		
		//Calculate effectiveness
		VaccinationConfigGroup.Parameter parameterWildtypeMRNA = VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2);
		{
			parameterWildtypeMRNA.atFullEffect(1.0 - ((1.0 - effectivnessAlphaMRNA) * vacMult));
			double fac = 0.9971;
			calculateVEPerDay(effectivnessAlphaMRNA, vacMult, fullEffectMRNA, parameterWildtypeMRNA, fac);
		}
		
		VaccinationConfigGroup.Parameter parameterAlphaMRNA = VaccinationConfigGroup.forStrain(VirusStrain.B117); 
		{
			parameterAlphaMRNA.atFullEffect(1.0 - ((1.0 - effectivnessAlphaMRNA) * vacMult));
			double fac = 0.9971;
			calculateVEPerDay(effectivnessAlphaMRNA, vacMult, fullEffectMRNA, parameterAlphaMRNA, fac);
		}
		
		VaccinationConfigGroup.Parameter parameterDeltaMRNA = VaccinationConfigGroup.forStrain(VirusStrain.MUTB);
		{
			parameterDeltaMRNA.atFullEffect(1.0 - ((1.0 - effectivnessDeltaMRNA) * vacMult));
			double fac = 0.9961;
			calculateVEPerDay(effectivnessDeltaMRNA, vacMult, fullEffectMRNA, parameterDeltaMRNA, fac);
		}
		VaccinationConfigGroup.Parameter parameterOmicronMRNA = VaccinationConfigGroup.forStrain(VirusStrain.OMICRON);
		{
			parameterOmicronMRNA.atFullEffect(1.0 - ((1.0 - effectivnessDeltaMRNA) * vacMult));
			double fac = 0.99;
			calculateVEPerDay(effectivnessDeltaMRNA, vacMult, fullEffectMRNA, parameterOmicronMRNA, fac);
		}
		
		//Calculate infectivity
		VaccinationConfigGroup.Parameter parameterWildtypeMRNAInfectivity = VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2);
		{
			parameterWildtypeMRNAInfectivity.atFullEffect(infectivityAlphaMRNA);
			double fac = 0.9968;
			calculateInfectivityPerDay(infectivityAlphaMRNA, fullEffectMRNA, parameterWildtypeMRNAInfectivity, fac);
		}
		
		VaccinationConfigGroup.Parameter parameterAlphaMRNAInfectivity = VaccinationConfigGroup.forStrain(VirusStrain.B117); 
		{
			parameterAlphaMRNAInfectivity.atFullEffect(infectivityAlphaMRNA);
			double fac = 0.9968;
			calculateInfectivityPerDay(infectivityAlphaMRNA, fullEffectMRNA, parameterAlphaMRNAInfectivity, fac);
		}
		
		VaccinationConfigGroup.Parameter parameterDeltaMRNAInfectivity = VaccinationConfigGroup.forStrain(VirusStrain.MUTB);
		{
			parameterDeltaMRNAInfectivity.atFullEffect(infectivityDeltaMRNA);
			double fac = 0.9916;
			calculateInfectivityPerDay(infectivityDeltaMRNA, fullEffectMRNA, parameterDeltaMRNAInfectivity, fac);
		}
		
		VaccinationConfigGroup.Parameter parameterOmicronMRNAInfectivity = VaccinationConfigGroup.forStrain(VirusStrain.OMICRON);
		{
			parameterOmicronMRNAInfectivity.atFullEffect(infectivityDeltaMRNA);
			double fac = 0.99;
			calculateInfectivityPerDay(infectivityDeltaMRNA, fullEffectMRNA, parameterOmicronMRNAInfectivity, fac);
		}
		
		
				
		vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
				.setDaysBeforeFullEffect(fullEffectMRNA)
				.setEffectiveness(parameterWildtypeMRNA)
				.setEffectiveness(parameterAlphaMRNA)
				.setEffectiveness(parameterDeltaMRNA)
				.setEffectiveness(parameterOmicronMRNA)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorShowingSymptomsAlphaMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atFullEffect(factorShowingSymptomsAlphaMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atFullEffect(factorShowingSymptomsDeltaMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atFullEffect(factorShowingSymptomsDeltaMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSeriouslySickAlphaMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atFullEffect(factorSeriouslySickAlphaMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atFullEffect(factorSeriouslySickDeltaMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON)
						.atFullEffect(factorSeriouslySickDeltaMRNA)
				)
				.setInfectivity(parameterWildtypeMRNAInfectivity)
				.setInfectivity(parameterAlphaMRNAInfectivity)
				.setInfectivity(parameterDeltaMRNAInfectivity)
				.setInfectivity(parameterOmicronMRNAInfectivity)
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
		
		//Calculate effectiveness
		VaccinationConfigGroup.Parameter parameterWildtypeVector = VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2);
		{
			parameterWildtypeVector.atFullEffect(1.0 - ((1.0 - effectivnessAlphaVector) * vacMult));
			double fac = 0.9953;
			calculateVEPerDay(effectivnessAlphaVector, vacMult, fullEffectVector, parameterWildtypeVector, fac);
		}

		VaccinationConfigGroup.Parameter parameterAlphaVector = VaccinationConfigGroup.forStrain(VirusStrain.B117); 
		{
			parameterAlphaVector.atFullEffect(1.0 - ((1.0 - effectivnessAlphaVector) * vacMult));
			double fac = 0.9953;
			calculateVEPerDay(effectivnessAlphaVector, vacMult, fullEffectVector, parameterAlphaVector, fac);
		}
		
		VaccinationConfigGroup.Parameter parameterDeltaVector = VaccinationConfigGroup.forStrain(VirusStrain.MUTB);
		{
			parameterDeltaVector.atFullEffect(1.0 - ((1.0 - effectivnessDeltaVector) * vacMult));
			double fac = 0.9948;
			calculateVEPerDay(effectivnessDeltaVector, vacMult, fullEffectVector, parameterDeltaVector, fac);
		}
		
		VaccinationConfigGroup.Parameter parameterOmicronVector = VaccinationConfigGroup.forStrain(VirusStrain.OMICRON);
		{
			parameterOmicronVector.atFullEffect(0.0);
		}
		
		//Calculate infectivity
		VaccinationConfigGroup.Parameter parameterWildtypeVectorInfectivity = VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2);
		{
			parameterWildtypeVectorInfectivity.atFullEffect(infectivityAlphaVector);
			double fac = 0.9962;
			calculateInfectivityPerDay(infectivityAlphaVector, fullEffectVector, parameterWildtypeVectorInfectivity, fac);
		}
		
		VaccinationConfigGroup.Parameter parameterAlphaVectorInfectivity = VaccinationConfigGroup.forStrain(VirusStrain.B117); 
		{
			parameterAlphaVectorInfectivity.atFullEffect(infectivityAlphaVector);
			double fac = 0.9962;
			calculateInfectivityPerDay(infectivityAlphaVector, fullEffectVector, parameterAlphaVectorInfectivity, fac);
		}
		
		VaccinationConfigGroup.Parameter parameterDeltaVectorInfectivity = VaccinationConfigGroup.forStrain(VirusStrain.MUTB);
		{
			parameterDeltaVectorInfectivity.atFullEffect(infectivityDeltaVector);
			double fac = 0.97;
			calculateInfectivityPerDay(infectivityDeltaVector, fullEffectVector, parameterDeltaVectorInfectivity, fac);
		}
		
		VaccinationConfigGroup.Parameter parameterOmicronVectorInfectivity = VaccinationConfigGroup.forStrain(VirusStrain.OMICRON);
		{
			parameterOmicronVectorInfectivity.atFullEffect(1.0);
		}
				
		vaccinationConfig.getOrAddParams(VaccinationType.vector)
				.setDaysBeforeFullEffect(fullEffectVector)
				.setEffectiveness(parameterWildtypeVector)
				.setEffectiveness(parameterAlphaVector)
				.setEffectiveness(parameterDeltaVector)
				.setEffectiveness(parameterOmicronVector)
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
				.setInfectivity(parameterWildtypeVectorInfectivity)
				.setInfectivity(parameterAlphaVectorInfectivity)
				.setInfectivity(parameterDeltaVectorInfectivity)
				.setInfectivity(parameterOmicronVectorInfectivity)
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
						.atFullEffect(1.0 - ((1.0 - effectivnessDeltaNatural) * vacMult))
						.atDay(100, 1.0 - ((1.0 - (effectivnessDeltaNatural - 0.06)) * vacMult))
						.atDay(400, 1.0 - ((1.0 - (effectivnessDeltaNatural - 0.14)) * vacMult))
						.atDay(700, 1.0 - ((1.0 - (effectivnessDeltaNatural - 0.22)) * vacMult))
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
						.atFullEffect(infectivityNatural)
				)
		;
		
		
	}

	private void calculateVEPerDay(double effectiveness, double vacMult, int fullEffect, VaccinationConfigGroup.Parameter parameter, double fac) {
		for (int day = 1; day < 720; day++) {
			double effectivenessOnDay = effectiveness * Math.pow(fac, day);
			effectivenessOnDay = 1.0 - ((1.0 - effectivenessOnDay) * vacMult);
			parameter.atDay(day + fullEffect, effectivenessOnDay);
		}
	}
	
	private void calculateInfectivityPerDay(double infectivity, int fullEffect, VaccinationConfigGroup.Parameter parameter, double fac) {
		for (int day = 1; day < 720; day++) {
			double infectivityOnDay = (1.0 - infectivity) * Math.pow(fac, day);
			infectivityOnDay = 1.0 - infectivityOnDay;
			parameter.atDay(day + fullEffect, infectivityOnDay);
		}
	}
	
	private void configureBooster(VaccinationConfigGroup vaccinationConfig, double boosterEff, double boosterSpeed, int boostAfter, double vacMult, LocalDate restrictionDate, double omicronBoost, double vacSp) {
		
		Map<LocalDate, Integer> boosterVaccinations = new HashMap<>();
				
		boosterVaccinations.put(LocalDate.parse("2020-01-01"), 0);

		boosterVaccinations.put(restrictionDate, (int) (2_352_480 * boosterSpeed / 7));
		boosterVaccinations.put(LocalDate.parse("2021-12-22"), (int) (2_352_480 * boosterSpeed * 0.5 / 7));
		boosterVaccinations.put(LocalDate.parse("2022-01-03"), (int) (2_352_480 * boosterSpeed * vacSp / 7));

		
		vaccinationConfig.setReVaccinationCapacity_pers_per_day(boosterVaccinations);
		
		//Calculate effectiveness
		VaccinationConfigGroup.Parameter parameterWildtype = VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2);
		VaccinationConfigGroup.Parameter parameterAlpha = VaccinationConfigGroup.forStrain(VirusStrain.B117); 
		VaccinationConfigGroup.Parameter parameterDelta = VaccinationConfigGroup.forStrain(VirusStrain.MUTB);		
		VaccinationConfigGroup.Parameter parameterOmicron = VaccinationConfigGroup.forStrain(VirusStrain.OMICRON);
		
		parameterWildtype.atDay(7, 1.0 - ((1.0 - boosterEff) * vacMult));
		parameterAlpha.atDay(7, 1.0 - ((1.0 - boosterEff) * vacMult));
		parameterDelta.atDay(7, 1.0 - ((1.0 - boosterEff) * vacMult));
		parameterOmicron.atDay(7, 1.0 - ((1.0 - omicronBoost) * vacMult));

		double facEffectiveness = 0.9961;

		calculateVEPerDay(boosterEff, vacMult, 7, parameterWildtype, facEffectiveness);
		calculateVEPerDay(boosterEff, vacMult, 7, parameterAlpha, facEffectiveness);
		calculateVEPerDay(boosterEff, vacMult, 7, parameterDelta, facEffectiveness);
		calculateVEPerDay(omicronBoost, vacMult, 7, parameterOmicron, facEffectiveness);
		
		//Calculate infectivity
		VaccinationConfigGroup.Parameter parameterWildtypeInfectivity = VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2);
		VaccinationConfigGroup.Parameter parameterAlphaInfectivity = VaccinationConfigGroup.forStrain(VirusStrain.B117); 
		VaccinationConfigGroup.Parameter parameterDeltaInfectivity = VaccinationConfigGroup.forStrain(VirusStrain.MUTB);		
		VaccinationConfigGroup.Parameter parameterOmicronInfectivity = VaccinationConfigGroup.forStrain(VirusStrain.OMICRON);
		
		parameterWildtypeInfectivity.atDay(7, 0.5);
		parameterAlphaInfectivity.atDay(7, 0.5);
		parameterDeltaInfectivity.atDay(7, 0.5);
		parameterOmicronInfectivity.atDay(7, 0.5);
		
		double facInfectivity = 0.9916;
		calculateInfectivityPerDay(0.5, 7, parameterWildtypeInfectivity, facInfectivity);
		calculateInfectivityPerDay(0.5, 7, parameterAlphaInfectivity, facInfectivity);
		calculateInfectivityPerDay(0.5, 7, parameterDeltaInfectivity, facInfectivity);
		calculateInfectivityPerDay(0.5, 7, parameterOmicronInfectivity, facInfectivity);

			
		vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
				.setBoostEffectiveness(parameterWildtype)
				.setBoostEffectiveness(parameterAlpha)
				.setBoostEffectiveness(parameterDelta)
				.setBoostEffectiveness(parameterOmicron)
				.setBoostInfectivity(parameterWildtypeInfectivity)
				.setBoostInfectivity(parameterAlphaInfectivity)
				.setBoostInfectivity(parameterDeltaInfectivity)
				.setBoostInfectivity(parameterOmicronInfectivity)
				.setBoostWaitPeriod(boostAfter * 30 + 6 * 7);
		;
				
		vaccinationConfig.getOrAddParams(VaccinationType.vector)
				.setBoostEffectiveness(parameterWildtype)
				.setBoostEffectiveness(parameterAlpha)
				.setBoostEffectiveness(parameterDelta)
				.setBoostEffectiveness(parameterOmicron)
				.setBoostInfectivity(parameterWildtypeInfectivity)
				.setBoostInfectivity(parameterAlphaInfectivity)
				.setBoostInfectivity(parameterDeltaInfectivity)
				.setBoostInfectivity(parameterOmicronInfectivity)
				.setBoostWaitPeriod(boostAfter * 30 + 9 * 7);
		;
	}

	public static final class Params {

		@GenerateSeeds(5)
		public long seed;
		
		@Parameter({0.5, 0.75, 1.0})
		double leis;
		
		@Parameter({0.0, 0.5, 1.0})
		double vacSp;
		
		@Parameter({0.5, 0.75})
		double leisUnv;
		
		@StringParameter({"current", "protected", "testAll"})
		String work;
		
		@StringParameter({"current", "protected"})
		String school;
		
		@Parameter({0.2, 0.6})
		double leisT;
		
		@Parameter({0.5, 2.0})
		double oHosF;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneBMBF211219.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


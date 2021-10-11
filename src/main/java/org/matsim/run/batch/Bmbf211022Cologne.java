package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.matsim.episim.model.Transition.to;


/**
 * Batch for bmbf report
 */
public class Bmbf211022Cologne implements BatchRun<Bmbf211022Cologne.Params> {

	@Override
	public SnzCologneProductionScenario getBindings(int id, @Nullable Params params) {

		double pHousehold = 0.0;
		
		if (params != null) 
			pHousehold = params.pHousehold;
		
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

		LocalDate restrictionDate = LocalDate.parse("2021-09-27");

		SnzCologneProductionScenario module = getBindings(id, params);

		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setProgressionConfig(progressionConfig(params, Transition.config()).build());
		episimConfig.setDaysInfectious(Integer.MAX_VALUE);

		episimConfig.setCalibrationParameter(1.0e-05);

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 0.83 * 1.4 * params.thetaFactor);

//		episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-cologne-20210917/" + params.seed + "-270-2020-11-20.zip");
//		episimConfig.setSnapshotSeed(SnapshotSeed.restore);

		// age susceptibility increases by 28% every 10 years
		if (params.ageDep.equals("yes")) {
			episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() / 3.);
			Map<Integer, Double> map = new HashMap<>();
			for (int i = 0; i<120; i++) map.put(i, Math.pow(1.02499323, i));
			episimConfig.setAgeSusceptibility(map);
		}
		
		//restrictions
		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy()).setHospitalScale(id);

		//curfew
		builder.restrict("2021-04-17", Restriction.ofClosingHours(21, 5), "leisure", "visit");
		Map<LocalDate, Double> curfewCompliance = new HashMap<LocalDate, Double>();
		curfewCompliance.put(LocalDate.parse("2021-04-17"), 1.0);
		curfewCompliance.put(LocalDate.parse("2021-05-31"), 0.0);
		if (params.curfew.equals("yes")) curfewCompliance.put(restrictionDate, 1.0);
		episimConfig.setCurfewCompliance(curfewCompliance);

		//masks
		if (params.masksEdu.equals("no")) builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.0), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		episimConfig.setPolicy(builder.build());

		//disease import 2020
		Map<LocalDate, Integer> importMap = new HashMap<>();
		double importFactorBeforeJune = 4.0;
		double imprtFctMult = 1.0;
		long importOffset = 0;
		double cologneFactor = 0.5;

		SnzCologneProductionScenario.interpolateImport(importMap, cologneFactor * imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-02-24").plusDays(importOffset),
				LocalDate.parse("2020-03-09").plusDays(importOffset), 0.9, 23.1);
		SnzCologneProductionScenario.interpolateImport(importMap, cologneFactor * imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-03-09").plusDays(importOffset),
				LocalDate.parse("2020-03-23").plusDays(importOffset), 23.1, 3.9);
		SnzCologneProductionScenario.interpolateImport(importMap, cologneFactor * imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-03-23").plusDays(importOffset),
				LocalDate.parse("2020-04-13").plusDays(importOffset), 3.9, 0.1);

		importMap.put(LocalDate.parse("2020-07-19"), (int) (0.5 * 32));
		importMap.put(LocalDate.parse("2020-08-09"), 1);

		episimConfig.setInfections_pers_per_day(importMap);


		//weather model
		try {
			Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutDoorFractionFromDateAndTemp2(SnzCologneProductionScenario.INPUT.resolve("cologneWeather.csv").toFile(),
					SnzCologneProductionScenario.INPUT.resolve("weatherDataAvgCologne2000-2020.csv").toFile(), 0.5, 18.5, 25.0, 18.5, 18.5, 5., 1.0);
			episimConfig.setLeisureOutdoorFraction(outdoorFractions);
		} catch (IOException e) {
			e.printStackTrace();
		}


		//mutations and vaccinations
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		Map<LocalDate, Integer> infPerDayB117 = new HashMap<>();
		infPerDayB117.put(LocalDate.parse("2020-01-01"), 0);

		infPerDayB117.put(LocalDate.parse("2020-12-30"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayB117);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(1.7);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySick(1.0);

		Map<LocalDate, Integer> infPerDayMUTB = new HashMap<>();
		infPerDayMUTB.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayMUTB.put(LocalDate.parse("2021-04-05"), 1);

		//disease import 2021
		infPerDayMUTB.put(LocalDate.parse("2021-07-25"), (int) (0.5 * 48 * 2));
		infPerDayMUTB.put(LocalDate.parse("2021-08-15"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.MUTB, infPerDayMUTB);
		
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setInfectiousness(2.2);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setFactorSeriouslySick(2.0);

		Map<Integer, Double> vaccinationCompliance = new HashMap<>();

		for (int i = 0; i < 12; i++) vaccinationCompliance.put(i, 0.0);
		for (int i = 12; i < 18; i++) vaccinationCompliance.put(i, 0.7);
		for (int i = 18; i < 25; i++) vaccinationCompliance.put(i, 0.7);
		for (int i = 25; i < 40; i++) vaccinationCompliance.put(i, 0.75);
		for (int i = 40; i < 65; i++) vaccinationCompliance.put(i, 0.8);
		for (int i = 65; i <= 120; i++) vaccinationCompliance.put(i, 0.9);

		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);
		
		if (params.newVacConfig.equals("yes")) {
			adaptVacinationConfig(vaccinationConfig);
		}
			
		//testing
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);

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
		leisureTests.put(LocalDate.parse("2020-01-01"), 0.);
		workTests.put(LocalDate.parse("2020-01-01"), 0.);
		eduTests.put(LocalDate.parse("2020-01-01"), 0.);

		for (int i = 1; i <= 31; i++) {
			leisureTests.put(testingStartDate.plusDays(i),  0.25 * i / 31.);
			workTests.put(testingStartDate.plusDays(i), 0.25 * i / 31.);
			eduTests.put(testingStartDate.plusDays(i), 0.8 * i / 31.);
		}


		eduTests.put(LocalDate.parse("2021-06-24"), 0.0);
		workTests.put(LocalDate.parse("2021-06-04"), 0.05);
		workTests.put(restrictionDate,  params.testRateWork);


		leisureTests.put(LocalDate.parse("2021-06-04"), 0.05);
//		leisureTests.put(LocalDate.parse("2021-08-23"),  0.2);

		leisureTests.put(restrictionDate, params.testRateLeisure);


		eduTests.put(LocalDate.parse("2021-08-06"), 0.6);
		eduTests.put(LocalDate.parse("2021-08-30"), 0.4);
		eduTests.put(restrictionDate,  params.testRateEdu);

		rapidTest.setTestingRatePerActivityAndDate((Map.of(
				"leisure", leisureTests,
				"work", workTests,
				"business", workTests,
				"educ_kiga", eduTests,
				"educ_primary", eduTests,
				"educ_secondary", eduTests,
				"educ_tertiary", eduTests,
				"educ_higher", eduTests,
				"educ_other", eduTests
		)));

		Map<LocalDate, Double> leisureTestsPCR = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTestsPCR = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTestsPCR = new HashMap<LocalDate, Double>();
		leisureTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		workTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		eduTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);

//		eduTestsPCR.put(LocalDate.parse("2021-09-06"),  params.pcrTestEdu);
//		workTestsPCR.put(LocalDate.parse("2021-09-06"),  params.pcrTestWork);
//		leisureTestsPCR.put(LocalDate.parse("2021-09-06"),  params.pcrTestLeis);


//		eduTestsPCR.put(LocalDate.parse("2021-08-06"), 0.1);

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

		rapidTest.setTestingCapacity_pers_per_day(Map.of(
				LocalDate.of(1970, 1, 1), 0,
				testingStartDate, Integer.MAX_VALUE));

		pcrTest.setTestingCapacity_pers_per_day(Map.of(
				LocalDate.of(1970, 1, 1), 0,
				testingStartDate, Integer.MAX_VALUE));


		return config;
	}

	private void adaptVacinationConfig(VaccinationConfigGroup vaccinationConfig) {
		
		double effectivnessAlphaMRNA = 0.97;
		double effectivnessDeltaMRNA = 0.94;
		double factorShowingSymptomsMRNA = 0.5;
		double factorSeriouslySickMRNA = 0.5;
		
		int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot
		vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
				.setDaysBeforeFullEffect(fullEffectMRNA)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 0.0)
						.atFullEffect(effectivnessAlphaMRNA)
						.atDay(100, 0.95)
						.atDay(400, 0.92)
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 0.0)
						.atFullEffect(effectivnessAlphaMRNA)
						.atDay(100, 0.95)
						.atDay(400, 0.92)
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 0.0)
						.atFullEffect(effectivnessDeltaMRNA)
						.atDay(100, 0.9)
						.atDay(400, 0.84)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickMRNA)
				)
		;
		
		double effectivnessAlphaVector = 0.82;
		double effectivnessDeltaVector = 0.54;
		double factorShowingSymptomsVector = 0.5;
		double factorSeriouslySickVector = 0.5;
		
		int fullEffectVector = 10 * 7; //second shot after 9 weeks, full effect one week after second shot
		vaccinationConfig.getOrAddParams(VaccinationType.vector)
				.setDaysBeforeFullEffect(fullEffectVector)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 0.0)
						.atFullEffect(effectivnessAlphaVector)
						.atDay(100, 0.74)
						.atDay(400, 0.62)
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 0.0)
						.atFullEffect(effectivnessAlphaVector)
						.atDay(100, 0.74)
						.atDay(400, 0.62)
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 0.0)
						.atFullEffect(effectivnessDeltaVector)
						.atDay(100, 0.42)
						.atDay(400, 0.3)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.B117)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickVector)
				)
		;
		
		double effectivnessAlphaNatural = 0.96;
		double effectivnessDeltaNatural = 0.9;
		double factorShowingSymptomsNatural = 0.5;
		double factorSeriouslySickNatural = 0.5;
		
		int fullEffectNatural = 7;
		vaccinationConfig.getOrAddParams(VaccinationType.natural)
				.setDaysBeforeFullEffect(fullEffectNatural)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 0.0)
						.atFullEffect(effectivnessAlphaNatural)
						.atDay(100, 0.94)
						.atDay(400, 0.89)
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 0.0)
						.atFullEffect(effectivnessAlphaNatural)
						.atDay(100, 0.94)
						.atDay(400, 0.89)
				)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 0.0)
						.atFullEffect(effectivnessDeltaNatural)
						.atDay(100, 0.84)
						.atDay(400, 0.76)
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
		;
		
		
	}

	public static final class Params {

		@GenerateSeeds(5)
		public long seed;

		@Parameter({0.4})
		double testRateEdu;
		
		@Parameter({0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0})
		double thetaFactor;
		
		@Parameter({0.0, 0.33, 0.66})
		double pHousehold;

		@Parameter({0.05})
		double testRateWork;

		@Parameter({0.05})
		double testRateLeisure;
		
		@IntParameter({180, Integer.MAX_VALUE})
		int recSus;

		@StringParameter({"no"})
		String curfew;

		@StringParameter({"no"})
		String masksEdu;
		
		@StringParameter({"no"})
		String ageDep;
		
		@StringParameter({"no", "yes"})
		String newVacConfig;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, Bmbf211022Cologne.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}

	/**
	 * Adds progression config to the given builder.
	 * @param params
	 */
	private static Transition.Builder progressionConfig(Params params, Transition.Builder builder) {

		Transition transitionRecSus;

		if (params.recSus != 180 ) {
		transitionRecSus = Transition.fixed(params.recSus);
		}
		else {
			transitionRecSus = Transition.logNormalWithMedianAndStd(params.recSus, 10.);
		
		}

		return builder
				// Inkubationszeit: Die Inkubationszeit [ ... ] liegt im Mittel (Median) bei 5–6 Tagen (Spannweite 1 bis 14 Tage)
				.from(EpisimPerson.DiseaseStatus.infectedButNotContagious,
						to(EpisimPerson.DiseaseStatus.contagious, Transition.fixed(0)))

// Dauer Infektiosität:: Es wurde geschätzt, dass eine relevante Infektiosität bereits zwei Tage vor Symptombeginn vorhanden ist und die höchste Infektiosität am Tag vor dem Symptombeginn liegt
// Dauer Infektiosität: Abstrichproben vom Rachen enthielten vermehrungsfähige Viren bis zum vierten, aus dem Sputum bis zum achten Tag nach Symptombeginn
				.from(EpisimPerson.DiseaseStatus.contagious,
						to(EpisimPerson.DiseaseStatus.showingSymptoms, Transition.logNormalWithMedianAndStd(6., 6.)),    //80%
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(8., 8.)))            //20%

// Erkankungsbeginn -> Hospitalisierung: Eine Studie aus Deutschland zu 50 Patienten mit eher schwereren Verläufen berichtete für alle Patienten eine mittlere (Median) Dauer von vier Tagen (IQR: 1–8 Tage)
				.from(EpisimPerson.DiseaseStatus.showingSymptoms,
						to(EpisimPerson.DiseaseStatus.seriouslySick, Transition.logNormalWithMedianAndStd(5., 5.)),
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(8., 8.)))

// Hospitalisierung -> ITS: In einer chinesischen Fallserie betrug diese Zeitspanne im Mittel (Median) einen Tag (IQR: 0–3 Tage)
				.from(EpisimPerson.DiseaseStatus.seriouslySick,
						to(EpisimPerson.DiseaseStatus.critical, Transition.logNormalWithMedianAndStd(1., 1.)),
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(14., 14.)))

// Dauer des Krankenhausaufenthalts: „WHO-China Joint Mission on Coronavirus Disease 2019“ wird berichtet, dass milde Fälle im Mittel (Median) einen Krankheitsverlauf von zwei Wochen haben und schwere von 3–6 Wochen
				.from(EpisimPerson.DiseaseStatus.critical,
						to(EpisimPerson.DiseaseStatus.seriouslySickAfterCritical, Transition.logNormalWithMedianAndStd(21., 21.)))

				.from(EpisimPerson.DiseaseStatus.seriouslySickAfterCritical,
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(7., 7.)))

				.from(EpisimPerson.DiseaseStatus.recovered,
						to(EpisimPerson.DiseaseStatus.susceptible, transitionRecSus))
				;
	}


}


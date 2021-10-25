package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.EpisimConfigGroup.SnapshotSeed;
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
 * Calibration
 */
public class Bmbf210924Cologne implements BatchRun<Bmbf210924Cologne.Params> {

	@Override
	public SnzCologneProductionScenario getBindings(int id, @Nullable Params params) {

//		boolean leisureNightly = false;
//		double leisureNightlyScale = 1.0;
//
//		if (params != null) {
//			if (params.leisureNightly.contains("true")) {
//				leisureNightly = true;
//				leisureNightlyScale = Double.parseDouble(params.leisureNightly.split("-")[1]);
//			}
//		}


		return new SnzCologneProductionScenario.Builder()
//				.setLeisureOffset( params == null ? 0d : params.leisureOffset)
//				.setLeisureNightly(leisureNightly)
				.setScale(1.3)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
//				.setLeisureNightlyScale(leisureNightlyScale)
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

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 0.83 * 1.4);

		episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-cologne-20210917/" + params.seed + "-270-2020-11-20.zip");

		episimConfig.setSnapshotSeed(SnapshotSeed.restore);


		//restrictions
		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy()).setHospitalScale(id);

//		builder.setHospitalScale(params.regrScale);


		builder.restrict("2021-04-17", Restriction.ofClosingHours(21, 5), "leisure", "visit");
		Map<LocalDate, Double> curfewCompliance = new HashMap<LocalDate, Double>();
		curfewCompliance.put(LocalDate.parse("2021-04-17"), 1.0);
		curfewCompliance.put(LocalDate.parse("2021-05-31"), 0.0);

		if (params.curfew.equals("yes")) curfewCompliance.put(restrictionDate, 1.0);

		episimConfig.setCurfewCompliance(curfewCompliance);


		builder.restrict("2021-10-18", 1.0, "educ_higher");
		builder.restrict("2021-12-24", 0.2, "educ_higher");
		builder.restrict("2022-01-08", 1.0, "educ_higher");

		builder.apply("2020-10-15", "2020-12-14", (d, e) -> e.put("fraction", 1 - 1.9 * (1 - (double) e.get("fraction"))), "leisure");

		builder.restrict("2021-08-17", Restriction.ofMask(FaceMask.N95, 0.9), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		if (params.masksEdu.equals("no")) builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.0), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		if (params.monday.equals("edu0pct") || params.monday.equals("edu50pct")) {
			double rf = 0.0;
			if (params.monday.equals("edu50pct")) rf = 0.5;

			for (int ii = 0; ii < Integer.MAX_VALUE; ii++) {

				LocalDate date = restrictionDate.plusDays(ii);
				//ignore holidays
				if (date.isAfter(LocalDate.parse("2021-10-11").minusDays(1)) && date.isBefore(LocalDate.parse("2021-10-23"))) continue;
				if (date.isAfter(LocalDate.parse("2021-12-24")) && date.isBefore(LocalDate.parse("2022-01-08"))) continue;

				if (restrictionDate.plusDays(ii).getDayOfWeek() == DayOfWeek.MONDAY) {
					builder.restrict(date, rf, "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
					builder.restrict(date.plusDays(1), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
				}
				if (restrictionDate.plusDays(ii).isAfter(LocalDate.parse("2023-06-01"))) {
					break;
				}
			}
		}

		if (!params.leisureUnv.equals("no")) {
			double fraction = Double.parseDouble(params.leisureUnv);
			builder.restrict(restrictionDate, Restriction.ofSusceptibleRf(fraction), "leisure");
		}

		episimConfig.setPolicy(builder.build());


		if (params.monday.equals("sunday")) {
			Map<LocalDate, DayOfWeek> inputDays = new HashMap<>();
			for (int ii = 0; ii < Integer.MAX_VALUE; ii++) {
				if (restrictionDate.plusDays(ii).getDayOfWeek() == DayOfWeek.MONDAY) {
					inputDays.put(restrictionDate.plusDays(ii), DayOfWeek.SUNDAY);
				}
				if (restrictionDate.plusDays(ii).isAfter(LocalDate.parse("2023-06-01"))) {
					break;
				}
			}
			episimConfig.setInputDays(inputDays);
		}





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

		infPerDayB117.put(LocalDate.parse(params.alphaDate), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayB117);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(1.7);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySick(1.0);

		Map<LocalDate, Integer> infPerDayMUTB = new HashMap<>();
		infPerDayMUTB.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayMUTB.put(LocalDate.parse(params.deltaDate), 1);

		double importFactor = 4.0;
//		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB,  1.0, LocalDate.parse("2021-06-14").plusDays(0),
//				LocalDate.parse("2021-06-21").plusDays(0), 1.0, 0.5 * importFactor * 1.6);
//		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB,  0.5 * importFactor, LocalDate.parse("2021-06-21").plusDays(0),
//				LocalDate.parse("2021-06-28").plusDays(0), 1.6, 2.8);
//		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB,  0.5 * importFactor, LocalDate.parse("2021-06-28").plusDays(0),
//				LocalDate.parse("2021-07-05").plusDays(0), 2.8, 4.6);
//		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB,  0.5 * importFactor, LocalDate.parse("2021-07-05").plusDays(0),
//				LocalDate.parse("2021-07-12").plusDays(0), 4.6, 5.9);
//		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB,  0.5 * importFactor, LocalDate.parse("2021-07-12").plusDays(0),
//				LocalDate.parse("2021-07-19").plusDays(0), 5.9, 7.3);
//		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB,  0.5 * importFactor, LocalDate.parse("2021-07-19").plusDays(0),
//				LocalDate.parse("2021-07-26").plusDays(0), 7.3, 10.2);
//		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB,  0.5 * importFactor, LocalDate.parse("2021-07-26").plusDays(0),
//				LocalDate.parse("2021-08-02").plusDays(0), 10.2, 13.2);
//

		infPerDayMUTB.put(LocalDate.parse("2021-07-25"), (int) (0.5 * 48 * 2));
		infPerDayMUTB.put(LocalDate.parse("2021-08-15"), 1);


		episimConfig.setInfections_pers_per_day(VirusStrain.MUTB, infPerDayMUTB);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setInfectiousness(2.2);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setFactorSeriouslySick(2.0);


		double effectivnessMRNA = 0.7;
		double factorShowingSymptomsMRNA =  0.12 / (1 - effectivnessMRNA);
		double factorSeriouslySickMRNA = 0.02 / ((1 - effectivnessMRNA) * factorShowingSymptomsMRNA);
		int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot
		vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
				.setDaysBeforeFullEffect(fullEffectMRNA)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 0.0)
						.atDay(fullEffectMRNA-7, effectivnessMRNA/2.)
						.atFullEffect(effectivnessMRNA)
						.atDay(fullEffectMRNA + 5*365, 0.0) //10% reduction every 6 months (source: TC)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atDay(fullEffectMRNA-7, 1.0 - ((1.0 - factorShowingSymptomsMRNA) / 2.))
						.atFullEffect(factorShowingSymptomsMRNA)
						.atDay(fullEffectMRNA + 5*365, 1.0) //10% reduction every 6 months (source: TC)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atDay(fullEffectMRNA-7, 1.0 - ((1.0 - factorSeriouslySickMRNA) / 2.))
						.atFullEffect(factorSeriouslySickMRNA)
						.atDay(fullEffectMRNA + 5*365, 1.0) //10% reduction every 6 months (source: TC)
				)
				;

		double effectivnessVector = 0.7 * 0.5/0.7;
		double factorShowingSymptomsVector = 0.32 / (1 - effectivnessVector);
		double factorSeriouslySickVector = 0.15 / ((1 - effectivnessVector) * factorShowingSymptomsVector);
		int fullEffectVector = 10 * 7; //second shot after 9 weeks, full effect one week after second shot

		vaccinationConfig.getOrAddParams(VaccinationType.vector)
			.setDaysBeforeFullEffect(fullEffectVector)
			.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
					.atDay(1, 0.0)
					.atDay(fullEffectVector-7, effectivnessVector/2.)
					.atFullEffect(effectivnessVector)
					.atDay(fullEffectVector + 5*365, 0.0) //10% reduction every 6 months (source: TC)
			)
			.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
					.atDay(1, 1.0)
					.atDay(fullEffectVector-7, 1.0 - ((1.0 - factorShowingSymptomsVector) / 2.))
					.atFullEffect(factorShowingSymptomsVector)
					.atDay(fullEffectVector + 5*365, 1.0) //10% reduction every 6 months (source: TC)
			)
			.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
					.atDay(1, 1.0)
					.atDay(fullEffectVector-7, 1.0 - ((1.0 - factorSeriouslySickVector) / 2.))
					.atFullEffect(factorSeriouslySickVector)
					.atDay(fullEffectVector + 5*365, 1.0) //10% reduction every 6 months (source: TC)
			)
			;

		Map<Integer, Double> vaccinationCompliance = new HashMap<>();

		for (int i = 0; i < 12; i++) vaccinationCompliance.put(i, 0.0);
		for (int i = 12; i < 18; i++) vaccinationCompliance.put(i, 0.7);
		for (int i = 18; i < 25; i++) vaccinationCompliance.put(i, 0.7);
		for (int i = 25; i < 40; i++) vaccinationCompliance.put(i, 0.75);
		for (int i = 40; i < 65; i++) vaccinationCompliance.put(i, 0.8);
		for (int i = 65; i <= 120; i++) vaccinationCompliance.put(i, 0.9);

		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);

		//testing
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);

		if (params.testVaccinated.equals("yes")) {
 			testingConfigGroup.setTestAllPersonsAfter(restrictionDate);
 		}

//		TestType testType = TestType.valueOf(params.testType);

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

	public static final class Params {

		@GenerateSeeds(5)
		public long seed;

//		@Parameter({4.0})
//		double importFactor;

//		@Parameter({1.1, 1.2, 1.3, 1.4})
//		double thetaFactor;

//		@Parameter({1.0, 1.1, 1.2, 1.3, 1.4})
//		double scale;

//		@Parameter({1.9})
//		double leisureFactor;

//		@StringParameter({"true-1.0", "true-1.1", "true-1.2", "true-1.3", "true-1.4", "false"})
//		String leisureNightly;

//		@Parameter({0.25, 0.3, 0.35})
//		double leisureOffset;

		@StringParameter({"2020-12-30"})
		String alphaDate;

		@StringParameter({"2021-04-05"})
		String deltaDate;

		@Parameter({0.0, 0.4})
		double testRateEdu;

		@Parameter({0.05, 0.4})
		double testRateWork;

		@Parameter({0.05, 0.4})
		double testRateLeisure;

//		@Parameter({1.2, 1.3, 1.4})
//		double regrScale;

		@StringParameter({"no", "yes"})
		String curfew;

		@StringParameter({"no", "yes"})
		String masksEdu;

		@StringParameter({"current", "sunday", "edu0pct"})
		String monday;

		@StringParameter({"no", "yes"})
		String testVaccinated;

		@StringParameter({"no", "0.5", "0.0"})
		String leisureUnv;

//		@StringParameter({"2020-12-14"})
//		String leisureFactorUntil;

//		@Parameter({0.25, 0.5, 0.75})
//		double summerImportFactor;

//		@Parameter({0.25})
//		double tesRateLeisureWork;
//
//		@Parameter({0.05})
//		double tesRateLeisureWork2;

//		@StringParameter({"alpha", "0.5"})
//		String delta1Vac;

//		@StringParameter({"no"})
//		String schoolMasks;
//
//		@StringParameter({"2021-05-01"})
//		String deltaDate;



	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, Bmbf210924Cologne.class.getName(),
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

		transitionRecSus = Transition.logNormalWithMedianAndStd(180., 10.);

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


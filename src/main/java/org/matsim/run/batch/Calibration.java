package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.EpisimConfigGroup.SnapshotSeed;
import org.matsim.episim.model.Transition;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzProductionScenario.ChristmasModel;
import org.matsim.run.modules.SnzBerlinProductionScenario.Snapshot;
import org.matsim.run.modules.SnzProductionScenario.Tracing;

import javax.annotation.Nullable;

import static org.matsim.episim.model.Transition.to;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Calibration
 */
public class Calibration implements BatchRun<Calibration.Params> {

	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {

		return new SnzBerlinProductionScenario.Builder()
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
//				.setWeatherModel(params == null ? SnzBerlinProductionScenario.WeatherModel.midpoints_200_250 : params.weatherModel)
//				.setImportFactorBeforeJune(params == null ? 1d : params.importFactorBeforeJune)
//				.setImportFactorAfterJune(params == null ? 1d : params.importFactorAfterJune)
				.setEasterModel(SnzBerlinProductionScenario.EasterModel.no)
				.setChristmasModel(SnzBerlinProductionScenario.ChristmasModel.no)
				.createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = getBindings(id, params);

		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setProgressionConfig(progressionConfig(params, Transition.config()).build());
		episimConfig.setDaysInfectious(Integer.MAX_VALUE);

		episimConfig.setCalibrationParameter(1.0e-05);

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 0.83);

		episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-20210820/" + params.seed + "-270-2020-11-20.zip");

		episimConfig.setSnapshotSeed(SnapshotSeed.restore);


//		if (id == 1)
//			episimConfig.setSnapshotInterval(100);

		//restrictions
		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		builder.restrict("2021-04-06", Restriction.ofClosingHours(21, 5), "leisure", "visit");
		Map<LocalDate, Double> curfewCompliance = new HashMap<LocalDate, Double>();
		curfewCompliance.put(LocalDate.parse("2021-04-06"), 1.0);
		curfewCompliance.put(LocalDate.parse("2021-05-16"), 0.0);
		episimConfig.setCurfewCompliance(curfewCompliance);

		//kein zusätzliches Lüften mehr nach den Sommerferien
		builder.restrict("2021-08-07", Restriction.ofCiCorrection(1.0), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		builder.restrict("2021-10-18", 1.0, "educ_higher");
		builder.restrict("2021-12-20", 0.2, "educ_higher");
		builder.restrict("2022-01-02", 1.0, "educ_higher");


		/* These entries will have no effect when extrapolation is based on hospital numbers
		builder.restrict("2021-10-11", 0.83, "work", "business");
		builder.restrict("2021-10-23", 1.0, "work", "business");

		builder.restrict("2021-12-20", 0.83, "work", "business");
		builder.restrict("2022-01-02", 1.0, "work", "business");
		 */

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		//weather model
		try {
			Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractions2(SnzBerlinProductionScenario.INPUT.resolve("tempelhofWeatherUntil20210816.csv").toFile(),
					SnzBerlinProductionScenario.INPUT.resolve("temeplhofWeatherDataAvg2000-2020.csv").toFile(), 0.5, 18.5, 25.0, 5.);
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

		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(params.alphaInf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySick(params.alphaSeriouslySickFactor);

		Map<LocalDate, Integer> infPerDayMUTB = new HashMap<>();
		infPerDayMUTB.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayMUTB.put(LocalDate.parse("2021-04-07"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.MUTB, infPerDayMUTB);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setInfectiousness(params.deltaInf);


		if (params.delta1Vac.equals("0.5")) {
			double effectivnessMRNA = params.deltaEscape * 0.7;
			double factorShowingSymptomsMRNA = params.deltaEscape * 0.05 / (1 - effectivnessMRNA);
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
							.atDay(fullEffectMRNA-7, factorShowingSymptomsMRNA*2.)
							.atFullEffect(factorShowingSymptomsMRNA)
							.atDay(fullEffectMRNA + 5*365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 1.0)
							.atDay(fullEffectMRNA-7, factorSeriouslySickMRNA*2.)
							.atFullEffect(factorSeriouslySickMRNA)
							.atDay(fullEffectMRNA + 5*365, 1.0) //10% reduction every 6 months (source: TC)
					)
					;

			double effectivnessVector = params.deltaEscape * 0.5;
			double factorShowingSymptomsVector = params.deltaEscape * 0.25 / (1 - effectivnessVector);
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
						.atDay(fullEffectVector-7, factorShowingSymptomsVector*2.)
						.atFullEffect(factorShowingSymptomsVector)
						.atDay(fullEffectVector + 5*365, 1.0) //10% reduction every 6 months (source: TC)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atDay(fullEffectVector-7, factorSeriouslySickVector*2.)
						.atFullEffect(factorSeriouslySickVector)
						.atDay(fullEffectVector + 5*365, 1.0) //10% reduction every 6 months (source: TC)
				)
				;
		}

		else {
			double effectivnessMRNA = params.deltaEscape * 0.7;
			double factorShowingSymptomsMRNA = params.deltaEscape * 0.05 / (1 - effectivnessMRNA);
			double factorSeriouslySickMRNA = 0.02 / ((1 - effectivnessMRNA) * factorShowingSymptomsMRNA);
			int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot
			vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
					.setDaysBeforeFullEffect(fullEffectMRNA)
					.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 0.0)
							.atFullEffect(effectivnessMRNA)
							.atDay(fullEffectMRNA + 5*365, 0.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 1.0)
							.atFullEffect(factorShowingSymptomsMRNA)
							.atDay(fullEffectMRNA + 5*365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 1.0)
							.atFullEffect(factorSeriouslySickMRNA)
							.atDay(fullEffectMRNA + 5*365, 1.0) //10% reduction every 6 months (source: TC)
					)
					;

			double effectivnessVector = params.deltaEscape * 0.5;
			double factorShowingSymptomsVector = params.deltaEscape * 0.25 / (1 - effectivnessVector);
			double factorSeriouslySickVector = 0.15 / ((1 - effectivnessVector) * factorShowingSymptomsVector);
			int fullEffectVector = 10 * 7; //second shot after 9 weeks, full effect one week after second shot

			vaccinationConfig.getOrAddParams(VaccinationType.vector)
				.setDaysBeforeFullEffect(fullEffectVector)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 0.0)
						.atFullEffect(effectivnessVector)
						.atDay(fullEffectVector + 5*365, 0.0) //10% reduction every 6 months (source: TC)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(factorShowingSymptomsVector)
						.atDay(fullEffectVector + 5*365, 1.0) //10% reduction every 6 months (source: TC)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atFullEffect(factorSeriouslySickVector)
						.atDay(fullEffectVector + 5*365, 1.0) //10% reduction every 6 months (source: TC)
				)
				;
		}



		Map<Integer, Double> vaccinationCompliance = new HashMap<>();

		for (int i = 0; i < 16; i++) vaccinationCompliance.put(i, 0.0);
		for (int i = 16; i < 25; i++) vaccinationCompliance.put(i, 0.7);
		for (int i = 25; i < 40; i++) vaccinationCompliance.put(i, 0.75);
		for (int i = 40; i < 65; i++) vaccinationCompliance.put(i, 0.8);
		for (int i = 65; i <= 120; i++) vaccinationCompliance.put(i, 0.9);

		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);

		//testing
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);

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
			leisureTests.put(testingStartDate.plusDays(i), params.testFactor * 0.1 * i / 31.);
			workTests.put(testingStartDate.plusDays(i), params.testFactor * 0.1 * i / 31.);
			eduTests.put(testingStartDate.plusDays(i), params.testFactor * 0.4 * i / 31.);
		}

		eduTests.put(LocalDate.parse("2021-06-24"), 0.0);
		workTests.put(LocalDate.parse("2021-06-04"), params.testFactor * params.testRateJune);
		leisureTests.put(LocalDate.parse("2021-06-04"), params.testFactor * params.testRateJune);

//		eduTests.put(LocalDate.parse("2021-08-06"), 0.2);

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

		eduTestsPCR.put(LocalDate.parse("2021-08-06"), 0.1);

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

//		@Parameter({0.78, 0.79, 0.80, 0.81, 0.82, 0.83, 0.84, 0.85, 0.86, 0.87, 0.88, 0.89})
//		double thetaFactor;

//		@Parameter({16.5, 17.5, 18.5})
//		double midPointSpring;
//
//		@Parameter({24.5, 25.0, 25.5})
//		double midPointFall;

		@StringParameter({"2020-11-30", "2020-12-05"})
		String alphaDate;

		@Parameter({1.0})
		double alphaSeriouslySickFactor;

		@Parameter({1.7})
		double alphaInf;

		@Parameter({2.5, 2.7, 2.9})
		double deltaInf;

		@Parameter({0.7, 1.0})
		double deltaEscape;

		@StringParameter({"alpha", "0.5"})
		String delta1Vac;

		@IntParameter({1, 2})
		int testFactor;

		@Parameter({0.0, 0.05, 0.1})
		double testRateJune;

//		@StringParameter({"no"})
//		String christmasModel;

//		@IntParameter({1, 2, 4, 8})
//		int testFactorLeisureWork;

//		@EnumParameter(value = SnzBerlinProductionScenario.WeatherModel.class, ignore = {"no", "midpoints_175_175"})
//		SnzBerlinProductionScenario.WeatherModel weatherModel;

//		@Parameter({0.7})
//		double mutBVaccinationEffectiveness;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, Calibration.class.getName(),
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


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
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzCologneProductionScenario;


import javax.annotation.Nullable;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.matsim.episim.EpisimConfigGroup.*;
import static org.matsim.episim.model.Transition.to;

public class JRBatchContactIntensity implements BatchRun<JRBatchContactIntensity.Params> {


	boolean DEBUG_MODE = true;

	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder()
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setLocationBasedContactIntensity(SnzBerlinProductionScenario.LocationBasedContactIntensity.yes)
				.setLocationBasedRestrictions(params != null ? params.locationBasedRestrictions : DistrictLevelRestrictions.yesForHomeLocation)
				.setActivityHandling(ActivityHandling.startOfDay)
				.setChristmasModel(SnzBerlinProductionScenario.ChristmasModel.no)
				.setEasterModel(SnzBerlinProductionScenario.EasterModel.no)
				.setSample(DEBUG_MODE ? 1 : 25)
				.createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "ci");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = getBindings(id, params);
		assert module != null;

		if (DEBUG_MODE) {
			if (params.seed == 4711 && params.thetaFactor == 1.0 && params.ciModifier == 0.) {

			} else {
				return null;
			}
		}

		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		//BMBF STUFF
		{

			// BMBF PARAMS
			double deltaInf = 2.2;

			double deltaVacEffect = 0.7;

			double importFactor = 4.0;

//			String eduTests = "current";
//
//			String workTests = "current";
//
//			String leisureTests = "current";

			String testVac = "no";

			String workUnv = "no";

			String leisureUnv = "no";

			episimConfig.setProgressionConfig(progressionConfig(params, Transition.config()).build());
			episimConfig.setDaysInfectious(Integer.MAX_VALUE);

			episimConfig.setCalibrationParameter(1.0e-05);

			episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 0.83);

			//			episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-20210901/" + params.seed + "-270-2020-11-20.zip");

			//			episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.restore);


			//		if (id == 1)
			//			episimConfig.setSnapshotInterval(100);

			//restrictions
			FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

			builder.restrict("2021-04-06", Restriction.ofClosingHours(21, 5), "leisure", "visit");
			Map<LocalDate, Double> curfewCompliance = new HashMap<LocalDate, Double>();
			curfewCompliance.put(LocalDate.parse("2021-04-06"), 1.0);
			curfewCompliance.put(LocalDate.parse("2021-05-16"), 0.0);
			episimConfig.setCurfewCompliance(curfewCompliance);

			//kein zusätzliches Lüften mehr nach den Sommerferien
			builder.restrict("2021-08-07", Restriction.ofCiCorrection(1.0), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");


			//Maskenpflicht nach den Sommerferien
			builder.restrict(LocalDate.parse("2021-08-07"), Restriction.ofMask(Map.of(
					FaceMask.N95, 0.45,
					FaceMask.SURGICAL, 0.45)),
					"educ_primary", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

			//		if (params.schoolMasks.equals("no")) {
			builder.restrict(LocalDate.parse("2021-09-06"), Restriction.ofMask(Map.of(
					FaceMask.N95, 0.0,
					FaceMask.SURGICAL, 0.0)),
					"educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
			//		}


			builder.restrict("2021-10-18", 1.0, "educ_higher");
			builder.restrict("2021-12-20", 0.2, "educ_higher");
			builder.restrict("2022-01-02", 1.0, "educ_higher");

			if (!workUnv.equals("no")) {
				double fraction = Double.parseDouble(workUnv);
				builder.restrict("2021-09-06", Restriction.ofSusceptibleRf(fraction), "work", "business");
			}
			if (!leisureUnv.equals("no")) {
				double fraction = Double.parseDouble(leisureUnv);
				builder.restrict("2021-09-06", Restriction.ofSusceptibleRf(fraction), "leisure");
			}


			// These entries will have no effect when extrapolation is based on hospital numbers
			//		builder.restrict("2021-10-11", 0.83, "work", "business");
			//		builder.restrict("2021-10-23", 1.0, "work", "business");
			//
			//		builder.restrict("2021-12-20", 0.83, "work", "business");
			//		builder.restrict("2022-01-02", 1.0, "work", "business");

			episimConfig.setPolicy(builder.build());

			//weather model
			try {
				Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutDoorFractionFromDateAndTemp2(SnzBerlinProductionScenario.INPUT.resolve("tempelhofWeatherUntil20210905.csv").toFile(),
						SnzBerlinProductionScenario.INPUT.resolve("temeplhofWeatherDataAvg2000-2020.csv").toFile(), 0.5, 18.5, 25.0, 5., 1.0);
				episimConfig.setLeisureOutdoorFraction(outdoorFractions);
			} catch (IOException e) {
				e.printStackTrace();
			}

			//weather model
//			try {
//				Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutDoorFractionFromDateAndTemp2(SnzCologneProductionScenario.INPUT.resolve("cologneWeather.csv").toFile(),
//						SnzCologneProductionScenario.INPUT.resolve("weatherDataAvgCologne2000-2020.csv").toFile(), 0.5, 18.5, 25.0, 18.5, 18.5, 5., 1.0, 1.0);
//				episimConfig.setLeisureOutdoorFraction(outdoorFractions);
//			} catch (IOException e) {
//				e.printStackTrace();
//			}


			//mutations and vaccinations
			VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
			VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

			Map<LocalDate, Integer> infPerDayB117 = new HashMap<>();
			infPerDayB117.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayB117.put(LocalDate.parse("2020-12-05"), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayB117);

			virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(1.7);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySick(1.0);

			Map<LocalDate, Integer> infPerDayMUTB = new HashMap<>();
			infPerDayMUTB.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayMUTB.put(LocalDate.parse("2021-05-01"), 1);

//			double importFactor = importFactor;
			SnzBerlinProductionScenario.interpolateImport(infPerDayMUTB, 1.0, LocalDate.parse("2021-06-14").plusDays(0),
					LocalDate.parse("2021-06-21").plusDays(0), 1.0, importFactor * 1.6);
			SnzBerlinProductionScenario.interpolateImport(infPerDayMUTB, importFactor, LocalDate.parse("2021-06-21").plusDays(0),
					LocalDate.parse("2021-06-28").plusDays(0), 1.6, 2.8);
			SnzBerlinProductionScenario.interpolateImport(infPerDayMUTB, importFactor, LocalDate.parse("2021-06-28").plusDays(0),
					LocalDate.parse("2021-07-05").plusDays(0), 2.8, 4.6);
			SnzBerlinProductionScenario.interpolateImport(infPerDayMUTB, importFactor, LocalDate.parse("2021-07-05").plusDays(0),
					LocalDate.parse("2021-07-12").plusDays(0), 4.6, 5.9);
			SnzBerlinProductionScenario.interpolateImport(infPerDayMUTB, importFactor, LocalDate.parse("2021-07-12").plusDays(0),
					LocalDate.parse("2021-07-19").plusDays(0), 5.9, 7.3);
			SnzBerlinProductionScenario.interpolateImport(infPerDayMUTB, importFactor, LocalDate.parse("2021-07-19").plusDays(0),
					LocalDate.parse("2021-07-26").plusDays(0), 7.3, 10.2);
			SnzBerlinProductionScenario.interpolateImport(infPerDayMUTB, importFactor, LocalDate.parse("2021-07-26").plusDays(0),
					LocalDate.parse("2021-08-02").plusDays(0), 10.2, 13.2);

			SnzBerlinProductionScenario.interpolateImport(infPerDayMUTB, 1.0, LocalDate.parse("2021-08-09").plusDays(0),
					LocalDate.parse("2021-08-31").plusDays(0), importFactor * 13.2, 1.0);


			episimConfig.setInfections_pers_per_day(VirusStrain.DELTA, infPerDayMUTB);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setInfectiousness(deltaInf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySick(2.0);


			double effectivnessMRNA = deltaVacEffect;
			double factorShowingSymptomsMRNA = 0.12 / (1 - effectivnessMRNA);
			double factorSeriouslySickMRNA = 0.02 / ((1 - effectivnessMRNA) * factorShowingSymptomsMRNA);
			int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot
			vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
					.setDaysBeforeFullEffect(fullEffectMRNA)
					.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
							.atDay(1, 0.0)
							.atDay(fullEffectMRNA - 7, effectivnessMRNA / 2.)
							.atFullEffect(effectivnessMRNA)
							.atDay(fullEffectMRNA + 5 * 365, 0.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
							.atDay(1, 1.0)
							.atDay(fullEffectMRNA - 7, 1.0 - ((1.0 - factorShowingSymptomsMRNA) / 2.))
							.atFullEffect(factorShowingSymptomsMRNA)
							.atDay(fullEffectMRNA + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
							.atDay(1, 1.0)
							.atDay(fullEffectMRNA - 7, 1.0 - ((1.0 - factorSeriouslySickMRNA) / 2.))
							.atFullEffect(factorSeriouslySickMRNA)
							.atDay(fullEffectMRNA + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
			;

			double effectivnessVector = deltaVacEffect * 0.5 / 0.7;
			double factorShowingSymptomsVector = 0.32 / (1 - effectivnessVector);
			double factorSeriouslySickVector = 0.15 / ((1 - effectivnessVector) * factorShowingSymptomsVector);
			int fullEffectVector = 10 * 7; //second shot after 9 weeks, full effect one week after second shot

			vaccinationConfig.getOrAddParams(VaccinationType.vector)
					.setDaysBeforeFullEffect(fullEffectVector)
					.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
							.atDay(1, 0.0)
							.atDay(fullEffectVector - 7, effectivnessVector / 2.)
							.atFullEffect(effectivnessVector)
							.atDay(fullEffectVector + 5 * 365, 0.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
							.atDay(1, 1.0)
							.atDay(fullEffectVector - 7, 1.0 - ((1.0 - factorShowingSymptomsVector) / 2.))
							.atFullEffect(factorShowingSymptomsVector)
							.atDay(fullEffectVector + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
							.atDay(1, 1.0)
							.atDay(fullEffectVector - 7, 1.0 - ((1.0 - factorSeriouslySickVector) / 2.))
							.atFullEffect(factorSeriouslySickVector)
							.atDay(fullEffectVector + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
			;


			Map<Integer, Double> vaccinationCompliance = new HashMap<>();

			//		if (params.vacCompl.equals("current")) {
			for (int i = 0; i < 12; i++) vaccinationCompliance.put(i, 0.0);
			for (int i = 12; i < 18; i++) vaccinationCompliance.put(i, 0.7);
			for (int i = 18; i < 25; i++) vaccinationCompliance.put(i, 0.7);
			for (int i = 25; i < 40; i++) vaccinationCompliance.put(i, 0.75);
			for (int i = 40; i < 65; i++) vaccinationCompliance.put(i, 0.8);
			for (int i = 65; i <= 120; i++) vaccinationCompliance.put(i, 0.9);
			//		}
			//		else if (params.vacCompl.equals("0.9")) {
			//			for (int i = 0; i < 12; i++) vaccinationCompliance.put(i, 0.0);
			//			for (int i = 12; i < 18; i++) vaccinationCompliance.put(i, 0.9);
			//			for (int i = 18; i < 25; i++) vaccinationCompliance.put(i, 0.9);
			//			for (int i = 25; i < 40; i++) vaccinationCompliance.put(i, 0.9);
			//			for (int i = 40; i < 65; i++) vaccinationCompliance.put(i, 0.9);
			//			for (int i = 65; i <= 120; i++) vaccinationCompliance.put(i, 0.9);
			//		}
			//		else {
			//			throw new RuntimeException();
			//		}


			vaccinationConfig.setCompliancePerAge(vaccinationCompliance);

			//testing
			TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);

			if (testVac.equals("yes")) {
				testingConfigGroup.setSelection(TestingConfigGroup.Selection.ALL_PERSONS);
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
				leisureTests.put(testingStartDate.plusDays(i), 0.25 * i / 31.);
				workTests.put(testingStartDate.plusDays(i), 0.25 * i / 31.);
				eduTests.put(testingStartDate.plusDays(i), 0.8 * i / 31.);
			}


			eduTests.put(LocalDate.parse("2021-06-24"), 0.0);
			workTests.put(LocalDate.parse("2021-06-04"), 0.05);
//			if (!workTests.equals("current"))
//				workTests.put(LocalDate.parse("2021-09-06"), Double.parseDouble(workTests.split("-")[0]));


			leisureTests.put(LocalDate.parse("2021-06-04"), 0.05);
			//		leisureTests.put(LocalDate.parse("2021-08-23"),  0.2);

//			if (!leisureTests.equals("current"))
//				leisureTests.put(LocalDate.parse("2021-09-06"), Double.parseDouble(leisureTests.split("-")[0]));


			eduTests.put(LocalDate.parse("2021-08-06"), 0.6);
			eduTests.put(LocalDate.parse("2021-08-30"), 0.4);

//			if (!eduTests.equals("current"))
//				eduTests.put(LocalDate.parse("2021-09-06"), Double.parseDouble(eduTests.split("-")[0]));


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

//			if (!eduTests.equals("current"))
//				eduTestsPCR.put(LocalDate.parse("2021-09-06"), Double.parseDouble(eduTests.split("-")[1]));
//			if (!workTests.equals("current"))
//				workTestsPCR.put(LocalDate.parse("2021-09-06"), Double.parseDouble(workTests.split("-")[1]));
//			if (!leisureTests.equals("current"))
//				leisureTestsPCR.put(LocalDate.parse("2021-09-06"), Double.parseDouble(leisureTests.split("-")[1]));


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
		}

		episimConfig.setDistrictLevelRestrictionsAttribute("subdistrict"); //TODO: SET IN OTHER BATCH CLASSES; remove from prod scenario

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.thetaFactor);


		//			episimConfig.getOrAddContainerParams("home_15").setContactIntensity(1.47 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		//			episimConfig.getOrAddContainerParams("home_25").setContactIntensity(0.88 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		//			episimConfig.getOrAddContainerParams("home_35").setContactIntensity(0.63 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		//			episimConfig.getOrAddContainerParams("home_45").setContactIntensity(0.49 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		//			episimConfig.getOrAddContainerParams("home_55").setContactIntensity(0.40 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		//			episimConfig.getOrAddContainerParams("home_65").setContactIntensity(0.34 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		//			episimConfig.getOrAddContainerParams("home_75").setContactIntensity(0.29 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);

		episimConfig.getOrAddContainerParams("home_15").setContactIntensity(1.0 + 3 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_25").setContactIntensity(1.0 + 2 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_35").setContactIntensity(1.0 + 1 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_45").setContactIntensity(1.0).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_55").setContactIntensity(1.0 - 1 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_65").setContactIntensity(1.0 - 2 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_75").setContactIntensity(1.0 - 3 * params.ciModifier).setSpacesPerFacility(1);

		//		episimConfig.getOrAddContainerParams("home").setContactIntensity(1.).setSpacesPerFacility(1);

		return config;
	}

	public static final class Params {

		@EnumParameter(EpisimConfigGroup.DistrictLevelRestrictions.class)
		EpisimConfigGroup.DistrictLevelRestrictions locationBasedRestrictions;

		@GenerateSeeds(5)
		public long seed;

		@Parameter({0.95, 0.975, 1.0, 1.025, 1.05})
		double thetaFactor;

		@Parameter({0.0, 0.1, 0.2, 0.3})
		Double ciModifier;


	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRBatchContactIntensity.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_ITERATIONS, Integer.toString(100),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}

	/**
	 * Adds progression config to the given builder.
	 *
	 * @param params
	 */
	private static Transition.Builder progressionConfig(JRBatchContactIntensity.Params params, Transition.Builder builder) {

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


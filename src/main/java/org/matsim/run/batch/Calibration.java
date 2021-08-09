package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
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
				.setWeatherModel(params == null ? SnzBerlinProductionScenario.WeatherModel.midpoints_200_250 : params.weatherModel)
				.setImportFactor(params == null ? 1d : params.importFactor)
				.setEasterModel(SnzBerlinProductionScenario.EasterModel.no)
				.setChristmasModel(SnzBerlinProductionScenario.ChristmasModel.restrictive)
				.createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
	}

//	@Override
//	public int getOffset() {
//		return 10000;
//	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = getBindings(id, params);

		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(1.534558994278839e-05);

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.thetaFactor);

		if (id == 1)
			episimConfig.setSnapshotInterval(100);

		//restrictions
		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		builder.restrict("2021-04-06", Restriction.ofClosingHours(21, 5), "leisure", "visit");
		Map<LocalDate, Double> curfewCompliance = new HashMap<LocalDate, Double>();
		curfewCompliance.put(LocalDate.parse("2021-04-06"), 0.5);
		curfewCompliance.put(LocalDate.parse("2021-05-16"), 0.0);
		episimConfig.setCurfewCompliance(curfewCompliance);

		LocalDate date1 = LocalDate.parse("2021-08-06");
		//kein zusätzliches Lüften mehr nach den Sommerferien
		builder.restrict("2021-08-07", Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		builder.restrict(date1, 1.0, "shop_daily", "shop_other", "errands");

		builder.restrict(date1, 1.0, "work", "business", "leisure", "visit");

		builder.restrict("2021-10-18", 1.0, "educ_higher");

		builder.apply("2021-03-26", "2021-04-09", (d, e) -> e.put("fraction", 0.92 * (double) e.get("fraction")), "work", "business");
		builder.apply("2021-06-24", "2021-08-05", (d, e) -> e.put("fraction", 0.92 * (double) e.get("fraction")), "work", "business");

		/* These entries will have no effect when extrapolation is based on hospital numbers
		builder.restrict("2021-10-11", 0.83, "work", "business");
		builder.restrict("2021-10-23", 1.0, "work", "business");

		builder.restrict("2021-12-20", 0.83, "work", "business");
		builder.restrict("2022-01-02", 1.0, "work", "business");
		 */

		builder.restrict("2021-12-20", 0.2, "educ_higher");
		builder.restrict("2022-01-02", 1.0, "educ_higher");

		episimConfig.setPolicy(FixedPolicy.class, builder.build());


		//mutations and vaccinations
		Map<LocalDate, Integer> infPerDayB117 = new HashMap<>();
		infPerDayB117.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayB117.put(LocalDate.parse("2020-11-30"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayB117);

		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		double vaccineEffectiveness = vaccinationConfig.getParams(VaccinationType.generic).getEffectiveness();

		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(1.8);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setVaccineEffectiveness(1.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySick(1.5);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySickVaccinated(0.05 / (1 - vaccineEffectiveness));

		virusStrainConfigGroup.getOrAddParams(VirusStrain.SARS_CoV_2).setFactorSeriouslySickVaccinated(0.05 / (1 - vaccineEffectiveness));

		Map<LocalDate, Integer> infPerDayMUTB = new HashMap<>();
		infPerDayMUTB.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayMUTB.put(LocalDate.parse("2021-04-07"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.MUTB, infPerDayMUTB);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setInfectiousness(2.5);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setVaccineEffectiveness(params.mutBVaccinationEffectiveness / vaccineEffectiveness);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setReVaccineEffectiveness(1.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setFactorSeriouslySickVaccinated(0.05 / (1 - params.mutBVaccinationEffectiveness));

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
			leisureTests.put(testingStartDate.plusDays(i), 0.05 * i / 31.);
			workTests.put(testingStartDate.plusDays(i), 0.05 * i / 31.);
			eduTests.put(testingStartDate.plusDays(i), 0.2 * i / 31.);
		}

		eduTests.put(LocalDate.parse("2021-06-24"), 0.0);
		workTests.put(LocalDate.parse("2021-06-24"), 0.0);
		leisureTests.put(LocalDate.parse("2021-06-24"), 0.0);

		eduTests.put(LocalDate.parse("2021-08-06"), 0.2);

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

		@GenerateSeeds(20)
		public long seed;

		@Parameter({0.9, 1.0, 1.1})
		double thetaFactor;

		@Parameter({0.9, 1.0, 1.1})
		double importFactor;

		@EnumParameter(value = SnzBerlinProductionScenario.WeatherModel.class, ignore = "no")
		SnzBerlinProductionScenario.WeatherModel weatherModel;

		@Parameter({0.7})
		double mutBVaccinationEffectiveness;

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


}


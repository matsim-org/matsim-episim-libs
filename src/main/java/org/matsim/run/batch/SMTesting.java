package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TestingConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.BatchRun.StringParameter;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinProductionScenario.ChristmasModel;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Testing strategies
 */
public class SMTesting implements BatchRun<SMTesting.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder().setSnapshot(SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "testing");
	}
	
//	@Override
//	public int getOffset() {
//		return 6000;
//	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder().setSnapshot(
				SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
		Config config = module.config();
//		config.global().setRandomSeed(params.seed);

		config.global().setRandomSeed(3831662765844904176L);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		episimConfig.setStartFromSnapshot("../episim-snapshot-270-2020-11-20.zip");
//		episimConfig.setSnapshotInterval(30);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
		
		//extrapolate restrictions
		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			if (params.activityLevel.equals("67pct")) builder.restrict("2021-04-12", 0.67, act);
			if (params.activityLevel.equals("trend")) {
				for (int i = 1; i<100; i++) {
					double fraction = 0.78 + i * 0.01;
					if (fraction > 1.0) break;
					builder.restrict(LocalDate.parse("2021-03-28").plusDays(i * 7), 0.78 + i * 0.01, act);
				}
			}
		
		}
					
		//schools
		if (params.schools.equals("50%open")) {}
		
		if (params.schools.equals("open")) {
			builder.clearAfter( "2021-04-01", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict("2021-04-11", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			//Sommerferien
			builder.restrict("2021-06-24", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
			builder.restrict("2021-08-07", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		}
		
		if (params.schools.equals("closed")) {
			builder.clearAfter( "2021-04-01", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict("2021-04-01", .2, "educ_kiga");
		}
		
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setEffectiveness(0.9);
		vaccinationConfig.setDaysBeforeFullEffect(28);
		// Based on https://experience.arcgis.com/experience/db557289b13c42e4ac33e46314457adc
		// 4/3 because model is bigger than just Berlin
		
		Map<LocalDate, Integer> vaccinations = new HashMap<>();
		
		int population = 4_800_000;
		
		vaccinations.put(LocalDate.parse("2020-01-01"), 0);
		
		vaccinations.put(LocalDate.parse("2020-12-27"), (int) (0.003 * population / 6));
		vaccinations.put(LocalDate.parse("2021-01-02"), (int) ((0.007 - 0.004) * population / 7));
		vaccinations.put(LocalDate.parse("2021-01-09"), (int) ((0.013 - 0.007) * population / 7));
		vaccinations.put(LocalDate.parse("2021-01-16"), (int) ((0.017 - 0.013) * population / 7));
		vaccinations.put(LocalDate.parse("2021-01-23"), (int) ((0.024 - 0.017) * population / 7));
		vaccinations.put(LocalDate.parse("2021-01-30"), (int) ((0.030 - 0.024) * population / 7));
		vaccinations.put(LocalDate.parse("2021-02-06"), (int) ((0.034 - 0.030) * population / 7));
		vaccinations.put(LocalDate.parse("2021-02-13"), (int) ((0.039 - 0.034) * population / 7));
		vaccinations.put(LocalDate.parse("2021-02-20"), (int) ((0.045 - 0.039) * population / 7));
		vaccinations.put(LocalDate.parse("2021-02-27"), (int) ((0.057 - 0.045) * population / 7));
		vaccinations.put(LocalDate.parse("2021-03-06"), (int) ((0.071 - 0.057) * population / 7));
		vaccinations.put(LocalDate.parse("2021-03-13"), (int) ((0.088 - 0.071) * population / 7));
		vaccinations.put(LocalDate.parse("2021-03-20"), (int) ((0.105 - 0.088) * population / 7));
		vaccinations.put(LocalDate.parse("2021-03-27"), (int) ((0.120 - 0.105) * population / 7));
		
		if (!params.vaccinationRate.equals("current")) {
			vaccinations.put(LocalDate.parse("2021-04-12"), (int) (Double.parseDouble(params.vaccinationRate) * 4./3.));
		}

		// https://experience.arcgis.com/experience/db557289b13c42e4ac33e46314457adc
		//1.1. 0.3
		//8.1. 0.7
		//15.1. 1.3
		//22.1. 1.7
		//29.1. 2.4
		//5.2. 3.
		//12.2. 3.4
		//19.2. 3.9
		//26.2. 4.5
		// 5.3. 5.7
		//12.3. 7.1
		//19.3. 8.8
		//26.3.	10.5
		//2.4. 12

		
		vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);

		Map<LocalDate, Integer> infPerDayVariant = new HashMap<>();
		infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayVariant.put(LocalDate.parse("2020-12-05"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayVariant);
		
		//easter model. input days are set in productionScenario
		if (params.easterModel.equals("yes")) {
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				double fraction;
				switch (params.activityLevel) {
				case "current":
					fraction = 0.78;
					break;
				case "trend":
					fraction = 0.79;
					break;
				case "67pct":
					fraction = 0.78;
					break;
				default:
					throw new RuntimeException();
				}
				builder.restrict(LocalDate.parse("2021-04-02"), 1.0, act);
				builder.restrict(LocalDate.parse("2021-04-03"), 1.0, act);
				builder.restrict(LocalDate.parse("2021-04-04"), 1.0, act);
				builder.restrict(LocalDate.parse("2021-04-05"), 1.0, act);
				builder.restrict(LocalDate.parse("2021-04-06"), fraction, act);
			}
		}
		
		if (params.curfew.contains("19-5")) builder.restrict("2021-04-06", Restriction.ofClosingHours(19, 5), "leisure", "visit");
		if (params.curfew.contains("21-5")) builder.restrict("2021-04-06", Restriction.ofClosingHours(21, 5), "leisure", "visit");

		if (params.curfew.contains("50pct")) episimConfig.setCurfewCompliance(0.5);
		if (params.curfew.contains("100pct")) episimConfig.setCurfewCompliance(1.);

		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());
		
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);
		
		testingConfigGroup.setStrategy(TestingConfigGroup.Strategy.ACTIVITIES);
				
		List<String> actsList = new ArrayList<String>();
		String testingStrategy = "leisure&work&edu";
		
		if (testingStrategy.contains("leisure")) {
			actsList.add("leisure");
		}
		if (testingStrategy.contains("work")) {
			actsList.add("work");
			actsList.add("business");
		}
		if (testingStrategy.contains("edu")) {
			actsList.add("educ_kiga");
			actsList.add("educ_primary");
			actsList.add("educ_secondary");
			actsList.add("educ_tertiary");
			actsList.add("educ_other");
		}

		testingConfigGroup.setActivities(actsList);
		
		testingConfigGroup.setFalseNegativeRate(0.3);
		
		testingConfigGroup.setFalsePositiveRate(0.03);
		
		testingConfigGroup.setHouseholdCompliance(1.0);
				
//		testingConfigGroup.setTestingRatePerActivity((Map.of(
//				"leisure", Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[2]) / 100.,
//				"work", Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[1]) / 100.,
//				"business", Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[1]) / 100.,
//				"educ_kiga", Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[0]) / 100.,
//				"educ_primary", Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[0]) / 100.,
//				"educ_secondary", Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[0]) / 100.,
//				"educ_tertiary", Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[0]) / 100.,
//				"educ_other", Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[0]) / 100.
//				)));
		
		double leisureRate = Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[2]) / 100.;
		double workRate = Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[1]) / 100.;
		double eduRate = Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[0]) / 100.;

		double leisureW10 = 0.002 * 3_570_000 / 1_320_000;
		double leisureW11 = 0.004 * 3_570_000 / 1_320_000;
		double leisureW12 = 0.005 * 3_570_000 / 1_320_000;

		testingConfigGroup.setTestingRatePerActivityAndDate((Map.of(
				"leisure", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						LocalDate.parse("2021-03-11"), leisureW10,
						LocalDate.parse("2021-03-18"), leisureW11,
						LocalDate.parse("2021-03-25"), leisureW12,
						LocalDate.parse("2021-04-12"), leisureRate
						),
				"work", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						LocalDate.parse("2021-04-12"), workRate
						),
				"business", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						LocalDate.parse("2021-04-12"), workRate
						),
				"educ_kiga", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						LocalDate.parse("2021-04-12"), eduRate
						),
				"educ_primary", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						LocalDate.parse("2021-04-12"), eduRate
						),
				"educ_secondary", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						LocalDate.parse("2021-04-12"), eduRate
						),
				"educ_tertiary", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						LocalDate.parse("2021-04-12"), eduRate
						),
				"educ_other", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						LocalDate.parse("2021-04-12"), eduRate
						)
				)));

		testingConfigGroup.setTestingCapacity_pers_per_day(Map.of(
				LocalDate.of(1970, 1, 1), 0, 
				LocalDate.of(2021, 3, 11), Integer.MAX_VALUE));

				
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(2.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setVaccineEffectiveness(1.0);
		
//		if (!params.B1351.equals("no")) {
//			episimConfig.setInfections_pers_per_day(VirusStrain.B1351, Map.of(
//					LocalDate.parse("2020-01-01"), 0,
//					LocalDate.parse("2021-02-01"), 1
//					));
//			virusStrainConfigGroup.getOrAddParams(VirusStrain.B1351).setInfectiousness(Double.parseDouble(params.B1351.split("-")[0]));
//			virusStrainConfigGroup.getOrAddParams(VirusStrain.B1351).setVaccineEffectiveness(Double.parseDouble(params.B1351.split("-")[1]));
//		}

		
		if (params.outdoorModel.contains("no")) {
			Map<LocalDate, Double> outdoorFractions = episimConfig.getLeisureOutdoorFraction();
			Map<LocalDate, Double> outdoorFractionsNew = new HashMap<LocalDate, Double>();
			for (Entry<LocalDate, Double> entry : outdoorFractions.entrySet()) {
				if (entry.getKey().isBefore(LocalDate.parse("2021-04-06"))) {
					outdoorFractionsNew.put(entry.getKey(), entry.getValue());
				}
			}
			outdoorFractionsNew.put(LocalDate.parse("2021-04-06"), 0.0);
			episimConfig.setLeisureOutdoorFraction(outdoorFractionsNew);
		}
		
		return config;
	}

	public static final class Params {

//		@GenerateSeeds(1)
//		public long seed;

		@StringParameter({"current", "20000", "40000"})
		String vaccinationRate;
		
		@StringParameter({"50%open", "open", "closed"})
		public String schools;
		
//		@StringParameter({"2020-12-15"})
//		String newVariantDate;
		
		@StringParameter({"current", "trend", "67pct"})
		String activityLevel;
		
//		@StringParameter({"FIXED_DAYS", "leisure", "work", "edu", "leisure&edu", "leisure&work", "work&edu", "leisure&work&edu"})
//		@StringParameter({"leisure&work&edu"})
//		String testingStrategy;
		
//		@Parameter({0.0, 0.02, 0.1, 0.2, 0.3})
//		double testingRateLeisure;
//		
//		@Parameter({0.0, 0.2, 0.4, 0.6})
//		double testingRateWork;
//		
//		@Parameter({0.0, 0.2, 0.4, 0.6})
//		double testingRateEdu;
		
		@StringParameter({
			"0-0-0", 
			"20-0-0", "20-20-0", "20-20-2", "20-20-10", "20-20-20", 
			"40-0-0", "40-20-0","40-20-2", "40-20-10", "40-20-20", "40-40-0", "40-40-2", "40-40-10", "40-40-20"
			})
		String testingRateEduWorkLeisure;
		
		@StringParameter({"yes", "no"})
		public String easterModel;
		
		@StringParameter({"no", "19-5-50pct", "19-5-100pct", "21-5-50pct", "21-5-100pct"})
		public String curfew;
		
//		@StringParameter({"no", "1.0-1.0", "1.0-0.5", "1.0-0.0", "2.0-1.0", "2.0-0.5", "2.0-0.0"})
//		public String B1351;
		
//		@Parameter({1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3})
//		double householdCompliance;
		
		@StringParameter({"yes", "no"})
		String outdoorModel;
	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, SMTesting.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


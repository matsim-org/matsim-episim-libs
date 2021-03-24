package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TestingConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
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
//		return 1500;
//	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder().setSnapshot(
				SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
		Config config = module.config();
//		config.global().setRandomSeed(params.seed);

		config.global().setRandomSeed(6137546356583794141L);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		episimConfig.setStartFromSnapshot("../episim-snapshot-270-2020-11-20.zip");
//		episimConfig.setSnapshotInterval(30);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
		
		//extrapolate restrictions
		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			if (params.extrapolateRestrictions.equals("67pct")) builder.restrict("2021-03-22", 0.67, act);
			if (params.extrapolateRestrictions.contains("yes")) {
				builder.restrict("2021-03-21", 0.8, act);
				if (params.extrapolateRestrictions.equals("yes")) {
					builder.restrict("2021-03-28", 0.84, act);
					builder.restrict("2021-04-04", 0.88, act);
					builder.restrict("2021-04-11", 0.92, act);
					builder.restrict("2021-04-18", 0.96, act);
					builder.restrict("2021-04-25", 1., act);
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
		
		if (params.vaccinationRate.equals("plus50pct")) {
			vaccinations.put(LocalDate.parse("2021-04-01"), (int) (1.5 * (0.071 - 0.057) * population / 7));
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
		
		vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);

		Map<LocalDate, Integer> infPerDayVariant = new HashMap<>();
		infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayVariant.put(LocalDate.parse("2020-12-10"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayVariant);
		
		//easter model. input days are set in productionScenario
		if (params.easterModel.equals("yes")) {
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				double fraction;
				switch (params.extrapolateRestrictions) {
				case "no":
					fraction = 0.76;
					break;
				case "yes":
					fraction = 0.88;
					break;
				case "yesUntil80":
					fraction = 0.8;
					break;
				case "67pct":
					fraction = 0.67;
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
		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());
		
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);
		
		testingConfigGroup.setStrategy(TestingConfigGroup.Strategy.ACTIVITIES);
				
		List<String> actsList = new ArrayList<String>();
		
		if (params.testingStrategy.contains("leisure")) {
			actsList.add("leisure");
		}
		if (params.testingStrategy.contains("work")) {
			actsList.add("work");
			actsList.add("business");
		}
		if (params.testingStrategy.contains("edu")) {
			actsList.add("educ_kiga");
			actsList.add("educ_primary");
			actsList.add("educ_secondary");
			actsList.add("educ_tertiary");
			actsList.add("educ_other");
		}

		testingConfigGroup.setActivities(actsList);
		
		testingConfigGroup.setFalseNegativeRate(0.3);
		
		testingConfigGroup.setFalsePositiveRate(0.03);
				
		testingConfigGroup.setTestingRatePerActivity((Map.of(
				"leisure", params.testingRateLeisure,
				"work", params.testingRateWork,
				"business", params.testingRateWork,
				"educ_kiga", params.testingRateEdu,
				"educ_primary", params.testingRateEdu,
				"educ_secondary", params.testingRateEdu,
				"educ_tertiary", params.testingRateEdu,
				"educ_other", params.testingRateEdu
				)));
		
//		double leisureRate1 = Integer.parseInt(params.testingRateLeisure.split("-")[0]) / 100.;
//		double workRate1 = Integer.parseInt(params.testingRateWork.split("-")[0]) / 100.;
//		double eduRate1 = Integer.parseInt(params.testingRateEdu.split("-")[0]) / 100.;
//		double leisureRate2 = Integer.parseInt(params.testingRateLeisure.split("-")[1]) / 100.;
//		double workRate2 = Integer.parseInt(params.testingRateWork.split("-")[1]) / 100.;
//		double eduRate2 = Integer.parseInt(params.testingRateEdu.split("-")[1]) / 100.;
//
//		
//		testingConfigGroup.setTestingRatePerActivityAndDate((Map.of(
//				"leisure", Map.of(
//						LocalDate.parse("2020-01-01"), 0.,
//						LocalDate.parse("2021-03-22"), leisureRate1,
//						LocalDate.parse("2021-04-05"), leisureRate2
//						),
//				"work", Map.of(
//						LocalDate.parse("2020-01-01"), 0.,
//						LocalDate.parse("2021-03-22"), workRate1,
//						LocalDate.parse("2021-04-05"), workRate2
//						),
//				"business", Map.of(
//						LocalDate.parse("2020-01-01"), 0.,
//						LocalDate.parse("2021-03-22"), workRate1,
//						LocalDate.parse("2021-04-05"), workRate2
//						),
//				"educ_kiga", Map.of(
//						LocalDate.parse("2020-01-01"), 0.,
//						LocalDate.parse("2021-03-22"), eduRate1,
//						LocalDate.parse("2021-04-05"), eduRate2
//						),
//				"educ_primary", Map.of(
//						LocalDate.parse("2020-01-01"), 0.,
//						LocalDate.parse("2021-03-22"), eduRate1,
//						LocalDate.parse("2021-04-05"), eduRate2
//						),
//				"educ_secondary", Map.of(
//						LocalDate.parse("2020-01-01"), 0.,
//						LocalDate.parse("2021-03-22"), eduRate1,
//						LocalDate.parse("2021-04-05"), eduRate2
//						),
//				"educ_tertiary", Map.of(
//						LocalDate.parse("2020-01-01"), 0.,
//						LocalDate.parse("2021-03-22"), eduRate1,
//						LocalDate.parse("2021-04-05"), eduRate2
//						),
//				"educ_other", Map.of(
//						LocalDate.parse("2020-01-01"), 0.,
//						LocalDate.parse("2021-03-22"), eduRate1,
//						LocalDate.parse("2021-04-05"), eduRate2
//						)
//				)));

		testingConfigGroup.setTestingCapacity_pers_per_day(Map.of(LocalDate.of(1970, 1, 1), 0, LocalDate.of(2021, 3, 22), Integer.MAX_VALUE));
				
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(2.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setVaccineEffectiveness(1.0);
		
//		if (params.outdoorModel.contains("no")) {
//			episimConfig.setLeisureOutdoorFraction(0.);
//		}
		
		return config;
	}

	public static final class Params {

//		@GenerateSeeds(1)
//		public long seed;

		@StringParameter({"current", "plus50pct"})
		String vaccinationRate;
		
		@StringParameter({"50%open", "closed", "open"})
		public String schools;
		
//		@StringParameter({"2020-12-15"})
//		String newVariantDate;
		
//		@StringParameter({"no", "yes", "yesUntil80"})
		@StringParameter({"no", "yes", "67pct"})
		String extrapolateRestrictions;
		
//		@StringParameter({"FIXED_DAYS", "leisure", "work", "edu", "leisure&edu", "leisure&work", "work&edu", "leisure&work&edu"})
		@StringParameter({"leisure&work&edu"})
		String testingStrategy;
		
//		@StringParameter({"0-0", "0-2", "0-10", "0-20", "2-2", "2-10", "2-20", "10-10", "10-20"})
//		String testingRateLeisure;
//		
////		@StringParameter({"0-0", "0-10", "0-20", "0-40", "10-10", "10-20", "10-40", "20-20", "20-40"})
//		@StringParameter({"0-0", "0-10", "0-20", "10-10", "10-20"})
//		String testingRateWork;
//		
//		@StringParameter({"0-0", "0-10", "0-20", "10-10", "10-20"})
//		String testingRateEdu;
		
		@Parameter({0.0, 0.02, 0.1, 0.2, 0.3})
		double testingRateLeisure;
		
//		@StringParameter({"0-0", "0-10", "0-20", "0-40", "10-10", "10-20", "10-40", "20-20", "20-40"})
		@Parameter({0.0, 0.2, 0.4, 0.6})
		double testingRateWork;
		
		@Parameter({0.0, 0.2, 0.4, 0.6})
		double testingRateEdu;
		
		@StringParameter({"no", "yes"})
		public String easterModel;
		
//		@StringParameter({"no"})
//		String outdoorModel;
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


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
import org.matsim.run.modules.SnzProductionScenario.ChristmasModel;

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
//		config.global().setRandomSeed(4711);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		episimConfig.setStartFromSnapshot("../episim-snapshot-270-2020-11-20.zip");
//		episimConfig.setSnapshotInterval(30);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		LocalDate restrictionDate = LocalDate.parse(params.restrictionDate);

		//extrapolate restrictions
		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			builder.clearAfter("2021-03-22", act);
			if (params.activityLevel.equals("67pct")) builder.restrict(restrictionDate, 0.67, act);
			if (params.activityLevel.equals("trend")) {
				for (int i = 1; i<100; i++) {
					double fraction = 0.78 + i * 0.01;
					if (fraction > 1.0) break;
					builder.restrict(restrictionDate.plusDays(i * 7), 0.78 + i * 0.01, act);
				}
			}

		}

		//schools
		if (params.schools.equals("50%open")) {}

		if (params.schools.equals("open")) {
			builder.clearAfter( restrictionDate.minusDays(2).toString(), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict(restrictionDate, 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			//Sommerferien
			builder.restrict("2021-06-24", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
			builder.restrict("2021-08-07", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		}

		if (params.schools.equals("closed")) {
			builder.clearAfter( restrictionDate.minusDays(2).toString(), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict(restrictionDate, .2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
		}


		if (!params.vaccinationRate.equals("current")) {
			VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
			Map<LocalDate, Integer> vaccinations = vaccinationConfig.getReVaccinationCapacity();
			vaccinations.put(restrictionDate, (int) (Double.parseDouble(params.vaccinationRate) * 4./3.));
			vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);
		}


		Map<LocalDate, Integer> infPerDayVariant = new HashMap<>();
		infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayVariant.put(LocalDate.parse("2020-12-05"), 1);
//		infPerDayVariant.put(LocalDate.parse("2020-11-25"), 1);

		episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayVariant);


		builder.restrict("2021-04-06", Restriction.ofClosingHours(21, 5), "leisure", "visit");
		Map<LocalDate, Double> curfewCompliance = new HashMap<LocalDate, Double>();
		curfewCompliance.put(LocalDate.parse("2021-04-06"), 0.5);

		if (params.leisure.equals("c_19-5_50pct")) {
			builder.restrict(restrictionDate, Restriction.ofClosingHours(19, 5), "leisure", "visit");
			curfewCompliance.put(restrictionDate, 0.5);
		}
		if (params.leisure.equals("c_19-5_100pct")) {
			builder.restrict(restrictionDate, Restriction.ofClosingHours(19, 5), "leisure", "visit");
			curfewCompliance.put(restrictionDate, 1.0);
		}
		if (params.leisure.equals("c_21-5_100pct")) {
			builder.restrict(restrictionDate, Restriction.ofClosingHours(21, 5), "leisure", "visit");
			curfewCompliance.put(restrictionDate, 1.0);
		}
		if (params.leisure.equals("c_0-24_100pct")) {
			builder.clearAfter(restrictionDate.toString(), "leisure", "visit");
			builder.restrict(restrictionDate, 0., "leisure", "visit");
		}
		if (params.leisure.equals("c_0-24_50pct")) {
			builder.clearAfter(restrictionDate.toString(), "leisure", "visit");
			builder.restrict(restrictionDate, 0.78 * 0.5, "leisure", "visit");
		}

		if (params.leisure.equals("noCurfew")) {
			builder.restrict(restrictionDate, Restriction.ofClosingHours(0, 0), "leisure", "visit");
			curfewCompliance.put(restrictionDate, 0.0);
		}



		if (params.work.contains("ffp")) builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.9), "work", "business");


		if (params.liftRestrictions.equals("after3weeks")) {
			LocalDate liftDate = restrictionDate.plusWeeks(3);
			//open schools
			builder.clearAfter( liftDate.toString(), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict(liftDate, 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			//Sommerferien
			builder.restrict("2021-06-24", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
			builder.restrict("2021-08-07", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");

			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				builder.clearAfter(liftDate.toString(), act);
				builder.restrict(liftDate, 0.8, act);
			}

			builder.restrict(liftDate, Restriction.ofMask(FaceMask.N95, 0.0), "work", "business");
			builder.restrict(liftDate, Restriction.ofClosingHours(0, 0), "leisure", "visit");
			curfewCompliance.put(liftDate, 0.0);
		}

		episimConfig.setCurfewCompliance(curfewCompliance);

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

		double leisureRate;
		double workRate;
		double eduRate;
		double leisureW10 = 0.002 * 3_570_000 / 1_320_000;
		double leisureW11 = 0.004 * 3_570_000 / 1_320_000;
		double leisureW12 = 0.005 * 3_570_000 / 1_320_000;

		if (!params.testingRateEduWorkLeisure.contains("current")) {
			leisureRate = Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[2]) / 100.;
			workRate = Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[1]) / 100.;
			eduRate = Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[0]) / 100.;
		}

		else {
			leisureRate = leisureW12;
			workRate = 0.;
			eduRate = 0.;
		}

		testingConfigGroup.setTestingRatePerActivityAndDate((Map.of(
				"leisure", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						LocalDate.parse("2021-03-11"), leisureW10,
						LocalDate.parse("2021-03-18"), leisureW11,
						LocalDate.parse("2021-03-25"), leisureW12,
						restrictionDate, leisureRate
						),
				"work", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						restrictionDate, workRate
						),
				"business", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						restrictionDate, workRate
						),
				"educ_kiga", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						restrictionDate, eduRate
						),
				"educ_primary", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						restrictionDate, eduRate
						),
				"educ_secondary", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						restrictionDate, eduRate
						),
				"educ_tertiary", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						restrictionDate, eduRate
						),
				"educ_other", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						restrictionDate, eduRate
						)
				)));

		testingConfigGroup.setTestingCapacity_pers_per_day(Map.of(
				LocalDate.of(1970, 1, 1), 0,
				LocalDate.of(2021, 3, 11), Integer.MAX_VALUE));


		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(2.0);
//		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(1.8);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setVaccineEffectiveness(1.0);

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

		@StringParameter({"current", "40000"})
		String vaccinationRate;

		@StringParameter({"50%open", "closed"})
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
			"current",
			"0-0-0", "20-0-0", "20-20-0", "20-20-10",
//			"20-20-20",
//			"40-0-0", "40-20-0","40-20-2", "40-20-10", "40-20-20", "40-40-0", "40-40-2", "40-40-10", "40-40-20"
			})
		String testingRateEduWorkLeisure;

		@StringParameter({"yes"})
		public String easterModel;

		@StringParameter({"current", "c_19-5_50pct", "c_19-5_100pct", "c_21-5_100pct", "c_0-24_100pct", "c_0-24_50pct", "noCurfew"})
		public String leisure;

		@StringParameter({"no", "ffp"})
		public String work;

//		@StringParameter({"no", "1.0-1.0", "1.0-0.5", "1.0-0.0", "2.0-1.0", "2.0-0.5", "2.0-0.0"})
//		public String B1351;

//		@Parameter({1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3})
//		double householdCompliance;

		@StringParameter({"yes", "no"})
		String outdoorModel;

		@StringParameter({"no", "after3weeks"})
		String liftRestrictions;

		@StringParameter({"2021-04-12", "2021-04-19"})
		String restrictionDate;
	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, SMTesting.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


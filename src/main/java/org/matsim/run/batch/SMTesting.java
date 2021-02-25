package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TestingConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;

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
	
	@Override
	public int getOffset() {
		return 3000;
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder().setSnapshot(
				SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
		Config config = module.config();
//		config.global().setRandomSeed(params.seed);

		config.global().setRandomSeed(7564655870752979346L);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.restore);
		episimConfig.setStartFromSnapshot("../episim-snapshot-330-2021-01-19.zip");
//		episimConfig.setSnapshotInterval(30);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
		
		//extrapolate restrictions after 17.01.
		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			if (params.extrapolateRestrictions.equals("no100%")) {
				builder.restrict("2021-03-07", 1., act);
			}
			if (params.extrapolateRestrictions.contains("yes")) {
				builder.restrict("2021-02-28", 0.72, act);
				builder.restrict("2021-03-07", 0.76, act);
				builder.restrict("2021-03-14", 0.8, act);
				if (params.extrapolateRestrictions.equals("yes")) {
					builder.restrict("2021-03-21", 0.84, act);
					builder.restrict("2021-03-28", 0.88, act);
					builder.restrict("2021-04-04", 0.92, act);
					builder.restrict("2021-04-11", 0.96, act);
					builder.restrict("2021-04-18", 1., act);
				}
			}
		}
			
		String restrictionDate = "2021-02-22";
		
		//schools
		if (params.schools.equals("50%&masks")) {
			builder.restrict(LocalDate.parse(restrictionDate), Restriction.ofMask(Map.of(FaceMask.N95, 0.9)), "educ_primary", "educ_secondary");
			builder.restrict(restrictionDate, .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict("2021-04-11", .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		}
		if (params.schools.equals("open&masks")) {
			builder.restrict(LocalDate.parse(restrictionDate), Restriction.ofMask(Map.of(FaceMask.N95, 0.9)), "educ_primary", "educ_secondary");
		}
		if (params.schools.equals("50%open")) {
			builder.restrict(restrictionDate, .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict("2021-04-11", .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		}
		if (params.schools.equals("closed")) builder.clearAfter( "2021-02-14", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
		
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setEffectiveness(0.9);
		vaccinationConfig.setDaysBeforeFullEffect(28);
		// Based on https://experience.arcgis.com/experience/db557289b13c42e4ac33e46314457adc
		// 4/3 because model is bigger than just Berlin
		int base = (int) (3000 * 4./3.);
		vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
				episimConfig.getStartDate(), 0,
				LocalDate.parse("2020-12-27"), (int) (2000 * 4./3.)
				,LocalDate.parse("2021-01-25"), base
				,LocalDate.parse("2021-03-01"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 1./7.)
				,LocalDate.parse("2021-03-03"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 2./7.)
				,LocalDate.parse("2021-03-05"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 3./7.)
				,LocalDate.parse("2021-03-07"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 4./7.)
				,LocalDate.parse("2021-03-09"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 5./7.)
				,LocalDate.parse("2021-03-10"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 6./7.)
				,LocalDate.parse("2021-03-12"), base + (int) ((4./3. * params.dailyInitialVaccinations - base))
				));

		if (!params.newVariantDate.equals("never")) {
			Map<LocalDate, Integer> infPerDayVariant = new HashMap<>();
			infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayVariant.put(LocalDate.parse(params.newVariantDate), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayVariant);
		}
		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());
		
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);
		
		testingConfigGroup.setStrategy(TestingConfigGroup.Strategy.ACTIVITIES);
		if (params.testingStrategy.equals("FIXED_DAYS")) testingConfigGroup.setStrategy(TestingConfigGroup.Strategy.FIXED_DAYS);
		
		
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
		
		testingConfigGroup.setFalseNegativeRate(params.testingFalseNegative);
		
		testingConfigGroup.setFalsePositiveRate(params.testingFalsePositive);
		
		testingConfigGroup.setTestingRate(params.testingRate);

		testingConfigGroup.setTestingCapacity_pers_per_day(Map.of(LocalDate.of(1970, 1, 1), 0, LocalDate.of(2021, 3, 1), Integer.MAX_VALUE));
				
//		VirusStrain.B117.infectiousness = params.newVariantInfectiousness;
		
		if (params.outdoorModel.contains("no")) {
			episimConfig.setLeisureOutdoorFraction(0.);
		}
		
		return config;
	}

	public static final class Params {

//		@GenerateSeeds(2)
//		public long seed;

		@IntParameter({10000})
		int dailyInitialVaccinations;
		
		@StringParameter({"closed", "50%open", "open"})
		public String schools;
		
		@StringParameter({"2020-12-15"})
		String newVariantDate;
		
		@StringParameter({"no", "yes", "yesUntil80"})
		String extrapolateRestrictions;
		
		@StringParameter({"FIXED_DAYS", "leisure", "work", "edu", "leisure&edu", "leisure&work", "work&edu", "leisure&work&edu"})
		String testingStrategy;
		
		@Parameter({0.1, 0.3})
		double testingFalseNegative;
		
		@Parameter({0.03})
		double testingFalsePositive;
		
		@Parameter({0.0, 0.2, 0.4, 0.6, 0.8, 1.0})
		double testingRate;
		
		@Parameter({2.0})
		double newVariantInfectiousness;
		
		@StringParameter({"no"})
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


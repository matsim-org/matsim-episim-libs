package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.AdaptivePolicy;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;


/**
 * Adaptive policy runs
 */
public class SMAdaptivePolicy implements BatchRun<SMAdaptivePolicy.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder().setSnapshot(SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "adaptivePolicy");
	}
	
	@Override
	public int getOffset() {
		return 1500;
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
		episimConfig.setStartFromSnapshot("../" + params.scenario + "-episim-snapshot-364-2021-02-22.zip");
//		episimConfig.setSnapshotInterval(30);			

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
				,LocalDate.parse("2021-03-07"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 1./7.)
				,LocalDate.parse("2021-03-08"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 2./7.)
				,LocalDate.parse("2021-03-09"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 3./7.)
				,LocalDate.parse("2021-03-10"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 4./7.)
				,LocalDate.parse("2021-03-11"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 5./7.)
				,LocalDate.parse("2021-03-12"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 6./7.)
				,LocalDate.parse("2021-03-13"), base + (int) ((4./3. * params.dailyInitialVaccinations - base))
				));

			Map<LocalDate, Integer> infPerDayVariant = new HashMap<>();
			infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayVariant.put(LocalDate.parse("2020-12-15"), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayVariant);
			if (params.B1351.contains("yes")) {
				Map<LocalDate, Integer> infPerDayB1351 = new HashMap<>();
				infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
				infPerDayVariant.put(LocalDate.parse("2021-02-23"), 1);
				episimConfig.setInfections_pers_per_day(VirusStrain.B1351, infPerDayB1351);
			}

		
		LocalDate date = LocalDate.parse("2021-02-23");
		
		com.typesafe.config.Config policy = AdaptivePolicy.config()
				.incidenceTrigger(params.workTrigger, params.workTrigger, "work", "business")
				.incidenceTrigger(params.leisureTrigger, params.leisureTrigger, "leisure", "visit")
				.incidenceTrigger(params.eduTrigger, params.eduTrigger, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
				.incidenceTrigger(params.shopErrandsTrigger, params.shopErrandsTrigger, "shop_other", "shop_daily", "errands")
				.initialPolicy(FixedPolicy.config()
						.restrict("2020-08-01", Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.9 * 0.9, FaceMask.SURGICAL, 0.9 * 0.1)), "pt", "shop_daily", "shop_other", "errands")
						.restrict("2020-08-08", Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
						.restrict("2020-10-25", Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.8 * 0.9, FaceMask.SURGICAL, 0.8 * 0.1)), "educ_higher", "educ_tertiary", "educ_other")
						.restrict(LocalDate.MIN, Restriction.of(params.openFraction), "work")
						.restrict(LocalDate.MIN, Restriction.of(params.openFraction), "shop_daily")
						.restrict(LocalDate.MIN, Restriction.of(params.openFraction), "shop_other")
						.restrict(LocalDate.MIN, Restriction.of(params.openFraction), "errands")
						.restrict(LocalDate.MIN, Restriction.of(params.openFraction), "business")
						.restrict(LocalDate.MIN, Restriction.of(params.openFraction), "visit")
						.restrict(LocalDate.MIN, Restriction.of(params.openFraction), "leisure")
						.restrict(LocalDate.MIN, Restriction.of(1.), "educ_kiga")
						.restrict(LocalDate.MIN, Restriction.of(1.), "educ_primary")
						.restrict(LocalDate.MIN, Restriction.of(1.), "educ_secondary")
						.restrict(LocalDate.MIN, Restriction.of(1.), "educ_tertiary")
						.restrict(LocalDate.MIN, Restriction.of(1.), "educ_other")
						)
				.restrictedPolicy(FixedPolicy.config()
						.restrict(date, Restriction.of(params.restrictedFraction), "work")
						.restrict(date, Restriction.of(params.restrictedFraction), "shop_daily")
						.restrict(date, Restriction.of(params.restrictedFraction), "shop_other")
						.restrict(date, Restriction.of(params.restrictedFraction), "errands")
						.restrict(date, Restriction.of(params.restrictedFraction), "business")
						.restrict(date, Restriction.of(params.restrictedFraction), "visit")
						.restrict(date, Restriction.of(params.restrictedFraction), "leisure")
						.restrict(date, Restriction.of(.2), "educ_kiga")
						.restrict(date, Restriction.of(.2), "educ_primary")
						.restrict(date, Restriction.of(.2), "educ_secondary")
						.restrict(date, Restriction.of(.2), "educ_tertiary")
						.restrict(date, Restriction.of(.2), "educ_other")
				)
				.openPolicy(FixedPolicy.config()
						.restrict(date, Restriction.of(params.openFraction), "work")
						.restrict(date, Restriction.of(params.openFraction), "shop_daily")
						.restrict(date, Restriction.of(params.openFraction), "shop_other")
						.restrict(date, Restriction.of(params.openFraction), "errands")
						.restrict(date, Restriction.of(params.openFraction), "business")
						.restrict(date, Restriction.of(params.openFraction), "visit")
						.restrict(date, Restriction.of(params.openFraction), "leisure")
						.restrict(date, Restriction.of(1.), "educ_kiga")
						.restrict(date, Restriction.of(1.), "educ_primary")
						.restrict(date, Restriction.of(1.), "educ_secondary")
						.restrict(date, Restriction.of(1.), "educ_tertiary")
						.restrict(date, Restriction.of(1.), "educ_other")
//						.restrict(LocalDate.parse("2021-03-29"), Restriction.of(.2), "educ_primary")
//						.restrict(LocalDate.parse("2021-03-29"), Restriction.of(.2), "educ_secondary")
//						.restrict(LocalDate.parse("2021-03-29"), Restriction.of(.2), "educ_tertiary")
//						.restrict(LocalDate.parse("2021-03-29"), Restriction.of(.2), "educ_other")
//						.restrict(LocalDate.parse("2021-04-11"), Restriction.of(1.), "educ_primary")
//						.restrict(LocalDate.parse("2021-04-11"), Restriction.of(1.), "educ_secondary")
//						.restrict(LocalDate.parse("2021-04-11"), Restriction.of(1.), "educ_tertiary")
//						.restrict(LocalDate.parse("2021-04-11"), Restriction.of(1.), "educ_other")
//						.restrict(LocalDate.parse("2021-06-24"), Restriction.of(.2), "educ_primary")
//						.restrict(LocalDate.parse("2021-06-24"), Restriction.of(.2), "educ_secondary")
//						.restrict(LocalDate.parse("2021-06-24"), Restriction.of(.2), "educ_tertiary")
//						.restrict(LocalDate.parse("2021-06-24"), Restriction.of(.2), "educ_other")
//						.restrict(LocalDate.parse("2021-08-07"), Restriction.of(1.), "educ_primary")
//						.restrict(LocalDate.parse("2021-08-07"), Restriction.of(1.), "educ_secondary")
//						.restrict(LocalDate.parse("2021-08-07"), Restriction.of(1.), "educ_tertiary")
//						.restrict(LocalDate.parse("2021-08-07"), Restriction.of(1.), "educ_other")
						)
				.build();		
		
		episimConfig.setPolicy(AdaptivePolicy.class, policy);
				
		return config;
	}

	public static final class Params {

//		@GenerateSeeds(2)
//		public long seed;

		@IntParameter({3000, 10000})
		int dailyInitialVaccinations;
		
//		@StringParameter({"B1351_1.35_0.0"})
		@StringParameter({"base-b117-2.0"})
		String scenario;
		
		@StringParameter({"yes-2.0-0.0"})
		String B1351;
		
		@Parameter({0.8, 0.65})
		double restrictedFraction;
		
		@Parameter({0.9})
		double openFraction;
		
		@Parameter({Integer.MAX_VALUE, 50., 35., 20.})
		double leisureTrigger;
		
		@Parameter({Integer.MAX_VALUE, 50., 35., 20.})
		double workTrigger;
		
		@Parameter({Integer.MAX_VALUE, 50., 35., 20.})
		double eduTrigger;
		
		@Parameter({Integer.MAX_VALUE, 50., 35., 20.})
		double shopErrandsTrigger;
	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, SMAdaptivePolicy.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


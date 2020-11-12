package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.AgeDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinProductionScenario.*;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Map;


/**
 * Interventions for symmetric Berlin week model
 */
public class BerlinSecondLockdown implements BatchRun<BerlinSecondLockdown.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario(25, DiseaseImport.yes, Restrictions.yes, Masks.yes, Tracing.yes, Snapshot.episim_snapshot_150_2020_07_14, AgeDependentInfectionModelWithSeasonality.class);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "secondLockdown");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario(25, DiseaseImport.yes, Restrictions.yes, Masks.yes, Tracing.yes, Snapshot.episim_snapshot_150_2020_07_14, AgeDependentInfectionModelWithSeasonality.class);

		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		if (params.lockdown.equals("leisure63")) builder.restrict("2020-11-02", 0.63, "leisure");


		if (params.lockdown.equals("outOfHomeExceptEdu59")) {
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				builder.restrict("2020-11-02", 0.59, act);
			}
		}

		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 300,
				LocalDate.of(2020, 6, 15), 2000,
				LocalDate.parse("2020-11-23"), params.tracingCapacity
		));

//		tracingConfig.setTracingDelay_days(Map.of(
//				LocalDate.of(2020, 1, 1), 4,
//				LocalDate.parse("2020-11-23"), params.tracingDelay
//		));
//
//		tracingConfig.setTracingProbability(Map.of(
//				LocalDate.of(2020, 1, 1), 0.6,
//				LocalDate.parse("2020-11-23"), params.tracingProbability
//		));


		if (params.schools.contains("school50")) {
			builder
					.restrict("2020-11-23", 0.5, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					//Weihnachtsferien
					.restrict("2020-12-21", 0.2, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					.restrict("2021-01-03", 0.5, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					//Winterferien
					.restrict("2021-02-01", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					.restrict("2021-02-07", 0.5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					//Osterferien
					.restrict("2021-03-29", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					.restrict("2021-04-11", 0.5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			;
		}
		if (params.schools.contains("N95masks")) {
			builder.restrict("2020-11-23", Restriction.ofMask(FaceMask.N95, 0.9), "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_higher");
		}

		if (params.schools.contains("earlyHolidays")) {
			builder.restrict("2020-11-23", 0., "educ_higher");
			builder.restrict("2020-11-23", 0.2, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
			builder.restrict("2021-01-03", 0.2, "educ_higher");
		}

		if (params.furhterMeasures.equals("outOfHomeExceptEdu59")) {
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				builder.restrict("2020-11-23", 0.59, act);
			}
		}

		if (params.furhterMeasures.equals("leisure10")) {
			builder.restrict("2020-11-23", 0.1, "leisure");
		}

		if (params.furhterMeasures.equals("outOfHomeExceptEdu59&leisure10")) {
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				if (act.contains("leisure")) continue;
				builder.restrict("2020-11-23", 0.59, act);
			}
			builder.restrict("2020-11-23", 0.1, "leisure");
		}

		episimConfig.setPolicy(FixedPolicy.class, builder.build());


		return config;
	}

	public static final class Params {

		@GenerateSeeds(5)
		public long seed;

		//2.11.
		@StringParameter({"outOfHomeExceptEdu59", "outOfHomeExceptEdu84", "leisure63"})
		public String lockdown;

		//23.11.
		@IntParameter({2000, Integer.MAX_VALUE})
		int tracingCapacity;

//		@Parameter({0.6, 0.9})
//		double tracingProbability;
//
//		@IntParameter({2, 4})
//		int tracingDelay;

		@StringParameter({"open", "school50", "N95masks", "school50&N95masks", "earlyHolidays"})
		public String schools;

		@StringParameter({"no", "outOfHomeExceptEdu59", "leisure10", "outOfHomeExceptEdu59&leisure10"})
		public String furhterMeasures;

	}


}

package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinProductionScenario.DiseaseImport;
import org.matsim.run.modules.SnzBerlinProductionScenario.Masks;
import org.matsim.run.modules.SnzBerlinProductionScenario.Restrictions;
import org.matsim.run.modules.SnzBerlinProductionScenario.Snapshot;
import org.matsim.run.modules.SnzBerlinProductionScenario.Tracing;

import javax.annotation.Nullable;

import java.time.LocalDate;
import java.util.Map;


/**
 * Interventions for symmetric Berlin week model with different contact models
 */
public class BerlinInterventionsOct implements BatchRun<BerlinInterventionsOct.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {

		return new SnzBerlinProductionScenario(25, DiseaseImport.yes, Restrictions.yes, Masks.yes, Tracing.yes, Snapshot.episim_snapshot_240_2020_10_12, AgeDependentInfectionModelWithSeasonality.class);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "interventionsOctober");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario(25, DiseaseImport.yes, Restrictions.yes, Masks.yes, Tracing.yes, Snapshot.episim_snapshot_240_2020_10_12, AgeDependentInfectionModelWithSeasonality.class);
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		
		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
		if (params.lockdown.equals("outOfHomeExceptEdu59")) {
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				builder.restrict("2020-11-02", 0.59, act);
			}
		}

		LocalDate interventionDate = LocalDate.parse("2020-11-23");
		builder.clearAfter(interventionDate.toString());

		{
			if (params.restriction.equals("current"));
			else if (params.restriction.equals("tracing-inf-90-2d")) {
				tracingConfig.setTracingCapacity_pers_per_day(Map.of(
						LocalDate.of(2020, 4, 1), 300,
						LocalDate.of(2020, 6, 15), 2000,
						interventionDate, Integer.MAX_VALUE
				));
				tracingConfig.setTracingProbability(Map.of(
						LocalDate.of(1970, 1, 1), 0.6,
						interventionDate, 0.9
				));
				tracingConfig.setTracingDelay_days(Map.of(
						LocalDate.of(1970, 1, 1), 4,
						interventionDate, 2
				));
			}
			else if (params.restriction.equals("tracing-cap-90-2d")) {
				tracingConfig.setTracingProbability(Map.of(
						LocalDate.of(1970, 1, 1), 0.6,
						interventionDate, 0.9
				));
				tracingConfig.setTracingDelay_days(Map.of(
						LocalDate.of(1970, 1, 1), 4,
						interventionDate, 2
				));
			}
			else if (params.restriction.equals("tracing-inf-90-4d")) {
				tracingConfig.setTracingCapacity_pers_per_day(Map.of(
						LocalDate.of(2020, 4, 1), 300,
						LocalDate.of(2020, 6, 15), 2000,
						interventionDate, Integer.MAX_VALUE
				));
				tracingConfig.setTracingProbability(Map.of(
						LocalDate.of(1970, 1, 1), 0.6,
						interventionDate, 0.9
				));


			}
			else if (params.restriction.equals("tracing-cap-90-4d")) {
				tracingConfig.setTracingProbability(Map.of(
						LocalDate.of(1970, 1, 1), 0.6,
						interventionDate, 0.9
				));

			}
			else if (params.restriction.equals("tracing-inf-60-2d")) {
				tracingConfig.setTracingCapacity_pers_per_day(Map.of(
						LocalDate.of(2020, 4, 1), 300,
						LocalDate.of(2020, 6, 15), 2000,
						interventionDate, Integer.MAX_VALUE
				));
				tracingConfig.setTracingDelay_days(Map.of(
						LocalDate.of(1970, 1, 1), 4,
						interventionDate, 2
				));

			}
			else if (params.restriction.equals("tracing-cap-60-2d")) {
				tracingConfig.setTracingDelay_days(Map.of(
						LocalDate.of(1970, 1, 1), 4,
						interventionDate, 2
				));
			}
			else if (params.restriction.equals("tracing-inf-60-4d")) {
				tracingConfig.setTracingCapacity_pers_per_day(Map.of(
						LocalDate.of(2020, 4, 1), 300,
						LocalDate.of(2020, 6, 15), 2000,
						interventionDate, Integer.MAX_VALUE
				));
			}
			
			else if (params.restriction.equals("schoolClothMasks")) builder.restrict(interventionDate, Restriction.ofMask(FaceMask.CLOTH, 0.9), "educ_primary", "educ_secondary", "educ_tertiary");
			else if (params.restriction.equals("school50")) builder.restrict(interventionDate, 0.5, "educ_primary", "educ_secondary", "educ_tertiary");
			else if (params.restriction.equals("school0")) builder.restrict(interventionDate, 0., "educ_primary", "educ_secondary", "educ_tertiary");
			else if (params.restriction.equals("school50&ClothMasks")) {
				builder.restrict(interventionDate, Restriction.ofMask(FaceMask.CLOTH, 0.9), "educ_primary", "educ_secondary", "educ_tertiary");
				builder.restrict(interventionDate, 0.5, "educ_primary", "educ_secondary", "educ_tertiary");
			}
			
			else if (params.restriction.equals("universities0")) builder.restrict(interventionDate, 0., "educ_higher");
			
			else if (params.restriction.equals("kiga50")) builder.restrict(interventionDate, 0.5, "educ_kiga");
			else if (params.restriction.equals("kiga0"))  builder.restrict(interventionDate, 0., "educ_kiga");

			else if (params.restriction.equals("leisure50")) builder.restrict(interventionDate, 0.5, "leisure");
			else if (params.restriction.equals("leisure0")) builder.restrict(interventionDate, 0., "leisure");
			else if (params.restriction.equals("leisureCurfew19-6")) builder.restrict(interventionDate, Restriction.ofClosingHours(19, 6), "leisure");
			else if (params.restriction.equals("leisureCurfew21-6")) builder.restrict(interventionDate, Restriction.ofClosingHours(21, 6), "leisure");
			else if (params.restriction.equals("leisureCurfew23-6")) builder.restrict(interventionDate, Restriction.ofClosingHours(23, 6), "leisure");
			else if (params.restriction.equals("leisure50&Curfew19-6")) {
				builder.restrict(interventionDate, Restriction.ofClosingHours(19, 6), "leisure");
				builder.restrict(interventionDate, 0.5, "leisure");
			}
			else if (params.restriction.equals("leisure50&Curfew21-6")) {
				builder.restrict(interventionDate, Restriction.ofClosingHours(21, 6), "leisure");
				builder.restrict(interventionDate, 0.5, "leisure");
			}
			else if (params.restriction.equals("leisure50&Curfew23-6")) {
				builder.restrict(interventionDate, Restriction.ofClosingHours(23, 6), "leisure");
				builder.restrict(interventionDate, 0.5, "leisure");
			}
			
			else if (params.restriction.equals("shop50")) builder.restrict(interventionDate, 0.5, "shop_daily", "shop_other");
			else if (params.restriction.equals("shop0")) builder.restrict(interventionDate, 0., "shop_daily", "shop_other");
			
			else if (params.restriction.equals("errands50")) builder.restrict(interventionDate, 0.5, "errands");
			else if (params.restriction.equals("errands0")) builder.restrict(interventionDate, 0., "errands");
			
			else if (params.restriction.equals("visit50")) builder.restrict(interventionDate, 0.5, "visit");
			else if (params.restriction.equals("visit0")) builder.restrict(interventionDate, 0., "visit");
			
			else if (params.restriction.equals("educ_other50")) builder.restrict(interventionDate, 0.5, "educ_other");
			else if (params.restriction.equals("educ_other0")) builder.restrict(interventionDate, 0., "educ_other");
			
			else if (params.restriction.equals("work75")) builder.restrict(interventionDate, 0.75, "work", "business");
			else if (params.restriction.equals("work50")) builder.restrict(interventionDate, 0.5, "work", "business");
			else if (params.restriction.equals("work25")) builder.restrict(interventionDate, 0.25, "work", "business");
			else if (params.restriction.equals("work0")) builder.restrict(interventionDate, 0., "work", "business");
			else if (params.restriction.equals("workN95Masks")) builder.restrict(interventionDate, Restriction.ofMask(FaceMask.N95, 0.9), "work", "business");

			else throw new RuntimeException("Measure not implemented: " + params.restriction);
		}

		episimConfig.setPolicy(FixedPolicy.class, builder.build());
	
		return config;
	}

	public static final class Params {

		@GenerateSeeds(10)
		public long seed;

		@StringParameter({"current", 
			"tracing-inf-60-4d", "tracing-cap-60-2d", "tracing-inf-60-2d", "tracing-cap-90-4d", "tracing-inf-90-4d", "tracing-cap-90-2d", "tracing-inf-90-2d",
			"schoolClothMasks", "school50", "school0", "school50&ClothMasks", 
			"universities0",
			"kiga50", "kiga0",
			"leisure50", "leisure0", "leisureCurfew19-6", "leisureCurfew21-6", "leisureCurfew23-6", "leisure50&Curfew19-6", "leisure50&Curfew21-6", "leisure50&Curfew23-6",
			"shop50", "shop0", 
			"errands50", "errands0",
			"visit50", "visit0",
			"educ_other50", "educ_other0",
			"work75", "work50", "work25", "work0", "workN95Masks"})
		public String restriction;
		
		@StringParameter({"outOfHomeExceptEdu59", "outOfHomeExceptEdu84"})
		public String lockdown;

	}


}

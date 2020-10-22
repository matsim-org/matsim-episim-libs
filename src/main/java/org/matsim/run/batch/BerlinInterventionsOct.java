package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimConfigGroup.InfectionParams;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.BatchRun.Parameter;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import javax.annotation.Nullable;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import static org.matsim.run.batch.ContactModels.*;


/**
 * Interventions for symmetric Berlin week model with different contact models
 */
public class BerlinInterventionsOct implements BatchRun<BerlinInterventionsOct.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		if (params == null)
			return new SnzBerlinWeekScenario2020();

		return new SnzBerlinWeekScenario2020(25, true, true, ContactModels.MODELS.get(params.contactModel));
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "interventionsOctober");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020 module = new SnzBerlinWeekScenario2020(25, true, true, MODELS.get(params.contactModel));
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30,
				LocalDate.of(2020, 6, 15), params.tracingCapacity
		));
		
		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
		//switch off holidays
		builder.restrict("2020-10-12", 1., "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		LocalDate interventionDate = LocalDate.parse("2020-10-26");
		builder.clearAfter(interventionDate.toString());

		{
			if (params.restriction.equals("current"));
			else if (params.restriction.equals("tracing-inf-75-1d")) {
				tracingConfig.setTracingCapacity_pers_per_day(Map.of(
						LocalDate.of(2020, 4, 1), 30,
						LocalDate.of(2020, 6, 15), params.tracingCapacity,
						interventionDate, Integer.MAX_VALUE
				));
				tracingConfig.setTracingProbability(Map.of(
						LocalDate.of(1970, 1, 1), 0.5,
						interventionDate, 0.75
				));
				tracingConfig.setTracingDelay_days(Map.of(
						LocalDate.of(1970, 1, 1), 2,
						interventionDate, 1
				));
			}
			else if (params.restriction.equals("tracing-cap-75-1d")) {
				tracingConfig.setTracingProbability(Map.of(
						LocalDate.of(1970, 1, 1), 0.5,
						interventionDate, 0.75
				));
				tracingConfig.setTracingDelay_days(Map.of(
						LocalDate.of(1970, 1, 1), 2,
						interventionDate, 1
				));
			}
			else if (params.restriction.equals("tracing-inf-50-5d")) {
				tracingConfig.setTracingCapacity_pers_per_day(Map.of(
						LocalDate.of(2020, 4, 1), 30,
						LocalDate.of(2020, 6, 15), params.tracingCapacity,
						interventionDate, Integer.MAX_VALUE
				));
				tracingConfig.setTracingDelay_days(Map.of(
						LocalDate.of(1970, 1, 1), 2,
						interventionDate, 5
				));

			}
			else if (params.restriction.equals("tracing-cap-50-5d")) {
				tracingConfig.setTracingDelay_days(Map.of(
						LocalDate.of(1970, 1, 1), 2,
						interventionDate, 5
				));

			}
			else if (params.restriction.equals("tracing-inf-0-2d")) {
				tracingConfig.setTracingCapacity_pers_per_day(Map.of(
						LocalDate.of(2020, 4, 1), 30,
						LocalDate.of(2020, 6, 15), params.tracingCapacity,
						interventionDate, Integer.MAX_VALUE
				));
				tracingConfig.setTracingProbability(Map.of(
						LocalDate.of(1970, 1, 1), 0.5,
						interventionDate, 0.
				));
			}
			else if (params.restriction.equals("tracing-cap-0-2d")) {
				tracingConfig.setTracingProbability(Map.of(
						LocalDate.of(1970, 1, 1), 0.5,
						interventionDate, 0.
				));
			}
			else if (params.restriction.equals("tracing-inf-50-2d")) {
				tracingConfig.setTracingCapacity_pers_per_day(Map.of(
						LocalDate.of(2020, 4, 1), 30,
						LocalDate.of(2020, 6, 15), params.tracingCapacity,
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
			"tracing-inf-75-1d", "tracing-cap-75-1d", "tracing-inf-50-5d", "tracing-cap-50-5d", "tracing-inf-0-2d", "tracing-cap-0-2d", "tracing-inf-50-2d",
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
		
		@StringParameter({SYMMETRIC_OLD})
		public String contactModel;
		
		@IntParameter({100})
		private int tracingCapacity;

	}


}

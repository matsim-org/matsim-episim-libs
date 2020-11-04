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
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinProductionScenario.DiseaseImport;
import org.matsim.run.modules.SnzBerlinProductionScenario.Masks;
import org.matsim.run.modules.SnzBerlinProductionScenario.Restrictions;
import org.matsim.run.modules.SnzBerlinProductionScenario.Snapshot;
import org.matsim.run.modules.SnzBerlinProductionScenario.Tracing;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;




/**
 * Interventions for symmetric Berlin week model
 */
public class Interventions implements BatchRun<Interventions.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		if (params == null)
			return new SnzBerlinWeekScenario2020();

		return new SnzBerlinProductionScenario(25, DiseaseImport.no, Restrictions.no, Masks.no, Tracing.no, Snapshot.no, AgeDependentInfectionModelWithSeasonality.class);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "basePaperInterventions");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = 
				new SnzBerlinProductionScenario(25, DiseaseImport.no, Restrictions.no, Masks.no, Tracing.no, Snapshot.no, AgeDependentInfectionModelWithSeasonality.class);
		
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		if (params.season.equals("summer")) {
			Map<LocalDate, Double> leisureOutdoorFraction = new TreeMap<>(Map.of(
					LocalDate.parse("2020-01-01"), 0.8, 
					LocalDate.parse("2030-01-01"), 0.8)
					);
			episimConfig.setLeisureOutdoorFraction(leisureOutdoorFraction);	
		}
		else if (params.season.equals("winter")) {
			Map<LocalDate, Double> leisureOutdoorFraction = new TreeMap<>(Map.of(
					LocalDate.parse("2020-01-01"), 0.1, 
					LocalDate.parse("2030-01-01"), 0.1)
					);
			episimConfig.setLeisureOutdoorFraction(leisureOutdoorFraction);
			
		}
		else throw new IllegalArgumentException("Season not implemented!");

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		
		ConfigBuilder builder = FixedPolicy.config();
		int interventionDay = 20;
		switch (params.intervention) {
		case "none":
			break;
		case "0.9CLOTH@PT&SHOP":
			builder.restrict(interventionDay, Restriction.ofMask(FaceMask.CLOTH, 0.9), "pt", "shop_daily", "shop_other");
			break;
		case "0.9FFP@WORK":
			builder.restrict(interventionDay, Restriction.ofMask(FaceMask.N95, 0.9), "work", "business");
			break;
		case "0.9CLOTH@SCHOOL":
			builder.restrict(interventionDay, Restriction.ofMask(FaceMask.CLOTH, 0.9), "educ_primary", "educ_secondary", "educ_tertiary");
			break;
		case "workBusiness50":
			builder.restrict(interventionDay, 0.5, "work", "business");
			break;
		case "workBusiness0":
			builder.restrict(interventionDay, 0., "work", "business");
			break;
		case "leisure50":
			builder.restrict(interventionDay, 0.5, "leisure", "visit");
			break;
		case "leisure0":
			builder.restrict(interventionDay, 0.0, "leisure", "visit");
			break;
		case "errands50":
			builder.restrict(interventionDay, 0.5, "errands");
			break;
		case "errands0":
			builder.restrict(interventionDay, 0.0, "errands");
			break;
		case "shop50":
			builder.restrict(interventionDay, 0.5, "shop_daily", "shop_other");
			break;
		case "shop0":
			builder.restrict(interventionDay, 0.0, "shop_daily", "shop_other");
			break;
		case "educ_kiga50":
			builder.restrict(interventionDay, 0.5, "educ_kiga");
			break;
		case "educ_kiga0":
			builder.restrict(interventionDay, 0.0, "educ_kiga");
			break;
		case "educ_other50":
			builder.restrict(interventionDay, 0.5, "educ_other");
			break;
		case "educ_other0":
			builder.restrict(interventionDay, 0.0, "educ_other");
			break;
		case "educ_school50":
			builder.restrict(interventionDay, 0.5, "educ_primary", "educ_secondary", "educ_tertiary");
			break;
		case "educ_school0":
			builder.restrict(interventionDay, 0.0, "educ_primary", "educ_secondary", "educ_tertiary");
			break;
		case "educ_higher50":
			builder.restrict(interventionDay, 0.5, "educ_higher");
			break;
		case "educ_higher0":
			builder.restrict(interventionDay, 0.0, "educ_higher");
			break;
		case "outOfHome90":
			builder.restrict(interventionDay, 0.9, AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
			break;
		case "outOfHome50":
			builder.restrict(interventionDay, 0.5, AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
			break;
		case "outOfHome0":
			builder.restrict(interventionDay, 0., AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
			break;

		case "tracing60-4d-2d":
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(1);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					episimConfig.getStartDate().plusDays(interventionDay), Integer.MAX_VALUE)
			);
			tracingConfig.setTracingProbability(0.6);
			tracingConfig.setTracingPeriod_days(2);
			tracingConfig.setTracingDelay_days(4);
			tracingConfig.setTraceSusceptible(true);
			break;
		case "tracing60-2d-2d":
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(1);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					episimConfig.getStartDate().plusDays(interventionDay), Integer.MAX_VALUE)
			);
			tracingConfig.setTracingProbability(0.6);
			tracingConfig.setTracingPeriod_days(2);
			tracingConfig.setTracingDelay_days(2);
			tracingConfig.setTraceSusceptible(true);
			break;
		case "tracing90-4d-2d":
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(1);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					episimConfig.getStartDate().plusDays(interventionDay), Integer.MAX_VALUE)
			);
			tracingConfig.setTracingProbability(0.9);
			tracingConfig.setTracingPeriod_days(2);
			tracingConfig.setTracingDelay_days(4);
			tracingConfig.setTraceSusceptible(true);
			break;
		case "tracing90-2d-2d":
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(1);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					episimConfig.getStartDate().plusDays(interventionDay), Integer.MAX_VALUE)
			);
			tracingConfig.setTracingProbability(0.9);
			tracingConfig.setTracingPeriod_days(2);
			tracingConfig.setTracingDelay_days(2);
			tracingConfig.setTraceSusceptible(true);
			break;
		case "tracing60-4d-4d":
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(1);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					episimConfig.getStartDate().plusDays(interventionDay), Integer.MAX_VALUE)
			);
			tracingConfig.setTracingProbability(0.6);
			tracingConfig.setTracingPeriod_days(4);
			tracingConfig.setTracingDelay_days(4);
			tracingConfig.setTraceSusceptible(true);
			break;
		case "tracing60-2d-4d":
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(1);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					episimConfig.getStartDate().plusDays(interventionDay), Integer.MAX_VALUE)
			);
			tracingConfig.setTracingProbability(0.6);
			tracingConfig.setTracingPeriod_days(4);
			tracingConfig.setTracingDelay_days(2);
			tracingConfig.setTraceSusceptible(true);
			break;
		case "tracing90-4d-4d":
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(1);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					episimConfig.getStartDate().plusDays(interventionDay), Integer.MAX_VALUE)
			);
			tracingConfig.setTracingProbability(0.9);
			tracingConfig.setTracingPeriod_days(4);
			tracingConfig.setTracingDelay_days(4);
			tracingConfig.setTraceSusceptible(true);
			break;
		case "tracing90-2d-4d":
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(1);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					episimConfig.getStartDate().plusDays(interventionDay), Integer.MAX_VALUE)
			);
			tracingConfig.setTracingProbability(0.9);
			tracingConfig.setTracingPeriod_days(4);
			tracingConfig.setTracingDelay_days(2);
			tracingConfig.setTraceSusceptible(true);
			break;

		default:
			throw new IllegalArgumentException("Unknown intervention: " + params.intervention);
	}
		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(25)
		public long seed;

		@StringParameter({"none", "tracing60-4d-2d", "tracing60-2d-2d", "tracing90-4d-2d", "tracing90-2d-2d", "tracing90-4d-4d", "tracing90-2d-4d", "0.9FFP@WORK", "0.9CLOTH@PT&SHOP", "0.9CLOTH@SCHOOL", "workBusiness50", "workBusiness0", "leisure50", "leisure0", "errands50", "errands0", "shop50", "shop0", "educ_kiga50", "educ_kiga0",
			"educ_school50", "educ_school0", "educ_higher50", "educ_higher0", "educ_other50", "educ_other0", "outOfHome90", "outOfHome50", "outOfHome0"})
		public String intervention;
		
		@StringParameter({"summer", "winter"})
		public String season;
		


	}


}

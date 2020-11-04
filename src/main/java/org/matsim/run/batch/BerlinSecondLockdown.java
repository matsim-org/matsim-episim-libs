package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.BatchRun.IntParameter;
import org.matsim.episim.BatchRun.Parameter;
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
public class BerlinSecondLockdown implements BatchRun<BerlinSecondLockdown.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		if (params == null)
			return new SnzBerlinWeekScenario2020();

		return new SnzBerlinProductionScenario(25, DiseaseImport.yes, Restrictions.yes, Masks.yes, Tracing.yes, Snapshot.no, AgeDependentInfectionModelWithSeasonality.class);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "secondLockdown");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = 
				new SnzBerlinProductionScenario(25, DiseaseImport.yes, Restrictions.yes, Masks.yes, Tracing.yes, Snapshot.no, AgeDependentInfectionModelWithSeasonality.class);
		
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		
		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
		if (params.lockdown.equals("leisure50")) {
			builder.restrict("2020-11-02", 0.5, "leisure");
			builder.restrict("2020-11-30", params.fraction, "leisure");
		}
		
		if (params.lockdown.equals("allOutOfHome50")) {
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				builder.restrict("2020-11-02", 0.5, act);
				builder.restrict("2020-11-30", params.fraction, act);
			}
		}

		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30,
				LocalDate.of(2020, 6, 15), 200,
				LocalDate.parse("2020-11-02"), 400,
				LocalDate.parse("2020-11-30"), params.tracingCapacity
		));
		
		tracingConfig.setTracingDelay_days(Map.of(
				LocalDate.of(2020, 1, 1), 4,
				LocalDate.parse("2020-11-30"), params.tracingDelay
		));
		
		tracingConfig.setTracingProbability(Map.of(
				LocalDate.of(2020, 1, 1), 0.6,
				LocalDate.parse("2020-11-30"), params.tracingProbability
		));

		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(1)
		public long seed;
		
		@StringParameter({"leisure50", "allOutOfHome50"})
		public String lockdown;
		
		@IntParameter({200, 300, 400, Integer.MAX_VALUE})
		int tracingCapacity;
		
		@Parameter({0.6, 0.8, 1.0})
		double tracingProbability;
		
		@IntParameter({2, 3, 4})
		int tracingDelay;
		
		@Parameter({0.5, 0.75, 0.9, 1.0})
		double fraction;

	}


}

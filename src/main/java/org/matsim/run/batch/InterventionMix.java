package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This batch is for mixing different intervention strategies.
 */
public class InterventionMix implements BatchRun<InterventionMix.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinWeekScenario2020(25, true, true, OldSymmetricContactModel.class);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "interventionMix");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		List<Double> p = List.of(params.edu, params.mask, params.ct);

		// we want all combinations of 0, 0.5, 1
		// and also all combinations where two measure are fixed at 0.5
		if (!p.stream().allMatch(d -> d == 0 || d == 0.5 || d == 1) && Collections.frequency(p, 0.5) < 2) {
			return null;
		}


		SnzBerlinWeekScenario2020 module = new SnzBerlinWeekScenario2020();
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();

		config.global().setRandomSeed(params.seed);

		int restrictionDay = 20;

		builder.restrict(restrictionDay, params.edu, "educ_primary", "educ_kiga", "educ_secondary",
				"educ_higher", "educ_tertiary", "educ_other");

		if (params.mask > 0)
			builder.restrict(restrictionDay, Restriction.ofMask(Map.of(FaceMask.SURGICAL, params.mask)), "pt", "shop_daily", "shop_other");


		if (params.ct > 0) {
			tracingConfig.setTracingProbability(params.ct);
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(1);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					episimConfig.getStartDate().plusDays(restrictionDay), Integer.MAX_VALUE)
			);
			episimConfig.setPolicy(FixedPolicy.class, builder.build());
		}


		// rest is not needed
		return config;
	}


	public static final class Params {

		@GenerateSeeds(50)
		long seed;

		@Parameter({0., 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.})
		double edu;

		@Parameter({0., 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.})
		double mask;

		@Parameter({0., 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.})
		double ct;
	}

}

package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.matsim.run.modules.AbstractSnzScenario2020.DEFAULT_ACTIVITIES;


/**
 * This batch is for mixing different intervention strategies.
 */
public class InterventionMix implements BatchRun<InterventionMix.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Object params) {
		return new SnzBerlinScenario25pct2020();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "interventionMix");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinScenario25pct2020 module = new SnzBerlinScenario25pct2020();
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		// by default no tracing
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);

		// reset present restrictions
		builder.clearAfter(params.referenceDate);
		openRestrictions(builder, params.referenceDate);

		config.global().setRandomSeed(params.seed);

		LocalDate referenceDate = LocalDate.parse(params.referenceDate);

		builder.restrict(referenceDate, params.edu, "educ_primary", "educ_kiga", "educ_secondary",
				"educ_higher", "educ_tertiary", "educ_other");

		if (params.mask > 0)
			builder.restrict(referenceDate, Restriction.ofMask(Map.of(FaceMask.SURGICAL, params.mask)), "pt", "shop_daily", "shop_other");

		if (params.ct > 0) {

			LocalDate warmUp = referenceDate.minusDays(14);
			long offset = ChronoUnit.DAYS.between(episimConfig.getStartDate(), warmUp) + 1;

			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay((int) Math.max(1, offset));
			tracingConfig.setTracingProbability(params.ct);

			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					warmUp, 0,
					referenceDate, Integer.MAX_VALUE
			));

		}

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	/**
	 * Opens all the restrictions
	 */
	private FixedPolicy.ConfigBuilder openRestrictions(FixedPolicy.ConfigBuilder builder, String date) {
		return builder.restrict(date, Restriction.none(), DEFAULT_ACTIVITIES)
				.restrict(date, Restriction.none(), "pt")
				.restrict(date, Restriction.none(), "quarantine_home")
				.restrict(date, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.,
						FaceMask.SURGICAL, 0.)), "pt", "shop_daily", "shop_other");
	}

	public static final class Params {

		@GenerateSeeds(50)
		long seed;

		@StringParameter({"2020-03-07"})
		String referenceDate;

		@Parameter({0, 0.5, 1})
		double edu;

		@Parameter({0, 0.5, 1})
		double mask;

		@Parameter({0, 0.5, 1})
		double ct;
	}

}

package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinSuperSpreaderScenario;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Compare different tracing options with regard to super spreading.
 */
public class BerlinSuperSpreadingTracing implements BatchRun<BerlinSuperSpreadingTracing.Params> {

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "superSpreadingTracing");
	}

	@Override
	public AbstractModule getBindings(int id, Object params) {
		Params p = (Params) params;
		return new SnzBerlinSuperSpreaderScenario(25, p.sigma, p.sigma);
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		Config config = new SnzBerlinSuperSpreaderScenario(25, params.sigma, params.sigma).config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		LocalDate referenceDate = LocalDate.parse(params.referenceDate);

		if (params.unrestricted.equals("yes")) {
			episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config().build());
		}

		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);

		LocalDate warmUp = referenceDate.minusDays(14);
		long offset = ChronoUnit.DAYS.between(episimConfig.getStartDate(), warmUp) + 1;

		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay((int) Math.max(1, offset));
		tracingConfig.setTracingProbability(0.75);
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				warmUp, 0,
				referenceDate, params.tracingCapacity
		));
		tracingConfig.setStrategy(TracingConfigGroup.Strategy.valueOf(params.strategy));

		// uses too much RAM otherwise
		tracingConfig.setTraceSusceptible(false);


		return config;
	}

	public static final class Params {

		@GenerateSeeds(15)
		private long seed;

		@StringParameter({"2020-03-07"})
		String referenceDate;

		@Parameter({0, 0.5, 1, 2})
		private double sigma;

		@IntParameter({0, 30, 60, Integer.MAX_VALUE})
		private int tracingCapacity;

		@StringParameter({"INDIVIDUAL_ONLY", "LOCATION", "LOCATION_WITH_TESTING"})
		public String strategy;

		@StringParameter({"yes"})
		public String unrestricted;

	}
}

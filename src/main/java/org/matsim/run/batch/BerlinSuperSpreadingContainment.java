package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinSuperSpreaderScenario;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Compare different tracing options with regard to super spreading.
 */
public class BerlinSuperSpreadingContainment implements BatchRun<BerlinSuperSpreadingContainment.Params> {

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "superSpreadingContainment");
	}

	@Override
	public AbstractModule getBindings(int id, Params params) {
		return new SnzBerlinSuperSpreaderScenario(true, 30, params.sigma, params.sigma);
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		Config config = new SnzBerlinSuperSpreaderScenario(true, 30, params.sigma, params.sigma).config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		LocalDate referenceDate = LocalDate.parse(params.referenceDate);

		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);

		if (params.unrestricted.equals("yes")) {
			episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config().build());
		}

		 if (params.tracingCapacity > 0) {

			LocalDate warmUp = referenceDate.minusDays(14);
			long offset = ChronoUnit.DAYS.between(episimConfig.getStartDate(), warmUp) + 1;

			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay((int) Math.max(1, offset));
			tracingConfig.setTracingProbability(0.75);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					warmUp, 0,
					referenceDate, params.tracingCapacity
			));
			tracingConfig.setStrategy(TracingConfigGroup.Strategy.valueOf(params.containment));

			// uses too much RAM otherwise
			tracingConfig.setTraceSusceptible(false);

		} else {

			// only one base case with no tracing
			if (!params.containment.equals("INDIVIDUAL_ONLY"))
				return null;
		}


		return config;
	}

	public static final class Params {

		@GenerateSeeds(15)
		private long seed;

		@Parameter({0, 1, 1.5})
		private double sigma;

		@IntParameter({0, 30, 90, Integer.MAX_VALUE})
		private int tracingCapacity;

		@StringParameter({"INDIVIDUAL_ONLY", "LOCATION", "LOCATION_WITH_TESTING", "IDENTIFY_SOURCE"})
		public String containment;

		@StringParameter({"2020-03-07"})
		String referenceDate;

		@StringParameter({"yes"})
		public String unrestricted;

	}
}

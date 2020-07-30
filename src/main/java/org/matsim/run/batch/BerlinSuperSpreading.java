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

/**
 * Run to analyze different viral load and susceptibility for persons.
 */
public class BerlinSuperSpreading implements BatchRun<BerlinSuperSpreading.Params> {

	public static final String[] ACTIVITIES = {"work", "leisure", "visit", "errands"};

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "superSpreading");
	}

	@Override
	public AbstractModule getBindings(int id, Object params) {
		Params p = (Params) params;
		return new SnzBerlinSuperSpreaderScenario(0, p.sigma);
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		Config config = new SnzBerlinSuperSpreaderScenario(0, params.sigma).config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy())
//				.clearAfter("2020-03-07", ACTIVITIES)
				.restrict("2020-03-07", Restriction.ofGroupSize(params.groupSize), ACTIVITIES);

		if (params.ciCorrection > 0)
			builder.restrict("2020-03-07", Restriction.ofCiCorrection(params.ciCorrection), ACTIVITIES);

		if (params.unrestricted.equals("yes")) {
			TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);

			// unrestricted
			episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config().build());
		}

		return config;
	}

	public static final class Params {

		@GenerateSeeds(40)
		private long seed;

		//@IntParameter({1, 42, 80, 154})
		private int groupSize;

		@Parameter({0, 1})
		private double sigma;

		//@Parameter({1.0})
		private double ciCorrection;

		@StringParameter({"yes"})
		public String unrestricted;

	}
}

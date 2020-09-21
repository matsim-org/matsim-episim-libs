package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinSuperSpreaderScenario;

/**
 * Compare different tracing options with regard to super spreading.
 */
public class BerlinDispersion implements BatchRun<BerlinDispersion.Params> {

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "dispersion");
	}

	@Override
	public AbstractModule getBindings(int id, Params params) {
		Params p = (Params) params;
		return new SnzBerlinSuperSpreaderScenario(p.contactModel.equals("SYMMETRIC"), p.maxContacts, p.sigma, p.sigma);
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		Config config = new SnzBerlinSuperSpreaderScenario(params.contactModel.equals("SYMMETRIC"), params.maxContacts, params.sigma, params.sigma).config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);

		// unrestricted
		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config().build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(10)
		private long seed;

		@Parameter({0})
		private double sigma;

		@StringParameter({"DEFAULT", "SYMMETRIC"})
		public String contactModel;

		@IntParameter({1, 3, 10, 30})
		private int maxContacts;

	}
}

package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinSuperSpreaderScenario;
import org.matsim.run.modules.SnzBerlinWeekScenario25pct2020;

/**
 * Run to analyze different viral load and susceptibility for persons.
 */
public class BerlinSuperSpreading implements BatchRun<BerlinSuperSpreading.Params> {

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "superSpreading");
	}

	@Override
	public AbstractModule getBindings(int id, Object params) {
		Params p = (Params) params;

		if (p.superSpreading.equals("yes")) {
			return new SnzBerlinSuperSpreaderScenario();
		} else {
			return new SnzBerlinWeekScenario25pct2020();
		}
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		Config config;
		if (params.superSpreading.equals("yes")) {
			config = new SnzBerlinSuperSpreaderScenario().config();
		} else {
			config = new SnzBerlinWeekScenario25pct2020().config();
		}

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy())
				.clearAfter("2020-03-07")
				.restrict("2020-03-07", Restriction.ofGroupSize(params.groupSize), "work", "leisure", "shop_daily", "business");

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(50)
		private long seed;

		@IntParameter({1, 42, 80, 154})
		private int groupSize;

		@StringParameter({"yes", "no"})
		private String superSpreading;

	}
}

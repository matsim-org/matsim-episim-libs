package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import javax.annotation.Nullable;
import java.util.Map;


/**
 * This batch run explores different random seeds via different snapshot times.
 */
public class StabilityRuns implements BatchRun<StabilityRuns.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Object params) {
		return new SnzBerlinScenario25pct2020();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("paper-3.3", "stability");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinScenario25pct2020 module = new SnzBerlinScenario25pct2020();
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		SnzBerlinScenario25pct2020.BasePolicyBuilder basePolicyBuilder = new SnzBerlinScenario25pct2020.BasePolicyBuilder(episimConfig);

		basePolicyBuilder.setAlpha(params.alpha);

		basePolicyBuilder.setCiCorrections(Map.of("2020-03-08", params.ci));

		episimConfig.setPolicy(FixedPolicy.class, basePolicyBuilder.build().build());


		//	episimConfig.setStartFromSnapshot(BatchRun.resolveForCluster(SnzBerlinScenario25pct2020.INPUT,
		//			String.format("episim-snapshot5-%03d.zip", params.startFrom)));

		config.global().setRandomSeed(params.seed);

		return config;
	}

	public static final class Params {

		@GenerateSeeds(300)
		long seed;

		@Parameter({1.7})
		double alpha;

		@Parameter({1})
		double ci;

	}

}

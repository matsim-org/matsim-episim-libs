package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import javax.annotation.Nullable;
import java.util.Map;


/**
 * This batch run explores different random seeds via different snapshot times.
 */
public class StabilityRuns implements BatchRun<StabilityRuns.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinScenario25pct2020();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("paper-3.1", "stability");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinScenario25pct2020 module = new SnzBerlinScenario25pct2020();
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		SnzBerlinScenario25pct2020.BasePolicyBuilder basePolicyBuilder = new SnzBerlinScenario25pct2020.BasePolicyBuilder(episimConfig);

		((CreateRestrictionsFromCSV) basePolicyBuilder.getActivityParticipation()).setAlpha(params.alpha);

		double ci;
		if (params.alpha == 1.0) {
			ci = 0.32;
		} else if (params.alpha == 1.2) {
			ci = 0.360;
		} else if (params.alpha == 1.4) {
			ci = 0.437;
		} else if (params.alpha == 1.7) {
			ci = 1;
		} else if (params.alpha == 0) {
			// special case with no correction
			((CreateRestrictionsFromCSV) basePolicyBuilder.getActivityParticipation()).setAlpha(1);
			ci = 1;
		} else if (params.alpha == -1) {
			ci = Double.NaN;
		} else
			throw new IllegalArgumentException("No ci known for alpha: " + params.alpha);

		basePolicyBuilder.setCiCorrections(Map.of("2020-03-07", ci));

		if (params.alpha > -1) {
			episimConfig.setPolicy(FixedPolicy.class, basePolicyBuilder.build().build());
		} else {
			// Set unrestricted configuration
			episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config().build());
		}

		//	episimConfig.setStartFromSnapshot(BatchRun.resolveForCluster(SnzBerlinScenario25pct2020.INPUT,
		//			String.format("episim-snapshot5-%03d.zip", params.startFrom)));

		config.global().setRandomSeed(params.seed);

		return config;
	}

	public static final class Params {

		@GenerateSeeds(300)
		long seed;

		@Parameter({-1})
		double alpha;

		//@Parameter({1})
		//double ci;

	}

}

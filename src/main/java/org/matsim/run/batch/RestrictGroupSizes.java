package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import javax.annotation.Nullable;


/**
 * This batch run explores restrictions on group sizes of activities.
 */
public class RestrictGroupSizes implements BatchRun<RestrictGroupSizes.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Object params) {
		return new SnzBerlinScenario25pct2020();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "groupSizes");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinScenario25pct2020 module = new SnzBerlinScenario25pct2020();
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		builder.restrict("2020-03-07", Restriction.ofGroupSize(params.maxGroupSize), "leisure");
		episimConfig.setPolicy(FixedPolicy.class, builder.build());


		return config;
	}

	public static final class Params {

		@IntParameter({-1, 5, 10, 15, 20, 25, 30})
		int maxGroupSize;

	}

}

package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import javax.annotation.Nullable;
import java.util.Map;


/**
 * A batch run for the influence of some parameters.
 */
public class ParamsBatch implements BatchRun<ParamsBatch.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinWeekScenario2020();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "params");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020 module = new SnzBerlinWeekScenario2020();
		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setStartDate(params.startDate);

		SnzBerlinScenario25pct2020.BasePolicyBuilder basePolicyBuilder = new SnzBerlinScenario25pct2020.BasePolicyBuilder(episimConfig);

		((CreateRestrictionsFromCSV) basePolicyBuilder.getActivityParticipation()).setAlpha(params.alpha);
		basePolicyBuilder.setCiCorrections(Map.of(params.ciDate, params.ci));

		episimConfig.setPolicy(FixedPolicy.class, basePolicyBuilder.build().build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(30)
		long seed;

		@Parameter({1})
		double alpha;

		@Parameter({0.28, 0.3, 0.32, 0.34})
		double ci;

		@StringParameter({"2020-02-16", "2020-02-17", "2020-02-18"})
		String startDate;

		@StringParameter({"2020-03-06", "2020-03-07"})
		String ciDate;
	}

}

package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;
import org.matsim.run.modules.SnzBerlinWeekScenario2020Symmetric;

import javax.annotation.Nullable;
import java.util.Map;


/**
 * Runs comparing different contact models for berlin.
 */
public class ContactModels implements BatchRun<ContactModels.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinScenario25pct2020();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "contactModels");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020Symmetric module = new SnzBerlinWeekScenario2020Symmetric();
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		// TODO
		episimConfig.setCalibrationParameter(1.0e-5);

		if (params.unrestricted.equals("yes")) {
			episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config().build());
		} else {

			SnzBerlinScenario25pct2020.BasePolicyBuilder basePolicyBuilder = new SnzBerlinScenario25pct2020.BasePolicyBuilder(episimConfig);
			basePolicyBuilder.setCiCorrections(Map.of("2020-03-07", 0.32));

			// TODO: ci corrections

			episimConfig.setPolicy(FixedPolicy.class, basePolicyBuilder.build().build());

		}

		config.global().setRandomSeed(params.seed);

		return config;
	}

	public static final class Params {

		@GenerateSeeds(50)
		long seed;

		@ClassParameter({DefaultContactModel.class, OldSymmetricContactModel.class,
				SymmetricContactModel.class, PairWiseContactModel.class})
		public Class<? extends ContactModel> contactModel;

		@StringParameter({"yes", "no"})
		public String unrestricted;

	}

}

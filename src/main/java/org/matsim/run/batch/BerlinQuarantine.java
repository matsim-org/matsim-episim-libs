package org.matsim.run.batch;

import com.google.inject.AbstractModule;


import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Map;


/**
 * batch class
 */
public class BerlinQuarantine implements BatchRun<BerlinQuarantine.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinWeekScenario2020(25, true, true, OldSymmetricContactModel.class);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "quarantine");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020 module = new  SnzBerlinWeekScenario2020(25, true, true, OldSymmetricContactModel.class);
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		tracingConfig.setTracingDelay_days(params.tracingDelay);

		tracingConfig.setQuarantineRelease(TracingConfigGroup.QuarantineRelease.valueOf(params.quarantineRelease));

		tracingConfig.setQuarantineDuration(Map.of(LocalDate.EPOCH, params.quarantineDuration));

		return config;
	}

	public static final class Params {

		@GenerateSeeds(10)
		long seed;

		@StringParameter({"NON_SYMPTOMS" , "SUSCEPTIBLE"})
		public String quarantineRelease;

		@IntParameter({14, 10, 7, 5})
		private int quarantineDuration;

		@IntParameter({1, 2, 3})
		private int tracingDelay;
	}

}

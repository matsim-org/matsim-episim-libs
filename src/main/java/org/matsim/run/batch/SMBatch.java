package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Map;


/**
 * sebastians playground
 */
public class SMBatch implements BatchRun<SMBatch.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinWeekScenario2020(25, true, true, OldSymmetricContactModel.class);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "weekSymmetric");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020 module = new SnzBerlinWeekScenario2020();
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

//		episimConfig.setStartDate(params.startDate);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		builder.restrict("2020-09-14", Restriction.ofCiCorrection(0.6 * params.eduCiCorrection), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30,
				LocalDate.of(2020, 6, 1), params.tracingCapacity)
		);

		if (params.mask.equals("none"));
		else if (params.mask.equals("cloth90")) builder.restrict("2020-09-14", Restriction.ofMask(FaceMask.CLOTH, 0.9), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		else if (params.mask.equals("FFP90")) builder.restrict("2020-09-14", Restriction.ofMask(FaceMask.N95, 0.9), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(10)
		long seed;

//		@StringParameter({"2020-02-16", "2020-02-18"})
//		public String startDate;

		@Parameter({0.25, 0.5, 1.})
		private double eduCiCorrection;

		@IntParameter({Integer.MAX_VALUE, 100})
		private int tracingCapacity;

		@StringParameter({"none", "cloth90", "FFP90"})
		public String mask;
	}


}

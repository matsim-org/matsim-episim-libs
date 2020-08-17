package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Map;


/**
 * This batch compares different numbers for initial infections
 */
public class HolidayReturnees implements BatchRun<HolidayReturnees.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Object params) {
		return new SnzBerlinWeekScenario2020();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "holidayReturnees");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020 module = new SnzBerlinWeekScenario2020();
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30,
				LocalDate.of(2020, 6, 1), params.tracingCapacity
		));


		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		builder.clearAfter("2020-05-30", "work");
		builder.restrict("2020-05-31", 0.85, "work");

		builder.interpolate("2020-06-01", "2020-10-01", Restriction.ofCiCorrection(0.32), Restriction.ofCiCorrection(0.64), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
		builder.interpolate("2020-06-01", "2020-10-01", Restriction.ofCiCorrection(0.32), Restriction.ofCiCorrection(0.64), "quarantine_home");

		if (params.furtherMeasuresOnOct1.equals("yes")) {

			String oct1 = "2020-10-01";

			builder.restrict(oct1, Restriction.ofMask(FaceMask.N95, 0.95), "pt", "shop_daily", "shop_other");

			builder.restrict(oct1, Restriction.ofMask(FaceMask.N95, 0.9), "work");

			builder.clearAfter("2020-10-02", "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");

			builder.restrict(oct1, 0.5, "educ_primary", "educ_kiga");
			builder.restrict(oct1, 0.5, "educ_secondary", "educ_tertiary", "educ_other");

			builder.restrict("2020-10-09", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
			builder.restrict("2020-10-25", 0.5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");


		}

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		episimConfig.setInfections_pers_per_day(Map.of(
				LocalDate.of(2020, 7, 9), params.infectionsPerDay
		));


		episimConfig.setInitialInfections(Integer.MAX_VALUE);

		config.global().setRandomSeed(params.seed);

		return config;
	}

	public static final class Params {

		@GenerateSeeds(20)
		long seed;

		@IntParameter({30, Integer.MAX_VALUE})
		int tracingCapacity;

		@IntParameter({1, 3, 5, 7, 9})
		int infectionsPerDay;

		@StringParameter({"yes", "no"})
		private String furtherMeasuresOnOct1;

	}

}

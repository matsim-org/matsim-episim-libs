package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Map;

/**
 * Batch class for curfew runs
 */
public class BerlinCurfew implements BatchRun<BerlinCurfew.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {

		return new SnzBerlinWeekScenario2020(25, true, true, OldSymmetricContactModel.class);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "curfew");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020 module = new SnzBerlinWeekScenario2020(25, true, true, OldSymmetricContactModel.class);
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		ConfigBuilder builder;
		LocalDate day;

		// test scenario with few restrictions
		if (params.variant.equals("testing")) {
			builder = FixedPolicy.config();
			day = episimConfig.getStartDate().plusDays(20);

			TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);
		} else {
			builder = FixedPolicy.parse(episimConfig.getPolicy());
			day = LocalDate.of(2020, 10, 12);

			TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					LocalDate.of(2020, 4, 1), 30,
					LocalDate.of(2020, 6, 15), params.tracingCapacity
			));
		}

		switch (params.curfew) {
			case "1-6":
				builder.restrict(day, Restriction.ofClosingHours(1, 6), "leisure");
				break;
			case "0-6":
				builder.restrict(day, Restriction.ofClosingHours(0, 6), "leisure");
				break;
			case "23-6":
				builder.restrict(day, Restriction.ofClosingHours(23, 6), "leisure");
				break;
			case "22-6":
				builder.restrict(day, Restriction.ofClosingHours(22, 6), "leisure");
				break;
			case "21-6":
				builder.restrict(day, Restriction.ofClosingHours(21, 6), "leisure");
				break;
			case "20-6":
				builder.restrict(day, Restriction.ofClosingHours(20, 6), "leisure");
				break;
			case "19-6":
				builder.restrict(day, Restriction.ofClosingHours(19, 6), "leisure");
				break;
			case "0-24":
				builder.restrict(day, Restriction.ofClosingHours(0, 24), "leisure");
				break;
			case "remainingFraction0":
				builder.restrict(day, 0., "leisure");
				break;
			case "no":
				break;
			default:
				throw new RuntimeException("not implemented");
		}

		if (params.holidays.equals("no")) builder.restrict("2020-10-12", 1., "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		else if (params.holidays.equals("yes"));
		else throw new RuntimeException();

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(20)
		public long seed;

//		@StringParameter({"current"})
		public String variant = "current";

		@StringParameter({"no", "yes"})
		public String holidays;

		@StringParameter({"no", "0-6", "23-6", "22-6", "21-6", "20-6", "19-6", "0-24"})
		public String curfew;

		@IntParameter({200, Integer.MAX_VALUE})
		int tracingCapacity;

	}


}

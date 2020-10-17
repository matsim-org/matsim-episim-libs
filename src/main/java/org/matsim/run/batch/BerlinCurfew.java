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

/**
 * Batch class for curfew runs
 */
public class BerlinCurfew implements BatchRun<BerlinCurfew.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {

		return new SnzBerlinWeekScenario2020(25, false, true, OldSymmetricContactModel.class);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "curfew");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020 module = new SnzBerlinWeekScenario2020(25, false, true, OldSymmetricContactModel.class);
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

		} else  {
			builder = FixedPolicy.parse(episimConfig.getPolicy());
			day = LocalDate.of(2020, 10, 12);
		}

		if (params.curfew.equals("23-6")) builder.restrict(day, Restriction.ofClosingHours(0,6 ,23,24), "leisure");
		else if (params.curfew.equals("22-6")) builder.restrict(day, Restriction.ofClosingHours(0,6 ,22,24), "leisure");
		else if (params.curfew.equals("21-6")) builder.restrict(day, Restriction.ofClosingHours(0,6 ,21,24), "leisure");
		else if (params.curfew.equals("20-6")) builder.restrict(day, Restriction.ofClosingHours(0,6 ,20,24), "leisure");
		else if (params.curfew.equals("0-24")) builder.restrict(day, Restriction.ofClosingHours(0,24), "leisure");
		else if (params.curfew.equals("remainingFraction0")) builder.restrict(day, 0., "leisure");
		else if (params.curfew.equals("no"));
		else throw new RuntimeException("not implemented");

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(50)
		public long seed;

		@StringParameter({"testing", "current"})
		public String variant;

		@StringParameter({"no", "23-6", "22-6", "21-6", "20-6", "0-24", "remainingFraction0"})
		public String curfew;

	}


}

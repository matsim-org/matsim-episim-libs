package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.BatchRun.Parameter;
import org.matsim.episim.BatchRun.StringParameter;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;
import org.matsim.run.modules.SnzBerlinWeekScenario25pct2020;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Map;

import static org.matsim.run.modules.AbstractSnzScenario2020.DEFAULT_ACTIVITIES;


/**
 * This batch run executes SnzBerlinWeekScenario25pct2020 with 100 seeds
 */
public class BerlinPublicTransport implements BatchRun<BerlinPublicTransport.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Object params) {
		return new SnzBerlinWeekScenario25pct2020();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "base");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario25pct2020 module = new SnzBerlinWeekScenario25pct2020();
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setStartDate("2020-02-16");
//		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		config.global().setRandomSeed(params.seed);
		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		builder.clearAfter("2020-04-01", "pt");
		int introductionPeriod = 14;
		LocalDate masksCenterDate = LocalDate.of(2020, 4, 27);
		for (int ii = 0; ii <= introductionPeriod; ii++) {
			LocalDate date = masksCenterDate.plusDays(-introductionPeriod / 2 + ii);
			builder.restrict(date, Restriction.ofMask(FaceMask.valueOf(params.maskType), params.maskCompliance * ii / introductionPeriod), "pt");
		}
		episimConfig.setPolicy(FixedPolicy.class, builder.build());
		return config;
	}

	public static final class Params {

		@GenerateSeeds(100)
		long seed;
		
		@StringParameter({"CLOTH", "SURGICAL", "N95"})
		String maskType;
		
		@Parameter({0.0, 0.5, 0.9, 1.0})
		double maskCompliance;

	}

}

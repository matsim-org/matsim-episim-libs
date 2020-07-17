package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.BatchRun.IntParameter;
import org.matsim.episim.BatchRun.Parameter;
import org.matsim.episim.BatchRun.StartDates;
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
 * This batch run examines the effects of increased indoor activities in winter
 */
public class BerlinWeekSeason implements BatchRun<BerlinWeekSeason.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Object params) {
		return new SnzBerlinWeekScenario25pct2020();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "outdoor");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario25pct2020 module = new SnzBerlinWeekScenario25pct2020();
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30,
				LocalDate.of(2020, 7, 1), params.tracingCapacity
		));
		
		episimConfig.setCalibrationParameter(1.4e-5);
		episimConfig.setOutdoorProba(params.winterOudoorFraction);
		LocalDate startDate = LocalDate.parse("2020-02-16");
		episimConfig.setStartDate(startDate);

		config.global().setRandomSeed(params.seed);

		return config;
	}

	public static final class Params {

//		@StartDates({"2020-02-16", "2020-02-17", "2020-02-18"})
//		LocalDate startDate;
		
		@GenerateSeeds(9)
		long seed;
		
		@Parameter({0.0, 0.1, 0.2, 0.4, 0.6, 0.8})
		double winterOudoorFraction;
		
		@IntParameter({30, 60, Integer.MAX_VALUE})
		int tracingCapacity;

	}

}

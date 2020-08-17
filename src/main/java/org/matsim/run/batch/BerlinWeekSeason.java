package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.InfectionModel;
import org.matsim.episim.model.InfectionModelWithSeasonality;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Map;


/**
 * This batch run examines the effects of increased indoor activities in winter
 */
public class BerlinWeekSeason implements BatchRun<BerlinWeekSeason.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Object params) {
		return (AbstractModule) Modules.override(new SnzBerlinWeekScenario2020(25))
				.with(new AbstractModule() {
					@Override
					protected void configure() {
						bind(InfectionModel.class).to(InfectionModelWithSeasonality.class).in(Singleton.class);
					}
				});
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "outdoor");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020 module = new SnzBerlinWeekScenario2020(25);
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30,
				LocalDate.of(2020, 7, 1), params.tracingCapacity
		));

		episimConfig.setCalibrationParameter(1.4e-5);
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

		@IntParameter({30, 60, Integer.MAX_VALUE})
		int tracingCapacity;

	}

}

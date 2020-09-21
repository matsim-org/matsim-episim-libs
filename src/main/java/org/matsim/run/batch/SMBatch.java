package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.AgeDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.AgeDependentProgressionModel;
import org.matsim.episim.model.ContactModel;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.InfectionModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinWeekScenario2020Symmetric;

import java.time.LocalDate;
import java.util.Map;

import javax.annotation.Nullable;


/**
 * sebastians playground
 */
public class SMBatch implements BatchRun<SMBatch.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Object params) {
		return (AbstractModule) Modules.override(new SnzBerlinWeekScenario2020Symmetric(25))
				.with(new AbstractModule() {
					@Override
					protected void configure() {
						bind(ContactModel.class).to(OldSymmetricContactModel.class).in(Singleton.class);
						bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);
						bind(InfectionModel.class).to(AgeDependentInfectionModelWithSeasonality.class).in(Singleton.class);
					}
//					@Provides
//					@Singleton
//					public Scenario scenario(Config config) {
//
//						// guice will use no args constructor by default, we check if this config was initialized
//						// this is only the case when no explicit binding are required
//						if (config.getModules().size() == 0)
//							throw new IllegalArgumentException("Please provide a config module or binding.");
//
//						config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);
//
//						// save some time for not needed inputs
//						config.facilities().setInputFile(null);
//
//						final Scenario scenario = ScenarioUtils.loadScenario( config );
//
//						return scenario;
//					}
				});
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "weekSymmetric");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020Symmetric module = new SnzBerlinWeekScenario2020Symmetric();
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

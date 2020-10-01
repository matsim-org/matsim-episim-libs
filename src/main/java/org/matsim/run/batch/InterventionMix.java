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
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;

import org.matsim.run.modules.SnzBerlinWeekScenario2020Symmetric;

import javax.annotation.Nullable;

import java.time.LocalDate;
import java.util.Map;

/**
 * This batch is for mixing different intervention strategies.
 */
public class InterventionMix implements BatchRun<InterventionMix.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return (AbstractModule) Modules.override(new SnzBerlinWeekScenario2020Symmetric(25))
				.with(new AbstractModule() {
					@Override
					protected void configure() {
						bind(ContactModel.class).to(OldSymmetricContactModel.class).in(Singleton.class);
						bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);
						bind(InfectionModel.class).to(AgeDependentInfectionModelWithSeasonality.class).in(Singleton.class);
					}
				});
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "interventionMix");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020Symmetric module = new SnzBerlinWeekScenario2020Symmetric();
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();

		config.global().setRandomSeed(params.seed);
		
		int restrictionDay = 20;

		builder.restrict(restrictionDay, params.edu, "educ_primary", "educ_kiga", "educ_secondary",
				"educ_higher", "educ_tertiary", "educ_other");

		if (params.mask > 0) builder.restrict(restrictionDay, Restriction.ofMask(Map.of(FaceMask.SURGICAL, params.mask)), "pt", "shop_daily", "shop_other");

		tracingConfig.setTracingProbability(params.ct);
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(1);
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				episimConfig.getStartDate(), 0,
				episimConfig.getStartDate().plusDays(restrictionDay), Integer.MAX_VALUE)
		);
		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	/**
	 * Opens all the restrictions
	 */
//	private FixedPolicy.ConfigBuilder openRestrictions(FixedPolicy.ConfigBuilder builder, String date) {
//		return builder.restrict(date, Restriction.none(), DEFAULT_ACTIVITIES)
//				.restrict(date, Restriction.none(), "pt")
//				.restrict(date, Restriction.none(), "quarantine_home")
//				.restrict(date, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.,
//						FaceMask.SURGICAL, 0.)), "pt", "shop_daily", "shop_other");
//	}

	public static final class Params {

		@GenerateSeeds(50)
		long seed;

		@Parameter({0.5})
//		@Parameter({0., 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.})
		double edu;

		@Parameter({0.5})
//		@Parameter({0., 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.})
		double mask;

//		@Parameter({0.5})
		@Parameter({0., 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.})
		double ct;
	}

}

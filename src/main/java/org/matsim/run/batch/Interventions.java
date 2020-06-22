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
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.matsim.run.modules.AbstractSnzScenario2020.DEFAULT_ACTIVITIES;


/**
 * This batch run executes different interventions strategies to measure their influence
 */
public class Interventions implements BatchRun<Interventions.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Object params) {
		return new SnzBerlinScenario25pct2020();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "interventions");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinScenario25pct2020 module = new SnzBerlinScenario25pct2020();
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		// by default no tracing
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);

		// reset present restrictions
		builder.clearAfter(params.referenceDate);
		openRestrictions(builder, params.referenceDate);

		LocalDate referenceDate = LocalDate.parse(params.referenceDate);

		switch (params.intervention) {
			case "none":
				break;

			case "ci0.32":
				builder.restrict(referenceDate, Restriction.ofCiCorrection(0.32), DEFAULT_ACTIVITIES)
						.restrict(referenceDate, Restriction.ofCiCorrection(0.32), "pt");
				break;

			case "ci0.5":
				builder.restrict(referenceDate, Restriction.ofCiCorrection(0.5), DEFAULT_ACTIVITIES)
						.restrict(referenceDate, Restriction.ofCiCorrection(0.5), "pt");
				break;

			case "edu0":
				builder.restrict(referenceDate, 0, "educ_primary", "educ_kiga", "educ_secondary",
						"educ_higher", "educ_tertiary", "educ_other");
				break;
			case "edu50":
				builder.restrict(referenceDate, 0.5, "educ_primary", "educ_kiga", "educ_secondary",
						"educ_higher", "educ_tertiary", "educ_other");
				break;

			case "leisure50":
				builder.restrict(referenceDate, 0.5, "leisure");
				break;

			case "shopping50":
				builder.restrict(referenceDate, 0.5, "shop_daily", "shop_other");
				break;

			case "work50":
				builder.restrict(referenceDate, 0.5, "work", "business");
				break;

			case "outOfHome50":
				builder.restrict(referenceDate, 0.5, DEFAULT_ACTIVITIES);
				break;

			case "clothMasks":
				// TODO
				break;

			case "n95masks":
				// TODO
				break;

			case "n95masksAll":
				// TODO
				break;

			case "contactTracing":

				LocalDate warmUp = referenceDate.minusDays(14);
				long offset = ChronoUnit.DAYS.between(episimConfig.getStartDate(), warmUp) + 1;

				tracingConfig.setPutTraceablePersonsInQuarantineAfterDay((int) Math.max(1, offset));

				tracingConfig.setTracingCapacity_pers_per_day(Map.of(
						warmUp, 0,
						referenceDate, 30
				));


				break;

			default:
				throw new IllegalArgumentException("Unknown intervention: " + params.intervention);
		}


		episimConfig.setPolicy(FixedPolicy.class, builder.build());


		return config;
	}

	/**
	 * Opens all the restrictions
	 */
	private FixedPolicy.ConfigBuilder openRestrictions(FixedPolicy.ConfigBuilder builder, String date) {
		return builder.restrict(date, Restriction.none(), DEFAULT_ACTIVITIES)
				.restrict(date, Restriction.none(), "pt")
				.restrict(date, Restriction.none(), "quarantine_home")
				.restrict(date, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.,
						FaceMask.SURGICAL, 0.)), "pt", "shop_daily", "shop_other");
	}

	public static final class Params {

		@StringParameter({"2020-03-08", "2020-04-20"})
		String referenceDate;

		@StringParameter({"none", "ci0.32", "ci0.5", "edu0", "edu50", "leisure50", "shopping50", "work50", "outOfHome50",
				"clothMasks", "n95masks", "n95masksAll", "contactTracing"})
		String intervention;

	}

}

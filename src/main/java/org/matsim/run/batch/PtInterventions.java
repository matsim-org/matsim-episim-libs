package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.BatchRun.Parameter;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Map;

import static org.matsim.run.modules.AbstractSnzScenario2020.DEFAULT_ACTIVITIES;


/**
 * This batch run executes different runs with different mask types and compliance rates
 */
public class PtInterventions implements BatchRun<PtInterventions.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
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
		openRestrictions(builder,params.referenceDate);

		config.global().setRandomSeed(params.seed);

		LocalDate referenceDate = LocalDate.parse(params.referenceDate);

		episimConfig.getOrAddContainerParams("pt").setContactIntensity(params.ptCi);

		switch (params.intervention) {
			case "none":
				break;
			case "ci0.32":
				builder.restrict(referenceDate, Restriction.ofCiCorrection(0.32), "pt");
				break;

			case "pt50":
				builder.restrict(referenceDate, 0.5, "pt");
				break;

			case "pt100":
				builder.restrict(referenceDate, 0., "pt");
				break;

			case "cloth50":
				builder.restrict(referenceDate, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.5)), "pt");
				break;
			case "cloth90":
				builder.restrict(referenceDate, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.9)), "pt");
				break;
			case "cloth100":
				builder.restrict(referenceDate, Restriction.ofMask(Map.of(FaceMask.CLOTH, 1.)), "pt");
				break;
			case "surgical50":
				builder.restrict(referenceDate, Restriction.ofMask(Map.of(FaceMask.SURGICAL, 0.5)), "pt");
				break;
			case "surgical90":
				builder.restrict(referenceDate, Restriction.ofMask(Map.of(FaceMask.SURGICAL, 0.9)), "pt");
				break;
			case "surgical100":
				builder.restrict(referenceDate, Restriction.ofMask(Map.of(FaceMask.SURGICAL, 1.)), "pt");
				break;
			case "N9550":
				builder.restrict(referenceDate, Restriction.ofMask(Map.of(FaceMask.N95, 0.5)), "pt");
				break;
			case "N9590":
				builder.restrict(referenceDate, Restriction.ofMask(Map.of(FaceMask.N95, 0.9)), "pt");
				break;
			case "N95100":
				builder.restrict(referenceDate, Restriction.ofMask(Map.of(FaceMask.N95, 1.)), "pt");
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

		@GenerateSeeds(100)
		long seed;

		@StringParameter({"2020-03-07"})
		String referenceDate;

		@StringParameter({"none", "pt50", "pt100", "cloth50", "cloth90", "cloth100", "surgical50", "surgical90", "surgical100", "N9550", "N9590", "N95100"})
		String intervention;

		@Parameter({1., 2., 5.})
		double ptCi;

	}

}

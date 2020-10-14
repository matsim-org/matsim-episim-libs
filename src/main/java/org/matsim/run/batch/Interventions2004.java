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
import java.util.Map;

import static org.matsim.run.modules.AbstractSnzScenario2020.DEFAULT_ACTIVITIES;


/**
 * This batch run executes different interventions strategies to measure their influence
 */
public class Interventions2004 implements BatchRun<Interventions2004.Params> {

	private static final String UNRESTRICTED = "unrestricted";
	private static final String RESTRICTED = "restricted";

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

		config.global().setRandomSeed(params.seed);

		LocalDate referenceDate = LocalDate.parse(params.referenceDate);

		double remainingFraction = 1.;

		if (params.runType.equals(RESTRICTED)) remainingFraction = 0.5;

		switch (params.intervention) {

			case "ci0.32":
				double ciCorrection = 1.;

				if (params.runType.equals(RESTRICTED)) ciCorrection = 0.32;

				builder.restrict(referenceDate, Restriction.ofCiCorrection(ciCorrection), DEFAULT_ACTIVITIES)
						.restrict(referenceDate, Restriction.ofCiCorrection(ciCorrection), "pt")
						.restrict(referenceDate, Restriction.ofCiCorrection(ciCorrection), "quarantine_home");
				break;

			case "edu0":
				double edu0Fraction = 1.;

				if (params.runType.equals(RESTRICTED)) edu0Fraction = 0.0;
				builder.clearAfter(params.referenceDate, "educ_primary");
				builder.clearAfter(params.referenceDate, "educ_kiga");
				builder.clearAfter(params.referenceDate, "educ_secondary");
				builder.clearAfter(params.referenceDate, "educ_higher");
				builder.clearAfter(params.referenceDate, "educ_tertiary");
				builder.clearAfter(params.referenceDate, "educ_other");

				builder.restrict(referenceDate, edu0Fraction, "educ_primary", "educ_kiga", "educ_secondary",
						"educ_higher", "educ_tertiary", "educ_other");
				break;

			case "edu50":
				builder.clearAfter(params.referenceDate, "educ_primary");
				builder.clearAfter(params.referenceDate, "educ_kiga");
				builder.clearAfter(params.referenceDate, "educ_secondary");
				builder.clearAfter(params.referenceDate, "educ_higher");
				builder.clearAfter(params.referenceDate, "educ_tertiary");
				builder.clearAfter(params.referenceDate, "educ_other");
				builder.restrict(referenceDate, remainingFraction, "educ_primary", "educ_kiga", "educ_secondary",
						"educ_higher", "educ_tertiary", "educ_other");
				break;

			case "leisure50":
				builder.clearAfter(params.referenceDate, "leisure");
				builder.restrict(referenceDate, remainingFraction, "leisure");
				break;

			case "shopping50":
				builder.clearAfter(params.referenceDate, "shop_daily");
				builder.clearAfter(params.referenceDate, "shop_other");
				builder.restrict(referenceDate, remainingFraction, "shop_daily", "shop_other");
				break;

			case "work50":
				builder.clearAfter(params.referenceDate, "work");
				builder.clearAfter(params.referenceDate, "business");
				builder.restrict(referenceDate, remainingFraction, "work", "business");
				break;

			case "outOfHome50":
				builder.clearAfter(params.referenceDate, "pt");
				builder.clearAfter(params.referenceDate, "work");
				builder.clearAfter(params.referenceDate, "leisure");
				builder.clearAfter(params.referenceDate, "educ_primary");
				builder.clearAfter(params.referenceDate, "educ_kiga");
				builder.clearAfter(params.referenceDate, "educ_secondary");
				builder.clearAfter(params.referenceDate, "educ_higher");
				builder.clearAfter(params.referenceDate, "educ_tertiary");
				builder.clearAfter(params.referenceDate, "educ_other");
				builder.clearAfter(params.referenceDate, "shop_daily");
				builder.clearAfter(params.referenceDate, "shop_other");
				builder.clearAfter(params.referenceDate, "visit");
				builder.clearAfter(params.referenceDate, "errands");
				builder.clearAfter(params.referenceDate, "business");
				builder.restrict(referenceDate, remainingFraction, DEFAULT_ACTIVITIES);
				break;

			case "masks0.6@pt&shop":

				double clothFraction = 0.;
				double surgicalFraction = 0.;

				if (params.runType.equals(RESTRICTED)) {
					clothFraction = 0.5;
					surgicalFraction = 0.1;
				}

				for (int i = 0; i<=14; i++) {
					builder.restrict(referenceDate.plusDays(i), Restriction.ofMask(Map.of(FaceMask.CLOTH, clothFraction, FaceMask.SURGICAL, surgicalFraction)), "pt", "shop_daily", "shop_other");
				}

				break;

			case "masks0.9@pt&shop":

				double n95Fraction = 0.;

				if (params.runType.equals(RESTRICTED)) {
					n95Fraction = 0.9;
				}

				for (int i = 0; i<=14; i++) {
					builder.restrict(referenceDate.plusDays(i), Restriction.ofMask(Map.of(FaceMask.CLOTH, 0., FaceMask.SURGICAL, 0., FaceMask.N95, n95Fraction)), "pt", "shop_daily", "shop_other");
				}

				break;

			case "masks0.9@work":
				if (params.runType.equals(RESTRICTED)) builder.restrict(referenceDate, Restriction.ofMask(Map.of(FaceMask.N95, 0.9)), "work");
				break;

			case "contactTracing50":
			{
				if (params.runType.equals(UNRESTRICTED)) {
					tracingConfig.setTracingCapacity_pers_per_day(Map.of(
							LocalDate.of(2020, 4, 1), 30,
							referenceDate, Integer.MAX_VALUE
					));
				}
				else {
					tracingConfig.setTracingCapacity_pers_per_day(Map.of(
							LocalDate.of(2020, 4, 1), 30,
							referenceDate, 0
					));
				}
				tracingConfig.setTracingProbability(0.5);
			}
				break;

			case "contactTracing75":
			{
				if (params.runType.equals(RESTRICTED)) {
					tracingConfig.setTracingCapacity_pers_per_day(Map.of(
							LocalDate.of(2020, 4, 1), 30,
							referenceDate, Integer.MAX_VALUE
					));
				}
				else {
					tracingConfig.setTracingCapacity_pers_per_day(Map.of(
							LocalDate.of(2020, 4, 1), 30,
							referenceDate, 0
					));
				}
				tracingConfig.setTracingProbability(0.75);


			}
				break;

			default:
				throw new IllegalArgumentException("Unknown intervention: " + params.intervention);
		}


		episimConfig.setPolicy(FixedPolicy.class, builder.build());


		return config;
	}

	public static final class Params {

		@GenerateSeeds(100)
		long seed;

		@StringParameter({"2020-04-20"})
		String referenceDate;

		@StringParameter({"ci0.32", "edu0", "edu50", "leisure50", "shopping50", "work50", "outOfHome50",
				"masks0.6@pt&shop", "masks0.9@pt&shop", "masks0.9@work", "contactTracing50", "contactTracing75"})
		String intervention;

		@StringParameter({RESTRICTED, UNRESTRICTED})
		String runType;

	}

}

package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinSuperSpreaderScenario;

import java.util.Map;


/**
 * This batch run explores restrictions on group sizes of activities.
 */
public class RestrictGroupSizes implements BatchRun<RestrictGroupSizes.Params> {


	@Override
	public AbstractModule getBindings(int id, Object params) {
		Params p = (Params) params;
		return new SnzBerlinSuperSpreaderScenario(25, p.sigma, p.sigma);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "groupSizes");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinSuperSpreaderScenario module = new SnzBerlinSuperSpreaderScenario(25, params.sigma, params.sigma);
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		config.global().setRandomSeed(params.seed);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		builder.clearAfter(params.referenceDate);
		//builder.clearAfter(params.referenceDate, "work", "leisure", "visit", "errands");

		if (params.containment.equals("GROUP_SIZES")) {

			Map<Double, Integer> work = Map.of(
					0.25, 72,
					0.50, 204,
					0.75, 568
			);
			builder.restrict(params.referenceDate, Restriction.ofGroupSize(work.get(params.remaining)), "work");


			Map<Double, Integer> leisure = Map.of(
					0.25, 140,
					0.50, 260,
					0.75, 500
			);
			builder.restrict(params.referenceDate, Restriction.ofGroupSize(leisure.get(params.remaining)), "leisure");

			Map<Double, Integer> visit = Map.of(
					0.25, 12,
					0.50, 24,
					0.75, 80
			);
			builder.restrict(params.referenceDate, Restriction.ofGroupSize(visit.get(params.remaining)), "visit");


			Map<Double, Integer> errands = Map.of(
					0.25, 112,
					0.50, 200,
					0.75, 416
			);
			builder.restrict(params.referenceDate, Restriction.ofGroupSize(errands.get(params.remaining)), "errands");

		} else if (params.containment.equals("UNIFORM")) {

			builder.restrict(params.referenceDate, Restriction.of(params.remaining), "work", "leisure", "visit", "errands");

		} else
			throw new IllegalStateException("Unknown containment");

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(15)
		long seed = 4711;

		@Parameter({0, 1, 1.5})
		double sigma;

		@Parameter({0.25, 0.5, 0.75})
		double remaining;

		@StringParameter({"GROUP_SIZES", "UNIFORM"})
		String containment;

		@StringParameter({"2020-03-07"})
		String referenceDate;

	}

}

package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.ContactModel;
import org.matsim.episim.model.DirectContactModel;
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.episim.model.SymmetricContactModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinSuperSpreaderScenario;

import java.util.Map;


/**
 * This batch run explores restrictions on group sizes of activities.
 */
public class RestrictGroupSizes implements BatchRun<RestrictGroupSizes.Params> {


	@Override
	public AbstractModule getBindings(int id, Params params) {
		return (AbstractModule) Modules.override(new SnzBerlinSuperSpreaderScenario(true, 30, params.sigma, params.sigma)).with(new AbstractModule() {
			@Override
			protected void configure() {
				if (params.contactModel.equals("OLD_SYMMETRIC"))
					bind(ContactModel.class).to(OldSymmetricContactModel.class).in(Singleton.class);
				else if (params.contactModel.equals("DIRECT"))
					bind(ContactModel.class).to(DirectContactModel.class).in(Singleton.class);
				else
					bind(ContactModel.class).to(SymmetricContactModel.class).in(Singleton.class);
			}
		});
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "groupSizes");
	}

	@Override
	public Config prepareConfig(int id, Params params) {


		SnzBerlinSuperSpreaderScenario module = new SnzBerlinSuperSpreaderScenario(true, 30, params.sigma, params.sigma);
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		config.global().setRandomSeed(params.seed);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		builder.clearAfter(params.referenceDate);
		//builder.clearAfter(params.referenceDate, "work", "leisure", "visit", "errands");

		// calib run 1 = sym / nspaces = 10
		// calib run 2 = old
		// calib run 3 = sym / nspaces = 1
		// calib run 4 = direct

		if (params.contactModel.equals("OLD_SYMMETRIC")) {
			episimConfig.setCalibrationParameter(9.42e-6);

		} else if (params.contactModel.equals("SYMMETRIC_N1")) {

			// should be 1.94e-5
			episimConfig.setCalibrationParameter(1.5e-4);
			episimConfig.getInfectionParams().forEach(p -> p.setSpacesPerFacility(1));

		} else if (params.contactModel.equals("SYMMETRIC_N10")) {

			// should be 2.25e-5
			episimConfig.setCalibrationParameter(1.75e-4);
			episimConfig.getInfectionParams()
					.stream()
					.filter(p -> !p.getContainerName().equals("home"))
					.forEach(p -> p.setSpacesPerFacility(10));

		} else if (params.contactModel.equals("DIRECT")) {
			episimConfig.setCalibrationParameter(1.2e-4);
		} else
			throw new IllegalStateException("Unknown contact model");


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

		@Parameter({0})
		double sigma;

		@Parameter({0.25, 0.5, 0.75})
		double remaining;

		@StringParameter({"GROUP_SIZES", "UNIFORM"})
		String containment;

		@StringParameter({"OLD_SYMMETRIC", "DIRECT", "SYMMETRIC_N10", "SYMMETRIC_N1"})
		String contactModel;

		@StringParameter({"2020-03-07"})
		String referenceDate;

	}

}

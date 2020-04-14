package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.RunEpisimSnz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SchoolClosure implements BatchRun<SchoolClosure.Params> {

	@Override
	public Config prepareConfig(int id, SchoolClosure.Params params) {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../he_snz_episim_events.xml.gz");
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.000002);

		RunEpisimSnz.addParams(episimConfig);

		episimConfig.getOrAddContainerParams("pt")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("tr")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("leisure")
				.setContactIntensity(5.0);
		episimConfig.getOrAddContainerParams("educ_kiga")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("educ_primary")
				.setContactIntensity(4.0);
		episimConfig.getOrAddContainerParams("educ_secondary")
				.setContactIntensity(2.0);
		episimConfig.getOrAddContainerParams("home")
				.setContactIntensity(3.0);

		com.typesafe.config.Config policyConf = FixedPolicy.config()
				.restrict(13 + params.offset, 0.9, "leisure")
				.restrict(13 + params.offset, 0.1, "educ_primary", "educ_kiga")
				.restrict(13 + params.offset, 0., "educ_secondary", "educ_higher")
				.restrict(13 + params.offset, params.remainingFractionLeisure, "leisure")
				.restrict(13 + params.offset, params.remainingFractionWork, "work")
				.restrict(13 + params.offset, params.remainingFractionShoppingBusinessErrands, "shopping", "errands", "business")
				.restrict(65 + params.offset, params.remainingFractionKiga, "educ_kiga")
				.restrict(65 + params.offset, params.remainingFractionPrima, "educ_primary")
				.restrict(65 + params.offset, params.remainingFractionSecon, "educ_secondary")
				.build();

		String policyFileName = "input/policy" + id + ".conf";
		episimConfig.setOverwritePolicyLocation(policyFileName);
		episimConfig.setPolicy(FixedPolicy.class, policyConf);

		config.plans().setInputFile("../he_entirePopulation_noPlans.xml.gz");

		return config;
	}

	@Override
	public void writeAuxiliaryFiles(Path directory, Config config) throws IOException {
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		Files.writeString(directory.resolve(episimConfig.getPolicyConfig()), episimConfig.getPolicy().root().render());
	}

	public static final class Params {

		@IntParameter({-5, 5})
		int offset;

		@Parameter({1.0, 0.5, 0.1})
		double remainingFractionKiga;

		@Parameter({1.0, 0.5, 0.1})
		double remainingFractionPrima;

		@Parameter({1.0, 0.5, 0.})
		double remainingFractionSecon;

		@Parameter({0.4, 0.2, 0})
		double remainingFractionLeisure;

		@Parameter({0.8, 0.4, 0.2})
		double remainingFractionWork;

		@Parameter({0.4, 0.2})
		double remainingFractionShoppingBusinessErrands;

	}

}

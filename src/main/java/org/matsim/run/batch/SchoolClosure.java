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

		episimConfig.setInputEventsFile("../snzDrt220a.0.events.reduced.xml.gz");
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.0000015);

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
				.restrict(26, 0.9, "leisure")
				.restrict(26, 0.1, "educ_primary", "educ_kiga")
				.restrict(26, 0., "educ_secondary", "educ_higher")
				.restrict(35, params.remainingFractionLeisure, "leisure")
				.restrict(35, params.remainingFractionWork, "work")
				.restrict(35, params.remainingFractionShopping, "shopping")
				.restrict(35, params.remainingFractionErrandsBusiness, "errands", "business")
				.restrict(63, params.remainingFractionKiga, "educ_kiga")
				.restrict(63, params.remainingFractionPrima, "educ_primary")
				.restrict(63, params.remainingFractionSecon, "educ_secondary")
				.restrict(63, params.remainingFractionHigher, "educ_higher")
				.build();

		String policyFileName = "policy" + id + ".conf";
		episimConfig.setOverwritePolicyLocation(policyFileName);
		episimConfig.setPolicy(FixedPolicy.class, policyConf);

		return config;
	}

	@Override
	public void writeAuxiliaryFiles(Path directory, Config config) throws IOException {
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		Files.writeString(directory.resolve(episimConfig.getPolicyConfig()), episimConfig.getPolicy().root().render());
	}

	public static final class Params {

		@Parameter({1.0, 0.5, 0.1})
		double remainingFractionKiga;

		@Parameter({1.0, 0.5, 0.1})
		double remainingFractionPrima;

		@Parameter({1.0, 0.5, 0.})
		double remainingFractionSecon;

		@Parameter({1.0, 0.5, 0.})
		double remainingFractionHigher;

		@Parameter({0.4, 0.2})
		double remainingFractionLeisure;

		@Parameter({0.4, 0.2})
		double remainingFractionWork;

		@Parameter({0.4, 0.2})
		double remainingFractionShopping;

		@Parameter({0.4, 0.2})
		double remainingFractionErrandsBusiness;

	}

}

package org.matsim.run.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;

import javax.inject.Singleton;

public class KnScenario extends AbstractModule {

	@Singleton
	@Provides
	public Config config() {
		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/snzDrt220a.0.events.reduced.xml.gz");
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);
		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.0000012);

		config.controler().setOutputDirectory("output-base-" + episimConfig.getCalibrationParameter());

		SnzScenario.addParams(episimConfig);

		SnzScenario.setContactIntensities(episimConfig);

		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config()
				.restrict(36, 0.1, "educ_kiga", "educ_primary")
				.restrict(36, 0.0, "educ_secondary", "educ_higher")
				.restrict(36, 0.9, "leisure")
				.build()
		);

		return config;
	}

}

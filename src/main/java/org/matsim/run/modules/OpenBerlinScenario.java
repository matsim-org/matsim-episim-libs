package org.matsim.run.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;

public class OpenBerlinScenario extends AbstractModule {

	/**
	 * Activity names of the default params from {@link #addDefaultParams(EpisimConfigGroup)}
	 */
	public static final String[] DEFAULT_ACTIVITIES = {
			"pt", "work", "leisure", "edu", "shop", "errands", "business", "other", "freight", "home"
	};

	/**
	 * Adds default parameters that should be valid for most scenarios.
	 */
	public static void addDefaultParams(EpisimConfigGroup config) {
		// pt
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("pt", "tr"));
		// regular out-of-home acts:
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("work"));
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("leisure", "leis"));
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("edu"));
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("shop"));
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("errands"));
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("business"));
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("other"));
		// freight act:
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("freight"));
		// home act:
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("home"));
	}

	@Provides
	@Singleton
	public Config config() {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		config.network().setInputFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-10pct/input/berlin-v5-network.xml.gz");

		// String episimEvents_1pct = "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct-schools/output-berlin-v5.4-1pct-schools/berlin-v5.4-1pct-schools.output_events_for_episim.xml.gz";
		String episimEvents_1pct = "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct/output-berlin-v5.4-1pct/berlin-v5.4-1pct.output_events_for_episim.xml.gz";
		String episimEvents_10pct = "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-10pct-schools/output-berlin-v5.4-10pct-schools/berlin-v5.4-10pct-schools.output_events_for_episim.xml.gz";

		episimConfig.setInputEventsFile(episimEvents_1pct);

		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.bln);
		episimConfig.setSampleSize(0.01);
		episimConfig.setCalibrationParameter(2);
		//  episimConfig.setOutputEventsFolder("events");

		long closingIteration = 14;

		addDefaultParams(episimConfig);

		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config()
				.shutdown(closingIteration, "leisure", "edu")
				.restrict(closingIteration, 0.2, "work", "business", "other")
				.restrict(closingIteration, 0.3, "shop", "errands")
				.restrict(closingIteration, 0.5, "pt")
				.open(closingIteration + 60, DEFAULT_ACTIVITIES)
				.build()
		);

		return config;
	}

}

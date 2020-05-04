package org.matsim.run.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;

import javax.inject.Singleton;

/**
 * Base class for a module containing the config for a snz scenario.
 * These are based on data provided by snz. Please note that this data is not publicly available.
 */
public abstract class AbstractSnzScenario extends AbstractModule {

	public static final String[] DEFAULT_ACTIVITIES = {
			"pt", "work", "leisure", "educ_kiga", "educ_primary", "educ_secondary", "educ_higher", "shopping", "errands", "business"
	};

	public static void setContactIntensities(EpisimConfigGroup episimConfig) {
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
	}

	public static void addParams(EpisimConfigGroup episimConfig) {

		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("pt", "tr"));
		// regular out-of-home acts:
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("work"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("leisure"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_kiga"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_primary"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_secondary"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_higher"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("shopping"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("errands"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("business"));

		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("home"));

	}


	/**
	 * Provider method that needs to be overwrite to generate fully configured scenario.
	 * Needs to be annotated with {@link Provides} and {@link Singleton}
	 */
	abstract public Config config();

	/**
	 * Creates a config with the default settings for all snz scenarios.
	 */
	protected Config getBaseConfig() {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.000_001_7);

		addParams(episimConfig);
		setContactIntensities(episimConfig);

		return config;
	}


}

package org.matsim.run.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.AgeDependentProgressionModel;
import org.matsim.episim.model.ProgressionModel;

import javax.inject.Singleton;

/**
 * Base class for a module containing the config for a snz scenario.
 * These are based on data provided by snz. Please note that this data is not publicly available.
 */
public abstract class AbstractSnzScenario2020 extends AbstractModule {

	public static final String[] DEFAULT_ACTIVITIES = {
			"work", "leisure", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_higher", "educ_other", "shop_daily", "shop_other", "visit", "errands", "business"
	};

//	public static void setContactIntensities(EpisimConfigGroup episimConfig) {
//		episimConfig.getOrAddContainerParams("pt")
//				.setContactIntensity(10.0);
//		episimConfig.getOrAddContainerParams("work")
//				.setContactIntensity(3.0);
//		episimConfig.getOrAddContainerParams("leisure")
//				.setContactIntensity(5.0);
//		episimConfig.getOrAddContainerParams("visit")
//				.setContactIntensity(5.0);
//		episimConfig.getOrAddContainerParams("educ_kiga")
//				.setContactIntensity(10.0);
//		episimConfig.getOrAddContainerParams("educ_primary")
//				.setContactIntensity(6.0);
//		episimConfig.getOrAddContainerParams("educ_secondary")
//				.setContactIntensity(4.0);
//		episimConfig.getOrAddContainerParams("educ_tertiary")
//				.setContactIntensity(3.0);
//		episimConfig.getOrAddContainerParams("educ_higher")
//				.setContactIntensity(3.0);
//		episimConfig.getOrAddContainerParams("educ_other")
//				.setContactIntensity(3.0);
//		episimConfig.getOrAddContainerParams("home")
//				.setContactIntensity(3.0 / 4.);
//		episimConfig.getOrAddContainerParams("quarantine_home")
//				.setContactIntensity(0.01);
//	}

	public static void addParams(EpisimConfigGroup episimConfig) {
		episimConfig.getOrAddContainerParams("pt", "tr")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("work")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("leisure")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("educ_kiga")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("educ_primary")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("educ_secondary")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("educ_tertiary")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("educ_higher")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("educ_other")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("shop_daily")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("shop_other")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("visit")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("errands")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("business")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("home")
				.setContactIntensity(1.)
				.setSpacesPerFacility(1.); // home facilities have already been split
		episimConfig.getOrAddContainerParams("quarantine_home")
				.setContactIntensity(1.);
	}

	@Override
	protected void configure() {

		// Use age dependent progression model
		bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);
		// WARNING: This does not affect runs with --config file, especially batch runs !!
	}

	/**
	 * Provider method that needs to be overwritten to generate fully configured scenario.
	 * Needs to be annotated with {@link Provides} and {@link Singleton}
	 */
	public abstract Config config();

	/**
	 * Creates a config with the default settings for all snz scenarios.
	 */
	protected static Config getBaseConfig() {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

		addParams(episimConfig);

		return config;
	}


}

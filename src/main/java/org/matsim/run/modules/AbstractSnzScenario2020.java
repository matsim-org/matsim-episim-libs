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
			"pt", "work", "leisure", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_higher", "educ_other", "shop_daily", "shop_other", "visit", "errands", "business"
	};

	public static void setContactIntensities(EpisimConfigGroup episimConfig) {
		episimConfig.getOrAddContainerParams("pt")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("tr")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("work")
				.setContactIntensity(2.0);
		episimConfig.getOrAddContainerParams("leisure")
				.setContactIntensity(5.0);
		episimConfig.getOrAddContainerParams("visit")
				.setContactIntensity(5.0);
		episimConfig.getOrAddContainerParams("educ_kiga")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("educ_primary")
				.setContactIntensity(6.0);
		episimConfig.getOrAddContainerParams("educ_secondary")
				.setContactIntensity(2.0);
		episimConfig.getOrAddContainerParams("educ_tertiary")
				.setContactIntensity(2.0);
		episimConfig.getOrAddContainerParams("educ_higher")
				.setContactIntensity(2.0);
		episimConfig.getOrAddContainerParams("educ_other")
				.setContactIntensity(2.0);
		episimConfig.getOrAddContainerParams("home")
				.setContactIntensity(3.0 / 4.);
		episimConfig.getOrAddContainerParams("quarantine_home")
				.setContactIntensity(3.0 / 4. / 3.);
	}

	public static void addParams(EpisimConfigGroup episimConfig) {

		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("pt", "tr"));
		// regular out-of-home acts:
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("work"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("leisure"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_kiga"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_primary"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_secondary"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_tertiary"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_higher"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_other"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("shop_daily"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("shop_other"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("visit"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("errands"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("business"));

		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("home"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("quarantine_home"));
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
	protected Config getBaseConfig() {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

		addParams(episimConfig);
		setContactIntensities(episimConfig);

		return config;
	}


}

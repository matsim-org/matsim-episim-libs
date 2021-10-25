package org.matsim.run.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;

import javax.inject.Singleton;

/**
 * Base class for a module containing the config for a snz scenario.
 * These are based on data provided by snz. Please note that this data is not publicly available.
 */
public abstract class AbstractSnzScenario extends AbstractModule {

	public static final String[] DEFAULT_ACTIVITIES = {
			"pt", "work", "leisure", "educ_kiga", "educ_primary", "educ_secondary", "educ_higher", "shopping", "errands", "business"
	};

	public static void addParams(EpisimConfigGroup episimConfig) {
		episimConfig.getOrAddContainerParams("pt", "tr")
				.setContactIntensity(10.0);

		episimConfig.getOrAddContainerParams("work");

		episimConfig.getOrAddContainerParams("leisure")
				.setContactIntensity(5.0);
		episimConfig.getOrAddContainerParams("educ_kiga")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("educ_primary")
				.setContactIntensity(4.0);
		episimConfig.getOrAddContainerParams("educ_secondary")
				.setContactIntensity(2.0);

		episimConfig.getOrAddContainerParams("educ_higher");
		episimConfig.getOrAddContainerParams("shopping");
		episimConfig.getOrAddContainerParams("errands");
		episimConfig.getOrAddContainerParams("business");

		episimConfig.getOrAddContainerParams("home")
				.setContactIntensity(3.0);
		episimConfig.getOrAddContainerParams("quarantine_home")
				.setContactIntensity(1.0);
	}

	@Override
	protected void configure() {

		// Use age dependent progression model
		bind(DiseaseStatusTransitionModel.class).to(AgeDependentDiseaseStatusTransitionModel.class).in(Singleton.class);
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

		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.000_002_8);

		addParams(episimConfig);

		return config;
	}


}

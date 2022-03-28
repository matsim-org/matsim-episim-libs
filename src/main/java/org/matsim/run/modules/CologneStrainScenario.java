package org.matsim.run.modules;

import com.google.inject.Provides;
import org.matsim.core.config.Config;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.inject.Singleton;

/**
 * Cologne scenario for calibration of different strains.
 */
public class CologneStrainScenario extends SnzCologneProductionScenario {


	public CologneStrainScenario() {
		super(new SnzCologneProductionScenario.Builder()
				// TODO: set all required optios
				.setScaleForActivityLevels(1.0)
		);

	}

	@Provides
	@Singleton
	public Config config() {

		Config config = super.config();

		// TODO: adapt config


		return config;
	}

}

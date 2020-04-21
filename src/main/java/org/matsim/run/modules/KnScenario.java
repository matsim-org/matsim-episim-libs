/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
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

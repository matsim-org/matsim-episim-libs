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

import com.google.inject.Provides;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;

import javax.inject.Singleton;

/**
 * Snz scenario for Heinsberg.
 *
 * @see AbstractSnzScenario
 */
public class SnzHeinsbergScenario extends AbstractSnzScenario {

	/**
	 * The base policy based on actual restrictions in the past and google mobility data.
	 */
	public static FixedPolicy.ConfigBuilder basePolicy(int offset) {

		return FixedPolicy.config()
				.restrict(11 - offset, 0.90, "work")
				.restrict(30 - offset, 0.40, "work")

				.restrict(11 - offset, 0., "educ_primary", "educ_kiga", "educ_secondary", "educ_higher")
				.restrict(24 - offset, 0.1, "educ_secondary")
				.restrict(30 - offset, 0.0, "educ_secondary")
				.restrict(65 - offset, 0.1, "educ_primary", "educ_kiga")
				.restrict(68 - offset, 0.1, "educ_secondary")
				.restrict(79 - offset, 0.2, "educ_secondary")

				.restrict(11 - offset, 0.60, "leisure")
				.restrict(30 - offset, 0.40, "leisure")
				.restrict(37 - offset, 0.10, "leisure")


				.restrict(11 - offset, 0.90, "shopping", "errands", "business")
				.restrict(32 - offset, 0.70, "shopping", "errands", "business");
	}

	@Provides
	@Singleton
	public Config config() {

		Config config = getBaseConfig();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg/episim-input/he_events_total.xml.gz");
		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg/episim-input/he_entirePopulation_noPlans.xml.gz");

		//episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/episim-input/he_small_snz_eventsForEpisim.xml.gz");
		//config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/episim-input/he_small_snz_populationWithDistrict.xml.gz");


		episimConfig.setInitialInfections(50);
		episimConfig.setInitialStartInfection(10);
		episimConfig.setInitialInfectionDistrict("Heinsberg");

		episimConfig.setPolicy(FixedPolicy.class, basePolicy(0).build());

		config.controler().setOutputDirectory("./output-heinsberg");


		return config;
	}

}

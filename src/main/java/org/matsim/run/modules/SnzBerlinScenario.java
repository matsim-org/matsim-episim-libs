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
import java.time.LocalDate;

/**
 * Scenario based on data provided by snz. Please note that this data is not publicly available.
 */
public class SnzBerlinScenario extends AbstractSnzScenario {

	/**
	 * The base policy based on actual restrictions in the past and google mobility data.
	 */
	public static FixedPolicy.ConfigBuilder basePolicy() {

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config()
				.interpolate("2020-03-08", "2020-03-22", Restriction.of(1), 0.45, "work")
				.interpolate("2020-03-08", "2020-03-22", Restriction.of(1), 0.1, "leisure")
				.interpolate("2020-03-15", "2020-03-22", Restriction.of(1), 0.5, "shopping", "errands", "business")

				//day 23 is the saturday 14th of march, so the weekend before schools got closed..
				.restrict("2020-03-14", 0.1, "educ_primary", "educ_kiga") // yyyy I thought that school closures started on day 26. --?? kai,
				.restrict("2020-03-14", 0., "educ_secondary", "educ_higher")

				.restrict("2020-05-04", 0.5, "educ_primary", "educ_kiga"); // 4/may.  Already "history" (on 30/apr).  :-)

		return builder;
	}

	@Provides
	@Singleton
	public Config config() {

		Config config = getBaseConfig();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_episim_events.xml.gz");
		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_entirePopulation_emptyPlans_withDistricts.xml.gz");

		episimConfig.setInitialInfections(50);
		episimConfig.setInitialInfectionDistrict("Berlin");

		episimConfig.setStartDate("2020-02-21");
		episimConfig.setPolicy(FixedPolicy.class, basePolicy().build());

		config.controler().setOutputDirectory("./output-berlinV2-google-progr");


		return config;
	}

}

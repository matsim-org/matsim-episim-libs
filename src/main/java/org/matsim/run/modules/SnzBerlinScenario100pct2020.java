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
 * Snz scenario for Berlin.
 *
 * @see AbstractSnzScenario
 */
public class SnzBerlinScenario100pct2020 extends AbstractSnzScenario2020 {

	/**
	 * The base policy based on actual restrictions in the past and google mobility data.
	 */
	public static FixedPolicy.ConfigBuilder basePolicy() {

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();
//				.interpolate("2020-03-07", "2020-03-14", Restriction.of(1), 0.8, "work")
//				.interpolate("2020-03-14", "2020-03-21", Restriction.of(0.8), 0.5, "work")
//				.interpolate("2020-03-21", "2020-03-28", Restriction.of(0.5), 0.45, "work")
//				.interpolate("2020-04-06", "2020-04-20", Restriction.of(0.45), 0.55, "work")
//
//				.interpolate("2020-03-15", "2020-03-29", Restriction.of(1), 0.1, "leisure")
//
//				.interpolate("2020-02-29", "2020-03-07", Restriction.of(1), 0.95, "shopping", "errands", "business")
//				.interpolate("2020-03-07", "2020-03-14", Restriction.of(0.95), 0.85, "shopping", "errands", "business")
//				.interpolate("2020-03-14", "2020-03-21", Restriction.of(0.85), 0.4, "shopping", "errands", "business")
//
//				//saturday 14th of march, so the weekend before schools got closed..
//				.restrict("2020-03-14", 0.1, "educ_primary", "educ_kiga") // yyyy I thought that school closures started on day 26. --?? kai,
//				.restrict("2020-03-14", 0., "educ_secondary", "educ_higher");

		return builder;
	}

	@Provides
	@Singleton
	public Config config() {

		Config config = getBaseConfig();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_episim_events_100pt.xml.gz");

		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_entirePopulation_emptyPlans_withDistricts.xml.gz");

		episimConfig.setInitialInfections(50);
		episimConfig.setInitialInfectionDistrict("Berlin");
		episimConfig.setSampleSize(1);
		episimConfig.setCalibrationParameter(0.000_001_0);

		episimConfig.setStartDate("2020-02-15");
		episimConfig.setPolicy(FixedPolicy.class, basePolicy().build());

		config.controler().setOutputDirectory("./output-berlin-100pct-" + episimConfig.getCalibrationParameter());


		return config;
	}

}

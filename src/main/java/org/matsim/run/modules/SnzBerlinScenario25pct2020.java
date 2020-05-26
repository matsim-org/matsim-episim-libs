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
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;

import java.io.File;
import java.io.IOException;

import javax.inject.Singleton;

/**
 * Snz scenario for Berlin.
 *
 * @see AbstractSnzScenario
 */
public class SnzBerlinScenario25pct2020 extends AbstractSnzScenario2020 {

	/**
	 * The base policy based on actual restrictions in the past and google mobility data.
	 */
	public static FixedPolicy.ConfigBuilder basePolicy() {

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config()
				.interpolate("2020-03-06", "2020-03-13", Restriction.of(1), Restriction.of(0.8), "work")
				.interpolate("2020-03-13", "2020-03-20", Restriction.of(0.8), Restriction.of(0.5), "work")
				.interpolate("2020-03-20", "2020-03-27", Restriction.of(0.5), Restriction.of(0.45), "work")
				.interpolate("2020-04-05", "2020-04-19", Restriction.of(0.45), Restriction.of(0.55), "work")
				.interpolate("2020-04-20", "2020-04-27", Restriction.of(0.55), Restriction.of(0.6), "work")

				.interpolate("2020-03-13", "2020-03-27", Restriction.of(1), Restriction.of(0.1), "leisure", "visit", "shop_other")
				.restrict("2020-04-27", Restriction.of(0.1, FaceMask.CLOTH), "shop_other")

				.interpolate("2020-02-28", "2020-03-06", Restriction.of(1), Restriction.of(0.95), "shop_daily", "errands", "business")
				.interpolate("2020-03-06", "2020-03-13", Restriction.of(0.95), Restriction.ofExposure(0.85), "shop_daily", "errands", "business")
				.interpolate("2020-03-13", "2020-03-20", Restriction.of(0.85), Restriction.of(0.4), "shop_daily", "errands", "business")
				.interpolate("2020-04-20", "2020-04-27", Restriction.of(0.4), Restriction.of(0.5), "shop_daily", "errands", "business")
				.interpolate("2020-04-28", "2020-05-04", Restriction.of(0.5, FaceMask.CLOTH), Restriction.of( 0.55), "shop_daily", "errands", "business")

				//saturday 14th of march, so the weekend before schools got closed..
				.restrict("2020-03-14", 0.1, "educ_primary", "educ_kiga")
				.restrict("2020-03-14", 0., "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")

				.restrict("2020-04-27", Restriction.of(1, FaceMask.CLOTH), "pt", "tr");

		return builder;
	}

	@Provides
	@Singleton
	public Config config() {

		Config config = getBaseConfig();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_episim_events_25pt.xml.gz");

		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz");

		episimConfig.setInitialInfections(50);
		episimConfig.setInitialInfectionDistrict("Berlin");
		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.000_002_0);
		episimConfig.setMaxInteractions(3);
		
		double alpha = 2.0;
		double exposure = 0.5;
		String startDate = "2020-02-11";
		String dateOfExposureChange = "2020-03-10";
		
		ConfigBuilder configBuilder = null;
		
		episimConfig.setStartDate(startDate);
		try {
			configBuilder = EpisimUtils.createRestrictionsFromCSV(episimConfig, new File("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/BerlinSnzData_daily_until20200517.csv"), alpha);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		configBuilder.restrict(dateOfExposureChange, Restriction.ofExposure(exposure), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
		configBuilder.restrict("2020-03-14", 0.1, "educ_primary", "educ_kiga")
			.restrict("2020-03-14", 0., "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		
		
		episimConfig.setPolicy(FixedPolicy.class, configBuilder.build());
		config.controler().setOutputDirectory("./output-berlin-25pct-restricts-" + alpha + "-" + exposure + "-" + dateOfExposureChange + "-" + episimConfig.getStartDate() + "-" + episimConfig.getCalibrationParameter());
//		config.controler().setOutputDirectory("./output-berlin-25pct-unrestricted-" + episimConfig.getCalibrationParameter());


		return config;
	}

}

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
import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

/**
 * Snz scenario for Berlin.
 *
 * @see AbstractSnzScenario
 */
public class SnzBerlinScenario25pct2020 extends AbstractSnzScenario2020 {

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

		double alpha = 1.4;
		double ciCorrection = 0.5;
		double clothMaskCompliance = 1./3.;
		double surgicalMaskCompliance = 1./6.;

		String startDate = "2020-02-11";
		String dateOfCiChange = "2020-03-10";

		ConfigBuilder configBuilder = new ConfigBuilder();

		episimConfig.setStartDate(startDate);
		try {
//			configBuilder = EpisimUtils.createRestrictionsFromCSV(episimConfig, new File("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/BerlinSnzData_daily_until20200517.csv"), alpha);
			configBuilder = EpisimUtils.createRestrictionsFromCSV2(episimConfig, new File("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/BerlinSnzData_daily_until20200517.csv"), alpha);
		} catch (IOException e) {
			e.printStackTrace();
		}

		configBuilder.restrict(dateOfCiChange, Restriction.ofCiCorrection(ciCorrection), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
		configBuilder.restrict(dateOfCiChange, Restriction.ofCiCorrection(ciCorrection), "pt");
				
		configBuilder.restrict("2020-03-14", 0.1, "educ_primary", "educ_kiga")
			.restrict("2020-03-14", 0., "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
			//.restrict("2020-04-27", Restriction.ofMask(FaceMask.CLOTH, clothMaskCompliance), AbstractSnzScenario2020.DEFAULT_ACTIVITIES)
			.restrict("2020-04-27", Restriction.ofMask(Map.of(FaceMask.CLOTH, clothMaskCompliance, FaceMask.SURGICAL, surgicalMaskCompliance)), AbstractSnzScenario2020.DEFAULT_ACTIVITIES)
			.restrict("2020-04-27", Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.6, FaceMask.SURGICAL, 0.3)), "pt", "shop_daily", "shop_other")
			;

		episimConfig.setPolicy(FixedPolicy.class, configBuilder.build());
		config.controler().setOutputDirectory("./output-berlin-25pct-SNZrestrictsFromCSV-" + alpha + "-" + ciCorrection + "-" + dateOfCiChange + "-" + clothMaskCompliance + "-" + surgicalMaskCompliance + "-" + episimConfig.getStartDate() + "-" + episimConfig.getCalibrationParameter());
//		config.controler().setOutputDirectory("./output-berlin-25pct-unrestricted-calibr-" + episimConfig.getCalibrationParameter());


		return config;
	}

}

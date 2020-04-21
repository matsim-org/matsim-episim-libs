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

public class SnzScenario extends AbstractModule {

	public static final String[] DEFAULT_ACTIVITIES = {
			"pt", "work", "leisure", "educ_kiga", "educ_primary", "educ_secondary", "educ_higher", "shopping", "errands", "business", "home"
	};

	public static void setContactIntensities(EpisimConfigGroup episimConfig) {
		episimConfig.getOrAddContainerParams("pt")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("tr")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("leisure")
				.setContactIntensity(5.0);
		episimConfig.getOrAddContainerParams("educ_kiga")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("educ_primary")
				.setContactIntensity(4.0);
		episimConfig.getOrAddContainerParams("educ_secondary")
				.setContactIntensity(2.0);
		episimConfig.getOrAddContainerParams("home")
				.setContactIntensity(3.0);
	}

	public static void addParams(EpisimConfigGroup episimConfig) {

		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("pt", "tr"));
		// regular out-of-home acts:
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("work"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("leisure"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_kiga"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_primary"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_secondary"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("educ_higher"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("shopping"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("errands"));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("business"));

		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("home"));

	}

	@Provides
	@Singleton
	public Config config() {
		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
//		config.plans().setInputFile("../berlin_pop_populationAttributes.xml.gz");

//		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/Berlin/episim-input/be_entirePopulation_noPlans.xml.gz");
//		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/Munich/episim-input/mu_entirePopulation_noPlans.xml.gz");
		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg/episim-input/he_entirePopulation_noPlans.xml.gz");
//		config.plans().setInputFile("./output/filteredPopulation.xml.gz");

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg/episim-input/he_events_total.xml.gz");
//		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/Munich/episim-input/mu_snz_episim_events.xml.gz");
//		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/Berlin/episim-input/be_snz_episim_events.xml.gz");
//		episimConfig.setInputEventsFile("/Users/sebastianmuller/Documents/episim-original-data/20200410_CH2019.25pct.run1.output_events.reducedForEpisim.xml.gz");


		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.000002);
		//episimConfig.setPutTracablePersonsInQuarantine(EpisimConfigGroup.PutTracablePersonsInQuarantine.yes);

		addParams(episimConfig);

		setContactIntensities(episimConfig);
		int a = -5;
		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config()
				.restrict(26-a, 0.9, "leisure")
				.restrict(26-a, 0.1, "educ_primary", "educ_kiga")
				.restrict(26-a, 0., "educ_secondary", "educ_higher")
				.restrict(35-a, 0.2, "leisure")
				.restrict(35-a, 0.6, "work")
				.restrict(35-a, 0.4, "shopping")
				.restrict(35-a, 0.4, "errands", "business")
				.restrict(63-a, 1, "educ_kiga")
				.restrict(63-a, 1, "educ_primary")
				.restrict(63-a, 1, "educ_secondary")
				.restrict(63-a, 0, "educ_higher")
				.build()
		);

//		RunEpisim.setOutputDirectory(config);
		config.controler().setOutputDirectory("./output-belin-reopenSchoolsAndKiga" + -a);

		return config;
	}
}

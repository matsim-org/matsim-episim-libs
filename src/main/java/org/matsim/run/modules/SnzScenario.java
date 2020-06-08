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
import org.matsim.episim.policy.Restriction;

import javax.inject.Singleton;

/**
 * Scenario based on data provided by snz. Please note that this data is not publicly available.
 * @deprecated Please move scenarios into separate modules and extend from {@link AbstractSnzScenario}.
 */
@Deprecated
public class SnzScenario extends AbstractModule {

	public static final String[] DEFAULT_ACTIVITIES = {
			"pt", "work", "leisure", "educ_kiga", "educ_primary", "educ_secondary", "educ_higher", "shopping", "errands", "business"
	};

	public static void setContactIntensities(EpisimConfigGroup episimConfig) {
		episimConfig.getOrAddContainerParams("pt")
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
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		int offset = -6;

		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.000_001_7);
		addParams(episimConfig);
		setContactIntensities(episimConfig);


//		prepareRunMunich(config, episimConfig, offset);
//		prepareRunBerlin(config, episimConfig, offset);
		prepareRunBerlinV2(config, episimConfig, offset);
//		prepareRunHeinsberg(config, episimConfig, offset);
//		prepareRunHeinsbergSmall(config, episimConfig, offset);

		return config;
	}

	private void prepareRunMunich(Config config, EpisimConfigGroup episimConfig, int offset) {
		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/Munich/episim-input/mu_snz_episim_events.xml.gz");
		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/Munich/episim-input/mu_entirePopulation_noPlans_withDistricts.xml.gz");

		episimConfig.setInitialInfections(50);
		episimConfig.setInitialInfectionDistrict("München");
		episimConfig.setPolicy(FixedPolicy.class, buildPolicyMunich(offset)	);
		config.controler().setOutputDirectory("./output-munich-" + offset);
	}

	public static void prepareRunBerlinV2(Config config, EpisimConfigGroup episimConfig, int offset) {
		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_episim_events.xml.gz");
		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_entirePopulation_emptyPlans_withDistricts.xml.gz");

		episimConfig.setInitialInfections(50);
		episimConfig.setInitialInfectionDistrict("Berlin");
		episimConfig.setPolicy(FixedPolicy.class, buildPolicyBerlin(offset)	);
		config.controler().setOutputDirectory("./output-berlinV2-google-progr" + offset);
	}

	private void prepareRunBerlin(Config config, EpisimConfigGroup episimConfig, int offset) {
		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/Berlin/episim-input/be_snz_episim_events.xml.gz");
		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/Berlin/episim-input/be_entirePopulation_noPlans_withDistricts.xml.gz");

		episimConfig.setInitialInfections(50);
		episimConfig.setInitialInfectionDistrict("Berlin");
		episimConfig.setPolicy(FixedPolicy.class, buildPolicyBerlin(offset)	);
		config.controler().setOutputDirectory("./output-berlin-" + offset);
	}

	private void prepareRunHeinsberg(Config config, EpisimConfigGroup episimConfig, int offset) {
		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg/episim-input/he_events_total.xml.gz");
		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg/episim-input/he_entirePopulation_noPlans.xml.gz");

		episimConfig.setInitialInfections(50);
		episimConfig.setInitialStartInfection(10);
		episimConfig.setInitialInfectionDistrict("Heinsberg");
		episimConfig.setPolicy(FixedPolicy.class, buildPolicyHeinsberg(offset)	);
		config.controler().setOutputDirectory("./output-heinsberg-" + offset);
	}
	private void prepareRunHeinsbergSmall(Config config, EpisimConfigGroup episimConfig, int offset) {
		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/episim-input/he_small_snz_eventsForEpisim.xml.gz");
		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/episim-input/he_small_snz_populationWithDistrict.xml.gz");

		episimConfig.setInitialInfections(50);
		episimConfig.setInitialStartInfection(10);
		episimConfig.setInitialInfectionDistrict("Heinsberg");
		episimConfig.setPolicy(FixedPolicy.class, buildPolicyHeinsberg(offset)	);
		config.controler().setOutputDirectory("./output-heinsbergSmall4-" + offset);
	}

	private com.typesafe.config.Config buildPolicyHeinsberg(int offset) {

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
				.restrict(32 - offset, 0.70, "shopping", "errands", "business")
				.build();
	}

	/**
	 * Use newer {@link FixedPolicy.ConfigBuilder#interpolate(String, String, Restriction, double, String...)} instead.
	 */
	@Deprecated
	public static class LinearInterpolation{
		private final long firstDay;
		private final double firstValue;
		private final long lastDay;
		private final double lastValue;
		public LinearInterpolation( long firstDay, double firstValue, long lastDay, double lastValue ) {
			this.firstDay = firstDay;
			this.firstValue = firstValue;
			this.lastDay = lastDay;
			this.lastValue = lastValue;
		}
		public double getValue( long day ) {
			return firstValue + (lastValue-firstValue)*(day-firstDay)/(lastDay-firstDay);
			// corner cases:
			// * day=firstDay --> fraction=firstValue
			// * day=lastDay --> fraction=lastValue
		}
	}

	/* package private for a test */ static com.typesafe.config.Config buildPolicyBerlin(int offset){
		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();
		{
			final int firstDay = 17 - offset; // 2 days earlier than previously
			final int lastDay = 31 - offset; // 2 days earlier than previously
			LinearInterpolation interpolation = new LinearInterpolation( firstDay, 1., lastDay, 0.45 );
			for( int day = firstDay ; day <= lastDay ; day++ ){
				builder.restrict( day, interpolation.getValue( day ), "work" );
			}
		}
		{
			final int firstDay = 17 - offset;
			final int lastDay = 31 - offset;
			LinearInterpolation interpolation = new LinearInterpolation( firstDay, 1., lastDay, 0.1 );
			for( int day = firstDay ; day <= lastDay ; day++ ){
				builder.restrict( day, interpolation.getValue( day ), "leisure" );
			}
		}
		{
			final int firstDay = 24 - offset;
			final int lastDay = 31 - offset;
			LinearInterpolation interpolation = new LinearInterpolation( firstDay, 1., lastDay, 0.5 );
			for( int day = firstDay ; day <= lastDay ; day++ ){
				builder.restrict( day, interpolation.getValue( day ), "shopping", "errands", "business" );
			}
		}

//		builder.restrict(15 - offset, 0.95, "shopping", "errands", "business")
//				.open(17 - offset, "shopping", "errands", "business")
//				.restrict(19 - offset, 0.95, "shopping", "errands", "business")
//				.open(20 - offset, "shopping", "errands", "business")
		// (kann man so machen, aber dann müssten wir Wochenend-Effekte auch in der base calibration (theta) drin haben.  kai, may'20)

//				.restrict(26 - offset, 0.95, "shopping", "errands", "business")
//				.restrict(27 - offset, 0.85, "shopping", "errands", "business")
//				.restrict(28 - offset, 0.75, "shopping", "errands", "business")
//				.restrict(29 - offset, 0.7, "shopping", "errands", "business")
//				.restrict(31 - offset, 0.65, "shopping", "errands", "business")
//				.restrict(33 - offset, 0.6, "shopping", "errands", "business")
//				.restrict(35 - offset, 0.65, "shopping", "errands", "business")
//				.restrict(36 - offset, 0.6, "shopping", "errands", "business")
//				.restrict(40 - offset, 0.65, "shopping", "errands", "business")
//				.restrict(45 - offset, 0.7, "shopping", "errands", "business")
//				.restrict(46 - offset, 0.75, "shopping", "errands", "business")
//				.restrict(48 - offset, 0.8, "shopping", "errands", "business")
//				.restrict(49 - offset, 0.85, "shopping", "errands", "business")
//				.restrict(50 - offset, 0.75, "shopping", "errands", "business")
//				.restrict(55 - offset, 0.7, "shopping", "errands", "business")

		builder
				//day 23 is the saturday 14th of march, so the weekend before schools got closed..
				.restrict(23 - offset, 0.1, "educ_primary", "educ_kiga") // yyyy I thought that school closures started on day 26. --?? kai,
				 // apr'20
				.restrict(23 - offset, 0., "educ_secondary", "educ_higher")
				.restrict(74 - offset, 0.5, "educ_primary", "educ_kiga") // 4/may.  Already "history" (on 30/apr).  :-)
		       ;
		return builder.build();
	}

	private com.typesafe.config.Config buildPolicyMunich(int offset) {
		return FixedPolicy.config()

				//holidays
				.restrict(1 - offset, 0.95, "work")
				.restrict(2 - offset, 0.9, "work")
				.restrict(3 - offset, 0.85, "work")
				.restrict(4 - offset, 0.75, "work")
				.restrict(6 - offset, 0.85, "work")
				.restrict(9 - offset, 0.9, "work")
				.restrict(10 - offset, 0.95, "work")
				.restrict(11 - offset, 1.0, "work")

				//..
				.restrict(21 - offset, 0.95, "work")
				.restrict(23 - offset, 0.9, "work")
				.restrict(24 - offset, 0.85, "work")
				.restrict(25 - offset, 0.8, "work")
				.restrict(26 - offset, 0.75, "work")
				.restrict(27 - offset, 0.7, "work")
				.restrict(28 - offset, 0.65, "work")
				.restrict(30 - offset, 0.6, "work")
				.restrict(32 - offset, 0.55, "work")

				//other
				.restrict(6 - offset, 0.95, "shopping", "errands", "business")
				.open(7 - offset, "shopping", "errands", "business")
				.restrict(14 - offset, 0.95, "shopping", "errands", "business")
				.open(15 - offset, "shopping", "errands", "business")
				.restrict(19 - offset, 0.95, "shopping", "errands", "business")
				.open(20 - offset, "shopping", "errands", "business")
				.restrict(26 - offset, 0.90, "shopping", "errands", "business")
				.restrict(27 - offset, 0.80, "shopping", "errands", "business")
				.restrict(28 - offset, 0.75, "shopping", "errands", "business")
				.restrict(29 - offset, 0.80, "shopping", "errands", "business")
				.restrict(30 - offset, 0.70, "shopping", "errands", "business")
				.restrict(31 - offset, 0.65, "shopping", "errands", "business")
				.restrict(32 - offset, 0.55, "shopping", "errands", "business")
				.restrict(36 - offset, 0.6, "shopping", "errands", "business")
				.restrict(44 - offset, 0.65, "shopping", "errands", "business")
				.restrict(46 - offset, 0.7, "shopping", "errands", "business")
				.restrict(48 - offset, 0.75, "shopping", "errands", "business")
				.restrict(49 - offset, 0.8, "shopping", "errands", "business")
				.restrict(51 - offset, 0.70, "shopping", "errands", "business")
				.restrict(55 - offset, 0.65, "shopping", "errands", "business")

				//leisure
				.restrict(18 - offset, 0.94, "leisure")
				.restrict(19 - offset, 0.87, "leisure")
				.restrict(20 - offset, 0.81, "leisure")
				.restrict(21 - offset, 0.74, "leisure")
				.restrict(22 - offset, 0.68, "leisure")
				.restrict(23 - offset, 0.61, "leisure")
				.restrict(24 - offset, 0.55, "leisure")
				.restrict(25 - offset, 0.49, "leisure")
				.restrict(26 - offset, 0.42, "leisure")
				.restrict(27 - offset, 0.36, "leisure")
				.restrict(28 - offset, 0.29, "leisure")
				.restrict(29 - offset, 0.23, "leisure")
				.restrict(30 - offset, 0.16, "leisure")
				.restrict(31 - offset, 0.1, "leisure")
				//day 23 is the saturday 14th of march, so the weekend before schools got closed..
				.restrict(23 - offset, 0.1, "educ_primary", "educ_kiga")
				.restrict(23 - offset, 0., "educ_secondary", "educ_higher")
				.build();
	}

}

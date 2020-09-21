/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.run.modules;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import javax.inject.Singleton;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.EpisimConfigGroup.InfectionParams;
import org.matsim.episim.model.AgeDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.AgeDependentProgressionModel;
import org.matsim.episim.model.ContactModel;
import org.matsim.episim.model.InfectionModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.vehicles.VehicleType;

import com.google.inject.Provides;

/**
* @author smueller
*/

public class SnzBerlinWeekScenario2020Symmetric extends AbstractSnzScenario2020  {
	/**
	 * Sample size of the scenario (Either 25 or 100)
	 */
	private final int sample;

	public SnzBerlinWeekScenario2020Symmetric() {
		this(25);
	}

	public SnzBerlinWeekScenario2020Symmetric(int sample) {
		this.sample = sample;
	}

	@Override
	protected void configure() {
		bind(ContactModel.class).to(OldSymmetricContactModel.class).in(Singleton.class);
		bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);
		bind(InfectionModel.class).to(AgeDependentInfectionModelWithSeasonality.class).in(Singleton.class);
	}

	@Provides
	@Singleton
	public Config config() {

		Config config = new SnzBerlinScenario25pct2020().config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.clearInputEventsFiles();

		config.plans().setInputFile(SnzBerlinScenario25pct2020.INPUT.resolve(String.format(
				"be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_%dpt_split.xml.gz", sample)).toString());

		episimConfig.addInputEventsFile(SnzBerlinScenario25pct2020.INPUT.resolve(String.format(
				"be_2020-week_snz_episim_events_wt_%dpt_split.xml.gz", sample)).toString())
				.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		episimConfig.addInputEventsFile(SnzBerlinScenario25pct2020.INPUT.resolve(String.format(
				"be_2020-week_snz_episim_events_sa_%dpt_split.xml.gz", sample)).toString())
				.addDays(DayOfWeek.SATURDAY);

		episimConfig.addInputEventsFile(SnzBerlinScenario25pct2020.INPUT.resolve(
				String.format("be_2020-week_snz_episim_events_so_%dpt_split.xml.gz", sample)).toString())
				.addDays(DayOfWeek.SUNDAY);

		if (sample == 100) {
			throw new RuntimeException("100pct scenario not configured");
		}

		episimConfig.setCalibrationParameter(9.e-6);
		episimConfig.setStartDate("2020-02-18");

		episimConfig.setInitialInfectionDistrict("Berlin");
		episimConfig.setInitialInfections(Integer.MAX_VALUE);

		for( InfectionParams infParams : episimConfig.getInfectionParams() ){
			if ( infParams.includesActivity( "home" ) ){
				infParams.setContactIntensity( 1. );
			} else if ( infParams.includesActivity( "quarantine_home" ) ) {
				infParams.setContactIntensity( 0.3 );
			} else if ( infParams.getContainerName().startsWith( "shop" ) ) {
				infParams.setContactIntensity( 0.88 );
			} else if ( infParams.includesActivity( "work" ) || infParams.includesActivity(
					"business" ) || infParams.includesActivity( "errands" ) ) {
				infParams.setContactIntensity( 1.47 );
			} else if ( infParams.getContainerName().startsWith( "edu" ) ) {
				infParams.setContactIntensity( 11. );
			} else if ( infParams.includesActivity( "pt" ) || infParams.includesActivity( "tr" )) {
				infParams.setContactIntensity( 10. );
			} else if ( infParams.includesActivity( "leisure" ) || infParams.includesActivity( "visit" ) ) {
				infParams.setContactIntensity( 9.24 );
			} else {
				throw new RuntimeException( "need to define contact intensity for activityType=" + infParams.getContainerName() );
			}
		}

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		// The following is, I think, the ci correction that we need around mar/6 in order to get the RKI infection peak right.  kai, sep/20
		builder.restrict("2020-03-07", Restriction.ofCiCorrection(0.6), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
		builder.restrict("2020-03-07", Restriction.ofCiCorrection(0.6), "quarantine_home");
		builder.restrict("2020-03-07", Restriction.ofCiCorrection(0.6), "pt");

		// yyyyyy why this? Could you please comment?  kai, sep/20
		builder.restrict("2020-08-08", Restriction.ofCiCorrection(0.6 * 0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30,
				LocalDate.of(2020, 6, 1), Integer.MAX_VALUE
		));

		//episimConfig.setWriteEvents(EpisimConfigGroup.WriteEvents.tracing);

		config.controler().setOutputDirectory(config.controler().getOutputDirectory() + "-week-" + episimConfig.getCalibrationParameter());

		return config;
	}

	@Provides
	@Singleton
	public Scenario scenario(Config config) {

		// guice will use no args constructor by default, we check if this config was initialized
		// this is only the case when no explicit binding are required
		if (config.getModules().size() == 0)
			throw new IllegalArgumentException("Please provide a config module or binding.");

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);

		// save some time for not needed inputs
		config.facilities().setInputFile(null);

		ControlerUtils.checkConfigConsistencyAndWriteToLog( config, "before loading scenario" );

		final Scenario scenario = ScenarioUtils.loadScenario( config );

		double capFactor = 1.3;

		for( VehicleType vehicleType : scenario.getVehicles().getVehicleTypes().values() ){
			switch( vehicleType.getId().toString() ) {
				case "bus":
					vehicleType.getCapacity().setSeats( (int) (70 * capFactor));
					vehicleType.getCapacity().setStandingRoom( (int) (40 * capFactor) );
					// https://de.wikipedia.org/wiki/Stadtbus_(Fahrzeug)#Stehpl%C3%A4tze
					break;
				case "metro":
					vehicleType.getCapacity().setSeats( (int) (200 * capFactor) );
					vehicleType.getCapacity().setStandingRoom( (int) (550 * capFactor) );
					// https://mein.berlin.de/ideas/2019-04585/#:~:text=Ein%20Vollzug%20der%20Baureihe%20H,mehr%20Stehpl%C3%A4tze%20zur%20Verf%C3%BCgung%20stehen.
					break;
				case "plane":
					vehicleType.getCapacity().setSeats( (int) (200 * capFactor) );
					vehicleType.getCapacity().setStandingRoom( (int) (0 * capFactor) );
					break;
				case "pt":
					vehicleType.getCapacity().setSeats( (int) (70 * capFactor) );
					vehicleType.getCapacity().setStandingRoom( (int) (70 * capFactor) );
					break;
				case "ship":
					vehicleType.getCapacity().setSeats( (int) (150 * capFactor) );
					vehicleType.getCapacity().setStandingRoom( (int) (150 * capFactor) );
					// https://www.berlin.de/tourismus/dampferfahrten/faehren/1824948-1824660-faehre-f10-wannsee-altkladow.html
					break;
				case "train":
					vehicleType.getCapacity().setSeats( (int) (250 * capFactor) );
					vehicleType.getCapacity().setStandingRoom( (int) (750 * capFactor) );
					// https://de.wikipedia.org/wiki/Stadler_KISS#Technische_Daten_der_Varianten , mehr als ICE (https://inside.bahn.de/ice-baureihen/)
					break;
				case "tram":
					vehicleType.getCapacity().setSeats( (int) (84 * capFactor) );
					vehicleType.getCapacity().setStandingRoom( (int) (216 * capFactor) );
					// https://mein.berlin.de/ideas/2019-04585/#:~:text=Ein%20Vollzug%20der%20Baureihe%20H,mehr%20Stehpl%C3%A4tze%20zur%20Verf%C3%BCgung%20stehen.
					break;
				default:
					throw new IllegalStateException( "Unexpected value=|" + vehicleType.getId().toString() + "|");
			}
		}

		return scenario;
	}
}

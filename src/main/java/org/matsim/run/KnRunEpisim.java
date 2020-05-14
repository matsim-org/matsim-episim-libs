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
package org.matsim.run;

import com.google.inject.Module;
import com.google.inject.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.*;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.reporting.AsyncEpisimWriter;
import org.matsim.episim.reporting.EpisimWriter;
import org.matsim.run.modules.AbstractSnzScenario;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzScenario;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public class KnRunEpisim {
	private static final Logger log = LogManager.getLogger( KnRunEpisim.class );

	private static final boolean verbose = false;
	private static final boolean logToOutput = true;

	public static void main(String[] args) throws IOException{

		OutputDirectoryLogging.catchLogEntries();

		if (!verbose) {
			Configurator.setLevel("org.matsim.core.config", Level.WARN);
			Configurator.setLevel("org.matsim.core.controler", Level.WARN);
			Configurator.setLevel("org.matsim.core.events", Level.WARN);
		}

		List<Module> modules = new ArrayList<>();
		modules.add( new AbstractModule(){
			@Override
			protected void configure() {

				binder().requireExplicitBindings();

				// Main model classes regarding progression / infection etc..
				bind( InfectionModel.class ).to(DefaultInfectionModel.class ).in( Singleton.class );
				bind( ProgressionModel.class ).to( AgeDependentProgressionModel.class ).in( Singleton.class );
				bind( FaceMaskModel.class ).to( DefaultFaceMaskModel.class ).in( Singleton.class );

				// Internal classes, should rarely be needed to be reconfigured
				bind(EpisimRunner.class).in( Singleton.class );
				bind( ReplayHandler.class ).in( Singleton.class );
				bind( InfectionEventHandler.class ).in( Singleton.class );
				bind( EpisimReporting.class ).in( Singleton.class );

			}
			@Provides
			@Singleton
			public Scenario scenario( Config config ) {

				// guice will use no args constructor by default, we check if this config was initialized
				// this is only the case when no explicit binding are required
				if (config.getModules().size() == 0)
					throw new IllegalArgumentException("Please provide a config module or binding.");

				config.vspExperimental().setVspDefaultsCheckingLevel( VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn );

				// save some time for not needed inputs
				config.facilities().setInputFile(null);
				config.vehicles().setVehiclesFile(null);

				return ScenarioUtils.loadScenario(config );
			}
			@Provides
			@Singleton
			public EpisimConfigGroup episimConfigGroup(Config config) {
				return ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
			}
			@Provides
			@Singleton
			public TracingConfigGroup tracingConfigGroup( Config config ) {
				return ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
			}
			@Provides
			@Singleton
			public EpisimWriter episimWriter( EpisimConfigGroup episimConfig ) {

				// Async writer is used for huge event number
				if (Runtime.getRuntime().availableProcessors() > 1 && episimConfig.getWriteEvents() != EpisimConfigGroup.WriteEvents.episim)
					// by default only one episim simulation is running
					return new AsyncEpisimWriter(1);
				else
					return new EpisimWriter();
			}
			@Provides
			@Singleton
			public EventsManager eventsManager() {
				return EventsUtils.createEventsManager();
			}
			@Provides
			@Singleton
			public SplittableRandom splittableRandom( Config config ) {
				return new SplittableRandom(config.global().getRandomSeed());
			}
		} );
		modules.add( new AbstractModule(){
			@Provides
			@Singleton
			public Config config() {
				Config config = ConfigUtils.createConfig(new EpisimConfigGroup() );
				EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

				episimConfig.setWriteEvents( EpisimConfigGroup.WriteEvents.episim );

				episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

				episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_episim_events_25pt.xml.gz" );
				config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz" );

				episimConfig.setInitialInfections(50 );
				episimConfig.setInitialInfectionDistrict("Berlin" );

				episimConfig.setStartDate( LocalDate.of( 2020, 2, 18) );

				AbstractSnzScenario2020.addParams(episimConfig );

				AbstractSnzScenario2020.setContactIntensities(episimConfig );

				// I had originally thought that one could calibrate the offset by shifting the unrestricted growth left/right until it hits
				// the hospital numbers.  Turns out that this does not work since the early leisure participation reductions already
				// influence this.  So the problem now is that this interacts. Consequence: rather fix the support days, and only change the
				// participation rates.

				episimConfig.setCalibrationParameter(0.000_001_6);
				episimConfig.getOrAddContainerParams("home").setContactIntensity(1.);

				// day 1 is 21/feb+offset.  In consequence:

				// * 5/mar is day 14+offset (first day of leisure linear reduction)
				// * 15/mar is day 24+offset (first day of "Veranstaltungsverbot" = Sunday)
				// * 20/mar is day 29+offset (last day of leisure linear reduction)

				// * 10/mar is day 19+offset (first day of work linear reduction)
				// * 23/mar is day 32+offset (last day of work linear reduction)

				// * errands etc. are reduced with same days as leisure

				// * 14/mar is day 23+offset (first day of school closures = Saturday)


				FixedPolicy.ConfigBuilder builder = FixedPolicy.config();

				final double workMid = 0.75;
				final double workEnd = 0.45;
//				final double workEnd = workMid;

				final double leisureMid = 0.7;
				final double leisureEnd = 0.1;
//				final double leisureEnd = leisureMid;

				final double eduLower = 0.1;
//				final double eduLower = 1.;
//
				final double eduHigher = 0.0 ;
//				final double eduHigher = 1.;

				final double other = 0.2;

				final LocalDate midDateLeisure = LocalDate.of( 2020, 3,16 );
				{ // leisure 1:
					final LocalDate startDate = LocalDate.of( 2020, 3, 5 ); // Do der letzten Woche, an der ich noch regulär im Büro war
					builder.interpolate( startDate, midDateLeisure, Restriction.of( 1. ), leisureMid, "leisure", "visit","shop_other" );
				}
				{ // errands
					final LocalDate startDate = LocalDate.of( 2020, 3, 5 ); // (retail in google is combined with leisure!)
					final LocalDate endDate = LocalDate.of( 2020, 3, 29 ); // leisure change last day
					builder.interpolate( startDate, endDate, Restriction.of( 1. ), other, "shopping", "errands", "business",
							"shop_daily", "shop_other" ) ;
				}
				final LocalDate midDateWork = LocalDate.of( 2020, 3, 16 );
				{ // work 1:
					final LocalDate startDate = LocalDate.of( 2020, 3, 10 ); // (ab Mo, 9.3. habe ich weitgehend home office gemacht)
					builder.interpolate( startDate, midDateWork, Restriction.of( 1. ), workMid, "work" );

				}
				// ===========================================
				{ // edu:
					builder.restrict( LocalDate.of( 2020, 3, 14), eduLower, "educ_primary", "educ_kiga" ) // = Samstag vor Schulschliessungen
					       .restrict( LocalDate.of( 2020, 3, 14 ), eduHigher, "educ_secondary", "educ_higher", "educ_other", "educ_tertiary" )
//					       .restrict(74-offset, 0.5, "educ_primary", "educ_kiga") // 4/may.  Already "history" (on 30/apr).  :-)
					;
				}
				{ // work 2:
					final LocalDate endDate = LocalDate.of( 2020, 3, 23 );
					builder.interpolate( midDateWork, endDate, Restriction.of( workMid ), workEnd,"work" );
				}
				{ // leisure 2:
					final LocalDate endDate = LocalDate.of( 2020, 3, 20 );
					builder.interpolate( midDateLeisure, endDate, Restriction.of( leisureMid ), leisureEnd, "leisure", "visit" );
				}
				episimConfig.setPolicy( FixedPolicy.class, builder.build() );
				episimConfig.setSampleSize(0.25);

//				episimConfig.getOrAddContainerParams("home").setContactIntensity(3.);
//				episimConfig.setCalibrationParameter(0.000_001_7);

//				episimConfig.getOrAddContainerParams("home").setContactIntensity(10.);
//				episimConfig.setCalibrationParameter(0.000_001_1);

//				episimConfig.getOrAddContainerParams("home").setContactIntensity(1.);
//				episimConfig.setCalibrationParameter(0.000_002_4); // maybe should have been 2_3

				StringBuilder strb = new StringBuilder();
				strb.append( "piecewise" );
				strb.append( "__theta" + episimConfig.getCalibrationParameter() );
//				strb.append( "_ciHome" + episimConfig.getOrAddContainerParams( "home" ).getContactIntensity() );
				strb.append( "__startDate_" + episimConfig.getStartDate() );
				strb.append( "__work_" + workMid + "_" + workEnd );
				strb.append( "__leis_" + leisureMid + "_" + leisureEnd );
				strb.append( "__eduLower_"  + eduLower);
				strb.append( "__eduHigher_" + eduHigher );
				strb.append( "__other" + other );
				config.controler().setOutputDirectory( strb.toString() );

//				config.controler().setOutputDirectory( "base-theta" + episimConfig.getCalibrationParameter() );

				return config;
			}

		});

		log.info( "Starting with modules: {}", modules );

		Injector injector = Guice.createInjector(modules);

		RunEpisim.printBindings( injector );

		Config config = injector.getInstance(Config.class);

		if (logToOutput) OutputDirectoryLogging.initLoggingWithOutputDirectory(config.controler().getOutputDirectory());

		EpisimRunner runner = injector.getInstance(EpisimRunner.class);
		runner.run(100);

		if (logToOutput) OutputDirectoryLogging.closeOutputDirLogging();

	}

}

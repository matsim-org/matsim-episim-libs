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
import org.matsim.episim.reporting.AsyncEpisimWriter;
import org.matsim.episim.reporting.EpisimWriter;
import org.matsim.run.modules.SnzScenario;
import org.matsim.run.modules.SnzScenario.LinearInterpolation;

import java.io.IOException;
import java.util.*;

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

				episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_episim_events.xml.gz" );
				config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_entirePopulation_emptyPlans_withDistricts.xml.gz" );

				episimConfig.setInitialInfections(50 );
				episimConfig.setInitialInfectionDistrict("Berlin" );

				SnzScenario.addParams(episimConfig );

				SnzScenario.setContactIntensities(episimConfig );

				// I had originally thought that one could calibrate the offset by shifting the unrestricted growth left/right until it hits
				// the hospital numbers.  Turns out that this does not work since the early leisure participation reductions already
				// influence this.  So the problem now is that this interacts. Consequence: rather fix the support days, and only change the
				// participation rates.

				episimConfig.setCalibrationParameter(0.000_002_8);
				final int offset = -5; // offset -6 now means first day = 15/feb (to set in python code)
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

//				final double eduLower = 0.1;
				final double eduLower = 1.;
//
//				final double eduHigher = 0.0 ;
				final double eduHigher = 1.;

				final double other = 0.2;

//				final double workMid = 1.;
//				final double workEnd = 1.;
//
//				final double leisureMid = 1.;
//				final double leisureEnd = 1.;
//
//				final double eduLower = 1.;
//				final double eduHigher = 1.;
//
//				final double other = 1.;

				{ // leisure 1:
					final int firstDay = 14-offset; // "14-offset" ist sozusagen das Ende der letzten Woche, in der ich noch regulär im Büro war
					final int lastDay = 25-offset;
					LinearInterpolation interpolation = new LinearInterpolation( firstDay, 1., lastDay, leisureMid );
					for( int day = firstDay ; day <= lastDay ; day++ ){
						builder.restrict( day, interpolation.getValue( day ), "leisure" );
					}
				}
				{ // errands
					final int firstDay = 14-offset; // (retail in google is combined with leisure!)
					final int lastDay = 29-offset; // leisure change last day
					LinearInterpolation interpolation = new LinearInterpolation( firstDay, 1., lastDay, other );
					for( int day = firstDay ; day <= lastDay ; day++ ){
						builder.restrict( day, interpolation.getValue( day ), "shopping", "errands", "business" );
					}
				}
				{ // work 1:
					final int firstDay = 19-offset; // ("18-offset" ist der Montag, ab dem ich weitgehend home office gemacht habe (9.3.))
					final int lastDay = 25-offset;
					LinearInterpolation interpolation = new LinearInterpolation( firstDay, 1., lastDay, workMid );
					for( int day = firstDay ; day <= lastDay ; day++ ){
						builder.restrict( day, interpolation.getValue( day ), "work" );
					}
				}
				// ===========================================
				{ // edu:
					builder.restrict( 23-offset, eduLower, "educ_primary", "educ_kiga" ) // "23-offset" = Samstag vor Schulschliessungen = 14.3.
					       .restrict( 23-offset, eduHigher, "educ_secondary", "educ_higher" )
//					       .restrict(74-offset, 0.5, "educ_primary", "educ_kiga") // 4/may.  Already "history" (on 30/apr).  :-)
					;
				}
				{ // work 2:
					final int firstDay = 25-offset;
					final int lastDay = 32-offset;
					LinearInterpolation interpolation = new LinearInterpolation( firstDay, workMid, lastDay, workEnd );
					for( int day = firstDay ; day <= lastDay ; day++ ){
						builder.restrict( day, interpolation.getValue( day ), "work" );
					}
				}
				{ // leisure 2:
					final int firstDay = 25-offset;
					final int lastDay = 29-offset;
					LinearInterpolation interpolation = new LinearInterpolation( firstDay, leisureMid, lastDay, leisureEnd );
					for( int day = firstDay ; day <= lastDay ; day++ ){
						builder.restrict( day, interpolation.getValue( day ), "leisure" );
					}
				}
//				{
//					final int firstDay = 54-offset;
//					final int lastDay = 62-offset;
//					LinearInterpolation interpolation = new LinearInterpolation( firstDay, work, lastDay, 0.55 );
//					for( int day = firstDay ; day <= lastDay ; day++ ){
//						builder.restrict( day, interpolation.getValue( day ), "work" );
//					}
//				}
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
				strb.append( "__offset" + offset );
//				strb.append( "_ciHome" + episimConfig.getOrAddContainerParams( "home" ).getContactIntensity() );
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

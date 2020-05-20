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
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

import static org.matsim.episim.EpisimPerson.*;
import static org.matsim.episim.model.Transition.logNormalWithMeanAndStd;
import static org.matsim.episim.model.Transition.logNormalWithMedianAndStd;

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

		/*
		 * 2020-05-20 RKI:
		 *
		 * Inkubationszeit 5-6 Tage (da wir im Mittel bei 0.5 anstecken und bei Übergang 5 -> 6 Symptome entwickeln, hätten wir das genau)
		 *
		 * Serielles Intervall 4 Tage.  Daraus folgt eigentlich fast zwangsläufig, dass die meisten Ansteckungen zwischen Tag 3 und Tag 5 nach
		 * Ansteckung stattfinden.
		 *
		 * Symptome bis Hospitalisierung: 4 bis 7
		 *
		 * Symptome bis Intensiv:
		 */

		boolean usingMeans = false ;

		final double infectedToContag = 4.; // orig 4
		final double infectedBNCStd = 2.;

		final double contagToSymptoms = 2.; // orig 2
		final double contagiousStd = 1.;

		// ---

		final double SymptomsToSSick = 8.; // orig 4; RKI 7; DAe 4;
		final double withSymptomsStd = 0.;

		final double sStickToCritical = 2.; // 3.; // orig 1; RKI ?; DAe 5
		final double seriouslySickStd = 0.;

		final double criticalToBetter = 10.; // orig 9
		final double criticalStd = 0.;

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

				episimConfig.setStartDate( LocalDate.of( 2020, 2, 12) );

				SnzBerlinScenario25pct2020.addParams(episimConfig );

				SnzBerlinScenario25pct2020.setContactIntensities(episimConfig );

				episimConfig.setCalibrationParameter(0.000_000_7);
//				episimConfig.getOrAddContainerParams("home" ).setContactIntensity( 0.3 );
				episimConfig.getOrAddContainerParams( AbstractInfectionModel.QUARANTINE_HOME ).setContactIntensity( 0.01 );


				// ---

				FixedPolicy.ConfigBuilder builder = FixedPolicy.config();

				// I had originally thought that one could calibrate the offset by shifting the unrestricted growth left/right until it hits
				// the hospital numbers.  Turns out that this does not work since the early leisure participation reductions already
				// influence this.  So the problem now is that this interacts. Consequence: fix the support days, and only change the
				// participation rates.

				boolean unrestricted = false ;

				final double alpha = 1.2;

				double workMid;
				double workEnd;
				double leisureMid1;
				double leisureMid2Base;
				double leisureMid2;
				double leisureEnd;
				double eduLower;
				double eduHigher;
				if ( unrestricted ){
					workMid = workEnd = 1.;
					leisureMid1 = 1.;
					leisureMid2Base = 1.;
					leisureMid2 = 1.;
					leisureEnd = 1;
					eduLower = 1.;
					eduHigher = 1.;
				} else{
					workMid = Math.max( 0., 1. - alpha * 0.25 );
					workEnd = Math.max( 0., 1. - alpha * 0.55 );

					leisureMid1 = Math.max( 0., 1. - alpha * 0.2 );
					leisureMid2Base = 0.4;
					leisureMid2 = Math.max( 0., 1. - alpha * leisureMid2Base );
					leisureEnd = Math.max( 0., 1. - alpha * 0.6 );

					eduLower = 0.1;

					eduHigher = 0.0;
				}

				final LocalDate midDateLeisure = LocalDate.of( 2020, 3,14 ); // Schliessung von Clubs, Bars, Kneipen
				final LocalDate midDateWork = LocalDate.of( 2020, 3, 16 );

				{ // leisure 1:
					final LocalDate startDate = LocalDate.of( 2020, 3, 5 ); // Do der letzten Woche, an der ich noch regulär im Büro war
					builder.interpolate( startDate, midDateLeisure, Restriction.of( 1. ), leisureMid1, "leisure", "visit","shop_other" );
				}
				{ // work 1:
					final LocalDate startDate = LocalDate.of( 2020, 3, 10 ); // (ab Mo, 9.3. habe ich weitgehend home office gemacht)
					builder.interpolate( startDate, midDateWork, Restriction.of( 1. ), workMid, "work","errands","business","shop_daily" );

				}
				// ===========================================
				{ // edu:
					builder.restrict( LocalDate.of( 2020, 3, 14), eduLower, "educ_primary", "educ_kiga" ) // = Samstag vor Schulschliessungen
					       .restrict( LocalDate.of( 2020, 3, 14 ), eduHigher, "educ_secondary", "educ_higher", "educ_other", "educ_tertiary" )
//					       .restrict(74-offset, 0.5, "educ_primary", "educ_kiga") // 4/may.  Already "history" (on 30/apr).  :-)
					;
				}
				// ===========================================
				{ // leisure 2:
					final LocalDate endDate = LocalDate.of( 2020, 3, 20 );
					builder.interpolate( midDateLeisure, endDate, Restriction.of( leisureMid2 ), leisureEnd, "leisure", "visit", "shop_other" );
				}
				{ // work 2:
					final LocalDate endDate = LocalDate.of( 2020, 3, 23 );
					builder.interpolate( midDateWork, endDate, Restriction.of( workMid ), workEnd,"work","errands","business","shop_daily");
				}
				episimConfig.setPolicy( FixedPolicy.class, builder.build() );
				episimConfig.setSampleSize(0.25);

				StringBuilder strb = new StringBuilder();
				strb.append( "theta" ).append( episimConfig.getCalibrationParameter() );
				strb.append( "__ciHome" ).append( episimConfig.getOrAddContainerParams( "home" ).getContactIntensity() );
				strb.append( "__ciQHome" ).append( episimConfig.getOrAddContainerParams( "quarantine_home" ).getContactIntensity() );
				strb.append( "__startDate_" ).append( episimConfig.getStartDate() );
				if ( unrestricted ) {
					strb.append( "__unrestricted" );
				} else{
					strb.append( "__alpha_" + alpha );
				}
//				strb.append( "__work_" + workMid + "_" + workEnd );
//				strb.append( "__leis_" + leisureMid + "_" + leisureEnd );
//				strb.append( "__eduLower_"  + eduLower);
//				strb.append( "__eduHigher_" + eduHigher );
//				strb.append( "__other" + other );
				strb.append( "__leisMid2Base_" ).append( new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.US)).format( leisureMid2Base ) );
//				strb.append("__midDateLeisure_").append( midDateLeisure );
				strb.append( "__infectedBNC_" ).append( infectedToContag ).append( "_" ).append( infectedBNCStd );
				strb.append( "__contag_" ).append( contagToSymptoms ).append( "_" ).append( contagiousStd );
				strb.append( "__wSymp_" ).append( SymptomsToSSick ).append( "_" ).append( withSymptomsStd );
				strb.append( "__sSick_" ).append( sStickToCritical ).append( "_" ).append( seriouslySickStd );
				strb.append( "__crit_" ).append( criticalToBetter ).append( "_" ).append( criticalStd );
				if ( usingMeans ) {
					strb.append( "__usingMeans" );
				}
				config.controler().setOutputDirectory( strb.toString() );

				return config;
			}

		});

		log.info( "Starting with modules: {}", modules );

		Injector injector = Guice.createInjector(modules);

		RunEpisim.printBindings( injector );

		Config config = injector.getInstance(Config.class);

		if (logToOutput) OutputDirectoryLogging.initLoggingWithOutputDirectory(config.controler().getOutputDirectory());

		ProgressionModel pm = injector.getInstance( ProgressionModel.class );
		if ( pm instanceof DefaultProgressionModel ) {
			final DefaultProgressionModel defaultProgressionModel = (DefaultProgressionModel) pm;
			if ( usingMeans ){
				defaultProgressionModel.setTransition( DiseaseStatus.infectedButNotContagious, logNormalWithMeanAndStd( infectedToContag, infectedBNCStd ) );
				defaultProgressionModel.setTransition( DiseaseStatus.contagious, logNormalWithMeanAndStd( contagToSymptoms, contagiousStd ) );
				defaultProgressionModel.setTransition( DiseaseStatus.showingSymptoms, logNormalWithMeanAndStd( SymptomsToSSick, withSymptomsStd ) );
				defaultProgressionModel.setTransition( DiseaseStatus.seriouslySick, logNormalWithMeanAndStd( sStickToCritical, seriouslySickStd ) );
				defaultProgressionModel.setTransition( DiseaseStatus.critical, logNormalWithMeanAndStd( criticalToBetter, criticalStd ) );
			} else {
				defaultProgressionModel.setTransition( DiseaseStatus.infectedButNotContagious, logNormalWithMedianAndStd( infectedToContag, infectedBNCStd ) );
				defaultProgressionModel.setTransition( DiseaseStatus.contagious, logNormalWithMedianAndStd( contagToSymptoms, contagiousStd ) );
				defaultProgressionModel.setTransition( DiseaseStatus.showingSymptoms, logNormalWithMedianAndStd( SymptomsToSSick, withSymptomsStd ) );
				defaultProgressionModel.setTransition( DiseaseStatus.seriouslySick, logNormalWithMedianAndStd( sStickToCritical, seriouslySickStd ) );
				defaultProgressionModel.setTransition( DiseaseStatus.critical, logNormalWithMedianAndStd( criticalToBetter, criticalStd ) );
			}
		}

		EpisimRunner runner = injector.getInstance(EpisimRunner.class);
		runner.run(150);

		if (logToOutput) OutputDirectoryLogging.closeOutputDirLogging();

	}

}

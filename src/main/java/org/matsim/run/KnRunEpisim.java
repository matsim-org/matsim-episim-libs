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

import com.google.common.base.Joiner;
import com.google.inject.Module;
import com.google.inject.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.events.EventsUtils;
<<<<<<< HEAD
import org.matsim.core.scenario.ScenarioUtils;
||||||| constructed merge base
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
=======
import org.matsim.core.utils.io.IOUtils;
>>>>>>> changes in kn scripts
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.episim.*;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.reporting.AsyncEpisimWriter;
import org.matsim.episim.reporting.EpisimWriter;
import org.matsim.run.modules.OpenBerlinScenario;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;
<<<<<<< HEAD
||||||| constructed merge base
import org.matsim.run.modules.SnzBerlinWeekScenario2020Symmetric;
import org.matsim.vehicles.VehicleType;
=======
import org.matsim.run.modules.SnzBerlinWeekScenario2020Symmetric;
>>>>>>> changes in kn scripts

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static java.lang.Math.max;
import static org.matsim.episim.EpisimConfigGroup.*;
import static org.matsim.episim.EpisimUtils.*;

public class KnRunEpisim {
	public static final String SUSCEPTIBILITY = "susceptibility";
	public static final String VIRAL_LOAD = "viralLoad";

	private static final Logger log = LogManager.getLogger( KnRunEpisim.class );

	private static final boolean verbose = false;
	private static final boolean logToOutput = true;

	// ===

	private enum ScenarioType{ openBln1pct, snzBerlinWeek2020Symmetric}
	private static final ScenarioType scenarioType = ScenarioType.openBln1pct;

	private enum ContactModelType{ original, oldSymmetric, symmetric, sqrt, direct }
	private static final ContactModelType contactModelType = ContactModelType.oldSymmetric;

	enum RestrictionsType {unrestr, triang, fromSnz, fromConfig }
	private static final RestrictionsType restrictionsType = RestrictionsType.unrestr;

	private static final double SIGMA_INFECT = 0.;
	// 2 leads to dynamics so unstable that it does not look plausible w.r.t. reality.  kai, jun'20

	private static final double SIGMA_SUSC = 0.;

	private static final int DISEASE_IMPORT_OFFSET = -0;

	// ===

	public static void main(String[] args) throws IOException{

		OutputDirectoryLogging.catchLogEntries();

		if (!verbose) {
			Configurator.setLevel("org.matsim.core.config", Level.WARN);
			Configurator.setLevel("org.matsim.core.controler", Level.WARN);
			Configurator.setLevel("org.matsim.core.events", Level.WARN);
		}

		List<Module> modules = new ArrayList<>();
		modules.add( new AbstractModule(){
			@Override protected void configure() {

				binder().requireExplicitBindings();

				// Main model classes regarding progression / infection etc..
				switch( contactModelType ) {
					case original:
						bind( ContactModel.class ).to( DefaultContactModel.class ).in( Singleton.class );
						break;
					case symmetric:
						bind( ContactModel.class ).to( SymmetricContactModel.class ).in( Singleton.class );
						break;
					case oldSymmetric:
						bind( ContactModel.class ).to( OldSymmetricContactModel.class ).in( Singleton.class );
						break;
					case sqrt:
						bind( ContactModel.class ).to( SqrtContactModel.class ).in( Singleton.class );
						break;
					case direct:
						bind( ContactModel.class ).to( DirectContactModel.class ).in( Singleton.class );
						break;
					default:
						throw new IllegalStateException( "Unexpected value: " + contactModelType );
				}
				if ( scenarioType == ScenarioType.openBln1pct ) {
					bind( InfectionModel.class ).to( DefaultInfectionModel.class ).in( Singleton.class );
					bind( ProgressionModel.class ).to( ConfigurableProgressionModel.class ).in( Singleton.class );
					bind( FaceMaskModel.class ).to( DefaultFaceMaskModel.class ).in( Singleton.class );
					bind( InitialInfectionHandler.class ).to( RandomInitialInfections.class ).in( Singleton.class );
				} else {
					bind( InfectionModel.class ).to( AgeDependentInfectionModelWithSeasonality.class ).in( Singleton.class );
					bind( ProgressionModel.class ).to( AgeDependentProgressionModel.class ).in( Singleton.class );
					bind( FaceMaskModel.class ).to( DefaultFaceMaskModel.class ).in( Singleton.class );
					bind( InitialInfectionHandler.class ).to( RandomInitialInfections.class ).in( Singleton.class );
				}

				// Internal classes, should rarely be needed to be reconfigured
				bind(EpisimRunner.class).in( Singleton.class );
				bind( ReplayHandler.class ).in( Singleton.class );
				bind( InfectionEventHandler.class ).in( Singleton.class );
				bind( EpisimReporting.class ).in( Singleton.class );

			}
			@Provides @Singleton public Scenario scenario( Config config ) {

<<<<<<< HEAD
				Scenario scenario = new SnzBerlinWeekScenario2020().scenario( config );
||||||| constructed merge base
				Scenario scenario = new SnzBerlinWeekScenario2020Symmetric().scenario( config );
=======
				Scenario scenario ;
				switch( scenarioType ) {
					case openBln1pct:
						scenario = new SnzBerlinWeekScenario2020Symmetric().scenario( config ); // try using this one
						break;
					case snzBerlinWeek2020Symmetric:
						scenario = new SnzBerlinWeekScenario2020Symmetric().scenario( config );
						break;
					default:
						throw new IllegalStateException( "Unexpected value: " + scenarioType );
				}
>>>>>>> changes in kn scripts

				SplittableRandom rnd = new SplittableRandom( 4715 );
				for( Person person : scenario.getPopulation().getPersons().values() ){
					person.getAttributes().putAttribute( VIRAL_LOAD, nextLogNormalFromMeanAndSigma( rnd, 1, SIGMA_INFECT ) );
					person.getAttributes().putAttribute( SUSCEPTIBILITY, nextLogNormalFromMeanAndSigma( rnd, 1, SIGMA_SUSC ) );
				}

				return scenario;
			}
			@Provides @Singleton public EpisimConfigGroup epsimConfig( Config config ) {
				return ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
			}
			@Provides @Singleton public TracingConfigGroup tracingConfig( Config config ) {
				return ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
			}
			@Provides @Singleton public EpisimWriter episimWriter( EpisimConfigGroup episimConfig ) {

				// Async writer is used for huge event number
				if (Runtime.getRuntime().availableProcessors() > 1 && episimConfig.getWriteEvents() != WriteEvents.episim)
					// by default only one episim simulation is running
					return new AsyncEpisimWriter(1);
				else
					return new EpisimWriter();
			}
			@Provides @Singleton public EventsManager eventsManager() {
				return EventsUtils.createEventsManager();
			}
			@Provides @Singleton public SplittableRandom splittableRandom( Config config ) {
				return new SplittableRandom(config.global().getRandomSeed());
			}
			@Provides @Singleton public Config config(){
				Config config ;
				EpisimConfigGroup episimConfig ;

				// NOTE: The dynamics is set by the guice bindings above; this here just configures parameters that have something to do with those different dynamics.
				if( contactModelType == ContactModelType.original ){
					if ( scenarioType == ScenarioType.snzBerlinWeek2020Symmetric ){
						config = new SnzBerlinWeekScenario2020().config();
						episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
						episimConfig.setCalibrationParameter( 1.e-5 );
						episimConfig.setStartDate( "2020-02-17" );
						episimConfig.setMaxContacts( 3. );
					} else if ( scenarioType == ScenarioType.openBln1pct ) {
						config = new OpenBerlinScenario().config();
						episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
						episimConfig.setCalibrationParameter( 1.e-5 );
						episimConfig.setStartDate( "2020-02-17" );
						episimConfig.setMaxContacts( 3. );
					} else {
						throw new RuntimeException( "not implemented" );
					}
				} else if( contactModelType == ContactModelType.oldSymmetric ){
					if ( scenarioType == ScenarioType.snzBerlinWeek2020Symmetric ){
						config = new SnzBerlinWeekScenario2020Symmetric().config();
						config.global().setRandomSeed( 4711 );
						episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
						episimConfig.setStartDate( "2020-02-18" );
						episimConfig.setCalibrationParameter( 5.e-6 );
					} else if ( scenarioType == ScenarioType.openBln1pct ) {
						config = new OpenBerlinScenario().config();
						config.global().setRandomSeed( 4711 );
						episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
						episimConfig.setStartDate( "2020-02-18" );
						episimConfig.setCalibrationParameter( 5.e-4 );
					} else {
						throw new RuntimeException( "not implemented" );
					}
				} else if( contactModelType == ContactModelType.symmetric ){
<<<<<<< HEAD
					config = new SnzBerlinWeekScenario2020().config();
					episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
					episimConfig.setStartDate( "2020-02-18" );
					episimConfig.setCalibrationParameter( 2.1e-5 );
					episimConfig.setMaxContacts( Double.NaN ); // interpreted as "typical number of interactions"
||||||| constructed merge base
					config = new SnzBerlinWeekScenario2020Symmetric().config();
					episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
					episimConfig.setStartDate( "2020-02-18" );
					episimConfig.setCalibrationParameter( 2.1e-5 );
					episimConfig.setMaxContacts( Double.NaN ); // interpreted as "typical number of interactions"
=======
					if ( scenarioType == ScenarioType.snzBerlinWeek2020Symmetric ){
						config = new SnzBerlinWeekScenario2020Symmetric().config();
						episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
						episimConfig.setStartDate( "2020-02-18" );
						episimConfig.setCalibrationParameter( 2.1e-5 );
						episimConfig.setMaxContacts( Double.NaN );

						// to save computing time:
						TracingConfigGroup tracingConfig = tracingConfig( config );
						tracingConfig.setTracingCapacity_pers_per_day( 0 );
						tracingConfig.setPutTraceablePersonsInQuarantineAfterDay( Integer.MAX_VALUE );
					} else if ( scenarioType == ScenarioType.openBln1pct ) {
						config = new OpenBerlinScenario().config();
						config.global().setRandomSeed( 4711 );
						episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
						episimConfig.setStartDate( "2020-02-18" );
						episimConfig.setCalibrationParameter( 5.e-6 );
					} else {
						throw new RuntimeException( "not implemented" );
					}
>>>>>>> changes in kn scripts
				} else if( contactModelType == ContactModelType.sqrt ){
<<<<<<< HEAD
					config = new SnzBerlinWeekScenario2020().config();
					episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
					episimConfig.setStartDate( "2020-02-13" );
					episimConfig.setCalibrationParameter( 1.e-5 );
					episimConfig.setMaxContacts( 10 ); // interpreted as "typical number of interactions"
||||||| constructed merge base
					config = new SnzBerlinWeekScenario2020Symmetric().config();
					episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
					episimConfig.setStartDate( "2020-02-13" );
					episimConfig.setCalibrationParameter( 1.e-5 );
					episimConfig.setMaxContacts( 10 ); // interpreted as "typical number of interactions"
=======
					if ( scenarioType== ScenarioType.snzBerlinWeek2020Symmetric ){
						config = new SnzBerlinWeekScenario2020Symmetric().config();
						episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
						episimConfig.setStartDate( "2020-02-13" );
						episimConfig.setCalibrationParameter( 1.e-5 );
						episimConfig.setMaxContacts( 10 ); // interpreted as "typical number of interactions"
					} else {
						throw new RuntimeException( "not implemented" );
					}
>>>>>>> changes in kn scripts
				} else if( contactModelType == ContactModelType.direct ){
					config = new SnzBerlinWeekScenario2020().config();
					episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
					episimConfig.setStartDate( "2020-02-17" );
					episimConfig.setCalibrationParameter( 1.2e-5 );
				} else{
					throw new RuntimeException( "not implemented for infectionModelType=" + contactModelType );
				}

				// derzeit proba_interact = maxIA/sqrt(containerSize).  Konsequenzen:
				// * wenn containerSize < maxIA, dann IA deterministisch.  Vermutl. kein Schaden.
				// * wenn containerSize gross, dann  theta und maxIA multiplikativ und somit redundant.
				// Ich werde jetzt erstmal maxIA auf das theta des alten Modells kalibrieren.  Aber perspektivisch
				// könnte man (wie ja auch schon vorher) maxIA plausibel festlegen, und dann theta kalibrieren.

				TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

				episimConfig.setWriteEvents( WriteEvents.episim );

				// ---

//				tracingConfig.setTracingCapacity_per_day( Integer.MAX_VALUE );
//				tracingConfig.setTracingCapacity_pers_per_day( 0 );

				// ---

				StringBuilder strb = new StringBuilder();
				strb.append( LocalDateTime.now().format( DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss" ) ) );
				strb.append( "__" ).append( contactModelType.name() );
				strb.append( "__" ).append( restrictionsType.name() );
				strb.append( "__theta" ).append( episimConfig.getCalibrationParameter() ).append( "@" ).append( episimConfig.getMaxContacts() );
				if ( SIGMA_INFECT !=0. ) strb.append( "__sInfct" ).append( SIGMA_INFECT );
				if ( SIGMA_SUSC !=0. ) strb.append( "__sSusc" ).append( SIGMA_SUSC );

				if ( restrictionsType==RestrictionsType.fromConfig ) {
					// do nothing
				} else if ( restrictionsType==RestrictionsType.fromSnz ){
					SnzBerlinScenario25pct2020.BasePolicyBuilder basePolicyBuilder = new SnzBerlinScenario25pct2020.BasePolicyBuilder( episimConfig );
					basePolicyBuilder.setCiCorrections( Map.of("2020-03-07", 0.25 ));
					basePolicyBuilder.setAlpha( 1. );

					FixedPolicy.ConfigBuilder restrictions = basePolicyBuilder.build();
					episimConfig.setPolicy(FixedPolicy.class, restrictions.build());

					strb.append( "_ciCorr" ).append(Joiner.on("_").withKeyValueSeparator("@").join(basePolicyBuilder.getCiCorrections()));
					strb.append( "_alph" ).append( basePolicyBuilder.getAlpha() );

				} else if ( restrictionsType==RestrictionsType.unrestr ) {
					final FixedPolicy.ConfigBuilder restrictions = FixedPolicy.config();
					episimConfig.setPolicy( FixedPolicy.class, restrictions.build() ); // overwrite snz policy with empty policy
					tracingConfig.setTracingCapacity_pers_per_day( 0 );
				}

				strb.append( "_seed" ).append( config.global().getRandomSeed() );
				strb.append( "_strtDt" ).append( episimConfig.getStartDate() );
				strb.append( "_imprtOffst" ).append( DISEASE_IMPORT_OFFSET );
				if ( !tracingConfig.getTracingCapacity().isEmpty() ) {
					strb.append( "_trCap" ).append( tracingConfig.getTracingCapacity() );
					strb.append( "_quStrt" ).append( episimConfig.getStartDate().plusDays( tracingConfig.getPutTraceablePersonsInQuarantineAfterDay() ) );
				}
				config.controler().setOutputDirectory( "output/" + strb.toString() );

				return config;
			}

		});

		log.info( "Starting with modules: {}", modules );

		Injector injector = Guice.createInjector(modules);

		RunEpisim.printBindings( injector );

		Config config = injector.getInstance(Config.class);

		if (logToOutput) OutputDirectoryLogging.initLoggingWithOutputDirectory(config.controler().getOutputDirectory());

		File fromFile = new File( "/Users/kainagel/git/all-matsim/episim-matsim/src/main/java/org/matsim/run/KnRunEpisim.java");
		File toFile = new File( config.controler().getOutputDirectory() + "/KnRunEpisim.java" ) ;

		try {
			Files.copy(fromFile.toPath(), toFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES );
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		ControlerUtils.checkConfigConsistencyAndWriteToLog( config, "just before calling run" );
		ConfigUtils.writeConfig( config, config.controler().getOutputDirectory() + "/output_config.xml.gz" );
		ConfigUtils.writeMinimalConfig( config, config.controler().getOutputDirectory() + "/output_config_reduced.xml.gz" );

		injector.getInstance(EpisimRunner.class).run( 90 );

		if (logToOutput) OutputDirectoryLogging.closeOutputDirLogging();

	}

	public static void writeGroupSizes( Object2IntMap<EpisimContainer<?>> maxGroupSize ){
		{
			List<Long> cnts = new ArrayList<>();
			for( Object2IntMap.Entry<EpisimContainer<?>> entry : maxGroupSize.object2IntEntrySet() ){
				EpisimContainer<?> container = entry.getKey();
				if( !(container instanceof InfectionEventHandler.EpisimFacility) ){
					continue;
				}
				int idx = container.getMaxGroupSize();
				while( idx >= cnts.size() ){
					cnts.add( 0L );
				}
				cnts.set( idx, cnts.get( idx ) + 1 );
			}
			try( BufferedWriter writer = IOUtils.getBufferedWriter( "maxGroupSizeFac.csv" ) ){
				for( int ii = 0 ; ii < cnts.size() ; ii++ ){
					writer.write( ii + "," + cnts.get( ii ) + "\n" );
				}
			} catch( IOException e ){
				e.printStackTrace();
			}
		}
		{
			List<Long> cnts = new ArrayList<>();
			for( Object2IntMap.Entry<EpisimContainer<?>> entry : maxGroupSize.object2IntEntrySet() ){
				EpisimContainer<?> container = entry.getKey();
				if( !(container instanceof InfectionEventHandler.EpisimVehicle) ){
					continue;
				}
				int idx = container.getMaxGroupSize();
				while( idx >= cnts.size() ){
					cnts.add( 0L );
				}
				cnts.set( idx, cnts.get( idx ) + 1 );
			}
			try( BufferedWriter writer = IOUtils.getBufferedWriter( "maxGroupSizeVeh.csv" ) ){
				for( int ii = 0 ; ii < cnts.size() ; ii++ ){
					writer.write( ii + "," + cnts.get( ii ) + "\n" );
				}
			} catch( IOException e ){
				e.printStackTrace();
			}
		}

		log.warn( "stopping here ..." );
		System.exit( -1 );
	}


	/*
	private static class MyInfectionModel implements InfectionModel {

		private final FaceMaskModel maskModel;
		private final EpisimConfigGroup episimConfig;

		@Inject MyInfectionModel(Config config, FaceMaskModel maskModel ) {
			this.maskModel = maskModel;
			this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		}


		@Override
		public double calcInfectionProbability(EpisimPerson target, EpisimPerson infector, Map<String, Restriction> restrictions, InfectionParams act1, InfectionParams act2, double jointTimeInContainer) {

			double contactIntensity = Math.min(act1.getContactIntensity(), act2.getContactIntensity());
			// ci corr can not be null, because sim is initialized with non null value
			double ciCorrection = Math.min(restrictions.get(act1.getContainerName()).getCiCorrection(), restrictions.get(act2.getContainerName()).getCiCorrection());

			// note that for 1pct runs, calibParam is of the order of one, which means that for typical times of 100sec or more, exp( - 1 * 1 * 100 ) \approx 0, and
			// thus the infection proba becomes 1.  Which also means that changes in contactIntensity has no effect.  kai, mar'20

			double susceptibility = (double) target.getAttributes().getAttribute( SUSCEPTIBILITY );
			double infectability = (double) infector.getAttributes().getAttribute( VIRAL_LOAD );

			return 1 - Math.exp(-episimConfig.getCalibrationParameter() * susceptibility * infectability * contactIntensity * jointTimeInContainer * ciCorrection
					* maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding
					* maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake
			);
		}
	}
	 */

}

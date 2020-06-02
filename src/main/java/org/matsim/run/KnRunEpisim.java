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
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.episim.*;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.reporting.AsyncEpisimWriter;
import org.matsim.episim.reporting.EpisimWriter;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static java.lang.Math.max;
import static org.matsim.episim.EpisimPerson.*;
import static org.matsim.episim.model.Transition.logNormalWithMedianAndStd;

public class KnRunEpisim {
	public static final String SUSCEPTIBILITY = "susceptibility";
	public static final String VIRAL_LOAD = "viralLoad";

	private static final Logger log = LogManager.getLogger( KnRunEpisim.class );

	private static final boolean verbose = false;
	private static final boolean logToOutput = true;

	private static final double sigmaInfectiousness = 1;
	// 2 leads to dynamics so unstable that it does not look plausible w.r.t. reality.  kai, jun'20

	private static final double sigmaSusc = 0.;

	private static final double probaInfectWSymptoms = 0.;

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
				bind( InfectionModel.class ).to(MyInfectionModel.class ).in( Singleton.class );
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

				config.vspExperimental().setVspDefaultsCheckingLevel( VspExperimentalConfigGroup.VspDefaultsCheckingLevel.ignore );

				// save some time for not needed inputs
				config.facilities().setInputFile(null);
				config.vehicles().setVehiclesFile(null);

				ControlerUtils.checkConfigConsistencyAndWriteToLog( config, "before loading scenario" );

				final Scenario scenario = ScenarioUtils.loadScenario( config );

				SplittableRandom rnd = new SplittableRandom( 4715 );
				for( Person person : scenario.getPopulation().getPersons().values() ){
					person.getAttributes().putAttribute( VIRAL_LOAD, EpisimUtils.nextLogNormalFromMeanAndSigma( rnd, 1,
							sigmaInfectiousness ) );
					person.getAttributes().putAttribute( SUSCEPTIBILITY, EpisimUtils.nextLogNormalFromMeanAndSigma( rnd, 1, sigmaSusc ) );
				}

				return scenario;
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
		 *
		 * ---
		 *
		 * Moduls is left of median: median = exp(mu); mode = exp(mu - sigma^2).  In consequence, the dynamically relevant times are shortened by
		 * increasing sigma, without having to touch the median.
		 */

		final double infectedToContag = 3.; // orig 4
		final double infectedBNCStd = infectedToContag/1.;

		final double contagToSymptoms = 1.5; // orig 2
		final double contagiousStd = contagToSymptoms/1.;

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
			public Config config() throws IOException{
				Config config = ConfigUtils.createConfig(new EpisimConfigGroup() );

				config.global().setRandomSeed( 4713 );

				EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

				episimConfig.setWriteEvents( EpisimConfigGroup.WriteEvents.episim );

				episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

				episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_episim_events_25pt.xml.gz" );
				config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz" );
				episimConfig.setSampleSize(0.25);

				episimConfig.setInitialInfections(50 );
				episimConfig.setInitialInfectionDistrict("Berlin" );

				SnzBerlinScenario25pct2020.addParams(episimConfig );

				SnzBerlinScenario25pct2020.setContactIntensities(episimConfig );

//				episimConfig.setMaxInteractions( 3 );
//				episimConfig.setCalibrationParameter(0.000_002_3);

				episimConfig.setMaxInteractions( 10 );
//				episimConfig.setCalibrationParameter( 0.000_000_69 ); // sigmaInfec=0.
				episimConfig.setCalibrationParameter( 0.000_000_8 ); // sigmaInfect=1.

//				episimConfig.setMaxInteractions( 100 );
//				episimConfig.setCalibrationParameter( 0.000_000_1 );

				// uniform susceptibility:
//				episimConfig.setCalibrationParameter( 0.000_001_4 );

				//lognormal susceptibility:
//				episimConfig.setCalibrationParameter( 0.000_000_032 );

				//lognormal susceptibility:
//				episimConfig.setCalibrationParameter( 0.000_000_0045 );

//				episimConfig.getOrAddContainerParams("home" ).setContactIntensity( 0.3 );
				episimConfig.getOrAddContainerParams( AbstractInfectionModel.QUARANTINE_HOME ).setContactIntensity( 0.01 );

				// ---

				RestrictionsType restrictionsType = RestrictionsType.unrestr;

				final CiChangeType ciChangeType = CiChangeType.inclHome;

				System.out.println(  ) ;

				StringBuilder strb = new StringBuilder();
				strb.append( LocalDateTime.now().format( DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss" ) ) );
				strb.append( "__" ).append( restrictionsType.name() );
				strb.append( "__theta" ).append( episimConfig.getCalibrationParameter() );
				strb.append( "@" ).append( episimConfig.getMaxInteractions() );
				strb.append( "__pWSymp" ).append( probaInfectWSymptoms );
				strb.append( "__infectedBNC" ).append( infectedToContag ).append( "_" ).append( infectedBNCStd );
				strb.append( "__contag" ).append( contagToSymptoms ).append( "_" ).append( contagiousStd );
				strb.append( "__sInfct" ).append( sigmaInfectiousness );
				strb.append( "__sSusc" ).append( sigmaSusc );

				if ( restrictionsType==RestrictionsType.triang ) {
					episimConfig.setStartDate( LocalDate.of( 2020, 2, 15 ) );
					// ===
					List<String> allActivitiesExceptHomeList = new ArrayList<>();
					List<String> allActivitiesExceptHomeAndEduList = new ArrayList<>();
					for( ConfigGroup infectionParams : episimConfig.getParameterSets().get( "infectionParams" ) ){
						final String activityType = infectionParams.getParams().get( "activityType" );
						if ( !activityType.contains( "home" ) ) {
							allActivitiesExceptHomeList.add(activityType);
							if (!activityType.contains( "educ_" ) ){
								allActivitiesExceptHomeAndEduList.add( activityType );
							}
						}
					}
					final String[] actsExceptHomeAndEdu = allActivitiesExceptHomeAndEduList.toArray( new String[0] );
					final String[] actsExceptHome = allActivitiesExceptHomeList.toArray( new String[0] );
					final String[] educ_lower = {"educ_primary", "educ_kiga"};
					final String[] educ_higher = {"educ_secondary", "educ_higher", "educ_tertiary", "educ_other"};
					FixedPolicy.ConfigBuilder restrictions = FixedPolicy.config();
					// ===
					// ci change:
					LocalDate dateOfCiCorrA = LocalDate.of( 2020, 3, 8 );
					double ciCorrA = 0.55;
					// (8.3.: Empfehlung Absage Veranstaltungen > 1000 Teilnehmer ???; Verhaltensänderungen?)
					restrictions.restrict( dateOfCiCorrA, Restriction.ofCiCorrection( ciCorrA ), actsExceptHome );
					restrictions.restrict( dateOfCiCorrA, Restriction.ofCiCorrection( ciCorrA ), "pt" );
					if ( ciChangeType==CiChangeType.inclHome) {
						restrictions.restrict( dateOfCiCorrA, Restriction.ofCiCorrection( ciCorrA ), "home" );
					}
					// Wir hatten sicher bereits Reaktionen im Arbeitsleben.  Nicht nur home office (= in den Mobilitätsdaten), sondern
					// auch kein Händeschütteln, Abstand, Räume lüften.  Freizeit damit vermutlich auch; die Tatsache, dass (dennoch)
					// viele der Berliner Grossinfektionen in dieser Woche in den Clubs stattfanden, ist vllt Konsequenz der Tatsache, dass
					// es vorher nicht genügend Virusträger in Bln gab.
					// Obiger Ansatz (insbesondere mit inclHome) sagt allerdings, dass wir das im Prinzip ins theta hinein absorbieren.

					// quick reductions towards lockdown:
					LocalDate triangleStartDate = LocalDate.of( 2020, 3, 8 );
					final LocalDate date_2020_03_24 = LocalDate.of( 2020, 3, 24 );
					double alpha = 1.2;
					final double remainingFractionAtMax = max( 0., 1. - alpha * 0.36 );
					restrictions.interpolate( triangleStartDate, date_2020_03_24,
							Restriction.of(1.), Restriction.of(remainingFractionAtMax),
							actsExceptHomeAndEdu );
					// school closures:
					restrictions.restrict( "2020-03-14", 0.1, educ_lower ).restrict( "2020-03-14", 0., educ_higher );
					// slow re-opening:
					restrictions.interpolate( date_2020_03_24, LocalDate.of( 2020,5,31 ),
							Restriction.of(remainingFractionAtMax ), Restriction.of( max( 0., 1.-alpha*0. ) ),
							actsExceptHomeAndEdu );
					// absorb masks into exposures and ramp up:
					final LocalDate dateOfCiCorrB = LocalDate.of( 2020, 4, 15 );
					final double ciCorrB = 0.15;
					final int nDays = 14;
					for ( int ii = 0 ; ii<= nDays ; ii++ ){
						double newExposure = ciCorrA + ( ciCorrA*ciCorrB - ciCorrA ) * ii / nDays ;
						// check: ii=0 --> old value; ii=nDays --> new value
						restrictions.restrict( dateOfCiCorrB.plusDays( ii ), Restriction.ofCiCorrection( newExposure ), "shop_daily","shop_other" );
						restrictions.restrict( dateOfCiCorrB.plusDays( ii ), Restriction.ofCiCorrection( newExposure ), "pt","tr", "leisure");
						restrictions.restrict( dateOfCiCorrB.plusDays( ii ), Restriction.ofCiCorrection( newExposure ), educ_higher );
						restrictions.restrict( dateOfCiCorrB.plusDays( ii ), Restriction.ofCiCorrection( newExposure ), educ_lower );
					}

					// ===
					episimConfig.setPolicy( FixedPolicy.class, restrictions.build() );

					strb.append( "_startDate" ).append( episimConfig.getStartDate() );

					strb.append( "_ciCorrA" ).append( ciCorrA );
					strb.append( "@" ).append( dateOfCiCorrA );
					strb.append( "_" + ciChangeType.name() );

					strb.append( "_triangStrt" ).append( triangleStartDate );
					strb.append( "_alpha" ).append( alpha );

					strb.append( "_ciCorrB" + ciCorrB + "@" ).append( dateOfCiCorrB ).append( "over" + nDays + "days" );

				} else if ( restrictionsType==RestrictionsType.frmSnz ){
					episimConfig.setStartDate( LocalDate.of( 2020, 2, 15 ) );

					LocalDate dateOfExposureChange = LocalDate.of( 2020, 3, 10 );
					double changedExposure = 0.1;

					double alpha = 2.;

					FixedPolicy.ConfigBuilder restrictions = EpisimUtils.createRestrictionsFromCSV( episimConfig, alpha, dateOfExposureChange, changedExposure );
					if ( ciChangeType== CiChangeType.inclHome ){
						restrictions.restrict( dateOfExposureChange, Restriction.of( 1., changedExposure, FaceMask.NONE ), "home" );
					}
					episimConfig.setPolicy( FixedPolicy.class, restrictions.build() );

					strb.append( "_startDate" ).append( episimConfig.getStartDate() );

					strb.append( "_chExposure" + changedExposure );
					strb.append( "_@" + dateOfExposureChange );
					strb.append( "_" + ciChangeType.name() );

					strb.append( "_alpha" + alpha );
				} else if ( restrictionsType==RestrictionsType.unrestr ) {
					episimConfig.setStartDate( LocalDate.of( 2020, 2, 15 ) );
					strb.append( "_startDate" ).append( episimConfig.getStartDate() );
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

		File fromFile = new File( "/Users/kainagel/git/all-matsim/episim-matsim/src/main/java/org/matsim/run/KnRunEpisim.java");
		File toFile = new File( config.controler().getOutputDirectory() + "/KnRunEpisim.java" ) ;

		try {
			Files.copy(fromFile.toPath(), toFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES );
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		ProgressionModel pm = injector.getInstance( ProgressionModel.class );
		if ( pm instanceof DefaultProgressionModel ) {
			final DefaultProgressionModel defaultProgressionModel = (DefaultProgressionModel) pm;
			defaultProgressionModel.setTransition( DiseaseStatus.infectedButNotContagious, logNormalWithMedianAndStd( infectedToContag, infectedBNCStd ) );
			defaultProgressionModel.setTransition( DiseaseStatus.contagious, logNormalWithMedianAndStd( contagToSymptoms, contagiousStd ) );
			defaultProgressionModel.setTransition( DiseaseStatus.showingSymptoms, logNormalWithMedianAndStd( SymptomsToSSick, withSymptomsStd ) );
			defaultProgressionModel.setTransition( DiseaseStatus.seriouslySick, logNormalWithMedianAndStd( sStickToCritical, seriouslySickStd ) );
			defaultProgressionModel.setTransition( DiseaseStatus.critical, logNormalWithMedianAndStd( criticalToBetter, criticalStd ) );
		}

		EpisimRunner runner = injector.getInstance(EpisimRunner.class);
		runner.run(365);

		if (logToOutput) OutputDirectoryLogging.closeOutputDirLogging();

	}

	enum RestrictionsType {unrestr, triang, frmSnz }
	enum CiChangeType{ exclHome, inclHome }

	private static class MyInfectionModel extends DefaultInfectionModel {
		private final FaceMaskModel maskModel;
		@Inject MyInfectionModel( SplittableRandom rnd, EpisimConfigGroup episimConfig, EpisimReporting reporting,
					  FaceMaskModel maskModel, TracingConfigGroup trConfig ) {
			super( rnd,  episimConfig,  reporting, maskModel, trConfig.getPutTraceablePersonsInQuarantineAfterDay() );
			this.maskModel = maskModel;
		}
		@Override protected double calcInfectionProbability(EpisimPerson target, EpisimPerson infector,
							  EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2,
							  double jointTimeInContainer) {

			if ( !( infector.getDiseaseStatus()==DiseaseStatus.contagious ) ){
				if ( ! ( rnd.nextDouble() < probaInfectWSymptoms ) ) {
					return 0.;
				}
			}

			Map<String, Restriction> r = getRestrictions();

			// ci corr can not be null, because sim is initialized with non null value
			double ciCorrection = Math.min(r.get(act1.getContainerName()).getCiCorrection(), r.get(act2.getContainerName()).getCiCorrection());
			double contactIntensity = Math.min(act1.getContactIntensity(), act2.getContactIntensity());

			// note that for 1pct runs, calibParam is of the order of one, which means that for typical times of 100sec or more,
			// exp( - 1 * 1 * 100 ) \approx 0, and thus the infection proba becomes 1.  Which also means that changes in contactIntensity has
			// no effect.  kai, mar'20

			double susceptibility = (double) target.getAttributes().getAttribute( SUSCEPTIBILITY );
			double infectability = (double) infector.getAttributes().getAttribute( VIRAL_LOAD );

			return 1 - Math.exp(-episimConfig.getCalibrationParameter() * susceptibility * infectability * contactIntensity * jointTimeInContainer * ciCorrection
							    * maskModel.getWornMask(infector, act2, iteration, r.get(act2.getContainerName())).shedding
							    * maskModel.getWornMask(target, act1, iteration, r.get(act1.getContainerName())).intake
					   );
		}

	}

}

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
import java.time.DayOfWeek;
import java.time.LocalDate;
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

	private static final double sigmaInfect = 0.;
	// 2 leads to dynamics so unstable that it does not look plausible w.r.t. reality.  kai, jun'20

	private static final double sigmaSusc = 0.;

	private enum WeekendHandling{ weekdaysOnly, inclWeekends }
	private static final WeekendHandling runType = WeekendHandling.inclWeekends;

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
				bind( InteractionModel.class ).to( SymmetricInteractionModel.class ).in( Singleton.class );
				bind( InfectionModel.class).to( MyInfectionModel.class ).in( Singleton.class );
				bind( ProgressionModel.class ).to( AgeDependentProgressionModel.class ).in( Singleton.class );
				bind( FaceMaskModel.class ).to( DefaultFaceMaskModel.class ).in( Singleton.class );

				// Internal classes, should rarely be needed to be reconfigured
				bind(EpisimRunner.class).in( Singleton.class );
				bind( ReplayHandler.class ).in( Singleton.class );
				bind( InfectionEventHandler.class ).in( Singleton.class );
				bind( EpisimReporting.class ).in( Singleton.class );

			}
			@Provides @Singleton public Scenario scenario( Config config ) {

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
					person.getAttributes().putAttribute( VIRAL_LOAD, nextLogNormalFromMeanAndSigma( rnd, 1, sigmaInfect ) );
					person.getAttributes().putAttribute( SUSCEPTIBILITY, nextLogNormalFromMeanAndSigma( rnd, 1, sigmaSusc ) );
				}

				return scenario;
			}
			@Provides @Singleton public EpisimConfigGroup episimConfigGroup(Config config) {
				return ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
			}
			@Provides @Singleton public TracingConfigGroup tracingConfigGroup( Config config ) {
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
				Config config = new SnzBerlinScenario25pct2020().config();

				EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
				TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

				switch( runType ) {
					case weekdaysOnly:
						break;
					case inclWeekends:
						episimConfig.clearInputEventsFiles();

						config.plans().setInputFile(SnzBerlinScenario25pct2020.INPUT.resolve("be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz").toString());

						episimConfig.addInputEventsFile(SnzBerlinScenario25pct2020.INPUT.resolve("be_2020-week_snz_episim_events_wt_25pt_split.xml.gz").toString())
							    .addDays( DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY );

						episimConfig.addInputEventsFile(SnzBerlinScenario25pct2020.INPUT.resolve("be_2020-week_snz_episim_events_sa_25pt_split.xml.gz").toString())
							    .addDays(DayOfWeek.SATURDAY);

						episimConfig.addInputEventsFile(SnzBerlinScenario25pct2020.INPUT.resolve("be_2020-week_snz_episim_events_so_25pt_split.xml.gz").toString())
							    .addDays(DayOfWeek.SUNDAY);

						episimConfig.setCalibrationParameter(1.13e-5);
						episimConfig.setStartDate("2020-02-15");
						break;
					default:
						throw new IllegalStateException( "Unexpected value: " + runType );
				}


				episimConfig.setWriteEvents( WriteEvents.episim );

				// ---

				config.global().setRandomSeed( 4711 );

				episimConfig.setMaxInteractions( 25. );

//				tracingConfig.setTracingCapacity_per_day( Integer.MAX_VALUE );
				tracingConfig.setTracingCapacity_pers_per_day( 0 );

				// ---

				RestrictionsType restrictionsType = RestrictionsType.fromConfig;

				StringBuilder strb = new StringBuilder();
				strb.append( LocalDateTime.now().format( DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss" ) ) );
				strb.append( "__" ).append( restrictionsType.name() );
				strb.append( "__theta" ).append( episimConfig.getCalibrationParameter() ).append( "@" ).append( episimConfig.getMaxInteractions() );
				if ( sigmaInfect!=0. ) strb.append( "__sInfct" ).append( sigmaInfect );
				if ( sigmaSusc!=0. ) strb.append( "__sSusc" ).append( sigmaSusc );
				strb.append( "__trStrt" ).append( tracingConfig.getPutTraceablePersonsInQuarantineAfterDay() );

				if ( restrictionsType==RestrictionsType.fromConfig ) {
					// do nothing
				} else if ( restrictionsType==RestrictionsType.triang ) {
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
					restrictions.restrict( dateOfCiCorrA, Restriction.ofCiCorrection( ciCorrA ), "pt", "home" );
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

					strb.append( "_ciCorrA" ).append( ciCorrA );
					strb.append( "@" ).append( dateOfCiCorrA );

					strb.append( "_triangStrt" ).append( triangleStartDate );
					strb.append( "_alpha" ).append( alpha );

					strb.append( "_ciCorrB" + ciCorrB + "@" ).append( dateOfCiCorrB ).append( "over" + nDays + "days" );

				} else if ( restrictionsType==RestrictionsType.fromSnz ){
					SnzBerlinScenario25pct2020.BasePolicyBuilder basePolicyBuilder = new SnzBerlinScenario25pct2020.BasePolicyBuilder( episimConfig );
					basePolicyBuilder.setCiCorrections( Map.of("2020-03-07", 0.32 ));
					basePolicyBuilder.setAlpha( 1. );

					FixedPolicy.ConfigBuilder restrictions = basePolicyBuilder.build();
					episimConfig.setPolicy(FixedPolicy.class, restrictions.build());

					strb.append( "_ciCorr" ).append(Joiner.on("_").withKeyValueSeparator("@").join(basePolicyBuilder.getCiCorrections()));
					strb.append( "_alph" ).append( basePolicyBuilder.getAlpha() );

				} else if ( restrictionsType==RestrictionsType.unrestr ) {
					final FixedPolicy.ConfigBuilder restrictions = FixedPolicy.config();
//					final double ciEdu = 0.;
//					restrictions.restrict( LocalDate.of(2020, 3, 7), Restriction.ofCiCorrection( ciEdu ), "educ_kiga", "educ_primary",
//							"educ_secondary", "educ_higher", "educ_tertiary", "educ_other" );
//					strb.append( "__ciEdu" ).append( ciEdu );
					episimConfig.setPolicy( FixedPolicy.class, restrictions.build() ); // overwrite snz policy
				}

				strb.append( "_seed" ).append( config.global().getRandomSeed() );
				strb.append( "_strtDt" ).append( episimConfig.getStartDate() );
				strb.append("_trCap" ).append( tracingConfig.getTracingCapacity() );
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

		injector.getInstance(EpisimRunner.class).run(100 );

		if (logToOutput) OutputDirectoryLogging.closeOutputDirLogging();

	}

	enum RestrictionsType {unrestr, triang, fromSnz, fromConfig }

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

}

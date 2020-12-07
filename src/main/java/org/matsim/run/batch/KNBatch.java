package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.model.AgeDependentProgressionModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.matsim.run.modules.SnzBerlinProductionScenario.*;


/**
 * sebastians playground
 */
public class KNBatch implements BatchRun<KNBatch.Params> {
	private static final int IMPORT_OFFSET = 0;
//	private static final Snapshot snapshot = Snapshot.episim_snapshot_120_2020_06_14;
	private static final Snapshot SNAPSHOT = Snapshot.no;

	@Override
	public AbstractModule getBindings( int id, @Nullable Params params ) {
		return new Builder()
				       .setSnapshot( SNAPSHOT )
				       .setImportOffset( IMPORT_OFFSET )
				       .createSnzBerlinProductionScenario();
	};

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
	}

	@Override
	public Config prepareConfig(int id, Params params) {
		SnzBerlinProductionScenario module = new Builder()
								     .setSnapshot( SNAPSHOT )
								     .setImportOffset( IMPORT_OFFSET )
								     .createSnzBerlinProductionScenario();

		Config config = module.config();
		config.global().setRandomSeed(4711);

		String[] tempXTheta = params.tempXTheta.split( "_" );
		double tempMidPoint = Double.parseDouble( tempXTheta[0] );
		double theta = Double.parseDouble( tempXTheta[1] );


		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * theta );
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
//		episimConfig.setSnapshotInterval( 30 );

//		episimConfig.getAgeInfectivity().replaceAll((k,v) ->  k < 20 ? v * params.childInfectivitySusceptibility : 1);
//		episimConfig.getAgeSusceptibility().replaceAll((k,v) -> k < 20 ? v * params.childInfectivitySusceptibility : 1);

		// The following are actually the wrong way round, influencing many of my tryout runs.  kai, dec'20
//		double newbornInfect=0.;
//		double newbornSuscept=0.7;


//		episimConfig.setAgeInfectivity( Map.of(
//				0,0.,
//				params.youthAge-1,0.,
//				params.youthAge,1.,
//				params.grownUpAge,1.
//						      ) );

		episimConfig.setAgeSusceptibility( Map.of(
				0,params.childSusc, params.youthAge-1,params.childSusc,
				params.youthAge,params.youthSusc, params.grownUpAge-1,params.youthSusc,
				params.grownUpAge,1.
				));


//		if (params.summerEnd.equals("fromWeather" )) {
			try {
				Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractionsFromWeatherData("berlinWeather.csv",
						2, tempMidPoint-params.tempPm, tempMidPoint+params.tempPm );
				episimConfig.setLeisureOutdoorFraction(outdoorFractions);
			} catch ( IOException e) {
				throw new RuntimeException( e );
			}
//		} else {
//			Map<LocalDate, Double> leisureOutdoorFraction = new TreeMap<>(Map.of(
//					LocalDate.parse("2020-01-15"), 0.1,
//					LocalDate.parse("2020-04-15"), 0.8,
//					LocalDate.parse(params.summerEnd ), 0.8,
//					LocalDate.parse("2020-11-15"), 0.1,
//					LocalDate.parse("2021-02-15"), 0.1,
//					LocalDate.parse("2021-04-15"), 0.8,
//					LocalDate.parse("2021-09-15"), 0.8 )
//			);
//			episimConfig.setLeisureOutdoorFraction(leisureOutdoorFraction);
//		}

		Map<LocalDate, Integer> importMap = new HashMap<>();
		int importOffset = 0;
		{
			double importFactor = params.impFactBefJun;
			importMap.put( episimConfig.getStartDate(), Math.max( 1, (int) Math.round( 0.9 * 1 ) ) );
			interpolateImport( importMap, importFactor, LocalDate.parse( "2020-02-24" ).plusDays( importOffset ), LocalDate.parse( "2020-03-09" ).plusDays( importOffset ), 0.9, 23.1 );
			interpolateImport( importMap, importFactor, LocalDate.parse( "2020-03-09" ).plusDays( importOffset ), LocalDate.parse( "2020-03-23" ).plusDays( importOffset ), 23.1, 3.9 );
			interpolateImport( importMap, importFactor, LocalDate.parse( "2020-03-23" ).plusDays( importOffset ), LocalDate.parse( "2020-04-13" ).plusDays( importOffset ), 3.9, 0.1 );
		}
		{
			double importFactor = params.impFactAftJun;
			if( importFactor == 0. ){
				importMap.put( LocalDate.parse( "2020-06-08" ), 1 );
			} else{
				interpolateImport( importMap, importFactor, LocalDate.parse( "2020-06-08" ).plusDays( importOffset ), LocalDate.parse( "2020-07-13" ).plusDays( importOffset ), 0.1, 2.7 );
				interpolateImport( importMap, importFactor, LocalDate.parse( "2020-07-13" ).plusDays( importOffset ), LocalDate.parse( "2020-08-10" ).plusDays( importOffset ), 2.7, 17.9 );
				interpolateImport( importMap, importFactor, LocalDate.parse( "2020-08-10" ).plusDays( importOffset ), LocalDate.parse( "2020-09-07" ).plusDays( importOffset ), 17.9, 5.4 );
			}
		}
		episimConfig.setInfections_pers_per_day(importMap);


		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class );
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), params.tracCapApr,
				LocalDate.of(2020, 6, 15), params.tracCapJun
								     ) );

		tracingConfig.setTracingDelay_days( Map.of(
				LocalDate.of( 2020, 4, 1), 4,
				LocalDate.of( 2020, 6, 15), 4
								     ) );


		return config;

	}

	public static final class Params {

		@StringParameter({
//				"15.0_1.0",
//				"17.5_0.8","20.0_0.75","22.5_0.7",
				"25.0_0.65"
		}) String tempXTheta;
		@Parameter( { 0. } ) double tempPm;
		@Parameter({4.0}) double impFactBefJun;
		@Parameter ({0.0}) double childSusc;
		@IntParameter( {7} ) int youthAge;
		@Parameter({0.}) double youthSusc;
		@IntParameter( {24} ) int grownUpAge;
		@Parameter({0.0}) double impFactAftJun;
		@IntParameter({600,1200}) int tracCapApr;
		@IntParameter({0}) int tracCapJun;

//		@GenerateSeeds(1) long seed;
//		@IntParameter({IMPORT_OFFSET}) int importOffset;
//		@Parameter( { 15.,20.,25.,30. } ) double tempMidPoint; // an meinem Geb. waren tagsüber 23 Grad; Tiefstwert nachfolgende Nacht 6
//		@Parameter({0.7}) double thetaFactor;
// 		@IntParameter({4}) int tracingDelay;
//		@Parameter({1.5}) double childInfectivitySusceptibility;
//		@StringParameter({"fromWeather"}) String summerEnd;
//		@Parameter({0.}) double newbornSuscept;
//		@Parameter({0.7}) double newbornInfect;

	}

	public static void main( String[] args ){
		String [] args2 = {
				RunParallel.OPTION_SETUP, org.matsim.run.batch.KNBatch.class.getName(),
				RunParallel.OPTION_PARAMS, KNBatch.Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString( 2 ),
				RunParallel.OPTION_ITERATIONS, Integer.toString( 330 )
		};

		RunParallel.main( args2 );
	}

	static final class MyProgressionModel implements ProgressionModel {
		AgeDependentProgressionModel delegate ;
		@Inject MyProgressionModel( SplittableRandom rnd, EpisimConfigGroup episimConfig, TracingConfigGroup tracingConfig ) {
			delegate = new AgeDependentProgressionModel( rnd, episimConfig, tracingConfig );
		}
		@Override public void setIteration( int day ){
			delegate.setIteration( day );
		}
		@Override public void updateState( EpisimPerson person, int day ){
			delegate.updateState( person, day );
		}
		@Override public boolean canProgress( EpisimReporting.InfectionReport report ){
//			return report.nTotalInfected > 0 || report.nInQuarantine > 0;
			return report.nInfectedButNotContagious + report.nContagious + report.nShowingSymptoms > 0 ;
		}

		@Override public int getNextTransitionDays(Id<Person> personId) {
			return delegate.getNextTransitionDays(personId);
		}

		@Override public EpisimPerson.DiseaseStatus getNextDiseaseStatus(Id<Person> personId) {
			return delegate.getNextDiseaseStatus(personId);
		}

		@Override public void beforeStateUpdates( Map<Id<Person>, EpisimPerson> persons, int day, EpisimReporting.InfectionReport report ){
			delegate.beforeStateUpdates( persons, day, report );
		}
	}


}

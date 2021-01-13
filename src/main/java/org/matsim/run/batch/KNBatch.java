package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.TracingConfigGroup.CapacityType;
import org.matsim.episim.model.AgeDependentProgressionModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.policy.FixedPolicy;
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
//	private static final Snapshot SNAPSHOT = Snapshot.no;

	@Override
	public AbstractModule getBindings( int id, @Nullable Params params ) {
		return new Builder()
//				       .setSnapshot( SNAPSHOT )
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
//								     .setSnapshot( SNAPSHOT )
								     .setImportOffset( IMPORT_OFFSET )
								     .createSnzBerlinProductionScenario();

		Config config = module.config();
//		config.global().setRandomSeed(params.seed);


		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

//		episimConfig.setStartFromSnapshot(
//				"output/theta_0.65-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0/" +
//				"output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0-seed_3831662765844904176/" +
//				"episim-snapshot-150-2020-07-14.zip"
//				);
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.restore);
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.theta );
		episimConfig.setSnapshotInterval( 30 );

//		episimConfig.getOrAddContainerParams("leisure").setContactIntensity(9.24);

		int youthAge = 7;
		episimConfig.setAgeSusceptibility( Map.of( 0,0., youthAge-1,0., youthAge,params.ythSusc, params.grwnUpAge -1,params.ythSusc,
				params.grwnUpAge,1. ) );

		{
			FixedPolicy.ConfigBuilder builder = FixedPolicy.parse( episimConfig.getPolicy() );
//			builder.restrict("2020-11-01", Restriction.ofCiCorrection( params.ciCorrLeisFrmNov ), "leisure" );
			builder.apply(params.leisFctDate, "2021-12-31", (date, map) -> map.put("fraction", 1 - params.leisFct * (1 - (double) map.get("fraction" )) ) , "leisure" );
			episimConfig.setPolicy( FixedPolicy.class, builder.build() );
		}

		try {
//				double tempPm = 5.;
//				Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractionsFromWeatherData("berlinWeather.csv",
//						2, tempMidPoint-tempPm, tempMidPoint+tempPm );
				Map<LocalDate,Double> outdoorFractions = EpisimUtils.getOutdoorFractions2( SnzBerlinProductionScenario.INPUT.resolve("berlinWeather.csv").toFile(),
						SnzBerlinProductionScenario.INPUT.resolve("berlinWeatherAvg2000-2020.csv").toFile(), 2, params.tMidSpring, 25., 5. );
			System.out.println( outdoorFractions );
			episimConfig.setLeisureOutdoorFraction(outdoorFractions);
		} catch ( IOException e) {
			throw new RuntimeException( e );
		}

		Map<LocalDate, Integer> importMap = new HashMap<>();
		int importOffset = 0;
		{
			double impFactBefJun = 4.;
			importMap.put( episimConfig.getStartDate(), Math.max( 1, (int) Math.round( 0.9 * 1 ) ) );
			interpolateImport( importMap, impFactBefJun, LocalDate.parse( "2020-02-24" ).plusDays( importOffset ), LocalDate.parse( "2020-03-09" ).plusDays( importOffset ), 0.9, 23.1 );
			interpolateImport( importMap, impFactBefJun, LocalDate.parse( "2020-03-09" ).plusDays( importOffset ), LocalDate.parse( "2020-03-23" ).plusDays( importOffset ), 23.1, 3.9 );
			interpolateImport( importMap, impFactBefJun, LocalDate.parse( "2020-03-23" ).plusDays( importOffset ), LocalDate.parse( "2020-04-13" ).plusDays( importOffset ), 3.9, 0.1 );
		}
		{
			double importFactor = params.imprtFctAftJun;
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
		tracingConfig.setCapacityType( CapacityType.PER_PERSON );
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), params.trcCapNInf
//				, LocalDate.of(2020, 6, 15), params.tracCapJun
								     ) );

		tracingConfig.setTracingDelay_days( Map.of(
				LocalDate.of( 2020, 4, 1), 4,
				LocalDate.of( 2020, 6, 15), 4
								     ) );


		return config;

	}

	public static final class Params {

		@Parameter({0.8}) double theta;
		@Parameter({17.5}) double tMidSpring;
		@Parameter({0.0}) double ythSusc;
		@IntParameter( {16} ) int grwnUpAge;
		@Parameter({1.5}) double leisFct;
		@StringParameter( {"2020-10-15"} ) String leisFctDate;
		@Parameter({0.}) double imprtFctAftJun;
		@IntParameter({0}) int trcCapNInf;
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

	@Override public int getOffset() { return 400; }

	public static void main( String[] args ){
		String [] args2 = {
				RunParallel.OPTION_SETUP, org.matsim.run.batch.KNBatch.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString( 4 ),
				RunParallel.OPTION_ITERATIONS, Integer.toString( 360 ),
				RunParallel.OPTION_METADATA
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

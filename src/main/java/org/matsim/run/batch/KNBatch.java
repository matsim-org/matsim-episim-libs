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
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;

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

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.thetaFactor);
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);

		episimConfig.setChildInfectivity(episimConfig.getChildInfectivity() * params.childInfectivitySusceptibility );
		episimConfig.setChildSusceptibility(episimConfig.getChildSusceptibility() * params.childInfectivitySusceptibility );

		if (params.summerEnd.equals("fromWeather" )) {
			try {
				Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractionsFromWeatherData("berlinWeather.csv", 2, params.temperature0, params.temperature1 );
				episimConfig.setLeisureOutdoorFraction(outdoorFractions);
			} catch ( IOException e) {
				throw new RuntimeException( e );
			}
		}

		else {
			Map<LocalDate, Double> leisureOutdoorFraction = new TreeMap<>(Map.of(
					LocalDate.parse("2020-01-15"), 0.1,
					LocalDate.parse("2020-04-15"), 0.8,
					LocalDate.parse(params.summerEnd ), 0.8,
					LocalDate.parse("2020-11-15"), 0.1,
					LocalDate.parse("2021-02-15"), 0.1,
					LocalDate.parse("2021-04-15"), 0.8,
					LocalDate.parse("2021-09-15"), 0.8 )
			);
			episimConfig.setLeisureOutdoorFraction(leisureOutdoorFraction);
		}

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class );
//		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
//				LocalDate.of(2020, 4, 1), 300,
//				LocalDate.of(2020, 6, 15), 2000
//								     ) );

		tracingConfig.setTracingDelay_days( Map.of(
				LocalDate.of( 2020, 4, 1), 4,
				LocalDate.of( 2020, 6, 15), params.tracingDelay
								     ) );


		return config;

	}

	public static final class Params {
		//		@GenerateSeeds(1) long seed;
//		@IntParameter({IMPORT_OFFSET}) int importOffset;
		@Parameter( { 20. } ) double temperature0; // an meinem Geb. waren tagsüber 23 Grad; Tiefstwert nachfolgende Nacht 6
		@Parameter( { 30. } ) double temperature1; //
		@Parameter({0.9,1.0,1.1}) double thetaFactor;
		@IntParameter({4}) int tracingDelay;
		@Parameter({1.0}) double childInfectivitySusceptibility;
		@StringParameter({"fromWeather"}) String summerEnd;
	}

	public static void main( String[] args ){
		String [] args2 = {
				RunParallel.OPTION_SETUP, org.matsim.run.batch.KNBatch.class.getName(),
				RunParallel.OPTION_PARAMS, KNBatch.Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString( 4 ),
				RunParallel.OPTION_ITERATIONS, Integer.toString( 120 )
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

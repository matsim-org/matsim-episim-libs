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


/**
 * sebastians playground
 */
public class KNBatch implements BatchRun<KNBatch.Params> {
	private static final int IMPORT_OFFSET = +7;

	@Override
	public AbstractModule getBindings( int id, @Nullable Params params ) {
		return new SnzBerlinProductionScenario.Builder()
				       .setSnapshot( SnzBerlinProductionScenario.Snapshot.episim_snapshot_120_2020_06_14 )
				       .setImportOffset( IMPORT_OFFSET )
				       .createSnzBerlinProductionScenario();
	};

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
	}

	@Override
	public Config prepareConfig(int id, Params params) {
		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder()
								     .setSnapshot( SnzBerlinProductionScenario.Snapshot.episim_snapshot_120_2020_06_14 )
								     .setImportOffset( params.importOffset )
								     .createSnzBerlinProductionScenario();

		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.theta);
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);

		episimConfig.setChildInfectivity(episimConfig.getChildInfectivity() * params.childInfectivitySusceptibility);
		episimConfig.setChildSusceptibility(episimConfig.getChildSusceptibility() * params.childInfectivitySusceptibility);

		if (params.winterEnd.equals("fromWeather")) {
			try {
				Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractionsFromWeatherData("berlinWeather.csv", 2 );
				episimConfig.setLeisureOutdoorFraction(outdoorFractions);
			} catch ( IOException e) {
				e.printStackTrace();
			}
		}

		else {
			Map<LocalDate, Double> leisureOutdoorFraction = new TreeMap<>(Map.of(
					LocalDate.parse("2020-01-15"), 0.1,
					LocalDate.parse("2020-04-15"), 0.8,
					LocalDate.parse(params.winterEnd), 0.8,
					LocalDate.parse("2020-11-15"), 0.1,
					LocalDate.parse("2021-02-15"), 0.1,
					LocalDate.parse("2021-04-15"), 0.8,
					LocalDate.parse("2021-09-15"), 0.8 )
			);
			episimConfig.setLeisureOutdoorFraction(leisureOutdoorFraction);
		}


		return config;

	}

	public static final class Params {
		@IntParameter({IMPORT_OFFSET})
		int importOffset;

		@GenerateSeeds(1)
		long seed;

		@Parameter({0.9})
		double theta;

		@Parameter({0.})
		double childInfectivitySusceptibility;

		@StringParameter({"2021-11-14"})
		String winterEnd;
	}

	public static void main( String[] args ){
		String [] args2 = {
				RunParallel.OPTION_SETUP, org.matsim.run.batch.KNBatch.class.getName(),
				RunParallel.OPTION_PARAMS, KNBatch.Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString( 4 ),
				RunParallel.OPTION_ITERATIONS, Integer.toString( 180 )
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

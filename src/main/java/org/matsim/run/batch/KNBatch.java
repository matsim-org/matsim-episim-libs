package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.VirusStrain;
import org.matsim.run.RunParallel;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.matsim.run.modules.SnzBerlinProductionScenario.Builder;


/**
 * sebastians playground
 */
public class KNBatch implements BatchRun<KNBatch.Params> {
	private static final Logger log = Logger.getLogger( KNBatch.class );

	@Override public AbstractModule getBindings( int id, @Nullable Params params ) {
		log.warn("entering getBindings ...");
		return new Builder().createSnzBerlinProductionScenario(); }

	@Override public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
	}

	@Override public Config prepareConfig(int id, Params params) {
		log.warn("entering prepareConfig ...");

		Config config = new Builder().setImportFactor( params.imprtFctMult ).createSnzBerlinProductionScenario().config();

		config.global().setRandomSeed( Long.parseLong( params.seed ) );

//		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

//		episimConfig.setStartFromSnapshot(
//				"output/theta_0.65-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0/" +
//				"output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0-seed_3831662765844904176/" +
//				"episim-snapshot-150-2020-07-14.zip"
//				);
//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.restore);
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.theta );
		episimConfig.setSnapshotInterval( 120 );

		if (!params.newVariantDate.equals("never")) {
			Map<LocalDate, Integer> infPerDayVariant = new HashMap<>();
			infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayVariant.put(LocalDate.parse(params.newVariantDate), 1);
			episimConfig.setInfections_pers_per_day( VirusStrain.B117, infPerDayVariant );
		}

		return config;

	}
	public static final class Params {
//		@GenerateSeeds(1) long seed;

		@Parameter({1.0}) double theta;
		@Parameter({3.}) double imprtFctMult;
		@StringParameter( {"2020-12-01"} ) String newVariantDate;
		@StringParameter( {"4711","7564655870752979346"} ) String seed;

	}

	@Override public int getOffset() { return 400; }

	public static void main( String[] args ){
		String [] args2 = {
				RunParallel.OPTION_SETUP, org.matsim.run.batch.KNBatch.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString( 4 ),
				RunParallel.OPTION_ITERATIONS, Integer.toString( (int)(365*1.25) ),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main( args2 );
	}

//	static final class MyProgressionModel implements ProgressionModel {
//		AgeDependentProgressionModel delegate ;
//		@Inject MyProgressionModel( SplittableRandom rnd, EpisimConfigGroup episimConfig, TracingConfigGroup tracingConfig, TestingConfigGroup testingConfigGroup ) {
//			delegate = new AgeDependentProgressionModel( rnd, episimConfig, tracingConfig, testingConfigGroup );
//		}
//		@Override public void setIteration( int day ){
//			delegate.setIteration( day );
//		}
//		@Override public void updateState( EpisimPerson person, int day ){
//			delegate.updateState( person, day );
//		}
//		@Override public boolean canProgress( EpisimReporting.InfectionReport report ){
////			return report.nTotalInfected > 0 || report.nInQuarantine > 0;
//			return report.nInfectedButNotContagious + report.nContagious + report.nShowingSymptoms > 0 ;
//		}
//
//		@Override public int getNextTransitionDays(Id<Person> personId) {
//			return delegate.getNextTransitionDays(personId);
//		}
//
//		@Override public EpisimPerson.DiseaseStatus getNextDiseaseStatus(Id<Person> personId) {
//			return delegate.getNextDiseaseStatus(personId);
//		}
//
//		@Override public void beforeStateUpdates( Map<Id<Person>, EpisimPerson> persons, int day, EpisimReporting.InfectionReport report ){
//			delegate.beforeStateUpdates( persons, day, report );
//		}
//	}


}

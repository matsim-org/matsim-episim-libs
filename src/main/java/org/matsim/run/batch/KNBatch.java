package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.SplittableRandom;


/**
 * sebastians playground
 */
public class KNBatch implements BatchRun<KNBatch.Params> {

	@Override
	public AbstractModule getBindings( int id, @Nullable Params params ) {
		return new AbstractModule(){
			@Override protected void configure(){
				binder().requireExplicitBindings();

				bind( InfectionModel.class ).to( DefaultInfectionModel.class ).in( Singleton.class );
				bind( ProgressionModel.class ).to( MyProgressionModel.class ).in( Singleton.class );
				bind( FaceMaskModel.class ).to( DefaultFaceMaskModel.class ).in( Singleton.class );
				bind( InitialInfectionHandler.class ).to( RandomInitialInfections.class ).in( Singleton.class );

				// Internal classes, should rarely be needed to be reconfigured
				bind( EpisimRunner.class ).in( Singleton.class );
				bind( ReplayHandler.class ).in( Singleton.class );
				bind( InfectionEventHandler.class ).in( Singleton.class );
				bind( EpisimReporting.class ).in( Singleton.class );
			}

			// yyyyyy does not work without the following lines:
//			@Provides Config config() {
//				return new SnzBerlinWeekScenario2020(25,false,false, OldSymmetricContactModel.class ).config();
//			}

			// yyyy where is the scenario binding coming from?

		};
	};

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "weekSymmetric");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		Config config = new SnzBerlinWeekScenario2020(25,false,false, OldSymmetricContactModel.class ).config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setCalibrationParameter( episimConfig.getCalibrationParameter() * params.factor );

		episimConfig.setInitialInfections( 10 );
		episimConfig.setInfections_pers_per_day( Collections.singletonMap( episimConfig.getStartDate(), 10 ) );

		episimConfig.setWriteEvents( EpisimConfigGroup.WriteEvents.all );

		final FixedPolicy.ConfigBuilder restrictions = FixedPolicy.config();
		episimConfig.setPolicy( FixedPolicy.class, restrictions.build() ); // overwrite snz policy with empty policy

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule( config, TracingConfigGroup.class );
		tracingConfig.setTracingCapacity_pers_per_day( 0 );

		return config;
	}

	public static final class Params {

		@GenerateSeeds(1)
		long seed;

		@Parameter( {0.01} )
		double factor;

//		@StringParameter({"2020-02-16", "2020-02-18"})
//		public String startDate;

//		@Parameter({0.25, 0.5, 1.})
//		private double eduCiCorrection;
//
//		@IntParameter({Integer.MAX_VALUE, 100})
//		private int tracingCapacity;
//
//		@StringParameter({"none", "cloth90", "FFP90"})
//		public String mask;
	}

	public static void main( String[] args ){
		String [] args2 = {
				RunParallel.OPTION_SETUP, org.matsim.run.batch.KNBatch.class.getName(),
				RunParallel.OPTION_PARAMS, KNBatch.Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString( 4 ),
				RunParallel.OPTION_ITERATIONS, Integer.toString( 365 )
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
		@Override public void beforeStateUpdates( Map<Id<Person>, EpisimPerson> persons, int day, EpisimReporting.InfectionReport report ){
			delegate.beforeStateUpdates( persons, day, report );
		}
	}


}

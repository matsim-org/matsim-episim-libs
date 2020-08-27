package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;

import org.matsim.episim.EpisimConfigGroup.InfectionParams;
import org.matsim.episim.model.AgeDependentProgressionModel;
import org.matsim.episim.model.ContactModel;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.InfectionModel;
import org.matsim.episim.model.InfectionModelWithSeasonality;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.model.SymmetricContactModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;
import org.matsim.vehicles.VehicleType;

import javax.annotation.Nullable;


/**
 * sebastians playground
 */
public class SMBatch implements BatchRun<SMBatch.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Object params) {
		return (AbstractModule) Modules.override(new SnzBerlinWeekScenario2020(25))
				.with(new AbstractModule() {
					@Override
					protected void configure() {
						bind(ContactModel.class).to(SymmetricContactModel.class).in(Singleton.class);
						bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);
						bind(InfectionModel.class).to(InfectionModelWithSeasonality.class).in(Singleton.class);
					}
					@Provides
					@Singleton
					public Scenario scenario(Config config) {

						// guice will use no args constructor by default, we check if this config was initialized
						// this is only the case when no explicit binding are required
						if (config.getModules().size() == 0)
							throw new IllegalArgumentException("Please provide a config module or binding.");

						config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);

						// save some time for not needed inputs
						config.facilities().setInputFile(null);
						
						final Scenario scenario = ScenarioUtils.loadScenario( config );
						
						double capFactor = 1.3;
						
						for( VehicleType vehicleType : scenario.getVehicles().getVehicleTypes().values() ){
							switch( vehicleType.getId().toString() ) {
								case "bus":
									vehicleType.getCapacity().setSeats( (int) (70 * capFactor));
									vehicleType.getCapacity().setStandingRoom( (int) (40 * capFactor) );
									// https://de.wikipedia.org/wiki/Stadtbus_(Fahrzeug)#Stehpl%C3%A4tze
									break;
								case "metro":
									vehicleType.getCapacity().setSeats( (int) (200 * capFactor) );
									vehicleType.getCapacity().setStandingRoom( (int) (550 * capFactor) );
									// https://mein.berlin.de/ideas/2019-04585/#:~:text=Ein%20Vollzug%20der%20Baureihe%20H,mehr%20Stehpl%C3%A4tze%20zur%20Verf%C3%BCgung%20stehen.
									break;
								case "plane":
									vehicleType.getCapacity().setSeats( (int) (200 * capFactor) );
									vehicleType.getCapacity().setStandingRoom( (int) (0 * capFactor) );
									break;
								case "pt":
									vehicleType.getCapacity().setSeats( (int) (70 * capFactor) );
									vehicleType.getCapacity().setStandingRoom( (int) (70 * capFactor) );
									break;
								case "ship":
									vehicleType.getCapacity().setSeats( (int) (150 * capFactor) );
									vehicleType.getCapacity().setStandingRoom( (int) (150 * capFactor) );
									// https://www.berlin.de/tourismus/dampferfahrten/faehren/1824948-1824660-faehre-f10-wannsee-altkladow.html
									break;
								case "train":
									vehicleType.getCapacity().setSeats( (int) (250 * capFactor) );
									vehicleType.getCapacity().setStandingRoom( (int) (750 * capFactor) );
									// https://de.wikipedia.org/wiki/Stadler_KISS#Technische_Daten_der_Varianten , mehr als ICE (https://inside.bahn.de/ice-baureihen/)
									break;
								case "tram":
									vehicleType.getCapacity().setSeats( (int) (84 * capFactor) );
									vehicleType.getCapacity().setStandingRoom( (int) (216 * capFactor) );
									// https://mein.berlin.de/ideas/2019-04585/#:~:text=Ein%20Vollzug%20der%20Baureihe%20H,mehr%20Stehpl%C3%A4tze%20zur%20Verf%C3%BCgung%20stehen.
									break;
								default:
									throw new IllegalStateException( "Unexpected value=|" + vehicleType.getId().toString() + "|");
							}
						}

						return scenario;
					}
				});
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "base");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020 module = new SnzBerlinWeekScenario2020();
		Config config = module.config();
		config.global().setRandomSeed(params.seed);
		
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setCalibrationParameter(params.calibrationParam);
		
		for( InfectionParams infParams : episimConfig.getInfectionParams() ){
			if ( infParams.includesActivity( "home" ) ){
				infParams.setContactIntensity( 1. );
			} else if ( infParams.includesActivity( "quarantine_home" ) ) {
				infParams.setContactIntensity( 0.3 );
			} else if ( infParams.getContainerName().startsWith( "shop" ) ) {
				infParams.setContactIntensity( 0.42 );
			} else if ( infParams.includesActivity( "work" ) || infParams.includesActivity(
					"business" ) || infParams.includesActivity( "errands" ) ) {
				infParams.setContactIntensity( 0.83 );
			} else if ( infParams.getContainerName().startsWith( "edu" ) ) {
				infParams.setContactIntensity( params.eduCi );
			} else if ( infParams.includesActivity( "pt" ) || infParams.includesActivity( "tr" )) {
				infParams.setContactIntensity( 3.33 );
			} else if ( infParams.includesActivity( "leisure" ) || infParams.includesActivity( "visit" ) ) {
				infParams.setContactIntensity( 9.24 );
			} else {
				throw new RuntimeException( "need to define contact intensity for activityType=" + infParams.getContainerName() );
			}
		}
		
		
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		// by default no tracing
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);
		
		ConfigBuilder builder = FixedPolicy.config();
		
		if (params.restriction.equals("none") || params.restriction.equals("calibr"));
		
		else if (params.restriction.equals("0.9FFP@PT")) builder.restrict(20, Restriction.ofMask(FaceMask.N95, 0.9), "pt");
		
		else if (params.restriction.equals("0.9CLOTH@PT")) builder.restrict(20, Restriction.ofMask(FaceMask.CLOTH, 0.9), "pt");
		
		else if (params.restriction.equals("work50")) builder.restrict(20, 0.5, "work");
		
		else if (params.restriction.equals("leisure50")) builder.restrict(20, 0.5, "leisure");
		
		else if (params.restriction.equals("shop50")) builder.restrict(20, 0.5, "shop_daily", "shop_other");
		
		else if (params.restriction.equals("edu50")) builder.restrict(20, 0.5, "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		
		else if (params.restriction.equals("edu0")) builder.restrict(20, 0., "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		else if (params.restriction.equals("outOfHome50")) builder.restrict(20, 0.5, AbstractSnzScenario2020.DEFAULT_ACTIVITIES);

		else throw new RuntimeException("Meausre not implemented: " + params.restriction);
		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(10)
		long seed;
		
		@Parameter({9.e-6})
		private double calibrationParam;
		
		@Parameter({11., 2.75})
		private double eduCi;
		
		@StringParameter({"none", "0.9FFP@PT", "0.9CLOTH@PT", "work50", "leisure50", "shop50", "edu50", "edu0", "outOfHome50"})
//		@StringParameter({"calibr"})
		public String restriction;

	}
	

}

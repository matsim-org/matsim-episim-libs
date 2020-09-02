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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

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
//						bind(InfectionModel.class).to(InfectionModelWithSeasonality.class).in(Singleton.class);
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
		
		double calibrationParam = 0;
		
		if (params.eduCi < 10.) calibrationParam = 9.e-6;// * 0.5; //12.e-6;
		
		else calibrationParam = 7.e-6;// * 0.5; //10.e-6;
		
		episimConfig.setCalibrationParameter(calibrationParam);
		episimConfig.setStartDate("2020-02-18");
		episimConfig.setInitialInfectionDistrict("Berlin");
		episimConfig.setInitialInfections(Integer.MAX_VALUE);
		
//		Map<LocalDate, Integer> importMap = new HashMap<>();
//		double importFactor = 2.;
//		int importOffset = params.importOffset;
//		importMap.put(episimConfig.getStartDate(), (int) Math.round(0.9 * importFactor));
//		importMap.put(LocalDate.of(2020, 2, 25).plusDays(importOffset), (int) Math.round(2.2 * importFactor));
//		importMap.put(LocalDate.of(2020, 2, 26).plusDays(importOffset), (int) Math.round(3.5 * importFactor));
//		importMap.put(LocalDate.of(2020, 2, 27).plusDays(importOffset), (int) Math.round(4.8 * importFactor));
//		importMap.put(LocalDate.of(2020, 2, 28).plusDays(importOffset), (int) Math.round(6.1 * importFactor));
//		importMap.put(LocalDate.of(2020, 2, 29).plusDays(importOffset), (int) Math.round(7.4 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 1).plusDays(importOffset), (int) Math.round(8.6 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 2).plusDays(importOffset), (int) Math.round(9.9 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 3).plusDays(importOffset), (int) Math.round(11.8 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 4).plusDays(importOffset), (int) Math.round(13.7 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 5).plusDays(importOffset), (int) Math.round(15.6 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 6).plusDays(importOffset), (int) Math.round(17.4 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 7).plusDays(importOffset), (int) Math.round(19.3 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 8).plusDays(importOffset), (int) Math.round(21.2 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 9).plusDays(importOffset), (int) Math.round(23.1 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 10).plusDays(importOffset), (int) Math.round(21.7 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 11).plusDays(importOffset), (int) Math.round(20.4 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 12).plusDays(importOffset), (int) Math.round(19. * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 13).plusDays(importOffset), (int) Math.round(17.7 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 14).plusDays(importOffset), (int) Math.round(16.3 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 15).plusDays(importOffset), (int) Math.round(15. * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 16).plusDays(importOffset), (int) Math.round(13.6 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 17).plusDays(importOffset), (int) Math.round(12.2 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 18).plusDays(importOffset), (int) Math.round(10.8 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 19).plusDays(importOffset), (int) Math.round(9.5 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 20).plusDays(importOffset), (int) Math.round(8.1 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 21).plusDays(importOffset), (int) Math.round(6.7 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 22).plusDays(importOffset), (int) Math.round(5.3 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 23).plusDays(importOffset), (int) Math.round(3.9 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 24).plusDays(importOffset), (int) Math.round(3.5 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 25).plusDays(importOffset), (int) Math.round(3.1 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 26).plusDays(importOffset), (int) Math.round(2.6 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 27).plusDays(importOffset), (int) Math.round(2.2 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 28).plusDays(importOffset), (int) Math.round(1.8 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 29).plusDays(importOffset), (int) Math.round(1.4 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 30).plusDays(importOffset), (int) Math.round(1. * importFactor));
//		importMap.put(LocalDate.of(2020, 4, 6).plusDays(importOffset), (int) Math.round(0.4 * importFactor));
//		importMap.put(LocalDate.of(2020, 4, 13).plusDays(importOffset), (int) Math.round(0.1 * importFactor));
//		
//		episimConfig.setInfections_pers_per_day(importMap);
		
//		episimConfig.setInfections_pers_per_day(Map.of(
//				episimConfig.getStartDate(), (int) (0.9 * importFactor),
//				LocalDate.of(2020, 3, 2), (int) (9.9 * importFactor),
//				LocalDate.of(2020, 3, 9), (int) (23.1 * importFactor),
//				LocalDate.of(2020, 3, 16), (int) (13.6 * importFactor),
//				LocalDate.of(2020, 3, 23), (int) (3.9 * importFactor),
//				LocalDate.of(2020, 3, 30), (int) (1.0 * importFactor),
//				LocalDate.of(2020, 4, 6),  1
//		));
		
		for( InfectionParams infParams : episimConfig.getInfectionParams() ){
			if ( infParams.includesActivity( "home" ) ){
				infParams.setContactIntensity( 1. );
			} else if ( infParams.includesActivity( "quarantine_home" ) ) {
				infParams.setContactIntensity( 0.3 );
			} else if ( infParams.getContainerName().startsWith( "shop" ) ) {
				infParams.setContactIntensity( 0.88 );
			} else if ( infParams.includesActivity( "work" ) || infParams.includesActivity(
					"business" ) || infParams.includesActivity( "errands" ) ) {
				infParams.setContactIntensity( 1.47 );
			} else if ( infParams.getContainerName().startsWith( "edu" ) ) {
				infParams.setContactIntensity( params.eduCi );
			} else if ( infParams.includesActivity( "pt" ) || infParams.includesActivity( "tr" )) {
				infParams.setContactIntensity( 10. );
			} else if ( infParams.includesActivity( "leisure" ) || infParams.includesActivity( "visit" ) ) {
				infParams.setContactIntensity( 9.24 );
			} else {
				throw new RuntimeException( "need to define contact intensity for activityType=" + infParams.getContainerName() );
			}
		}
		
//		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		// by default no tracing
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);
		
		ConfigBuilder builder = FixedPolicy.config();
		{
			if (params.restriction.startsWith("none") || params.restriction.startsWith("calibr"));
			
			else if (params.restriction.equals("0.9FFP@PT&SHOP")) builder.restrict(20, Restriction.ofMask(FaceMask.N95, 0.9), "pt", "shop_daily", "shop_other");
			
			else if (params.restriction.equals("0.9CLOTH@PT&SHOP")) builder.restrict(20, Restriction.ofMask(FaceMask.CLOTH, 0.9), "pt", "shop_daily", "shop_other");
			
			else if (params.restriction.equals("work50")) builder.restrict(20, 0.5, "work");
			
			else if (params.restriction.equals("leisure50")) builder.restrict(20, 0.5, "leisure");
			
			else if (params.restriction.equals("shop50")) builder.restrict(20, 0.5, "shop_daily", "shop_other");
			
			else if (params.restriction.equals("educ50")) builder.restrict(20, 0.5, "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
			else if (params.restriction.equals("educ_kiga50")) builder.restrict(20, 0.5, "educ_kiga");
			else if (params.restriction.equals("educ_primary50")) builder.restrict(20, 0.5, "educ_primary");
			else if (params.restriction.equals("educ_secondary50")) builder.restrict(20, 0.5, "educ_secondary");
			else if (params.restriction.equals("educ_tertiary50")) builder.restrict(20, 0.5, "educ_tertiary");
			else if (params.restriction.equals("educ_higher50")) builder.restrict(20, 0.5, "educ_higher");
			else if (params.restriction.equals("educ_other50")) builder.restrict(20, 0.5, "educ_other");
			
			else if (params.restriction.equals("educ0")) builder.restrict(20, 0., "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
			else if (params.restriction.equals("educ_kiga0")) builder.restrict(20, 0., "educ_kiga");
			else if (params.restriction.equals("educ_primary0")) builder.restrict(20, 0., "educ_primary");
			else if (params.restriction.equals("educ_secondary0")) builder.restrict(20, 0., "educ_secondary");
			else if (params.restriction.equals("educ_tertiary0")) builder.restrict(20, 0., "educ_tertiary");
			else if (params.restriction.equals("educ_higher0")) builder.restrict(20, 0., "educ_higher");
			else if (params.restriction.equals("educ_other0")) builder.restrict(20, 0., "educ_other");
			
			else if (params.restriction.equals("0.9FFP@EDU")) builder.restrict(20, Restriction.ofMask(FaceMask.N95, 0.9), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
			
			else if (params.restriction.equals("0.9CLOTH@EDU")) builder.restrict(20, Restriction.ofMask(FaceMask.CLOTH, 0.9), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
			
			else if (params.restriction.equals("outOfHome50")) builder.restrict(20, 0.5, AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
	
			else throw new RuntimeException("Measure not implemented: " + params.restriction);
		}
		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(20)
		long seed;
		
//		@Parameter({8.e-6})
//		private double calibrationParam;
		
		@Parameter({2.75, 11})
		private double eduCi;
		
//		@IntParameter({0, -5})
//		private int importOffset;
		
		@StringParameter({"none", "0.9FFP@PT&SHOP", "0.9CLOTH@PT&SHOP", "work50", "leisure50", "shop50", "educ50", "educ_kiga50", 
			"educ_primary50", "educ_secondary50", "educ_tertiary50", "educ_higher50", "educ_other50", "educ0", "educ_kiga0", 
			"educ_primary0", "educ_secondary0", "educ_tertiary0", "educ_higher0", "educ_other0", "outOfHome50", "0.9FFP@EDU", "0.9CLOTH@EDU"})
//		@StringParameter({"calibr-noCi-increasedImportv5"})
//		@StringParameter({"calibr-increasedImport-0109-v4"})
		public String restriction;

	}
	

}

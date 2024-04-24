/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.run.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.TracingConfigGroup.CapacityType;
import org.matsim.episim.model.*;
import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
import org.matsim.episim.model.vaccination.VaccinationByAge;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.vehicles.VehicleType;

import javax.inject.Singleton;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Scenario for Germany using Senozon data.
 * Not calibrated and only for computational or structural analysis.
 */
public final class SnzGermanyScenario extends AbstractModule {

	/**
	 * Path pointing to the input folder. Must be configured at runtime with EPISIM_INPUT variable.
	 */
	public static final Path INPUT = EpisimUtils.resolveInputPath("./path-not-set");

	/**
	 * Enable tracing.
	 */
	private final boolean tracing = true;

	private double imprtFctMult = 1.;
	private double importFactorBeforeJune = 4.;
	private double importFactorAfterJune = 0.5;

	/**
	 * Empty constructor is needed for running scenario from command line.
	 */
	@SuppressWarnings("unused")
	private SnzGermanyScenario() {
	}

	@Override
	protected void configure() {
		bind(ContactModel.class).to(SymmetricContactModel.class).in(Singleton.class);
		bind(DiseaseStatusTransitionModel.class).to(AgeDependentDiseaseStatusTransitionModel.class).in(Singleton.class);
		bind(InfectionModel.class).to(AgeDependentInfectionModelWithSeasonality.class).in(Singleton.class);
		bind(VaccinationModel.class).to(VaccinationByAge.class).in(Singleton.class);
	}

	public static void interpolateImport(Map<LocalDate, Integer> importMap, double importFactor, LocalDate start, LocalDate end, double a, double b) {
		int days = end.getDayOfYear() - start.getDayOfYear();
		for (int i = 1; i <= days; i++) {
			double fraction = (double) i / days;
			importMap.put(start.plusDays(i), (int) Math.round(importFactor * (a + fraction * (b - a))));
		}
	}

	@Provides
	@Singleton
	public Config config() {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		config.vehicles().setVehiclesFile(INPUT.resolve("de_2020-vehicles.xml").toString());

		config.plans().setInputFile("germany_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz");

//		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_wt_%dpt_split_wRestaurants.xml.gz", sample))
//				.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
//
//		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_sa_%dpt_split_wRestaurants.xml.gz", sample))
//				.addDays(DayOfWeek.SATURDAY);
//
//		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_so_%dpt_split_wRestaurants.xml.gz", sample))
//				.addDays(DayOfWeek.SUNDAY);

		episimConfig.addInputEventsFile("de2020gsmwt_events_reduced.xml.gz.xml.gz")
				.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		episimConfig.addInputEventsFile("de2020gsmsa_events_reduced.xml.gz.xml.gz")
				.addDays(DayOfWeek.SATURDAY);

		episimConfig.addInputEventsFile("de2020gsmso_events_reduced.xml.gz.xml.gz")
				.addDays(DayOfWeek.SUNDAY);

		episimConfig.setCalibrationParameter(1.7E-5 * 0.8);
		episimConfig.setStartDate("2020-02-25");
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);
		episimConfig.setSampleSize(1);
		episimConfig.setHospitalFactor(0.5);
		episimConfig.setProgressionConfig(AbstractSnzScenario2020.baseProgressionConfig(Transition.config()).build());


		//inital infections and import
		episimConfig.setInitialInfections(Integer.MAX_VALUE);

		episimConfig.setInitialInfectionDistrict(null);
		Map<LocalDate, Integer> importMap = new HashMap<>();
		interpolateImport(importMap, imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-02-24"),
				LocalDate.parse("2020-03-09"), 0.9, 23.1);
		interpolateImport(importMap, imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-03-09"),
				LocalDate.parse("2020-03-23"), 23.1, 3.9);
		interpolateImport(importMap, imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-03-23"),
				LocalDate.parse("2020-04-13"), 3.9, 0.1);
		interpolateImport(importMap, imprtFctMult * importFactorAfterJune, LocalDate.parse("2020-06-08"),
				LocalDate.parse("2020-07-13"), 0.1, 2.7);
		interpolateImport(importMap, imprtFctMult * importFactorAfterJune, LocalDate.parse("2020-07-13"),
				LocalDate.parse("2020-08-10"), 2.7, 17.9);
		interpolateImport(importMap, imprtFctMult * importFactorAfterJune, LocalDate.parse("2020-08-10"),
				LocalDate.parse("2020-09-07"), 17.9, 6.1);
		interpolateImport(importMap, imprtFctMult * importFactorAfterJune, LocalDate.parse("2020-10-26"),
				LocalDate.parse("2020-12-21"), 6.1, 1.1);

		episimConfig.setInfections_pers_per_day(importMap);


		int spaces = 20;
		//contact intensities
		episimConfig.getOrAddContainerParams("pt", "tr").setContactIntensity(10.0).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("work").setContactIntensity(1.47).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("leisure").setContactIntensity(9.24).setSpacesPerFacility(spaces).setSeasonality(1.0);
//		episimConfig.getOrAddContainerParams("restaurant").setContactIntensity(9.24).setSpacesPerFacility(spaces).setSeasonal(true);
		episimConfig.getOrAddContainerParams("educ_kiga").setContactIntensity(11.0).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("educ_primary").setContactIntensity(11.0).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("educ_secondary").setContactIntensity(11.0).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("educ_tertiary").setContactIntensity(11.).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("educ_higher").setContactIntensity(5.5).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("educ_other").setContactIntensity(11.).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("shop_daily").setContactIntensity(0.88).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("shop_other").setContactIntensity(0.88).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("errands").setContactIntensity(1.47).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("business").setContactIntensity(1.47).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("visit").setContactIntensity(9.24).setSpacesPerFacility(spaces); // 33/3.57
		episimConfig.getOrAddContainerParams("home").setContactIntensity(1.0).setSpacesPerFacility(1); // 33/33
		episimConfig.getOrAddContainerParams("quarantine_home").setContactIntensity(1.0).setSpacesPerFacility(1); // 33/33


		//restrictions and masks
		SnzBerlinScenario25pct2020.BasePolicyBuilder basePolicyBuilder = new SnzBerlinScenario25pct2020.BasePolicyBuilder(episimConfig);
		basePolicyBuilder.setCiCorrections(Map.of());
		FixedPolicy.ConfigBuilder builder = basePolicyBuilder.buildFixed();

		//tracing
		if (tracing) {
			TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
//			int offset = (int) (ChronoUnit.DAYS.between(episimConfig.getStartDate(), LocalDate.parse("2020-04-01")) + 1);
			int offset = 46;
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(offset);
			tracingConfig.setTracingProbability(0.5);
			tracingConfig.setTracingPeriod_days(2);
			tracingConfig.setMinContactDuration_sec(15 * 60.);
			tracingConfig.setQuarantineHouseholdMembers(true);
			tracingConfig.setEquipmentRate(1.);
			tracingConfig.setTracingDelay_days(5);
			tracingConfig.setTraceSusceptible(true);
			tracingConfig.setCapacityType(CapacityType.PER_PERSON);
			int tracingCapacity = 200;
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					LocalDate.of(2020, 4, 1), (int) (tracingCapacity * 0.2),
					LocalDate.of(2020, 6, 15), tracingCapacity
			));
		}

		Map<LocalDate, DayOfWeek> inputDays = new HashMap<>();

		inputDays.put(LocalDate.parse("2020-12-21"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2020-12-22"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2020-12-23"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2020-12-24"), DayOfWeek.SUNDAY);
		inputDays.put(LocalDate.parse("2020-12-25"), DayOfWeek.SUNDAY);
		inputDays.put(LocalDate.parse("2020-12-26"), DayOfWeek.SUNDAY);

		inputDays.put(LocalDate.parse("2020-12-28"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2020-12-29"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2020-12-30"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2020-12-31"), DayOfWeek.SUNDAY);
		inputDays.put(LocalDate.parse("2021-01-01"), DayOfWeek.SUNDAY);

		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			double fraction = 0.5925;

			builder.restrict(LocalDate.parse("2020-12-24"), 1.0, act);
		}


		inputDays.put(LocalDate.parse("2021-03-08"), DayOfWeek.SUNDAY);
		inputDays.put(LocalDate.parse("2021-04-02"), DayOfWeek.SUNDAY);
		inputDays.put(LocalDate.parse("2021-04-05"), DayOfWeek.SUNDAY);

		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			double fraction = 0.72;
			builder.restrict(LocalDate.parse("2021-04-02"), 1.0, act);
			builder.restrict(LocalDate.parse("2021-04-03"), 1.0, act);
			builder.restrict(LocalDate.parse("2021-04-04"), 1.0, act);
			builder.restrict(LocalDate.parse("2021-04-05"), 1.0, act);
			builder.restrict(LocalDate.parse("2021-04-06"), fraction, act);
		}


		episimConfig.setInputDays(inputDays);

		episimConfig.setLeisureOutdoorFraction(Map.of(
				LocalDate.of(2020, 1, 1), 0.)
		);

		//leisure factor
		double leisureFactor = 1.6;
		builder.apply("2020-10-15", "2020-12-14", (d, e) -> e.put("fraction", 1 - leisureFactor * (1 - (double) e.get("fraction"))), "leisure");


		episimConfig.setPolicy(FixedPolicy.class, builder.build());
		config.controler().setOutputDirectory("output-snz-germany");

		return config;
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

		ControlerUtils.checkConfigConsistencyAndWriteToLog(config, "before loading scenario");

		final Scenario scenario = ScenarioUtils.loadScenario(config);

		double capFactor = 1.3;

		for (VehicleType vehicleType : scenario.getVehicles().getVehicleTypes().values()) {
			switch (vehicleType.getId().toString()) {
				case "bus":
					vehicleType.getCapacity().setSeats((int) (70 * capFactor));
					vehicleType.getCapacity().setStandingRoom((int) (40 * capFactor));
					// https://de.wikipedia.org/wiki/Stadtbus_(Fahrzeug)#Stehpl%C3%A4tze
					break;
				case "metro":
					vehicleType.getCapacity().setSeats((int) (200 * capFactor));
					vehicleType.getCapacity().setStandingRoom((int) (550 * capFactor));
					// https://mein.berlin.de/ideas/2019-04585/#:~:text=Ein%20Vollzug%20der%20Baureihe%20H,mehr%20Stehpl%C3%A4tze%20zur%20Verf%C3%BCgung%20stehen.
					break;
				case "plane":
					vehicleType.getCapacity().setSeats((int) (200 * capFactor));
					vehicleType.getCapacity().setStandingRoom((int) (0 * capFactor));
					break;
				case "pt":
					vehicleType.getCapacity().setSeats((int) (70 * capFactor));
					vehicleType.getCapacity().setStandingRoom((int) (70 * capFactor));
					break;
				case "ship":
					vehicleType.getCapacity().setSeats((int) (150 * capFactor));
					vehicleType.getCapacity().setStandingRoom((int) (150 * capFactor));
					// https://www.berlin.de/tourismus/dampferfahrten/faehren/1824948-1824660-faehre-f10-wannsee-altkladow.html
					break;
				case "train":
					vehicleType.getCapacity().setSeats((int) (250 * capFactor));
					vehicleType.getCapacity().setStandingRoom((int) (750 * capFactor));
					// https://de.wikipedia.org/wiki/Stadler_KISS#Technische_Daten_der_Varianten , mehr als ICE (https://inside.bahn.de/ice-baureihen/)
					break;
				case "tram":
					vehicleType.getCapacity().setSeats((int) (84 * capFactor));
					vehicleType.getCapacity().setStandingRoom((int) (216 * capFactor));
					// https://mein.berlin.de/ideas/2019-04585/#:~:text=Ein%20Vollzug%20der%20Baureihe%20H,mehr%20Stehpl%C3%A4tze%20zur%20Verf%C3%BCgung%20stehen.
					break;
				default:
					throw new IllegalStateException("Unexpected value=|" + vehicleType.getId().toString() + "|");
			}
		}

		return scenario;
	}
}

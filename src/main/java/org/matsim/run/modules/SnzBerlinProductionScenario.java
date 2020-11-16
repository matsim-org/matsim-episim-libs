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
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinScenario25pct2020.BasePolicyBuilder;
import org.matsim.vehicles.VehicleType;

import javax.inject.Singleton;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Scenario for Berlin using Senozon events for different weekdays.
 */
public class SnzBerlinProductionScenario extends AbstractModule {

	public static enum DiseaseImport {yes, no}
	public static enum Restrictions {yes, no, onlyEdu, allExceptSchoolsAndDayCare, allExceptUniversities, allExceptEdu}
	public static enum Masks {yes, no}
	public static enum Tracing {yes, no}
	public static enum Snapshot {no, episim_snapshot_030_2020_03_16, episim_snapshot_060_2020_04_15, episim_snapshot_090_2020_05_15, episim_snapshot_120_2020_06_14, episim_snapshot_150_2020_07_14, episim_snapshot_180_2020_08_13, episim_snapshot_210_2020_09_12, episim_snapshot_240_2020_10_12}

	private final int sample;
	private final DiseaseImport diseaseImport;
	private final Restrictions restrictions;
	private final Masks masks;
	private final Tracing tracing;
	private final Snapshot snapshot;
	private final Class<? extends InfectionModel> infectionModel;


	/**
	 * Path pointing to the input folder. Can be configured at runtime with EPISIM_INPUT variable.
	 */
	private static Path INPUT = EpisimUtils.resolveInputPath("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input");

	public SnzBerlinProductionScenario() {
		this(25, DiseaseImport.yes, Restrictions.yes, Masks.yes, Tracing.yes, Snapshot.no, AgeDependentInfectionModelWithSeasonality.class);
	}

	public SnzBerlinProductionScenario(int sample, DiseaseImport diseaseImport, Restrictions restrictions, Masks masks, Tracing tracing, Snapshot snapshot, Class<? extends InfectionModel> infectionModel) {
		this.sample = sample;
		this.diseaseImport = diseaseImport;
		this.restrictions = restrictions;
		this.masks = masks;
		this.tracing = tracing;
		this.snapshot = snapshot;
		this.infectionModel = infectionModel;
	}

	private static Map<LocalDate, Integer> interpolateImport(Map<LocalDate, Integer> importMap, double importFactor, LocalDate start, LocalDate end, double a, double b) {
		int days = end.getDayOfYear() - start.getDayOfYear();
		for (int i = 1; i <= days; i++) {
			double fraction = (double) i / days;
			importMap.put(start.plusDays(i), (int) Math.round(importFactor * (a + fraction * (b - a))));
		}
		return importMap;
	}

	/**
	 * Resolve input for sample size. Smaller than 25pt samples are in a different subfolder.
	 */
	private static String inputForSample(String base, int sample) {
		Path folder = (sample == 100 | sample == 25) ? INPUT : INPUT.resolve("samples");
		return folder.resolve(String.format(base, sample)).toString();
	}

	@Override
	protected void configure() {
		bind(ContactModel.class).to(SymmetricContactModel.class).in(Singleton.class);
		bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);
		bind(InfectionModel.class).to(infectionModel).in(Singleton.class);
	}

	@Provides
	@Singleton
	public Config config() {

		if (this.sample != 25) throw new RuntimeException("Sample size not calibrated! Currently only 25% is calibrated. Comment this line out to continue.");

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		config.vehicles().setVehiclesFile(INPUT.resolve("de_2020-vehicles.xml").toString());

		config.plans().setInputFile(inputForSample("be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_%dpt_split.xml.gz", sample));

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_wt_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_sa_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.SATURDAY);

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_so_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.SUNDAY);

		episimConfig.setCalibrationParameter(1.7E-5);
		episimConfig.setStartDate("2020-02-16");
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);
		episimConfig.setSampleSize(this.sample / 100.);
		episimConfig.setHospitalFactor(1.);
		episimConfig.setProgressionConfig(AbstractSnzScenario2020.baseProgressionConfig(Transition.config()).build());

		if (this.snapshot != Snapshot.no) episimConfig.setStartFromSnapshot(INPUT.resolve("snapshots/" + snapshot + ".zip").toString());

//		episimConfig.setSnapshotInterval(14);

		//inital infections and import
		episimConfig.setInitialInfections(Integer.MAX_VALUE);
		if (this.diseaseImport == DiseaseImport.yes) {
			episimConfig.setInitialInfectionDistrict(null);
			Map<LocalDate, Integer> importMap = new HashMap<>();
			double importFactor = 1.;
			importMap.put(episimConfig.getStartDate(), Math.max(1, (int) Math.round(0.9 * importFactor)));
			int importOffset = 0;
			importMap = interpolateImport(importMap, importFactor, LocalDate.parse("2020-02-24").plusDays(importOffset),
					LocalDate.parse("2020-03-09").plusDays(importOffset), 0.9, 23.1);
			importMap = interpolateImport(importMap, importFactor, LocalDate.parse("2020-03-09").plusDays(importOffset),
					LocalDate.parse("2020-03-23").plusDays(importOffset), 23.1, 3.9);
			importMap = interpolateImport(importMap, importFactor, LocalDate.parse("2020-03-23").plusDays(importOffset),
					LocalDate.parse("2020-04-13").plusDays(importOffset), 3.9, 0.1);
			importMap = interpolateImport(importMap, importFactor, LocalDate.parse("2020-06-08").plusDays(importOffset),
					LocalDate.parse("2020-07-13").plusDays(importOffset), 0.1, 2.7);
			importMap = interpolateImport(importMap, importFactor, LocalDate.parse("2020-07-13").plusDays(importOffset),
					LocalDate.parse("2020-08-10").plusDays(importOffset), 2.7, 17.9);
			importMap = interpolateImport(importMap, importFactor, LocalDate.parse("2020-08-10").plusDays(importOffset),
					LocalDate.parse("2020-09-07").plusDays(importOffset), 17.9, 5.4);
			episimConfig.setInfections_pers_per_day(importMap);
		}
		else {
			episimConfig.setInitialInfectionDistrict("Berlin");
			episimConfig.setCalibrationParameter(2.54e-5);
		}

		if (this.infectionModel != AgeDependentInfectionModelWithSeasonality.class) {
			if (this.diseaseImport == DiseaseImport.yes) {
				episimConfig.setCalibrationParameter(1.6E-5);
			}
			else {
				episimConfig.setCalibrationParameter(1.6E-5 * 2.54e-5 / 1.7E-5);
			}
		}

		int spaces = 20;
		//contact intensities
		episimConfig.getOrAddContainerParams("pt", "tr").setContactIntensity(10.0).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("work").setContactIntensity(1.47).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("leisure").setContactIntensity(9.24).setSpacesPerFacility(spaces);
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
		episimConfig.getOrAddContainerParams("visit").setContactIntensity(9.24).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("home").setContactIntensity(1.0).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("quarantine_home").setContactIntensity(1.0).setSpacesPerFacility(1);

		//restrictions and masks
		BasePolicyBuilder basePolicyBuilder = new BasePolicyBuilder(episimConfig);
		if (this.restrictions == Restrictions.no || this.restrictions == Restrictions.onlyEdu) basePolicyBuilder.setCsv(null);
		if (this.restrictions == Restrictions.no || this.restrictions == Restrictions.allExceptEdu || this.restrictions == Restrictions.allExceptSchoolsAndDayCare) basePolicyBuilder.setRestrictSchoolsAndDayCare(false);
		if (this.restrictions == Restrictions.no || this.restrictions == Restrictions.allExceptEdu || this.restrictions == Restrictions.allExceptUniversities) basePolicyBuilder.setRestrictUniversities(false);

		if (this.masks == Masks.no) basePolicyBuilder.setMaskCompliance(0);
		basePolicyBuilder.setCiCorrections(Map.of());
		FixedPolicy.ConfigBuilder builder = basePolicyBuilder.build();
		builder.restrict("2020-08-08", Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		//tracing
		if (this.tracing == Tracing.yes) {
			TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
			int offset = (int) (ChronoUnit.DAYS.between(episimConfig.getStartDate(), LocalDate.parse("2020-04-01")) + 1);
			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(offset);
			tracingConfig.setTracingProbability(0.6);
			tracingConfig.setTracingPeriod_days(2);
			tracingConfig.setMinContactDuration_sec(15 * 60.);
			tracingConfig.setQuarantineHouseholdMembers(true);
			tracingConfig.setEquipmentRate(1.);
			tracingConfig.setTracingDelay_days(4);
			tracingConfig.setTraceSusceptible(true);
			tracingConfig.setCapacityType(CapacityType.PER_CONTACT_PERSON);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					LocalDate.of(2020, 4, 1), 300,
					LocalDate.of(2020, 6, 15), 2000
			));
		}

		config.controler().setOutputDirectory("output-snzWeekScenario-" + sample + "%");

		return config;
	}

	@Provides
	@Singleton
	private Scenario scenario(Config config) {

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

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

import com.google.inject.Provides;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimConfigGroup.InfectionParams;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinScenario25pct2020.BasePolicyBuilder;
import org.matsim.vehicles.VehicleType;

import javax.inject.Singleton;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Model for Berlin week with different configurations.
 */
public class SnzBerlinWeekScenario2020 extends AbstractSnzScenario2020 {

	/**
	 * Calibration parameter for no disease import.
	 */
	private static final Map<Class<? extends ContactModel>, Double> CALIB = Map.of(
			OldSymmetricContactModel.class, 1.07e-5,
			SymmetricContactModel.class, 2.54e-5, // nSpaces=20
			DefaultContactModel.class, 1.45e-5,
			PairWiseContactModel.class, 1.91e-5
	);
	/**
	 * Sample size of the scenario (Either 25 or 100)
	 */
	private final int sample;

	/**
	 * Enable Disease import based on RKI numbers.
	 */
	private final boolean withDiseaseImport;

	/**
	 * Enable modified CI values based on room sizes.
	 */
	private final boolean withModifiedCi;

	/**
	 * The contact model to use.
	 */
	private final Class<? extends ContactModel> contactModel;

	public SnzBerlinWeekScenario2020() {
		this(25, true, true, OldSymmetricContactModel.class);
	}

	/**
	 * Create new scenario module.
	 * @param sample sample sizes to use, currently 1, 10, 25, 100 are supported
	 * @param withDiseaseImport enable disease import data from rki, otherwise will be constant each day
	 * @param withModifiedCi use new ci values based on avg. room sizes, otherwise ci will be same for each activity.
	 * @param contactModel contact model to use
	 */
	public SnzBerlinWeekScenario2020(int sample, boolean withDiseaseImport, boolean withModifiedCi, Class<? extends ContactModel> contactModel) {
		this.sample = sample;
		this.withDiseaseImport = withDiseaseImport;
		this.withModifiedCi = withModifiedCi;
		this.contactModel = contactModel;
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
		Path folder = (sample == 100 | sample == 25) ? SnzBerlinScenario25pct2020.INPUT : SnzBerlinScenario25pct2020.INPUT.resolve("samples");
		return folder.resolve(String.format(base, sample)).toString();
	}

	@Override
	protected void configure() {
		bind(ContactModel.class).to(contactModel).in(Singleton.class);
		bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);

		if (contactModel == DefaultContactModel.class) {
			bind(InfectionModel.class).to(InfectionModelWithSeasonality.class).in(Singleton.class);
		} else
			bind(InfectionModel.class).to(AgeDependentInfectionModelWithSeasonality.class).in(Singleton.class);
	}

	@Provides
	@Singleton
	public Config config() {

		Config config = new SnzBerlinScenario25pct2020().config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.clearInputEventsFiles();

		config.plans().setInputFile(inputForSample("be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_%dpt_split.xml.gz", sample));

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_wt_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_sa_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.SATURDAY);

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_so_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.SUNDAY);


		//for (InfectionParams infParams : episimConfig.getInfectionParams()) {
		//	if (!infParams.includesActivity("home")) infParams.setSpacesPerFacility(1);
		//}

		episimConfig.setStartDate("2020-02-18");

		BasePolicyBuilder basePolicyBuilder = new BasePolicyBuilder(episimConfig);

		//import numbers based on https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Situationsberichte/Sept_2020/2020-09-22-de.pdf?__blob=publicationFile
		//values are calculated here: https://docs.google.com/spreadsheets/d/1aJ2XonFpfjKCpd0ZeXmzKe5fmDe0HHBGtXfJ-NDBolo/edit#gid=0

		if (withDiseaseImport) {
			basePolicyBuilder.setCiCorrections(Map.of());
			episimConfig.setInitialInfectionDistrict(null);
			episimConfig.setInitialInfections(Integer.MAX_VALUE);
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

			episimConfig.setCalibrationParameter(6.e-6);

			// new param 9.50e-06?

		} else {

			episimConfig.setCalibrationParameter(CALIB.get(contactModel));

			if (contactModel == OldSymmetricContactModel.class) {
				basePolicyBuilder.setCiCorrections(Map.of("2020-03-08", 0.48));
			} else if (contactModel == SymmetricContactModel.class) {
				basePolicyBuilder.setCiCorrections(Map.of("2020-03-09", 0.74));
			} else if (contactModel == DefaultContactModel.class) {
				basePolicyBuilder.setCiCorrections(Map.of("2020-03-05", 0.53));
			}


			// this was the old value
			//basePolicyBuilder.setCiCorrections(Map.of("2020-03-07", 0.6));

		}

		if (withModifiedCi) {
			for (InfectionParams infParams : episimConfig.getInfectionParams()) {
				if (infParams.includesActivity("home")) {
					infParams.setContactIntensity(1.);
				} else if (infParams.includesActivity("quarantine_home")) {
					infParams.setContactIntensity(0.3);
				} else if (infParams.getContainerName().startsWith("shop")) {
					infParams.setContactIntensity(0.88);
				} else if (infParams.includesActivity("work") || infParams.includesActivity(
						"business") || infParams.includesActivity("errands")) {
					infParams.setContactIntensity(1.47);
				} else if (infParams.getContainerName().startsWith("edu")) {
					infParams.setContactIntensity(11.);
				} else if (infParams.includesActivity("pt") || infParams.includesActivity("tr")) {
					infParams.setContactIntensity(10.);
				} else if (infParams.includesActivity("leisure") || infParams.includesActivity("visit")) {
					infParams.setContactIntensity(9.24);
				} else {
					throw new RuntimeException("need to define contact intensity for activityType=" + infParams.getContainerName());
				}
			}
		}

		FixedPolicy.ConfigBuilder builder = basePolicyBuilder.build();

		// yyyyyy why this? Could you please comment?  kai, sep/20
		// we're setting ciCorrection at educ facilities to 0.5 after summer holidays (the assumption is that from that point onwards windows are opened regularly)
		builder.restrict("2020-08-08", Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30,
				LocalDate.of(2020, 6, 15), Integer.MAX_VALUE
		));

		//episimConfig.setWriteEvents(EpisimConfigGroup.WriteEvents.tracing);

		config.controler().setOutputDirectory(config.controler().getOutputDirectory() + "-week-" + episimConfig.getCalibrationParameter());

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

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
import com.google.inject.multibindings.Multibinder;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.episim.model.activity.ActivityParticipationModel;
import org.matsim.episim.model.activity.DefaultParticipationModel;
import org.matsim.episim.model.activity.LocationBasedParticipationModel;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.model.listener.HouseholdSusceptibility;
import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
import org.matsim.episim.model.vaccination.VaccinationFromData;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.policy.ShutdownPolicy;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Scenario for Cologne using Senozon events for different weekdays.
 */
public final class SnzCologneProductionScenario extends SnzProductionScenario {

	public static class Builder extends SnzProductionScenario.Builder<SnzCologneProductionScenario> {

		private double leisureOffset = 0.0;
		private double scale = 1.0;
		private boolean leisureNightly = false;
		private double leisureNightlyScale = 1.0;
		private double householdSusc = 1.0;

		public Builder() {
			this.vaccinationModel = VaccinationFromData.class;
		}

		@Override
		public SnzCologneProductionScenario build() {
			return new SnzCologneProductionScenario(this);
		}

		@Deprecated
		public SnzCologneProductionScenario createSnzCologneProductionScenario() {
			return build();
		}

		public Builder setLeisureOffset(double offset) {
			this.leisureOffset = offset;
			return this;
		}

		public Builder setScale(double scale) {
			this.scale = scale;
			return this;
		}

		public Builder setLeisureNightly(boolean leisureNightly) {
			this.leisureNightly = leisureNightly;
			return this;
		}

		public Builder setLeisureNightlyScale(double leisureNightlyScale) {
			this.leisureNightlyScale = leisureNightlyScale;
			return this;
		}

		public Builder setHouseholdSusc(double householdSusc) {
			this.householdSusc = householdSusc;
			return this;
		}
	}

	private final int sample;
	private final int importOffset;
	private final DiseaseImport diseaseImport;
	private final Restrictions restrictions;
	private final Tracing tracing;
	private final Vaccinations vaccinations;
	private final WeatherModel weatherModel;
	private final Class<? extends InfectionModel> infectionModel;
	private final Class<? extends VaccinationModel> vaccinationModel;
	private final EpisimConfigGroup.ActivityHandling activityHandling;

	private final double imprtFctMult;
	private final double importFactorBeforeJune;
	private final double importFactorAfterJune;
	private final double leisureOffset;
	private final double scale;
	private final boolean leisureNightly;
	private final double leisureNightlyScale;
	private final double householdSusc;
	private final LocationBasedRestrictions locationBasedRestrictions;

	/**
	 * Path pointing to the input folder. Can be configured at runtime with EPISIM_INPUT variable.
	 */
	public static final Path INPUT = EpisimUtils.resolveInputPath("../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input");

	/**
	 * Empty constructor is needed for running scenario from command line.
	 */
	@SuppressWarnings("unused")
	private SnzCologneProductionScenario() {
		this(new Builder());
	}

	private SnzCologneProductionScenario(Builder builder) {
		this.sample = builder.sample;
		this.diseaseImport = builder.diseaseImport;
		this.restrictions = builder.restrictions;
		this.tracing = builder.tracing;
		this.activityHandling = builder.activityHandling;
		this.infectionModel = builder.infectionModel;
		this.importOffset = builder.importOffset;
		this.vaccinationModel = builder.vaccinationModel;
		this.vaccinations = builder.vaccinations;
		this.weatherModel = builder.weatherModel;
		this.imprtFctMult = builder.imprtFctMult;
		this.leisureOffset = builder.leisureOffset;
		this.scale = builder.scale;
		this.leisureNightly = builder.leisureNightly;
		this.leisureNightlyScale = builder.leisureNightlyScale;
		this.householdSusc = builder.householdSusc;

		this.importFactorBeforeJune = builder.importFactorBeforeJune;
		this.importFactorAfterJune = builder.importFactorAfterJune;
		this.locationBasedRestrictions = builder.locationBasedRestrictions;
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
		bind(DiseaseStatusTransitionModel.class).to(AgeDependentDiseaseStatusTransitionModel.class).in(Singleton.class);
		bind(InfectionModel.class).to(infectionModel).in(Singleton.class);
		bind(VaccinationModel.class).to(vaccinationModel).in(Singleton.class);
		bind(ShutdownPolicy.class).to(FixedPolicy.class).in(Singleton.class);

		if (activityHandling == EpisimConfigGroup.ActivityHandling.startOfDay) {
			if (locationBasedRestrictions == LocationBasedRestrictions.yes) {
				bind(ActivityParticipationModel.class).to(LocationBasedParticipationModel.class);
			} else {
				bind(ActivityParticipationModel.class).to(DefaultParticipationModel.class);
			}
		}

		bind(HouseholdSusceptibility.Config.class).toInstance(
				HouseholdSusceptibility.newConfig().withSusceptibleHouseholds(householdSusc, 5.0)
		);

		Multibinder.newSetBinder(binder(), SimulationListener.class)
				.addBinding().to(HouseholdSusceptibility.class);

	}

	@Provides
	@Singleton
	public Config config() {

		double cologneFactor = 0.5; // Cologne model has about half as many agents as Berlin model, -> 2_352_480

		if (this.sample != 25 && this.sample != 100)
			throw new RuntimeException("Sample size not calibrated! Currently only 25% is calibrated. Comment this line out to continue.");

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		config.global().setRandomSeed(7564655870752979346L);

		config.vehicles().setVehiclesFile(INPUT.resolve("de_2020-vehicles.xml").toString());

		config.plans().setInputFile(inputForSample("cologne_snz_entirePopulation_emptyPlans_withDistricts_%dpt_split.xml.gz", sample));

		episimConfig.addInputEventsFile(inputForSample("cologne_snz_episim_events_wt_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		episimConfig.addInputEventsFile(inputForSample("cologne_snz_episim_events_sa_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.SATURDAY);

		episimConfig.addInputEventsFile(inputForSample("cologne_snz_episim_events_so_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.SUNDAY);

		episimConfig.setActivityHandling(activityHandling);


		episimConfig.setCalibrationParameter(1.7E-5 * 0.8);
		episimConfig.setStartDate("2020-02-25");
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);
		episimConfig.setSampleSize(this.sample / 100.);
		episimConfig.setHospitalFactor(0.5);
		episimConfig.setProgressionConfig(AbstractSnzScenario2020.baseProgressionConfig(Transition.config()).build());
		episimConfig.setThreads(8);

		//inital infections and import
		episimConfig.setInitialInfections(Integer.MAX_VALUE);
		if (this.diseaseImport != DiseaseImport.no) {

			SnzProductionScenario.configureDiseaseImport(episimConfig, diseaseImport, importOffset,
					cologneFactor * imprtFctMult, importFactorBeforeJune, importFactorAfterJune);
		}


		SnzProductionScenario.configureContactIntensities(episimConfig);

		//restrictions and masks
		CreateRestrictionsFromCSV activityParticipation = new CreateRestrictionsFromCSV(episimConfig);

		activityParticipation.setInput(INPUT.resolve("cologneSnzData_daily_until20211114.csv"));

		activityParticipation.setScale(this.scale);
		activityParticipation.setLeisureAsNightly(this.leisureNightly);
		activityParticipation.setNightlyScale(this.leisureNightlyScale);

		ConfigBuilder builder;
		try {
			builder = activityParticipation.createPolicy();
		} catch (IOException e1) {
			throw new UncheckedIOException(e1);
		}

		builder.restrict(LocalDate.parse("2020-03-16"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2020-04-27"), 0.5, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2020-06-29"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2020-08-11"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//Lueften nach den Sommerferien
		builder.restrict(LocalDate.parse("2020-08-11"), Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2020-12-31"), Restriction.ofCiCorrection(1.0), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");

		builder.restrict(LocalDate.parse("2020-10-12"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2020-10-23"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");


		builder.restrict(LocalDate.parse("2020-12-23"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-01-11"), 0.5, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-03-29"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-04-10"), 0.5, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-07-05"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-08-17"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-08-17"), Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");

		builder.restrict(LocalDate.parse("2021-10-11"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-10-18"), 1.0, "educ_higher");
		builder.restrict(LocalDate.parse("2021-10-23"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-12-24"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2022-01-08"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");


		{
			LocalDate masksCenterDate = LocalDate.of(2020, 4, 27);
			for (int ii = 0; ii <= 14; ii++) {
				LocalDate date = masksCenterDate.plusDays(-14 / 2 + ii);
				double clothFraction = 1. / 3. * 0.9;
				double ffpFraction = 1. / 3. * 0.9;
				double surgicalFraction = 1. / 3. * 0.9;

				builder.restrict(date, Restriction.ofMask(Map.of(
								FaceMask.CLOTH, clothFraction * ii / 14,
								FaceMask.N95, ffpFraction * ii / 14,
								FaceMask.SURGICAL, surgicalFraction * ii / 14)),
						"pt", "shop_daily", "shop_other", "errands");
			}
			builder.restrict(LocalDate.parse("2021-08-17"), Restriction.ofMask(FaceMask.N95, 0.9), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		}


		//tracing
		if (this.tracing == Tracing.yes) {

			SnzProductionScenario.configureTracing(config, cologneFactor);

		}


		Map<LocalDate, DayOfWeek> inputDays = new HashMap<>();
		episimConfig.setInputDays(inputDays);

		//outdoorFractions
		if (this.weatherModel != WeatherModel.no) {

			SnzProductionScenario.configureWeather(episimConfig, weatherModel,
					SnzCologneProductionScenario.INPUT.resolve("cologneWeather.csv").toFile(),
					SnzCologneProductionScenario.INPUT.resolve("weatherDataAvgCologne2000-2020.csv").toFile()
			);


		} else {
			episimConfig.setLeisureOutdoorFraction(Map.of(
					LocalDate.of(2020, 1, 1), 0.)
			);
		}

		//leisure & work factor
		if (this.restrictions != Restrictions.no) {
			builder.apply("2020-10-15", "2020-12-14", (d, e) -> e.put("fraction", 1 - 1.9 * (1 - (double) e.get("fraction"))), "leisure");
//			builder.applyToRf("2020-10-15", "2020-12-14", (d, rf) -> rf - leisureOffset, "leisure");

			BiFunction<LocalDate, Double, Double> workVacFactor = (d, rf) -> rf * 0.92;

			builder.applyToRf("2020-04-03", "2020-04-17", workVacFactor, "work", "business");
			builder.applyToRf("2020-06-26", "2020-08-07", workVacFactor, "work", "business");
			builder.applyToRf("2020-10-09", "2020-10-23", workVacFactor, "work", "business");
			builder.applyToRf("2020-12-18", "2021-01-01", workVacFactor, "work", "business");
			builder.applyToRf("2021-01-29", "2021-02-05", workVacFactor, "work", "business");
			builder.applyToRf("2021-03-26", "2021-04-09", workVacFactor, "work", "business");
			builder.applyToRf("2021-07-01", "2021-08-13", workVacFactor, "work", "business");
			builder.applyToRf("2021-10-08", "2021-10-22", workVacFactor, "work", "business");


		}

		if (this.vaccinations.equals(Vaccinations.yes)) {

			VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
			SnzProductionScenario.configureVaccines(vaccinationConfig, 2_352_480);

			if (vaccinationModel.equals(VaccinationFromData.class)) {
				// Compliance and capacity will come from data
				vaccinationConfig.setCompliancePerAge(Map.of(0, 1.0));

				vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of());

				vaccinationConfig.setFromFile(INPUT.resolve("cologneVaccinations.csv").toString());
			}
		}


		episimConfig.setPolicy(builder.build());

		config.controler().setOutputDirectory("output-snzWeekScenario-" + sample + "%");

		return config;
	}

}

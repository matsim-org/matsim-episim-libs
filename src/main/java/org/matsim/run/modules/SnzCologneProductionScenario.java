 /* project: org.matsim.*
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
import org.matsim.episim.TestingConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
 import org.matsim.episim.VirusStrainConfigGroup;
 import org.matsim.episim.model.*;
 import org.matsim.episim.model.activity.ActivityParticipationModel;
 import org.matsim.episim.model.activity.DefaultParticipationModel;
 import org.matsim.episim.model.activity.LocationBasedParticipationModel;
 import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
 import org.matsim.episim.model.listener.HouseholdSusceptibility;
 import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
 import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
import org.matsim.episim.model.testing.TestType;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
 import java.util.function.BiFunction;

 /**
  * Scenario for Cologne using Senozon events for different weekdays.
  */
 public class SnzCologneProductionScenario extends SnzProductionScenario {

	 public static class Builder extends SnzProductionScenario.Builder<SnzCologneProductionScenario> {

		 private double leisureOffset = 0.0;
		 private double scale = 1.3;
		 private boolean leisureNightly = false;
		 private boolean testing = true;

		 private double leisureCorrection = 1.9;
		 private double leisureNightlyScale = 1.0;
		 private double householdSusc = 1.0;
		 private int alphaOffsetDays = 0;

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

		 public Builder setScaleForActivityLevels(double scale) {
			 this.scale = scale;
			 return this;
		 }

		 public Builder setLeisureNightly(boolean leisureNightly) {
			 this.leisureNightly = leisureNightly;
			 return this;
		 }
		 public Builder setTesting(boolean testing) {
			 this.testing = testing;
			 return this;
		 }

		 public Builder setLeisureCorrection(double leisureCorrection) {
			 this.leisureCorrection = leisureCorrection;
			 return this;
		 }

		 public Builder setLeisureNightlyScale(double leisureNightlyScale) {
			 this.leisureNightlyScale = leisureNightlyScale;
			 return this;
		 }

		 public Builder setAlphaOffsetDays(int alphaOffsetDays) {
			 this.alphaOffsetDays = alphaOffsetDays;
			 return this;
		 }

		 public Builder setSuscHouseholds_pct(double householdSusc) {
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
	 private final boolean testing;
	 private final double leisureCorrection;
	 private final double leisureNightlyScale;
	 private final double householdSusc;
	 private final int alphaOffsetDays;
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

	 protected SnzCologneProductionScenario(Builder builder) {
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
		 this.testing = builder.testing;
		 this.leisureCorrection = builder.leisureCorrection;
		 this.leisureNightlyScale = builder.leisureNightlyScale;
		 this.householdSusc = builder.householdSusc;
		 this.alphaOffsetDays = builder.alphaOffsetDays;

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

		 bind(VaccinationFromData.Config.class).toInstance(
				 VaccinationFromData.newConfig("05315")
						 .withAgeGroup("05-11", 67158.47)
						 .withAgeGroup("12-17", 54587.2)
						 .withAgeGroup("18-59", 676995)
						 .withAgeGroup("60+", 250986)
		 );

		/* Bremen:
		bind(VaccinationFromData.Config.class).toInstance(
				VaccinationFromData.newConfig("04011")
						.withAgeGroup("05-11", 34643)
						.withAgeGroup("12-17", 29269)
						.withAgeGroup("18-59", 319916)
						.withAgeGroup("60+", 154654)
		);
		*/

		/* Dresden:
			VaccinationFromData.newConfig("14612")
					.withAgeGroup("12-17", 28255.8)
					.withAgeGroup("18-59", 319955)
					.withAgeGroup("60+", 151722)
		 */

		 Multibinder.newSetBinder(binder(), SimulationListener.class)
				 .addBinding().to(HouseholdSusceptibility.class);

	 }

	 @Provides
	 @Singleton
	 public Config config() {

		 double cologneFactor = 0.5; // Cologne model has about half as many agents as Berlin model, -> 2_352_480

//		if (this.sample != 25 && this.sample != 100)
//			throw new RuntimeException("Sample size not calibrated! Currently only 25% is calibrated. Comment this line out to continue.");

		 //general config
		 Config config = ConfigUtils.createConfig(new EpisimConfigGroup());


		 config.global().setRandomSeed(7564655870752979346L);

		 config.vehicles().setVehiclesFile(INPUT.resolve("de_2020-vehicles.xml").toString());

		 config.plans().setInputFile(inputForSample("cologne_snz_entirePopulation_emptyPlans_withDistricts_%dpt_split.xml.gz", sample));

		 //episim config
		 EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		 episimConfig.addInputEventsFile(inputForSample("cologne_snz_episim_events_wt_%dpt_split.xml.gz", sample))
				 .addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		 episimConfig.addInputEventsFile(inputForSample("cologne_snz_episim_events_sa_%dpt_split.xml.gz", sample))
				 .addDays(DayOfWeek.SATURDAY);

		 episimConfig.addInputEventsFile(inputForSample("cologne_snz_episim_events_so_%dpt_split.xml.gz", sample))
				 .addDays(DayOfWeek.SUNDAY);

		 episimConfig.setActivityHandling(activityHandling);


		 episimConfig.setCalibrationParameter(1.13e-05 * 0.92);
		 episimConfig.setStartDate("2020-02-25");
		 episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);
		 episimConfig.setSampleSize(this.sample / 100.);
		 episimConfig.setHospitalFactor(0.5);
		 episimConfig.setThreads(8);
		 episimConfig.setDaysInfectious(Integer.MAX_VALUE);


		 //progression model
		 //episimConfig.setProgressionConfig(AbstractSnzScenario2020.baseProgressionConfig(Transition.config()).build());
		 episimConfig.setProgressionConfig(SnzProductionScenario.progressionConfig(Transition.config()).build());// TODO: why does this immediately override?


		 //inital infections and import
		 episimConfig.setInitialInfections(Integer.MAX_VALUE);
		 if (this.diseaseImport != DiseaseImport.no) {

			 //			SnzProductionScenario.configureDiseaseImport(episimConfig, diseaseImport, importOffset,
			 //					cologneFactor * imprtFctMult, importFactorBeforeJune, importFactorAfterJune);
			 //disease import 2020
			 Map<LocalDate, Integer> importMap = new HashMap<>();
			 double importFactorBeforeJune = 4.0;

			 interpolateImport(importMap, cologneFactor  * importFactorBeforeJune, LocalDate.parse("2020-02-24"),
					 LocalDate.parse("2020-03-09"), 0.9, 23.1);
			 interpolateImport(importMap, cologneFactor * importFactorBeforeJune, LocalDate.parse("2020-03-09"),
					 LocalDate.parse("2020-03-23"), 23.1, 3.9);
			 interpolateImport(importMap, cologneFactor * importFactorBeforeJune, LocalDate.parse("2020-03-23"),
					 LocalDate.parse("2020-04-13"), 3.9, 0.1);

			 //summer holidays
			 LocalDate summerHolidaysEnd = LocalDate.parse("2020-08-11");

			 interpolateImport(importMap, 1.0, summerHolidaysEnd.minusDays(21), summerHolidaysEnd, 1.0, 12);
			 interpolateImport(importMap,  1.0, summerHolidaysEnd, summerHolidaysEnd.plusDays(21), 12, 1.0);

			 episimConfig.setInfections_pers_per_day(importMap);
		 }


		 //contact intensities
		 SnzProductionScenario.configureContactIntensities(episimConfig);

		 //restrictions and masks
		 CreateRestrictionsFromCSV activityParticipation = new CreateRestrictionsFromCSV(episimConfig);

		 activityParticipation.setInput(INPUT.resolve("cologneSnzData_daily_until20220311.csv"));

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
		 //Sommerferien (source: https://www.nrw-ferien.de/nrw-ferien-2022.html)
		 builder.restrict(LocalDate.parse("2020-06-29"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2020-08-11"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 //Lueften nach den Sommerferien
		 builder.restrict(LocalDate.parse("2020-08-11"), Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2020-12-31"), Restriction.ofCiCorrection(1.0), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 //Herbstferien
		 builder.restrict(LocalDate.parse("2020-10-12"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2020-10-23"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 //Weihnachtsferien TODO: check end date; shouldn't it be 2021-01-06
		 builder.restrict(LocalDate.parse("2020-12-23"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2021-01-11"), 0.5, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 //Osterferien
		 builder.restrict(LocalDate.parse("2021-03-29"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2021-04-10"), 0.5, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 //Sommerferien
		 builder.restrict(LocalDate.parse("2021-07-05"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2021-08-17"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 //		builder.restrict(LocalDate.parse("2021-08-17"), Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 //Herbstferien (different end dates for school + university)
		 builder.restrict(LocalDate.parse("2021-10-11"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2021-10-18"), 1.0, "educ_higher");
		 builder.restrict(LocalDate.parse("2021-10-23"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 //Weihnachtsferien
		 builder.restrict(LocalDate.parse("2021-12-24"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2022-01-08"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2022-04-01"), 1.0, "educ_higher");
		 //Osterferien
		 builder.restrict(LocalDate.parse("2022-04-11"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2022-04-23"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 //Sommerferien
		 builder.restrict(LocalDate.parse("2022-06-27"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2022-08-09"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 //Herbstferien
		 builder.restrict(LocalDate.parse("2022-10-04"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2022-10-15"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 //Weihnachtsferien
		 builder.restrict(LocalDate.parse("2022-12-23"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2023-01-06"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");


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
			 //			builder.restrict(LocalDate.parse("2021-08-17"), Restriction.ofMask(FaceMask.N95, 0.9), "educ_primary", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
			 //			builder.restrict(LocalDate.parse("2021-11-02"), Restriction.ofMask(FaceMask.N95, 0.0), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
			 //			builder.restrict(LocalDate.parse("2021-12-02"), Restriction.ofMask(FaceMask.N95, 0.9), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		 }

		 //curfew
		 builder.restrict("2021-04-17", Restriction.ofClosingHours(21, 5), "leisure", "visit");
		 Map<LocalDate, Double> curfewCompliance = new HashMap<LocalDate, Double>();
		 curfewCompliance.put(LocalDate.parse("2021-04-17"), 1.0);
		 curfewCompliance.put(LocalDate.parse("2021-05-31"), 0.0);
		 episimConfig.setCurfewCompliance(curfewCompliance);


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

			 if (leisureCorrection != 1)
				 builder.apply("2020-10-15", "2020-12-07", (d, e) -> e.put("fraction", 1 - leisureCorrection * (1 - (double) e.get("fraction"))), "leisure");
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
			 builder.applyToRf("2021-12-22", "2022-01-05", workVacFactor, "work", "business");


			 builder.restrict(LocalDate.parse("2022-04-11"), 0.78 * 0.92, "work", "business");
			 builder.restrict(LocalDate.parse("2022-04-23"), 0.78, "work", "business");
			 builder.restrict(LocalDate.parse("2022-06-27"), 0.78 * 0.92, "work", "business");
			 builder.restrict(LocalDate.parse("2022-08-09"), 0.78, "work", "business");
			 builder.restrict(LocalDate.parse("2022-10-04"), 0.78 * 0.92, "work", "business");
			 builder.restrict(LocalDate.parse("2022-10-15"), 0.78, "work", "business");
			 builder.restrict(LocalDate.parse("2022-12-23"), 0.78 * 0.92, "work", "business");
			 builder.restrict(LocalDate.parse("2023-01-06"), 0.78, "work", "business");


		 }

		 if (this.vaccinations.equals(Vaccinations.yes)) {

			 VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
			 SnzProductionScenario.configureVaccines(vaccinationConfig, 2_352_480);

			 if (vaccinationModel.equals(VaccinationFromData.class)) {
				 // Compliance and capacity will come from data
				 vaccinationConfig.setCompliancePerAge(Map.of(0, 1.0));

				 vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of());
				 vaccinationConfig.setReVaccinationCapacity_pers_per_day(Map.of());

				 vaccinationConfig.setFromFile(INPUT.resolve("Aktuell_Deutschland_Landkreise_COVID-19-Impfungen.csv").toString());
			 }
		 }
		 
		 if (this.testing) {
				TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);

				testingConfigGroup.setTestAllPersonsAfter(LocalDate.parse("2021-10-01"));

				TestingConfigGroup.TestingParams rapidTest = testingConfigGroup.getOrAddParams(TestType.RAPID_TEST);
				TestingConfigGroup.TestingParams pcrTest = testingConfigGroup.getOrAddParams(TestType.PCR);

				testingConfigGroup.setStrategy(TestingConfigGroup.Strategy.ACTIVITIES);

				List<String> actsList = new ArrayList<String>();
				actsList.add("leisure");
				actsList.add("work");
				actsList.add("business");
				actsList.add("educ_kiga");
				actsList.add("educ_primary");
				actsList.add("educ_secondary");
				actsList.add("educ_tertiary");
				actsList.add("educ_other");
				actsList.add("educ_higher");
				testingConfigGroup.setActivities(actsList);

				rapidTest.setFalseNegativeRate(0.3);
				rapidTest.setFalsePositiveRate(0.03);

				pcrTest.setFalseNegativeRate(0.1);
				pcrTest.setFalsePositiveRate(0.01);

				testingConfigGroup.setHouseholdCompliance(1.0);

				LocalDate testingStartDate = LocalDate.parse("2021-03-19");

				Map<LocalDate, Double> leisureTests = new HashMap<LocalDate, Double>();
				Map<LocalDate, Double> workTests = new HashMap<LocalDate, Double>();
				Map<LocalDate, Double> eduTests = new HashMap<LocalDate, Double>();
				Map<LocalDate, Double> kigaPrimaryTests = new HashMap<LocalDate, Double>();
				Map<LocalDate, Double> uniTests = new HashMap<LocalDate, Double>();
				leisureTests.put(LocalDate.parse("2020-01-01"), 0.);
				workTests.put(LocalDate.parse("2020-01-01"), 0.);
				eduTests.put(LocalDate.parse("2020-01-01"), 0.);
				kigaPrimaryTests.put(LocalDate.parse("2020-01-01"), 0.);
				uniTests.put(LocalDate.parse("2020-01-01"), 0.);

				for (int i = 1; i <= 31; i++) {
					leisureTests.put(testingStartDate.plusDays(i), 0.1 * i / 31.);
					workTests.put(testingStartDate.plusDays(i), 0.1 * i / 31.);
					eduTests.put(testingStartDate.plusDays(i), 0.4 * i / 31.);
					kigaPrimaryTests.put(testingStartDate.plusDays(i), 0.4 * i / 31.);
					uniTests.put(testingStartDate.plusDays(i), 0.8 * i / 31.);

				}

				kigaPrimaryTests.put(LocalDate.parse("2021-05-10"), 0.0);

				workTests.put(LocalDate.parse("2021-06-04"), 0.05);

				workTests.put(LocalDate.parse("2021-11-24"), 0.5);

				leisureTests.put(LocalDate.parse("2021-06-04"), 0.05);
				leisureTests.put(LocalDate.parse("2021-08-23"), 0.2);

				eduTests.put(LocalDate.parse("2021-09-20"), 0.6);

				rapidTest.setTestingRatePerActivityAndDate((Map.of(
						"leisure", leisureTests,
						"work", workTests,
						"business", workTests,
						"educ_kiga", eduTests,
						"educ_primary", eduTests,
						"educ_secondary", eduTests,
						"educ_tertiary", eduTests,
						"educ_higher", uniTests,
						"educ_other", eduTests
				)));

				Map<LocalDate, Double> leisureTestsVaccinated = new HashMap<LocalDate, Double>();
				Map<LocalDate, Double> workTestsVaccinated = new HashMap<LocalDate, Double>();
				Map<LocalDate, Double> eduTestsVaccinated = new HashMap<LocalDate, Double>();

				leisureTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
				workTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
				eduTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);

				leisureTestsVaccinated.put(LocalDate.parse("2021-08-23"), 0.2);

				rapidTest.setTestingRatePerActivityAndDateVaccinated((Map.of(
						"leisure", leisureTestsVaccinated,
						"work", workTestsVaccinated,
						"business", workTestsVaccinated,
						"educ_kiga", eduTestsVaccinated,
						"educ_primary", eduTestsVaccinated,
						"educ_secondary", eduTestsVaccinated,
						"educ_tertiary", eduTestsVaccinated,
						"educ_higher", eduTestsVaccinated,
						"educ_other", eduTestsVaccinated
				)));


				Map<LocalDate, Double> leisureTestsPCR = new HashMap<LocalDate, Double>();
				Map<LocalDate, Double> workTestsPCR = new HashMap<LocalDate, Double>();
				Map<LocalDate, Double> kigaPramaryTestsPCR = new HashMap<LocalDate, Double>();
				Map<LocalDate, Double> eduTestsPCR = new HashMap<LocalDate, Double>();

				leisureTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
				workTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
				kigaPramaryTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
				eduTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);

				kigaPramaryTestsPCR.put(LocalDate.parse("2021-05-10"), 0.4);


				pcrTest.setTestingRatePerActivityAndDate((Map.of(
						"leisure", leisureTestsPCR,
						"work", workTestsPCR,
						"business", workTestsPCR,
						"educ_kiga", kigaPramaryTestsPCR,
						"educ_primary", kigaPramaryTestsPCR,
						"educ_secondary", eduTestsPCR,
						"educ_tertiary", eduTestsPCR,
						"educ_higher", eduTestsPCR,
						"educ_other", eduTestsPCR
				)));

				Map<LocalDate, Double> leisureTestsPCRVaccinated = new HashMap<LocalDate, Double>();
				Map<LocalDate, Double> workTestsPCRVaccinated = new HashMap<LocalDate, Double>();
				Map<LocalDate, Double> eduTestsPCRVaccinated = new HashMap<LocalDate, Double>();
				leisureTestsPCRVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
				workTestsPCRVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
				eduTestsPCRVaccinated.put(LocalDate.parse("2020-01-01"), 0.);

				pcrTest.setTestingRatePerActivityAndDateVaccinated((Map.of(
						"leisure", leisureTestsPCRVaccinated,
						"work", workTestsPCRVaccinated,
						"business", workTestsPCRVaccinated,
						"educ_kiga", eduTestsPCRVaccinated,
						"educ_primary", eduTestsPCRVaccinated,
						"educ_secondary", eduTestsPCRVaccinated,
						"educ_tertiary", eduTestsPCRVaccinated,
						"educ_higher", eduTestsPCRVaccinated,
						"educ_other", eduTestsPCRVaccinated
				)));

				rapidTest.setTestingCapacity_pers_per_day(Map.of(
						LocalDate.of(1970, 1, 1), 0,
						testingStartDate, Integer.MAX_VALUE));

				pcrTest.setTestingCapacity_pers_per_day(Map.of(
						LocalDate.of(1970, 1, 1), 0,
						testingStartDate, Integer.MAX_VALUE));
			 
		 }

		 SnzProductionScenario.configureStrains(episimConfig, ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class), alphaOffsetDays);

		 builder.setHospitalScale(this.scale);

		 episimConfig.setPolicy(builder.build());

		 config.controler().setOutputDirectory("output-snzWeekScenario-" + sample + "%");

		 return config;
	 }

 }

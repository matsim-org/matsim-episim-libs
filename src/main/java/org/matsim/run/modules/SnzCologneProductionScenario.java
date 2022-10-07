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
 import org.matsim.episim.*;
 import org.matsim.episim.model.*;
 import org.matsim.episim.model.activity.ActivityParticipationModel;
 import org.matsim.episim.model.activity.DefaultParticipationModel;
 import org.matsim.episim.model.activity.LocationBasedParticipationModel;
 import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
 import org.matsim.episim.model.listener.HouseholdSusceptibility;
 import org.matsim.episim.model.listener.WriteAntibodies;
 import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
 import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
 import org.matsim.episim.model.testing.TestType;
 import org.matsim.episim.model.testing.TestingModel;
 import org.matsim.episim.model.vaccination.VaccinationFromData;
 import org.matsim.episim.model.vaccination.VaccinationModel;
 import org.matsim.episim.policy.FixedPolicy;
 import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
 import org.matsim.episim.policy.Restriction;
 import org.matsim.episim.policy.ShutdownPolicy;

 import javax.inject.Singleton;
 import java.io.*;
 import java.nio.file.Path;
 import java.time.DayOfWeek;
 import java.time.LocalDate;
 import java.util.*;
 import java.util.function.BiFunction;
 import java.util.stream.Collectors;

 /**
  * Scenario for Cologne using Senozon events for different weekdays.
  */
 public class SnzCologneProductionScenario extends SnzProductionScenario {

	 public static class Builder extends SnzProductionScenario.Builder<SnzCologneProductionScenario> {

		 private double leisureOffset = 0.0;
		 private double scale = 1.3;
		 private boolean leisureNightly = false;

		 private double leisureCorrection = 1.9;
		 private double leisureNightlyScale = 1.0;
		 private double householdSusc = 1.0;
		 public CarnivalModel carnivalModel = CarnivalModel.no;

		 private boolean sebastianUpdate = true;


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

		 public Builder setLeisureCorrection(double leisureCorrection) {
			 this.leisureCorrection = leisureCorrection;
			 return this;
		 }

		 public Builder setLeisureNightlyScale(double leisureNightlyScale) {
			 this.leisureNightlyScale = leisureNightlyScale;
			 return this;
		 }

		 public Builder setSuscHouseholds_pct(double householdSusc) {
			 this.householdSusc = householdSusc;
			 return this;
		 }

		 public Builder setCarnivalModel(CarnivalModel carnivalModel) {
			 this.carnivalModel = carnivalModel;
			 return this;
		 }

		 public Builder setSebastianUpdate(boolean sebastianUpdate) {
			 this.sebastianUpdate = sebastianUpdate;
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

	 private final Class<? extends TestingModel> testingModel;
	 private final EpisimConfigGroup.ActivityHandling activityHandling;

	 private final double imprtFctMult;
	 private final double importFactorBeforeJune;
	 private final double importFactorAfterJune;
	 private final double leisureOffset;
	 private final double scale;
	 private final boolean leisureNightly;
	 private final double leisureCorrection;
	 private final double leisureNightlyScale;
	 private final double householdSusc;
	 private final LocationBasedRestrictions locationBasedRestrictions;
	 private final CarnivalModel carnivalModel;


	 public enum CarnivalModel {yes, no}

	 public final boolean sebastianUpdate;

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
		 this.testingModel = builder.testingModel;
		 this.vaccinations = builder.vaccinations;
		 this.weatherModel = builder.weatherModel;
		 this.imprtFctMult = builder.imprtFctMult;
		 this.leisureOffset = builder.leisureOffset;
		 this.scale = builder.scale;
		 this.leisureNightly = builder.leisureNightly;
		 this.leisureCorrection = builder.leisureCorrection;
		 this.leisureNightlyScale = builder.leisureNightlyScale;
		 this.householdSusc = builder.householdSusc;

		 this.importFactorBeforeJune = builder.importFactorBeforeJune;
		 this.importFactorAfterJune = builder.importFactorAfterJune;
		 this.locationBasedRestrictions = builder.locationBasedRestrictions;
		 this.carnivalModel = builder.carnivalModel;

		 this.sebastianUpdate = builder.sebastianUpdate;
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
		 bind(TestingModel.class).to(testingModel).in(Singleton.class);
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


		 // antibody model
		 AntibodyModel.Config antibodyConfig = new AntibodyModel.Config();
		 antibodyConfig.setImmuneReponseSigma(3.0);
		 bind(AntibodyModel.Config.class).toInstance(antibodyConfig);

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

		 Multibinder<SimulationListener> listener = Multibinder.newSetBinder(binder(), SimulationListener.class);

		 listener.addBinding().to(HouseholdSusceptibility.class);

		 // Write antibodies, iteration is hard-coded

		 // listener.addBinding().to(WriteAntibodies.class);

	 }

	 @Provides
	 @Singleton
	 public Config config() {

		 LocalDate restrictionDate = LocalDate.parse("2022-03-01");
		 double cologneFactor = 0.5; // Cologne model has about half as many agents as Berlin model, -> 2_352_480

		 if (this.sample != 25 && this.sample != 100)
			 throw new RuntimeException("Sample size not calibrated! Currently only 25% is calibrated. Comment this line out to continue.");

		 //general config
		 Config config = ConfigUtils.createConfig(new EpisimConfigGroup());


		 config.global().setRandomSeed(7564655870752979346L);

		 config.vehicles().setVehiclesFile(INPUT.resolve("de_2020-vehicles.xml").toString());

		 config.plans().setInputFile(inputForSample("cologne_snz_entirePopulation_emptyPlans_withDistricts_%dpt_split.xml.gz", sample));

		 //episim config
		 EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		 episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 0.96 * 1.06);

		 episimConfig.addInputEventsFile(inputForSample("cologne_snz_episim_events_wt_%dpt_split.xml.gz", sample))
				 .addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		 episimConfig.addInputEventsFile(inputForSample("cologne_snz_episim_events_sa_%dpt_split.xml.gz", sample))
				 .addDays(DayOfWeek.SATURDAY);

		 episimConfig.addInputEventsFile(inputForSample("cologne_snz_episim_events_so_%dpt_split.xml.gz", sample))
				 .addDays(DayOfWeek.SUNDAY);

		 episimConfig.setActivityHandling(activityHandling);


		 episimConfig.setCalibrationParameter(1.0e-05 * 0.83 * 1.4);
		 episimConfig.setStartDate("2020-02-25");
		 episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);
		 episimConfig.setSampleSize(this.sample / 100.);
		 episimConfig.setHospitalFactor(0.5);
		 episimConfig.setThreads(8);
		 episimConfig.setDaysInfectious(Integer.MAX_VALUE);

		 //progression model
		 //episimConfig.setProgressionConfig(AbstractSnzScenario2020.baseProgressionConfig(Transition.config()).build());
		 episimConfig.setProgressionConfig(SnzProductionScenario.progressionConfig(Transition.config()).build());// TODO: why does this immediately override?


		 //DISEASE IMPORT
		 episimConfig.setInitialInfections(Integer.MAX_VALUE);
		 if (this.diseaseImport != DiseaseImport.no) {

			 configureDiseaseImport(cologneFactor, episimConfig);

		 }


		 //CONTACT INTENSITY
		 SnzProductionScenario.configureContactIntensities(episimConfig);
		 //work
		 double workCi = 0.75;
		 episimConfig.getOrAddContainerParams("work").setContactIntensity(episimConfig.getOrAddContainerParams("work").getContactIntensity() * workCi);
		 episimConfig.getOrAddContainerParams("business").setContactIntensity(episimConfig.getOrAddContainerParams("business").getContactIntensity() * workCi);

		 //leisure & visit
		 double leisureCi = 0.4;
		 episimConfig.getOrAddContainerParams("leisure").setContactIntensity(episimConfig.getOrAddContainerParams("leisure").getContactIntensity() * 0.6 * leisureCi);
		 episimConfig.getOrAddContainerParams("visit").setContactIntensity(episimConfig.getOrAddContainerParams("visit").getContactIntensity() * leisureCi);


		 //school
		 double schoolCi = 0.75;
		 episimConfig.getOrAddContainerParams("educ_kiga").setContactIntensity(episimConfig.getOrAddContainerParams("educ_kiga").getContactIntensity() * schoolCi);
		 episimConfig.getOrAddContainerParams("educ_primary").setContactIntensity(episimConfig.getOrAddContainerParams("educ_primary").getContactIntensity() * schoolCi);
		 episimConfig.getOrAddContainerParams("educ_secondary").setContactIntensity(episimConfig.getOrAddContainerParams("educ_secondary").getContactIntensity() * schoolCi);
		 episimConfig.getOrAddContainerParams("educ_tertiary").setContactIntensity(episimConfig.getOrAddContainerParams("educ_tertiary").getContactIntensity() * schoolCi);
		 episimConfig.getOrAddContainerParams("educ_higher").setContactIntensity(episimConfig.getOrAddContainerParams("educ_higher").getContactIntensity() * schoolCi);
		 episimConfig.getOrAddContainerParams("educ_other").setContactIntensity(episimConfig.getOrAddContainerParams("educ_other").getContactIntensity() * schoolCi);


		 //SEASONALITY
		 episimConfig.getOrAddContainerParams("work").setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("educ_kiga").setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("educ_primary").setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("educ_secondary").setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("educ_tertiary").setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("educ_higher").setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("educ_other").setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("errands").setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("business").setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("visit").setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("home").setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("quarantine_home").setSeasonality(0.5);

		 //restrictions and masks
		 CreateRestrictionsFromCSV activityParticipation = new CreateRestrictionsFromCSV(episimConfig);

		 activityParticipation.setInput(INPUT.resolve("CologneSnzData_daily_until20220723.csv"));

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

		 builder.restrict(LocalDate.parse("2022-07-23"), 0.2, "educ_higher");
		 builder.restrict(LocalDate.parse("2022-10-17"), 1.0, "educ_higher");

		 //Herbstferien
		 builder.restrict(LocalDate.parse("2022-10-04"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2022-10-15"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 //Weihnachtsferien
		 builder.restrict(LocalDate.parse("2022-12-23"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2023-01-06"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");

		 builder.restrict(LocalDate.parse("2022-12-19"), 0.2, "educ_higher");
		 builder.restrict(LocalDate.parse("2022-12-31"), 1.0, "educ_higher");


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

		 // vaccinated individuals (even tho they are allowed to complete activities, they will naturally reduce their activities)
		 LocalDate transition2gTo3g = LocalDate.of(2022, 3, 4);
		 double leis = 1.0;
		 builder.restrict(LocalDate.parse("2021-12-01"), Restriction.ofVaccinatedRf(0.75), "leisure");
		 builder.restrict(transition2gTo3g, Restriction.ofVaccinatedRf(leis), "leisure");

		 //2G for unvaccinated susceptible agents  (will move more activities in private homes, thats why we assume 0.75 for both vaccinated and unvaccinated)
		 builder.restrict(LocalDate.parse("2021-11-22"), Restriction.ofSusceptibleRf(0.75), "leisure");
		 builder.restrict(transition2gTo3g, Restriction.ofSusceptibleRf(leis), "leisure");

		 double schoolFac = 0.5;
		 builder.restrict(LocalDate.parse("2021-08-17"), Restriction.ofCiCorrection(1 - (0.5 * schoolFac)), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");

		 builder.restrict(LocalDate.parse("2021-08-17"), Restriction.ofMask(FaceMask.N95, 0.9 * schoolFac), "educ_primary", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2021-11-02"), Restriction.ofMask(FaceMask.N95, 0.0), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2021-12-02"), Restriction.ofMask(FaceMask.N95, 0.9 * schoolFac), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");

		 // mask mandate removed
		 builder.restrict(LocalDate.of(2022, 4, 4), Restriction.ofMask(FaceMask.N95, 0.), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other", "leisure", "work", "business"); //todo: check

		 builder.restrict(LocalDate.of(2022, 4, 4), Restriction.ofMask(Map.of(
						 FaceMask.CLOTH, 0.0,
						 FaceMask.N95, 0.0,
						 FaceMask.SURGICAL, 0.0)),
				 "shop_daily", "shop_other", "errands");

		 builder.restrict(LocalDate.of(2022, 4, 4), Restriction.ofMask(Map.of(
						 FaceMask.CLOTH, 0.0,
						 FaceMask.N95, 0.25,
						 FaceMask.SURGICAL, 0.25)),
				 "pt");


		 //tracing
		 if (this.tracing == Tracing.yes) {

			 SnzProductionScenario.configureTracing(config, cologneFactor);

		 }


		 Map<LocalDate, DayOfWeek> inputDays = new HashMap<>();
		 inputDays.put(LocalDate.parse("2021-11-01"), DayOfWeek.SUNDAY);
		 episimConfig.setInputDays(inputDays);

		 //outdoorFractions
		 if (this.weatherModel != WeatherModel.no) {

			 double outdoorAlpha = 0.8;
			 SnzProductionScenario.configureWeather(episimConfig, weatherModel,
					 SnzCologneProductionScenario.INPUT.resolve("cologneWeather.csv").toFile(),
					 SnzCologneProductionScenario.INPUT.resolve("weatherDataAvgCologne2000-2020.csv").toFile(), outdoorAlpha
			 );


		 } else {
			 episimConfig.setLeisureOutdoorFraction(Map.of(
					 LocalDate.of(2020, 1, 1), 0.)
			 );
		 }

		 //leisure & work factor
		 if (this.restrictions != Restrictions.no) {

			 if (leisureCorrection == 0.) {  // assume old factor of 1.9, only applied to leisure TODO: get rid of this artifact
				 builder.apply("2020-10-15", "2020-12-14", (d, e) -> e.put("fraction", 1 - 1.9 * (1 - (double) e.get("fraction"))), "leisure");
			 } else if (leisureCorrection != 1) {
				 builder.apply("2020-10-15", "2020-12-14", (d, e) -> e.put("fraction", 1 - leisureCorrection * (1 - (double) e.get("fraction"))), "business", "errands", "leisure", "shop_daily", "shop_other", "visit", "work");
			 }

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

			 vaccinationConfig.setUseIgA(true);
			 vaccinationConfig.setTimePeriodIgA(730);

			 Map<Integer, Double> vaccinationCompliance = new HashMap<>();
			 for (int i = 0; i < 5; i++) vaccinationCompliance.put(i, 0.0);
			 for (int i = 5; i <= 11; i++) vaccinationCompliance.put(i, 0.4);
			 for (int i = 12; i <= 120; i++) vaccinationCompliance.put(i, 1.0);
			 vaccinationConfig.setCompliancePerAge(vaccinationCompliance);

			 Map<LocalDate, Integer> vaccinations = new HashMap<>();
			 double population = 2_352_480;
			 vaccinations.put(LocalDate.parse("2022-04-12"), (int) (0.0002 * population / 7));
			 vaccinations.put(LocalDate.parse("2022-06-30"), 0);

			 vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);
			 vaccinationConfig.setDaysValid(270);
			 vaccinationConfig.setValidDeadline(LocalDate.parse("2022-01-01"));

			 vaccinationConfig.setBeta(1.2);

			 configureBooster(vaccinationConfig, 1.0, 3);

		 }

		 if (carnivalModel.equals(CarnivalModel.yes)) {
			 // Friday 25.2 to Monday 28.2 (Rosenmontag)
			 builder.restrict(LocalDate.parse("2022-02-25"), 1., "work", "leisure", "shop_daily", "shop_other", "visit", "errands", "business");
			 builder.restrict(LocalDate.parse("2022-02-27"), 1., "work", "leisure", "shop_daily", "shop_other", "visit", "errands", "business"); // sunday, to overwrite the setting on sundays
			 builder.restrict(LocalDate.parse("2022-03-01"), 0.7, "work", "leisure", "shop_daily", "shop_other", "visit", "errands", "business"); // tuesday, back to normal after carnival

			 builder.restrict(LocalDate.parse("2022-02-25"), Restriction.ofCiCorrection(2.0), "leisure");
			 builder.restrict(LocalDate.parse("2022-03-01"), Restriction.ofCiCorrection(1.0), "leisure");

			 inputDays.put(LocalDate.parse("2022-02-28"), DayOfWeek.SUNDAY); // set monday to be a sunday
		 }

		 builder.setHospitalScale(this.scale);

		 episimConfig.setPolicy(builder.build());


		 //configure strains
		 //alpha
		 double aInf = 1.9 * 1.4;
		 VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(aInf);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySick(0.5);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySickVaccinated(0.5);


		 //delta
		 double deltaInf = 3.1 * 0.9;
		 double deltaHos = 1.0;
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setInfectiousness(deltaInf);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySick(deltaHos);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySickVaccinated(deltaHos);


		 //BA.1
		 double oHos = 0.2;
		 double ba1Inf = 1.9 * deltaInf;
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setInfectiousness(ba1Inf);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySick(oHos);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySickVaccinated(oHos);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorCritical(oHos);


		 //BA.2
		 double ba2Inf = 1.7 * ba1Inf;
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setInfectiousness(ba2Inf);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorSeriouslySick(oHos);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorSeriouslySickVaccinated(oHos);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorCritical(oHos);

		 //BA.5
		 double ba5Inf = ba2Inf;
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setInfectiousness(ba5Inf);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySick(oHos);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySickVaccinated(oHos);
		 virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorCritical(oHos);


		 //---------------------------------------
		 //		T E S T I N G
		 //---------------------------------------

		 // this section is split into four nested sections:
		 // A) RAPID test
		 // 	i) unvaccinated
		 //		ii) vaccinated
		 // B) PCR Test
		 //		i) unvaccinated
		 //		ii) vaccinated

		 // load testing config group and set general parameters
		 TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);

		 testingConfigGroup.setTestAllPersonsAfter(LocalDate.parse("2021-10-01"));



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


		 // 1) Rapid test
		 TestingConfigGroup.TestingParams rapidTest = testingConfigGroup.getOrAddParams(TestType.RAPID_TEST);
		 rapidTest.setFalseNegativeRate(0.3);
		 rapidTest.setFalsePositiveRate(0.03);

		//		1i) unvaccianted

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

//		 String testing = "current";


		 // 3g removed for work
		 workTests.put(LocalDate.of(2022, 3, 20), 0.);

//		 if (testing.equals("no")) {
		 // turn of school tests after easter break
		 kigaPrimaryTests.put(LocalDate.of(2022, 4, 25), 0.0);
		 eduTests.put(LocalDate.of(2022, 4, 25), 0.0);
		 uniTests.put(LocalDate.of(2022, 4, 25), 0.0);
//		 }

		 // no more regulation regarding test, we assume once every 2 weeks
		 leisureTests.put(LocalDate.of(2022, 4, 25), 0.1);


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

		 //		1ii) vaccinated
		 Map<LocalDate, Double> leisureTestsVaccinated = new HashMap<>();
		 Map<LocalDate, Double> workTestsVaccinated = new HashMap<>();
		 Map<LocalDate, Double> eduTestsVaccinated = new HashMap<>();

		 leisureTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
		 workTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
		 eduTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);

		 leisureTestsVaccinated.put(LocalDate.parse("2021-08-23"), 0.2);

//		 if (testing.equals("no")) {
		 leisureTestsVaccinated.put(LocalDate.of(2022, 4, 25), 0.0);
		 workTestsVaccinated.put(LocalDate.of(2022, 4, 25), 0.0);
		 eduTestsVaccinated.put(LocalDate.of(2022, 4, 25), 0.0);
//		 }


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


		 // 2) PCR Test
		 TestingConfigGroup.TestingParams pcrTest = testingConfigGroup.getOrAddParams(TestType.PCR);
		 pcrTest.setFalseNegativeRate(0.1);
		 pcrTest.setFalsePositiveRate(0.01);

		 // 	2i) unvaccinated

		 Map<LocalDate, Double> leisureTestsPCR = new HashMap<LocalDate, Double>();
		 Map<LocalDate, Double> workTestsPCR = new HashMap<LocalDate, Double>();
		 Map<LocalDate, Double> kigaPramaryTestsPCR = new HashMap<LocalDate, Double>();
		 Map<LocalDate, Double> eduTestsPCR = new HashMap<LocalDate, Double>();

		 leisureTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		 workTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		 kigaPramaryTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		 eduTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);

		 kigaPramaryTestsPCR.put(LocalDate.parse("2021-05-10"), 0.4);

//		 if (testing.equals("no")) {
		 leisureTestsPCR.put(LocalDate.of(2022, 4, 25), 0.0);
		 workTestsPCR.put(LocalDate.of(2022, 4, 25), 0.0);
		 kigaPramaryTestsPCR.put(LocalDate.of(2022, 4, 25), 0.0);
		 eduTestsPCR.put(LocalDate.of(2022, 4, 25), 0.0);
//		 }


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

		 // 	2ii) vaccinated

		 Map<LocalDate, Double> leisureTestsPCRVaccinated = new HashMap<>();
		 Map<LocalDate, Double> workTestsPCRVaccinated = new HashMap<>();
		 Map<LocalDate, Double> eduTestsPCRVaccinated = new HashMap<>();
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

		 //tracing
		 TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		 boolean qv = false;
		 //		if (params.qV.equals("yes")) {
		 //			qv = true;
		 //		}
		 tracingConfig.setQuarantineVaccinated((Map.of(
				 episimConfig.getStartDate(), false
				 //				restrictionDate, qv
		 )));

		 tracingConfig.setQuarantineDuration(Map.of(
				 episimConfig.getStartDate(), 14,
				 LocalDate.parse("2022-01-01"), 10
		 ));

		 int greenPassValid = 90;
		 int greenPassValidBoostered = Integer.MAX_VALUE;

		 tracingConfig.setGreenPassValidDays(greenPassValid);
		 tracingConfig.setGreenPassBoosterValidDays(greenPassValidBoostered);

		 EpisimPerson.QuarantineStatus qs = EpisimPerson.QuarantineStatus.atHome;

		 tracingConfig.setQuarantineStatus(Map.of(
				 episimConfig.getStartDate(), EpisimPerson.QuarantineStatus.atHome
				 //					restrictionDate, qs
		 ));

		 config.controler().setOutputDirectory("output-snzWeekScenario-" + sample + "%");

		 return config;
	 }

	 private void configureDiseaseImport(double cologneFactor, EpisimConfigGroup episimConfig) {

		 // initialize map to store initial infections per strain
		 Map<LocalDate, Integer> infPerDayWild = new HashMap<>();
		 Map<LocalDate, Integer> infPerDayAlpha = new HashMap<>();
		 Map<LocalDate, Integer> infPerDayDelta = new HashMap<>();
		 Map<LocalDate, Integer> infPerDayBa1 = new HashMap<>();
		 Map<LocalDate, Integer> infPerDayBa2 = new HashMap<>();
		 Map<LocalDate, Integer> infPerDayBa5 = new HashMap<>();


		 // Wild Type Disease Import (before we have import data)
		 double importFactorBeforeJune = 4.0;
		 double imprtFctMult = 1.0;
		 long importOffset = 0;

		 interpolateImport(infPerDayWild, cologneFactor * imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-02-24").plusDays(importOffset),
				 LocalDate.parse("2020-03-09").plusDays(importOffset), 0.9, 23.1);
		 interpolateImport(infPerDayWild, cologneFactor * imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-03-09").plusDays(importOffset),
				 LocalDate.parse("2020-03-23").plusDays(importOffset), 23.1, 3.9);
		 interpolateImport(infPerDayWild, cologneFactor * imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-03-23").plusDays(importOffset),
				 LocalDate.parse("2020-04-13").plusDays(importOffset), 3.9, 0.1);

		 infPerDayWild.put(LocalDate.parse("2020-07-19"), (int) (0.5 * 32));
		 infPerDayWild.put(LocalDate.parse("2020-08-09"), 1);

		 for (Map.Entry<LocalDate, Integer> entry : infPerDayWild.entrySet()) {
			 if (entry.getKey().isBefore(LocalDate.parse("2020-08-12"))) {
				 int value = entry.getValue();
				 value = Math.max(1, value);
				 infPerDayWild.put(entry.getKey(), value);
			 }
		 }


		 double facWild = 4.0;
		 double facAlpha = 4.0;
		 double facDelta = 4.0;
		 double facBa1 = 4.0;
		 double facBa2 = 4.0;


		 infPerDayAlpha.put(LocalDate.parse("2020-01-01"), 0);
		 infPerDayDelta.put(LocalDate.parse("2020-01-01"), 0);
		 infPerDayBa1.put(LocalDate.parse("2020-01-01"), 0);
		 infPerDayBa2.put(LocalDate.parse("2020-01-01"), 0);
		 infPerDayBa5.put(LocalDate.parse("2020-01-01"), 0);

		 NavigableMap<LocalDate, Double> data = DataUtils.readDiseaseImport(SnzCologneProductionScenario.INPUT.resolve("cologneDiseaseImport.csv"));

		 NavigableMap<LocalDate, Map<VirusStrain, Double>> shares = DataUtils.readVOC(SnzCologneProductionScenario.INPUT.resolve("VOC_Cologne_RKI.csv"));

		 // Get import share
		 BiFunction<LocalDate, VirusStrain, Double> lookup = (date, strain) -> shares.floorEntry(date).getValue().getOrDefault(strain, 0d);
		 LocalDate date = null;

		 for (Map.Entry<LocalDate, Double> e : data.entrySet()) {

			 double factor = 0.25 * 2352476. / 919936.; //25% sample, data is given for Cologne City, so we have to scale it to the whole model

			 double cases = factor * e.getValue();
			 date = e.getKey();

//				 infPerDayBa5.put(date, (int) (lookup.apply(date, VirusStrain.OMICRON_BA5) * cases * facBa5));
			 infPerDayBa2.put(date, (int) (lookup.apply(date, VirusStrain.OMICRON_BA2) * cases * facBa2));
			 infPerDayBa1.put(date, (int) (lookup.apply(date, VirusStrain.OMICRON_BA1) * cases * facBa1));
			 infPerDayDelta.put(date, (int) (lookup.apply(date, VirusStrain.DELTA) * cases * facDelta));
			 infPerDayAlpha.put(date, (int) (lookup.apply(date, VirusStrain.ALPHA) * cases * facAlpha));
			 infPerDayWild.put(date, (int) (lookup.apply(date, VirusStrain.SARS_CoV_2) * cases * facWild));

		 }

		 LocalDate wildImportEnds = LocalDate.of(2021, 11, 21);
		 infPerDayWild = infPerDayWild.entrySet().stream().filter(entry -> entry.getKey().isBefore(wildImportEnds)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		 infPerDayWild.put(wildImportEnds, 1);

		 LocalDate alphaImportEnds = LocalDate.of(2021, 7, 17);
		 infPerDayAlpha = infPerDayAlpha.entrySet().stream().filter(entry -> entry.getKey().isBefore(alphaImportEnds)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		 infPerDayAlpha.put(alphaImportEnds, 1);

		 LocalDate deltaImportEnds = LocalDate.of(2022, 1, 16);
		 infPerDayDelta = infPerDayDelta.entrySet().stream().filter(entry -> entry.getKey().isBefore(deltaImportEnds)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		 infPerDayDelta.put(deltaImportEnds, 1);


		 LocalDate dateAfterCsvEnds = date.plusDays(1);
		 infPerDayBa2.put(dateAfterCsvEnds, 1);

		 // initial import alpha
		 LocalDate startDateAlpha = LocalDate.parse("2021-01-15");
		 for (int i = 0; i < 7; i++) {
			 infPerDayAlpha.put(startDateAlpha.plusDays(i), 4);
		 }
		 infPerDayAlpha.put(startDateAlpha.plusDays(7), 1);

		 // initial import BA.1 // TODO: Why did we comment this out
//		LocalDate ba1Date = LocalDate.parse(params.ba1Date);
//		for (int i = 0; i < 7; i++) {
//			infPerDayBa1.put(ba1Date.plusDays(i), 4);
//		}
//		infPerDayBa1.put(ba1Date.plusDays(7), 1);


		 // initial import BA.2
		 LocalDate ba2Date = LocalDate.parse("2021-12-18");
		 for (int i = 0; i < 7; i++) {
			 infPerDayBa2.put(ba2Date.plusDays(i), 4);
		 }
		 infPerDayBa2.put(ba2Date.plusDays(7), 1);

		 // initial import BA.5
		 LocalDate ba5Date = LocalDate.parse("2022-04-10");
		 for (int i = 0; i < 7; i++) {
			 infPerDayBa5.put(ba5Date.plusDays(i), 4);
		 }
		 infPerDayBa5.put(ba5Date.plusDays(7), 1);

		 // set all initial infections
		 episimConfig.setInfections_pers_per_day(VirusStrain.SARS_CoV_2, infPerDayWild);
		 episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayAlpha);
		 episimConfig.setInfections_pers_per_day(VirusStrain.DELTA, infPerDayDelta);
		 episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA1, infPerDayBa1);
		 episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA2, infPerDayBa2);
		 episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA5, infPerDayBa5);
	 }

	 private void configureBooster(VaccinationConfigGroup vaccinationConfig, double boosterSpeed, int boostAfter) {

		 Map<LocalDate, Integer> boosterVaccinations = new HashMap<>();

		 boosterVaccinations.put(LocalDate.parse("2020-01-01"), 0);

		 boosterVaccinations.put(LocalDate.parse("2022-04-12"), (int) (2_352_480 * 0.002 * boosterSpeed / 7));
		 boosterVaccinations.put(LocalDate.parse("2022-06-30"), 0);

		 vaccinationConfig.setReVaccinationCapacity_pers_per_day(boosterVaccinations);


		 vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
				 .setBoostWaitPeriod(boostAfter * 30 + 6 * 7);
		 ;

		 vaccinationConfig.getOrAddParams(VaccinationType.ba1Update)
				 .setBoostWaitPeriod(boostAfter * 30 + 6 * 7);
		 ;

		 vaccinationConfig.getOrAddParams(VaccinationType.vector)
				 .setBoostWaitPeriod(boostAfter * 30 + 9 * 7);
		 ;
	 }


 }

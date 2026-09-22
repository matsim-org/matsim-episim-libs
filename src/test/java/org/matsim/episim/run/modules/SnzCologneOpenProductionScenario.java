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

 package org.matsim.episim.run.modules;

 import com.google.inject.AbstractModule;
 import com.google.inject.Provides;
 import com.google.inject.multibindings.Multibinder;
 import jakarta.inject.Singleton;
 import org.matsim.api.core.v01.Scenario;
 import org.matsim.core.config.Config;
 import org.matsim.core.config.ConfigUtils;
 import org.matsim.core.config.groups.VspExperimentalConfigGroup;
 import org.matsim.core.controler.ControllerUtils;
 import org.matsim.core.scenario.ScenarioUtils;
 import org.matsim.episim.*;
 import org.matsim.episim.model.*;
 import org.matsim.episim.model.activity.ActivityParticipationModel;
 import org.matsim.episim.model.activity.DefaultParticipationModel;
 import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
 import org.matsim.episim.model.listener.HouseholdSusceptibility;
 import org.matsim.episim.model.ImmunityModel;
 import org.matsim.episim.model.LegacySplitImmunityModel;
 import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
 import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
 import org.matsim.episim.model.testing.DefaultTestingModel;
 import org.matsim.episim.model.testing.TestingModel;
 import org.matsim.episim.model.vaccination.NoVaccination;
 import org.matsim.episim.model.vaccination.VaccinationModel;
 import org.matsim.episim.policy.FixedPolicy;
 import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
 import org.matsim.episim.policy.Restriction;
 import org.matsim.episim.policy.ShutdownPolicy;
 import org.matsim.vehicles.VehicleType;


 import java.io.IOException;
 import java.io.UncheckedIOException;
 import java.time.DayOfWeek;
 import java.time.LocalDate;
 import java.util.*;
 import java.util.function.BiFunction;

 import static org.matsim.episim.model.Transition.to;

 /**
  * Open Scenario for Cologne using Senozon events for different weekdays.
  */
 public final class SnzCologneOpenProductionScenario extends AbstractModule {

	 public enum DiseaseImport {yes, no}

	 public enum Restrictions {yes, no,}

	 public enum Masks {yes, no}

	 public enum Tracing {yes, no}

	 private final DiseaseImport diseaseImport;
	 private final Restrictions restrictions;
	 private final Masks masks;
	 private final Tracing tracing;

	 private final Class<? extends InfectionModel> infectionModel;
	 private final Class<? extends VaccinationModel> vaccinationModel;

	 private final Class<? extends TestingModel> testingModel;

	 private final double scale;
	 private final boolean leisureNightly;
	 private final double leisureNightlyScale;
	 private final double householdSusc;


	 /**
	  * Empty constructor is needed for running scenario from command line.
	  */
	 @SuppressWarnings("unused")
	 private SnzCologneOpenProductionScenario() {
		 this(new Builder());
	 }

	 private SnzCologneOpenProductionScenario(Builder builder) {
		 this.diseaseImport = builder.diseaseImport;
		 this.restrictions = builder.restrictions;
		 this.masks = builder.masks;
		 this.tracing = builder.tracing;
		 this.infectionModel = builder.infectionModel;
		 this.vaccinationModel = builder.vaccinationModel;
		 this.testingModel = builder.testingModel;
		 this.scale = builder.scale;
		 this.leisureNightly = builder.leisureNightly;
		 this.leisureNightlyScale = builder.leisureNightlyScale;
		 this.householdSusc = builder.householdSusc;

	 }


	 @Override
	 protected void configure() {

		 bind(ContactModel.class).to(SymmetricContactModel.class).in(Singleton.class);
		 bind(DiseaseStatusTransitionModel.class).to(AgeDependentDiseaseStatusTransitionModel.class).in(Singleton.class);
		 bind(ImmunityModel.class).annotatedWith(Legacy.class).to(LegacySplitImmunityModel.class).in(Singleton.class);
		 bind(InfectionModel.class).to(infectionModel).in(Singleton.class);
		 bind(VaccinationModel.class).to(vaccinationModel).in(Singleton.class);
		 bind(TestingModel.class).to(testingModel).in(Singleton.class);
		 bind(ShutdownPolicy.class).to(FixedPolicy.class).in(Singleton.class);
		 bind(ActivityParticipationModel.class).to(DefaultParticipationModel.class);

		 // antibody model
		 AntibodyModel.Config antibodyConfig = new AntibodyModel.Config();
		 antibodyConfig.setImmuneResponseSigma(3.0);
		 bind(AntibodyModel.Config.class).toInstance(antibodyConfig);

		 bind(HouseholdSusceptibility.Config.class).toInstance(
			 HouseholdSusceptibility.newConfig().withSusceptibleHouseholds(householdSusc, 5.0)
		 );


		 Multibinder<SimulationListener> listener = Multibinder.newSetBinder(binder(), SimulationListener.class);

		 listener.addBinding().to(HouseholdSusceptibility.class);


	 }

	 @Provides
	 @Singleton
	 public Config config() {

		 double cologneFactor = 0.5; // Cologne model has about half as many agents as Berlin model, -> 2_352_480

		 //general config
		 Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		 config.global().setRandomSeed(7564655870752979346L);

		 config.vehicles().setVehiclesFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/openDataModel/cologne/input/cologne_2020-vehicles.xml.gz");

		 config.plans().setInputFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/openDataModel/cologne/input/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt_split_grid.xml.gz");

		 config.controller().setOutputDirectory("output-snzOpenCologne-25%");

		 //episim config
		 EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		 episimConfig.addInputEventsFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/openDataModel/cologne/input/cologne_snz_episim_events_wt_25pt_split.xml.gz")
			 .addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		 episimConfig.addInputEventsFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/openDataModel/cologne/input/cologne_snz_episim_events_sa_25pt_split.xml.gz")
			 .addDays(DayOfWeek.SATURDAY);

		 episimConfig.addInputEventsFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/openDataModel/cologne/input/cologne_snz_episim_events_so_25pt_split.xml.gz")
			 .addDays(DayOfWeek.SUNDAY);

		 episimConfig.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay);


		 episimConfig.setCalibrationParameter(1.0e-05 * 0.83 * 1.4 * 1.2 * 1.7);
		 episimConfig.setStartDate("2020-02-25");
		 episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);
		 episimConfig.setSampleSize(25 / 100.);
		 episimConfig.setHospitalFactor(0.5);
		 episimConfig.setThreads(8);
		 episimConfig.setDaysInfectious(Integer.MAX_VALUE);

		 episimConfig.setProgressionConfig(progressionConfig(Transition.config()).build());


		 //---------------------------------------
		 //		I M P O R T
		 //---------------------------------------
		 episimConfig.setInitialInfections(Integer.MAX_VALUE);
		 if (this.diseaseImport != DiseaseImport.no) {

			 configureDiseaseImport(cologneFactor, episimConfig);

		 }


		 //----------------------------------------------------------------------------
		 //		C O N T A C T     I N T E N S I T Y    /    S E A S O N A L I T Y
		 //----------------------------------------------------------------------------

		 configureContactIntensitiesAndSeasonality(episimConfig);


		 //----------------------------------------------------------------------------
		 //		R E S T R I C T I O N S  /  C O N T A C T    R E D U C T I O N
		 //----------------------------------------------------------------------------

		 CreateRestrictionsFromCSV activityParticipation = new CreateRestrictionsFromCSV(episimConfig);

		 activityParticipation.setInput("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/openDataModel/cologne/input/cologne_until20221231_mobility_data.csv");

		 activityParticipation.setScale(this.scale);
		 activityParticipation.setLeisureAsNightly(this.leisureNightly);
		 activityParticipation.setNightlyScale(this.leisureNightlyScale);

		 ConfigBuilder builder;
		 try {
			 builder = activityParticipation.createPolicy();
		 } catch (IOException e1) {
			 throw new UncheckedIOException(e1);
		 }
		 builder.setHospitalScale(this.scale);

		 // school lockdown
		 builder.restrict(LocalDate.parse("2020-03-16"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		 builder.restrict(LocalDate.parse("2020-04-27"), 0.5, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");


		 //leisure & work factor
		 if (this.restrictions != Restrictions.no) {

			 BiFunction<LocalDate, Double, Double> workVacFactor = (d, rf) -> rf * 0.92;

			 builder.applyToRf("2020-04-03", "2020-04-17", workVacFactor, "work", "business");
			 builder.applyToRf("2020-06-26", "2020-08-07", workVacFactor, "work", "business");
		 }

		 //---------------------------------------
		 //		M A S K S
		 //---------------------------------------

		 if (masks == Masks.yes) {

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

			 for (LocalDate date = LocalDate.parse("2020-04-21"); date.isBefore(LocalDate.parse("2021-05-01")); date = date.plusDays(1)) {
				 builder.restrict(date, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.45, FaceMask.SURGICAL, 0.45)), "pt", "errands", "shop_daily", "shop_other");
			 }

		 }


		 episimConfig.setPolicy(builder.build());


		 //---------------------------------------
		 //		T R A C I N G
		 //---------------------------------------


		 if (this.tracing == Tracing.yes) {

			 configureTracing(config, cologneFactor);

		 }

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

		 ControllerUtils.checkConfigConsistencyAndWriteToLog(config, "before loading scenario");

		 final Scenario scenario = ScenarioUtils.loadScenario(config);

		 double capFactor = 1.3;

		 configureVehicleCapacities(scenario, capFactor);

		 return scenario;
	 }

	 private static void configureVehicleCapacities(Scenario scenario, double capFactor) {
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
	 }

	 /**
	  * Configure default contact intensities.
	  */
	 public static void configureContactIntensitiesAndSeasonality(EpisimConfigGroup episimConfig) {
		 int spaces = 20;

		 double workCiMod = 0.75;
		 double leisureCiMod = 0.4;
		 double schoolCiMod = 0.75;

		 //contact intensities
		 episimConfig.getOrAddContainerParams("pt", "tr").setContactIntensity(10.0 * workCiMod).setSpacesPerFacility(spaces);
		 episimConfig.getOrAddContainerParams("work").setContactIntensity(1.47).setSpacesPerFacility(spaces).setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("leisure").setContactIntensity(9.24 * leisureCiMod).setSpacesPerFacility(spaces).setSeasonality(1.0);
		 episimConfig.getOrAddContainerParams("leisPublic").setContactIntensity(9.24 * leisureCiMod).setSpacesPerFacility(spaces).setSeasonality(1.0);
		 episimConfig.getOrAddContainerParams("leisPrivate").setContactIntensity(9.24 * leisureCiMod).setSpacesPerFacility(spaces).setSeasonality(1.0);
		 episimConfig.getOrAddContainerParams("educ_kiga").setContactIntensity(11.0 * schoolCiMod).setSpacesPerFacility(spaces).setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("educ_primary").setContactIntensity(11.0 * schoolCiMod).setSpacesPerFacility(spaces).setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("educ_secondary").setContactIntensity(11.0 * schoolCiMod).setSpacesPerFacility(spaces).setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("educ_tertiary").setContactIntensity(11. * schoolCiMod).setSpacesPerFacility(spaces).setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("educ_higher").setContactIntensity(5.5 * schoolCiMod).setSpacesPerFacility(spaces).setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("educ_other").setContactIntensity(11. * schoolCiMod).setSpacesPerFacility(spaces).setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("shop_daily").setContactIntensity(0.88).setSpacesPerFacility(spaces);
		 episimConfig.getOrAddContainerParams("shop_other").setContactIntensity(0.88).setSpacesPerFacility(spaces);
		 episimConfig.getOrAddContainerParams("errands").setContactIntensity(1.47).setSpacesPerFacility(spaces).setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("business").setContactIntensity(1.47 * workCiMod).setSpacesPerFacility(spaces).setSeasonality(0.5);
		 episimConfig.getOrAddContainerParams("visit").setContactIntensity(9.24 * leisureCiMod).setSpacesPerFacility(spaces).setSeasonality(0.5); // 33/3.57
		 episimConfig.getOrAddContainerParams("home").setContactIntensity(1.0).setSpacesPerFacility(1).setSeasonality(0.5); // 33/33
		 episimConfig.getOrAddContainerParams("quarantine_home").setContactIntensity(1.0).setSpacesPerFacility(1).setSeasonality(0.5); // 33/33

	 }

	 /**
	  * Configure default tracing options
	  *
	  * @param factor scale for tracing capacity
	  */
	 public static void configureTracing(Config config, double factor) {

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
		 tracingConfig.setCapacityType(TracingConfigGroup.CapacityType.PER_PERSON);
		 int tracingCapacity = (int) (200 * factor);
		 tracingConfig.setTracingCapacity_pers_per_day(Map.of(
			 LocalDate.of(2020, 4, 1), (int) (tracingCapacity * 0.2),
			 LocalDate.of(2020, 6, 15), tracingCapacity
		 ));

	 }


	 private void configureDiseaseImport(double cologneFactor, EpisimConfigGroup episimConfig) {

		 // initialize map to store initial infections per strain
		 Map<LocalDate, Integer> infPerDayWild = new HashMap<>();

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

		 for (Map.Entry<LocalDate, Integer> entry : infPerDayWild.entrySet()) {
			 if (entry.getKey().isBefore(LocalDate.parse("2020-08-12"))) {
				 int value = entry.getValue();
				 value = Math.max(1, value);
				 infPerDayWild.put(entry.getKey(), value);
			 }
		 }

		 episimConfig.setInfections_pers_per_day(VirusStrain.SARS_CoV_2, infPerDayWild);

	 }

	 public static void interpolateImport(Map<LocalDate, Integer> importMap, double importFactor, LocalDate start, LocalDate end, double a, double b) {
		 int days = end.getDayOfYear() - start.getDayOfYear();
		 for (int i = 1; i <= days; i++) {
			 double fraction = (double) i / days;
			 importMap.put(start.plusDays(i), (int) Math.round(importFactor * (a + fraction * (b - a))));
		 }
	 }

	 /**
	  * Adds progression config to the given builder.
	  */
	 static Transition.Builder progressionConfig(Transition.Builder builder) {

		 return builder
			 // Inkubationszeit: Die Inkubationszeit [ ... ] liegt im Mittel (Median) bei 5–6 Tagen (Spannweite 1 bis 14 Tage)
			 .from(EpisimPerson.DiseaseStatus.infectedButNotContagious,
				 to(EpisimPerson.DiseaseStatus.contagious, Transition.fixed(0)))

			 // Dauer Infektiosität:: Es wurde geschätzt, dass eine relevante Infektiosität bereits zwei Tage vor Symptombeginn vorhanden ist und die höchste Infektiosität am Tag vor dem Symptombeginn liegt
			 // Dauer Infektiosität: Abstrichproben vom Rachen enthielten vermehrungsfähige Viren bis zum vierten, aus dem Sputum bis zum achten Tag nach Symptombeginn
			 .from(EpisimPerson.DiseaseStatus.contagious,
				 to(EpisimPerson.DiseaseStatus.showingSymptoms, Transition.logNormalWithMedianAndStd(6., 6.)),    //80%
				 to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(8., 8.)))            //20%

			 // Erkankungsbeginn -> Hospitalisierung: Eine Studie aus Deutschland zu 50 Patienten mit eher schwereren Verläufen berichtete für alle Patienten eine mittlere (Median) Dauer von vier Tagen (IQR: 1–8 Tage)
			 .from(EpisimPerson.DiseaseStatus.showingSymptoms,
				 to(EpisimPerson.DiseaseStatus.seriouslySick, Transition.logNormalWithMedianAndStd(5., 5.)),
				 to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(8., 8.)))

			 // Hospitalisierung -> ITS: In einer chinesischen Fallserie betrug diese Zeitspanne im Mittel (Median) einen Tag (IQR: 0–3 Tage)
			 .from(EpisimPerson.DiseaseStatus.seriouslySick,
				 to(EpisimPerson.DiseaseStatus.critical, Transition.logNormalWithMedianAndStd(1., 1.)),
				 to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(14., 14.)))

			 // Dauer des Krankenhausaufenthalts: „WHO-China Joint Mission on Coronavirus Disease 2019“ wird berichtet, dass milde Fälle im Mittel (Median) einen Krankheitsverlauf von zwei Wochen haben und schwere von 3–6 Wochen
			 .from(EpisimPerson.DiseaseStatus.critical,
				 to(EpisimPerson.DiseaseStatus.seriouslySickAfterCritical, Transition.logNormalWithMedianAndStd(21., 21.)))

			 .from(EpisimPerson.DiseaseStatus.seriouslySickAfterCritical,
				 to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(7., 7.)))

			 .from(EpisimPerson.DiseaseStatus.recovered,
				 to(EpisimPerson.DiseaseStatus.susceptible, Transition.fixed(1)))
			 ;
	 }


	 public static class Builder {

		 DiseaseImport diseaseImport = DiseaseImport.yes;
		 Restrictions restrictions = Restrictions.yes;
		 Masks masks = Masks.yes;
		 Tracing tracing = Tracing.yes;
		 Class<? extends InfectionModel> infectionModel = AgeAndProgressionDependentInfectionModelWithSeasonality.class;
		 Class<? extends VaccinationModel> vaccinationModel = NoVaccination.class;
		 Class<? extends TestingModel> testingModel = DefaultTestingModel.class;
		 private final double scale = 1.3;
		 private final boolean leisureNightly = false;

		 private final double leisureNightlyScale = 1.0;
		 private final double householdSusc = 0.35;


		 public SnzCologneOpenProductionScenario build() {
			 return new SnzCologneOpenProductionScenario(this);
		 }

		 public Builder setMasks(Masks masks) {
			 this.masks = masks;
			 return this;
		 }

		 public Builder setInfectionModel(Class<? extends InfectionModel> infectionModel) {
			 this.infectionModel = infectionModel;
			 return this;
		 }

	 }


 }

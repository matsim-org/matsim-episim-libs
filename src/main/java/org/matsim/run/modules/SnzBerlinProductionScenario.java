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
import org.matsim.episim.*;
import org.matsim.episim.TracingConfigGroup.CapacityType;
import org.matsim.episim.model.*;
import org.matsim.episim.model.activity.ActivityParticipationModel;
import org.matsim.episim.model.activity.DefaultParticipationModel;
import org.matsim.episim.model.activity.LocationBasedParticipationModel;
import org.matsim.episim.model.input.CreateAdjustedRestrictionsFromCSV;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.model.input.RestrictionInput;
import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
import org.matsim.episim.policy.AdjustedPolicy;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.ShutdownPolicy;
import org.matsim.vehicles.VehicleType;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scenario for Berlin using Senozon events for different weekdays.
 */
public final class SnzBerlinProductionScenario extends AbstractModule {

	public static class Builder {
		private int importOffset = 0;
		private int sample = 25;
		private DiseaseImport diseaseImport = DiseaseImport.yes;
		private Restrictions restrictions = Restrictions.yes;
		private AdjustRestrictions adjustRestrictions = AdjustRestrictions.no;
		private Masks masks = Masks.yes;
		private Tracing tracing = Tracing.yes;
		private Vaccinations vaccinations = Vaccinations.yes;
		private ChristmasModel christmasModel = ChristmasModel.restrictive;
		private EasterModel easterModel = EasterModel.no;
		private WeatherModel weatherModel = WeatherModel.midpoints_175_250;
		private Snapshot snapshot = Snapshot.no;
		private EpisimConfigGroup.ActivityHandling activityHandling = EpisimConfigGroup.ActivityHandling.startOfDay;
		private Class<? extends InfectionModel> infectionModel = AgeAndProgressionDependentInfectionModelWithSeasonality.class;
		private Class<? extends VaccinationModel> vaccinationModel = VaccinationByAge.class;

		private double imprtFctMult = 1.;
		private double importFactorBeforeJune = 4.;
		private double importFactorAfterJune = 0.5;

		private LocationBasedRestrictions locationBasedRestrictions = LocationBasedRestrictions.no;

		public Builder setImportFactorBeforeJune(double importFactorBeforeJune) {
			this.importFactorBeforeJune = importFactorBeforeJune;
			return this;
		}

		public Builder setImportFactorAfterJune(double importFactorAfterJune) {
			this.importFactorAfterJune = importFactorAfterJune;
			return this;
		}

		public Builder setSample(int sample) {
			this.sample = sample;
			return this;
		}

		public Builder setDiseaseImport(DiseaseImport diseaseImport) {
			this.diseaseImport = diseaseImport;
			return this;
		}

		public Builder setRestrictions(Restrictions restrictions) {
			this.restrictions = restrictions;
			return this;
		}

		public Builder setAdjustRestrictions(AdjustRestrictions adjustRestrictions) {
			this.adjustRestrictions = adjustRestrictions;
			return this;
		}

		public Builder setMasks(Masks masks) {
			this.masks = masks;
			return this;
		}

		public Builder setTracing(Tracing tracing) {
			this.tracing = tracing;
			return this;
		}

		public Builder setVaccinations(Vaccinations vaccinations) {
			this.vaccinations = vaccinations;
			return this;
		}

		public Builder setChristmasModel(ChristmasModel christmasModel) {
			this.christmasModel = christmasModel;
			return this;
		}

		public Builder setEasterModel(EasterModel easterModel) {
			this.easterModel = easterModel;
			return this;
		}

		public Builder setWeatherModel(WeatherModel weatherModel) {
			this.weatherModel = weatherModel;
			return this;
		}

		public Builder setSnapshot(Snapshot snapshot) {
			this.snapshot = snapshot;
			return this;
		}

		public Builder setInfectionModel(Class<? extends InfectionModel> infectionModel) {
			this.infectionModel = infectionModel;
			return this;
		}

		public Builder setVaccinationModel(Class<? extends VaccinationModel> vaccinationModel) {
			this.vaccinationModel = vaccinationModel;
			return this;
		}

		public Builder setActivityHandling(EpisimConfigGroup.ActivityHandling activityHandling) {
			this.activityHandling = activityHandling;
			return this;
		}

		public SnzBerlinProductionScenario createSnzBerlinProductionScenario() {
			return new SnzBerlinProductionScenario(this);
		}

		public Builder setImportOffset(int importOffset) {
			this.importOffset = importOffset;
			return this;
		}

		public Builder setImportFactor(double imprtFctMult) {
			this.imprtFctMult = imprtFctMult;
			return this;
		}
	}

	public static enum DiseaseImport {yes, onlySpring, no}

	public static enum Restrictions {yes, no, onlyEdu, allExceptSchoolsAndDayCare, allExceptUniversities, allExceptEdu}

	public static enum Masks {yes, no}

	public static enum Tracing {yes, no}

	public static enum Vaccinations {yes, no}

	public static enum Snapshot {no, episim_snapshot_060_2020_04_24, episim_snapshot_120_2020_06_23, episim_snapshot_180_2020_08_22, episim_snapshot_240_2020_10_21}

	public static enum ChristmasModel {no, restrictive, permissive}

	public static enum WeatherModel {no, midpoints_175_175, midpoints_175_250, midpoints_200_250, midpoints_175_200, midpoints_200_200}

	public static enum AdjustRestrictions {yes, no}

	public static enum EasterModel {yes, no}

	public static enum LocationBasedRestrictions {yes, no}

	private final int sample;
	private final int importOffset;
	private final DiseaseImport diseaseImport;
	private final Restrictions restrictions;
	private final AdjustRestrictions adjustRestrictions;
	private final Masks masks;
	private final Tracing tracing;
	private final Snapshot snapshot;
	private final Vaccinations vaccinations;
	private final ChristmasModel christmasModel;
	private final EasterModel easterModel;
	private final WeatherModel weatherModel;
	private final Class<? extends InfectionModel> infectionModel;
	private final Class<? extends VaccinationModel> vaccinationModel;
	private final EpisimConfigGroup.ActivityHandling activityHandling;

	private final double imprtFctMult;
	private final double importFactorBeforeJune;
	private final double importFactorAfterJune;
	private final LocationBasedRestrictions locationBasedRestrictions;

	/**
	 * Path pointing to the input folder. Can be configured at runtime with EPISIM_INPUT variable.
	 */
	public static final Path INPUT = EpisimUtils.resolveInputPath("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input");

	/**
	 * Empty constructor is needed for running scenario from command line.
	 */
	@SuppressWarnings("unused")
	private SnzBerlinProductionScenario() {
		this(new Builder());
	}

	private SnzBerlinProductionScenario(Builder builder) {
		this.sample = builder.sample;
		this.diseaseImport = builder.diseaseImport;
		this.restrictions = builder.restrictions;
		this.adjustRestrictions = builder.adjustRestrictions;
		this.masks = builder.masks;
		this.tracing = builder.tracing;
		this.snapshot = builder.snapshot;
		this.activityHandling = builder.activityHandling;
		this.infectionModel = builder.infectionModel;
		this.importOffset = builder.importOffset;
		this.vaccinationModel = builder.vaccinationModel;
		this.vaccinations = builder.vaccinations;
		this.christmasModel = builder.christmasModel;
		this.weatherModel = builder.weatherModel;
		this.imprtFctMult = builder.imprtFctMult;
		this.importFactorBeforeJune = builder.importFactorBeforeJune;
		this.importFactorAfterJune = builder.importFactorAfterJune;
		this.easterModel = builder.easterModel;
		this.locationBasedRestrictions = builder.locationBasedRestrictions;
	}

	public static void interpolateImport(Map<LocalDate, Integer> importMap, double importFactor, LocalDate start, LocalDate end, double a, double b) {
		int days = end.getDayOfYear() - start.getDayOfYear();
		for (int i = 1; i <= days; i++) {
			double fraction = (double) i / days;
			importMap.put(start.plusDays(i), (int) Math.round(importFactor * (a + fraction * (b - a))));
		}
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

		if (adjustRestrictions == AdjustRestrictions.yes)
			bind(ShutdownPolicy.class).to(AdjustedPolicy.class).in(Singleton.class);
		else
			bind(ShutdownPolicy.class).to(FixedPolicy.class).in(Singleton.class);

		if (activityHandling == EpisimConfigGroup.ActivityHandling.startOfDay) {
			if (locationBasedRestrictions == LocationBasedRestrictions.yes) {
				bind(ActivityParticipationModel.class).to(LocationBasedParticipationModel.class);
			} else {
				bind(ActivityParticipationModel.class).to(DefaultParticipationModel.class);
			}
		}
	}

	@Provides
	@Singleton
	public Config config() {

		if (this.sample != 25 && this.sample != 100)
			throw new RuntimeException("Sample size not calibrated! Currently only 25% is calibrated. Comment this line out to continue.");

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		config.global().setRandomSeed(7564655870752979346L);

		config.vehicles().setVehiclesFile(INPUT.resolve("de_2020-vehicles.xml").toString());

		config.plans().setInputFile(inputForSample("be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_%dpt_split.xml.gz", sample));

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_wt_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_sa_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.SATURDAY);

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_so_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.SUNDAY);

		episimConfig.setActivityHandling(activityHandling);

		episimConfig.setThreads(6);

//		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_wt_%dpt_split.xml.gz", sample))
//				.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
//
//		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_sa_%dpt_split.xml.gz", sample))
//				.addDays(DayOfWeek.SATURDAY);
//
//		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_so_%dpt_split.xml.gz", sample))
//				.addDays(DayOfWeek.SUNDAY);

		episimConfig.setCalibrationParameter(1.7E-5 * 0.8);
		episimConfig.setStartDate("2020-02-25");
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);
		episimConfig.setSampleSize(this.sample / 100.);
		episimConfig.setHospitalFactor(0.5);
		episimConfig.setProgressionConfig(AbstractSnzScenario2020.baseProgressionConfig(Transition.config()).build());
		episimConfig.setThreads(8);

		if (this.snapshot != Snapshot.no) episimConfig.setStartFromSnapshot(INPUT.resolve("snapshots/" + snapshot + ".zip").toString());

		//inital infections and import
		episimConfig.setInitialInfections(Integer.MAX_VALUE);
		if (this.diseaseImport != DiseaseImport.no) {
			episimConfig.setInitialInfectionDistrict(null);
			Map<LocalDate, Integer> importMap = new HashMap<>();
			interpolateImport(importMap, imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-02-24").plusDays(importOffset),
					LocalDate.parse("2020-03-09").plusDays(importOffset), 0.9, 23.1);
			interpolateImport(importMap, imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-03-09").plusDays(importOffset),
					LocalDate.parse("2020-03-23").plusDays(importOffset), 23.1, 3.9);
			interpolateImport(importMap, imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-03-23").plusDays(importOffset),
					LocalDate.parse("2020-04-13").plusDays(importOffset), 3.9, 0.1);
			if (this.diseaseImport == DiseaseImport.yes) {
				interpolateImport(importMap, imprtFctMult * importFactorAfterJune, LocalDate.parse("2020-06-08").plusDays(importOffset),
						LocalDate.parse("2020-07-13").plusDays(importOffset), 0.1, 2.7);
				interpolateImport(importMap, imprtFctMult * importFactorAfterJune, LocalDate.parse("2020-07-13").plusDays(importOffset),
						LocalDate.parse("2020-08-10").plusDays(importOffset), 2.7, 17.9);
				interpolateImport(importMap, imprtFctMult * importFactorAfterJune, LocalDate.parse("2020-08-10").plusDays(importOffset),
						LocalDate.parse("2020-09-07").plusDays(importOffset), 17.9, 6.1);
				interpolateImport(importMap, imprtFctMult * importFactorAfterJune, LocalDate.parse("2020-10-26").plusDays(importOffset),
						LocalDate.parse("2020-12-21").plusDays(importOffset), 6.1, 1.1);
			}
			episimConfig.setInfections_pers_per_day(importMap);
		} else {
			episimConfig.setInitialInfectionDistrict("Berlin");
			episimConfig.setCalibrationParameter(2.54e-5);
		}

		if (this.infectionModel != AgeDependentInfectionModelWithSeasonality.class) {
			if (this.diseaseImport == DiseaseImport.yes) {
				episimConfig.setCalibrationParameter(1.6E-5);
			} else {
				episimConfig.setCalibrationParameter(1.6E-5 * 2.54e-5 / 1.7E-5);
			}
		}

		int spaces = 20;

		//contact intensities
		episimConfig.getOrAddContainerParams("pt", "tr").setContactIntensity(10.0).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("work").setContactIntensity(1.47).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("leisure").setContactIntensity(9.24).setSpacesPerFacility(spaces).setSeasonal(true);
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

		// todo: use the plain 1/v values (1./3.57, 1./33, ...) and multiply theta with 33.  kai, feb'21

		//restrictions and masks
		RestrictionInput activityParticipation;
		SnzBerlinScenario25pct2020.BasePolicyBuilder basePolicyBuilder = new SnzBerlinScenario25pct2020.BasePolicyBuilder(episimConfig);
		if (adjustRestrictions == AdjustRestrictions.yes) {
			activityParticipation = new CreateAdjustedRestrictionsFromCSV();
		} else {
			activityParticipation = new CreateRestrictionsFromCSV(episimConfig);
		}

		String untilDate = "20210802";
		activityParticipation.setInput(INPUT.resolve("BerlinSnzData_daily_until20210815.csv"));

		//location based restrictions
		if (locationBasedRestrictions == LocationBasedRestrictions.yes) {
			config.facilities().setInputFile(INPUT.resolve("be_2020-facilities_assigned_simplified_grid_WithNeighborhoodAndPLZ.xml.gz").toString());
			episimConfig.setDistrictLevelRestrictions(EpisimConfigGroup.DistrictLevelRestrictions.yes);
			episimConfig.setDistrictLevelRestrictionsAttribute("subdistrict");

			if (activityParticipation instanceof CreateRestrictionsFromCSV) {
				List<String> subdistricts = Arrays.asList("Spandau", "Neukoelln", "Reinickendorf",
						"Charlottenburg_Wilmersdorf", "Marzahn_Hellersdorf", "Mitte", "Pankow", "Friedrichshain_Kreuzberg",
						"Tempelhof_Schoeneberg", "Treptow_Koepenick", "Lichtenberg", "Steglitz_Zehlendorf");


				Map<String, Path> subdistrictInputs = new HashMap<>();
				for (String subdistrict : subdistricts) {
					subdistrictInputs.put(subdistrict, INPUT.resolve("perNeighborhood/" + subdistrict + "SnzData_daily_until" + untilDate + ".csv"));
				}

				((CreateRestrictionsFromCSV) activityParticipation).setDistrictInputs(subdistrictInputs);
			}
		}

		basePolicyBuilder.setActivityParticipation(activityParticipation);

		if (this.restrictions == Restrictions.no || this.restrictions == Restrictions.onlyEdu) {
			basePolicyBuilder.setActivityParticipation(null);
		}
		if (this.restrictions == Restrictions.no || this.restrictions == Restrictions.allExceptEdu || this.restrictions == Restrictions.allExceptSchoolsAndDayCare) {
			basePolicyBuilder.setRestrictSchoolsAndDayCare(false);
		}
		if (this.restrictions == Restrictions.no || this.restrictions == Restrictions.allExceptEdu || this.restrictions == Restrictions.allExceptUniversities) {
			basePolicyBuilder.setRestrictUniversities(false);
		}

		if (this.masks == Masks.no) basePolicyBuilder.setMaskCompliance(0);
		basePolicyBuilder.setCiCorrections(Map.of());
		FixedPolicy.ConfigBuilder builder = basePolicyBuilder.buildFixed();

		//tracing
		if (this.tracing == Tracing.yes) {
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

		//christmasModel
		if (this.christmasModel != ChristmasModel.no) {
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

			// TODO: this need to be set earlier in the policy builder
			if (this.adjustRestrictions != AdjustRestrictions.no) {
				throw new RuntimeException("Christmas model currently only works when adjusted restrictions are switched off!");
			}

			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				double fraction = 0.5925;

				if (this.christmasModel == ChristmasModel.restrictive) {
					builder.restrict(LocalDate.parse("2020-12-24"), 1.0, act);
				}
				if (this.christmasModel == ChristmasModel.permissive) {
					builder.restrict(LocalDate.parse("2020-12-24"), 1.0, act);
					builder.restrict(LocalDate.parse("2020-12-31"), 1.0, act);
					builder.restrict(LocalDate.parse("2021-01-02"), fraction, act);
				}
			}
		}

		if (this.easterModel == EasterModel.yes) {
			inputDays.put(LocalDate.parse("2021-03-08"), DayOfWeek.SUNDAY);
			inputDays.put(LocalDate.parse("2021-04-02"), DayOfWeek.SUNDAY);
			inputDays.put(LocalDate.parse("2021-04-05"), DayOfWeek.SUNDAY);

			// TODO: this need to be set earlier in the policy builder
			if (this.adjustRestrictions != AdjustRestrictions.no) {
				throw new RuntimeException("Christmas model currently only works when adjusted restrictions are switched off!");
			}

			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				double fraction = 0.72;
				builder.restrict(LocalDate.parse("2021-04-02"), 1.0, act);
				builder.restrict(LocalDate.parse("2021-04-03"), 1.0, act);
				builder.restrict(LocalDate.parse("2021-04-04"), 1.0, act);
				builder.restrict(LocalDate.parse("2021-04-05"), 1.0, act);
				builder.restrict(LocalDate.parse("2021-04-06"), fraction, act);
			}
		}


		episimConfig.setInputDays(inputDays);

		//outdoorFractions
		if (this.weatherModel != WeatherModel.no) {
			double midpoint1 = 0.1 * Double.parseDouble(this.weatherModel.toString().split("_")[1]);
			double midpoint2 = 0.1 * Double.parseDouble(this.weatherModel.toString().split("_")[2]);
			try {
				Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractions2(SnzBerlinProductionScenario.INPUT.resolve("tempelhofWeatherUntil20210816.csv").toFile(),
						SnzBerlinProductionScenario.INPUT.resolve("temeplhofWeatherDataAvg2000-2020.csv").toFile(), 0.5, midpoint1, midpoint2, 5.);
				episimConfig.setLeisureOutdoorFraction(outdoorFractions);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			episimConfig.setLeisureOutdoorFraction(Map.of(
					LocalDate.of(2020, 1, 1), 0.)
			);
		}

		// TODO: is this still needed with adjusted policy?
		//leisure & work factor
		double leisureFactor = 1.6;
		if (this.restrictions != Restrictions.no) {
			builder.apply("2020-10-15", "2020-12-14", (d, e) -> e.put("fraction", 1 - leisureFactor * (1 - (double) e.get("fraction"))), "leisure");

			double workVacFactor = 0.92;
			builder.apply("2020-04-03", "2020-04-17", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
			builder.apply("2020-06-26", "2020-08-07", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
			builder.apply("2020-10-09", "2020-10-23", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
			builder.apply("2020-12-18", "2021-01-01", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
			builder.apply("2021-01-29", "2021-02-05", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
			builder.apply("2021-03-26", "2021-04-09", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
			builder.apply("2021-06-25", "2021-08-06", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
		}


		// vaccinations
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

		if (this.vaccinations.equals(Vaccinations.yes)) {
			double effectivnessMRNA = 0.7;
			double factorShowingSymptomsMRNA = 0.05 / (1 - effectivnessMRNA); //95% protection against symptoms
			double factorSeriouslySickMRNA = 0.02 / ((1 - effectivnessMRNA) * factorShowingSymptomsMRNA); //98% protection against severe disease
			int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot
			vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
					.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
							.atDay(1, 0.0)
							.atFullEffect(effectivnessMRNA)
							.atDay(fullEffectMRNA + 5*365, 0.0) //10% reduction every 6 months (source: TC)
					)
					.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B117)
							.atDay(1, 0.0)
							.atFullEffect(effectivnessMRNA)
							.atDay(fullEffectMRNA + 5*365, 0.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
							.atDay(1, 1.0)
							.atFullEffect(factorShowingSymptomsMRNA)
							.atDay(fullEffectMRNA + 5*365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.B117)
							.atDay(1, 1.0)
							.atFullEffect(factorShowingSymptomsMRNA)
							.atDay(fullEffectMRNA + 5*365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
							.atDay(1, 1.0)
							.atFullEffect(factorSeriouslySickMRNA)
							.atDay(fullEffectMRNA + 5*365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.B117)
							.atDay(1, 1.0)
							.atFullEffect(factorSeriouslySickMRNA)
							.atDay(fullEffectMRNA + 5*365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setDaysBeforeFullEffect(fullEffectMRNA); 
		
		double effectivnessVector = 0.5;
		double factorShowingSymptomsVector = 0.25 / (1 - effectivnessVector); //75% protection against symptoms
		double factorSeriouslySickVector = 0.15 / ((1 - effectivnessVector) * factorShowingSymptomsMRNA); //85% protection against severe disease
		int fullEffectVector = 10 * 7; //second shot after 9 weeks, full effect one week after second shot

		vaccinationConfig.getOrAddParams(VaccinationType.vector)
			.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
					.atDay(1, 0.0)
					.atFullEffect(effectivnessVector)
					.atDay(fullEffectVector + 5*365, 0.0) //10% reduction every 6 months (source: TC)
			)
			.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B117)
					.atDay(1, 0.0)
					.atFullEffect(effectivnessVector)
					.atDay(fullEffectVector + 5*365, 0.0) //10% reduction every 6 months (source: TC)
			)
			.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
					.atDay(1, 1.0)
					.atFullEffect(factorShowingSymptomsVector)
					.atDay(fullEffectVector + 5*365, 1.0) //10% reduction every 6 months (source: TC)
			)
			.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.B117)
					.atDay(1, 1.0)
					.atFullEffect(factorShowingSymptomsVector)
					.atDay(fullEffectVector + 5*365, 1.0) //10% reduction every 6 months (source: TC)
			)
			.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
					.atDay(1, 1.0)
					.atFullEffect(factorSeriouslySickVector)
					.atDay(fullEffectVector + 5*365, 1.0) //10% reduction every 6 months (source: TC)
			)
			.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.B117)
					.atDay(1, 1.0)
					.atFullEffect(factorSeriouslySickVector)
					.atDay(fullEffectVector + 5*365, 1.0) //10% reduction every 6 months (source: TC)
			)
			.setDaysBeforeFullEffect(fullEffectVector); //second shot after 6 weeks, full effect one week after second shot

			// Based on https://experience.arcgis.com/experience/db557289b13c42e4ac33e46314457adc

			Map<LocalDate, Map<VaccinationType, Double>> share = new HashMap<>();

			share.put(LocalDate.parse("2020-01-01"), Map.of(VaccinationType.mRNA, 1d, VaccinationType.vector, 0d));
			share.put(LocalDate.parse("2020-12-28"), Map.of(VaccinationType.mRNA, 1.00d, VaccinationType.vector, 0.00d));
			share.put(LocalDate.parse("2021-01-04"), Map.of(VaccinationType.mRNA, 1.00d, VaccinationType.vector, 0.00d));
			share.put(LocalDate.parse("2021-01-11"), Map.of(VaccinationType.mRNA, 1.00d, VaccinationType.vector, 0.00d));
			share.put(LocalDate.parse("2021-01-18"), Map.of(VaccinationType.mRNA, 1.00d, VaccinationType.vector, 0.00d));
			share.put(LocalDate.parse("2021-01-25"), Map.of(VaccinationType.mRNA, 1.00d, VaccinationType.vector, 0.00d));
			share.put(LocalDate.parse("2021-02-01"), Map.of(VaccinationType.mRNA, 1.00d, VaccinationType.vector, 0.00d));
			share.put(LocalDate.parse("2021-02-08"), Map.of(VaccinationType.mRNA, 0.87d, VaccinationType.vector, 0.13d));
			share.put(LocalDate.parse("2021-02-15"), Map.of(VaccinationType.mRNA, 0.75d, VaccinationType.vector, 0.25d));
			share.put(LocalDate.parse("2021-02-22"), Map.of(VaccinationType.mRNA, 0.63d, VaccinationType.vector, 0.37d));
			share.put(LocalDate.parse("2021-03-01"), Map.of(VaccinationType.mRNA, 0.52d, VaccinationType.vector, 0.48d));
			share.put(LocalDate.parse("2021-03-08"), Map.of(VaccinationType.mRNA, 0.45d, VaccinationType.vector, 0.55d));
			share.put(LocalDate.parse("2021-03-15"), Map.of(VaccinationType.mRNA, 0.75d, VaccinationType.vector, 0.25d));
			share.put(LocalDate.parse("2021-03-22"), Map.of(VaccinationType.mRNA, 0.55d, VaccinationType.vector, 0.45d));
			share.put(LocalDate.parse("2021-03-29"), Map.of(VaccinationType.mRNA, 0.71d, VaccinationType.vector, 0.29d));
			share.put(LocalDate.parse("2021-04-05"), Map.of(VaccinationType.mRNA, 0.77d, VaccinationType.vector, 0.23d));
			share.put(LocalDate.parse("2021-04-12"), Map.of(VaccinationType.mRNA, 0.76d, VaccinationType.vector, 0.24d));
			share.put(LocalDate.parse("2021-04-19"), Map.of(VaccinationType.mRNA, 0.70d, VaccinationType.vector, 0.30d));
			share.put(LocalDate.parse("2021-04-26"), Map.of(VaccinationType.mRNA, 0.91d, VaccinationType.vector, 0.09d));
			share.put(LocalDate.parse("2021-05-03"), Map.of(VaccinationType.mRNA, 0.78d, VaccinationType.vector, 0.22d));
			share.put(LocalDate.parse("2021-05-10"), Map.of(VaccinationType.mRNA, 0.81d, VaccinationType.vector, 0.19d));
			share.put(LocalDate.parse("2021-05-17"), Map.of(VaccinationType.mRNA, 0.70d, VaccinationType.vector, 0.30d));
			share.put(LocalDate.parse("2021-05-24"), Map.of(VaccinationType.mRNA, 0.67d, VaccinationType.vector, 0.33d));
			share.put(LocalDate.parse("2021-05-31"), Map.of(VaccinationType.mRNA, 0.72d, VaccinationType.vector, 0.28d));
			share.put(LocalDate.parse("2021-06-07"), Map.of(VaccinationType.mRNA, 0.74d, VaccinationType.vector, 0.26d));
			share.put(LocalDate.parse("2021-06-14"), Map.of(VaccinationType.mRNA, 0.79d, VaccinationType.vector, 0.21d));
			share.put(LocalDate.parse("2021-06-21"), Map.of(VaccinationType.mRNA, 0.87d, VaccinationType.vector, 0.13d));
			share.put(LocalDate.parse("2021-06-28"), Map.of(VaccinationType.mRNA, 0.91d, VaccinationType.vector, 0.09d));
			share.put(LocalDate.parse("2021-07-05"), Map.of(VaccinationType.mRNA, 0.91d, VaccinationType.vector, 0.09d));
			share.put(LocalDate.parse("2021-07-12"), Map.of(VaccinationType.mRNA, 0.87d, VaccinationType.vector, 0.13d));
			share.put(LocalDate.parse("2021-07-19"), Map.of(VaccinationType.mRNA, 0.87d, VaccinationType.vector, 0.13d));
			share.put(LocalDate.parse("2021-07-26"), Map.of(VaccinationType.mRNA, 0.86d, VaccinationType.vector, 0.14d));
			share.put(LocalDate.parse("2021-08-02"), Map.of(VaccinationType.mRNA, 0.85d, VaccinationType.vector, 0.15d));
			share.put(LocalDate.parse("2021-08-09"), Map.of(VaccinationType.mRNA, 0.86d, VaccinationType.vector, 0.14d));

			vaccinationConfig.setVaccinationShare(share);


			Map<LocalDate, Integer> vaccinations = new HashMap<>();

			int population = 4_800_000;

			vaccinations.put(LocalDate.parse("2020-01-01"), 0);

			vaccinations.put(LocalDate.parse("2020-12-27"), (int) (0.003 * population / 6));
			vaccinations.put(LocalDate.parse("2021-01-02"), (int) ((0.007 - 0.004) * population / 7));
			vaccinations.put(LocalDate.parse("2021-01-09"), (int) ((0.013 - 0.007) * population / 7));
			vaccinations.put(LocalDate.parse("2021-01-16"), (int) ((0.017 - 0.013) * population / 7));
			vaccinations.put(LocalDate.parse("2021-01-23"), (int) ((0.024 - 0.017) * population / 7));
			vaccinations.put(LocalDate.parse("2021-01-30"), (int) ((0.030 - 0.024) * population / 7));
			vaccinations.put(LocalDate.parse("2021-02-06"), (int) ((0.034 - 0.030) * population / 7));
			vaccinations.put(LocalDate.parse("2021-02-13"), (int) ((0.039 - 0.034) * population / 7));
			vaccinations.put(LocalDate.parse("2021-02-20"), (int) ((0.045 - 0.039) * population / 7));
			vaccinations.put(LocalDate.parse("2021-02-27"), (int) ((0.057 - 0.045) * population / 7));
			vaccinations.put(LocalDate.parse("2021-03-06"), (int) ((0.071 - 0.057) * population / 7));
			vaccinations.put(LocalDate.parse("2021-03-13"), (int) ((0.088 - 0.071) * population / 7));
			vaccinations.put(LocalDate.parse("2021-03-20"), (int) ((0.105 - 0.088) * population / 7));
			vaccinations.put(LocalDate.parse("2021-03-27"), (int) ((0.120 - 0.105) * population / 7));
			vaccinations.put(LocalDate.parse("2021-04-03"), (int) ((0.140 - 0.120) * population / 7));
			vaccinations.put(LocalDate.parse("2021-04-10"), (int) ((0.183 - 0.140) * population / 7));
			//extrapolated from 5.4. until 22.4.
			vaccinations.put(LocalDate.parse("2021-04-17"), (int) ((0.207 - 0.123) * population / 17));

			vaccinations.put(LocalDate.parse("2021-04-22"), (int) ((0.279 - 0.207) * population / 13));
			vaccinations.put(LocalDate.parse("2021-05-05"), (int) ((0.404 - 0.279) * population / 23));
			vaccinations.put(LocalDate.parse("2021-05-28"), (int) ((0.484 - 0.404) * population / 14));
			vaccinations.put(LocalDate.parse("2021-06-11"), (int) ((0.535 - 0.484) * population / 14));
			vaccinations.put(LocalDate.parse("2021-06-25"), (int) ((0.583 - 0.535) * population / 19));
			vaccinations.put(LocalDate.parse("2021-07-14"), (int) ((0.605 - 0.583) * population / 14)); // until 07-28

			vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);
		}



		episimConfig.setPolicy(builder.build());

		config.controler().setOutputDirectory("output-snzWeekScenario-" + sample + "%");

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

		// save some time for not needed inputs (facilities are needed for location based restrictions)
		if (locationBasedRestrictions == LocationBasedRestrictions.no) {
			config.facilities().setInputFile(null);
		}

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

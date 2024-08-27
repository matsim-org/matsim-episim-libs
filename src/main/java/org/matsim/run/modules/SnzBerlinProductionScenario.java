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
import org.matsim.episim.model.input.CreateAdjustedRestrictionsFromCSV;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.model.input.RestrictionInput;
import org.matsim.episim.model.listener.HouseholdSusceptibility;
import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
import org.matsim.episim.model.vaccination.VaccinationFromData;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.policy.AdjustedPolicy;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.policy.ShutdownPolicy;

import javax.inject.Singleton;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Scenario for Berlin using Senozon events for different weekdays.
 */
public final class SnzBerlinProductionScenario extends SnzProductionScenario {

	public static class Builder extends SnzProductionScenario.Builder<SnzBerlinProductionScenario> {

		private Snapshot snapshot = Snapshot.no;

		public Builder setSnapshot(Snapshot snapshot) {
			this.snapshot = snapshot;
			return this;
		}

		@Override
		public SnzBerlinProductionScenario build() {
			return new SnzBerlinProductionScenario(this);
		}
	}

	public enum Snapshot {no, episim_snapshot_060_2020_04_24, episim_snapshot_120_2020_06_23, episim_snapshot_180_2020_08_22, episim_snapshot_240_2020_10_21}

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

		//TODO: this is not in the cologne prod scenario, is it still needed?
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

		bind(VaccinationFromData.Config.class).toInstance(
				VaccinationFromData.newConfig("11000")
						.withAgeGroup("05-11", 237886.9)
						.withAgeGroup("12-17", 182304.4)
						.withAgeGroup("18-59", 2138015)
						.withAgeGroup("60+", 915851)
		);

		// TODO: from Cologne, do we need?
		//		Multibinder.newSetBinder(binder(), SimulationListener.class)
		//				.addBinding().to(HouseholdSusceptibility.class);


	}

	@Provides
	@Singleton
	public Config config() {

		//		if (this.sample != 25 && this.sample != 100)
		//			throw new RuntimeException("Sample size not calibrated! Currently only 25% is calibrated. Comment this line out to continue.");

		//general config
		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());


		config.global().setRandomSeed(7564655870752979346L);

		config.vehicles().setVehiclesFile(INPUT.resolve("de_2020-vehicles.xml").toString());

		config.plans().setInputFile(inputForSample("be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_%dpt_split.xml.gz", sample));

		//episim config
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_wt_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_sa_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.SATURDAY);

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_so_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.SUNDAY);

		episimConfig.setActivityHandling(activityHandling);

		//		episimConfig.setThreads(6); // TODO: check repeat from below

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
		episimConfig.setThreads(8);
		episimConfig.setDaysInfectious(Integer.MAX_VALUE);

		//progression model
		//		episimConfig.setProgressionConfig(AbstractSnzScenario2020.baseProgressionConfig(Transition.config()).build());
		episimConfig.setProgressionConfig(SnzProductionScenario.progressionConfig(Transition.config()).build());


		//snapshot
		if (this.snapshot != Snapshot.no)
			episimConfig.setStartFromSnapshot(INPUT.resolve("snapshots/" + snapshot + ".zip").toString());

		//inital infections and import // TODO: this is done differently in Cologne
		episimConfig.setInitialInfections(Integer.MAX_VALUE);
		if (this.diseaseImport != DiseaseImport.no) {

			SnzProductionScenario.configureDiseaseImport(
					episimConfig,
					diseaseImport,
					importOffset,
					imprtFctMult,
					importFactorBeforeJune,
					importFactorAfterJune
			);

		} else {
			episimConfig.setInitialInfectionDistrict("Berlin");
			episimConfig.setCalibrationParameter(2.54e-5);
		}

		//age-dependent infection model
		if (this.infectionModel != AgeDependentInfectionModelWithSeasonality.class) {
			if (this.diseaseImport == DiseaseImport.yes) {
				episimConfig.setCalibrationParameter(1.6E-5); // TODO: setting calibration param in multiple places may be error-prone
			} else {
				episimConfig.setCalibrationParameter(1.6E-5 * 2.54e-5 / 1.7E-5);
			}
		}

		//contact intensities
		SnzProductionScenario.configureContactIntensities(episimConfig);

		//restrictions and masks
		RestrictionInput activityParticipation;
		SnzBerlinScenario25pct2020.BasePolicyBuilder basePolicyBuilder = new SnzBerlinScenario25pct2020.BasePolicyBuilder(episimConfig); // TODO: should SnzBerlinScenario25pct2020 be absorbed into this class to match Cologne
		if (adjustRestrictions == AdjustRestrictions.yes) {
			activityParticipation = new CreateAdjustedRestrictionsFromCSV();
		} else {
			activityParticipation = new CreateRestrictionsFromCSV(episimConfig);
		}

		String untilDate = "20220204";
		activityParticipation.setInput(INPUT.resolve("BerlinSnzData_daily_until20220204.csv"));

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

		//curfew TODO: when is the restriction of closing hours lifted? (also for Cologne)
		builder.restrict("2021-04-24", Restriction.ofClosingHours(22, 5), "leisure", "visit");
		Map<LocalDate, Double> curfewCompliance = new HashMap<LocalDate, Double>();
		curfewCompliance.put(LocalDate.parse("2021-04-24"), 1.0);//https://www.berlin.de/aktuelles/berlin/6522691-958092-naechtliche-ausgangsbeschraenkungen-ab-s.html
		curfewCompliance.put(LocalDate.parse("2021-05-19"), 0.0); //https://www.berlin.de/aktuelles/berlin/6593002-958092-ausgangsbeschraenkungen-gelten-mittwoch-.html
		episimConfig.setCurfewCompliance(curfewCompliance);


		//tracing
		if (this.tracing == Tracing.yes) {

			SnzProductionScenario.configureTracing(config, 1.0);

		}

		//christmas and easter models
		Map<LocalDate, DayOfWeek> inputDays = new HashMap<>();

		if (this.christmasModel != ChristmasModel.no) {
			SnzProductionScenario.configureChristmasModel(christmasModel, inputDays, builder);
		}

		if (this.easterModel == EasterModel.yes) {
			SnzProductionScenario.configureEasterModel(inputDays, builder);
		}

		episimConfig.setInputDays(inputDays);

		//outdoorFractions
		if (this.weatherModel != WeatherModel.no) {

			SnzProductionScenario.configureWeather(episimConfig, weatherModel,
					INPUT.resolve("tempelhofWeatherUntil20220208.csv").toFile(),
					INPUT.resolve("temeplhofWeatherDataAvg2000-2020.csv").toFile(), 1.0)
			;


		} else {
			episimConfig.setLeisureOutdoorFraction(Map.of(
					LocalDate.of(2020, 1, 1), 0.)
			);
		}

		//leisure & work factor
		double leisureFactor = 1.6;
		if (this.restrictions != Restrictions.no) {
			builder.applyToRf("2020-10-15", "2020-12-14", (d, e) -> 1 - leisureFactor * (1 - e), "leisure");

			double workVacFactor = 0.92;
			BiFunction<LocalDate, Double, Double> f = (d, e) -> workVacFactor * e;
			//WorkVac Factors are applied from last school day -> last vacation day (Saturday not Sunday,
			// if vacation ends on weekend, b/c new restrictions for following week are always set sundays)
			builder.applyToRf("2020-04-03", "2020-04-17", f, "work", "business");
			//Sommerferien
			builder.applyToRf("2020-06-26", "2020-08-07", f, "work", "business");
			//Herbstferien
			builder.applyToRf("2020-10-09", "2020-10-23", f, "work", "business");
			//Weihnachtsferien
			builder.applyToRf("2020-12-18", "2021-01-01", f, "work", "business");
			//Winterferien
			builder.applyToRf("2021-01-29", "2021-02-05", f, "work", "business");
			//Osterferien
			builder.applyToRf("2021-03-26", "2021-04-09", f, "work", "business");
			//Sommerfeirien
			builder.applyToRf("2021-06-25", "2021-08-06", f, "work", "business");
			//Herbstferien
			builder.applyToRf("2021-10-08", "2021-10-22", f, "work", "business");
			//Weihnachtsferien
			builder.applyToRf("2021-12-17", "2022-01-04", f, "work", "business");
			//Winterferien
			builder.applyToRf("2022-01-28", "2022-02-04", f, "work", "business");
			// future
			//Osterferien
			builder.restrict(LocalDate.parse("2022-04-08"), 0.92, "work", "business");
			builder.restrict(LocalDate.parse("2022-04-23"), 1.0, "work", "business");
			//Sommerferien
			builder.restrict(LocalDate.parse("2022-07-07"), 0.92, "work", "business");
			builder.restrict(LocalDate.parse("2022-08-19"), 1.0, "work", "business");
			//Herbstferien
			builder.restrict(LocalDate.parse("2022-10-21"), 0.92, "work", "business");
			builder.restrict(LocalDate.parse("2022-11-05"), 1.0, "work", "business");
			//Weihnachtsferien
			builder.restrict(LocalDate.parse("2022-12-22"), 0.92, "work", "business");
			builder.restrict(LocalDate.parse("2023-01-02"), 1.0, "work", "business");

		}


		//vaccinations
		if (this.vaccinations.equals(Vaccinations.yes)) {

			VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

			SnzProductionScenario.configureVaccines(vaccinationConfig, 4_800_000);

		}

		episimConfig.setPolicy(builder.build());

		config.controler().setOutputDirectory("output-snzWeekScenario-" + sample + "%");

		return config;
	}
}

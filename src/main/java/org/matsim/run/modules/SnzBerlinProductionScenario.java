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
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.TracingConfigGroup.CapacityType;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.vehicles.VehicleType;

import javax.inject.Singleton;

import java.io.IOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Scenario for Berlin using Senozon events for different weekdays.
 */
public final class SnzBerlinProductionScenario extends AbstractModule {
	// classes should either be final or package-private if not explicitly designed for inheritance.  kai, dec'20
	
	public static enum DiseaseImport {yes, onlySpring, no}
	public static enum Restrictions {yes, no, onlyEdu, allExceptSchoolsAndDayCare, allExceptUniversities, allExceptEdu}
	public static enum Masks {yes, no}
	public static enum Tracing {yes, no}
	public static enum Vaccinations {yes, no}
	public static enum ChristmasModel {no, restrictive, permissive}
	public static enum EasterModel {yes, no}
	public static enum WeatherModel {no, midpoints_175_250, midpoints_175_175, midpoints_200_250}
	public static enum Snapshot {no, episim_snapshot_060_2020_04_24, episim_snapshot_120_2020_06_23, episim_snapshot_180_2020_08_22, episim_snapshot_240_2020_10_21}


	public static class Builder{
		private int importOffset = 0;
		private int sample = 25;
		private DiseaseImport diseaseImport = DiseaseImport.yes;
		private Restrictions restrictions = Restrictions.yes;
		private Masks masks = Masks.yes;
		private Tracing tracing = Tracing.yes;
		private Vaccinations vaccinations = Vaccinations.yes;
		private ChristmasModel christmasModel = ChristmasModel.restrictive;
		private EasterModel easterModel = EasterModel.yes;
		private WeatherModel weatherModel = WeatherModel.midpoints_175_250;
		private Snapshot snapshot = Snapshot.no;
		private Class<? extends InfectionModel> infectionModel = AgeDependentInfectionModelWithSeasonality.class;
		private Class<? extends VaccinationModel> vaccinationModel = VaccinationByAge.class;
		private double imprtFctMult = 1.;
		private double importFactorBeforeJune = 4.;
		private double importFactorAfterJune = 0.5;

		public Builder setImportFactorBeforeJune( double importFactorBeforeJune ){
			this.importFactorBeforeJune = importFactorBeforeJune;
			return this;
		}
		public Builder setImportFactorAfterJune( double importFactorAfterJune ){
			this.importFactorAfterJune = importFactorAfterJune;
			return this;
		}
		public Builder setSample( int sample ){
			this.sample = sample;
			return this;
		}
		public Builder setDiseaseImport( DiseaseImport diseaseImport ){
			this.diseaseImport = diseaseImport;
			return this;
		}
		public Builder setRestrictions( Restrictions restrictions ){
			this.restrictions = restrictions;
			return this;
		}
		public Builder setMasks( Masks masks ){
			this.masks = masks;
			return this;
		}
		public Builder setTracing( Tracing tracing ){
			this.tracing = tracing;
			return this;
		}
		public Builder setVaccinations( Vaccinations vaccinations ){
			this.vaccinations = vaccinations;
			return this;
		}
		public Builder setChristmasModel( ChristmasModel christmasModel ){
			this.christmasModel = christmasModel;
			return this;
		}
		public Builder setEasterModel( EasterModel easterModel ){
			this.easterModel = easterModel;
			return this;
		}
		public Builder setWeatherModel( WeatherModel weatherModel ){
			this.weatherModel = weatherModel;
			return this;
		}
		public Builder setSnapshot( Snapshot snapshot ){
			this.snapshot = snapshot;
			return this;
		}
		public Builder setInfectionModel( Class<? extends InfectionModel> infectionModel ){
			this.infectionModel = infectionModel;
			return this;
		}
		public Builder setVaccinationModel( Class<? extends VaccinationModel> vaccinationModel ){
			this.vaccinationModel = vaccinationModel;
			return this;
		}
		public SnzBerlinProductionScenario createSnzBerlinProductionScenario(){
			return new SnzBerlinProductionScenario( sample, diseaseImport, restrictions, masks, tracing, vaccinations, christmasModel, easterModel, weatherModel, snapshot,
					infectionModel, importOffset, vaccinationModel, importFactorBeforeJune, importFactorAfterJune, imprtFctMult );
		}
		public Builder setImportOffset( int importOffset ){
			this.importOffset = importOffset;
			return this;
		}
		public Builder setImportFactor( double imprtFctMult ){
			this.imprtFctMult = imprtFctMult;
			return this;
		}
	}

	/**
	 * Path pointing to the input folder. Can be configured at runtime with EPISIM_INPUT variable.
	 */
	public static final Path INPUT = EpisimUtils.resolveInputPath("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input");

	private final double imprtFctMult ;
	private final int sample;
	private final int importOffset;
	private final DiseaseImport diseaseImport;
	private final Restrictions restrictions;
	private final Masks masks;
	private final Tracing tracing;
	private final Vaccinations vaccinations;
	private final ChristmasModel christmasModel;
	private final EasterModel easterModel;
	private final Snapshot snapshot;
	private final WeatherModel weatherModel;
	private final Class<? extends InfectionModel> infectionModel;
	private final Class<? extends VaccinationModel> vaccinationModel;
	private final double importFactorBeforeJune;
	private final double importFactorAfterJune;



	/**
	 * Empty constructor is needed for running scenario from command line.
	 */
	@SuppressWarnings("unused")
	private SnzBerlinProductionScenario() {
		this(25, DiseaseImport.yes, Restrictions.yes, Masks.yes, Tracing.yes, Vaccinations.yes, ChristmasModel.restrictive, EasterModel.yes, WeatherModel.midpoints_175_250,
				Snapshot.no, AgeDependentInfectionModelWithSeasonality.class, 0, VaccinationByAge.class,
				4., 0.5, 1. );
	}

	private SnzBerlinProductionScenario( int sample, DiseaseImport diseaseImport, Restrictions restrictions, Masks masks, Tracing tracing, Vaccinations vaccinations, ChristmasModel christmasModel, EasterModel easterModel, 
			WeatherModel weatherModel, Snapshot snapshot, Class<? extends InfectionModel> infectionModel, int importOffset, Class<? extends VaccinationModel> vaccinationModel,
					     double importFactorBeforeJune, double importFactorAfterJune, double imprtFctMult ) {
		this.sample = sample;
		this.diseaseImport = diseaseImport;
		this.restrictions = restrictions;
		this.masks = masks;
		this.tracing = tracing;
		this.vaccinations = vaccinations;
		this.christmasModel = christmasModel;
		this.easterModel = easterModel;
		this.weatherModel = weatherModel;
		this.snapshot = snapshot;
		this.infectionModel = infectionModel;
		this.importOffset = importOffset;
		this.vaccinationModel = vaccinationModel;
		this.importFactorBeforeJune = importFactorBeforeJune;
		this.importFactorAfterJune = importFactorAfterJune;
		this.imprtFctMult = imprtFctMult;
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
		bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);
		bind(InfectionModel.class).to(infectionModel).in(Singleton.class);
		bind(VaccinationModel.class).to(vaccinationModel).in(Singleton.class);
	}

	@Provides
	@Singleton
	public Config config() {

		if (this.sample != 25) throw new RuntimeException("Sample size not calibrated! Currently only 25% is calibrated. Comment this line out to continue.");

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		config.global().setRandomSeed(7564655870752979346L);

		config.vehicles().setVehiclesFile(INPUT.resolve("de_2020-vehicles.xml").toString());

		config.plans().setInputFile(inputForSample("be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_%dpt_split.xml.gz", sample));

//		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_wt_%dpt_split_wRestaurants.xml.gz", sample))
//				.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
//
//		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_sa_%dpt_split_wRestaurants.xml.gz", sample))
//				.addDays(DayOfWeek.SATURDAY);
//
//		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_so_%dpt_split_wRestaurants.xml.gz", sample))
//				.addDays(DayOfWeek.SUNDAY);
		
		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_wt_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_sa_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.SATURDAY);

		episimConfig.addInputEventsFile(inputForSample("be_2020-week_snz_episim_events_so_%dpt_split.xml.gz", sample))
				.addDays(DayOfWeek.SUNDAY);

		episimConfig.setCalibrationParameter(1.7E-5 * 0.8);
		episimConfig.setStartDate("2020-02-25");
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);
		episimConfig.setSampleSize(this.sample / 100.);
		episimConfig.setHospitalFactor(0.5);
		episimConfig.setProgressionConfig(AbstractSnzScenario2020.baseProgressionConfig(Transition.config()).build());

		if (this.snapshot != Snapshot.no) episimConfig.setStartFromSnapshot(INPUT.resolve("snapshots/" + snapshot + ".zip").toString());

		//inital infections and import
		episimConfig.setInitialInfections(Integer.MAX_VALUE);
		if (this.diseaseImport != DiseaseImport.no) {
			episimConfig.setInitialInfectionDistrict(null);
			Map<LocalDate, Integer> importMap = new HashMap<>();
			interpolateImport( importMap, imprtFctMult*importFactorBeforeJune, LocalDate.parse( "2020-02-24" ).plusDays( importOffset ),
					LocalDate.parse( "2020-03-09" ).plusDays( importOffset ), 0.9, 23.1 );
			interpolateImport( importMap, imprtFctMult*importFactorBeforeJune, LocalDate.parse( "2020-03-09" ).plusDays( importOffset ),
					LocalDate.parse( "2020-03-23" ).plusDays( importOffset ), 23.1, 3.9 );
			interpolateImport( importMap, imprtFctMult*importFactorBeforeJune, LocalDate.parse( "2020-03-23" ).plusDays( importOffset ),
					LocalDate.parse( "2020-04-13" ).plusDays( importOffset ), 3.9, 0.1 );
			if (this.diseaseImport == DiseaseImport.yes) {
				interpolateImport( importMap, imprtFctMult*importFactorAfterJune, LocalDate.parse( "2020-06-08" ).plusDays( importOffset ),
						LocalDate.parse( "2020-07-13" ).plusDays( importOffset ), 0.1, 2.7 );
				interpolateImport( importMap, imprtFctMult*importFactorAfterJune, LocalDate.parse( "2020-07-13" ).plusDays( importOffset ),
						LocalDate.parse( "2020-08-10" ).plusDays( importOffset ), 2.7, 17.9 );
				interpolateImport( importMap, imprtFctMult*importFactorAfterJune, LocalDate.parse( "2020-08-10" ).plusDays( importOffset ),
						LocalDate.parse( "2020-09-07" ).plusDays( importOffset ), 17.9, 6.1 );
				interpolateImport( importMap, imprtFctMult*importFactorAfterJune, LocalDate.parse( "2020-10-26" ).plusDays( importOffset ),
						LocalDate.parse( "2020-12-21" ).plusDays( importOffset ), 6.1, 1.1 );
			}
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
		SnzBerlinScenario25pct2020.BasePolicyBuilder basePolicyBuilder = new SnzBerlinScenario25pct2020.BasePolicyBuilder(episimConfig);
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
		FixedPolicy.ConfigBuilder builder = basePolicyBuilder.build();

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
				Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractions2(SnzBerlinProductionScenario.INPUT.resolve("berlinWeather_until20210608_tegel_until20210430.csv").toFile(),
						SnzBerlinProductionScenario.INPUT.resolve("berlinWeatherAvg2000-2020.csv").toFile(), 0.5, midpoint1, midpoint2, 5. );
				episimConfig.setLeisureOutdoorFraction(outdoorFractions);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else {
			episimConfig.setLeisureOutdoorFraction(Map.of(
					LocalDate.of(2020, 1, 1), 0.)
			);
		}

		//leisure & work factor
		double leisureFactor = 1.6;
		if (this.restrictions != Restrictions.no) {
			builder.apply("2020-10-15", "2020-12-14", (d, e) -> e.put("fraction", 1 - leisureFactor * (1 - (double) e.get("fraction"))), "leisure");
			
			double workVacFactor = 0.92;
			builder.apply("2020-04-03", "2020-04-17", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
			builder.apply("2020-06-26", "2020-08-07", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
			builder.apply("2020-10-09", "2020-10-23", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
			builder.apply("2020-12-18", "2021-01-01", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
//			builder.apply("2021-01-29", "2021-02-05", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
//			builder.apply("2021-03-26", "2021-04-09", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
//			builder.apply("2021-06-25", "2021-08-06", (d, e) -> e.put("fraction", workVacFactor * (double) e.get("fraction")), "work", "business");
		}
		
		
		
		//vacinations
		if (this.vaccinations.equals(Vaccinations.yes)) {
			VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
			vaccinationConfig.setEffectiveness(0.9);
			vaccinationConfig.setDaysBeforeFullEffect(28);
			// Based on https://experience.arcgis.com/experience/db557289b13c42e4ac33e46314457adc
			
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
			vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);
		}
		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());
		
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

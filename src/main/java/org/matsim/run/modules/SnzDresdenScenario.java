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
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.TracingConfigGroup.CapacityType;
import org.matsim.episim.model.*;
import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;

import javax.inject.Singleton;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scenario for Dresden using Senozon data.
 */
public final class SnzDresdenScenario extends AbstractModule {

	/**
	 * Path pointing to the input folder. Needs to be adapted or set using the EPISIM_INPUT environment variable.
	 */
	public static final Path INPUT = EpisimUtils.resolveInputPath("../shared-svn/projects/episim/matsim-files/snz/Dresden/episim-input");

	/**
	 * Empty constructor is needed for running scenario from command line.
	 */
	@SuppressWarnings("unused")
	public SnzDresdenScenario() {
	}

	@Override
	protected void configure() {
		bind(ContactModel.class).to(SymmetricContactModel.class).in(Singleton.class);
		bind(DiseaseStatusTransitionModel.class).to(AgeDependentDiseaseStatusTransitionModel.class).in(Singleton.class);
		bind(InfectionModel.class).to(AgeDependentInfectionModelWithSeasonality.class).in(Singleton.class);
		bind(VaccinationModel.class).to(VaccinationByAge.class).in(Singleton.class);
	}


	@Provides
	@Singleton
	public Config config() {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);
		episimConfig.setSampleSize(1);

		// Input files

		config.plans().setInputFile(INPUT.resolve("dresden_snz_entirePopulation_emptyPlans_withDistricts_100pt_split_noCoord.xml.gz").toString());

		episimConfig.addInputEventsFile(INPUT.resolve("dresden_snz_episim_events_wt_100pt_split.xml.gz").toString())
				.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		episimConfig.addInputEventsFile(INPUT.resolve("dresden_snz_episim_events_sa_100pt_split.xml.gz").toString())
				.addDays(DayOfWeek.SATURDAY);

		episimConfig.addInputEventsFile(INPUT.resolve("dresden_snz_episim_events_so_100pt_split.xml.gz").toString())
				.addDays(DayOfWeek.SUNDAY);


		// Calibration parameter

		episimConfig.setCalibrationParameter(1.7E-5 * 0.8); // TODO
		episimConfig.setStartDate("2020-02-25");


		// Progression config

		episimConfig.setProgressionConfig(AbstractSnzScenario2020.baseProgressionConfig(Transition.config()).build());


		// Initial infections and import

		episimConfig.setInitialInfections(Integer.MAX_VALUE);
		episimConfig.setInfections_pers_per_day(Map.of(LocalDate.EPOCH, 1));

		// Contact intensities

		int spaces = 20;
		episimConfig.getOrAddContainerParams("pt", "tr").setContactIntensity(10.0).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("work").setContactIntensity(1.47).setSpacesPerFacility(spaces);
		episimConfig.getOrAddContainerParams("leisure").setContactIntensity(9.24).setSpacesPerFacility(spaces).setSeasonal(true);
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


		// Tracing

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

		// Vaccination capacity

		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.getParams(VaccinationType.generic).setEffectiveness(0.9);
		vaccinationConfig.getParams(VaccinationType.generic).setDaysBeforeFullEffect(28);

		Map<LocalDate, Integer> vaccinations = new HashMap<>();

		int population = 4_800_000;

		vaccinations.put(LocalDate.parse("2020-01-01"), 0);
		vaccinations.put(LocalDate.parse("2020-12-27"), (int) (0.003 * population / 6));

		// Vaccination compliance by age
		Map<Integer, Double> vaccinationCompliance = new HashMap<>();
		vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);

		// Vaccinate everybody with age above 0
		vaccinationCompliance.put(0, 1d);

		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);


		// Policy and restrictions
		CreateRestrictionsFromCSV restrictions = new CreateRestrictionsFromCSV(episimConfig);
		restrictions.setInput(INPUT.resolve("DresdenSnzData_daily_until20210709.csv"));

		// restrictions.setExtrapolation(EpisimUtils.Extrapolation.linear); // TODO

		// Using the same base policy as berlin
		SnzBerlinScenario25pct2020.BasePolicyBuilder builder = new SnzBerlinScenario25pct2020.BasePolicyBuilder(episimConfig);
		builder.setActivityParticipation(restrictions);
		FixedPolicy.ConfigBuilder policy = builder.buildFixed();

		// Set compliance rate of 90% for cloth masks
		policy.restrict(LocalDate.parse("2020-04-01"), Restriction.ofMask(FaceMask.CLOTH, 0.9), "pt");


		// Testing rates

		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);
		testingConfigGroup.setStrategy(TestingConfigGroup.Strategy.ACTIVITIES);

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

		testingConfigGroup.getParams(TestType.RAPID_TEST).setFalseNegativeRate(0.3);
		testingConfigGroup.getParams(TestType.RAPID_TEST).setFalsePositiveRate(0.03);

		// Test 10% of persons doing these activities
		testingConfigGroup.getParams(TestType.RAPID_TEST).setTestingRate(0.1);

		// All households can get tested
		testingConfigGroup.setHouseholdCompliance(1.0);

		//LocalDate testingDate = LocalDate.parse("2021-04-19");

		episimConfig.setPolicy(FixedPolicy.class, policy.build());
		config.controler().setOutputDirectory("output-snz-dresden");

		return config;
	}
}

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
import org.matsim.episim.model.*;
import org.matsim.episim.model.vaccination.VaccinationByAge;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.vehicles.VehicleType;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract class and utility class for most recent Senozo scenarios.
 * <p>
 * This is a newer version of {@link AbstractSnzScenario2020} and {@link AbstractSnzScenario}.
 */
public abstract class SnzProductionScenario extends AbstractModule {

	/**
	 * Configures pre defined disease import.
	 */
	public static void configureDiseaseImport(EpisimConfigGroup episimConfig, DiseaseImport diseaseImport, int offset,
	                                          double factor, double importFactorBeforeJune, double importFactorAfterJune) {

		episimConfig.setInitialInfections(Integer.MAX_VALUE);
		episimConfig.setInitialInfectionDistrict(null);
		Map<LocalDate, Integer> importMap = new HashMap<>();
		interpolateImport(importMap, factor * importFactorBeforeJune, LocalDate.parse("2020-02-24").plusDays(offset),
				LocalDate.parse("2020-03-09").plusDays(offset), 0.9, 23.1);
		interpolateImport(importMap, factor * importFactorBeforeJune, LocalDate.parse("2020-03-09").plusDays(offset),
				LocalDate.parse("2020-03-23").plusDays(offset), 23.1, 3.9);
		interpolateImport(importMap, factor * importFactorBeforeJune, LocalDate.parse("2020-03-23").plusDays(offset),
				LocalDate.parse("2020-04-13").plusDays(offset), 3.9, 0.1);

		if (diseaseImport == DiseaseImport.yes) {
			interpolateImport(importMap, factor * importFactorAfterJune, LocalDate.parse("2020-06-08").plusDays(offset),
					LocalDate.parse("2020-07-13").plusDays(offset), 0.1, 2.7);
			interpolateImport(importMap, factor * importFactorAfterJune, LocalDate.parse("2020-07-13").plusDays(offset),
					LocalDate.parse("2020-08-10").plusDays(offset), 2.7, 17.9);
			interpolateImport(importMap, factor * importFactorAfterJune, LocalDate.parse("2020-08-10").plusDays(offset),
					LocalDate.parse("2020-09-07").plusDays(offset), 17.9, 6.1);
			interpolateImport(importMap, factor * importFactorAfterJune, LocalDate.parse("2020-10-26").plusDays(offset),
					LocalDate.parse("2020-12-21").plusDays(offset), 6.1, 1.1);
		}

		episimConfig.setInfections_pers_per_day(importMap);
	}

	/**
	 * Configure default contact intensities.
	 */
	public static void configureContactIntensities(EpisimConfigGroup episimConfig) {
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

	/**
	 * Configure default christmas model.
	 */
	public static void configureChristmasModel(ChristmasModel christmasModel, Map<LocalDate, DayOfWeek> inputDays, FixedPolicy.ConfigBuilder builder) {

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

			if (christmasModel == ChristmasModel.restrictive) {
				builder.restrict(LocalDate.parse("2020-12-24"), 1.0, act);
			}
			if (christmasModel == ChristmasModel.permissive) {
				builder.restrict(LocalDate.parse("2020-12-24"), 1.0, act);
				builder.restrict(LocalDate.parse("2020-12-31"), 1.0, act);
				builder.restrict(LocalDate.parse("2021-01-02"), fraction, act);
			}
		}

	}


	/**
	 * Configure easter model.
	 */
	protected static void configureEasterModel(Map<LocalDate, DayOfWeek> inputDays, FixedPolicy.ConfigBuilder builder) {
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


	/**
	 * Configure outdoor fractions for weather model.
	 */
	public static void configureWeather(EpisimConfigGroup episimConfig, WeatherModel weatherModel, File weather, File avgWeather) {
		if (weatherModel != WeatherModel.no) {
			double midpoint1 = 0.1 * Double.parseDouble(weatherModel.toString().split("_")[1]);
			double midpoint2 = 0.1 * Double.parseDouble(weatherModel.toString().split("_")[2]);
			try {
				Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractions2(weather, avgWeather, 0.5, midpoint1, midpoint2, 5.);
				episimConfig.setLeisureOutdoorFraction(outdoorFractions);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		} else {
			episimConfig.setLeisureOutdoorFraction(Map.of(
					LocalDate.of(2020, 1, 1), 0.)
			);
		}
	}

	/**
	 * Configure vaccination types and efficiencies, as well as capacities.
	 */
	public static void configureVaccines(VaccinationConfigGroup vaccinationConfig, int population) {

		//wildtype and alpha
		{
			double effectivnessMRNA = 0.7;
			double factorShowingSymptomsMRNA = 0.05 / (1 - effectivnessMRNA); //95% protection against symptoms
			double factorSeriouslySickMRNA = 0.02 / ((1 - effectivnessMRNA) * factorShowingSymptomsMRNA); //98% protection against severe disease
			int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot
			vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
					.setDaysBeforeFullEffect(fullEffectMRNA)
					.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
							.atDay(1, 0.0)
							.atFullEffect(effectivnessMRNA)
							.atDay(fullEffectMRNA + 5 * 365, 0.0) //10% reduction every 6 months (source: TC)
					)
					.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B117)
							.atDay(1, 0.0)
							.atFullEffect(effectivnessMRNA)
							.atDay(fullEffectMRNA + 5 * 365, 0.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
							.atDay(1, 1.0)
							.atFullEffect(factorShowingSymptomsMRNA)
							.atDay(fullEffectMRNA + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.B117)
							.atDay(1, 1.0)
							.atFullEffect(factorShowingSymptomsMRNA)
							.atDay(fullEffectMRNA + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
							.atDay(1, 1.0)
							.atFullEffect(factorSeriouslySickMRNA)
							.atDay(fullEffectMRNA + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.B117)
							.atDay(1, 1.0)
							.atFullEffect(factorSeriouslySickMRNA)
							.atDay(fullEffectMRNA + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
			;

			double effectivnessVector = 0.5;
			double factorShowingSymptomsVector = 0.25 / (1 - effectivnessVector); //75% protection against symptoms
			double factorSeriouslySickVector = 0.15 / ((1 - effectivnessVector) * factorShowingSymptomsVector); //85% protection against severe disease
			int fullEffectVector = 10 * 7; //second shot after 9 weeks, full effect one week after second shot

			vaccinationConfig.getOrAddParams(VaccinationType.vector)
					.setDaysBeforeFullEffect(fullEffectVector)
					.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
							.atDay(1, 0.0)
							.atFullEffect(effectivnessVector)
							.atDay(fullEffectVector + 5 * 365, 0.0) //10% reduction every 6 months (source: TC)
					)
					.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B117)
							.atDay(1, 0.0)
							.atFullEffect(effectivnessVector)
							.atDay(fullEffectVector + 5 * 365, 0.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
							.atDay(1, 1.0)
							.atFullEffect(factorShowingSymptomsVector)
							.atDay(fullEffectVector + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.B117)
							.atDay(1, 1.0)
							.atFullEffect(factorShowingSymptomsVector)
							.atDay(fullEffectVector + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
							.atDay(1, 1.0)
							.atFullEffect(factorSeriouslySickVector)
							.atDay(fullEffectVector + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.B117)
							.atDay(1, 1.0)
							.atFullEffect(factorSeriouslySickVector)
							.atDay(fullEffectVector + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					);
		}

		//delta
		{
			double effectivnessMRNA = 0.7;
			double factorShowingSymptomsMRNA =  0.12 / (1 - effectivnessMRNA);
			double factorSeriouslySickMRNA = 0.02 / ((1 - effectivnessMRNA) * factorShowingSymptomsMRNA);
			int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot
			vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
					.setDaysBeforeFullEffect(fullEffectMRNA)
					.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 0.0)
							.atDay(fullEffectMRNA-7, effectivnessMRNA/2.)
							.atFullEffect(effectivnessMRNA)
							.atDay(fullEffectMRNA + 5*365, 0.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 1.0)
							.atDay(fullEffectMRNA-7, 1.0 - ((1.0 - factorShowingSymptomsMRNA) / 2.))
							.atFullEffect(factorShowingSymptomsMRNA)
							.atDay(fullEffectMRNA + 5*365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 1.0)
							.atDay(fullEffectMRNA-7, 1.0 - ((1.0 - factorSeriouslySickMRNA) / 2.))
							.atFullEffect(factorSeriouslySickMRNA)
							.atDay(fullEffectMRNA + 5*365, 1.0) //10% reduction every 6 months (source: TC)
					)
					;

			double effectivnessVector = 0.7 * 0.5/0.7;
			double factorShowingSymptomsVector = 0.32 / (1 - effectivnessVector);
			double factorSeriouslySickVector = 0.15 / ((1 - effectivnessVector) * factorShowingSymptomsVector);
			int fullEffectVector = 10 * 7; //second shot after 9 weeks, full effect one week after second shot

			vaccinationConfig.getOrAddParams(VaccinationType.vector)
				.setDaysBeforeFullEffect(fullEffectVector)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 0.0)
						.atDay(fullEffectVector-7, effectivnessVector/2.)
						.atFullEffect(effectivnessVector)
						.atDay(fullEffectVector + 5*365, 0.0) //10% reduction every 6 months (source: TC)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atDay(fullEffectVector-7, 1.0 - ((1.0 - factorShowingSymptomsVector) / 2.))
						.atFullEffect(factorShowingSymptomsVector)
						.atDay(fullEffectVector + 5*365, 1.0) //10% reduction every 6 months (source: TC)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
						.atDay(1, 1.0)
						.atDay(fullEffectVector-7, 1.0 - ((1.0 - factorSeriouslySickVector) / 2.))
						.atFullEffect(factorSeriouslySickVector)
						.atDay(fullEffectVector + 5*365, 1.0) //10% reduction every 6 months (source: TC)
				)
				;

		}


		// Based on https://experience.arcgis.com/experience/db557289b13c42e4ac33e46314457adc

		Map<LocalDate, Map<VaccinationType, Double>> share = new HashMap<>();

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
		share.put(LocalDate.parse("2021-06-14"), Map.of(VaccinationType.mRNA, 0.80d, VaccinationType.vector, 0.20d));
		share.put(LocalDate.parse("2021-06-21"), Map.of(VaccinationType.mRNA, 0.87d, VaccinationType.vector, 0.13d));
		share.put(LocalDate.parse("2021-06-28"), Map.of(VaccinationType.mRNA, 0.91d, VaccinationType.vector, 0.09d));
		share.put(LocalDate.parse("2021-07-05"), Map.of(VaccinationType.mRNA, 0.91d, VaccinationType.vector, 0.09d));
		share.put(LocalDate.parse("2021-07-12"), Map.of(VaccinationType.mRNA, 0.87d, VaccinationType.vector, 0.13d));
		share.put(LocalDate.parse("2021-07-19"), Map.of(VaccinationType.mRNA, 0.87d, VaccinationType.vector, 0.13d));
		share.put(LocalDate.parse("2021-07-26"), Map.of(VaccinationType.mRNA, 0.86d, VaccinationType.vector, 0.14d));
		share.put(LocalDate.parse("2021-08-02"), Map.of(VaccinationType.mRNA, 0.84d, VaccinationType.vector, 0.16d));
		share.put(LocalDate.parse("2021-08-09"), Map.of(VaccinationType.mRNA, 0.84d, VaccinationType.vector, 0.16d));
		share.put(LocalDate.parse("2021-08-16"), Map.of(VaccinationType.mRNA, 0.84d, VaccinationType.vector, 0.16d));
		share.put(LocalDate.parse("2021-08-23"), Map.of(VaccinationType.mRNA, 0.85d, VaccinationType.vector, 0.15d));
		share.put(LocalDate.parse("2021-08-30"), Map.of(VaccinationType.mRNA, 0.86d, VaccinationType.vector, 0.14d));
		share.put(LocalDate.parse("2021-09-06"), Map.of(VaccinationType.mRNA, 0.85d, VaccinationType.vector, 0.15d));
		share.put(LocalDate.parse("2021-09-13"), Map.of(VaccinationType.mRNA, 0.84d, VaccinationType.vector, 0.16d));
		share.put(LocalDate.parse("2021-09-20"), Map.of(VaccinationType.mRNA, 0.84d, VaccinationType.vector, 0.16d));
		share.put(LocalDate.parse("2021-09-27"), Map.of(VaccinationType.mRNA, 0.86d, VaccinationType.vector, 0.14d));
		share.put(LocalDate.parse("2021-10-04"), Map.of(VaccinationType.mRNA, 0.87d, VaccinationType.vector, 0.13d));
		share.put(LocalDate.parse("2021-10-11"), Map.of(VaccinationType.mRNA, 0.89d, VaccinationType.vector, 0.11d));
		share.put(LocalDate.parse("2021-10-18"), Map.of(VaccinationType.mRNA, 0.89d, VaccinationType.vector, 0.11d));


		vaccinationConfig.setVaccinationShare(share);


		Map<LocalDate, Integer> vaccinations = new HashMap<>();

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

	public static void interpolateImport(Map<LocalDate, Integer> importMap, double importFactor, LocalDate start, LocalDate end, double a, double b) {
		int days = end.getDayOfYear() - start.getDayOfYear();
		for (int i = 1; i <= days; i++) {
			double fraction = (double) i / days;
			importMap.put(start.plusDays(i), (int) Math.round(importFactor * (a + fraction * (b - a))));
		}
	}

	@Provides
	@Singleton
	public Scenario scenario(Config config) {

		// guice will use no args constructor by default, we check if this config was initialized
		// this is only the case when no explicit binding are required
		if (config.getModules().size() == 0)
			throw new IllegalArgumentException("Please provide a config module or binding.");

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);

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

	public enum DiseaseImport {yes, onlySpring, no}

	public enum Restrictions {yes, no, onlyEdu, allExceptSchoolsAndDayCare, allExceptUniversities, allExceptEdu}

	public enum Masks {yes, no}

	public enum Tracing {yes, no}

	public enum Vaccinations {yes, no}

	public enum ChristmasModel {no, restrictive, permissive}

	public enum WeatherModel {no, midpoints_175_175, midpoints_175_250, midpoints_200_250, midpoints_175_200, midpoints_200_200}

	public enum AdjustRestrictions {yes, no}

	public enum EasterModel {yes, no}

	public enum LocationBasedRestrictions {yes, no}

	/**
	 * Abstract builder with default config options.
	 */
	public abstract static class Builder<T extends SnzProductionScenario> {
		int importOffset = 0;
		int sample = 25;
		DiseaseImport diseaseImport = DiseaseImport.yes;
		Restrictions restrictions = Restrictions.yes;
		AdjustRestrictions adjustRestrictions = AdjustRestrictions.no;
		Masks masks = Masks.yes;
		Tracing tracing = Tracing.yes;
		Vaccinations vaccinations = Vaccinations.yes;
		ChristmasModel christmasModel = ChristmasModel.restrictive;
		EasterModel easterModel = EasterModel.no;
		WeatherModel weatherModel = WeatherModel.midpoints_175_250;
		EpisimConfigGroup.ActivityHandling activityHandling = EpisimConfigGroup.ActivityHandling.startOfDay;
		Class<? extends InfectionModel> infectionModel = AgeAndProgressionDependentInfectionModelWithSeasonality.class;
		Class<? extends VaccinationModel> vaccinationModel = VaccinationByAge.class;

		double imprtFctMult = 1.;
		double importFactorBeforeJune = 4.;
		double importFactorAfterJune = 0.5;

		LocationBasedRestrictions locationBasedRestrictions = LocationBasedRestrictions.no;

		/**
		 * Build the scenario module.
		 */
		public abstract T build();

		/**
		 * Use {@link #build()}. This method is only here to avoid changes of old batch runs for now.
		 */
		@Deprecated
		public T createSnzBerlinProductionScenario() {
			return build();
		}

		public Builder<T> setImportFactorBeforeJune(double importFactorBeforeJune) {
			this.importFactorBeforeJune = importFactorBeforeJune;
			return this;
		}

		public Builder<T> setImportFactorAfterJune(double importFactorAfterJune) {
			this.importFactorAfterJune = importFactorAfterJune;
			return this;
		}

		public Builder<T> setSample(int sample) {
			this.sample = sample;
			return this;
		}

		public Builder<T> setDiseaseImport(DiseaseImport diseaseImport) {
			this.diseaseImport = diseaseImport;
			return this;
		}

		public Builder<T> setRestrictions(Restrictions restrictions) {
			this.restrictions = restrictions;
			return this;
		}

		public Builder<T> setAdjustRestrictions(AdjustRestrictions adjustRestrictions) {
			this.adjustRestrictions = adjustRestrictions;
			return this;
		}

		public Builder<T> setMasks(Masks masks) {
			this.masks = masks;
			return this;
		}

		public Builder<T> setTracing(Tracing tracing) {
			this.tracing = tracing;
			return this;
		}

		public Builder<T> setVaccinations(Vaccinations vaccinations) {
			this.vaccinations = vaccinations;
			return this;
		}

		public Builder<T> setChristmasModel(ChristmasModel christmasModel) {
			this.christmasModel = christmasModel;
			return this;
		}

		public Builder<T> setEasterModel(EasterModel easterModel) {
			this.easterModel = easterModel;
			return this;
		}

		public Builder<T> setWeatherModel(WeatherModel weatherModel) {
			this.weatherModel = weatherModel;
			return this;
		}

		public Builder<T> setInfectionModel(Class<? extends InfectionModel> infectionModel) {
			this.infectionModel = infectionModel;
			return this;
		}

		public Builder<T> setVaccinationModel(Class<? extends VaccinationModel> vaccinationModel) {
			this.vaccinationModel = vaccinationModel;
			return this;
		}

		public Builder<T> setActivityHandling(EpisimConfigGroup.ActivityHandling activityHandling) {
			this.activityHandling = activityHandling;
			return this;
		}

		public Builder<T> setImportOffset(int importOffset) {
			this.importOffset = importOffset;
			return this;
		}

		public Builder<T> setImportFactor(double imprtFctMult) {
			this.imprtFctMult = imprtFctMult;
			return this;
		}
	}

}

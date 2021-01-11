package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.apache.commons.csv.CSVFormat;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.TracingConfigGroup.CapacityType;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Interventions for symmetric Berlin week model with different contact models
 */
public class SMBatch implements BatchRun<SMBatch.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder().setSnapshot(SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder().setSnapshot(
				SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		double theta = 0;
		if (params.leisureFactor > 1.5) theta = 0.8;
		else theta = 0.75;
		
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * theta);
		episimConfig.setStartDate("2020-02-25");
//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		episimConfig.setSnapshotInterval(120);
		episimConfig.setHospitalFactor(0.5);


		Map<LocalDate, Integer> importMap = new HashMap<>();
		int importOffset = 0;
//		importMap.put(episimConfig.getStartDate(), Math.max(1, (int) Math.round(0.9 * 1)));
		importMap = SnzBerlinProductionScenario.interpolateImport(importMap, 4., LocalDate.parse("2020-02-24").plusDays(importOffset),
				LocalDate.parse("2020-03-09").plusDays(importOffset), 0.9, 23.1);
		importMap = SnzBerlinProductionScenario.interpolateImport(importMap, 4., LocalDate.parse("2020-03-09").plusDays(importOffset),
				LocalDate.parse("2020-03-23").plusDays(importOffset), 23.1, 3.9);
		importMap = SnzBerlinProductionScenario.interpolateImport(importMap, 4., LocalDate.parse("2020-03-23").plusDays(importOffset),
				LocalDate.parse("2020-04-13").plusDays(importOffset), 3.9, 0.1);
		double importFactorAfterJune = 0.5;
		if (importFactorAfterJune == 0.) {
			importMap.put(LocalDate.parse("2020-06-08"), 1);
		} else {
			importMap = SnzBerlinProductionScenario.interpolateImport(importMap, importFactorAfterJune, LocalDate.parse("2020-06-08").plusDays(importOffset),
					LocalDate.parse("2020-07-13").plusDays(importOffset), 0.1, 2.7);
			importMap = SnzBerlinProductionScenario.interpolateImport(importMap, importFactorAfterJune, LocalDate.parse("2020-07-13").plusDays(importOffset),
					LocalDate.parse("2020-08-10").plusDays(importOffset), 2.7, 17.9);
			importMap = SnzBerlinProductionScenario.interpolateImport(importMap, importFactorAfterJune, LocalDate.parse("2020-08-10").plusDays(importOffset),
					LocalDate.parse("2020-09-07").plusDays(importOffset), 17.9, 6.1);
			importMap = SnzBerlinProductionScenario.interpolateImport(importMap, importFactorAfterJune, LocalDate.parse("2020-10-26").plusDays(importOffset),
					LocalDate.parse("2020-12-21").plusDays(importOffset), 6.1, 1.1);
		}
		episimConfig.setInfections_pers_per_day(importMap);

		try {
//			double weatherMidPointSpring = Double.parseDouble(params.weatherMidPoints.split("_")[0]);
//			double weatherMidPointFall = Double.parseDouble(params.weatherMidPoints.split("_")[1]);
			Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractions2(SnzBerlinProductionScenario.INPUT.resolve("berlinWeather.csv").toFile(), SnzBerlinProductionScenario.INPUT.resolve("berlinWeatherAvg2000-2020.csv").toFile(), 0.5, 17.5, 25.0,
					(double) 5.);
			episimConfig.setLeisureOutdoorFraction(outdoorFractions);
		} catch (IOException e) {
			e.printStackTrace();
		}

//		if (params.childSusInf.equals("0")) {
//			episimConfig.setAgeInfectivity(Map.of(
//					24, 0.,
//					25, 1d
//			));
//			episimConfig.setAgeSusceptibility(Map.of(
//					24, 0.,
//					25, 1d
//			));
//		}
//		if (params.childSusInf.equals("linear")) {
//			episimConfig.setAgeInfectivity(Map.of(
//					0, 0.,
//					20, 1d
//			));
//			episimConfig.setAgeSusceptibility(Map.of(
//					0, 0.7,
//					20, 1d
//			));
//		}

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		//leisure factor
		builder.apply(params.leisureFactorDate, "2021-12-31", (d, e) -> e.put("fraction", 1 - params.leisureFactor * (1 - (double) e.get("fraction"))), "leisure");

		//christmas model
		{
			Map<LocalDate, DayOfWeek> christmasInputDays = new HashMap<>();

			christmasInputDays.put(LocalDate.parse("2020-12-21"), DayOfWeek.SATURDAY);
			christmasInputDays.put(LocalDate.parse("2020-12-22"), DayOfWeek.SATURDAY);
			christmasInputDays.put(LocalDate.parse("2020-12-23"), DayOfWeek.SATURDAY);
			christmasInputDays.put(LocalDate.parse("2020-12-24"), DayOfWeek.SUNDAY);
			christmasInputDays.put(LocalDate.parse("2020-12-25"), DayOfWeek.SUNDAY);
			christmasInputDays.put(LocalDate.parse("2020-12-26"), DayOfWeek.SUNDAY);

			christmasInputDays.put(LocalDate.parse("2020-12-28"), DayOfWeek.SATURDAY);
			christmasInputDays.put(LocalDate.parse("2020-12-29"), DayOfWeek.SATURDAY);
			christmasInputDays.put(LocalDate.parse("2020-12-30"), DayOfWeek.SATURDAY);
			christmasInputDays.put(LocalDate.parse("2020-12-31"), DayOfWeek.SUNDAY);
			christmasInputDays.put(LocalDate.parse("2021-01-01"), DayOfWeek.SUNDAY);

			episimConfig.setInputDays(christmasInputDays);

			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				double fraction = 0.5925;
				if (act.contains("leisure")) fraction = 1 - params.leisureFactor * (1 - fraction);

				if (params.christmasModel.equals("restrictive")) {
					builder.restrict("2020-12-24", 1.0, act);
//					builder.restrict("2020-12-27", fraction, act);
				}
				if (params.christmasModel.equals("permissive")) {
					builder.restrict("2020-12-24", 1.0, act);
//					builder.restrict("2020-12-27", fraction, act);
					builder.restrict("2020-12-31", 1.0, act);
					builder.restrict("2021-01-02", fraction, act);
				}
			}
		}
		
		//school reopening
		if (params.schools.equals("open")) builder.restrict("2021-01-25", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
		if (params.schools.equals("50%&masks")) {
			builder.restrict(LocalDate.parse("2021-01-25"), Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.9 * 0.9, FaceMask.SURGICAL, 0.9 * 0.1)), "educ_primary", "educ_secondary");
			builder.restrict("2021-01-25", .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict("2021-02-07", .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
			builder.restrict("2021-04-11", .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		}


//		Map<LocalDate, Integer> vacMap = EpisimUtils.readCSV(Path.of("germanyVaccinations.csv"),
//				CSVFormat.DEFAULT, "date", "nVaccinated").entrySet().stream()
//				.collect(Collectors.toMap(Map.Entry::getKey,
//						e -> (int) ((e.getValue() * episimConfig.getSampleSize()) / (81_100_000 / 4_800_000)))
//				);
//
//		System.out.println(vacMap);
		// TODO: check vac map and put into model
		
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setEffectiveness(0.9);
		vaccinationConfig.setDaysBeforeFullEffect(28);
		// Based on https://experience.arcgis.com/experience/db557289b13c42e4ac33e46314457adc
		// 0.25 for 25% sample, 4/3 because model is bigger than just Berlin
		int base = (int) (0.25 * 2000 * 4./3.);
		vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
				episimConfig.getStartDate(), 0,
				LocalDate.parse("2020-12-27"), base
				,LocalDate.parse("2021-01-18"), base + (int) ((params.vaccinationFactor * base - base) * 1./7.)
				,LocalDate.parse("2021-01-20"), base + (int) ((params.vaccinationFactor * base - base) * 2./7.)
				,LocalDate.parse("2021-01-22"), base + (int) ((params.vaccinationFactor * base - base) * 3./7.)
				,LocalDate.parse("2021-01-24"), base + (int) ((params.vaccinationFactor * base - base) * 4./7.)
				,LocalDate.parse("2021-01-26"), base + (int) ((params.vaccinationFactor * base - base) * 5./7.)
				,LocalDate.parse("2021-01-28"), base + (int) ((params.vaccinationFactor * base - base) * 6./7.)
				,LocalDate.parse("2021-01-30"), base + (int) ((params.vaccinationFactor * base - base))
				));
				
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		tracingConfig.setCapacityType(CapacityType.PER_PERSON);

		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), (int) (params.tracingCapacity * 0.2),
				LocalDate.of(2020, 6, 15), params.tracingCapacity
		));

		if (!params.newVariantDate.equals("never")) {
			Map<LocalDate, Integer> infPerDayVariant = new HashMap<>();
			infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayVariant.put(LocalDate.parse(params.newVariantDate), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayVariant);
		}

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(1)
		public long seed;

//		@Parameter({0.75, 0.8})
//		double theta;

		@Parameter({1.3, 1.6})
		double leisureFactor;

		@StringParameter({"2020-09-15", "2020-10-15"})
		String leisureFactorDate;

//		//@StringParameter({"0", "current", "linear"})
//		@StringParameter({"current"})
//		public String childSusInf;

		@IntParameter({200, 400})
		int tracingCapacity;

		@IntParameter({1, 2, 10})
		int vaccinationFactor;
		
		@StringParameter({"no", "restrictive", "permissive"})
		public String christmasModel;
		
		@StringParameter({"closed", "open", "50%&masks"})
		public String schools;

		@StringParameter({"2020-12-15", "2020-11-15", "2020-10-15", "2020-09-15"})
		String newVariantDate;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, SMBatch.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(330),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


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
	
//	@Override
//	public int getOffset() {
//		return 600;
//	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder().setSnapshot(
				SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
		Config config = module.config();
//		config.global().setRandomSeed(params.seed);

		config.global().setRandomSeed(7564655870752979346L);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		episimConfig.setSnapshotInterval(120);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

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

				if (params.christmasModel.equals("restrictive")) {
					builder.restrict("2020-12-24", 1.0, act);
				}
				if (params.christmasModel.equals("permissive")) {
					builder.restrict("2020-12-24", 1.0, act);
					builder.restrict("2020-12-31", 1.0, act);
					builder.restrict("2021-01-02", fraction, act);
				}
			}
		}
		
		//extrapolate restrictions after 17.01.
		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			if (params.extrapolateRestrictions.contains("yes")) {
				builder.restrict("2021-01-17", 0.72, act);
				builder.restrict("2021-01-24", 0.76, act);
				builder.restrict("2021-01-31", 0.8, act);
				if (params.extrapolateRestrictions.equals("yes")) {
					builder.restrict("2021-02-07", 0.84, act);
					builder.restrict("2021-02-14", 0.88, act);
					builder.restrict("2021-02-21", 0.92, act);
					builder.restrict("2021-02-28", 0.96, act);
					builder.restrict("2021-03-07", 1., act);
				}
			}
		}
		
		//leisure factor
		builder.apply("2020-10-15", "2021-12-31", (d, e) -> e.put("fraction", 1 - params.leisureFactor * (1 - (double) e.get("fraction"))), "leisure");
		
		//curfew
		if (params.curfew.equals("18-5")) builder.restrict("2021-01-25", Restriction.ofClosingHours(18, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
		if (params.curfew.equals("19-5")) builder.restrict("2021-01-25", Restriction.ofClosingHours(19, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
		if (params.curfew.equals("20-5")) builder.restrict("2021-01-25", Restriction.ofClosingHours(20, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
		if (params.curfew.equals("21-5")) builder.restrict("2021-01-25", Restriction.ofClosingHours(21, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
		if (params.curfew.equals("22-5")) builder.restrict("2021-01-25", Restriction.ofClosingHours(22, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
		
		//school reopening
//		if (params.schools.equals("open")) builder.restrict("2021-02-15", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
		if (params.schools.equals("50%&masks")) {
			builder.restrict(LocalDate.parse("2021-02-15"), Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.9 * 0.9, FaceMask.SURGICAL, 0.9 * 0.1)), "educ_primary", "educ_secondary");
			builder.restrict("2021-02-15", .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict("2021-04-11", .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		}
		if (params.schools.equals("closed")) builder.clearAfter("2021-02-14", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");

		//masks at work
		if (params.ffpAtWork.equals("yes")) builder.restrict("2021-01-25", Restriction.ofMask(FaceMask.N95, 0.9), "work");
		
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
		// 4/3 because model is bigger than just Berlin
		int base = (int) (2000 * 4./3.);
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

//		@GenerateSeeds(2)
//		public long seed;

		@Parameter({1.6})
		double leisureFactor;

		@IntParameter({1, 5})
		int vaccinationFactor;
		
		@StringParameter({"permissive"})
//		@StringParameter({"restrictive", "permissive"})
		public String christmasModel;
		
		@StringParameter({"closed", "open", "50%&masks"})
		public String schools;
		
		@StringParameter({"no", "yes"})
		public String ffpAtWork;

//		@StringParameter({"2020-12-15", "2020-11-15", "2020-10-15", "2020-09-15"})
		@StringParameter({"2020-10-15", "2020-09-15"})
		String newVariantDate;
		
		@StringParameter({"no", "yes", "yesUntil80"})
		String extrapolateRestrictions;
		
//		@StringParameter({"no", "18-5", "19-5", "20-5", "21-5", "22-5"})
		@StringParameter({"no", "18-5", "20-5", "21-5", "22-5"})
		String curfew;

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


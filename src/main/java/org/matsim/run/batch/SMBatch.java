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
//		return 400;
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
		
		//christmas factor
		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			builder.apply("2020-12-19", "2021-01-02", (d, e) -> e.put("fraction", Math.min(1., 1 - params.christmasFactor * (1 - (double) e.get("fraction")))), act);
		}
		
		//extrapolate restrictions after 17.01.
		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			if (params.extrapolateRestrictions.contains("yes")) {
				builder.restrict("2021-01-24", 0.72, act);
				builder.restrict("2021-01-31", 0.76, act);
				builder.restrict("2021-02-07", 0.8, act);
				if (params.extrapolateRestrictions.equals("yes")) {
					builder.restrict("2021-02-14", 0.84, act);
					builder.restrict("2021-02-21", 0.88, act);
					builder.restrict("2021-02-28", 0.92, act);
					builder.restrict("2021-03-07", 0.96, act);
					builder.restrict("2021-03-10", 1., act);
				}
			}
		}
		
		//leisure factor
		builder.apply("2020-10-15", "2021-12-31", (d, e) -> e.put("fraction", 1 - params.leisureFactor * (1 - (double) e.get("fraction"))), "leisure");
		
		String restrictionDate = "2021-02-15";
		//curfew
		if (params.furtherMeasures.equals("curfew_18-5")) builder.restrict(restrictionDate, Restriction.ofClosingHours(18, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
		if (params.furtherMeasures.equals("curfew_19-5")) builder.restrict(restrictionDate, Restriction.ofClosingHours(19, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
		if (params.furtherMeasures.equals("curfew_20-5")) builder.restrict(restrictionDate, Restriction.ofClosingHours(20, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
		if (params.furtherMeasures.equals("curfew_21-5")) builder.restrict(restrictionDate, Restriction.ofClosingHours(21, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
		if (params.furtherMeasures.equals("curfew_22-5")) builder.restrict(restrictionDate, Restriction.ofClosingHours(22, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
		
		//schools
		if (params.furtherMeasures.equals("50%&masksAtSchool")) {
			builder.restrict(LocalDate.parse(restrictionDate), Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.9 * 0.9, FaceMask.SURGICAL, 0.9 * 0.1)), "educ_primary", "educ_secondary");
			builder.restrict(restrictionDate, .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict("2021-04-11", .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		}
		if (!params.furtherMeasures.equals("50%&masksAtSchool") && !params.furtherMeasures.equals("openSchools")) builder.clearAfter( "2021-02-14", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");

		//masks at work
		if (params.furtherMeasures.equals("ffpAtWork")) builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.9), "work");
		
		//closing PT
		if (params.furtherMeasures.equals("closePublicTransport")) builder.restrict(restrictionDate, 0., "pt");
		
		//open Universities
		if (params.furtherMeasures.equals("openUniversities")) builder.restrict(restrictionDate, 1., "educ_higher");
		
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
		int base = (int) (3000 * 4./3.);
		vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
				episimConfig.getStartDate(), 0,
				LocalDate.parse("2020-12-27"), (int) (2000 * 4./3.)
				,LocalDate.parse("2021-01-25"), base
				,LocalDate.parse("2021-02-01"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 1./7.)
				,LocalDate.parse("2021-02-03"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 2./7.)
				,LocalDate.parse("2021-02-05"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 3./7.)
				,LocalDate.parse("2021-02-07"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 4./7.)
				,LocalDate.parse("2021-02-09"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 5./7.)
				,LocalDate.parse("2021-02-10"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 6./7.)
				,LocalDate.parse("2021-02-12"), base + (int) ((4./3. * params.dailyInitialVaccinations - base))
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
		
		@Parameter({1., 0.5, 0.})
		double christmasFactor;

		@IntParameter({3000, 10000})
		int dailyInitialVaccinations;
		
		@StringParameter({"permissive"})
//		@StringParameter({"restrictive", "permissive"})
		public String christmasModel;
		
		@StringParameter({"no", "ffpAtWork", "openSchools", "50%&masksAtSchool", "curfew_18-5", "curfew_20-5", "curfew_22-5", "closePublicTransport", "openUniversities"})
		public String furtherMeasures;

//		@StringParameter({"2020-12-15", "2020-11-15", "2020-10-15", "2020-09-15"})
		@StringParameter({"2020-11-15", "2020-12-15"})
		String newVariantDate;
		
		@StringParameter({"no", "yes", "yesUntil80"})
		String extrapolateRestrictions;
		
		@Parameter({1.35})
		double newVariantInfectiousness;
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


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
		config.global().setRandomSeed( Long.parseLong( params.seed ) );

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		episimConfig.setSnapshotInterval(120);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
//		//extrapolate restrictions after 17.01.
//		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
//			if (act.contains("educ")) continue;
//			if (params.extrapolateRestrictions.equals("no100%")) {
//				builder.restrict("2021-02-15", 1., act);
//			}
//			if (params.extrapolateRestrictions.contains("yes")) {
//				builder.restrict("2021-02-07", 0.72, act);
//				builder.restrict("2021-02-14", 0.76, act);
//				builder.restrict("2021-02-21", 0.8, act);
//				if (params.extrapolateRestrictions.equals("yes")) {
//					builder.restrict("2021-02-28", 0.84, act);
//					builder.restrict("2021-03-07", 0.88, act);
//					builder.restrict("2021-03-14", 0.92, act);
//					builder.restrict("2021-03-21", 0.96, act);
//					builder.restrict("2021-03-28", 1., act);
//				}
//			}
//		}
//
//		String restrictionDate = "2021-02-15";
//		//curfew
//		if (params.curfew.equals("18-5")) builder.restrict("2021-03-15", Restriction.ofClosingHours(18, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
//		if (params.curfew.equals("19-5")) builder.restrict("2021-03-15", Restriction.ofClosingHours(19, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
//		if (params.curfew.equals("20-5")) builder.restrict("2021-03-15", Restriction.ofClosingHours(20, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
//		if (params.curfew.equals("21-5")) builder.restrict("2021-03-15", Restriction.ofClosingHours(21, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
//		if (params.curfew.equals("22-5")) builder.restrict("2021-03-15", Restriction.ofClosingHours(22, 5), "leisure", "shop_daily", "shop_other", "visit", "errands");
//
//		//schools
//		if (params.schools.equals("50%&masks")) {
//			builder.restrict(LocalDate.parse(restrictionDate), Restriction.ofMask(Map.of(FaceMask.N95, 0.9)), "educ_primary", "educ_secondary");
//			builder.restrict(restrictionDate, .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
//			builder.restrict("2021-04-11", .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
//		}
//		if (params.schools.equals("open&masks")) {
//			builder.restrict(LocalDate.parse(restrictionDate), Restriction.ofMask(Map.of(FaceMask.N95, 0.9)), "educ_primary", "educ_secondary");
//		}
//		if (params.schools.equals("50%open")) {
//			builder.restrict(restrictionDate, .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
//			builder.restrict("2021-04-11", .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
//		}
//		if (params.schools.equals("closed")) builder.clearAfter( "2021-02-14", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
//
//		//masks at work
//		if (params.work.equals("ffp")) builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.9), "work");
//
////		Map<LocalDate, Integer> vacMap = EpisimUtils.readCSV(Path.of("germanyVaccinations.csv"),
////				CSVFormat.DEFAULT, "date", "nVaccinated").entrySet().stream()
////				.collect(Collectors.toMap(Map.Entry::getKey,
////						e -> (int) ((e.getValue() * episimConfig.getSampleSize()) / (81_100_000 / 4_800_000)))
////				);
////
////		System.out.println(vacMap);
//		// TODO: check vac map and put into model
//
//		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
//		vaccinationConfig.setEffectiveness(0.9);
//		vaccinationConfig.setDaysBeforeFullEffect(28);
//		// Based on https://experience.arcgis.com/experience/db557289b13c42e4ac33e46314457adc
//		// 4/3 because model is bigger than just Berlin
//		int base = (int) (3000 * 4./3.);
//		vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
//				episimConfig.getStartDate(), 0,
//				LocalDate.parse("2020-12-27"), (int) (2000 * 4./3.)
//				,LocalDate.parse("2021-01-25"), base
//				,LocalDate.parse("2021-02-01"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 1./7.)
//				,LocalDate.parse("2021-02-03"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 2./7.)
//				,LocalDate.parse("2021-02-05"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 3./7.)
//				,LocalDate.parse("2021-02-07"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 4./7.)
//				,LocalDate.parse("2021-02-09"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 5./7.)
//				,LocalDate.parse("2021-02-10"), base + (int) ((4./3. * params.dailyInitialVaccinations - base) * 6./7.)
//				,LocalDate.parse("2021-02-12"), base + (int) ((4./3. * params.dailyInitialVaccinations - base))
//				));

		if (!params.newVariantDate.equals("never")) {
			Map<LocalDate, Integer> infPerDayVariant = new HashMap<>();
			infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayVariant.put(LocalDate.parse(params.newVariantDate), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayVariant);
		}

			episimConfig.setPolicy(FixedPolicy.class, builder.build());
		
//		VirusStrain.B117.infectiousness = params.newVariantInfectiousness;
		
		return config;
	}

	public static final class Params {
		@StringParameter( {"4711","7564655870752979346"} ) String seed;

//		@IntParameter({3000})
//		int dailyInitialVaccinations;
		
//		@StringParameter({"closed"})
//		public String schools;
		
//		@StringParameter({"no",})
//		public String work;
		
//		@StringParameter({"no"})
//		public String curfew;

		@StringParameter({"2020-12-01"})
		String newVariantDate;

//		@StringParameter({"no"})
//		String extrapolateRestrictions;
	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, SMBatch.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString(4),
				RunParallel.OPTION_ITERATIONS, Integer.toString(330),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


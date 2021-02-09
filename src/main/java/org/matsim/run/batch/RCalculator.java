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
 * Interventions for RCalculator
 */
public class RCalculator implements BatchRun<RCalculator.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder().setSnapshot(SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "rCalc");
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

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
//		episimConfig.setStartFromSnapshot("/Users/sebastianmuller/git/matsim-episim/output/leisureFactor_1.6-vaccinationFactor_1-christmasModel_permissive-schools_closed-ffpAtWork_no-newVariantDate_2020-12-15-extrapolateRestrictions_yesUntil80-curfew_no/episim-snapshot-060-2020-04-24.zip");

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

		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setEffectiveness(0.9);
		vaccinationConfig.setDaysBeforeFullEffect(28);
		
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		
		LocalDate interventionDate = LocalDate.parse(params.interventionDate);
		builder.clearAfter(interventionDate.minusDays(1).toString());
		switch (params.intervention) {
		case "none":
			break;
		case "l_50":
			builder.restrict(interventionDate, 0.5, "leisure");
			break;
		case "l_0":
			builder.restrict(interventionDate, 0.0, "leisure");
			break;
		case "l_c_18-5":
			builder.restrict(interventionDate, Restriction.ofClosingHours(18, 5), "leisure");
			break;
		case "l_c_19-5":
			builder.restrict(interventionDate, Restriction.ofClosingHours(19, 5), "leisure");
			break;
		case "l_c_20-5":
			builder.restrict(interventionDate, Restriction.ofClosingHours(20, 5), "leisure");
			break;
		case "l_c_21-5":
			builder.restrict(interventionDate, Restriction.ofClosingHours(21, 5), "leisure");
			break;
		case "l_c_22-5":
			builder.restrict(interventionDate, Restriction.ofClosingHours(22, 5), "leisure");
			break;
		case "w_50":
			builder.restrict(interventionDate, 0.5, "work", "business");
			break;
		case "w_0":
			builder.restrict(interventionDate, 0.0, "work", "business");
			break;
		case "w_ffp":
			builder.restrict(interventionDate, Restriction.ofMask(FaceMask.N95, 0.9), "work", "business");
			break;
		case "s_50":
			builder.restrict(interventionDate, 0.5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			break;
		case "s_100":
			builder.restrict(interventionDate, 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			break;
		case "s_100_masks":
			builder.restrict(interventionDate, 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict(interventionDate, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.9 * 0.9, FaceMask.SURGICAL, 0.9 * 0.1)), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			break;
		case "s_50_masks":
			builder.restrict(interventionDate, 0.5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict(interventionDate, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.9 * 0.9, FaceMask.SURGICAL, 0.9 * 0.1)), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			break;
		case "e_50":
			builder.restrict(interventionDate, 0.5, "errands");
			break;
		case "e_0":
			builder.restrict(interventionDate, 0.0, "errands");
			break;
		case "t_0.75_5_200":
			tracingConfig.setTracingProbability(Map.of(
					episimConfig.getStartDate(), 0.5,
					interventionDate, 0.75)
			);
			break;
		case "t_0.5_3_200":
			tracingConfig.setTracingDelay_days(Map.of(
					episimConfig.getStartDate(), 5,
					interventionDate, 3)
			);
			break;
		case "t_0.5_5_inf":
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					LocalDate.of(2020, 4, 1), (int) (200 * 0.2),
					LocalDate.of(2020, 6, 15), 200,
					interventionDate, Integer.MAX_VALUE)
			);
			break;
		case "t_0.75_3_inf":
			tracingConfig.setTracingProbability(Map.of(
					episimConfig.getStartDate(), 0.5,
					interventionDate, 0.75)
			);
			tracingConfig.setTracingDelay_days(Map.of(
					episimConfig.getStartDate(), 5,
					interventionDate, 3)
			);
			tracingConfig.setTracingCapacity_pers_per_day(Map.of(
					LocalDate.of(2020, 4, 1), (int) (200 * 0.2),
					LocalDate.of(2020, 6, 15), 200,
					interventionDate, Integer.MAX_VALUE)
			);
			break;
		case "v_10":
			vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse("2020-12-27"), (int)(2000 * 4./3.),
					interventionDate, (int) (4831120 * 0.1),
					interventionDate.plusDays(1), (int) (0)
					));
			break;
		case "v_20":				
			vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse("2020-12-27"), (int)(2000 * 4./3.),
					interventionDate, (int) (4831120 * 0.2),
					interventionDate.plusDays(1), (int) (0)
					));
			break;
		case "v_40":
			vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse("2020-12-27"), (int)(2000 * 4./3.),
					interventionDate, (int) (4831120 * 0.4),
					interventionDate.plusDays(1), (int) (0)
					));
			break;
		case "v_60":
			vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse("2020-12-27"), (int)(2000 * 4./3.),
					interventionDate, (int) (4831120 * 0.6),
					interventionDate.plusDays(1), (int) (0)
					));
			break;
		case "v_80":
			vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse("2020-12-27"), (int)(2000 * 4./3.),
					interventionDate, (int) (4831120 * 0.8),
					interventionDate.plusDays(1), (int) (0)
					));
			break;
		case "v_100":
			vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse("2020-12-27"), (int)(2000 * 4./3.),
					interventionDate, (int) (4831120),
					interventionDate.plusDays(1), (int) (0)
					));
			break;
			
		default:
			throw new IllegalArgumentException("Unknown intervention: " + params.intervention);
	}

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

		@GenerateSeeds(8)
		public long seed;

		@Parameter({1.6})
		double leisureFactor;

		@IntParameter({1})
		int vaccinationFactor;
		
		@StringParameter({"permissive"})
//		@StringParameter({"restrictive", "permissive"})
		public String christmasModel;

		@StringParameter({"2020-12-15", "2020-11-15"})
		String newVariantDate;
		
		@StringParameter({"yesUntil80"})
		String extrapolateRestrictions;
		
		@StringParameter({"none", 
			"l_50", "l_0", "l_c_18-5", "l_c_19-5", "l_c_20-5", "l_c_21-5", "l_c_22-5",
			"w_50", "w_0", "w_ffp",
			"s_50", "s_100", "s_100_masks", "s_50_masks",
			"e_50", "e_0",
			"t_0.75_5_200", "t_0.5_3_200", "t_0.5_5_inf", "t_0.75_3_inf",
			"v_10", "v_20", "v_40", "v_60", "v_80", "v_100"})
		String intervention;
		
		@StringParameter({"2021-02-15"})
		String interventionDate;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, RCalculator.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(330),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


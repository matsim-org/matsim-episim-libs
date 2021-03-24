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
		
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		episimConfig.setStartFromSnapshot("episim-snapshot-330-2021-01-19.zip");

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setEffectiveness(0.9);
		vaccinationConfig.setDaysBeforeFullEffect(1);
		vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
				episimConfig.getStartDate(), 0,
				LocalDate.parse("2020-12-27"), (int) (2000 * 4./3.),
				LocalDate.parse("2021-01-25"), (int) (3000 * 4./3.)
				));
		
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		
		LocalDate interventionDate = LocalDate.parse(params.interventionDate);
		builder.clearAfter("2021-02-21");
		if (params.intervention.startsWith("v_")) interventionDate = interventionDate.minusDays(14);
		switch (params.intervention) {
		case "none":
			break;
		case "l_100":
			builder.restrict(interventionDate, 1., "leisure");
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
		case "w_100":
			builder.restrict(interventionDate, 1., "work", "business");
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
					LocalDate.parse("2021-01-25"), (int) (3000 * 4./3.),
					interventionDate, (int) (4831120 * 0.1),
					interventionDate.plusDays(1), (int) (0)
					));
			break;
		case "v_20":				
			vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse("2020-12-27"), (int)(2000 * 4./3.),
					LocalDate.parse("2021-01-25"), (int) (3000 * 4./3.),
					interventionDate, (int) (4831120 * 0.2),
					interventionDate.plusDays(1), (int) (0)
					));
			break;
		case "v_40":
			vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse("2020-12-27"), (int)(2000 * 4./3.),
					LocalDate.parse("2021-01-25"), (int) (3000 * 4./3.),
					interventionDate, (int) (4831120 * 0.4),
					interventionDate.plusDays(1), (int) (0)
					));
			break;
		case "v_60":
			vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse("2020-12-27"), (int)(2000 * 4./3.),
					LocalDate.parse("2021-01-25"), (int) (3000 * 4./3.),
					interventionDate, (int) (4831120 * 0.6),
					interventionDate.plusDays(1), (int) (0)
					));
			break;
		case "v_80":
			vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse("2020-12-27"), (int)(2000 * 4./3.),
					LocalDate.parse("2021-01-25"), (int) (3000 * 4./3.),
					interventionDate, (int) (4831120 * 0.8),
					interventionDate.plusDays(1), (int) (0)
					));
			break;
		case "v_100":
			vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse("2020-12-27"), (int)(2000 * 4./3.),
					LocalDate.parse("2021-01-25"), (int) (3000 * 4./3.),
					interventionDate, (int) (4831120),
					interventionDate.plusDays(1), (int) (0)
					));
			break;
			
		default:
			throw new IllegalArgumentException("Unknown intervention: " + params.intervention);
	}

		Map<LocalDate, Integer> infPerDayVariant = new HashMap<>();
		infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayVariant.put(LocalDate.parse(params.newVariantDate), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayVariant);
		

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(16)
		public long seed;

		@StringParameter({"2020-12-15"})
		String newVariantDate;
		
		@StringParameter({"none", 
			"l_100", "l_50", "l_0", "l_c_18-5", "l_c_19-5", "l_c_20-5", "l_c_21-5", "l_c_22-5",
			"w_100", "w_50", "w_0", "w_ffp",
			"s_50", "s_100", "s_100_masks", "s_50_masks",
			"t_0.75_5_200", "t_0.5_3_200", "t_0.5_5_inf", "t_0.75_3_inf",
			"v_10", "v_20", "v_40", "v_60", "v_80", "v_100"})
		String intervention;
		
		@StringParameter({"2021-03-08"})
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


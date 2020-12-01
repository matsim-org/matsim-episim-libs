package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.AgeDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinProductionScenario.*;

import javax.annotation.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;


/**
 * Interventions for symmetric Berlin week model
 */
public class BerlinSecondLockdown implements BatchRun<BerlinSecondLockdown.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new Builder().setDiseaseImport( DiseaseImport.yes ).setRestrictions( Restrictions.yes ).setMasks( Masks.yes ).setTracing( Tracing.yes ).setSnapshot(
				Snapshot.episim_snapshot_240_2020_10_12 ).setInfectionModel( AgeDependentInfectionModelWithSeasonality.class ).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "secondLockdown");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new Builder().setDiseaseImport( DiseaseImport.yes ).setRestrictions( Restrictions.yes ).setMasks(
				Masks.yes ).setTracing( Tracing.yes ).setSnapshot( Snapshot.episim_snapshot_240_2020_10_12 ).setInfectionModel(
				AgeDependentInfectionModelWithSeasonality.class ).createSnzBerlinProductionScenario();

		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
		if (params.lockdown.equals("outOfHomeExceptEdu59")) {
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				builder.clearAfter("2020-11-02", act);
				builder.restrict("2020-11-02", 0.59, act);
			}
		}

		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 300,
				LocalDate.of(2020, 6, 15), 2000,
				LocalDate.parse("2020-12-07"), params.tracingCapacity
		));

		if (params.schools.contains("school50")) {
			builder
					.restrict("2020-12-07", 0.5, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					//Weihnachtsferien
					.restrict("2020-12-21", 0.2, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					.restrict("2021-01-03", 0.5, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					//Winterferien
					.restrict("2021-02-01", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					.restrict("2021-02-07", 0.5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					//Osterferien
					.restrict("2021-03-29", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					.restrict("2021-04-11", 0.5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			;
		}
		if (params.schools.contains("N95masks")) {
			builder.restrict("2020-12-07", Restriction.ofMask(FaceMask.N95, 0.9), "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_higher");
		}

		if (params.schools.contains("earlyHolidays")) {
			builder.restrict("2020-12-07", 0., "educ_higher");
			builder.restrict("2020-12-07", 0.2, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
			builder.restrict("2021-01-03", 0.2, "educ_higher");
		}
		
		if (params.schools.contains("noHolidays")) {
			builder.clearAfter("2020-12-08", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		}

		if (params.furhterMeasures.contains("outOfHomeExceptEdu59")) {
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				builder.restrict("2020-12-07", 0.59, act);
			}
		}
		
		if (params.furhterMeasures.contains("FFP@Work")) {
			builder.restrict("2020-12-07", Restriction.ofMask(FaceMask.N95, 0.9), "work");
		}
		
		if (params.furhterMeasures.equals("curfew18-6")) builder.restrict("2020-12-07", Restriction.ofClosingHours(18, 6), "leisure", "shop_daily", "shop_other", "visit", "errands");
		if (params.furhterMeasures.equals("curfew20-6")) builder.restrict("2020-12-07", Restriction.ofClosingHours(20, 6), "leisure", "shop_daily", "shop_other", "visit", "errands");
		if (params.furhterMeasures.equals("curfew22-6")) builder.restrict("2020-12-07", Restriction.ofClosingHours(22, 6), "leisure", "shop_daily", "shop_other", "visit", "errands");
		
		if (params.reducedGroupsize.equals("yes")) {
			builder.restrict("2020-11-02", Restriction.ofReducedGroupSize(4 * 10), "leisure");
			builder.restrict("2020-12-01", Restriction.ofReducedGroupSize(4 * 5), "leisure");
		}
		
		if (params.christmasModel.equals("yes")) {
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				builder.restrict("2020-12-19", 1.0, act);
			}
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
			double fraction = 0.795;
			if (params.furhterMeasures.contains("outOfHomeExceptEdu59") || params.lockdown.equals("outOfHomeExceptEdu59")) fraction = 0.59;				
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				builder.restrict("2021-01-04", fraction, act);
			}
		}
		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());


		return config;
	}

	public static final class Params {

		@GenerateSeeds(1)
		public long seed;

		@StringParameter({"outOfHomeExceptEdu59", "fromData"})
		public String lockdown;

		@IntParameter({0, 2000, Integer.MAX_VALUE})
		int tracingCapacity;

		@StringParameter({"open", "school50", "N95masks", "school50&N95masks", "earlyHolidays", "noHolidays"})
		public String schools;

		@StringParameter({"no", "outOfHomeExceptEdu59", "FFP@Work", "curfew18-6","curfew20-6","curfew22-6"})
		public String furhterMeasures;
		
		@StringParameter({"no", "yes"})
		public String reducedGroupsize;
		
		@StringParameter({"no", "yes"})
		public String christmasModel;
		
		


	}


}

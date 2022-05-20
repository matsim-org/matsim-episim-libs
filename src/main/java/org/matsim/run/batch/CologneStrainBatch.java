package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.vaccination.NoVaccination;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.CologneStrainScenario;
import org.matsim.run.modules.SnzProductionScenario.Vaccinations;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Batch to run all seeds for the cologne strain scenario.
 */
public class CologneStrainBatch implements BatchRun<CologneStrainBatch.Params> {

	@Override
	public CologneStrainScenario getBindings(int id, @Nullable Params params) {
		return new CologneStrainScenario( 1.8993316907481814, Vaccinations.no, NoVaccination.class, false);
	}

	@Override
	public BatchRun.Metadata getMetadata() {
		return BatchRun.Metadata.of("cologne", "strain");
	}

	@Nullable
	@Override
	public Config prepareConfig(int id, Params params) {

		CologneStrainScenario scenario = getBindings(id, params);

		Config config = scenario.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(1.1293063756440906e-05);
		episimConfig.setSnapshotInterval((int) ChronoUnit.DAYS.between(episimConfig.getStartDate(), LocalDate.parse("2021-01-14")));
		episimConfig.setSnapshotPrefix("strain_base_" + params.seed);

		//disease import
		/*
		{
			 Map<LocalDate, Integer> importMap = new HashMap<>();
			 double importFactorBeforeJune = 4.0;
			 double cologneFactor = 0.5;

			 SnzProductionScenario.interpolateImport(importMap, cologneFactor  * importFactorBeforeJune, LocalDate.parse("2020-02-24"),
					 LocalDate.parse("2020-03-09"), 0.9, 23.1);
			 SnzProductionScenario.interpolateImport(importMap, cologneFactor * importFactorBeforeJune, LocalDate.parse("2020-03-09"),
					 LocalDate.parse("2020-03-23"), 23.1, 3.9);
			 SnzProductionScenario.interpolateImport(importMap, cologneFactor * importFactorBeforeJune, LocalDate.parse("2020-03-23"),
					 LocalDate.parse("2020-04-13"), 3.9, 0.1);

			 //summer holidays
			 LocalDate summerHolidaysEnd = LocalDate.parse("2020-08-11");

			 SnzProductionScenario.interpolateImport(importMap, 1.0, summerHolidaysEnd.minusDays(params.w), summerHolidaysEnd, 1.0, params.h);
			 SnzProductionScenario.interpolateImport(importMap,  1.0, summerHolidaysEnd, summerHolidaysEnd.plusDays(params.w), params.h, 1.0);

			 episimConfig.setInfections_pers_per_day(importMap);
		}
		 */


		return config;
	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneStrainBatch.class.getName(),
				RunParallel.OPTION_PARAMS, CologneStrainBatch.Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(350),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}



	public static final class Params {

		@GenerateSeeds(12)
		long seed;

		/*
		@IntParameter({4, 8, 12, 16, 20, 24, 28, 32})
		int h;

		@IntParameter({21, 24, 28})
		int w;
		 */

	}
}

package org.matsim.run.batch;

import com.google.inject.AbstractModule;


import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import java.time.LocalDate;
import java.util.Map;

import javax.annotation.Nullable;


/**
 * batch class used for calibration of scenario with disease import from data
 */
public class BerlinDiseaseImport implements BatchRun<BerlinDiseaseImport.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinWeekScenario2020(25, true, true, OldSymmetricContactModel.class);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "diseaseImport");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020 module = new SnzBerlinWeekScenario2020();
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInitialInfectionDistrict(null);
		episimConfig.setInitialInfections(Integer.MAX_VALUE);

		if (params.ageBoundaries.startsWith("0")) {
			episimConfig.setUpperAgeBoundaryForInitInfections(9);
		}
		if (params.ageBoundaries.startsWith("1")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(10);
			episimConfig.setUpperAgeBoundaryForInitInfections(19);
		}
		if (params.ageBoundaries.startsWith("2")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(20);
			episimConfig.setUpperAgeBoundaryForInitInfections(29);
		}
		if (params.ageBoundaries.startsWith("3")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(30);
			episimConfig.setUpperAgeBoundaryForInitInfections(39);
		}
		if (params.ageBoundaries.startsWith("4")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(40);
			episimConfig.setUpperAgeBoundaryForInitInfections(49);
		}
		if (params.ageBoundaries.startsWith("5")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(50);
			episimConfig.setUpperAgeBoundaryForInitInfections(59);
		}
		if (params.ageBoundaries.startsWith("6")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(60);
			episimConfig.setUpperAgeBoundaryForInitInfections(69);
		}
		if (params.ageBoundaries.startsWith("7")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(70);
			episimConfig.setUpperAgeBoundaryForInitInfections(79);
		}
		if (params.ageBoundaries.startsWith("8")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(80);
		}
		//6.e-6 is current default for runs without age boundaries
		episimConfig.setCalibrationParameter(params.calibrationParam);
//		episimConfig.setCalibrationParameter(6.e-6);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30,
				LocalDate.of(2020, 6, 15), params.tracingCapacity
		));

		return config;
	}

	public static final class Params {

		@GenerateSeeds(10)
		long seed;

		@Parameter({6.e-6, 4.e-6, 2.e-6})
		private double calibrationParam;

		@StringParameter({"0-9", "10-19", "20-29", "30-39", "40-49", "50-59", "60-69", "70-79", "80+", "random"})
		public String ageBoundaries;

		@IntParameter({100, Integer.MAX_VALUE})
		private int tracingCapacity;

	}

}

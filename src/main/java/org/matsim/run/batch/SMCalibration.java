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
public class SMCalibration implements BatchRun<SMCalibration.Params> {

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

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.thetaFactor);

//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
//		episimConfig.setSnapshotInterval(120);


		try {
			Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractions2(SnzBerlinProductionScenario.INPUT.resolve("berlinWeather.csv").toFile(), SnzBerlinProductionScenario.INPUT.resolve("berlinWeatherAvg2000-2020.csv").toFile(), 0.5, params.midPoint1, params.midPoint2,
					(double) 5.);
			episimConfig.setLeisureOutdoorFraction(outdoorFractions);
		} catch (IOException e) {
			e.printStackTrace();
		}

//		VirusStrain.B117.infectiousness = 1.35;

		return config;
	}

	public static final class Params {

		@GenerateSeeds(2)
		public long seed;

		@Parameter({0.001, 0.01, 0.1, 0.5, 1, 2, 10, 100})
		double thetaFactor;

		@Parameter({17.5, 20., 22.5})
		double midPoint1;

		@Parameter({20., 22.5, 25.})
		double midPoint2;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, SMCalibration.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(330),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


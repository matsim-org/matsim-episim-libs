package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TestingConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;


/**
 * Calibrated until 31.08.20, then prognosis
 */
public class BerlinOutOfSample implements BatchRun<BerlinOutOfSample.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder().createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "basePaper");
	}

//	@Override
//	public int getOffset() {
//		return 10000;
//	}

	@Override
	public Config prepareConfig(int id, Params params) {

		double theta = 0.0;
		double activityLevel = 0.0;
		double temp1 = 17.5;
		String weatherData = "berlinWeather_until20210504.csv";
		SnzBerlinProductionScenario.Masks masks = SnzBerlinProductionScenario.Masks.yes;
		SnzBerlinProductionScenario.WeatherModel weatherModel = SnzBerlinProductionScenario.WeatherModel.midpoints_175_250;


		if (params.run.equals("base")) {
			if (params.calibrationUntil.equals("2020-09-01")) theta = 1.3242211858016036e-05;
			else if (params.calibrationUntil.equals("2020-08-01")) theta = 1.3003752011694416e-05;
			else if (params.calibrationUntil.equals("2020-07-01")) theta = 1.29446361732609e-05;
			else if (params.calibrationUntil.equals("2020-06-01")) theta = 1.2681910406410474e-05;
			else if (params.calibrationUntil.equals("2020-05-01")) theta = 1.2046242402811963e-05;
			else throw new RuntimeException();
		}
		else if (params.run.equals("noMasks")) {
			masks = SnzBerlinProductionScenario.Masks.no;
			if (params.calibrationUntil.equals("2020-09-01")) theta = 1.2487267512614e-05;
			else if (params.calibrationUntil.equals("2020-08-01")) theta = 1.2616952113320623e-05;
			else if (params.calibrationUntil.equals("2020-07-01")) theta = 1.2729541370386845e-05;
			else if (params.calibrationUntil.equals("2020-06-01")) theta = 1.2740569081654608e-05;
			else if (params.calibrationUntil.equals("2020-05-01")) theta = 1.2716360988066567e-05;
			else throw new RuntimeException();

		}
		else if (params.run.equals("weather-20-25")) {
			weatherModel = SnzBerlinProductionScenario.WeatherModel.midpoints_200_250;
			temp1 = 20.;
			if (params.calibrationUntil.equals("2020-09-01")) theta = 1.1690737081348964e-05;
			else if (params.calibrationUntil.equals("2020-08-01")) theta = 1.1763819344130063e-05;
			else if (params.calibrationUntil.equals("2020-07-01")) theta = 1.1706179791488293e-05;
			else if (params.calibrationUntil.equals("2020-06-01")) theta = 1.1780172463353952e-05;
			else if (params.calibrationUntil.equals("2020-05-01")) theta = 1.1101742134854978e-05;
			else throw new RuntimeException();
		}

		else throw new RuntimeException();


		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder()
				.setMasks(masks)
				.setWeatherModel(weatherModel)
				.createSnzBerlinProductionScenario();
		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		if (params.calibrationUntil.equals("2020-09-01")) {
			activityLevel = 0.96;
			weatherData = "berlinWeather_until20200831.csv";
		}
		else if (params.calibrationUntil.equals("2020-08-01")) {
			activityLevel = 0.90;
			weatherData = "berlinWeather_until20200731.csv";
		}
		else if (params.calibrationUntil.equals("2020-07-01")) {
			activityLevel = 0.90;
			weatherData = "berlinWeather_until20200630.csv";
		}
		else if (params.calibrationUntil.equals("2020-06-01")) {
			activityLevel = 0.88;
			weatherData = "berlinWeather_until20200531.csv";
		}
		else if (params.calibrationUntil.equals("2020-05-01")) {
			activityLevel = 0.71;
			weatherData = "berlinWeather_until20200430.csv";
		}
		else throw new RuntimeException();

		if (params.avgTemperatures.equals("no")) weatherData = "berlinWeather_until20210504.csv";


		episimConfig.setCalibrationParameter(theta);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		LocalDate calibrationUntil = LocalDate.parse(params.calibrationUntil);


		if (params.activityLevel.equals("frozen")) {
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.startsWith("edu")) continue;
				builder.clearAfter(params.calibrationUntil, act);
				builder.restrict(calibrationUntil, activityLevel, act);
			}

			if (calibrationUntil.equals(LocalDate.parse("2020-05-01")) || calibrationUntil.equals(LocalDate.parse("2020-06-01"))) {
				builder.restrict("2020-06-28", activityLevel, "work", "business");
				builder.restrict("2020-07-05", activityLevel, "work", "business");
				builder.restrict("2020-07-12", activityLevel, "work", "business");
				builder.restrict("2020-07-19", activityLevel, "work", "business");
				builder.restrict("2020-07-26", activityLevel, "work", "business");
				builder.restrict("2020-08-02", activityLevel, "work", "business");
				builder.restrict("2020-08-09", activityLevel, "work", "business");
				builder.apply("2020-06-26", "2020-08-07", (d, e) -> e.put("fraction", 0.83 * (double) e.get("fraction")), "work", "business");
			}

			if (calibrationUntil.equals(LocalDate.parse("2020-07-01"))) {
				builder.restrict("2020-07-05", activityLevel, "work", "business");
				builder.restrict("2020-07-12", activityLevel, "work", "business");
				builder.restrict("2020-07-19", activityLevel, "work", "business");
				builder.restrict("2020-07-26", activityLevel, "work", "business");
				builder.restrict("2020-08-02", activityLevel, "work", "business");
				builder.restrict("2020-08-09", activityLevel, "work", "business");
				builder.apply("2020-07-01", "2020-08-07", (d, e) -> e.put("fraction", 0.92 * (double) e.get("fraction")), "work", "business");
			}

			if (calibrationUntil.equals(LocalDate.parse("2020-08-01"))) {
				builder.restrict("2020-08-02", activityLevel, "work", "business");
				builder.restrict("2020-08-09", activityLevel, "work", "business");
				builder.apply("2020-08-01", "2020-08-07", (d, e) -> e.put("fraction", 0.92 * (double) e.get("fraction")), "work", "business");
			}

			builder.restrict("2020-10-11", activityLevel, "work", "business");
			builder.restrict("2020-10-18", activityLevel, "work", "business");
			builder.restrict("2020-10-25", activityLevel, "work", "business");
			builder.apply("2020-10-09", "2020-10-23", (d, e) -> e.put("fraction", 0.83 * (double) e.get("fraction")), "work", "business");

		}


		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		if (!params.diseaseImport.equals("real")) {
			Map<LocalDate, Integer> diseaseImport = episimConfig.getInfections_pers_per_day().get(VirusStrain.SARS_CoV_2);
			Map<LocalDate, Integer> diseaseImportNew = new HashMap<LocalDate, Integer>();


			for (Entry<LocalDate, Integer> e : diseaseImport.entrySet()) {
				if (e.getKey().isBefore(calibrationUntil))
					diseaseImportNew.put(e.getKey(), e.getValue());
			}

//			if (calibrationUntil.isBefore(LocalDate.parse("2020-06-15")))
			if (params.diseaseImport.equals("1")) {
				diseaseImportNew.put(calibrationUntil, 1);
			}

			episimConfig.setInfections_pers_per_day(diseaseImportNew);
		}


		try {
			Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractions2(SnzBerlinProductionScenario.INPUT.resolve(weatherData).toFile(),
					SnzBerlinProductionScenario.INPUT.resolve("berlinWeatherAvg2000-2020.csv").toFile(), 0.5, temp1, 25., 5. );
			episimConfig.setLeisureOutdoorFraction(outdoorFractions);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return config;
	}

	public static final class Params {

		@GenerateSeeds(30)
		public long seed;

		@StringParameter({"base", "noMasks", "weather-20-25"})
		String run;

		@StringParameter({"real", "frozen"})
		String activityLevel;

		@StringParameter({"2020-09-01", "2020-08-01", "2020-07-01", "2020-06-01", "2020-05-01"})
		String calibrationUntil;

//		@StringParameter({"frozen", "1", "real"})
		@StringParameter({"1", "real"})
		String diseaseImport;

		@StringParameter({"yes", "no"})
		String avgTemperatures;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, BerlinOutOfSample.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


package org.matsim.scenarioCreation;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.RunEpisim;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * This is a runnable class that is used in the calibration process and executes exactly one trial.
 */
@CommandLine.Command(
		name = "trial",
		description = "Run one simulation trial (used for calibration)",
		abbreviateSynopsis = true,
		showDefaultValues = true
)
public final class RunTrial implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(RunTrial.class);

	/**
	 * Parse parameter supplied to the Run trial script.
	 *
	 * @param name         name of the parameter
	 * @param defaultValue default value if not present.
	 */
	public static double parseParam(String name, double defaultValue) {
		String property = System.getProperty("EPISIM_" + name, null);

		if (property == null)
			return defaultValue;

		return Double.parseDouble(property);
	}

	@CommandLine.Parameters(paramLabel = "MODULE", arity = "1", description = "Name of module to load (See RunEpisim)")
	private String moduleName;

	@CommandLine.Option(names = "--number", description = "Trial number", required = true)
	private int number;

	@CommandLine.Option(names = "--name", defaultValue = "calibration", description = "Name for the output directory")
	private String name;

	@CommandLine.Option(names = "--runs", description = "Number of runs with different seeds", defaultValue = "3")
	private int runs;

	@CommandLine.Option(names = "--max-tasks", description = "Number of runs to run in parallel", defaultValue = "8")
	private int maxTasks;

	@CommandLine.Option(names = "--calibParameter", description = "Calibration parameter", defaultValue = "-1")
	private double calibParameter;

	@CommandLine.Option(names = "--offset", description = "Adds an offset to start date", defaultValue = "0")
	private int offset;

	@CommandLine.Option(names = "--days", description = "Number of days to simulate", defaultValue = "52")
	private int days;

	@CommandLine.Option(names = "--ci", description = "Overwrite contact intensities", split = ";")
	private Map<String, Double> ci = new HashMap<>();

	@CommandLine.Option(names = "--param", description = "Specify arbitrary parameter", split = ";")
	private Map<String, Double> params = new HashMap<>();

	@CommandLine.Option(names = "--infectiousness", description = "Set infectiousness for strain")
	private Map<VirusStrain, Double> infectiousness = new HashMap<>();

	@CommandLine.Option(names = "--alpha", description = "Alpha parameter for restrictions", defaultValue = "-1")
	private double alpha;

	@CommandLine.Option(names = "--hospitalFactor", description = "Hospital factor", defaultValue = "-1")
	private double hospitalFactor;

	@CommandLine.Option(names = "--correction", description = "Contact intensity correction", defaultValue = "1")
	private double correction;

	@CommandLine.Option(names = "--start", description = "Start day of the correction")
	private String correctionStart;

	@CommandLine.Option(names = "--snapshot", description = "Path to snapshot to start from")
	private Path snapshot;

	@CommandLine.Option(names = "--unconstrained",
			description = "Removes the restrictions completely in order to calibrate for unconstrained exponential growth.",
			defaultValue = "false")
	private boolean unconstrained;

	public static void main(String[] args) {
		System.exit(new CommandLine(new RunTrial())
				.execute(args));
	}

	@Override
	public Integer call() throws Exception {

		Configurator.setRootLevel(Level.ERROR);
		Configurator.setLevel(log.getName(), Level.INFO);

		for (Map.Entry<String, Double> e : params.entrySet()) {
			System.setProperty("EPISIM_" + e.getKey(), String.valueOf(e.getValue()));
		}

		Module base = RunEpisim.resolveModules(List.of(moduleName)).get(0);
		TrialBatch trial = new TrialBatch(base);

		List<List<Object>> paramValues = new ArrayList<>();
		List<PreparedRun.Run> preparedRuns = new ArrayList<>();

		// same seeding procedure as used in batch runs
		Random rnd = new Random(1);

		// Prepare trials like batch runs
		for (int i = 1; i <= runs; i++) {
			Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(base));
			Config config = injector.getInstance(Config.class);

			long seed = rnd.nextLong();

			TrialParams params = new TrialParams(config, i, i == 1 ? 4711 : seed);
			List<Object> args = List.of(i);

			paramValues.add(args);
			PreparedRun.Run run = new PreparedRun.Run(i, args, trial.prepareConfig(i, params), params);
			preparedRuns.add(run);
		}

		log.info("Starting trial number {} ({} runs) with {} iterations", number, preparedRuns.size(), days);

		log.info("Parameters: {}", params);

		if (correctionStart != null)
			name += "-" + correctionStart;

		PreparedRun prepare = new PreparedRun(trial, List.of("run"), paramValues, preparedRuns);
		RunParallel<TrialBatch> batch = new RunParallel<>(prepare);
		int ret = new CommandLine(batch).execute(
				RunParallel.OPTION_TASKS, String.valueOf(Math.min(maxTasks, preparedRuns.size())),
				RunParallel.OPTION_ITERATIONS, String.valueOf(days),
				"--output", String.format("output-%s/%d", name, number)
		);

		return ret;
	}

	/**
	 * Batch config
	 */
	private final class TrialBatch implements BatchRun<TrialParams> {

		private final Module base;

		public TrialBatch(Module base) {
			this.base = base;
		}

		@Override
		public Metadata getMetadata() {
			return Metadata.of("run", "run");
		}

		@Nullable
		@Override
		public Module getBindings(int id, @Nullable TrialParams params) {
			return base;
		}

		@Nullable
		@Override
		public Config prepareConfig(int id, TrialParams params) {


			EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(params.config, EpisimConfigGroup.class);

			if (offset != 0) {
				log.info("Using offset {} days", offset);
				LocalDate startDate = episimConfig.getStartDate();
				episimConfig.setStartDate(startDate.plusDays(offset));
			}

			// clear restrictions
			if (unconstrained) {
				episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config().build());
				log.info("Cleared all restrictions");
			} else if (alpha > -1 && correction > -1) {

				SnzBerlinScenario25pct2020.BasePolicyBuilder builder = new SnzBerlinScenario25pct2020.BasePolicyBuilder(episimConfig);
				((CreateRestrictionsFromCSV) builder.getActivityParticipation()).setAlpha(alpha);

				HashMap<String, Double> original = new HashMap<>(builder.getCiCorrections());
				original.put(correctionStart, correction);

				builder.setCiCorrections(original);

				FixedPolicy.ConfigBuilder policyConf = builder.buildFixed();

				log.info("Setting policy to alpha={}, ciCorrection={}, correctionStart={}", alpha, correction, correctionStart);

				episimConfig.setPolicy(FixedPolicy.class, policyConf.build());

			} else {
				log.warn("No alpha or ci correction specified");
			}


			for (Map.Entry<String, Double> e : ci.entrySet()) {
				log.info("Setting contact intensity {}={}", e.getKey(), e.getValue());
				episimConfig.getOrAddContainerParams(e.getKey()).setContactIntensity(e.getValue());
			}

			if (calibParameter > -1) {
				log.info("Setting calibration parameter to {}", calibParameter);
				episimConfig.setCalibrationParameter(calibParameter);
			}

			int iterations = days;

			if (correctionStart != null) {
				log.info("Using correction date {} to calculate new number of iterations", correctionStart);
				LocalDate endDate = LocalDate.parse(correctionStart).plusDays(days);
				iterations = (int) (ChronoUnit.DAYS.between(episimConfig.getStartDate(), endDate) + 1);

				// Write a new snapshot
				//if (params.run == 0 && snapshot != null) {
				//	episimConfig.setSnapshotInterval(iterations - 7);
				//}
			}

			if (snapshot != null) {
				String p = snapshot.toString().replace(".zip", params.seed + ".zip");
				log.info("Starting from snapshot {}", p);
				episimConfig.setStartFromSnapshot(p);
			}

			if (hospitalFactor > -1) {
				log.info("Setting hospital factor to {}", hospitalFactor);
				episimConfig.setHospitalFactor(hospitalFactor);
			}

			if (!infectiousness.isEmpty()) {

				VirusStrainConfigGroup strainConfig = ConfigUtils.addOrGetModule(params.config, VirusStrainConfigGroup.class);

				for (Map.Entry<VirusStrain, Double> e : infectiousness.entrySet()) {
					log.info("Setting infectiousness for strain {}: {}", e.getKey(), e.getValue());
					strainConfig.getOrAddParams(e.getKey()).setInfectiousness(e.getValue());
				}
			}

			log.info("Setting seed for run {} to {}", params.run, params.seed);
			params.config.global().setRandomSeed(params.seed);
			episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.restore);

			// events are not needed for calibration
			episimConfig.setWriteEvents(EpisimConfigGroup.WriteEvents.none);
			episimConfig.setThreads(6);

			return params.config;
		}

	}


	/**
	 * Params for single trial run.
	 */
	private static final class TrialParams {

		private final Config config;
		private final int run;
		private final long seed;

		public TrialParams(Config config, int run, long seed) {
			this.config = config;
			this.run = run;
			this.seed = seed;
		}
	}


}

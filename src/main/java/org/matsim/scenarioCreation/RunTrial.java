package org.matsim.scenarioCreation;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimRunner;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.RunEpisim;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;
import picocli.CommandLine;

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

	@CommandLine.Parameters(paramLabel = "MODULE", arity = "1..*", description = "List of modules to load (See RunEpisim)")
	private List<String> moduleNames = new ArrayList<>();

	@CommandLine.Option(names = "--number", description = "Trial number", required = true)
	private int number;

	@CommandLine.Option(names = "--run", description = "Run number", defaultValue = "0")
	private int run;

	@CommandLine.Option(names = "--calibParameter", description = "Calibration parameter", defaultValue = "-1")
	private double calibParameter;

	@CommandLine.Option(names = "--offset", description = "Adds an offset to start date", defaultValue = "0")
	private int offset;

	@CommandLine.Option(names = "--days", description = "Number of days to simulate", defaultValue = "52")
	private int days;

	@CommandLine.Option(names = "--ci", description = "Overwrite contact intensities", split = ";")
	private Map<String, Double> ci = new HashMap<>();

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

		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(RunEpisim.resolveModules(moduleNames)));

		Config config = injector.getInstance(Config.class);

		String name = unconstrained ? "calibration-unconstrained" : "calibration";

		if (correctionStart != null)
			name += "-" + correctionStart;

		config.controler().setOutputDirectory(String.format("output-%s/%d/run%d/", name, number, run));

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

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
			builder.setAlpha(alpha);

			HashMap<String, Double> original = new HashMap<>(builder.getCiCorrections());
			original.put(correctionStart, correction);

			builder.setCiCorrections(original);

			FixedPolicy.ConfigBuilder policyConf = builder.build();

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
			if (run == 0 && snapshot != null) {
				episimConfig.setSnapshotInterval(iterations - 7);
			}
		}

		if (snapshot != null) {
			log.info("Starting from snapshot {}", snapshot);
			episimConfig.setStartFromSnapshot(snapshot.toString());
		}

		if (hospitalFactor > -1) {
			log.info("Setting hospital factor to {}", hospitalFactor);
			episimConfig.setHospitalFactor(hospitalFactor);
		}

		if (run > 0) {
			Random rnd = new Random(run);
			long seed = rnd.nextLong();
			log.info("Setting seed for run {} to {}", run, seed);
			config.global().setRandomSeed(seed);
			episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		}

		log.info("Starting trial number {} (run {}) at {} with {} iterations", number, run, episimConfig.getStartDate(), iterations);

		EpisimRunner runner = injector.getInstance(EpisimRunner.class);
		runner.run(iterations);

		return 0;
	}

}

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
import picocli.CommandLine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * This is a runnable class that is used in the calibration process and executes exactly one trial.
 */
@CommandLine.Command(
		name = "trial",
		description = "Run one simulation trial (used for calibration)"
)
public final class RunTrial implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(RunTrial.class);

	@CommandLine.Option(names = "--number", description = "Trial number", required = true)
	private int number;

	@CommandLine.Option(names = "--calibParameter", description = "Calibration parameter", required = true)
	private double calibParameter;

	@CommandLine.Option(names = "--offset", description = "Adds an offset to start date", defaultValue = "0")
	private int offset;

	@CommandLine.Option(names = "--days", description = "Number of days to simulate", defaultValue = "45")
	private int days;

	@CommandLine.Option(
			names = "--with-restrictions",
			description = "By default the restrictions are removed completely in order to calibrate" +
					"for unconstrained exponential growth. This flag keeps original restrictions and policy as defined in the scenario.",
			defaultValue = "false")
	private boolean withRestrictions;

	@CommandLine.Parameters(paramLabel = "MODULE", arity = "1..*", defaultValue = "SnzBerlinScenario",
			description = "List of modules to load (See RunEpisim)")
	private List<String> moduleNames = new ArrayList<>();

	public static void main(String[] args) {
		System.exit(new CommandLine(new RunTrial()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		Configurator.setLevel("org.matsim.core.config", Level.WARN);
		Configurator.setLevel("org.matsim.core.controler", Level.WARN);
		Configurator.setLevel("org.matsim.core.events", Level.WARN);

		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(RunEpisim.resolveModules(moduleNames)));

		Config config = injector.getInstance(Config.class);

		String name = withRestrictions ? "calibration-restrictions" : "calibration";

		config.controler().setOutputDirectory(String.format("output-%s/%d/", name, number));

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		if (offset != 0) {
			LocalDate startDate = episimConfig.getStartDate();
			episimConfig.setStartDate(startDate.plusDays(offset));
		}

		// clear restrictions
		if (!withRestrictions) {
			episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config().build());
		}

		episimConfig.setCalibrationParameter(calibParameter);

		log.info("Starting run {} with parameter {}", number, calibParameter);

		EpisimRunner runner = injector.getInstance(EpisimRunner.class);
		runner.run(days);

		return 0;
	}
}

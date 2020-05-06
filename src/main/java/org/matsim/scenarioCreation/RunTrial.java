package org.matsim.scenarioCreation;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimRunner;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzScenario;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * This is a runnable class that is used in the calibration process and executes exactly one trial.
 */
@CommandLine.Command(
		name = "trial",
		description = "Run one simulation trial (used for calibration)"
)
public final class RunTrial implements Callable<Integer> {

	@CommandLine.Option(names = "--number", description = "Trial number", required = true)
	public int number;

	@CommandLine.Option(names = "--calibParameter", description = "Calibration parameter", required = true)
	public double calibParameter;

	public static void main(String[] args) {
		System.exit(new CommandLine(new RunTrial()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		Configurator.setLevel("org.matsim.core.config", Level.WARN);
		Configurator.setLevel("org.matsim.core.controler", Level.WARN);
		Configurator.setLevel("org.matsim.core.events", Level.WARN);

		Injector injector = Guice.createInjector(new EpisimModule(), new SnzScenario());

		Config config = injector.getInstance(Config.class);

		config.controler().setOutputDirectory("output-calibration/" + number + "/");

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		// No restrictions
		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config().build());
		episimConfig.setCalibrationParameter(calibParameter);

		EpisimRunner runner = injector.getInstance(EpisimRunner.class);
		runner.run(45);

		return 0;
	}
}

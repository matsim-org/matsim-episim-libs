package org.matsim.run;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimRunner;
import org.matsim.run.modules.OpenBerlinScenario;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "episim",
		description = "Epidemic simulations for MATSim"
)
public class RunEpisim implements Callable<Integer> {

	public static void main(String[] args) {
		new CommandLine(new RunEpisim()).execute(args);
	}

	@Override
	public Integer call() throws Exception {

		OutputDirectoryLogging.catchLogEntries();

		Injector injector = Guice.createInjector(new EpisimModule(), new OpenBerlinScenario());

		Config config = injector.getInstance(Config.class);

		// TODO: retrieve original arguments
		//ConfigUtils.applyCommandline(config, Arrays.copyOfRange(args, 0, args.length));

		OutputDirectoryLogging.initLoggingWithOutputDirectory(config.controler().getOutputDirectory());

		EpisimRunner runner = injector.getInstance(EpisimRunner.class);
		runner.run(500);

		OutputDirectoryLogging.closeOutputDirLogging();

		return 0;
	}
}

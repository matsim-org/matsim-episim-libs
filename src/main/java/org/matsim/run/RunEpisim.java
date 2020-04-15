package org.matsim.run;

import com.google.common.collect.Lists;
import com.google.inject.*;
import com.google.inject.Module;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimRunner;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "episim",
		description = "Run epidemic simulations for MATSim",
		usageHelpWidth = 120,
		mixinStandardHelpOptions = true,
		subcommands = {CommandLine.HelpCommand.class, RunParallel.class, CreateBatteryForCluster.class, ScenarioCreation.class}
)
public class RunEpisim implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(RunEpisim.class);

	@CommandLine.Option(names = "--modules", arity = "1..*", description = "List of modules to load. " +
			"Use the short name from org.matsim.run.modules.* or the fully qualified classname.",
			defaultValue = "OpenBerlinScenario")
	private List<String> moduleNames;

	@CommandLine.Option(names = "--config", description = "Optional Path to config file to load.")
	private Path config;

	@CommandLine.Option(names = "--log", description = "Enable logging to output directory.", defaultValue = "false")
	private boolean logToOutput;

	@CommandLine.Parameters(hidden = true)
	private String[] remainder;

	public static void main(String[] args) {
		new CommandLine(new RunEpisim())
				.setStopAtUnmatched(false)
				.setUnmatchedOptionsArePositionalParams(true)
				.execute(args);
	}

	@Override
	public Integer call() throws Exception {

		OutputDirectoryLogging.catchLogEntries();

		List<Module> modules = Lists.newArrayList(new EpisimModule());

		try {
			modules.addAll(resolveModules(moduleNames));
		} catch (ReflectiveOperationException e) {
			log.error("Could not resolve modules", e);
			return 1;
		}

		if (config != null) {
			if (!Files.exists(config)) {
				log.error("Config file {} does not exists.", config);
				return 1;
			}

			// Bind module only providing the config
			modules.add(new ConfigHolder());
		}

		log.info("Starting with modules: {}", modules);

		Injector injector = Guice.createInjector(modules);

		StringBuilder bindings = new StringBuilder();

		for (Map.Entry<Key<?>, Binding<?>> e : injector.getBindings().entrySet()) {
			bindings.append("\n\t\t").append(e.getKey().getTypeLiteral()).append(" with { ")
					.append(e.getValue().getProvider()).append(" }");
		}

		log.info("Defined Bindings: {}", bindings.toString());

		Config config = injector.getInstance(Config.class);

		ConfigUtils.applyCommandline(config, Arrays.copyOfRange(remainder, 0, remainder.length));

		if (logToOutput) OutputDirectoryLogging.initLoggingWithOutputDirectory(config.controler().getOutputDirectory());

		EpisimRunner runner = injector.getInstance(EpisimRunner.class);
		runner.run(500);

		if (logToOutput) OutputDirectoryLogging.closeOutputDirLogging();

		return 0;
	}

	private List<Module> resolveModules(List<String> modules) throws ReflectiveOperationException {
		List<Module> result = new ArrayList<>();

		for (String name : modules) {
			// Build module path
			if (!name.contains(".")) name = "org.matsim.run.modules." + name;

			Class<?> clazz = ClassLoader.getSystemClassLoader().loadClass(name);
			Module module = (Module) clazz.getDeclaredConstructor().newInstance();
			result.add(module);
		}

		return result;
	}

	private class ConfigHolder extends AbstractModule {
		@Override
		protected void configure() {
			bind(Config.class).toInstance(ConfigUtils.loadConfig(config.toString()));
		}
	}

}

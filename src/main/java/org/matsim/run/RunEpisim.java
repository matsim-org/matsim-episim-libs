/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.run;

import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.internal.BindingImpl;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.util.Modules;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimRunner;
import org.matsim.run.modules.OpenBerlinScenario;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Main class to start episim. It will plug the {@link EpisimModule} together with user supplied modules and start
 * the {@link EpisimRunner}.
 * There needs to be a module that provides a {@link Config} for episim to run or it can be supplied as option with
 * <em>--config [path to xml]</em>
 * <p>
 * Example usage:
 * <pre>
 *   	org.matsim.run.RunEpisim --modules OpenBerlinScenario
 * </pre>
 */
@CommandLine.Command(
		name = "episim",
		headerHeading = RunEpisim.HEADER,
		header = RunEpisim.COLOR + "\t:: Episim ::|@%n",
		description = {"", "Run epidemic simulations for MATSim."},
		footer = "@|cyan If you would like to contribute or report an issue please go to https://github.com/matsim-org/matsim-episim.|@",
		optionListHeading = "%n@|bold,underline Options:|@%n",
		commandListHeading = "%n@|bold,underline Commands:|@%n",
		footerHeading = "\n",
		usageHelpWidth = 120,
		usageHelpAutoWidth = true, showDefaultValues = true, mixinStandardHelpOptions = true, abbreviateSynopsis = true,
		subcommands = {CommandLine.HelpCommand.class, RunParallel.class, CreateBatteryForCluster.class, ScenarioCreation.class, AnalysisCommand.class}
)
public class RunEpisim implements Callable<Integer> {

	public static final String COLOR = "@|bold,fg(81) ";
	public static final String HEADER = COLOR +
			"  __  __   _ _____ ___ _       \n" +
			" |  \\/  | /_\\_   _/ __(_)_ __  \n" +
			" | |\\/| |/ _ \\| | \\__ \\ | '  \\ \n" +
			" |_|  |_/_/ \\_\\_| |___/_|_|_|_|\n|@";

	private static final Logger log = LogManager.getLogger(RunEpisim.class);

	@CommandLine.Option(names = "--modules", arity = "0..*", description = "List of modules to load. " +
			"Use the short name from org.matsim.run.modules.* or the fully qualified classname.", defaultValue = "${env:EPISIM_MODULES}")
	private List<String> moduleNames = new ArrayList<>();

	@CommandLine.Option(names = "--config", description = "Optional Path to config file to load.")
	private Path config;

	@CommandLine.Option(names = {"-v", "--verbose"}, description = "Enable additional logging from MATSim core", defaultValue = "false")
	private boolean verbose;

	@CommandLine.Option(names = "--log", description = "Enable logging to output directory.", defaultValue = "false")
	private boolean logToOutput;

	@CommandLine.Option(names = "--iterations", description = "Maximum number of days to simulate.", defaultValue = "360")
	private int maxIterations;

	@CommandLine.Parameters(hidden = true)
	private String[] remainder;

	public static void main(String[] args) {
		new CommandLine(new RunEpisim())
				.setStopAtUnmatched(false)
				.setUnmatchedOptionsArePositionalParams(true)
				.execute(args);
		// (the "execute" will run "RunEpisim#call()")
	}

	/**
	 * Prints defined bindings to log.
	 */
	static void printBindings(Injector injector) {
		StringBuilder bindings = new StringBuilder();

		for (Map.Entry<Key<?>, Binding<?>> e : injector.getBindings().entrySet()) {
			BindingImpl<?> binding = (BindingImpl<?>) e.getValue();
			bindings.append("\n\t\t").append(e.getKey().getTypeLiteral()).append(" with { ");

			// Guice toString methods are very inconsistent
			// we only re-use some of them
			if (binding instanceof ConstructorBinding) {
				bindings.append("constructor from ").
						append(binding.getSource())
						.append("[").append(binding.getScoping()).append("]");

			} else if (binding instanceof LinkedKeyBinding) {
				Key<?> target = ((LinkedKeyBinding<?>) binding).getLinkedKey();

				bindings.append(target.getTypeLiteral()).append(" from ")
						.append(binding.getSource())
						.append("[").append(binding.getScoping()).append("]");


			} else if (binding instanceof ProviderInstanceBinding || binding instanceof InstanceBinding)
				bindings.append(binding.getProvider());

			else {
				bindings.append(binding.getProvider()).append(" from ").
						append(binding.getSource())
						.append("[").append(binding.getScoping()).append("]");
			}

			bindings.append(" }");
		}

		log.info("Defined Bindings: {}", bindings.toString());
	}

	/**
	 * Resolve and instantiate modules by their name.
	 */
	public static List<Module> resolveModules(List<String> modules) throws ReflectiveOperationException {
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

	@Override
	public Integer call() throws Exception {

		OutputDirectoryLogging.catchLogEntries();

		if (!verbose) {
			Configurator.setLevel("org.matsim.core.config", Level.WARN);
			Configurator.setLevel("org.matsim.core.controler", Level.WARN);
			Configurator.setLevel("org.matsim.core.events", Level.WARN);
		}

		List<Module> modules;
		try {
			modules = new ArrayList<>(resolveModules(moduleNames));
		} catch (ReflectiveOperationException e) {
			log.error("Could not resolve modules", e);
			return 1;
		}

		if (config != null) {
			if (!Files.exists(config)) {
				log.error("Config file {} does not exist.", config);
				return 1;
			}

			// Bind module only providing the config
			modules.add(new ConfigHolder());
		}

		if (modules.isEmpty()) {
			log.info("Using default OpenBerlinScenario");
			modules.add(new OpenBerlinScenario());
		}

		log.info("Starting with modules: {}", modules);

		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(modules));
		// yyyyyy In MATSim, the use of "override" in the production code was a consequence of the original design, which was a framework with default modules, and the
		// capability to replace them was added later.  Most of us agree that this went against the intent of Guice, which we interpret as forcing users to provide unique
		// bindings, and abort when there are zero or multiple bindings for the same thing.  With that interpretation, "override" should not be used in production code, but
		// only for testing. --  Here, it seems, that we are going back to the matsim approach where there are default modules everywhere, and configuration is done by
		// replacing them.  Was the other approach (to enforce explicit bindings, i.e. to _not_ use override in the production code) ever tried?  If so, for which reasons
		// was it rejected?  kai, apr'20

		printBindings(injector);

		Config config = injector.getInstance(Config.class);

		// We collect the remaining unparsed options and give them to the MATSim command line util
		// it will parse options to modify the config like --config:controler.runId
		if (remainder != null)
			ConfigUtils.applyCommandline(config, Arrays.copyOfRange(remainder, 0, remainder.length));
		// yyyy We now have two command line utils on top of each other.  I can see that it makes sense to use an external library rather than
		// something self-written.  But could you please defend the design decision to use both of them on top of each other?  kai, apr'20

		if (logToOutput) OutputDirectoryLogging.initLoggingWithOutputDirectory(config.controler().getOutputDirectory());

		EpisimRunner runner = injector.getInstance(EpisimRunner.class);
		runner.run(maxIterations);

		if (logToOutput) OutputDirectoryLogging.closeOutputDirLogging();

		return 0;
	}

	private class ConfigHolder extends AbstractModule {
		@Override
		protected void configure() {
			bind(Config.class).toInstance(ConfigUtils.loadConfig(config.toString()));
		}
	}

}

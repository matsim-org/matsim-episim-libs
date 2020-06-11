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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.*;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Execute one {@link BatchRun} run in parallel. The work can also be distributed across multiple runners,
 * by using the <em>--worker-index</em> and <em>--total-worker</em> options.
 *
 * @param <T> type to match batch run and params
 * @see CreateBatteryForCluster
 */
@CommandLine.Command(
		name = "runParallel",
		description = "Run batch scenario in parallel in one process.",
		showDefaultValues = true,
		mixinStandardHelpOptions = true
)
public class RunParallel<T> implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(RunParallel.class);

	@CommandLine.Option(names = "--output", defaultValue = "${env:EPISIM_OUTPUT:-output}")
	private Path output;

	@CommandLine.Option(names = "--setup", defaultValue = "${env:EPISIM_SETUP:-org.matsim.run.batch.Berlin2020Tracing}")
	private Class<? extends BatchRun<T>> setup;

	@CommandLine.Option(names = "--params", defaultValue = "${env:EPISIM_PARAMS:-org.matsim.run.batch.Berlin2020Tracing$Params}")
	private Class<T> params;

	@CommandLine.Option(names = "--threads", defaultValue = "4", description = "Number of threads to use concurrently")
	private int threads;

	@CommandLine.Option(names = "--total-worker", defaultValue = "1", description = "Total number of worker processes available for this run." +
			"The tasks will be split evenly between all processes using the index.")
	private int totalWorker;

	@CommandLine.Option(names = "--worker-index", defaultValue = "0", description = "Index of this worker process")
	private int workerIndex;

	@CommandLine.Option(names = "--min-job", defaultValue = "${env:EPISIM_MIN_JOB:-0}", description = "Job to start at (skip first n jobs).")
	private int minJob;

	@CommandLine.Option(names = "--max-jobs", defaultValue = "${env:EPISIM_MAX_JOBS:-0}", description = "Maximum number of jobs to execute. (0=all)")
	private int maxJobs;


	@SuppressWarnings("rawtypes")
	public static void main(String[] args) {
		System.exit(new CommandLine(new RunParallel()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		Configurator.setLevel("org.matsim.core.config", Level.WARN);
		Configurator.setLevel("org.matsim.core.controler", Level.WARN);
		Configurator.setLevel("org.matsim.core.events", Level.WARN);

		if (!Files.exists(output)) Files.createDirectories(output);

		// Same context as if would be run from config
		URL context = new File("./input").toURI().toURL();

		ExecutorService executor = Executors.newFixedThreadPool(threads);

		PreparedRun prepare = BatchRun.prepare(setup, params);
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		log.info("Reading base scenario...");

		// All config need to have the same base config (population, events, etc..)
		Config baseConfig = prepare.runs.get(0).config;
		baseConfig.setContext(context);
		EpisimConfigGroup episimBase = ConfigUtils.addOrGetModule(baseConfig, EpisimConfigGroup.class);

		Scenario scenario = ScenarioUtils.loadScenario(baseConfig);
		ReplayHandler replay = new ReplayHandler(episimBase, scenario);

		int i = 0;
		for (PreparedRun.Run run : prepare.runs) {
			if (i++ % totalWorker != workerIndex)
				continue;

			if (i < minJob)
				continue;

			if (maxJobs > 0 && i >= maxJobs) break;

			EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(run.config, EpisimConfigGroup.class);
			if (!episimBase.getInputEventsFile().equals(episimConfig.getInputEventsFile())) {
				log.error("Input files differs for run {}", run.id);
				return 1;
			}

			String outputPath = output + "/" + prepare.getOutputName(run);
			run.config.controler().setOutputDirectory(outputPath);
			run.config.controler().setRunId(prepare.setup.getMetadata().name + run.id);
			run.config.setContext(context);

			futures.add(CompletableFuture.runAsync(new Task(prepare.setup.getBindings(),
					new ParallelModule(scenario, run.config, replay)), executor));
		}

		log.info("Created {} (out of {}) tasks for worker {} ({} threads available)", futures.size(), prepare.runs.size(), workerIndex, threads);

		// Wait for all futures to complete
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		log.info("Finished all tasks");
		executor.shutdown();

		return 0;
	}

	private static final class ParallelModule extends AbstractModule {

		private final Scenario scenario;
		private final Config config;
		private final ReplayHandler replay;

		private ParallelModule(Scenario scenario, Config config, ReplayHandler replay) {
			this.scenario = scenario;
			this.config = config;
			this.replay = replay;
		}

		@Override
		protected void configure() {
			bind(Scenario.class).toInstance(scenario);
			bind(Config.class).toInstance(config);
			bind(ReplayHandler.class).toInstance(replay);
		}
	}

	private static final class Task implements Runnable {

		private static final AtomicInteger i = new AtomicInteger(0);

		@Nullable
		private final AbstractModule bindings;
		private final ParallelModule module;

		private Task(@Nullable AbstractModule bindings, ParallelModule module) {
			this.bindings = bindings;
			this.module = module;
		}

		@Override
		public void run() {

			Module base;
			if (bindings == null)
				base = new EpisimModule();
			else
				base = Modules.override(new EpisimModule()).with(bindings);


			// overwrite the scenario definition
			Injector injector = Guice.createInjector(Modules.override(base).with(this.module));

			if (i.getAndIncrement() == 0) {
				RunEpisim.printBindings(injector);
			}

			log.info("Starting task: {}", this.module.config.controler().getOutputDirectory());

			EpisimRunner runner = injector.getInstance(EpisimRunner.class);

			runner.run(360);

			log.info("Task finished: {}", this.module.config.controler().getOutputDirectory());
		}
	}

}

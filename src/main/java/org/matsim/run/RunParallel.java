/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */


package org.matsim.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.PreparedRun;
import org.matsim.episim.ReplayHandler;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CommandLine.Command(
		name = "RunParallel",
		description = "Run batch scenario in parallel in one process.",
		showDefaultValues = true,
		mixinStandardHelpOptions = true
)
public class RunParallel<T> implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(CreateBatteryForCluster.class);

	@CommandLine.Option(names = "--output", defaultValue = "${env:EPISIM_OUTPUT:-output}")
	private Path output;

	@CommandLine.Option(names = "--setup", defaultValue = "${env:EPISIM_SETUP:-org.matsim.run.batch.SchoolClosure}")
	private Class<? extends BatchRun<T>> setup;

	@CommandLine.Option(names = "--params", defaultValue = "${env:EPISIM_PARAMS:-org.matsim.run.batch.SchoolClosure$Params}")
	private Class<T> params;

	@CommandLine.Option(names = "--threads", defaultValue = "4", description = "Number of threads to use concurrently")
	private int threads;

	@CommandLine.Option(names = "--total-worker", defaultValue = "1", description = "Total number of worker processes available for this run." +
			"The tasks will be split evenly between all processes using the index.")
	private int totalWorker;

	@CommandLine.Option(names = "--worker-index", defaultValue = "0", description = "Index of this worker process")
	private int workerIndex;

	@CommandLine.Option(names = "--max-jobs", defaultValue = "${env:EPISIM_MAX_JOBS:-0}", description = "Maximum number of jobs to execute. (0=all)")
	private int maxJobs;


	@SuppressWarnings("rawtypes")
	public static void main(String[] args) {
		System.exit(new CommandLine(new RunParallel()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		ExecutorService executor = Executors.newFixedThreadPool(threads);

		PreparedRun prepare = BatchRun.prepare(setup, params);
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		log.info("Reading base scenario...");

		// All config need to have the same base config (population, events, etc..)
		Config baseConfig = prepare.runs.get(0).config;
		EpisimConfigGroup episimBase = ConfigUtils.addOrGetModule(baseConfig, EpisimConfigGroup.class);

		Scenario scenario = ScenarioUtils.loadScenario(baseConfig);
		ReplayHandler replay = new ReplayHandler(episimBase, scenario);

		int i = 0;
		for (PreparedRun.Run run : prepare.runs) {
			if (i++ % totalWorker != workerIndex)
				continue;

			if (maxJobs > 0 && i >= maxJobs) break;

			EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(run.config, EpisimConfigGroup.class);
			if (!episimBase.getInputEventsFile().equals(episimConfig.getInputEventsFile())) {
				log.error("Input files differs for run {}", run.id);
				return 1;
			}

			String outputPath = output + "/" + prepare.setup.getOutputName(run);
			Path out = Paths.get(outputPath);
			if (!Files.exists(out)) Files.createDirectories(out);
			run.config.controler().setOutputDirectory(outputPath);

			futures.add(CompletableFuture.runAsync(new Task(scenario, run.config, replay), executor));
		}

		log.info("Created {} (out of {}) tasks for worker {} ({} threads available)", futures.size(), prepare.runs.size(), workerIndex, threads);

		// Wait for all futures to complete
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		log.info("Finished all tasks");
		executor.shutdown();

		return 0;
	}

	private static class Task implements Runnable {

		private final Scenario scenario;
		private final Config config;
		private final ReplayHandler replay;

		private Task(Scenario scenario, Config config, ReplayHandler replay) {
			this.scenario = scenario;
			this.config = config;
			this.replay = replay;
		}

		@Override
		public void run() {
			RunEpisim.simulationLoop(config, scenario, replay, 200, null);
			log.info("Task finished: {}", config.controler().getOutputDirectory());
		}
	}

}

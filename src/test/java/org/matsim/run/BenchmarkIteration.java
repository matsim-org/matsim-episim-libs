package org.matsim.run;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.ReplayHandler;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class BenchmarkIteration {

	private EventsManager events;

	private ReplayHandler replay;
	private int iteration;

	public static void main(String[] args) throws RunnerException {

		Options opt = new OptionsBuilder()
				.include(BenchmarkIteration.class.getSimpleName())
				.warmupIterations(12).warmupTime(TimeValue.seconds(1))
				.measurementIterations(30).measurementTime(TimeValue.seconds(1))
				.forks(1)
				.build();

		new Runner(opt).run();
	}

	@Setup
	public void setup() {
		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
//		config.plans().setInputFile("../berlin_pop_populationAttributes.xml.gz");
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/Berlin/episim-input/snzDrt220.0.events.reduced.xml.gz");
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.0000012);

		RunEpisimSnz.addParams(episimConfig);
		RunEpisimSnz.setContactIntensities(episimConfig);

		Scenario scenario = ScenarioUtils.loadScenario(config);

		replay = new ReplayHandler(episimConfig, scenario);
		events = EventsUtils.createEventsManager();

		InfectionEventHandler eventHandler = new InfectionEventHandler(config, scenario, events);
		events.addHandler(eventHandler);

	}

	@Benchmark
	public void iteration() {

		events.resetHandlers(iteration);
		replay.replayEvents(events, iteration);
		iteration++;

	}


}

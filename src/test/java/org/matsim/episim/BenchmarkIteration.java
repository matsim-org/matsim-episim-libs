package org.matsim.episim;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;
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

	private EpisimRunner runner;
	private InfectionEventHandler handler;
	private ReplayHandler replay;
	private EpisimReporting reporting;
	private int iteration = 1;

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

		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(new SnzBerlinWeekScenario2020()));

		//injector.getInstance(EpisimConfigGroup.class).setWriteEvents(EpisimConfigGroup.WriteEvents.tracing);
		//injector.getInstance(TracingConfigGroup.class).setPutTraceablePersonsInQuarantineAfterDay(0);

		runner = injector.getInstance(EpisimRunner.class);
		replay = injector.getInstance(ReplayHandler.class);
		handler = injector.getInstance(InfectionEventHandler.class);
		reporting = injector.getInstance(EpisimReporting.class);

		injector.getInstance(EventsManager.class).addHandler(handler);

		// benchmark with event writing
		// injector.getInstance(EventsManager.class).addHandler(reporting);

		handler.init(replay.getEvents());
	}

	@Benchmark
	public void iteration() {

		runner.doStep(replay, handler, reporting, iteration);
		iteration++;

	}
}

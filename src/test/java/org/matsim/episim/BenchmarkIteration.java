package org.matsim.episim;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.run.modules.SnzBerlinScenario;
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

		Injector injector = Guice.createInjector(new EpisimModule(), new SnzBerlinScenario());

		//injector.getInstance(EpisimConfigGroup.class).setWriteEvents(EpisimConfigGroup.WriteEvents.all);
		injector.getInstance(TracingConfigGroup.class).setPutTraceablePersonsInQuarantineAfterDay(0);

		runner = injector.getInstance(EpisimRunner.class);
		replay = injector.getInstance(ReplayHandler.class);
		handler = injector.getInstance(InfectionEventHandler.class);

		injector.getInstance(EventsManager.class).addHandler(handler);

		// benchmark with event writing
		//injector.getInstance(EventsManager.class).addHandler(injector.getInstance(EpisimReporting.class));

		handler.init(replay.getEvents());
	}

	@Benchmark
	public void iteration() {

		runner.doStep(replay, handler, iteration);
		iteration++;

	}
}

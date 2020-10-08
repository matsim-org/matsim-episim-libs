package org.matsim.scenarioCreation;

import com.google.common.collect.Streams;
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
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.DefaultContactModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * This is a runnable class that is used to write all contact events between persons.
 */
@CommandLine.Command(
		name = "extractContacts",
		description = "Run one simulation trial to generate contact events between all persons",
		abbreviateSynopsis = true,
		showDefaultValues = true
)
public final class ExtractContacts implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(ExtractContacts.class);

	@CommandLine.Parameters(paramLabel = "sample", arity = "1", description = "Sample size of scenario to load",
			defaultValue = "10")
	private int sample;

	@CommandLine.Option(names = "--output", description = "Output folder", defaultValue = "output-contacts")
	private Path output;

	public static void main(String[] args) {
		System.exit(new CommandLine(new ExtractContacts())
				.execute(args));
	}

	@Override
	public Integer call() throws Exception {

		Configurator.setLevel("org.matsim.core.config", Level.WARN);
		Configurator.setLevel("org.matsim.core.controler", Level.WARN);
		Configurator.setLevel("org.matsim.core.events", Level.WARN);

		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(new SnzBerlinWeekScenario2020(sample, false, false, DefaultContactModel.class)));

		Config config = injector.getInstance(Config.class);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		// Change config to write all contacts
		episimConfig.setMaxContacts(Double.MAX_VALUE);
		episimConfig.setWriteEvents(EpisimConfigGroup.WriteEvents.tracing);
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(0);

		int iterations = episimConfig.getInputEventsFiles().size();

		Map<DayOfWeek, EpisimConfigGroup.EventFileParams> days = new EnumMap<>(DayOfWeek.class);

		// one graph per file
		for (EpisimConfigGroup.EventFileParams file : episimConfig.getInputEventsFiles()) {
			file.getDays().forEach(d -> days.put(d, file));
		}

		// reduce days
		Iterator<Map.Entry<DayOfWeek, EpisimConfigGroup.EventFileParams>> it = days.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<DayOfWeek, EpisimConfigGroup.EventFileParams> e = it.next();
			DayOfWeek next = e.getKey().plus(1);
			if (days.get(next) == e.getValue() && days.size() > 1)
				it.remove();
		}

		LocalDate startDate = LocalDate.of(2020, 1, 5).plusDays(days.keySet().iterator().next().getValue());
		log.info("Simulating {} iterations: {}, starting from {}", iterations, days.keySet(), startDate);

		episimConfig.setStartDate(startDate);

		// empty restrictions
		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config().build());
		config.controler().setOutputDirectory(output.toString());

		EpisimRunner runner = injector.getInstance(EpisimRunner.class);
		runner.run(iterations + 1);

		// On windows, files are still busy, so we wait here
		Thread.sleep(5000);

		log.info("Finished");

		Streams.zip(
				days.keySet().stream(),
				Files.list(output.resolve("events")),
				(day, path) -> {
					try {
						log.info("Copying {} for {}", path, day);
						Files.copy(path, output.resolve("events_" + day.toString() + "-" + sample + "pt.xml.gz"));
					} catch (IOException e) {
						log.error("Could not move file", e);
					}
					return 0;
				}
		).toArray();

		return 0;
	}

}

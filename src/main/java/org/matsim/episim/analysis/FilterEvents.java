package org.matsim.episim.analysis;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.api.core.v01.events.Event;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.events.EpisimInitialInfectionEvent;
import org.matsim.episim.events.EpisimStartEvent;
import org.matsim.episim.events.EpisimVaccinationEvent;
import org.matsim.episim.reporting.EpisimWriter;
import org.matsim.run.AnalysisCommand;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

@CommandLine.Command(
		name = "filterEvents",
		description = "Create reduced event file by filtering only for certain types."
)
public class FilterEvents implements OutputAnalysis {

	private static final Logger log = LogManager.getLogger(FilterEvents.class);

		@CommandLine.Option(names = "--output", defaultValue = "../public-svn/matsim/scenarios/countries/de/episim/battery/jakob/2022-08-02/2-vax-b/analysis")
//	@CommandLine.Option(names = "--output", defaultValue = "./output/")
	private Path output;

	@CommandLine.Parameters(paramLabel = "TYPE", arity = "0..*", description = "Names of event types to keep")
	private Set<String> filter = Set.of(EpisimStartEvent.EVENT_TYPE, EpisimInitialInfectionEvent.EVENT_TYPE, EpisimInfectionEvent.EVENT_TYPE, EpisimVaccinationEvent.EVENT_TYPE);

	public static void main(String[] args) {
		System.exit(new CommandLine(new FilterEvents()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		Configurator.setLevel("org.matsim.core.config", Level.WARN);
		Configurator.setLevel("org.matsim.core.controler", Level.WARN);
		Configurator.setLevel("org.matsim.core.events", Level.WARN);
		Configurator.setLevel("org.matsim.core.utils", Level.WARN);

		if (!Files.exists(output)) {
			log.error("Output path {} does not exist.", output);
			return 2;
		}

		AnalysisCommand.forEachScenario(output, scenario -> {
			try {
				analyzeOutput(scenario);
			} catch (IOException e) {
				log.error("Failed processing {}", scenario, e);
			}
		});

		log.info("done");

		return 0;
	}

	@Override
	public void analyzeOutput(Path output) throws IOException {

		String id = AnalysisCommand.getScenarioPrefix(output);
		TarArchiveOutputStream zipOut = new TarArchiveOutputStream(Files.newOutputStream(output.resolve(id + "events_reduced.tar")));

		Handler handler = new Handler(filter, zipOut);

		AnalysisCommand.forEachEvent(output, s -> {
			handler.reset(-1);
		}, false, handler);

		handler.closeEntry();
		zipOut.close();

		log.info("Filtered {} out of {} events for {}", handler.filtered, handler.total, id);

	}

	private static class Handler implements BasicEventHandler {

		private final Set<String> filter;
		private final TarArchiveOutputStream zipOut;
		private final ByteArrayOutputStream os = new ByteArrayOutputStream(1024);

		private OutputStreamWriter out;
		private int day = 1;
		private int total = 0;
		private int filtered = 0;

		private Handler(Set<String> filter, TarArchiveOutputStream zipOut) {
			this.filter = filter;
			this.zipOut = zipOut;
		}

		@Override
		public void handleEvent(Event event) {

			total++;

			if (filter.contains(event.getEventType())) {
				try {
					EpisimWriter.writeEvent(out, event, event.getTime());
					filtered++;
				} catch (IOException e) {
					log.error("Could not write event", e);
				}
			}

		}

		@Override
		public void reset(int iteration) {

			try {
				if (out != null) {
					closeEntry();
				}

				out = new OutputStreamWriter(new GZIPOutputStream(os));
				out.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<events version=\"1.0\">\n");


			} catch (IOException e) {
				log.error("Could not write day", e);
			}
		}

		private void closeEntry() throws IOException {

			out.append("</events>");
			out.close();

			// Increment day after it was written
			TarArchiveEntry entry = new TarArchiveEntry(String.format("day_%03d.xml.gz", day++));
			entry.setSize(os.size());

			zipOut.putArchiveEntry(entry);

			os.writeTo(zipOut);
			os.reset();

			zipOut.closeArchiveEntry();
			zipOut.flush();

		}

	}

}

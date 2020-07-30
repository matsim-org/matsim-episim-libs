package org.matsim.episim.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.jgrapht.nio.GraphExporter;
import org.jgrapht.nio.gexf.GEXFExporter;
import org.jgrapht.nio.graphml.GraphMLExporter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.episim.events.EpisimContactEvent;
import org.matsim.episim.events.EpisimEventsReader;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Executable class to analyze contacts events.
 */
@CommandLine.Command(
		name = "contactGraph",
		description = "Build contact graph from event file."
)
class AnalyzeContactGraph implements Callable<Integer>, BasicEventHandler {

	private static final Logger log = LogManager.getLogger(AnalyzeSnzData.class);

	@CommandLine.Parameters(arity = "1..*")
	private Path input;

	@CommandLine.Option(names = "--output", defaultValue = "output-graph")
	private Path outputFolder;

	private Graph<Id<Person>, DefaultEdge> graph;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalyzeContactGraph()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		EventsManager manager = EventsUtils.createEventsManager();

		manager.addHandler(this);

		graph = new DefaultUndirectedGraph<>(DefaultEdge.class);

		new EpisimEventsReader(manager).readFile(input.toString());

		log.info("Reading done");

		Files.createDirectories(outputFolder);

		Map<String , GraphExporter<Id<Person>, DefaultEdge>> exporter = Map.of(
			//	"s6", new Graph6Sparse6Exporter<>(Graph6Sparse6Exporter.Format.SPARSE6),
				"graphml", new GraphMLExporter<>(),
				"gexf", new GEXFExporter<>()
		);

		for (Map.Entry<String, GraphExporter<Id<Person>, DefaultEdge>> e : exporter.entrySet()) {

			try {
				String filename = outputFolder.resolve("graph." + e.getKey() + ".gz").toString();
				e.getValue().exportGraph(graph, IOUtils.getBufferedWriter(filename));

				log.info("Written {}", filename);

			} catch (RuntimeException exc) {
				log.error("Could not export to format " + e.getKey());
			}



		}

		return 0;
	}

	@Override
	public void handleEvent(Event event) {
		if (event instanceof EpisimContactEvent) {

			EpisimContactEvent ev = (EpisimContactEvent) event;

			graph.addVertex(ev.getPersonId());
			graph.addVertex(ev.getContactPersonId());
			graph.addEdge(ev.getPersonId(), ev.getContactPersonId());

		}
	}

	@Override
	public void reset(int iteration) {
	}

}

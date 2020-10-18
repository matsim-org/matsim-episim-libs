package org.matsim.episim.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.nio.GraphExporter;
import org.jgrapht.nio.gml.GmlExporter;
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

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.Callable;

/**
 * Executable class to crate contact graph from events.
 */
@CommandLine.Command(
		name = "contactGraph",
		description = "Build contact graph from event file."
)
public class CreateContactGraph implements Callable<Integer>, BasicEventHandler {

	private static final Logger log = LogManager.getLogger(AnalyzeSnzData.class);

	@CommandLine.Parameters(arity = "1")
	private Path input;

	@CommandLine.Option(names = "--output", defaultValue = "output-graph")
	private Path outputFolder;

	private DefaultUndirectedWeightedGraph<Id<Person>, DefaultEdge> graph;

	public static void main(String[] args) {
		System.exit(new CommandLine(new CreateContactGraph()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		EventsManager manager = EventsUtils.createEventsManager();

		manager.addHandler(this);

		graph = new DefaultUndirectedWeightedGraph<>(DefaultEdge.class);

		new EpisimEventsReader(manager).readFile(input.toString());

		log.info("Created graph with {} nodes and {} edges", graph.vertexSet().size(), graph.edgeSet().size());

		Files.createDirectories(outputFolder);

		Map<String, GraphExporter<Id<Person>, DefaultEdge>> exporter = Map.of(
				//	"s6", new Graph6Sparse6Exporter<>(Graph6Sparse6Exporter.Format.SPARSE6),
				"graphml", new GraphMLExporter<>(),
				"gml", new GmlExporter<>()
				//	"gexf", new GEXFExporter<>()
		);

		for (Map.Entry<String, GraphExporter<Id<Person>, DefaultEdge>> e : exporter.entrySet()) {

			try {
				String baseName = input.getFileName().toString().replace(".xml", "").replace(".gz", "");

				String filename = outputFolder.resolve("graph-" + baseName + "." + e.getKey() + ".gz").toString();
				BufferedWriter writer = IOUtils.getBufferedWriter(filename);
				e.getValue().exportGraph(graph, writer);
				writer.close();

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
			DefaultEdge edge = graph.getEdge(ev.getPersonId(), ev.getContactPersonId());
			if (edge == null) {
				edge = graph.addEdge(ev.getPersonId(), ev.getContactPersonId());
				graph.setEdgeWeight(edge, ev.getDuration());
			} else {
				graph.setEdgeWeight(edge, graph.getEdgeWeight(edge) + ev.getDuration());
			}
		}
	}

	@Override
	public void reset(int iteration) {
	}

}

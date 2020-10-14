package org.matsim.episim.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.nio.graphml.GraphMLImporter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Executable class to analyze contacts events.
 */
@CommandLine.Command(
		name = "analyzeGraph",
		description = "Build contact graph from event file."
)
class AnalyzeContactGraph implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(AnalyzeSnzData.class);

	@CommandLine.Parameters(arity = "1")
	private Path input;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalyzeContactGraph()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		DefaultUndirectedWeightedGraph<Id<Person>, DefaultEdge> graph = new DefaultUndirectedWeightedGraph<>(DefaultEdge.class);

		GraphMLImporter<Id<Person>, DefaultEdge> importer = new GraphMLImporter<>();
		importer.setVertexFactory(Id::createPersonId);

		importer.importGraph(graph, IOUtils.getBufferedReader(input.toString()));

		log.info("Imported graph with {} edges and {} nodes", graph.edgeSet().size(), graph.vertexSet().size());


		return 0;
	}



}

package org.matsim.episim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.nio.AttributeType;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.graphml.GraphMLExporter;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.run.AnalysisCommand;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Runnable class, see command description.
 */
@CommandLine.Command(
		name = "extractInfectionsGraph",
		description = "Extracts graph of all infections."
)
public class ExtractInfectionGraph implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(ExtractInfectionGraph.class);

	@CommandLine.Option(names = "--output", defaultValue = "./output/")
	private Path output;

	public static void main(String[] args) {
		System.exit(new CommandLine(new ExtractInfectionGraph()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		if (!Files.exists(output)) {
			log.error("Output path {} does not exist.", output);
			return 2;
		}

		AnalysisCommand.forEachScenario(output, path -> {
			try {
				readScenario(path);
			} catch (Exception e) {
				log.warn("Could not process scenario: {}", path, e);
			}
		});

		log.info("All done");

		return 0;
	}

	private void readScenario(Path scenario) throws IOException {

		String id = AnalysisCommand.getScenarioPrefix(scenario);

		Graph<String, CSVRecord> graph = new DefaultDirectedGraph<>(CSVRecord.class);

		Set<String> infected = new HashSet<>();

		try (BufferedReader reader = Files.newBufferedReader(scenario.resolve(id + "infectionEvents.txt"))) {
			CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader());

			for (CSVRecord record : parser) {

				String a = record.get("infector");
				String b = record.get("infected");

				infected.add(b);

				graph.addVertex(a);
				graph.addVertex(b);

				graph.addEdge(a, b, record);
			}
		}

		GraphMLExporter<String, CSVRecord> exporter = new GraphMLExporter<>();

		exporter.setVertexIdProvider(Function.identity());

		exporter.registerAttribute("source", GraphMLExporter.AttributeCategory.NODE, AttributeType.BOOLEAN);
		exporter.registerAttribute("facility", GraphMLExporter.AttributeCategory.EDGE, AttributeType.STRING);
		exporter.registerAttribute("infectionType", GraphMLExporter.AttributeCategory.EDGE, AttributeType.STRING);
		exporter.registerAttribute("groupSize", GraphMLExporter.AttributeCategory.EDGE, AttributeType.INT);
		exporter.registerAttribute("date", GraphMLExporter.AttributeCategory.EDGE, AttributeType.STRING);

		exporter.setVertexAttributeProvider(v -> Map.of(
				"source", new DefaultAttribute<>(!infected.contains(v), AttributeType.BOOLEAN)
		));

		exporter.setEdgeAttributeProvider(r -> Map.of(
				"facility", new DefaultAttribute<>(r.get("facility"), AttributeType.STRING),
				"infectionType", new DefaultAttribute<>(r.get("infectionType"), AttributeType.STRING),
				"groupSize", new DefaultAttribute<>(Integer.parseInt(r.get("groupSize")), AttributeType.INT),
				"date", new DefaultAttribute<>(r.get("date"), AttributeType.STRING)
				)
		);

		BufferedWriter bw = Files.newBufferedWriter(scenario.resolve(id + "post.infections.graphml"));

		exporter.exportGraph(graph, bw);
		bw.close();

	}
}

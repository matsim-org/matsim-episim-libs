package org.matsim.episim.analysis;

import com.google.common.base.Joiner;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.events.EpisimPersonStatusEventHandler;
import org.matsim.run.AnalysisCommand;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Runnable class, see command description.
 */
@CommandLine.Command(
		name = "extractInfectionsByAge",
		description = "Extract age distribution of infected people using events and population."
)
public class ExtractInfectionsByAge implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(ExtractInfectionsByAge.class);

	@CommandLine.Option(names = "--output", defaultValue = "./output/")
	private Path output;

	@CommandLine.Option(names = "--population", description = "Path to population file", required = true)
	private Path p;

	@CommandLine.Option(names = "--district", description = "District to filter for")
	private String district = null;

	@CommandLine.Option(names = "--attr", defaultValue = "microm:modeled:age", description = "Name of the age attribute")
	private String ageAttr;

	private Population population;

	public static void main(String[] args) {
		System.exit(new CommandLine(new ExtractInfectionsByAge()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		if (!Files.exists(output)) {
			log.error("Output path {} does not exist.", output);
			return 2;
		}

		population = PopulationUtils.readPopulation(this.p.toString());

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

	private void readScenario(Path path) throws IOException {

		String id = AnalysisCommand.getScenarioPrefix(path);
		BufferedWriter bw = Files.newBufferedWriter(path.resolve(id + "post.infectionsByAge.txt"));

		bw.write("date\t");
		bw.write(Joiner.on("\t").join(IntStream.range(1, 100).boxed().toArray()));

		Map<String, Int2IntMap> infections = new LinkedHashMap<>();

		CSVParser parser = new CSVParser(Files.newBufferedReader(path.resolve((id + "infectionEvents.txt"))),
				CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader());

		for (CSVRecord record : parser) {

			String infected = record.get("infected");
			Person p = population.getPersons().get(Id.createPersonId(infected));

			if (district != null) {
				if (!district.equals(p.getAttributes().getAttribute("district")))
					continue;
			}

			Int2IntMap date = infections.computeIfAbsent(record.get("date"), (k) -> new Int2IntOpenHashMap());
			Object age = p.getAttributes().getAttribute(ageAttr);
			if (age != null)
				date.merge((int) age, 1, Integer::sum);
			else
				log.warn("Person {} without age attribute, has {}", p, p.getAttributes().getAsMap().keySet());
		}

		for (Map.Entry<String, Int2IntMap> e : infections.entrySet()) {

			bw.write("\n");
			bw.write(e.getKey());
			bw.write("\t");

			Int2IntMap day = e.getValue();
			bw.write(Joiner.on("\t").join(IntStream.range(1, 100).map(day::get).boxed().toArray()));
		}

		bw.close();

		// Writer for other disease states
		Map<EpisimPerson.DiseaseStatus, BufferedWriter> writer = new EnumMap<>(EpisimPerson.DiseaseStatus.class);

		writer.put(EpisimPerson.DiseaseStatus.seriouslySick, Files.newBufferedWriter(path.resolve(id + "post.seriouslySickByAge.txt")));
		writer.put(EpisimPerson.DiseaseStatus.critical, Files.newBufferedWriter(path.resolve(id + "post.criticalByAge.txt")));

		Map<EpisimPerson.DiseaseStatus, Int2IntMap> counts = new EnumMap<>(EpisimPerson.DiseaseStatus.class);
		writer.keySet().forEach(k -> counts.put(k, new Int2IntOpenHashMap()));

		for (BufferedWriter w : writer.values()) {
			w.write("day\t");
			w.write(Joiner.on("\t").join(IntStream.range(1, 100).boxed().toArray()));
		}

		Runnable writeRow = () -> {
			for (Map.Entry<EpisimPerson.DiseaseStatus, BufferedWriter> e : writer.entrySet()) {

				Int2IntMap day = counts.get(e.getKey());
				try {
					e.getValue().write(Joiner.on("\t").join(IntStream.range(1, 100).map(day::get).boxed().toArray()));
				} catch (IOException exc) {
					log.error(exc);
				}
				counts.get(e.getKey()).clear();
			}
		};

		AtomicInteger day = new AtomicInteger(0);
		AnalysisCommand.forEachEvent(path,
				d -> {

					if (day.getAndIncrement() > 0)
						writeRow.run();

					for (BufferedWriter value : writer.values()) {
						try {
							value.write("\n");
							value.write(String.valueOf(day.get()));
							value.write("\t");
						} catch (IOException e) {
							log.error(e);
						}
					}
				}
				,
				(EpisimPersonStatusEventHandler) e -> {

					if (!counts.containsKey(e.getDiseaseStatus()))
						return;

					Person p = population.getPersons().get(e.getPersonId());
					Object age = p.getAttributes().getAttribute(ageAttr);
					if (age != null)
						counts.get(e.getDiseaseStatus()).merge((int) age, 1, Integer::sum);
				}
		);

		writeRow.run();

		for (BufferedWriter w : writer.values()) {
			w.close();
		}

		log.info("Finished scenario: {}", path);
	}
}

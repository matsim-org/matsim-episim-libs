package org.matsim.episim.analysis;

import com.google.common.base.Joiner;
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
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
import java.time.LocalDate;
import java.util.*;
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

	@CommandLine.Option(names = "--district", description = "District to filter for", defaultValue = "Berlin")
	private String district = null;

	@CommandLine.Option(names = "--detailed", description = "Write detailed stats for multiple infection status, non-aggregated")
	private boolean detailed = false;

	@CommandLine.Option(names = "--age-groups", description = "Age groups as list of bin edges")
	private List<Integer> ageGroups = List.of(0, 5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90);

	@CommandLine.Option(names = "--attr", defaultValue = "microm:modeled:age", description = "Name of the age attribute")
	private String ageAttr;

	private Population population;
	private Int2IntSortedMap groupSizes;

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
		groupSizes = new Int2IntAVLTreeMap();

		// Aggregate by age group
		for (Person p : population.getPersons().values()) {
			if (district != null) {
				if (!district.equals(p.getAttributes().getAttribute("district")))
					continue;
			}

			groupSizes.merge(getAgeGroup((Integer) p.getAttributes().getAttribute(ageAttr)), 1, Integer::sum);
		}

		log.info("Age groups: {}", groupSizes);

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

			Int2IntMap date = infections.computeIfAbsent(calcDate(record), (k) -> new Int2IntOpenHashMap());
			Object age = p.getAttributes().getAttribute(ageAttr);
			if (age != null)
				date.merge((int) age, 1, Integer::sum);
			else
				log.warn("Person {} without age attribute, has {}", p, p.getAttributes().getAsMap().keySet());
		}

		BufferedWriter bw = Files.newBufferedWriter(path.resolve(id + "post.incidenceByAge.tsv"));
		bw.write("date\t");

		for (int i = 0; i < ageGroups.size() - 1; i++) {
			bw.write(String.format("%d-%d", ageGroups.get(i), +ageGroups.get(i + 1) - 1));
			bw.write("\t");
		}

		bw.write(String.format("%d+", ageGroups.get(ageGroups.size() - 1)));

		for (Map.Entry<String, Int2IntMap> e : infections.entrySet()) {

			bw.write("\n");
			bw.write(e.getKey());
			bw.write("\t");

			Int2IntMap day = e.getValue();

			int group = 0;
			double aggr = 0;

			for (int i = 0; i < 100; i++) {

				int g = getAgeGroup(i);
				if (group == g) {
					aggr += day.get(i);
				} else {

					bw.write(calcIncidence(group, aggr));
					bw.write('\t');

					group = g;
					aggr = 0;
				}
			}

			bw.write(calcIncidence(group, aggr));
		}

		bw.close();

		if (!detailed)
			return;

		bw = Files.newBufferedWriter(path.resolve(id + "post.infectionsByAge.txt"));
		bw.write("date\t");
		bw.write(Joiner.on("\t").join(IntStream.range(1, 100).boxed().toArray()));

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

	/**
	 * Calculate incidence per 100k.
	 */
	private String calcIncidence(int group, double aggr) {

		double scale = 100_000d / groupSizes.get(group);
		return String.valueOf(aggr * scale);
	}

	/**
	 * Calculate date of infection from record.
	 */
	private String calcDate(CSVRecord record) {
		LocalDate date = LocalDate.parse(record.get("date"));

		// if not monday, go to next monday
		int day = date.getDayOfWeek().getValue();
		if (day != 1) {
			date = date.plusDays(8 - day);
		}

		return date.toString();
	}

	/**
	 * Get lower bound of age group.
	 */
	private int getAgeGroup(int age) {

		int idx = Collections.binarySearch(ageGroups, age);

		if (idx >= 0)
			return ageGroups.get(idx);


		return ageGroups.get(Math.abs(idx) - 2);
	}

}

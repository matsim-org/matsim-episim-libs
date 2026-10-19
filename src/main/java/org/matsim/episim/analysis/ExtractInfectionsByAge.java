package org.matsim.episim.analysis;

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Runnable class, see command description. As post-processing it uses the population of the injected scenario.
 * <p>
 * {@code post.incidenceByAge.tsv} has one row for every complete week of the run (Tuesday to Monday, labelled with the
 * Monday), also for weeks without infections, so that runs which only differ by seed can be averaged row by row.
 */
@CommandLine.Command(
		name = "extractInfectionsByAge",
		description = "Extract age distribution of infected people using events and population."
)
public class ExtractInfectionsByAge implements OutputAnalysis {

	private static final Logger log = LogManager.getLogger(ExtractInfectionsByAge.class);

	@CommandLine.Option(names = "--output", defaultValue = "./output/")
	private Path output;

	@CommandLine.Option(names = "--population", description = "Path or URL of the population file, required on the command line")
	private String p;

	@CommandLine.Option(names = "--district", description = "District to filter for", required = true)
	private String district = null;

	@CommandLine.Option(names = "--detailed", description = "Write detailed stats for multiple infection status, non-aggregated")
	private boolean detailed = false;

	@CommandLine.Option(names = "--age-groups", description = "Age groups as list of bin edges")
	private List<Integer> ageGroups = List.of(0, 5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90);

	@CommandLine.Option(names = "--attr", defaultValue = "microm:modeled:age", description = "Name of the age attribute")
	private String ageAttr;

	@Inject(optional = true)
	private Scenario scenario;

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

		if (p == null) {
			log.error("--population is required on the command line.");
			return 2;
		}

		population = PopulationUtils.readPopulation(p);

		AnalysisCommand.forEachScenario(output, path -> {
			try {
				analyzeOutput(path);
			} catch (Exception e) {
				log.warn("Could not process scenario: {}", path, e);
			}
		});

		log.info("All done");

		return 0;
	}

	@Override
	public void analyzeOutput(Path path) throws IOException {

		if (population == null) {
			if (scenario != null)
				population = scenario.getPopulation();
			else if (p != null)
				population = PopulationUtils.readPopulation(p);
			else
				throw new IllegalStateException("No scenario injected and no --population given");
		}

		if (groupSizes == null)
			groupSizes = countGroupSizes();

		String id = AnalysisCommand.getScenarioPrefix(path);
		Map<LocalDate, Int2IntMap> infections = new TreeMap<>();
		int withoutAge = 0;

		try (CSVParser parser = new CSVParser(Files.newBufferedReader(path.resolve((id + "infectionEvents.txt"))),
				CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader())) {

			for (CSVRecord record : parser) {

				Person p = population.getPersons().get(Id.createPersonId(record.get("infected")));

				if (p == null || !district.equals(p.getAttributes().getAttribute("district")))
					continue;

				Object age = p.getAttributes().getAttribute(ageAttr);
				if (age == null) {
					withoutAge++;
					continue;
				}

				infections.computeIfAbsent(calcDate(LocalDate.parse(record.get("date"))), (k) -> new Int2IntOpenHashMap())
						.merge((int) age, 1, Integer::sum);
			}
		}

		if (withoutAge > 0)
			log.warn("{} infected persons without attribute {} in {}", withoutAge, ageAttr, path);

		List<LocalDate> weeks = completeWeeks(path.resolve(id + "infections.txt"));

		BufferedWriter bw = Files.newBufferedWriter(path.resolve(id + "post.incidenceByAge.tsv"));
		bw.write("date\t");

		for (int i = 0; i < ageGroups.size() - 1; i++) {
			bw.write(String.format("%d-%d", ageGroups.get(i), +ageGroups.get(i + 1) - 1));
			bw.write("\t");
		}

		bw.write(String.format("%d+", ageGroups.get(ageGroups.size() - 1)));

		for (LocalDate week : weeks) {

			Int2IntMap byGroup = new Int2IntOpenHashMap();
			for (Int2IntMap.Entry e : infections.getOrDefault(week, Int2IntMaps.EMPTY_MAP).int2IntEntrySet())
				byGroup.merge(getAgeGroup(e.getIntKey()), e.getIntValue(), Integer::sum);

			bw.write("\n");
			bw.write(week.toString());

			for (int group : ageGroups) {
				bw.write("\t");
				bw.write(calcIncidence(group, byGroup.get(group)));
			}
		}

		bw.close();

		if (!detailed)
			return;

		bw = Files.newBufferedWriter(path.resolve(id + "post.infectionsByAge.txt"));
		bw.write("date\t");
		bw.write(Joiner.on("\t").join(IntStream.range(1, 100).boxed().toArray()));

		for (Map.Entry<LocalDate, Int2IntMap> e : infections.entrySet()) {

			bw.write("\n");
			bw.write(e.getKey().toString());
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
				false, (EpisimPersonStatusEventHandler) e -> {

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
	 * Number of persons in the district per age group.
	 */
	private Int2IntSortedMap countGroupSizes() {
		Int2IntSortedMap sizes = new Int2IntAVLTreeMap();

		for (Person p : population.getPersons().values()) {
			if (!district.equals(p.getAttributes().getAttribute("district")))
				continue;

			Object age = p.getAttributes().getAttribute(ageAttr);
			if (age != null)
				sizes.merge(getAgeGroup((int) age), 1, Integer::sum);
		}

		log.info("Age groups: {}", sizes);
		return sizes;
	}

	/**
	 * Labels of all weeks whose seven days lie within the simulated days listed in {@code infections.txt}.
	 */
	private static List<LocalDate> completeWeeks(Path infectionsFile) throws IOException {
		LocalDate first = null;
		LocalDate last = null;

		try (CSVParser parser = new CSVParser(Files.newBufferedReader(infectionsFile),
				CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader())) {
			for (CSVRecord record : parser) {
				LocalDate date = LocalDate.parse(record.get("date"));
				if (first == null || date.isBefore(first)) first = date;
				if (last == null || date.isAfter(last)) last = date;
			}
		}

		List<LocalDate> weeks = new ArrayList<>();
		if (first == null)
			return weeks;

		LocalDate lastWeek = last.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		for (LocalDate week = calcDate(first.plusDays(6)); !week.isAfter(lastWeek); week = week.plusWeeks(1))
			weeks.add(week);

		return weeks;
	}

	/**
	 * Calculate incidence per 100k.
	 */
	private String calcIncidence(int group, double aggr) {

		int size = groupSizes.get(group);
		if (size == 0)
			return "NaN";

		return String.valueOf(aggr * 100_000d / size);
	}

	/**
	 * Week of an infection date: the date itself if it is a Monday, else the next Monday.
	 */
	private static LocalDate calcDate(LocalDate date) {
		return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
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

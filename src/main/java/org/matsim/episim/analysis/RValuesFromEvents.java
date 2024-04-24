/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.episim.analysis;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.events.EpisimInfectionEventHandler;
import org.matsim.episim.events.EpisimPersonStatusEvent;
import org.matsim.episim.events.EpisimPersonStatusEventHandler;
import org.matsim.run.AnalysisCommand;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;


/**
 * @author smueller
 * Calculates R values for all runs in given directory, dated on day of switching to contagious
 * Output is written to rValues.txt in the working directory
 */
@CommandLine.Command(
		name = "calculateRValues",
		description = "Calculate R values summaries"
)
public class RValuesFromEvents implements OutputAnalysis {

	private static final Logger log = LogManager.getLogger(RValuesFromEvents.class);

	/**
	 * Activity types used by this analysis.
	 */
	private static final List<String> ACTIVITY_TYPES = List.of(
			"home", "leisure", "leisPrivate", "leisPublic", "schools", "day care", "university", "work&business", "pt", "other"
	);

	@CommandLine.Option(names = "--output", defaultValue = "./output/")
	private Path output;

	@CommandLine.Option(names = "--start-date", defaultValue = "2020-02-24")
	private LocalDate startDate;


	public static void main(String[] args) {
		System.exit(new CommandLine(new RValuesFromEvents()).execute(args));
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

		log.info("Done");

		return 0;
	}

	@Override
	public void analyzeOutput(Path output) throws IOException {

		String id = AnalysisCommand.getScenarioPrefix(output);

		InfectionsHandler infHandler = new InfectionsHandler();
		RHandler rHandler = new RHandler();

		List<String> eventFiles = AnalysisCommand.forEachEvent(output, s -> {
		}, false, infHandler, rHandler);

		BufferedWriter bw = Files.newBufferedWriter(output.resolve(id + "infectionsPerActivity.txt"));
		bw.write("day\tdate\tactivity\tinfections\tinfectionsShare\tscenario");

		int rollingAveragae = 3;
		for (int i = 0 + rollingAveragae; i <= eventFiles.size() - rollingAveragae; i++) {
			for (Entry<String, Int2IntMap> e : infHandler.infectionsPerActivity.entrySet()) {
				if (!e.getKey().equals("total")) {
					int infections = 0;
					int totalInfections = 0;
					double infectionsShare = 0.;
					for (int j = i - rollingAveragae; j <= i + rollingAveragae; j++) {
						int infectionsDay = 0;
						int totalInfectionsDay = 0;

						if (e.getValue().containsKey(j))
							infectionsDay = e.getValue().get(j);

						if (infHandler.infectionsPerActivity.get("total").containsKey(j))
							totalInfectionsDay = infHandler.infectionsPerActivity.get("total").get(j);

						infections = infections + infectionsDay;
						totalInfections = totalInfections + totalInfectionsDay;
					}
					if (startDate.plusDays(i).getDayOfWeek() == DayOfWeek.THURSDAY) {
						infectionsShare = (double) infections / totalInfections;
						bw.write("\n" + i + "\t" + startDate.plusDays(i).toString() + "\t" + e.getKey() + "\t" + (double) infections / (2 * rollingAveragae + 1) + "\t" + infectionsShare);
					}
				}
			}
		}

		bw.close();

		bw = Files.newBufferedWriter(output.resolve(id + "rValues.txt"));
		bw.write("day\tdate\trValue\tnewContagious\tscenario\t");
		bw.write(AnalysisCommand.TSV.join(ACTIVITY_TYPES));

		for (int i = 0; i <= eventFiles.size(); i++) {
			int noOfInfectors = 0;
			// infected persons per activity
			Object2IntMap<String> noOfInfected = new Object2IntOpenHashMap<>();
			for (InfectedPerson ip : rHandler.getInfected()) {
				if (ip.contagiousDay == i) {
					noOfInfectors++;
					ip.noOfInfected.forEach((k, v) -> noOfInfected.mergeInt(k, v, Integer::sum));
				}
			}

			double r = noOfInfectors == 0 ? 0 : (double) noOfInfected.getInt("total") / noOfInfectors;

			String join = "\n" + AnalysisCommand.TSV.join(
					i, startDate.plusDays(i).toString(), r, noOfInfectors, output.getFileName()
			) + "\t";

			int finalNoOfInfectors = noOfInfectors;
			join += AnalysisCommand.TSV.join(ACTIVITY_TYPES.stream()
					.map(k -> finalNoOfInfectors == 0 ? 0 : (double) noOfInfected.getInt(k) / finalNoOfInfectors)
					.collect(Collectors.toList())
			);

			bw.write(join);
		}

		bw.close();

		log.info("Calculated results for scenario {}", output);

	}

	private static class InfectedPerson {

		private final String id;
		private final Object2IntMap<String> noOfInfected = new Object2IntOpenHashMap<>();
		private int contagiousDay = 0;

		InfectedPerson(String id) {
			this.id = id;
		}

		void increaseNoOfInfectedByOne(String infectionType) {
			noOfInfected.mergeInt("total", 1, Integer::sum);
			noOfInfected.mergeInt(infectionType, 1, Integer::sum);
		}
	}

	private static class RHandler implements EpisimPersonStatusEventHandler, EpisimInfectionEventHandler {

		private final Set<String> activityTypes = new TreeSet<>();
		private final Map<String, InfectedPerson> infectedPersons = new LinkedHashMap<>();

		/**
		 * If a person is infected another time it will be removed from {@link #infectedPersons} and put here.
		 */
		private final List<InfectedPerson> handledInfections = new ArrayList<>();

		/**
		 * All infected persons, where a persons can occur multiple times.
		 */
		private Iterable<InfectedPerson> getInfected() {
			return Iterables.concat(infectedPersons.values(), handledInfections);
		}

		@Override
		public void handleEvent(EpisimInfectionEvent event) {
			String infectorId = event.getInfectorId().toString();
			InfectedPerson infector = infectedPersons.computeIfAbsent(infectorId, InfectedPerson::new);


			String activityType = getActivityType(event.getInfectionType());
			activityTypes.add(activityType);
			infector.increaseNoOfInfectedByOne(activityType);
		}

		@Override
		public void handleEvent(EpisimPersonStatusEvent event) {

			if (event.getDiseaseStatus() == DiseaseStatus.contagious) {

				int day = (int) (event.getTime() / 86400);
				String personId = event.getPersonId().toString();

				InfectedPerson person = infectedPersons.computeIfAbsent(personId, InfectedPerson::new);

				// a person is infected another time
				if (person.contagiousDay > 0 && person.contagiousDay != day) {
					handledInfections.add(infectedPersons.remove(personId));

					person = infectedPersons.computeIfAbsent(personId, InfectedPerson::new);
				}

				person.contagiousDay = day;
			}
		}
	}

	private static class InfectionsHandler implements EpisimInfectionEventHandler {

		private final Map<String, Int2IntMap> infectionsPerActivity = new TreeMap<>();

		@Override
		public void handleEvent(EpisimInfectionEvent event) {
			String infectionType = getActivityType(event.getInfectionType());

			int day = (int) event.getTime() / 86400;

			infectionsPerActivity.computeIfAbsent("total", k -> new Int2IntOpenHashMap())
					.merge(day, 1, Integer::sum);

			infectionsPerActivity.computeIfAbsent(infectionType, k -> new Int2IntOpenHashMap())
					.merge(day, 1, Integer::sum);

		}
	}

	/**
	 * Compute group of infection type.
	 */
	private static String getActivityType(String infectionType) {

		String activityType;
//			if (infectionType.endsWith("educ_higher")) infectionType = "edu_higher";
//			else if (infectionType.endsWith("educ_other")) infectionType = "edu_other";
//			else if (infectionType.endsWith("educ_kiga")) infectionType = "edu_kiga";
//			else if (infectionType.endsWith("educ_primary") || infectionType.endsWith("educ_secondary") || infectionType.endsWith("educ_tertiary")) infectionType = "edu_school";
		if (infectionType.endsWith("educ_primary") || infectionType.endsWith("educ_secondary") || infectionType.endsWith("educ_tertiary") || infectionType.endsWith("educ_other"))
			activityType = "schools";
		else if (infectionType.endsWith("educ_higher")) activityType = "university";
		else if (infectionType.endsWith("educ_kiga")) activityType = "day care";
		else if (infectionType.endsWith("leisure")) activityType = "leisure";
		else if (infectionType.endsWith("leisPublic")) activityType = "leisPublic";
		else if (infectionType.endsWith("leisPrivate")) activityType = "leisPrivate";
		else if (infectionType.endsWith("work") || infectionType.endsWith("business")) activityType = "work&business";
		else if (infectionType.endsWith("home")) activityType = "home";
		else if (infectionType.startsWith("pt")) activityType = "pt";
		else activityType = "other";

		return activityType;
	}

}





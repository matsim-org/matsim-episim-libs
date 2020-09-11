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

import org.apache.commons.lang.ArrayUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.events.*;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
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
public class SMRValuesFromEvents implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(SMRValuesFromEvents.class);

	@CommandLine.Option(names = "--output", defaultValue = "./output/")
	private Path output;

	@CommandLine.Option(names = "--start-date", defaultValue = "2020-02-16")
	private LocalDate startDate;


	public static void main(String[] args) {
		System.exit(new CommandLine(new SMRValuesFromEvents()).execute(args));
	}

	@Override
	public Integer call() throws Exception {
		Configurator.setLevel("org.matsim.core.config", Level.WARN);
		Configurator.setLevel("org.matsim.core.controler", Level.WARN);
		Configurator.setLevel("org.matsim.core.events", Level.WARN);
		Configurator.setLevel("org.matsim.core.utils", Level.WARN);

		Set<Path> scenarios = new LinkedHashSet<>();

		if (!Files.exists(output)) {
			log.error("Output path {} does not exist.", output);
			return 1;
		}

		Files.list(output)
				.filter(p -> Files.isDirectory(p))
				.forEach(scenarios::add);

		log.info("Read " + scenarios.size() + " files");
		log.info(scenarios);
		
		BufferedWriter rValues = Files.newBufferedWriter(output.resolve("rValues.txt"));
		rValues.write("day\tdate\trValue\tnewContagious\tscenario");
		
		BufferedWriter infectionsPerActivity = Files.newBufferedWriter(output.resolve("infectionsPerActivity.txt"));
		infectionsPerActivity.write("day\tdate\tactivity\tinfections\tscenario");

		scenarios.parallelStream().forEach(scenario -> {
			try {
				calcValues(scenario, rValues, infectionsPerActivity);
			} catch (IOException e) {
				log.error("Failed processing {}", scenario, e);
			}
		});
		
		rValues.close();
		infectionsPerActivity.close();
		
		log.info("done");
		
		return 0;
	}

	private void calcValues(Path scenario, BufferedWriter rValues, BufferedWriter infectionsPerActivity) throws IOException {

		Path eventFolder = scenario.resolve("events");
		if (!Files.exists(eventFolder)) {
			log.warn("No events found at {}", eventFolder);
			return;
		}

		Optional<Path> config = Files.find(scenario, 1, (path, basicFileAttributes) -> path.toString().contains("config")).findFirst();
		if (config.isEmpty()) {
			log.warn("Could not find config for {}", scenario);
			return;
		}

		String name = config.get().getFileName().toString();
		String id = name.substring(0, name.indexOf('.'));

		EventsManager manager = EventsUtils.createEventsManager();
		InfectionsHandler infHandler = new InfectionsHandler();
		RHandler rHandler = new RHandler();

		manager.addHandler(infHandler);
		manager.addHandler(rHandler);

		List<Path> eventFiles = Files.list(eventFolder)
				.filter(p -> p.getFileName().toString().contains("xml.gz"))
				.collect(Collectors.toList());

		for (Path p : eventFiles) {
			try {
				new EpisimEventsReader(manager).readFile(p.toString());
			} catch (UncheckedIOException e) {
				log.warn("Caught UncheckedIOException. Could not read file {}", p);
			}
		}


		BufferedWriter bw = Files.newBufferedWriter(scenario.resolve(id + ".infectionsPerActivity.txt"));
		bw.write("day\tdate\tactivity\tinfections\tscenario");

		for (int i = 0; i <= eventFiles.size(); i++) {
			for (Entry<String, HashMap<Integer, Integer>> e : infHandler.infectionsPerActivity.entrySet()) {
				if (e.getKey().equals("pt") || e.getKey().equals("total")|| e.getKey().equals("edu_kiga") || e.getKey().equals("edu_school") || e.getKey().equals("leisure") || e.getKey().equals("work&business") || e.getKey().equals("home") ) {
					int infections = 0;
					if (e.getValue().get(i) != null) infections = e.getValue().get(i);
					bw.write("\n" + i + "\t" + startDate.plusDays(i).toString() + "\t" + e.getKey() + "\t" + infections + "\t" + scenario.getFileName());
					if (infections != 0) {
						infectionsPerActivity.write("\n" + i + "\t" + startDate.plusDays(i).toString() + "\t" + e.getKey() + "\t" + infections + "\t" + scenario.getFileName());
					}
				}
			}
		}
		
		infectionsPerActivity.flush();
		bw.close();

		bw = Files.newBufferedWriter(scenario.resolve(id + ".rValues.txt"));
		bw.write("day\tdate\trValue\tnewContagious\tscenario");

		for (int i = 0; i <= eventFiles.size(); i++) {
			int noOfInfectors = 0;
			int noOfInfected = 0;
			for (InfectedPerson ip : rHandler.infectedPersons.values()) {
				if (ip.getContagiousDay() == i) {
					noOfInfectors++;
					noOfInfected = noOfInfected + ip.getNoOfInfected();
				}
			}
			double r = 0;
			if (noOfInfectors != 0) r = (double) noOfInfected / noOfInfectors;
			bw.write("\n" + i + "\t" + startDate.plusDays(i).toString() + "\t" + r + "\t" + noOfInfectors + "\t" + scenario.getFileName());
//			if (r != 0) {
				rValues.write("\n" + i + "\t" + startDate.plusDays(i).toString() + "\t" + r + "\t" + noOfInfectors + "\t" + scenario.getFileName());
//			}
		}
		rValues.flush();
		bw.close();

		log.info("Calculated results for scenario {}", scenario);

	}

	private static class InfectedPerson {

		private String id;
		private int noOfInfected;
		private int contagiousDay;

		InfectedPerson(String id) {
			this.id = id;
			this.noOfInfected = 0;
		}

		String getId() {
			return id;
		}

		void setId(String id) {
			this.id = id;
		}

		int getNoOfInfected() {
			return noOfInfected;
		}

		void increaseNoOfInfectedByOne() {
			this.noOfInfected++;
		}

		int getContagiousDay() {
			return contagiousDay;
		}

		void setContagiousDay(int contagiousDay) {
			this.contagiousDay = contagiousDay;
		}

	}

	private static class RHandler implements EpisimPersonStatusEventHandler, EpisimInfectionEventHandler {

		private final Map<String, InfectedPerson> infectedPersons = new LinkedHashMap<>();

		@Override
		public void handleEvent(EpisimInfectionEvent event) {
			String infectorId = event.getInfectorId().toString();
			InfectedPerson infector = infectedPersons.computeIfAbsent(infectorId, InfectedPerson::new);
			infector.increaseNoOfInfectedByOne();
		}

		@Override
		public void handleEvent(EpisimPersonStatusEvent event) {

			if (event.getDiseaseStatus() == DiseaseStatus.contagious) {

				String personId = event.getPersonId().toString();
				InfectedPerson person = infectedPersons.computeIfAbsent(personId, InfectedPerson::new);
				person.setContagiousDay((int) event.getTime() / 86400);
			}
		}
	}

	private static class InfectionsHandler implements EpisimInfectionEventHandler {

		private final Map<String, HashMap<Integer, Integer>> infectionsPerActivity = new LinkedHashMap<>();


		@Override
		public void handleEvent(EpisimInfectionEvent event) {
			String infectionType = event.getInfectionType();
//			if (infectionType.startsWith("educ_higher")) infectionType = "edu_higher";
//			if (infectionType.startsWith("educ_other")) infectionType = "edu_other";
			if (infectionType.startsWith("educ_kiga")) infectionType = "edu_kiga";
			if (infectionType.startsWith("educ_primary") || infectionType.startsWith("educ_secondary") || infectionType.startsWith("educ_tertiary")) infectionType = "edu_school";			
			if (infectionType.startsWith("leisure")) infectionType = "leisure";
			if (infectionType.startsWith("work") || infectionType.startsWith("business")) infectionType = "work&business";
			if (infectionType.startsWith("home")) infectionType = "home";


			if (!infectionsPerActivity.containsKey("total")) infectionsPerActivity.put("total", new HashMap<>());
			if (!infectionsPerActivity.containsKey(infectionType)) infectionsPerActivity.put(infectionType, new HashMap<>());

			HashMap<Integer, Integer> infectionsPerDay = infectionsPerActivity.get(infectionType);
			HashMap<Integer, Integer> infectionsPerDayTotal = infectionsPerActivity.get("total");

			int day = (int) event.getTime() / 86400;

			if (!infectionsPerDay.containsKey(day)) infectionsPerDay.put(day, 1);
			else infectionsPerDay.replace(day, infectionsPerDay.get(day) + 1);

			if (!infectionsPerDayTotal.containsKey(day)) infectionsPerDayTotal.put(day, 1);
			else infectionsPerDayTotal.replace(day, infectionsPerDayTotal.get(day) + 1);

		}
	}

}





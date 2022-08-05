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

import com.google.inject.Inject;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
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
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * @author jakobrehmann
 * Calculates secondary attack rate for all runs in given directory
 * Output is written to secondaryAttackRate.txt in the working directory
 */

@CommandLine.Command(
		name = "calculateSecondaryAttackRate",
		description = "Calculate Secondary Attack Rate"
)
public class SecondaryAttackRateFromEvents implements OutputAnalysis {

	private static final Logger log = LogManager.getLogger(SecondaryAttackRateFromEvents.class);

	@CommandLine.Option(names = "--output", defaultValue = "./output/")
//	@CommandLine.Option(names = "--output", defaultValue = "../public-svn/matsim/scenarios/countries/de/episim/battery/jakob/2022-07-22/1-calibration/output")
	private Path output;

	@CommandLine.Option(names = "--start-date", defaultValue = "2020-02-24")
	private LocalDate startDate;

	@CommandLine.Option(names = "--input", defaultValue = "/scratch/projects/bzz0020/episim-input")
//	 @CommandLine.Option(names = "--input", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input")
	private String input;

	@CommandLine.Option(names = "--population-file", defaultValue = "/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz")
	private String populationFile;


	@Inject
	private Scenario scenario;

	private Population population;


	public static void main(String[] args) {
		System.exit(new CommandLine(new SecondaryAttackRateFromEvents()).execute(args));
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

		population = PopulationUtils.readPopulation(input + populationFile);

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

		if (scenario != null)
			population = scenario.getPopulation();


		Map<String, String> personToHousehold = new HashMap<>();
		for (Person person : population.getPersons().values()) {
			if(person.getAttributes().getAttribute("district").equals("Köln"))
				personToHousehold.put(person.getId().toString(), person.getAttributes().getAttribute("homeId").toString());
		}

		Map<String, Long> personCountPerHousehold = personToHousehold.values().stream()
				.collect(Collectors.groupingBy(Function.identity(),
						Collectors.counting()));


		SecondaryAttackRateHandler hhHandler = new SecondaryAttackRateHandler(personToHousehold);

		List<String> eventFiles = AnalysisCommand.forEachEvent(output, s -> {
		}, false, hhHandler);

		Map<Integer, Map<String, Integer>> itToHouseholdToInfections = hhHandler.getItToHouseholdToInfections();

		BufferedWriter bw = Files.newBufferedWriter(output.resolve(id + "secondaryAttackRate.txt"));
		bw.write("day\tdate\trate");

		int rollingAverage = 3;
		for (int i = 0 + rollingAverage; i <= eventFiles.size() - rollingAverage; i++) {

			if (startDate.plusDays(i).getDayOfWeek() != DayOfWeek.THURSDAY) {
				continue;
			}

			long totalHhInfectionsForWeek = 0;
			long totalHhMemberCnt = 0;
			for (int j = i - rollingAverage; j <= i + rollingAverage; j++) {

				if (itToHouseholdToInfections.containsKey(j)) {
					for (Entry<String, Integer> hhToInfections : itToHouseholdToInfections.get(j).entrySet()) {
						totalHhInfectionsForWeek += hhToInfections.getValue();
						totalHhMemberCnt = totalHhMemberCnt + personCountPerHousehold.get(hhToInfections.getKey()) - 1;
					}
				}
			}
			double secondaryAttackRate = (double) totalHhInfectionsForWeek / totalHhMemberCnt;
			bw.write("\n" + i + "\t" + startDate.plusDays(i).toString() + "\t" + secondaryAttackRate);
			}
		bw.close();
		log.info("Calculated results for scenario {}", output);
	}

	private static class SecondaryAttackRateHandler implements EpisimPersonStatusEventHandler {

		/**
		 * personID to hh Id
		 */
		Map<String, String> personToHousehold;

		/**
		 * keys: all agents who are currently the index agent for their household
		 * values: day at which they became contagious
		 */
		Map<String, Integer> indexPersons = new HashMap<>();

		/**
		 * keys: all households that currently contain an index agent.
		 * values: count of how many agents in index agent's hh are infected before index agent recovers
		 */
		Map<String, Integer> householdToInfections = new HashMap<>();

		/**
		 * This map aggregates all information required to calculate secondary attack rate. Households are
		 * entered here once the index agent recovers, but are backdated to the day they become contagious.
		 * keys: iterations at which index agents become contagious
		 * values: map containing household id and how many house mates were infected
		 */
		Map<Integer, Map<String, Integer>> itToHouseholdToInfections = new HashMap<>();


		public SecondaryAttackRateHandler(Map<String, String> personToHousehold) {
			this.personToHousehold = personToHousehold;
		}

		public Map<Integer, Map<String, Integer>> getItToHouseholdToInfections() {
			return itToHouseholdToInfections;
		}

		@Override
		public void handleEvent(EpisimPersonStatusEvent event) {

			int day = (int) (event.getTime() / 86400);
			String personId = event.getPersonId().toString();

			// if agent does not live in district being evaluated
			if (!personToHousehold.containsKey(personId)) {
				return;
			}


			String hhId = personToHousehold.get(personId);

			if (event.getDiseaseStatus() == DiseaseStatus.contagious) {
				if (indexPersons.containsKey(personId)) {
					throw new RuntimeException("person was already contagious, but was never removed from indexPersons when recovered");
				}

				//todo: if they become contagious on the same day, don't count that case?
				if (householdToInfections.containsKey(hhId) ) {
					householdToInfections.merge(hhId, 1, Integer::sum);
				} else {
					indexPersons.put(personId, day);
					householdToInfections.put(hhId, 0);
				}
			}

			if (event.getDiseaseStatus() == DiseaseStatus.recovered) {
				if (indexPersons.containsKey(personId)) {

					// update global map with household infections for index agent who has just recovered (back-dated to day contagious)
					Integer dayContagious = indexPersons.get(personId);
					itToHouseholdToInfections.putIfAbsent(dayContagious, new HashMap<>());
					itToHouseholdToInfections.get(dayContagious).put(hhId, householdToInfections.get(hhId));

					// now that hh infections have been saved to global map, remove from temp maps.
					householdToInfections.remove(hhId);
					indexPersons.remove(personId);

				}
			}
		}

	}

}





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


import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.events.*;
import org.matsim.run.AnalysisCommand;
import org.matsim.utils.objectattributes.attributable.Attributes;

import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Callable;


/**
 * @author smueller
 * Calculates R values for all runs in given directory, dated on day of switching to contagious
 * Output is written to rValues.txt in the working directory
 */
@CommandLine.Command(
		name = "vacEff",
		description = "Calculate R values summaries"
)
public class InicidencePerStrain implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(InicidencePerStrain.class);

	@CommandLine.Option(names = "--output", defaultValue = "./output/211217/")
	private Path output;

	@CommandLine.Option(names = "--input", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input")
	private String input;

	@CommandLine.Option(names = "--population-file", defaultValue = "/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz")
	private String populationFile;

	@CommandLine.Option(names = "--start-date", defaultValue = "2020-02-24")
	private LocalDate startDate;


	public static void main(String[] args) {
		System.exit(new CommandLine(new InicidencePerStrain()).execute(args));
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
				calcValues(scenario);
			} catch (IOException e) {
				log.error("Failed processing {}", scenario, e);
			}
		});

		log.info("done");

		return 0;
	}


	private Population readPopulation() {
		Config config = ConfigUtils.createConfig();
		config.plans().setInputFile(input + populationFile);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		return scenario.getPopulation();
	}

	private void calcValues(Path scenario) throws IOException {

		Population population = readPopulation();
		initPopulation(population);

		String id = AnalysisCommand.getScenarioPrefix(scenario);

		BufferedWriter bw = Files.newBufferedWriter(scenario.resolve(id + "vaccineEff.txt"));
		bw.write("vacDate" + "\t" + "date" + "\t" + "period" + "\t" +  "vaccinatedInfected" + "\t" + "vaccinatedNotInfected" + "\t" + "controlGroupInfected" + "\t" + "controlGroupNotInfected" + "\t" + "efficacy" );
		bw.flush();

		Map<String, Map<LocalDate, Integer>> symptoms = new HashMap<>();
		Map<String, Map<LocalDate, Integer>> seriouslySick = new HashMap<>();

		Handler handler = new Handler(population, startDate, symptoms, seriouslySick);

		AnalysisCommand.forEachEvent(scenario, s -> {}, false, handler);

		int persons = 0;
		for (Person p : population.getPersons().values()) {
			if (p.getAttributes().getAttribute("district").equals("Köln")) {
				persons++;
			}
		}

		System.out.print("date");
		for (String strain : symptoms.keySet()) {
			System.out.print("\t");
			System.out.print(strain);
		}
		System.out.println("");


		for (int i = 0; i<850; i++) {
			LocalDate date = startDate.plusDays(i);
			System.out.print(date);
			for (String strain : symptoms.keySet()) {
				System.out.print("\t");
				if (symptoms.get(strain).containsKey(date)) {
					int cases = symptoms.get(strain).get(date);
					System.out.print(cases / (persons / 100_000.0));
				}
				else {
					System.out.print("0.0");
				}
			}
			System.out.println("");
		}

//		for (int i = 0; i<850; i++) {
//			LocalDate date = startDate.plusDays(i);
//			System.out.print(date);
//			for (String strain : seriouslySick.keySet()) {
//				System.out.print("\t");
//				if (seriouslySick.get(strain).containsKey(date)) {
//					int cases = seriouslySick.get(strain).get(date);
//					System.out.print(cases / (persons / 100_000.0));
//				}
//				else {
//					System.out.print("0.0");
//				}
//			}
//			System.out.println("");
//		}

		bw.close();

		log.info("Calculated results for scenario {}", scenario);

	}



	private void initPopulation(Population population) {

		for (Person p : population.getPersons().values()) {
			Attributes attributes = p.getAttributes();
			attributes.putAttribute("strain", "");
		}

	}


	private static class Handler implements EpisimPersonStatusEventHandler, EpisimInfectionEventHandler, EpisimInitialInfectionEventHandler {

		private Population population;
		private LocalDate startDate;
		private Map<String, Map<LocalDate, Integer>> symptoms = new HashMap<>();
		private Map<String, Map<LocalDate, Integer>> seriouslySick = new HashMap<>();


		public Handler(Population population, LocalDate startDate, Map<String, Map<LocalDate, Integer>> symptoms, Map<String, Map<LocalDate, Integer>> seriouslySick) {
			this.population = population;
			this.startDate = startDate;
			this.symptoms = symptoms;
			this.seriouslySick = seriouslySick;
		}


		@Override
		public void handleEvent(EpisimInfectionEvent event) {
			Person person = this.population.getPersons().get(event.getPersonId());
			Attributes attr = person.getAttributes();
			attr.putAttribute("strain", event.getVirusStrain().toString());
		}


		@Override
		public void handleEvent(EpisimPersonStatusEvent event) {

			DiseaseStatus status = event.getDiseaseStatus();

			if (!(status == DiseaseStatus.showingSymptoms || status == DiseaseStatus.seriouslySick)) {
				return;
			}

			Person person = this.population.getPersons().get(event.getPersonId());
			Attributes attr = person.getAttributes();

			if (!attr.getAttribute("district").equals("Köln")) {
				return;
			}

			String strain = (String) attr.getAttribute("strain");
			if (strain == "") {
				System.out.println(status);
				return;
			}

			LocalDate date = startDate.plusDays( (int) (event.getTime() / 86_400));

			if (status == DiseaseStatus.showingSymptoms) {
				if (symptoms.containsKey(strain)) {
					if (symptoms.get(strain).containsKey(date)) {
						int oldValue = symptoms.get(strain).get(date);
						symptoms.get(strain).replace(date, oldValue+1);
					}
					else {
						symptoms.get(strain).put(date, 1);
					}
				}
				else {
					Map<LocalDate, Integer> map = new HashMap<>();
					map.put(date, 1);
					symptoms.put(strain, map);
				}
			}
			if (status == DiseaseStatus.seriouslySick) {
				if (seriouslySick.containsKey(strain)) {
					if (seriouslySick.get(strain).containsKey(date)) {
						int oldValue = seriouslySick.get(strain).get(date);
						seriouslySick.get(strain).replace(date, oldValue+1);
					}
					else {
						seriouslySick.get(strain).put(date, 1);
					}
				}
				else {
					Map<LocalDate, Integer> map = new HashMap<>();
					map.put(date, 1);
					seriouslySick.put(strain, map);
				}
			}

		}


		@Override
		public void handleEvent(EpisimInitialInfectionEvent event) {
			Person person = this.population.getPersons().get(event.getPersonId());
			Attributes attr = person.getAttributes();
			attr.putAttribute("strain", event.getVirusStrain().toString());
		}

	}


}





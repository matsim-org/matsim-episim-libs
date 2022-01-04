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
import org.matsim.episim.events.*;
import org.matsim.run.AnalysisCommand;
import org.matsim.utils.objectattributes.attributable.Attributes;

import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;


@CommandLine.Command(
		name = "vacEff",
		description = "Calculate vaccination effectiveness"
)
public class VaccinationEffectivenessFromPotentialInfections implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(VaccinationEffectivenessFromPotentialInfections.class);

	@CommandLine.Option(names = "--output", defaultValue = "./output/1/")
	private Path output;
	
	@CommandLine.Option(names = "--input", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input")
	private String input;
	
	@CommandLine.Option(names = "--population-file", defaultValue = "/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz")
	private String populationFile;
	
	@CommandLine.Option(names = "--start-date", defaultValue = "2020-02-24")
	private LocalDate startDate;


	public static void main(String[] args) {
		System.exit(new CommandLine(new VaccinationEffectivenessFromPotentialInfections()).execute(args));
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
		bw.write("");
		bw.flush();
		
		Map<String, Map<Long, Double>> vac = new HashMap<>();
		Map<String, Map<Long, Double>> unVac = new HashMap<>();
		
		Handler handler = new Handler(population, startDate, vac, unVac);
		
		AnalysisCommand.forEachEvent(scenario, s -> {}, handler);
		
		Map<Long, Double> mRNAB117Vac = vac.get("mRNA-B117");
		Map<Long, Double> mRNAB117UnVac = unVac.get("mRNA-B117");

		
		for (Entry<Long, Double> a : mRNAB117Vac.entrySet()) {
			double b = mRNAB117UnVac.get(a.getKey());
			System.out.println(a.getKey() + "\t" + (b - a.getValue())/b);
		}
		
		bw.close();
		
		log.info("Calculated results for scenario {}", scenario);

	}



	private void initPopulation(Population population) {
		
		for (Person p : population.getPersons().values()) {
			Attributes attributes = p.getAttributes();
			attributes.putAttribute("vaccine", "");
			attributes.putAttribute("vaccinationDate", "");
		}
		
	}


	private static class Handler implements EpisimPotentialInfectionEventHandler, EpisimVaccinationEventHandler {

		private Population population;
		private LocalDate startDate;
		private Map<String, Map<Long, Double>> vac = new HashMap<>();
		private Map<String, Map<Long, Double>> unVac = new HashMap<>();

	
		public Handler(Population population, LocalDate startDate, Map<String, Map<Long, Double>> vac, Map<String, Map<Long, Double>> unvac) {
			this.population = population;
			this.startDate = startDate;
			this.unVac = unvac;
			this.vac = vac;
		}
		


		@Override
		public void handleEvent(EpisimVaccinationEvent event) {
			String date = startDate.plusDays( (int) (event.getTime() / 86_400)).toString();
			
			if (event.getReVaccination())
				return;
			
			population.getPersons().get(event.getPersonId()).getAttributes().putAttribute("vaccinationDate", date);
			population.getPersons().get(event.getPersonId()).getAttributes().putAttribute("vaccine", event.getVaccinationType().toString());

		}



		@Override
		public void handleEvent(EpisimPotentialInfectionEvent event) {
			Person person = population.getPersons().get(event.getPersonId());
			Attributes attr = person.getAttributes();
			LocalDate vaccinationDate = LocalDate.parse((String) attr.getAttribute("vaccinationDate"));
			LocalDate date = startDate.plusDays( (int) (event.getTime() / 86_400));
			
			long daysSinceVaccination = ChronoUnit.DAYS.between(vaccinationDate, date);
			
			String vaccine = (String) attr.getAttribute("vaccine");
			String strain = event.getStrain().toString();
			
			String strainVaccine = String.join("-", vaccine, strain);
			
			if (vac.containsKey(strainVaccine)) {
				Map<Long, Double> map = vac.get(strainVaccine);
				if (map.containsKey(daysSinceVaccination)) {
					double oldProbaSum = map.get(daysSinceVaccination);
					double newProbaSum = oldProbaSum + event.getProbability();
					map.replace(daysSinceVaccination, newProbaSum);
				}
				else {
					map.put(daysSinceVaccination, event.getProbability());
				}
			}
			else {
				Map<Long, Double> map = new HashMap<>();
				map.put(daysSinceVaccination, event.getProbability());
				vac.put(strainVaccine, map);
			}
			
			
			if (unVac.containsKey(strainVaccine)) {
				Map<Long, Double> map = unVac.get(strainVaccine);
				if (map.containsKey(daysSinceVaccination)) {
					double oldProbaSum = map.get(daysSinceVaccination);
					double newProbaSum = oldProbaSum + event.getUnVacProbability();
					map.replace(daysSinceVaccination, newProbaSum);
				}
				else {
					map.put(daysSinceVaccination, event.getUnVacProbability());
				}
			}
			else {
				Map<Long, Double> map = new HashMap<>();
				map.put(daysSinceVaccination, event.getUnVacProbability());
				unVac.put(strainVaccine, map);
			}			

			
		}
	}


}





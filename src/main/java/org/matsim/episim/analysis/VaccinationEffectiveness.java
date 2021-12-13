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
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.events.*;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.run.AnalysisCommand;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.utils.objectattributes.attributable.Attributes;

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
		name = "vacEff",
		description = "Calculate R values summaries"
)
public class VaccinationEffectiveness implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(VaccinationEffectiveness.class);

	@CommandLine.Option(names = "--output", defaultValue = "./output/he/")
	private Path output;
	
	@CommandLine.Option(names = "--input", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input")
	private String input;
	
	@CommandLine.Option(names = "--population-file", defaultValue = "/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz")
	private String populationFile;
	
	@CommandLine.Option(names = "--start-date", defaultValue = "2020-02-24")
	private LocalDate startDate;


	public static void main(String[] args) {
		System.exit(new CommandLine(new VaccinationEffectiveness()).execute(args));
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
		
		Handler handler = new Handler(population, startDate);
		
		List<String> eventFiles = AnalysisCommand.forEachEvent(scenario, s -> {}, handler);
		

		int days4aggregation = 7;
		
		LocalDate date = LocalDate.parse("2021-01-01");
		
		while( date.isBefore(startDate.plusDays(700)) ){
			
			System.out.println("+++ date: " + date + " +++");
			
			int year = date.getYear();
			int day = date.getDayOfYear();
			
			Map<Id<Person>, Person> vaccinated = new HashMap<>();
			
			Map<Id<Person>, Person> controlGroup = new HashMap<>();
			
			for (Person p : population.getPersons().values()) {
				
				Attributes attributes = p.getAttributes();
				
				//ignore agents that don't live in Cologne
				String district = (String) attributes.getAttribute("district");
				if (!district.equals("Köln")) {
					continue;
					
				}
				
				//ignore children
				int age = (int) attributes.getAttribute("microm:modeled:age");
				if (age < 18) {
					continue;
				}

				//ignore imported cases
				List<LocalDate> infectionDates = (List<LocalDate>) attributes.getAttribute("infections");
				List<LocalDate> contagiousDates = (List<LocalDate>) attributes.getAttribute("contagious");
				if(contagiousDates.size() > infectionDates.size()) {
					continue;
				}
				
				//ignore recovered
				boolean recovered = false;
				List<LocalDate> recoveredDates = (List<LocalDate>) attributes.getAttribute("recovered");
				for (LocalDate d : recoveredDates) {
					if (d.isBefore(date)) {
						recovered = true;
						break;
					}
				}
				if (recovered) continue;
				
				//ignore alpha and wildtype
				List<String> strains = (List<String>) attributes.getAttribute("strains");
				if (strains.contains(VirusStrain.B117.toString()) || strains.contains(VirusStrain.SARS_CoV_2.toString())) {
					continue;
				}
				
				//ignore vector vaccines
				String vaccine = (String) attributes.getAttribute("vaccine");
				if (vaccine.equals(VaccinationType.vector.toString())) {
					continue;
				}
				
				String vaccinationDateAsString = (String) attributes.getAttribute("vaccinationDate");
				
				if (!vaccinationDateAsString.equals("")) {
					
					LocalDate vaccinationDate = LocalDate.parse(vaccinationDateAsString);
					
					if (vaccinationDate.getYear() == year && vaccinationDate.getDayOfYear() / days4aggregation  == day / days4aggregation) {
						vaccinated.put(p.getId(), p);
					}
					else if (vaccinationDate.isAfter(date)) {
						controlGroup.put(p.getId(), p);
					}
						
					
				}
				else controlGroup.put(p.getId(), p);
			}
						
			LocalDate date2 = date;
			int period = 0;
			while( date2.isBefore(startDate.plusDays(700)) ){
				
				Map<Id<Person>, Person> newVaccinated = new HashMap<>();
				
				int vaccinatedInfected = 0;
				int vaccinatedNotInfected = 0;
				
				for (Person p : vaccinated.values()) {
					boolean infected = false;
					List<LocalDate> contagiousDates = (List<LocalDate>) p.getAttributes().getAttribute("contagious");
					
					for (LocalDate d : contagiousDates) {
						if (d.getYear() == date2.getYear() && d.getDayOfYear() / days4aggregation == date2.getDayOfYear() / days4aggregation) {
							infected = true;
							break;
						}
					}
					if (infected) vaccinatedInfected++;
					else {
						vaccinatedNotInfected++;
						newVaccinated.put(p.getId(), p);
					}
				}
				vaccinated = newVaccinated;
				
				
				Map<Id<Person>, Person> newControlGroup = new HashMap<>();
				
				int controlGroupInfected = 0;
				int controlGroupNotInfected = 0;
				
				for (Person p : controlGroup.values()) {
					boolean infected = false;
					List<LocalDate> contagiousDates = (List<LocalDate>) p.getAttributes().getAttribute("contagious");
					
					for (LocalDate d : contagiousDates) {
						if (d.getYear() == date2.getYear() && d.getDayOfYear() / days4aggregation == date2.getDayOfYear() / days4aggregation) {
							infected = true;
							break;
						}
					}
					if (infected) controlGroupInfected++;
					else {
						controlGroupNotInfected++;
						
						LocalDate vaccinationDate = null;
						String vaccinationDateAsString = (String) p.getAttributes().getAttribute("vaccinationDate");
						if (!vaccinationDateAsString.equals("")) {	
							vaccinationDate = LocalDate.parse(vaccinationDateAsString);
						}
						
						if(vaccinationDate == null) {
							newControlGroup.put(p.getId(), p);
						}
						else if (vaccinationDate.isAfter(date2)) {
							newControlGroup.put(p.getId(), p);
						}	
					}

				}
				controlGroup = newControlGroup;
				
				double infShareControlGroup = controlGroupInfected / (controlGroupInfected + controlGroupNotInfected + 0.0);
				double infShareVaccinated = vaccinatedInfected / (vaccinatedInfected + vaccinatedNotInfected + 0.0);
				double efficacy = (infShareControlGroup - infShareVaccinated) / infShareControlGroup;
				
				bw.newLine();
				bw.write(date + "\t" + date2 + "\t" + period + "\t" + vaccinatedInfected + "\t" + vaccinatedNotInfected + "\t" + controlGroupInfected + "\t" + controlGroupNotInfected + "\t" + efficacy);
				
				period = period + 1;
				date2 = date2.plusDays(days4aggregation);
				
			}
			
			bw.flush();
						
			date = date.plusDays(days4aggregation);
		}
		

		bw.close();
		
		log.info("Calculated results for scenario {}", scenario);

	}



	private void initPopulation(Population population) {
		
		for (Person p : population.getPersons().values()) {
			Attributes attributes = p.getAttributes();
			attributes.putAttribute("vaccine", "");
			attributes.putAttribute("vaccinationDate", "");
			attributes.putAttribute("strains", new ArrayList<>());
			attributes.putAttribute("infections", new ArrayList<>()); //
			attributes.putAttribute("contagious", new ArrayList<>()); //
			attributes.putAttribute("recovered", new ArrayList<>());
			attributes.putAttribute("imported", "yes");
		}
		
	}


	private static class Handler implements EpisimPersonStatusEventHandler, EpisimVaccinationEventHandler, EpisimInfectionEventHandler {

		private Population population;
		private LocalDate startDate;
	
		public Handler(Population population, LocalDate startDate) {
			this.population = population;
			this.startDate = startDate;
		}
		
		@Override
		public void handleEvent(EpisimInfectionEvent event) {
			Person person = population.getPersons().get(event.getPersonId());
			Attributes personAttributes = person.getAttributes();
			
			List<Integer> infections = (List<Integer>) personAttributes.getAttribute("infections");
			infections.add((int) (event.getTime() / 86_400));
			personAttributes.putAttribute("infections", infections);
			
			List<String> strains = (List<String>) personAttributes.getAttribute("strains");
			strains.add(event.getStrain().toString());
			personAttributes.putAttribute("strains", strains);
		}

		@Override
		public void handleEvent(EpisimPersonStatusEvent event) {
			Person person = population.getPersons().get(event.getPersonId());
			Attributes personAttributes = person.getAttributes();
			DiseaseStatus status = event.getDiseaseStatus();
			
			if (status.equals(DiseaseStatus.susceptible)) {
				List<LocalDate> recovered = (List<LocalDate>) personAttributes.getAttribute("recovered");
				recovered.add(startDate.plusDays( (int) (event.getTime() / 86_400)));
				personAttributes.putAttribute("recovered", recovered);
			}
			if (status.equals(DiseaseStatus.contagious)) {
				List<LocalDate> contagious = (List<LocalDate>) personAttributes.getAttribute("contagious");
				contagious.add(startDate.plusDays( (int) (event.getTime() / 86_400)));
				personAttributes.putAttribute("contagious", contagious);
			}
//			
//			attributes.putAttribute(status.toString(), startDate.plusDays((int) (event.getTime() / 86_400)));
		}

		@Override
		public void handleEvent(EpisimVaccinationEvent event) {
			String date = startDate.plusDays( (int) (event.getTime() / 86_400)).toString();
			
			if (event.getReVaccination())
				return;
			
			population.getPersons().get(event.getPersonId()).getAttributes().putAttribute("vaccinationDate", date);
			population.getPersons().get(event.getPersonId()).getAttributes().putAttribute("vaccine", event.getVaccinationType().toString());

		}
	}


}





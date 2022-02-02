 /* project: org.matsim.*
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
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.events.*;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
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
 * Calcualte vaccination effectiveness from events
 */
@CommandLine.Command(
		name = "vacEff",
		description = "Calcualte vaccination effectiveness from events"
)
public class VaccinationEffectiveness implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(VaccinationEffectiveness.class);

	@CommandLine.Option(names = "--output", defaultValue = "./output/")
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
		
		Map<Integer, Integer> vacWildtypePerPeriod = new HashMap<>();
		Map<Integer, Integer> vacAlphaPerPeriod = new HashMap<>();
		Map<Integer, Integer> vacDeltaPerPeriod = new HashMap<>();
		Map<Integer, Integer> vacOmicronPerPeriod = new HashMap<>();
		Map<Integer, Integer> vacNotInfectedPerPeriod = new HashMap<>();

		
		Map<Integer, Integer> cgWildtypePerPeriod = new HashMap<>();
		Map<Integer, Integer> cgAlphaPerPeriod = new HashMap<>();
		Map<Integer, Integer> cgDeltaPerPeriod = new HashMap<>();
		Map<Integer, Integer> cgOmicronPerPeriod = new HashMap<>();
		Map<Integer, Integer> cgNotInfectedPerPeriod = new HashMap<>();


		BufferedWriter bw = Files.newBufferedWriter(scenario.resolve(id + "post.ve.tsv"));
		
		bw.write("day");
		bw.write("\t");
		bw.write("wildtypeVe");
		bw.write("\t");
		bw.write("alphaVe");
		bw.write("\t");
		bw.write("deltaVe");
		bw.write("\t");
		bw.write("omicronVe");
		bw.flush();

		
		Handler handler = new Handler(population, startDate);
		
		AnalysisCommand.forEachEvent(scenario, s -> {}, handler);

		int days4aggregation = 14;
		
		LocalDate date = LocalDate.parse("2021-01-01");
		
		while( date.isBefore(startDate.plusDays(850)) ){
			
			System.out.println("+++ date: " + date + " +++");
			
			int year = date.getYear();
			int day = date.getDayOfYear();
			
			Map<Id<Person>, Person> vaccinated = new HashMap<>();
			
			Map<Id<Person>, Person> potentialTwins = new HashMap<>();
			
			for (Person p : population.getPersons().values()) {
				
				Attributes attributes = p.getAttributes();
				
				//ignore agents that don't live in Cologne
				String district = (String) attributes.getAttribute("district");
				if (!district.equals("Köln")) {
					continue;
					
				}
				
				//ignore children
//				int age = (int) attributes.getAttribute("microm:modeled:age");
//				if (age < 18) {
//					continue;
//				}

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
//				List<String> strains = (List<String>) attributes.getAttribute("strains");
//				if (strains.contains(VirusStrain.B117.toString()) || strains.contains(VirusStrain.SARS_CoV_2.toString())) {
//					continue;
//				}
				
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
						potentialTwins.put(p.getId(), p);
					}
						
					
				}
				else potentialTwins.put(p.getId(), p);
			}
			
			Map<Id<Person>, Person> vaccinatedWithTwins = new HashMap<>();
			
			int foundTwins = 0;
			for (Person p : vaccinated.values()) {
				Person twin = findTwin(p, potentialTwins, date);
				if (twin != null) {
					p.getAttributes().putAttribute("twin", twin.getId());
					vaccinatedWithTwins.put(p.getId(), p);
					foundTwins++;
				}
				else {
					p.getAttributes().putAttribute("twin", "");
				}
			}
			System.out.println("Found " + foundTwins + " twins. Vaccinated group: " + vaccinated.size());

						
			LocalDate date2 = date;
			int period = 0;
			while( date2.isBefore(startDate.plusDays(700)) ){
				
				Map<Id<Person>, Person> newVaccinated = new HashMap<>();

				for (Person p : vaccinatedWithTwins.values()) {
					boolean infected = false;
					String strain = "";
					
					boolean boostered = false;

					boolean twinInfected = false;
					String strainTwin = "";

					boolean twinVaccinated = false;
					
					Person twin = population.getPersons().get(p.getAttributes().getAttribute("twin"));
										
					List<LocalDate> contagiousDates = (List<LocalDate>) p.getAttributes().getAttribute("contagious");
					
					for (int ii = 0; ii<contagiousDates.size(); ii++) {
						LocalDate d = contagiousDates.get(ii);
						if (d.getYear() == date2.getYear() && d.getDayOfYear() / days4aggregation == date2.getDayOfYear() / days4aggregation) {
							infected = true;
							List<String> strains = (List<String>)  p.getAttributes().getAttribute("strains");
							strain = strains.get(ii);
							break;
						}
					}

					if (infected) {
						if (VirusStrain.valueOf(strain) == VirusStrain.SARS_CoV_2) {
							if (vacWildtypePerPeriod.containsKey(period)) {
								int value = vacWildtypePerPeriod.get(period) + 1;
								vacWildtypePerPeriod.replace(period, value);
							}
							else 
								vacWildtypePerPeriod.put(period, 1);
						}
						
						if (VirusStrain.valueOf(strain) == VirusStrain.ALPHA) {
							if (vacAlphaPerPeriod.containsKey(period)) {
								int value = vacAlphaPerPeriod.get(period) + 1;
								vacAlphaPerPeriod.replace(period, value);
							}
							else 
								vacAlphaPerPeriod.put(period, 1);
						}
						
						if (VirusStrain.valueOf(strain) == VirusStrain.DELTA) {
							if (vacDeltaPerPeriod.containsKey(period)) {
								int value = vacDeltaPerPeriod.get(period) + 1;
								vacDeltaPerPeriod.replace(period, value);
							}
							else 
								vacDeltaPerPeriod.put(period, 1);
						}
						
						if (VirusStrain.valueOf(strain) == VirusStrain.OMICRON_BA1) {
							if (vacOmicronPerPeriod.containsKey(period)) {
								int value = vacOmicronPerPeriod.get(period) + 1;
								vacOmicronPerPeriod.replace(period, value);
							}
							else 
								vacOmicronPerPeriod.put(period, 1);
							
						}
					
					}
					else {
						if (vacNotInfectedPerPeriod.containsKey(period)) {
							int value = vacNotInfectedPerPeriod.get(period) + 1;
							vacNotInfectedPerPeriod.replace(period, value);
						}
						else 
							vacNotInfectedPerPeriod.put(period, 1);
					}
					
					List<LocalDate> contagiousDatesTwin = (List<LocalDate>) twin.getAttributes().getAttribute("contagious");
					
					for (int ii = 0; ii<contagiousDatesTwin.size(); ii++) {
						LocalDate d = contagiousDatesTwin.get(ii);
						if (d.getYear() == date2.getYear() && d.getDayOfYear() / days4aggregation == date2.getDayOfYear() / days4aggregation) {
							twinInfected = true;
							List<String> strains = (List<String>)  twin.getAttributes().getAttribute("strains");
							strainTwin = strains.get(ii);
							break;
						}
					}
					if (twinInfected) {
						
						if (VirusStrain.valueOf(strainTwin) == VirusStrain.SARS_CoV_2) {
							if (cgWildtypePerPeriod.containsKey(period)) {
								int value = cgWildtypePerPeriod.get(period) + 1;
								cgWildtypePerPeriod.replace(period, value);
							}
							else 
								cgWildtypePerPeriod.put(period, 1);
						}
						
						if (VirusStrain.valueOf(strainTwin) == VirusStrain.ALPHA) {
							if (cgAlphaPerPeriod.containsKey(period)) {
								int value = cgAlphaPerPeriod.get(period) + 1;
								cgAlphaPerPeriod.replace(period, value);
							}
							else 
								cgAlphaPerPeriod.put(period, 1);
						}
						
						if (VirusStrain.valueOf(strainTwin) == VirusStrain.DELTA) {
							if (cgDeltaPerPeriod.containsKey(period)) {
								int value = cgDeltaPerPeriod.get(period) + 1;
								cgDeltaPerPeriod.replace(period, value);
							}
							else 
								cgDeltaPerPeriod.put(period, 1);
						}
						
						if (VirusStrain.valueOf(strainTwin) == VirusStrain.OMICRON_BA1) {
							if (cgOmicronPerPeriod.containsKey(period)) {
								int value = cgOmicronPerPeriod.get(period) + 1;
								cgOmicronPerPeriod.replace(period, value);
							}
							else 
								cgOmicronPerPeriod.put(period, 1);
						}
					}
					else {
						if (cgNotInfectedPerPeriod.containsKey(period)) {
							int value = cgNotInfectedPerPeriod.get(period) + 1;
							cgNotInfectedPerPeriod.replace(period, value);
						}
						else 
							cgNotInfectedPerPeriod.put(period, 1);
					}
					
					
					LocalDate vaccinationDate = null;
					String vaccinationDateAsString = (String) twin.getAttributes().getAttribute("vaccinationDate");
					if (!vaccinationDateAsString.equals("")) {	
						vaccinationDate = LocalDate.parse(vaccinationDateAsString);
						if (vaccinationDate.isBefore(date2)) {
							twinVaccinated = true;
						}
					}
					
					LocalDate boosterDate = null;
					String boosterDateAsString = (String) p.getAttributes().getAttribute("boosterDate");
					if (!boosterDateAsString.equals("")) {	
						boosterDate = LocalDate.parse(boosterDateAsString);
						if (boosterDate.isBefore(date2)) {
							boostered = true;
						}
					}
					
					
					if (!infected && !twinInfected && !twinVaccinated && !boostered)
						newVaccinated.put(p.getId(), p);
				}
				vaccinatedWithTwins = newVaccinated;
				
				period = period + 1;
				date2 = date2.plusDays(days4aggregation);
				
			}
			
			bw.flush();
						
			date = date.plusDays(days4aggregation);
		}
		

		for (int i = 0; i <= 850.0 / days4aggregation ; i++) {
			bw.newLine();

			bw.write(String.valueOf(i * days4aggregation));
			bw.write("\t");
			
			int vacNotInfected = 0;
			
			if (vacNotInfectedPerPeriod.containsKey(i)) 
				vacNotInfected = vacNotInfectedPerPeriod.get(i);

			int wildtypeInfected = 0;
			
			if (vacWildtypePerPeriod.containsKey(i)) 
				wildtypeInfected = vacWildtypePerPeriod.get(i);

			int alphaInfected = 0;

			if (vacAlphaPerPeriod.containsKey(i)) 
				alphaInfected = vacAlphaPerPeriod.get(i);

			int deltaInfected = 0;

			if (vacDeltaPerPeriod.containsKey(i)) 
				deltaInfected = vacDeltaPerPeriod.get(i);

			int omicronInfected = 0;
			
			if (vacOmicronPerPeriod.containsKey(i)) 
				omicronInfected = vacOmicronPerPeriod.get(i);

			int cgNotInfected = 0;

			if (cgNotInfectedPerPeriod.containsKey(i)) 
				cgNotInfected = cgNotInfectedPerPeriod.get(i);

			int cgWildtypeInfected = 0;

			if (cgWildtypePerPeriod.containsKey(i)) 
				cgWildtypeInfected = cgWildtypePerPeriod.get(i);
			
			int cgAlphaInfected = 0;
			
			if (cgAlphaPerPeriod.containsKey(i)) 
				cgAlphaInfected = cgAlphaPerPeriod.get(i);
			
			int cgDeltaInfected = 0;

			if (cgDeltaPerPeriod.containsKey(i)) 
				cgDeltaInfected = cgDeltaPerPeriod.get(i);
			
			int cgOmicronInfected = 0;
			
			if (cgOmicronPerPeriod.containsKey(i)) 
				cgOmicronInfected = cgOmicronPerPeriod.get(i);
			
			double wildtypeVe = (double) (cgWildtypeInfected - wildtypeInfected) / cgWildtypeInfected;
			double alphaVe = (double) (cgAlphaInfected - alphaInfected) / cgAlphaInfected;
			double deltaVe = (double) (cgDeltaInfected - deltaInfected) / cgDeltaInfected;
			double omicronVe = (double) (cgOmicronInfected - omicronInfected) / cgOmicronInfected;
			
			bw.write(String.valueOf(wildtypeVe));
			bw.write("\t");
			bw.write(String.valueOf(alphaVe));
			bw.write("\t");
			bw.write(String.valueOf(deltaVe));
			bw.write("\t");
			bw.write(String.valueOf(omicronVe));

			bw.flush();
			
		}
		
		bw.close();
		
		log.info("Calculated results for scenario {}", scenario);

	}


	private Person findTwin(Person p, Map<Id<Person>, Person> potentialTwins, LocalDate date) {
		
		List<Id<Person>> personIds = new ArrayList<Id<Person>>(potentialTwins.keySet());
		Collections.shuffle(personIds);
		int pAge = (int) p.getAttributes().getAttribute("microm:modeled:age");
		String pSex = (String) p.getAttributes().getAttribute("microm:modeled:sex");

				
		Person twin = null;
		
		for (Object pId : personIds) {
			Person potentialTwin = potentialTwins.get(pId);
			int potentialTwinAge = (int) potentialTwin.getAttributes().getAttribute("microm:modeled:age");
			String potentialTwinSex = (String) potentialTwin.getAttributes().getAttribute("microm:modeled:sex");
			
			if (potentialTwinAge == pAge) {
				if (potentialTwinSex.equals(pSex)) {
					twin = potentialTwin;
					break;
				}
			}
		}
		
		return twin;
	}

	private void initPopulation(Population population) {
		
		for (Person p : population.getPersons().values()) {
			Attributes attributes = p.getAttributes();
			attributes.putAttribute("vaccine", "");
			attributes.putAttribute("vaccinationDate", "");
			attributes.putAttribute("boosterDate", "");
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
			
			if (event.getN() > 1) {
				population.getPersons().get(event.getPersonId()).getAttributes().putAttribute("boosterDate", date);
			}
			else {
				population.getPersons().get(event.getPersonId()).getAttributes().putAttribute("vaccinationDate", date);
				population.getPersons().get(event.getPersonId()).getAttributes().putAttribute("vaccine", event.getVaccinationType().toString());
			}
			


		}
	}


}





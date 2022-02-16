 /* project: org.matsim.*
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
 import it.unimi.dsi.fastutil.ints.IntArrayList;
 import it.unimi.dsi.fastutil.ints.IntList;
 import org.apache.logging.log4j.Level;
 import org.apache.logging.log4j.LogManager;
 import org.apache.logging.log4j.Logger;
 import org.apache.logging.log4j.core.config.Configurator;
 import org.matsim.api.core.v01.Id;
 import org.matsim.api.core.v01.IdMap;
 import org.matsim.api.core.v01.Scenario;
 import org.matsim.api.core.v01.population.Person;
 import org.matsim.api.core.v01.population.Population;
 import org.matsim.core.population.PopulationUtils;
 import org.matsim.episim.EpisimPerson.DiseaseStatus;
 import org.matsim.episim.events.*;
 import org.matsim.episim.model.VaccinationType;
 import org.matsim.episim.model.VirusStrain;
 import org.matsim.run.AnalysisCommand;
 import picocli.CommandLine;

 import java.io.BufferedWriter;
 import java.io.IOException;
 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.time.LocalDate;
 import java.util.*;


 /**
  * Calcualte hospital numbers from events
  */
 @CommandLine.Command(
		 name = "hospitalNumbers",
		 description = "Calculate vaccination effectiveness from events"
 )
 public class HospitalNumbersFromEvents implements OutputAnalysis {

	 private static final Logger log = LogManager.getLogger(HospitalNumbersFromEvents.class);

	 @CommandLine.Option(names = "--output", defaultValue = "./output/")
	 private Path output;

	 @CommandLine.Option(names = "--input", defaultValue = "/scratch/projects/bzz0020/episim-input")
//	 @CommandLine.Option(names = "--input", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input")
	 private String input;

	 @CommandLine.Option(names = "--population-file", defaultValue = "/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz")
	 private String populationFile;

	 @CommandLine.Option(names = "--start-date", defaultValue = "2020-02-24")
	 private LocalDate startDate;

	 @CommandLine.Option(names = "--district", description = "District to filter for", defaultValue = "Köln")
	 private String district;

	 private Population population;

	 @Inject
	 private Scenario scenario;

	 private final Random rnd = new Random(1234);

	 public static void main(String[] args) {
		 System.exit(new CommandLine(new HospitalNumbersFromEvents()).execute(args));
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

		 log.info("done");

		 return 0;
	 }

	 @Override
	 public void analyzeOutput(Path output) throws IOException {

		 if (scenario != null)
			 population = scenario.getPopulation();

		 String id = AnalysisCommand.getScenarioPrefix(output);

		 BufferedWriter bw = Files.newBufferedWriter(output.resolve(id + "post.hospital.tsv"));


		 Map<Id<Person>, Holder> data = new IdMap<>(Person.class, population.getPersons().size());

		 Handler handler = new Handler(data, startDate);

		 AnalysisCommand.forEachEvent(output, s -> {
		 }, handler);

		 
		 		 

		 bw.close();

		 log.info("Calculated results for output {}", output);

	 }



	 private static class Handler implements EpisimPersonStatusEventHandler, EpisimVaccinationEventHandler, EpisimInfectionEventHandler {

		 private final Map<Id<Person>, Holder> data;
		 private final LocalDate startDate;


		 public Handler(Map<Id<Person>, Holder> data, LocalDate startDate) {
			 this.data = data;
			 this.startDate = startDate;
		 }

		 @Override
		 public void handleEvent(EpisimInfectionEvent event) {

			 Holder attr = data.computeIfAbsent(event.getPersonId(), Holder::new);

			 int day = (int) (event.getTime() / 86_400);

			 attr.infections.add(day);
			 attr.strains.add(event.getVirusStrain());
		 }

		 @Override
		 public void handleEvent(EpisimPersonStatusEvent event) {

			 Holder attr = data.computeIfAbsent(event.getPersonId(), Holder::new);
			 DiseaseStatus status = event.getDiseaseStatus();

			 int day = (int) (event.getTime() / 86_400);

			 if (status.equals(DiseaseStatus.susceptible)) {
				 attr.recoveredDates.add(startDate.plusDays(day));
			 }
			 if (status.equals(DiseaseStatus.contagious)) {
				 attr.contagiousDates.add(startDate.plusDays(day));
			 }

		 }

		 @Override
		 public void handleEvent(EpisimVaccinationEvent event) {
			 int day = (int) (event.getTime() / 86_400);
			 LocalDate date = startDate.plusDays(day);
			 Holder attr = data.computeIfAbsent(event.getPersonId(), Holder::new);

			 if (event.getN() == 2) {
				 attr.boosterDate = date;
			 } else if (event.getN() == 1){
				 attr.vaccinationDate = date;
				 attr.vaccine = event.getVaccinationType();
			 }
			 else {
				 //todo
			 }
		 }
	 }


	 /**
	  * Data holder for attributes
	  */
	 private static final class Holder {

		 private VaccinationType vaccine = null;
		 private LocalDate vaccinationDate = null;
		 private LocalDate boosterDate = null;
		 private final List<VirusStrain> strains = new ArrayList<>();
		 private final IntList infections = new IntArrayList();
		 private final List<LocalDate> contagiousDates = new ArrayList<>();
		 private final List<LocalDate> recoveredDates = new ArrayList<>();

		 private Holder(Id<Person> personId) {
			 // Id is not stored at the moment
		 }
	 }

 }





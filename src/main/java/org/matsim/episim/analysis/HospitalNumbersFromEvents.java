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
 import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
 import it.unimi.dsi.fastutil.ints.Int2IntMap;
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
 import org.matsim.core.config.Config;
 import org.matsim.core.config.ConfigUtils;
 import org.matsim.core.population.PopulationUtils;
 import org.matsim.episim.EpisimConfigGroup;
 import org.matsim.episim.EpisimPerson;
 import org.matsim.episim.EpisimPerson.DiseaseStatus;
 import org.matsim.episim.VaccinationConfigGroup;
 import org.matsim.episim.VirusStrainConfigGroup;
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


 /**
  * Calcualte hospital numbers from events
  */
 @CommandLine.Command(
		 name = "hospitalNumbers",
		 description = "Calculate vaccination effectiveness from events"
 )
 public class HospitalNumbersFromEvents implements OutputAnalysis {

	 private static final Logger log = LogManager.getLogger(HospitalNumbersFromEvents.class);

	 //	 @CommandLine.Option(names = "--output", defaultValue = "./output/")
	 @CommandLine.Option(names = "--output", defaultValue = "C:/Users/jakob/Desktop/output")
	 private Path output;

	 //	 @CommandLine.Option(names = "--input", defaultValue = "/scratch/projects/bzz0020/episim-input")
	 @CommandLine.Option(names = "--input", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input")
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
	 private double hospitalFactor = 0.5; //TODO: what should this be?
	 private double immunityFactor = 1.0; //TODO: what should this be?

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

		 Int2IntMap iteration2HospitalizationCnt = new Int2IntArrayMap();

		 for (Map.Entry<Id<Person>, Holder> personEntry : handler.data.entrySet()) {

			 Id<Person> personId = personEntry.getKey();
			 Holder person = personEntry.getValue();
			 if (person.infections.size() > 0) {

				 int age = (int) population.getPersons().get(personId).getAttributes().getAttribute("microm:modeled:age");

				 // loop through infections
				 for (int iInfection = 0; iInfection < person.infections.size(); iInfection++) {

					 VirusStrain strain = person.strains.get(iInfection);
					 int infectionIteration = person.infections.getInt(iInfection);

					 double ageFactor = getProbaOfTransitioningToSeriouslySick(age);

					 //TODO: I haven't gotten the strainConfig into here yet.
//					 double vaccinationFactor = person.vaccine != null ?
//							 strainConfig.getParams(strain).getFactorSeriouslySickVaccinated() :
//							 strainConfig.getParams(strain).getFactorSeriouslySick();

					 if (rnd.nextDouble() < ageFactor
//							 * vaccinationFactor //TODO
							 * getSeriouslySickFactor(person, strain))
					 {
					 	int hospitalizationIteration = infectionIteration + 4; // TODO: this shouldn't be hardcoded
					 	int hospitalizationCnt = iteration2HospitalizationCnt.getOrDefault(hospitalizationIteration, 0);
					 	iteration2HospitalizationCnt.put(hospitalizationIteration, ++hospitalizationCnt);
					 }
				 }

			 }
		 }


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
			 } else if (event.getN() == 1) {
				 attr.vaccinationDate = date;
				 attr.vaccine = event.getVaccinationType();
			 } else {
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

	 protected double getProbaOfTransitioningToSeriouslySick(int age) {

		 double proba = -1;

		 if (age < 10) {
			 proba = 0.1 / 100;
		 } else if (age < 20) {
			 proba = 0.3 / 100;
		 } else if (age < 30) {
			 proba = 1.2 / 100;
		 } else if (age < 40) {
			 proba = 3.2 / 100;
		 } else if (age < 50) {
			 proba = 4.9 / 100;
		 } else if (age < 60) {
			 proba = 10.2 / 100;
		 } else if (age < 70) {
			 proba = 16.6 / 100;
		 } else if (age < 80) {
			 proba = 24.3 / 100;
		 } else {
			 proba = 27.3 / 100;
		 }

		 return proba * hospitalFactor;
	 }

	 protected double getProbaOfTransitioningToCritical(int age) { // changed from EpiSimPerson
		 double proba = -1;

		 //		 int age = person.getAge();

		 if (age < 40) {
			 proba = 5. / 100;
		 } else if (age < 50) {
			 proba = 6.3 / 100;
		 } else if (age < 60) {
			 proba = 12.2 / 100;
		 } else if (age < 70) {
			 proba = 27.4 / 100;
		 } else if (age < 80) {
			 proba = 43.2 / 100;
		 } else {
			 proba = 70.9 / 100;
		 }

		 return proba;
	 }

	 public double getSeriouslySickFactor(Holder person, VirusStrain strain) {


		 int numVaccinations = 0;

		 if (person.boosterDate != null) {
			 numVaccinations = 3; //TODO: should this be 2?
		 } else if (person.vaccinationDate != null) {
			 numVaccinations = 2; //TODO: should this be 1?
		 }

		 int numInfections = person.infections.size() - 1; //TODO: why -1?

		 if (numVaccinations == 0 && numInfections == 0)
			 return 1.0;

		 double veSeriouslySick = 0.0;

		 //vaccinated persons that are boostered either by infection or by 3rd shot
		 if (numVaccinations > 1 || (numVaccinations > 0 && numInfections > 1)) {
			 if (strain == VirusStrain.OMICRON_BA1 || strain == VirusStrain.OMICRON_BA2)
				 veSeriouslySick = 0.9;
			 else
				 veSeriouslySick = 0.95;
		 }

		 //vaccinated persons or persons who have had a severe course of disease in the past
		 //		 else if (numVaccinations == 1 || person.hadDiseaseStatus(DiseaseStatus.seriouslySick))
		 else if (numVaccinations == 1 || person.strains.contains(VirusStrain.SARS_CoV_2) || person.strains.contains(VirusStrain.ALPHA) || person.strains.contains(VirusStrain.DELTA))

			 if (strain == VirusStrain.OMICRON_BA1 || strain == VirusStrain.OMICRON_BA2)
				 veSeriouslySick = 0.55;
			 else
				 veSeriouslySick = 0.9;

		 else {
			 if (strain == VirusStrain.OMICRON_BA1 || strain == VirusStrain.OMICRON_BA2)
				 veSeriouslySick = 0.55;
			 else
				 veSeriouslySick = 0.6;
		 }

		 double factorInf = immunityFactor;

		 double factorSeriouslySick = (1.0 - veSeriouslySick) / factorInf;

		 factorSeriouslySick = Math.min(1.0, factorSeriouslySick);
		 factorSeriouslySick = Math.max(0.0, factorSeriouslySick);

		 return factorSeriouslySick;
	 }

 }





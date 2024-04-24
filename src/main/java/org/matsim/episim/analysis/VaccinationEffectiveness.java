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
 import it.unimi.dsi.fastutil.ints.Int2IntMap;
 import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
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
  * @author smueller
  * Calcualte vaccination effectiveness from events
  */
 @CommandLine.Command(
		 name = "vacEff",
		 description = "Calculate vaccination effectiveness from events"
 )
 public class VaccinationEffectiveness implements OutputAnalysis {

	 private static final Logger log = LogManager.getLogger(VaccinationEffectiveness.class);

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

		 Int2IntMap vacWildtypePerPeriod = new Int2IntOpenHashMap();
		 Int2IntMap vacAlphaPerPeriod = new Int2IntOpenHashMap();
		 Int2IntMap vacDeltaPerPeriod = new Int2IntOpenHashMap();
		 Int2IntMap vacOmicronPerPeriod = new Int2IntOpenHashMap();
		 Int2IntMap vacOmicronBA2PerPeriod = new Int2IntOpenHashMap();
		 Int2IntMap vacNotInfectedPerPeriod = new Int2IntOpenHashMap();

		 Int2IntMap cgWildtypePerPeriod = new Int2IntOpenHashMap();
		 Int2IntMap cgAlphaPerPeriod = new Int2IntOpenHashMap();
		 Int2IntMap cgDeltaPerPeriod = new Int2IntOpenHashMap();
		 Int2IntMap cgOmicronPerPeriod = new Int2IntOpenHashMap();
		 Int2IntMap cgOmicronBA2PerPeriod = new Int2IntOpenHashMap();
		 Int2IntMap cgNotInfectedPerPeriod = new Int2IntOpenHashMap();


		 BufferedWriter bw = Files.newBufferedWriter(output.resolve(id + "post.ve.tsv"));

		 bw.write("day");
		 bw.write("\t");
		 bw.write("wildtypeVe");
		 bw.write("\t");
		 bw.write("alphaVe");
		 bw.write("\t");
		 bw.write("deltaVe");
		 bw.write("\t");
		 bw.write("omicronBA1Ve");
		 bw.write("\t");
		 bw.write("omicronBA2Ve");
		 bw.flush();

		 Map<Id<Person>, Holder> data = new IdMap<>(Person.class, population.getPersons().size());

		 Handler handler = new Handler(data, startDate);

		 AnalysisCommand.forEachEvent(output, s -> {
		 }, false, handler);

		 int days4aggregation = 14;

		 LocalDate date = LocalDate.parse("2021-01-01");
		 LocalDate endDate = startDate.plusDays(handler.endDay);

		 while (date.isBefore(endDate)) {

			 log.debug("+++ date: {} +++", date);

			 Map<Id<Person>, Person> vaccinated = new IdMap<>(Person.class, population.getPersons().size());
			 Map<Id<Person>, Person> potentialTwins = new IdMap<>(Person.class, population.getPersons().size());

			 LocalDate until = date.plusDays(days4aggregation);

			 for (Person p : population.getPersons().values()) {

				 Holder attributes = data.get(p.getId());

				 if (attributes == null)
					 continue;

				 //ignore agents that don't live in District
				 String pDistrict = (String) p.getAttributes().getAttribute("district");
				 if (!district.equals(pDistrict)) {
					 continue;
				 }

				 //ignore children
//				int age = (int) attributes.getAttribute("microm:modeled:age");
//				if (age < 18) {
//					continue;
//				}

				 //ignore imported cases
				 if (attributes.contagiousDates.size() > attributes.infections.size()) {
					 continue;
				 }

				 //ignore recovered
				 boolean recovered = false;
				 for (LocalDate d : attributes.recoveredDates) {
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
				 if (attributes.vaccine == VaccinationType.vector) {
					 continue;
				 }

				 if (attributes.vaccinationDate != null) {

					 LocalDate vaccinationDate = attributes.vaccinationDate;

					 if ((vaccinationDate.isEqual(date) || vaccinationDate.isAfter(date)) && vaccinationDate.isBefore(until)) {
						 vaccinated.put(p.getId(), p);
					 } else if (vaccinationDate.isAfter(date)) {
						 potentialTwins.put(p.getId(), p);
					 }

				 } else potentialTwins.put(p.getId(), p);
			 }

			 Map<Id<Person>, Person> vaccinatedWithTwins = new IdMap<>(Person.class, vaccinated.size());

			 int foundTwins = 0;
			 for (Person p : vaccinated.values()) {
				 Person twin = findTwin(p, potentialTwins, date);
				 if (twin != null) {
					 vaccinatedWithTwins.put(p.getId(), twin);
					 foundTwins++;
				 }
			 }

			 log.info("Found {} twins. Vaccinated group: {}", foundTwins, vaccinated.size());


			 LocalDate date2 = date;
			 int period = 0;
			 while (date2.isBefore(endDate)) {

				 Map<Id<Person>, Person> newVaccinated = new HashMap<>();

				 for (Map.Entry<Id<Person>, Person> e : vaccinatedWithTwins.entrySet()) {

					 Person p = population.getPersons().get(e.getKey());
					 Person twin = e.getValue();

					 boolean infected = false;
					 VirusStrain strain = null;

					 boolean boostered = false;

					 boolean twinInfected = false;
					 VirusStrain strainTwin = null;

					 boolean twinVaccinated = false;

					 Holder attributes = data.get(p.getId());

					 for (int ii = 0; ii < attributes.contagiousDates.size(); ii++) {
						 LocalDate d = attributes.contagiousDates.get(ii);
						 if (d.getYear() == date2.getYear() && d.getDayOfYear() / days4aggregation == date2.getDayOfYear() / days4aggregation) {
							 infected = true;
							 strain = attributes.strains.get(ii);
							 break;
						 }
					 }

					 if (infected) {
						 if (strain == VirusStrain.SARS_CoV_2) {
							 vacWildtypePerPeriod.merge(period, 1, Integer::sum);
						 }

						 if (strain == VirusStrain.ALPHA) {
							 vacAlphaPerPeriod.merge(period, 1, Integer::sum);
						 }

						 if (strain == VirusStrain.DELTA) {
							 vacDeltaPerPeriod.merge(period, 1, Integer::sum);
						 }

						 if (strain == VirusStrain.OMICRON_BA1) {
							 vacOmicronPerPeriod.merge(period, 1, Integer::sum);
						 }
						 if (strain == VirusStrain.OMICRON_BA2) {
							 vacOmicronBA2PerPeriod.merge(period, 1, Integer::sum);
						 }

					 } else {
						 vacNotInfectedPerPeriod.merge(period, 1, Integer::sum);

					 }

					 Holder twinAttributes = data.get(twin.getId());

					 for (int ii = 0; ii < twinAttributes.contagiousDates.size(); ii++) {
						 LocalDate d = twinAttributes.contagiousDates.get(ii);
						 if (d.getYear() == date2.getYear() && d.getDayOfYear() / days4aggregation == date2.getDayOfYear() / days4aggregation) {
							 twinInfected = true;
							 strainTwin = twinAttributes.strains.get(ii);
							 break;
						 }
					 }

					 if (twinInfected) {

						 if (strainTwin == VirusStrain.SARS_CoV_2) {
							 cgWildtypePerPeriod.merge(period, 1, Integer::sum);
						 }

						 if (strainTwin == VirusStrain.ALPHA) {
							 cgAlphaPerPeriod.merge(period, 1, Integer::sum);
						 }

						 if (strainTwin == VirusStrain.DELTA) {
							 cgDeltaPerPeriod.merge(period, 1, Integer::sum);
						 }

						 if (strainTwin == VirusStrain.OMICRON_BA1) {
							 cgOmicronPerPeriod.merge(period, 1, Integer::sum);
						 }
						 if (strainTwin == VirusStrain.OMICRON_BA2) {
							 cgOmicronBA2PerPeriod.merge(period, 1, Integer::sum);
						 }
					 } else {
						 cgNotInfectedPerPeriod.merge(period, 1, Integer::sum);
					 }

					 if (twinAttributes.vaccinationDate != null) {
						 if (twinAttributes.vaccinationDate.isBefore(date2)) {
							 twinVaccinated = true;
						 }
					 }

					 if (attributes.boosterDate != null) {
						 if (attributes.boosterDate.isBefore(date2)) {
							 boostered = true;
						 }
					 }


					 if (!infected && !twinInfected && !twinVaccinated && !boostered)
						 newVaccinated.put(p.getId(), twin);
				 }

				 vaccinatedWithTwins = newVaccinated;

				 period = period + 1;
				 date2 = date2.plusDays(days4aggregation);

			 }

			 bw.flush();

			 date = date.plusDays(days4aggregation);
		 }


		 for (int i = 0; i <= 850.0 / days4aggregation; i++) {
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

			 int omicronBA2Infected = 0;

			 if (vacOmicronBA2PerPeriod.containsKey(i))
				 omicronBA2Infected = vacOmicronBA2PerPeriod.get(i);

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

			 int cgOmicronBA2Infected = 0;

			 if (cgOmicronBA2PerPeriod.containsKey(i))
				 cgOmicronBA2Infected = cgOmicronBA2PerPeriod.get(i);

			 double wildtypeVe = (double) (cgWildtypeInfected - wildtypeInfected) / cgWildtypeInfected;
			 double alphaVe = (double) (cgAlphaInfected - alphaInfected) / cgAlphaInfected;
			 double deltaVe = (double) (cgDeltaInfected - deltaInfected) / cgDeltaInfected;
			 double omicronVe = (double) (cgOmicronInfected - omicronInfected) / cgOmicronInfected;
			 double omicronBA2Ve = (double) (cgOmicronBA2Infected - omicronBA2Infected) / cgOmicronBA2Infected;
			 bw.write(String.valueOf(wildtypeVe));
			 bw.write("\t");
			 bw.write(String.valueOf(alphaVe));
			 bw.write("\t");
			 bw.write(String.valueOf(deltaVe));
			 bw.write("\t");
			 bw.write(String.valueOf(omicronVe));
			 bw.write("\t");
			 bw.write(String.valueOf(omicronBA2Ve));

			 bw.flush();

		 }

		 bw.close();

		 log.info("Calculated results for output {}", output);

	 }


	 private Person findTwin(Person p, Map<Id<Person>, Person> potentialTwins, LocalDate date) {

		 List<Id<Person>> personIds = new ArrayList<>(potentialTwins.keySet());
		 Collections.shuffle(personIds, rnd);

		 int pAge = (int) p.getAttributes().getAttribute("microm:modeled:age");
		 String pSex = (String) p.getAttributes().getAttribute("microm:modeled:sex");


		 Person twin = null;

		 for (Id<Person> pId : personIds) {
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

	 private static class Handler implements EpisimPersonStatusEventHandler, EpisimVaccinationEventHandler, EpisimInfectionEventHandler {

		 private final Map<Id<Person>, Holder> data;
		 private final LocalDate startDate;

		 /**
		  * Approximate end date. Last occurred date in events.
		  */
		 private int endDay;

		 public Handler(Map<Id<Person>, Holder> data, LocalDate startDate) {
			 this.data = data;
			 this.startDate = startDate;
		 }

		 @Override
		 public void handleEvent(EpisimInfectionEvent event) {

			 Holder attr = data.computeIfAbsent(event.getPersonId(), Holder::new);

			 int day = (int) (event.getTime() / 86_400);

			 if (day > endDay)
				 endDay = day;

			 attr.infections.add(day);
			 attr.strains.add(event.getVirusStrain());
		 }

		 @Override
		 public void handleEvent(EpisimPersonStatusEvent event) {

			 Holder attr = data.computeIfAbsent(event.getPersonId(), Holder::new);
			 DiseaseStatus status = event.getDiseaseStatus();

			 int day = (int) (event.getTime() / 86_400);
			 if (day > endDay)
				 endDay = day;

			 if (status.equals(DiseaseStatus.susceptible)) {
				 attr.recoveredDates.add(startDate.plusDays(day));
			 }
			 if (status.equals(DiseaseStatus.contagious)) {
				 attr.contagiousDates.add(startDate.plusDays(day));
			 }
//
//			attributes.putAttribute(status.toString(), startDate.plusDays((int) (event.getTime() / 86_400)));
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





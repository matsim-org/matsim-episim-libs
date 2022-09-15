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
 import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
 import it.unimi.dsi.fastutil.doubles.DoubleList;
 import it.unimi.dsi.fastutil.ints.*;
 import it.unimi.dsi.fastutil.objects.*;
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
 import org.matsim.episim.*;
 import org.matsim.episim.events.*;
 import org.matsim.episim.model.VirusStrain;
 import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
 import org.matsim.run.AnalysisCommand;
 import org.matsim.run.batch.CologneScenarioHubRound3;
 import picocli.CommandLine;

 import java.io.*;
 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.time.LocalDate;
 import java.util.*;
 import java.util.stream.Collectors;


 /**
  * Calculate hospital numbers from events
  */
 @CommandLine.Command(
		 name = "hospitalNumbers",
		 description = "Calculate hospital numbers from events"
 )
 public class HospitalNumbersFromEvents implements OutputAnalysis {

//	 @CommandLine.Option(names = "--output", defaultValue = "./output/")
	 @CommandLine.Option(names = "--output", defaultValue = "../public-svn/matsim/scenarios/countries/de/episim/battery/jakob/2022-08-02/3-vax/analysis/strainA")
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

	 private static final Logger log = LogManager.getLogger(HospitalNumbersFromEvents.class);


	 private final String DATE = "date";
	 private final String DAY = "day";


	 @Inject
	 private Scenario scenario;

	 private Population population;

	 // source: incidence wave vs. hospitalization wave in cologne/nrw (see https://docs.google.com/spreadsheets/d/1jmaerl27LKidD1uk3azdIL1LmvHuxazNQlhVo9xO1z8/edit?usp=sharing)
	 private static final Object2IntMap<VirusStrain> lagBetweenInfectionAndHospitalisation = renderLagBetweenInfectionAndHospitalisation();
	 private static Object2IntMap<VirusStrain> renderLagBetweenInfectionAndHospitalisation() {
		 Object2IntMap<VirusStrain> days = new Object2IntAVLTreeMap<>(
				 Map.of(VirusStrain.SARS_CoV_2, 14,
						 VirusStrain.ALPHA, 14,
						 VirusStrain.DELTA, 14,
						 VirusStrain.OMICRON_BA1, 14,
						 VirusStrain.OMICRON_BA2, 14,
						 VirusStrain.OMICRON_BA5, 14
				 ));

		 for (VirusStrain virusStrain : CologneScenarioHubRound3.newVirusStrains.values()) {
			 days.put(virusStrain, 14);
		 }

		 return days;
	 }


	  // source: hospitalization wave vs. ICU wave in cologne/nrw (see https://docs.google.com/spreadsheets/d/1jmaerl27LKidD1uk3azdIL1LmvHuxazNQlhVo9xO1z8/edit?usp=sharing)
	 private static final Object2IntMap<VirusStrain> lagBetweenHospitalizationAndICU = renderLagBetweenHospitalizationAndICU();

	 private static Object2IntAVLTreeMap<VirusStrain> renderLagBetweenHospitalizationAndICU() {
		 Object2IntAVLTreeMap<VirusStrain> days = new Object2IntAVLTreeMap<>(
				 Map.of(VirusStrain.SARS_CoV_2, 6,
						 VirusStrain.ALPHA, 6,
						 VirusStrain.DELTA, 6,
						 VirusStrain.OMICRON_BA1, 6,
						 VirusStrain.OMICRON_BA2, 6,
						 VirusStrain.OMICRON_BA5, 6
				 ));
		 for (VirusStrain virusStrain : CologneScenarioHubRound3.newVirusStrains.values()) {
			 days.put(virusStrain, 6);
		 }
		 return days;
	 }

	 // Austria study in https://docs.google.com/spreadsheets/d/1jmaerl27LKidD1uk3azdIL1LmvHuxazNQlhVo9xO1z8/edit#gid=0
	 private static final Object2IntMap<VirusStrain> daysInHospitalGivenNoICU = renderDaysInHospitalGivenNoICU();

	 private static Object2IntAVLTreeMap<VirusStrain> renderDaysInHospitalGivenNoICU() {
		 Object2IntAVLTreeMap<VirusStrain> days = new Object2IntAVLTreeMap<>(
				 Map.of(VirusStrain.SARS_CoV_2, 12,
						 VirusStrain.ALPHA, 12,
						 VirusStrain.DELTA, 12,
						 VirusStrain.OMICRON_BA1, 7,
						 VirusStrain.OMICRON_BA2, 7,
						 VirusStrain.OMICRON_BA5, 7
				 ));

		 for (VirusStrain virusStrain : CologneScenarioHubRound3.newVirusStrains.values()) {
			 days.put(virusStrain, 7);
		 }

		 return days;
	 }

	 private static final Object2IntMap<VirusStrain> daysInICU = renderDaysInICU();

	 private static Object2IntAVLTreeMap<VirusStrain> renderDaysInICU() {
		 Object2IntAVLTreeMap<VirusStrain> days = new Object2IntAVLTreeMap<>(
				 Map.of(VirusStrain.SARS_CoV_2, 15, // Debeka & Ireland studies
						 VirusStrain.ALPHA, 15, // Debeka & Ireland studies
						 VirusStrain.DELTA, 15, // this and following values come from nrw analysis on Tabellenblatt 5
						 VirusStrain.OMICRON_BA1, 10,
						 VirusStrain.OMICRON_BA2, 10,
						 VirusStrain.OMICRON_BA5, 10
				 ));

		 for (VirusStrain virusStrain : CologneScenarioHubRound3.newVirusStrains.values()) {
			 days.put(virusStrain, 10);
		 }

		 return days;
	 }

	 // ??
	 private static final Object2IntMap<VirusStrain> daysInHospitalGivenICU = renderDaysInHospitalGivenICU();

	 private static Object2IntAVLTreeMap<VirusStrain> renderDaysInHospitalGivenICU() {
		 Object2IntAVLTreeMap<VirusStrain> days = new Object2IntAVLTreeMap<>(
				 Map.of(VirusStrain.SARS_CoV_2, 60,
						 VirusStrain.ALPHA, 60,
						 VirusStrain.DELTA, 60,
						 VirusStrain.OMICRON_BA1, 60,
						 VirusStrain.OMICRON_BA2, 60,
						 VirusStrain.OMICRON_BA5, 60
				 ));

		 for (VirusStrain virusStrain : CologneScenarioHubRound3.newVirusStrains.values()) {
			 days.put(virusStrain, 60);
		 }

		 return days;
	 }


	 private static final double beta = 1.2;

	 private static final double hospitalFactor = 0.3;

	 // base
	 private static final double factorWild =  1.0;

	 private static final double factorAlpha = 1.0 * factorWild;

	 // delta: 2.3x more severe than alpha - Hospital admission and emergency care attendance risk for SARS-CoV-2 delta (B.1.617.2) compared with alpha (B.1.1.7) variants of concern: a cohort study
	 private static final double factorDelta = 1.2 * factorWild;//1.6 * factorWild;

	 // omicron: approx 0.3x (intrinsic) severity of delta - Comparative analysis of the risks of hospitalisation and death associated with SARS-CoV-2 omicron (B.1.1.529) and delta (B.1.617.2) variants in England: a cohort study
	 private static final double factorOmicron = 0.3  * factorDelta; //  reportedShareOmicron / reportedShareDelta

	 private static final double factorBA5 = 1.5 * factorOmicron;



	 // ??
	 private static final double factorWildAndAlphaICU = 1.;
	 private static final double factorDeltaICU = 1.;
	 private static final double factorOmicronICU = 1.;
	 private static final double factorBA5ICU = 1.;

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



		 // Part 1: calculate hospitalizations for each seed and save as csv
		 List<Path> pathList = new ArrayList<>();
		 AnalysisCommand.forEachScenario(output, pathToScenario -> {
			 try {
				 pathList.add(pathToScenario);
				 // analyzeOutput is where the hospitalization post processing occurs
				 analyzeOutput(pathToScenario);

			 } catch (IOException e) {
				 log.error("Failed processing {}", pathToScenario, e);
			 }
		 });

		 log.info("done");

		 return 0;
	 }

	 @Override
	 public void analyzeOutput(Path pathToScenario) throws IOException {

		 if (scenario != null)
			 population = scenario.getPopulation();

		 String id = AnalysisCommand.getScenarioPrefix(pathToScenario);

		 // builds the path to the output file that is produced by this analysis
		 final Path tsvPath = pathToScenario.resolve(id + "post.hospital.tsv");
		 final Path tsvPathAge = pathToScenario.resolve(id + "post.hospitalAgeBased.tsv");

		 // calculates hospitalizations
		 calculateHospitalizationsAndWriteOutput(pathToScenario, tsvPath,tsvPathAge);

		 log.info("Calculated results for output {}", pathToScenario);

	 }


	 /**
	  * calculates hospitalizations based on the events file for the scenario. This is done by calling
	  * the custom EventHandler, which is defined later in this class.
	  * @param pathToScenario path to the directory containing the output events file
	  * @param tsvPath filename for the hospitalization output produced by this method
	  * @throws IOException
	  */
	 private void calculateHospitalizationsAndWriteOutput(Path pathToScenario, Path tsvPath, Path tsvPathAge) throws IOException {
		 // open new buffered writer for hospitalization output and write the header row.
		 BufferedWriter bw = Files.newBufferedWriter(tsvPath);
		 bw.write(AnalysisCommand.TSV.join(DAY, DATE,"measurement", "severity", "n"));

		 // open new bw for age-based hospital admission
		 BufferedWriter bwAge = Files.newBufferedWriter(tsvPathAge);
		 bwAge.write(AnalysisCommand.TSV.join(DAY, DATE,"measurement", "severity", "ages","n"));

		 ConfigHolder holderOmicron = configure(factorBA5, factorBA5ICU);
		 ConfigHolder holderDelta = configure(factorDelta, factorDeltaICU);

		 List<Handler> handlers = List.of(
				 new Handler("Omicron", population, holderOmicron, 0.0, false),
				 new Handler("Delta", population, holderDelta, 0.0, false),
				 new Handler("Omicron-pessimistic", population, holderOmicron, 0.0, true),
				 new Handler("Delta-pessimistic", population, holderDelta, 0.0, true)
//				 new Handler("Omicron-Paxlovid-0.75", population, holderOmicron, 0.75),
//				 new Handler("Delta-Paxlovid-0.75", population, holderDelta, 0.75)
		 );

		 // feed the output events file to the handler, so that the hospitalizations may be calculated
		 List<String> eventFiles = AnalysisCommand.forEachEvent(pathToScenario, s -> {
		 }, true, handlers.toArray(new Handler[0]));


		 for (Handler handler : handlers) {

			 // calculates the number of agents in the scenario's population (25% sample) who live in Cologne
			 // this is used to normalize the hospitalization values
			 Set<Person> filteredPopulation = population.getPersons().values().stream()
					 .filter(x -> x.getAttributes().getAttribute("district").equals(district)).collect(Collectors.toSet());

			 double popSize = filteredPopulation.size();

			 Object2IntMap<EpisimReporting.AgeGroup> popSizePerAgeGroup = new Object2IntAVLTreeMap<>();
			 int sizeOfPreviousAgeGroup = 0;
			 for (EpisimReporting.AgeGroup ageGroup : EpisimReporting.AgeGroup.values()) {
				 int popAboveLowerBound = (int) filteredPopulation.stream().filter(person -> ((int) person.getAttributes().getAttribute("microm:modeled:age")) >= ageGroup.lowerBoundAge).count();
				 int popForAgeGroup = popAboveLowerBound - sizeOfPreviousAgeGroup;
				 popSizePerAgeGroup.put(ageGroup, popForAgeGroup);
				 sizeOfPreviousAgeGroup = popAboveLowerBound;
			 }


			 for (int day = 0; day < eventFiles.size(); day++) {
				 LocalDate date = startDate.plusDays(day);

				 // calculates Incidence - 7day hospitalizations per 100,000 residents
				 double intakesHosp = getWeeklyHospitalizations(handler.postProcessHospitalAdmissions, day) * 100_000. / popSize;

				 double intakesIcu = getWeeklyHospitalizations(handler.postProcessICUAdmissions, day) * 100_000. / popSize;

				 // calculates daily hospital occupancy, per 100,000 residents
				 double occupancyHosp = handler.postProcessHospitalFilledBeds.getOrDefault(day, 0) * 100_000. / popSize;
				 double occupancyIcu = handler.postProcessHospitalFilledBedsICU.getOrDefault(day, 0) * 100_000. / popSize;

				 bw.newLine();
				 bw.write(AnalysisCommand.TSV.join(day, date, HospitalNumbersFromEventsPlotter.INTAKES_HOSP, handler.name , intakesHosp));
				 bw.newLine();
				 bw.write(AnalysisCommand.TSV.join(day, date, HospitalNumbersFromEventsPlotter.INTAKES_ICU, handler.name, intakesIcu));
				 bw.newLine();
				 bw.write(AnalysisCommand.TSV.join(day, date, HospitalNumbersFromEventsPlotter.OCCUPANCY_HOSP, handler.name, occupancyHosp));
				 bw.newLine();
				 bw.write(AnalysisCommand.TSV.join(day, date, HospitalNumbersFromEventsPlotter.OCCUPANCY_ICU, handler.name, occupancyIcu));

				 for (EpisimReporting.AgeGroup ageGroup : EpisimReporting.AgeGroup.values()) {
					 bwAge.newLine();
					 double intakesHospAge = getWeeklyHospitalizations(handler.postProcessHospitalAdmissions, day, ageGroup) * 100_000. / popSizePerAgeGroup.getInt(ageGroup);
					 bwAge.write(AnalysisCommand.TSV.join(day, date.toString(), HospitalNumbersFromEventsPlotter.INTAKES_HOSP, handler.name , ageGroup.name(), intakesHospAge));
				 }
			 }

		 }

		 bw.close();
		 bwAge.close();

	 }


	 static int getWeeklyHospitalizations(Int2IntMap hospMap, Integer today) {
		 int weeklyHospitalizations = 0;
		 for (int i = 0; i < 7; i++) {
			 try {
				 weeklyHospitalizations += hospMap.getOrDefault(today - i, 0);
			 } catch (Exception ignored) {

			 }
		 }
		 return weeklyHospitalizations;
	 }

	 static int getWeeklyHospitalizations(Int2ObjectMap<Object2IntMap<EpisimReporting.AgeGroup>> hospMap, Integer today) {
		 int weeklyHospitalizations = 0;
		 for (int i = 0; i < 7; i++) {
			 try {
				 Object2IntMap<EpisimReporting.AgeGroup> map = hospMap.getOrDefault(today - i, new Object2IntAVLTreeMap<>());
				 weeklyHospitalizations += map.values().intStream().sum();

			 } catch (Exception ignored) {

			 }
		 }
		 return weeklyHospitalizations;
	 }

	 static int getWeeklyHospitalizations(Int2ObjectMap<Object2IntMap<EpisimReporting.AgeGroup>> hospMap, Integer today, EpisimReporting.AgeGroup ageGroup) {
		 int weeklyHospitalizations = 0;
		 for (int i = 0; i < 7; i++) {
			 try {
				 Object2IntMap<EpisimReporting.AgeGroup> map = hospMap.getOrDefault(today - i, new Object2IntAVLTreeMap<>());
				 weeklyHospitalizations += map.getOrDefault(ageGroup, 0);

			 } catch (Exception e) {
				 e.printStackTrace();

			 }
		 }
		 return weeklyHospitalizations;
	 }


	 public static final class Handler implements EpisimVaccinationEventHandler, EpisimInfectionEventHandler {



		 final Map<Id<Person>, ImmunizablePerson> data;
		 private final String name;
		 private final Population population;
		 private final Random rnd;
		 private final ConfigHolder holder;

		 private final double paxlovidCompliance;

		 private final int paxlovidDay;

//		 final Int2IntSortedMap postProcessHospitalAdmissions;
		 Int2ObjectMap<Object2IntMap<EpisimReporting.AgeGroup>> postProcessHospitalAdmissions;
		 final Int2IntSortedMap postProcessICUAdmissions;
		 final Int2IntSortedMap postProcessHospitalFilledBeds;
		 final Int2IntSortedMap postProcessHospitalFilledBedsICU;
		 private final AgeDependentDiseaseStatusTransitionModel transitionModel;
		 private final boolean pessimisticScenario;


		 Handler(String name, Population population, ConfigHolder holder, double paxlovidCompliance, boolean pessimisticScenario) {

			 // instantiate the custom event handler that calculates hospitalizations based on events
			 this.name = name;
			 this.data =  new IdMap<>(Person.class, population.getPersons().size());
			 this.population = population;
			 this.rnd = new Random(1234);
			 this.holder = holder;
			 this.paxlovidCompliance = paxlovidCompliance;
			 this.paxlovidDay = (int) LocalDate.of(2020, 2, 25).datesUntil(LocalDate.of(2022, 11, 1)).count();

			 this.pessimisticScenario = pessimisticScenario;
//			 this.postProcessHospitalAdmissions = new Int2IntAVLTreeMap();
			 this.postProcessHospitalAdmissions = new Int2ObjectAVLTreeMap<>();

			 this.postProcessICUAdmissions = new Int2IntAVLTreeMap();
			 this.postProcessHospitalFilledBeds = new Int2IntAVLTreeMap();
			 this.postProcessHospitalFilledBedsICU = new Int2IntAVLTreeMap();
			 this.transitionModel = new AgeDependentDiseaseStatusTransitionModel(new SplittableRandom(1234), holder.episimConfig, holder.vaccinationConfig, holder.strainConfig);

		 }

		 @Override
		 public void handleEvent(EpisimInfectionEvent event) {

			 ImmunizablePerson person = data.computeIfAbsent(event.getPersonId(),
					 personId -> new ImmunizablePerson(personId, getAge(personId)));

			 String district = (String) population.getPersons().get(person.personId).getAttributes().getAttribute("district");

			 if (!district.equals("Köln")){
				 return;
			 }

			 person.addInfection(event.getTime());
			 person.setAntibodyLevelAtInfection(event.getAntibodies());
			 person.setVirusStrain(event.getVirusStrain());

			 int day = (int) (event.getTime() / 86_400);

			 updateHospitalizationsPost(person, event.getVirusStrain(), day);

		 }



		 @Override
		 public void handleEvent(EpisimVaccinationEvent event) {
			 ImmunizablePerson person = data.computeIfAbsent(event.getPersonId(), personId -> new ImmunizablePerson(personId, getAge(personId)));

			 String district = (String) population.getPersons().get(person.personId).getAttributes().getAttribute("district");

			 if (!district.equals("Köln")){
				 return;
			 }

			 int day = (int) (event.getTime() / 86_400);

			 person.addVaccination(day);
		 }

		 private int getAge(Id<Person> personId) {
			 return (int) population.getPersons().get(personId).getAttributes().getAttribute("microm:modeled:age");
		 }


		 private void updateHospitalizationsPost(ImmunizablePerson person, VirusStrain strain, int infectionIteration) {



			 if (!lagBetweenInfectionAndHospitalisation.containsKey(strain)
					 || !lagBetweenHospitalizationAndICU.containsKey(strain)
					 || !daysInHospitalGivenNoICU.containsKey(strain)
					 || !daysInICU.containsKey(strain)
					 || !daysInHospitalGivenICU.containsKey(strain)) {
				 throw new RuntimeException("strain " + strain + " not registered in all data structures which describe length of stay in hospital");
			 }


			 if (goToHospital(person, infectionIteration)) {

				 // newly admitted to hospital
				 int inHospital = infectionIteration + lagBetweenInfectionAndHospitalisation.getInt(strain);
				 boolean personAssignedToSingleAgeGroup = false;
				 for (EpisimReporting.AgeGroup ageGroup : EpisimReporting.AgeGroup.values()) {
					 if (person.getAge() >= ageGroup.lowerBoundAge) {
						 Object2IntMap<EpisimReporting.AgeGroup> admissionsPerAgeGroup = postProcessHospitalAdmissions.getOrDefault(inHospital, new Object2IntOpenHashMap<>());
						 admissionsPerAgeGroup.mergeInt(ageGroup, 1, Integer::sum);
						 postProcessHospitalAdmissions.put(inHospital, admissionsPerAgeGroup);
						 personAssignedToSingleAgeGroup = true;
						 break;

					 }
				 }

				 if (!personAssignedToSingleAgeGroup) {
					 throw new RuntimeException("Person needs to be assigned to at least one age group");
				 }




				 if (goToICU(person, inHospital)) {

					 // newly admitted to ICU
					 int inICU = inHospital + lagBetweenHospitalizationAndICU.getInt(strain);
					 postProcessICUAdmissions.mergeInt(inHospital, 1, Integer::sum);


					 int outICU = inICU + daysInICU.getInt(strain);
					 int outHospital = inHospital + daysInHospitalGivenICU.getInt(strain);

					 if (outICU > outHospital) {
						 throw new RuntimeException("Agent cannot leave ICU after leaving hospital");
					 }

					 // total days in hospital (in or out of ICU)
					 for (int day = inHospital; day < outHospital; day++) {
						 postProcessHospitalFilledBeds.mergeInt(day, 1, Integer::sum);
					 }

					 //days in ICU (critical)
					 for (int day = inICU; day < outICU; day++) {
						 postProcessHospitalFilledBedsICU.mergeInt(day, 1, Integer::sum);
					 }


				 } else {
					 int outHospital = inHospital + daysInHospitalGivenNoICU.getInt(strain);
					 //days in regular part of hospital
					 for (int day = inHospital; day < outHospital; day++) {
						 postProcessHospitalFilledBeds.mergeInt(day, 1, Integer::sum);
					 }
				 }
			 }

		 }



		 /**
		  * calculates the probability that agent goes to hospital given an infection.
		  */
		 private boolean goToHospital(ImmunizablePerson person, int day) {

			 double ageFactor = transitionModel.getProbaOfTransitioningToSeriouslySick(person);
			 double strainFactor = holder.strainConfig.getParams(person.getVirusStrain()).getFactorSeriouslySick();
			 double immunityFactor = transitionModel.getSeriouslySickFactor(person, holder.vaccinationConfig, day);

			 double paxlovidFactor = 1.0;
			 if (person.getAge() > 60 && day >= this.paxlovidDay) {
				 if (rnd.nextDouble() < this.paxlovidCompliance) {
					 paxlovidFactor = 0.33; // todo
				 }
			 }

			 double chanceOfHospitalisation = ageFactor
					 * strainFactor
					 * Math.min((immunityFactor + (this.pessimisticScenario ? 0.2 : 0.0)), 1.0)
					 * paxlovidFactor;

			 return rnd.nextDouble() < chanceOfHospitalisation;
		 }

		 /**
		  * calculates the probability that agent goes to into critical care (ICU) given hospitalization
		  */
		 private boolean goToICU(ImmunizablePerson person, int day) {


			 double ageFactor = transitionModel.getProbaOfTransitioningToCritical(person);
			 double strainFactor = holder.strainConfig.getParams(person.getVirusStrain()).getFactorCritical();
			 double immunityFactor =  transitionModel.getCriticalFactor(person, holder.vaccinationConfig, day); //todo: revert

			 return rnd.nextDouble() < ageFactor
					 * strainFactor
					 * immunityFactor;
		 }



		 /**
		  * Data holder for attributes
		  */
		 static final class ImmunizablePerson implements Immunizable{


			 /**
			  * Id of person which this data structure emulates.
			  */
			 private final Id<Person> personId;

			 /**
			  * Iteration when this person was vaccinated.
			  */
			 private final IntList vaccinationDates = new IntArrayList();

			 /**
			  * Second at which a person is infected (divide by 24*60*60 to get iteration/day)
			  */
			 private final DoubleList infectionDates = new DoubleArrayList();

			 /**
			  * Virus strain of most recent (or current) infection
			  */
			 private VirusStrain strain;

			 /**
			  * Antibody level at last infection.
			  */
			 private double antibodyLevelAtInfection = 0;
			 private int age;

			 ImmunizablePerson(Id<Person> personId, int age) {
				 this.personId = personId;
				 this.age = age;
			 }

			 @Override
			 public Id<Person> getPersonId() {
				 return this.personId;
			 }

			 @Override
			 public int getNumVaccinations() {
				 return vaccinationDates.size();
			 }

			 @Override
			 public int getNumInfections() {
				 return infectionDates.size();
			 }

			 public void setVirusStrain(VirusStrain strain) {
				 this.strain = strain;
			 }


			 @Override
			 public VirusStrain getVirusStrain() {
				 return strain;
			 }

			 public void addVaccination(int day) {
				 vaccinationDates.add(day);
			 }

			 @Override
			 public IntList getVaccinationDates() {
				 return this.vaccinationDates;
			 }


			 public void addInfection(double seconds) {
				 this.infectionDates.add(seconds);
			 }

			 @Override
			 public DoubleList getInfectionDates() {
				 return this.infectionDates;
			 }


			 public void setAntibodyLevelAtInfection(double antibodyLevelAtInfection) {
				 this.antibodyLevelAtInfection = antibodyLevelAtInfection;
			 }

			 @Override
			 public double getAntibodyLevelAtInfection() {
				 return antibodyLevelAtInfection;
			 }

			 @Override
			 public boolean hadDiseaseStatus(EpisimPerson.DiseaseStatus status) {
				 throw new UnsupportedOperationException("this method is not supported by Holder");

			 }

			 @Override
			 public int daysSince(EpisimPerson.DiseaseStatus status, int day) {
				 throw new UnsupportedOperationException("this method is not supported by Holder");

			 }

			 @Override
			 public int getAge() {
				 return this.age;
			 }


		 }

	 }



	 /**
	  * This method configures the episim config, vaccination config, and strain config to the extent
	  * necessary for post processing.
	  * @param
	  */
	 private ConfigHolder configure(double facA, double facAICU) {

		 Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		 // configure episimConfig
		 EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		 episimConfig.setHospitalFactor(hospitalFactor);

		 // configure strainConfig: add factorSeriouslySick for each strain
		 VirusStrainConfigGroup strainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		 strainConfig.getOrAddParams(VirusStrain.SARS_CoV_2).setFactorSeriouslySick(factorWild);
		 strainConfig.getOrAddParams(VirusStrain.SARS_CoV_2).setFactorCritical(factorWildAndAlphaICU);
		 strainConfig.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySick(factorAlpha);
		 strainConfig.getOrAddParams(VirusStrain.ALPHA).setFactorCritical(factorAlpha);

		 strainConfig.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySick(factorDelta);
		 strainConfig.getOrAddParams(VirusStrain.DELTA).setFactorCritical(factorDeltaICU);

		 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySick(factorOmicron);
		 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorCritical(factorOmicronICU);
		 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorSeriouslySick(factorOmicron);
		 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorCritical(factorOmicronICU);
		 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySick(factorBA5);
		 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorCritical(factorBA5ICU);

//		 strainConfig.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(facA);
//		 strainConfig.getOrAddParams(VirusStrain.STRAIN_A).setFactorCritical(facAICU);
//
//		 strainConfig.getOrAddParams(VirusStrain.STRAIN_B).setFactorSeriouslySick(facA);
//		 strainConfig.getOrAddParams(VirusStrain.STRAIN_B).setFactorCritical(facAICU);

		 for (VirusStrain virusStrain : CologneScenarioHubRound3.newVirusStrains.values()) {
			 strainConfig.getOrAddParams(virusStrain).setFactorSeriouslySick(facA);
			 strainConfig.getOrAddParams(virusStrain).setFactorCritical(facAICU);
		 }





		 // configure vaccinationConfig: set beta factor
		 VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		 vaccinationConfig.setBeta(beta);

		 return new ConfigHolder(episimConfig, vaccinationConfig, strainConfig);
	 }

	 private static final class ConfigHolder {
		 private final EpisimConfigGroup episimConfig;
		 private final VaccinationConfigGroup vaccinationConfig;
		 private final VirusStrainConfigGroup strainConfig;


		 private ConfigHolder(EpisimConfigGroup episimConfig, VaccinationConfigGroup vaccinationConfig, VirusStrainConfigGroup strainConfig) {
			 this.episimConfig = episimConfig;
			 this.vaccinationConfig = vaccinationConfig;
			 this.strainConfig = strainConfig;
		 }
	 }
 }


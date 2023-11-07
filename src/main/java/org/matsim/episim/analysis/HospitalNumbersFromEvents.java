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

	 @CommandLine.Option(names = "--output", defaultValue = "/Users/jakob/git/matsim-episim/2023-10-27/events_hosp")
//	 @CommandLine.Option(names = "--output", defaultValue = "/Users/jakob/git/matsim-episim/2023-10-06/1/output/")
//	 @CommandLine.Option(names = "--output", defaultValue = "/Users/jakob/git/matsim-episim/A_originalImmHist")
//	 @CommandLine.Option(names = "--output", defaultValue = "/Users/jakob/git/matsim-episim/B_startedFromImmHist")
//	 @CommandLine.Option(names = "--output", defaultValue = "/Users/jakob/git/public-svn/matsim/scenarios/countries/de/episim/battery/jakob/2022-10-18/3-meas/analysis/")
	 private Path output;

//	 @CommandLine.Option(names = "--input", defaultValue = "/scratch/projects/bzz0020/episim-input")
	 @CommandLine.Option(names = "--input", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input")
	 private String input;

	 @CommandLine.Option(names = "--population-file", defaultValue = "/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz")
	 private String populationFile;

//	 @CommandLine.Option(names = "--start-date", defaultValue = "2022-04-01")
	 @CommandLine.Option(names = "--start-date", defaultValue = "2020-02-25")
	 private LocalDate startDate;

	 @CommandLine.Option(names = "--district", description = "District to filter for", defaultValue = "Köln")
	 private String district;

	 private static final Logger log = LogManager.getLogger(HospitalNumbersFromEvents.class);


	 private final String DATE = "date";
	 private final String DAY = "day";


	 @Inject
	 private Scenario scenario;

	 private Population population;

	 // TODO: check age or strain based lags in literature
	 // source: incidence wave vs. hospitalization wave in cologne/nrw (see https://docs.google.com/spreadsheets/d/1jmaerl27LKidD1uk3azdIL1LmvHuxazNQlhVo9xO1z8/edit?usp=sharing)
	 private static final Object2IntMap<VirusStrain> lagBetweenInfectionAndHospitalisation = setLagBetweenInfectionAndHospitalisation();

	 private static Object2IntAVLTreeMap<VirusStrain> setLagBetweenInfectionAndHospitalisation() {
		 Object2IntAVLTreeMap<VirusStrain> lagBetweenInfectionAndHospitalisation = new Object2IntAVLTreeMap<>();

		 for (VirusStrain strain : VirusStrain.values()) {
			 lagBetweenInfectionAndHospitalisation.put(strain, 14);
		 }

		 return lagBetweenInfectionAndHospitalisation;
	 }

	 // source: hospitalization wave vs. ICU wave in cologne/nrw (see https://docs.google.com/spreadsheets/d/1jmaerl27LKidD1uk3azdIL1LmvHuxazNQlhVo9xO1z8/edit?usp=sharing)
	 private static final Object2IntMap<VirusStrain> lagBetweenHospitalizationAndICU = setLagBetweenHospitalizationAndICU();

	 private static Object2IntAVLTreeMap<VirusStrain> setLagBetweenHospitalizationAndICU() {
		 Object2IntAVLTreeMap<VirusStrain> lagBetweenHospitalizationAndICU = new Object2IntAVLTreeMap<>();

		 for (VirusStrain strain : VirusStrain.values()) {
			 lagBetweenHospitalizationAndICU.put(strain, 6);
		 }

		 return lagBetweenHospitalizationAndICU;

	 }

	 // Austria study in https://docs.google.com/spreadsheets/d/1jmaerl27LKidD1uk3azdIL1LmvHuxazNQlhVo9xO1z8/edit#gid=0
	 private static final Object2IntMap<VirusStrain> daysInHospitalGivenNoICU = setDaysInHospitalGivenNoICU();

	 private static Object2IntAVLTreeMap<VirusStrain> setDaysInHospitalGivenNoICU() {
		 Object2IntAVLTreeMap<VirusStrain> daysInHospitalGivenNoICU = new Object2IntAVLTreeMap<>();
		 for (VirusStrain strain : VirusStrain.values()) {
			 daysInHospitalGivenNoICU.put(strain, 7);
		 }

		 daysInHospitalGivenNoICU.put(VirusStrain.SARS_CoV_2, 12);
		 daysInHospitalGivenNoICU.put(VirusStrain.ALPHA, 12);
		 daysInHospitalGivenNoICU.put(VirusStrain.DELTA, 12);

		 return daysInHospitalGivenNoICU;
	 }

	 private static final Object2IntMap<VirusStrain> daysInICU = setDaysInICU();

	 private static Object2IntAVLTreeMap<VirusStrain> setDaysInICU() {
		 Object2IntAVLTreeMap<VirusStrain> daysInICU = new Object2IntAVLTreeMap<>();

		 for (VirusStrain strain : VirusStrain.values()) {
			 daysInICU.put(strain, 10);
		 }

		 daysInICU.put(VirusStrain.SARS_CoV_2, 15);
		 daysInICU.put(VirusStrain.ALPHA, 15);
		 daysInICU.put(VirusStrain.DELTA, 15);

		 return daysInICU;
	 }

	 // ??
	 private static final Object2IntMap<VirusStrain> daysInHospitalGivenICU = setDaysInHospitalGivenICU();

	 private static Object2IntAVLTreeMap<VirusStrain> setDaysInHospitalGivenICU() {
		 Object2IntAVLTreeMap<VirusStrain> daysInHospitalGivenICU = new Object2IntAVLTreeMap<>();

		 for (VirusStrain strain : VirusStrain.values()) {
			 daysInHospitalGivenICU.put(strain, 60);
		 }

		 return daysInHospitalGivenICU;
	 }


	 private static final double beta = 1.2;

	 private static final double hospitalFactor = 0.3; // Based on "guess & check", accounts for unreported cases TODO: Potential follow-up

	 private static final Map<VirusStrain, Double> seriouslySickFactorModifier_BASE = Map.of(
		 VirusStrain.DELTA, 1.2,
		 VirusStrain.OMICRON_BA1, 0.45
		 );

	 private static final Map<VirusStrain, Double> seriouslySickFactorModifier_MILD = Map.of(
		 VirusStrain.DELTA, 1.2,
		 VirusStrain.OMICRON_BA1, 0.45,
		 VirusStrain.OMICRON_BA5, 1.2
		 );

//	 private static final Map<VirusStrain, Double> seriouslySickFactorModifier_MILD = Map.of(
//		 VirusStrain.DELTA, 1.2,
//		 VirusStrain.OMICRON_BA1, 0.45,
//		 VirusStrain.OMICRON_BA5, 1.2
//	 );


	 private static final Map<VirusStrain, Double> seriouslySickFactorModifier_SEVERE = Map.of(
		 VirusStrain.DELTA, 1.2,
		 VirusStrain.OMICRON_BA1, 0.45,
		 VirusStrain.OMICRON_BA5, 1.2,
		 VirusStrain.A_1, 1.5
	 );


	 // ICU: so far, we assume no difference between strains
	 private static final double factorICU = 1.; // TODO : Check literature for reasonable values
	 public static void main(String[] args) {
		 System.exit(new CommandLine(new HospitalNumbersFromEvents()).execute(args));
	 }

	 @Override
	 public Integer call() throws Exception {
		 // logger configuration
		 Configurator.setLevel("org.matsim.core.config", Level.WARN);
		 Configurator.setLevel("org.matsim.core.controler", Level.WARN);
		 Configurator.setLevel("org.matsim.core.events", Level.WARN);
		 Configurator.setLevel("org.matsim.core.utils", Level.WARN);

		 // check if events file exists
		 if (!Files.exists(output)) {
			 log.error("Output path {} does not exist.", output);
			 return 2;
		 }

		 // read population
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

			 // Part 2: aggregate over multiple seeds & produce tsv output & plot
//			 HospitalNumbersFromEventsPlotter.aggregateAndProducePlots(output, pathList);

		 //TODO: move to other class
//		 HospitalNumbersFromEventsPlotter.aggregateAndProducePlots(output, pathList, "_Base", startDate, "Base");
//		 HospitalNumbersFromEventsPlotter.aggregateAndProducePlots(output, pathList, "_Waning", startDate, "Waning");
//		 HospitalNumbersFromEventsPlotter.aggregateAndProducePlots(output, pathList, "_Mild", startDate, "Mild");
//		 HospitalNumbersFromEventsPlotter.aggregateAndProducePlots(output, pathList, "_Severe", startDate, "Severe");


//		 }


		 return 0;
	 }

	 @Override
	 public void analyzeOutput(Path pathToScenario) throws IOException {

		 if (scenario != null)
			 population = scenario.getPopulation();

		 String id = AnalysisCommand.getScenarioPrefix(pathToScenario);

		 // builds the path to the output file that is produced by this analysis
		 final Path tsvPath = pathToScenario.resolve(id + "post.hospital.tsv");

		 // calculates hospitalizations
		 calculateHospitalizationsAndWriteOutput(pathToScenario, tsvPath);

		 log.info("Calculated results for output {}", pathToScenario);

	 }


	 /**
	  * calculates hospitalizations based on the events file for the scenario. This is done by calling
	  * the custom EventHandler, which is defined later in this class.
	  * @param pathToScenario path to the directory containing the output events file
	  * @param tsvPath filename for the hospitalization output produced by this method
	  * @throws IOException
	  */
	 private void calculateHospitalizationsAndWriteOutput(Path pathToScenario, Path tsvPath) throws IOException {
		 // open new buffered writer for hospitalization output and write the header row.
		 BufferedWriter bw = Files.newBufferedWriter(tsvPath);
		 bw.write(AnalysisCommand.TSV.join(DAY, DATE,"measurement", "severity", "n")); // + "\thospNoImmunity\thospBaseImmunity\thospBoosted\tincNoImmunity\tincBaseImmunity\tincBoosted"));


		 ConfigHolder holderBase = configure(seriouslySickFactorModifier_BASE);
//		 ConfigHolder holderMild = configure(seriouslySickFactorModifier_MILD);
		 ConfigHolder holderSevere = configure(seriouslySickFactorModifier_SEVERE);

		 List<Handler> handlers = List.of(
			 new Handler("Base", population, holderBase),
//			 new Handler("Mild", population, holderMild),
			 new Handler("Severe", population, holderSevere)
		 );

		 // feed the output events file to the handler, so that the hospitalizations may be calculated
		 List<String> eventFiles = AnalysisCommand.forEachEvent(pathToScenario, s -> {
		 }, true, handlers.toArray(new Handler[0]));

		 for (Handler handler : handlers) {


			 // calculates the number of agents in the scenario's population (25% sample) who live in Cologne
			 // this is used to normalize the hospitalization values
			 double popSize = (int) population.getPersons().values().stream()
				 .filter(x -> x.getAttributes().getAttribute("district").equals(district)).count();


			 // calcualtes population in each age bin.
			 Int2LongAVLTreeMap popSizeByAge = new Int2LongAVLTreeMap(Collections.reverseOrder());
			 long popAboveUpperBound = 0;
			 for (int lowerBound : handler.postProcessHospitalAdmissionsByAge.keySet()) {
				 long popAboveLowerBound = (long) population.getPersons().values().stream()
					 .filter(x -> x.getAttributes().getAttribute("district").equals(district))
					 .filter(x -> (int) x.getAttributes().getAttribute("microm:modeled:age") >= lowerBound).count();

				 long popInBin = popAboveLowerBound - popAboveUpperBound;
				 popAboveUpperBound = popAboveLowerBound;
				 popSizeByAge.put(lowerBound, popInBin);
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
				 bw.write(AnalysisCommand.TSV.join(day, date, HospitalNumbersFromEventsPlotter.INTAKES_HOSP, handler.name, intakesHosp));

				 List<Integer> ages = handler.postProcessHospitalAdmissionsByAge.keySet().stream().sorted().collect(Collectors.toList());

				 for (int i = 0; i < ages.size(); i++) {
					 int lowerBound = ages.get(i);
					 String lab = String.valueOf(lowerBound) + (i < ages.size() -1 ? "to" + (ages.get(i + 1) - 1) : "+");
					 double incidenceForAgeBin = getWeeklyHospitalizations(handler.postProcessHospitalAdmissionsByAge.get(lowerBound), day) * 100_000. / popSizeByAge.get(lowerBound);

					 bw.newLine();
					 bw.write(AnalysisCommand.TSV.join(day, date, HospitalNumbersFromEventsPlotter.INTAKES_HOSP + "_" + lab, handler.name, incidenceForAgeBin));
				 }

				 bw.newLine();
				 bw.write(AnalysisCommand.TSV.join(day, date, HospitalNumbersFromEventsPlotter.INTAKES_ICU, handler.name, intakesIcu));
				 bw.newLine();
				 bw.write(AnalysisCommand.TSV.join(day, date, HospitalNumbersFromEventsPlotter.OCCUPANCY_HOSP, handler.name, occupancyHosp));
				 bw.newLine();
				 bw.write(AnalysisCommand.TSV.join(day, date, HospitalNumbersFromEventsPlotter.OCCUPANCY_ICU, handler.name, occupancyIcu));

			 }
		 }

		 bw.close();
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


	 public static final class Handler implements EpisimInfectionEventHandler, EpisimInitialInfectionEventHandler{
		 final Map<Id<Person>, ImmunizablePerson> data;
		 private final String name;
		 private final Population population;
		 private final Random rnd;
		 private final ConfigHolder holder;

		 final Int2IntSortedMap postProcessHospitalAdmissions;
		 final Int2IntSortedMap postProcessICUAdmissions;
		 final Int2IntSortedMap postProcessHospitalFilledBeds;
		 final Int2IntSortedMap postProcessHospitalFilledBedsICU;

		 private final AgeDependentDiseaseStatusTransitionModel transitionModel;
		 private final Int2ObjectAVLTreeMap<Int2IntAVLTreeMap> postProcessHospitalAdmissionsByAge;


		 Handler(String name, Population population, ConfigHolder holder) {

			 // instantiate the custom event handler that calculates hospitalizations based on events
			 this.name = name;
			 this.data =  new IdMap<>(Person.class, population.getPersons().size());
			 this.population = population;
			 this.rnd = new Random(1234);
			 this.holder = holder;

			 // key : iteration, value : admissions/filled beds
			 this.postProcessHospitalAdmissions = new Int2IntAVLTreeMap();
			 this.postProcessHospitalAdmissionsByAge = new Int2ObjectAVLTreeMap<>(Collections.reverseOrder());

			 Integer[] ageBins = {0, 18, 60, 80};

			 for (int ageLowerBound : ageBins) {
				 this.postProcessHospitalAdmissionsByAge.put(ageLowerBound, new Int2IntAVLTreeMap());
			 }


			 this.postProcessICUAdmissions = new Int2IntAVLTreeMap();
			 this.postProcessHospitalFilledBeds = new Int2IntAVLTreeMap();
			 this.postProcessHospitalFilledBedsICU = new Int2IntAVLTreeMap();

			 this.transitionModel = new AgeDependentDiseaseStatusTransitionModel(new SplittableRandom(1234), holder.episimConfig, holder.vaccinationConfig, holder.strainConfig);

//			 try {
//				 this.printer = new CSVPrinter(Files.newBufferedWriter(Path.of("hospCalibration.tsv")), CSVFormat.DEFAULT.withDelimiter('\t'));
//				 printer.printRecord("day", "date", "personId", "age", "strain", "numInfections","numVaccinations");
//			 } catch (IOException ex) {
//				 ex.printStackTrace();
//			 }
		 }


		 @Override
		 public void handleEvent(EpisimInfectionEvent event) {


			 ImmunizablePerson person = data.computeIfAbsent(event.getPersonId(),
					 personId -> new ImmunizablePerson(personId, getAge(personId)));

			 String district = (String) population.getPersons().get(person.personId).getAttributes().getAttribute("district");

			 if (!district.equals("Köln")){
				 return;
			 }

			 VirusStrain virusStrain = event.getVirusStrain();
			 person.addInfection(event.getTime());
			 person.setVirusStrain(virusStrain);
			 person.setNumVaccinations(event.getNumVaccinations());

			 person.updateMaxAntibodies(virusStrain, event.getMaxAntibodies());

			 int day = (int) (event.getTime() / 86_400);


			 updateHospitalizationsPost(person, virusStrain, day);


			 // print to csv

//			 System.out.println(event.getVirusStrain().toString());

//			 try {
//				 this.printer.printRecord(String.valueOf(day)
//						 , LocalDate.of(2020, 2, 25).plusDays(day).toString()
//						 , person.getPersonId().toString()
//						 , String.valueOf(person.getAge())
//						 , event.getVirusStrain().toString()
//						 , String.valueOf(person.getNumInfections() - 1)
//						 , String.valueOf(person.getNumVaccinations()));
//
//			 } catch (IOException e) {
//				 e.printStackTrace();
//			 }

		 }

		 @Override
		 public void handleEvent(EpisimInitialInfectionEvent event) {
			 handleEvent(event.asInfectionEvent());
		 }


//		 @Override
//		 public void handleEvent(EpisimVaccinationEvent event) {
//
////			 if (!event.getPersonId().toString().equals("12102f5"))
////				 return;
//
//			 ImmunizablePerson person = data.computeIfAbsent(event.getPersonId(), personId -> new ImmunizablePerson(personId, getAge(personId)));
//
//			 String district = (String) population.getPersons().get(person.personId).getAttributes().getAttribute("district");
//
//			 if (!district.equals("Köln")){
//				 return;
//			 }
//
//			 int day = (int) (event.getTime() / 86_400);
//
//			 if (person.getNumVaccinations()==0) {
//				 changeBaseImmunity.mergeInt(day, 1, Integer::sum);
//				 changeNoImmunity.mergeInt(day, -1, Integer::sum);
//			 } else if (person.getNumVaccinations() == 1) {
//				 changeBoostered.mergeInt(day, 1, Integer::sum);
//				 changeBaseImmunity.mergeInt(day, -1, Integer::sum);
//			 }
//
//
//			 person.addVaccination(day);

//		 }

		 private int getAge(Id<Person> personId) {
			 return (int) population.getPersons().get(personId).getAttributes().getAttribute("microm:modeled:age");
		 }

		 private void updateHospitalizationsPost(ImmunizablePerson person, VirusStrain strain, int infectionIteration) {

			 // check whether we entered all information for the strain
			 if (!lagBetweenInfectionAndHospitalisation.containsKey(strain)
					 || !lagBetweenHospitalizationAndICU.containsKey(strain)
					 || !daysInHospitalGivenNoICU.containsKey(strain)
					 || !daysInICU.containsKey(strain)
					 || !daysInHospitalGivenICU.containsKey(strain)) {
				 throw new RuntimeException("strain " + strain + " not registered in all data structures which describe length of stay in hospital");
			 }


			 // check if go to hospital
			 if (goToHospital(person, infectionIteration)) {

				 // newly admitted to hospital
				 int inHospital = infectionIteration + lagBetweenInfectionAndHospitalisation.getInt(strain);
				 postProcessHospitalAdmissions.mergeInt(inHospital, 1, Integer::sum);


				 for (int lowerBound : postProcessHospitalAdmissionsByAge.keySet()) {
					 if (person.age >= lowerBound) {
						 postProcessHospitalAdmissionsByAge.get(lowerBound).merge(inHospital, 1, Integer::sum);
						 break;
					 }
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

			 return rnd.nextDouble() < ageFactor
					 * strainFactor
					 * immunityFactor;
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

			 /**
			  * Maximal antibody level reached by agent w/ respect to each strain
			  */
			 private final Object2DoubleMap<VirusStrain> maxAntibodies = new Object2DoubleOpenHashMap<>();

			 private final int age;

			 private int numVaccinations;

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
				 return numVaccinations;
			 }
			 public void setNumVaccinations(int numVaccinations) {
				 this.numVaccinations = numVaccinations;

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

//			 public void addVaccination(int day) {
//				 vaccinationDates.add(day);
//			 }

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
			 public Object2DoubleMap<VirusStrain> getMaxAntibodies() {
				 return maxAntibodies;
			 }

			 @Override
			 public double getMaxAntibodies(VirusStrain strain) {
				 return maxAntibodies.getDouble(strain);
			 }

			 public void updateMaxAntibodies(VirusStrain strain, double maxAb){
				 this.maxAntibodies.put(strain, maxAb);
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
	 private static ConfigHolder configure(Map<VirusStrain, Double> seriouslySickFactorModifier) {

		 Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		 // configure episimConfig
		 EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		 episimConfig.setHospitalFactor(hospitalFactor);

		 // configure strainConfig: add factorSeriouslySick for each strain
		 VirusStrainConfigGroup strainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		 for (VirusStrain strain : VirusStrain.values()) {
			 double seriouslySickFactorParent = 1.0;
			 if (strain.parent != null) {
				 seriouslySickFactorParent = strainConfig.getParams(strain.parent).getFactorSeriouslySick();
			 }
			 strainConfig.getOrAddParams(strain).setFactorSeriouslySick(seriouslySickFactorParent * seriouslySickFactorModifier.getOrDefault(strain, 1.0));

			 strainConfig.getOrAddParams(strain).setFactorCritical(factorICU);
		 }

		 // configure vaccinationConfig: set beta factor
		 VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		 vaccinationConfig.setBeta(beta);

		 return new ConfigHolder(episimConfig, vaccinationConfig, strainConfig);
	 }

	  static final class ConfigHolder {
		 private final EpisimConfigGroup episimConfig;
		 private final VaccinationConfigGroup vaccinationConfig;
		 private final VirusStrainConfigGroup strainConfig;


		 ConfigHolder(EpisimConfigGroup episimConfig, VaccinationConfigGroup vaccinationConfig, VirusStrainConfigGroup strainConfig) {
			 this.episimConfig = episimConfig;
			 this.vaccinationConfig = vaccinationConfig;
			 this.strainConfig = strainConfig;
		 }
	 }
 }


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


 import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
 import it.unimi.dsi.fastutil.doubles.DoubleList;
 import it.unimi.dsi.fastutil.ints.*;
 import it.unimi.dsi.fastutil.objects.*;
 import org.apache.commons.csv.CSVFormat;
 import org.apache.commons.csv.CSVParser;
 import org.apache.commons.csv.CSVPrinter;
 import org.apache.commons.csv.CSVRecord;
 import org.apache.logging.log4j.Level;
 import org.apache.logging.log4j.LogManager;
 import org.apache.logging.log4j.Logger;
 import org.apache.logging.log4j.core.config.Configurator;
 import org.matsim.api.core.v01.Id;
 import org.matsim.api.core.v01.IdMap;
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
 import tech.tablesaw.api.*;
 import tech.tablesaw.plotly.components.Axis;
 import tech.tablesaw.plotly.components.Figure;
 import tech.tablesaw.plotly.components.Layout;
 import tech.tablesaw.plotly.components.Page;
 import tech.tablesaw.plotly.traces.ScatterTrace;
 import tech.tablesaw.table.TableSliceGroup;

 import java.io.*;
 import java.nio.charset.StandardCharsets;
 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.time.LocalDate;
 import java.time.format.DateTimeFormatter;
 import java.time.temporal.ChronoUnit;
 import java.util.*;


 /**
  * Calculate hospital numbers from events
  */
 @CommandLine.Command(
		 name = "hospitalNumbers",
		 description = "Calculate hospital numbers from events"
 )
 public class HospitalNumbersFromEvents implements OutputAnalysis {

	 //	 	 @CommandLine.Option(names = "--output", defaultValue = "./output/")
	 @CommandLine.Option(names = "--output", defaultValue = "../public-svn/matsim/scenarios/countries/de/episim/battery/jakob/2022-06-16/2/analysis/policy_leis75/")
	 private Path output;

	 //	 	 @CommandLine.Option(names = "--input", defaultValue = "/scratch/projects/bzz0020/episim-input")
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
	 private final String INTAKES_HOSP = "intakesHosp";
	 private final String INTAKES_ICU = "intakesIcu";
	 private final String OCCUPANCY_HOSP = "occupancyHosp";
	 private final String OCCUPANCY_ICU = "occupancyIcu";

	 private Population population;
	 List<Double> strainFactors;
	 private List<Id<Person>> filteredPopulationIds;

	 private EpisimConfigGroup episimConfig;
	 private VirusStrainConfigGroup strainConfig;
	 private VaccinationConfigGroup vaccinationConfig;


	 // source: incidence wave vs. hospitalization wave in cologne/nrw (see https://docs.google.com/spreadsheets/d/1jmaerl27LKidD1uk3azdIL1LmvHuxazNQlhVo9xO1z8/edit?usp=sharing)
	 private static final Object2IntMap<VirusStrain> lagBetweenInfectionAndHospitalisation = new Object2IntAVLTreeMap<>(
			 Map.of(VirusStrain.SARS_CoV_2, 14,
					 VirusStrain.ALPHA, 14,
					 VirusStrain.DELTA, 14,
					 VirusStrain.OMICRON_BA1, 14,
					 VirusStrain.OMICRON_BA2, 14,
					 VirusStrain.STRAIN_A, 14
			 ));

	 // source: hospitalization wave vs. ICU wave in cologne/nrw (see https://docs.google.com/spreadsheets/d/1jmaerl27LKidD1uk3azdIL1LmvHuxazNQlhVo9xO1z8/edit?usp=sharing)
	 private static final Object2IntMap<VirusStrain> lagBetweenHospitalizationAndICU = new Object2IntAVLTreeMap<>(
			 Map.of(VirusStrain.SARS_CoV_2, 6,
					 VirusStrain.ALPHA, 6,
					 VirusStrain.DELTA, 6,
					 VirusStrain.OMICRON_BA1, 6,
					 VirusStrain.OMICRON_BA2, 6,
					 VirusStrain.STRAIN_A, 6
			 ));

	 // Austria study in https://docs.google.com/spreadsheets/d/1jmaerl27LKidD1uk3azdIL1LmvHuxazNQlhVo9xO1z8/edit#gid=0
	 private static final Object2IntMap<VirusStrain> daysInHospitalGivenNoICU = new Object2IntAVLTreeMap<>(
			 Map.of(VirusStrain.SARS_CoV_2, 12,
					 VirusStrain.ALPHA, 12,
					 VirusStrain.DELTA, 12,
					 VirusStrain.OMICRON_BA1, 7,
					 VirusStrain.OMICRON_BA2, 7,
					 VirusStrain.STRAIN_A, 7
			 ));

	 private static final Object2IntMap<VirusStrain> daysInICU = new Object2IntAVLTreeMap<>(
			 Map.of(VirusStrain.SARS_CoV_2, 15, // Debeka & Ireland studies
					 VirusStrain.ALPHA, 15, // Debeka & Ireland studies
					 VirusStrain.DELTA, 15, // this and following values come from nrw analysis on Tabellenblatt 5
					 VirusStrain.OMICRON_BA1, 10,
					 VirusStrain.OMICRON_BA2, 10,
					 VirusStrain.STRAIN_A, 10
			 ));

	 // ??
	 private static final Object2IntMap<VirusStrain> daysInHospitalGivenICU = new Object2IntAVLTreeMap<>(
			 Map.of(VirusStrain.SARS_CoV_2, 60,
					 VirusStrain.ALPHA, 60,
					 VirusStrain.DELTA, 60,
					 VirusStrain.OMICRON_BA1, 60,
					 VirusStrain.OMICRON_BA2, 60,
					 VirusStrain.STRAIN_A, 60
			 ));



//	 private static final Map<VirusStrain, Double> antibodyMultiplier = new HashMap<>(
//			 Map.of(
//					 VirusStrain.SARS_CoV_2, 1.,
//					 VirusStrain.ALPHA, 1.,
//					 VirusStrain.DELTA, 1.,
//					 VirusStrain.OMICRON_BA1, 3.7, //8. without incr. boost effectiveness
//					 VirusStrain.OMICRON_BA2, 3.7,
//					 VirusStrain.STRAIN_A, 3.7
//			 ));


	 private static final double beta = 1.2;
	 private final int populationCntOfficialKoelln = 919_936;
	 private final int populationCntOfficialNrw = 17_930_000;


	 private static final double hospitalFactor = 0.4;

//	 private static final double reportedShareWildAndAlpha = 1.;//0.5; //0.33 -> 0.5
//	 private static final double reportedShareDelta = 1.;//0.5;
//	 private static final double reportedShareOmicron = 1.;//0.25; // 0.33 -> 0.5


	 // base
	 private static final double factorWild =  1.0;

	 private static final double factorAlpha = 1.0 * factorWild;

	 // delta: 2.3x more severe than alpha - Hospital admission and emergency care attendance risk for SARS-CoV-2 delta (B.1.617.2) compared with alpha (B.1.1.7) variants of concern: a cohort study
	 private static final double factorDelta = 1.6 * factorWild;

	 // omicron: approx 0.3x (intrinsic) severity of delta - Comparative analysis of the risks of hospitalisation and death associated with SARS-CoV-2 omicron (B.1.1.529) and delta (B.1.617.2) variants in England: a cohort study
	 private static final double factorOmicron = 0.3  * factorDelta; // * reportedShareOmicron / reportedShareDelta


	 // ??
	 private static final double factorWildAndAlphaICU = 1.;
	 private static final double factorDeltaICU = 1.;
	 private static final double factorOmicronICU = 1.;

	 private String outputAppendix = "";

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


		 // Here we define values factorSeriouslySickStrainA should have
		 strainFactors = List.of(factorOmicron, factorDelta);

		 configure();

//		 List<Double> strainFactors = List.of(factorOmicron);



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
//			 aggregateAndProducePlots(output, pathList);

//		 }


		 return 0;
	 }

	 /**
	  * This method configures the episim config, vaccination config, and strain config to the extent
	  * necessary for post processing.
	  * @param
	  */
	 private void configure() {
//		 Double facAICU;
		 // here we configure file name of the outputs produced by the post-processing analysis.
//		 if (facA == factorWild) {
////			 outputAppendix = "_Alpha";
//			 facAICU = factorWildAndAlphaICU;
//		 } else if (facA == factorDelta) {
////			 outputAppendix = "_Delta";
//			 facAICU = factorDeltaICU;
//		 } else if (facA == factorOmicron) {
////			 outputAppendix = "_Omicron";
//			 facAICU = factorOmicronICU;
//		 } else {
//			 throw new RuntimeException("not clear what to do");
//		 }
//


		 Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		 // configure episimConfig
		 episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		 episimConfig.setHospitalFactor(hospitalFactor);

		 // configure strainConfig: add factorSeriouslySick for each strain
		 strainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
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
		 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySick(factorOmicron);
		 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorCritical(factorOmicronICU);

//		 strainConfig.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(facA);
//		 strainConfig.getOrAddParams(VirusStrain.STRAIN_A).setFactorCritical(facAICU);
//

		 // configure vaccinationConfig: set beta factor
		 vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		 vaccinationConfig.setBeta(beta);
	 }

	 @Override
	 public void analyzeOutput(Path pathToScenario) throws IOException {


		 String id = AnalysisCommand.getScenarioPrefix(pathToScenario);

		 // builds the path to the output file that is produced by this analysis
		 final Path tsvPath = pathToScenario.resolve(id + "post.hospital" + outputAppendix + ".tsv");

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
//		 bw.write(AnalysisCommand.TSV.join(DAY,  DATE,INTAKES_HOSP, INTAKES_ICU, OCCUPANCY_HOSP, OCCUPANCY_ICU)); // + "\thospNoImmunity\thospBaseImmunity\thospBoosted\tincNoImmunity\tincBaseImmunity\tincBoosted"));
		 bw.write(AnalysisCommand.TSV.join(DAY, DATE,"measurement", "severity", "n")); // + "\thospNoImmunity\thospBaseImmunity\thospBoosted\tincNoImmunity\tincBaseImmunity\tincBoosted"));



		 for (Double facA : strainFactors) {

			 // configure post processing run
//			 configure(facA);
			 double facAICU = 0.;
			 String diseaseSevName = "";
			 if (facA == factorWild) {
				 diseaseSevName = "Alpha";
				 facAICU = factorWildAndAlphaICU;
			 } else if (facA == factorDelta) {
				 diseaseSevName = "Delta";
				 facAICU = factorDeltaICU;
			 } else if (facA == factorOmicron) {
				 diseaseSevName = "Omicron";
				 facAICU = factorOmicronICU;
			 } else {
				 throw new RuntimeException("not clear what to do");
			 }

			 strainConfig.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(facA);
			 strainConfig.getOrAddParams(VirusStrain.STRAIN_A).setFactorCritical(facAICU);



			 // instantiate the custom event handler that calculates hospitalizations based on events
			 Map<Id<Person>, Handler.ImmunizablePerson> data = new IdMap<>(Person.class, population.getPersons().size());
			 Handler handler = new Handler(data, population, episimConfig, strainConfig, vaccinationConfig);

			 // feed the output events file to the handler, so that the hospitalizations may be calculated
			 AnalysisCommand.forEachEvent(pathToScenario, s -> {
			 }, handler);


			 int maxIteration = Math.max(
					 Math.max(handler.postProcessHospitalAdmissions.keySet().lastInt(),
							 handler.postProcessICUAdmissions.keySet().lastInt()),
					 Math.max(handler.postProcessHospitalFilledBeds.keySet().lastInt(),
							 handler.postProcessHospitalFilledBedsICU.keySet().lastInt()));


			 // calculates the number of agents in the scenario's population (25% sample) who live in Cologne
			 // this is used to normalize the hospitalization values
			 double popSize = (int) population.getPersons().values().stream()
					 .filter(x -> x.getAttributes().getAttribute("district").equals(district)).count();


			 double totNoImmunity = popSize;
			 double totbaseImmunity = 0;
			 double totBoostered = 0;

			 for (int day = 0; day <= maxIteration; day++) {
				 LocalDate date = startDate.plusDays(day);

				 // calculates Incidence - 7day hospitalizations per 100,000 residents
				 double intakesHosp = getWeeklyHospitalizations(handler.postProcessHospitalAdmissions, day) * 100_000. / popSize;

				 double intakesIcu = getWeeklyHospitalizations(handler.postProcessICUAdmissions, day) * 100_000. / popSize;

				 // calculates daily hospital occupancy, per 100,000 residents
				 double occupancyHosp = handler.postProcessHospitalFilledBeds.getOrDefault(day, 0) * 100_000. / popSize;
				 double occupancyIcu = handler.postProcessHospitalFilledBedsICU.getOrDefault(day, 0) * 100_000. / popSize;


				 bw.newLine();
				 bw.write(AnalysisCommand.TSV.join(day, date, "intakesHosp", diseaseSevName, intakesHosp));
				 bw.newLine();
				 bw.write(AnalysisCommand.TSV.join(day, date, "intakesICU", diseaseSevName, intakesIcu));
				 bw.newLine();
				 bw.write(AnalysisCommand.TSV.join(day, date, "occupancyHosp ", diseaseSevName, occupancyHosp));
				 bw.newLine();
				 bw.write(AnalysisCommand.TSV.join(day, date, "occupancyICU", diseaseSevName, occupancyIcu));

//			 //
//			 totNoImmunity += handler.changeNoImmunity.get(day);
//			 totbaseImmunity += handler.changeBaseImmunity.get(day);
//			 totBoostered += handler.changeBoostered.get(day);
//
//			 double hospNoImmunity = getWeeklyHospitalizations(handler.hospNoImmunity, day) * 100_000. / totNoImmunity;
//			 double hospBaseImmunity = getWeeklyHospitalizations(handler.hospBaseImmunity, day) * 100_000. / totbaseImmunity;
//			 double hospBoosted = getWeeklyHospitalizations(handler.hospBoostered, day) * 100_000. / totBoostered;
//
//			 double incNoImmunity = getWeeklyHospitalizations(handler.incNoImmunity, day) * 100_000. / totNoImmunity;
//			 double incBaseImmunity = getWeeklyHospitalizations(handler.incBaseImmunity, day) * 100_000. / totbaseImmunity;
//			 double incBoosted = getWeeklyHospitalizations(handler.incBoostered, day) * 100_000. / totBoostered;


			 }
		 }

		 bw.close();
	 }

	 public static final class Handler implements EpisimVaccinationEventHandler, EpisimInfectionEventHandler {


		 final Map<Id<Person>, ImmunizablePerson> data;
		 private final Population population;
		 private final Random rnd;
		 private final VirusStrainConfigGroup strainConfig;
		 private final VaccinationConfigGroup vaccinationConfig;

		 final Int2IntSortedMap postProcessHospitalAdmissions;
		 final Int2IntSortedMap postProcessICUAdmissions;
		 final Int2IntSortedMap postProcessHospitalFilledBeds;
		 final Int2IntSortedMap postProcessHospitalFilledBedsICU;

		 private final Int2IntSortedMap changeBaseImmunity;
		 private final Int2IntMap changeNoImmunity;
		 private final Int2IntMap changeBoostered;
		 private final Int2IntMap hospNoImmunity;
		 private final Int2IntMap hospBoostered;
		 private final Int2IntMap hospBaseImmunity;
		 private final Int2IntMap incNoImmunity;
		 private final Int2IntMap incBaseImmunity;
		 private final Int2IntMap incBoostered;

//		 private final EpisimWriter episimWriter;
		  BufferedWriter hospCalibration;

//		 private CSVPrinter printer;

		 private final AgeDependentDiseaseStatusTransitionModel transitionModel;


		 Handler(Map<Id<Person>, ImmunizablePerson> data, Population population, EpisimConfigGroup episimConfig, VirusStrainConfigGroup strainConfig, VaccinationConfigGroup vaccinationConfig) {
			 this.data = data;
			 this.population = population;
			 this.rnd = new Random(1234);
			 this.strainConfig = strainConfig;
			 this.vaccinationConfig = vaccinationConfig;


			 this.postProcessHospitalAdmissions = new Int2IntAVLTreeMap();
			 this.postProcessICUAdmissions = new Int2IntAVLTreeMap();
			 this.postProcessHospitalFilledBeds = new Int2IntAVLTreeMap();
			 this.postProcessHospitalFilledBedsICU = new Int2IntAVLTreeMap();

			 this.changeNoImmunity = new Int2IntAVLTreeMap();
			 this.changeBaseImmunity = new Int2IntAVLTreeMap();
			 this.changeBoostered = new Int2IntAVLTreeMap();
			 this.hospNoImmunity = new Int2IntAVLTreeMap();
			 this.hospBoostered = new Int2IntAVLTreeMap();
			 this.hospBaseImmunity = new Int2IntAVLTreeMap();
			 this.incNoImmunity = new Int2IntAVLTreeMap();
			 this.incBaseImmunity = new Int2IntAVLTreeMap();
			 this.incBoostered = new Int2IntAVLTreeMap();

			 this.transitionModel = new AgeDependentDiseaseStatusTransitionModel(new SplittableRandom(1234), episimConfig, vaccinationConfig, strainConfig);

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

			 person.addInfection(event.getTime());
			 person.setAntibodyLevelAtInfection(event.getAntibodies());
			 person.setVirusStrain(event.getVirusStrain());

			 int day = (int) (event.getTime() / 86_400);

			 updateHospitalizationsPost(person, event.getVirusStrain(), day);

			 if (person.getNumVaccinations()==0) {
				 incNoImmunity.mergeInt(day, 1, Integer::sum);
			 } else if (person.getNumVaccinations()==1) {
				 incBaseImmunity.mergeInt(day, 1, Integer::sum);
			 } else {
				 incBoostered.mergeInt(day, 1, Integer::sum);
			 }

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
		 public void handleEvent(EpisimVaccinationEvent event) {
			 ImmunizablePerson person = data.computeIfAbsent(event.getPersonId(), personId -> new ImmunizablePerson(personId, getAge(personId)));

			 String district = (String) population.getPersons().get(person.personId).getAttributes().getAttribute("district");

			 if (!district.equals("Köln")){
				 return;
			 }

			 int day = (int) (event.getTime() / 86_400);

			 if (person.getNumVaccinations()==0) {
				 changeBaseImmunity.mergeInt(day, 1, Integer::sum);
				 changeNoImmunity.mergeInt(day, -1, Integer::sum);
			 } else if (person.getNumVaccinations() == 1) {
				 changeBoostered.mergeInt(day, 1, Integer::sum);
				 changeBaseImmunity.mergeInt(day, -1, Integer::sum);
			 }


			 person.addVaccination(day);
		 }

		 private int getAge(Id<Person> personId) {
			 return (int) population.getPersons().get(personId).getAttributes().getAttribute("microm:modeled:age");
		 }

		 private void updateHospitalizationsPost(ImmunizablePerson person, VirusStrain strain, int infectionIteration) {


			 if (goToHospital(person, infectionIteration)) {

				 // newly admitted to hospital
				 int inHospital = infectionIteration + lagBetweenInfectionAndHospitalisation.getInt(strain);
				 postProcessHospitalAdmissions.mergeInt(inHospital, 1, Integer::sum);


				 if (person.getNumVaccinations()==0) {
					 hospNoImmunity.mergeInt(inHospital, 1, Integer::sum);
				 } else if (person.getNumVaccinations()==1) {
					 hospBaseImmunity.mergeInt(inHospital, 1, Integer::sum);
				 } else {
					 hospBoostered.mergeInt(inHospital, 1, Integer::sum);
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
			 double strainFactor = strainConfig.getParams(person.getVirusStrain()).getFactorSeriouslySick();
			 double immunityFactor = transitionModel.getSeriouslySickFactor(person, vaccinationConfig, day);

			 return rnd.nextDouble() < ageFactor
					 * strainFactor
					 * immunityFactor;
		 }

		 /**
		  * calculates the probability that agent goes to into critical care (ICU) given hospitalization
		  */
		 private boolean goToICU(ImmunizablePerson person, int day) {


			 double ageFactor = transitionModel.getProbaOfTransitioningToCritical(person);
			 double strainFactor = strainConfig.getParams(person.getVirusStrain()).getFactorCritical();
			 double immunityFactor =  transitionModel.getCriticalFactor(person, vaccinationConfig, day); //todo: revert

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

	 private void aggregateAndProducePlots(Path output, List<Path> pathList) throws IOException {


		 // read hospitalization tsv for all seeds and aggregate them!
		 // NOTE: all other parameters should be the same, otherwise the results will be useless!
		 Int2DoubleSortedMap intakeHosp = new Int2DoubleAVLTreeMap();
		 Int2DoubleSortedMap intakeIcu = new Int2DoubleAVLTreeMap();
		 Int2DoubleSortedMap occupancyHosp = new Int2DoubleAVLTreeMap();
		 Int2DoubleSortedMap occupancyIcu = new Int2DoubleAVLTreeMap();

//		 Int2DoubleSortedMap hospNoImmunity =  new Int2DoubleAVLTreeMap();
//		 Int2DoubleSortedMap hospBaseImmunity =  new Int2DoubleAVLTreeMap();
//		 Int2DoubleSortedMap hospBoosted =  new Int2DoubleAVLTreeMap();
//
//		 Int2DoubleSortedMap incNoImmunity =  new Int2DoubleAVLTreeMap();
//		 Int2DoubleSortedMap incBaseImmunity =  new Int2DoubleAVLTreeMap();
//		 Int2DoubleSortedMap incBoosted =  new Int2DoubleAVLTreeMap();




		 for (Path path : pathList) {

			 String id = AnalysisCommand.getScenarioPrefix(path);

			 final Path tsvPath = path.resolve(id + "post.hospital" + outputAppendix + ".tsv");

			 try (CSVParser parser = new CSVParser(Files.newBufferedReader(tsvPath), CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader())) {

				 for (CSVRecord record : parser) {

					 int day = Integer.parseInt(record.get(DAY));
					 intakeHosp.mergeDouble(day, Double.parseDouble(record.get(INTAKES_HOSP)) / pathList.size(), Double::sum);
					 intakeIcu.mergeDouble(day, Double.parseDouble(record.get(INTAKES_ICU)) / pathList.size(), Double::sum);
					 occupancyHosp.mergeDouble(day, Double.parseDouble(record.get(OCCUPANCY_HOSP)) / pathList.size(), Double::sum);
					 occupancyIcu.mergeDouble(day, Double.parseDouble(record.get(OCCUPANCY_ICU)) / pathList.size(), Double::sum);

//					 hospNoImmunity.mergeDouble(day, Double.parseDouble(record.get("hospNoImmunity")) / pathList.size(), Double::sum);
//					 hospBaseImmunity.mergeDouble(day, Double.parseDouble(record.get("hospBaseImmunity")) / pathList.size(), Double::sum);
//					 hospBoosted.mergeDouble(day, Double.parseDouble(record.get("hospBoosted")) / pathList.size(), Double::sum);
//
//					 incNoImmunity.mergeDouble(day, Double.parseDouble(record.get("incNoImmunity")) / pathList.size(), Double::sum);
//					 incBaseImmunity.mergeDouble(day, Double.parseDouble(record.get("incBaseImmunity")) / pathList.size(), Double::sum);
//					 incBoosted.mergeDouble(day, Double.parseDouble(record.get("incBoosted")) / pathList.size(), Double::sum);
				 }
			 }
		 }

		 // read rki data and add to tsv
		 Int2DoubleMap rkiHospIncidence = new Int2DoubleAVLTreeMap();
		 Int2DoubleMap rkiHospIncidenceAdj = new Int2DoubleAVLTreeMap();
		 try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("../covid-sim/src/assets/rki-deutschland-hospitalization.csv")),
				 CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader())) {

			 for (CSVRecord record : parser) {
				 if (!record.get("Bundesland").equals("Nordrhein-Westfalen")) {
					 continue;
				 }
				 LocalDate date = LocalDate.parse((record.get("Datum")));
				 int day = (int) startDate.until(date, ChronoUnit.DAYS);

				 double incidence;
				 try {
					 incidence = Double.parseDouble(record.get("PS_adjustierte_7T_Hospitalisierung_Inzidenz"));
				 } catch (NumberFormatException e) {
					 incidence = Double.NaN;

				 }


				 rkiHospIncidence.put(day, incidence);

				 double incidenceAdj;
				 if (date.isBefore(LocalDate.of(2020, 12, 10))) {
					 incidenceAdj = incidence;
				 } else if (date.isBefore(LocalDate.of(2021, 1, 11))) {
					 incidenceAdj = 23. / 16. * incidence;
				 } else if (date.isBefore(LocalDate.of(2021, 3, 22))) {
					 incidenceAdj = 8. / 6. * incidence;
				 } else if (date.isBefore(LocalDate.of(2021, 5, 3))) {
					 incidenceAdj = 15./11. * incidence;
				 } else if (date.isBefore(LocalDate.of(2021, 11, 8))) {
					 incidenceAdj = incidence;
				 } else if (date.isBefore(LocalDate.of(2021, 12, 6))) {
					 incidenceAdj = 16. / 13. * incidence;
				 } else if (date.isBefore(LocalDate.of(2022, 1, 24))) {
					 incidenceAdj = incidence;
				 } else {
					 incidenceAdj = 11./14 * incidence;
				 }
				 rkiHospIncidenceAdj.put(day, incidenceAdj);
			 }
		 }

		 Int2IntMap reportedIcuCases = new Int2IntAVLTreeMap();

		 try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("../public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/DIVI/nrw-divi-processed-ICUincidence-until20220504.csv")),
				 CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader())) {

			 for (CSVRecord record : parser) {
				 String dateStr = record.get("date").split("T")[0];
				 LocalDate date = LocalDate.parse(dateStr);
				 int day = (int) startDate.until(date, ChronoUnit.DAYS);

				 int cases = 0;
				 try {
					 cases = Integer.parseInt(record.get("erstaufnahmen"));
				 } catch (NumberFormatException ignored) {

				 }

				 reportedIcuCases.put(day, cases);
			 }
		 }


		 Int2DoubleMap reportedIcuIncidence = new Int2DoubleAVLTreeMap();
		 for (Integer day : reportedIcuCases.keySet()) {
			 // calculates Incidence - 7day hospitalizations per 100,000 residents

			 double xxx = getWeeklyHospitalizations(reportedIcuCases, day) * 100_000. / populationCntOfficialNrw;
			 reportedIcuIncidence.put((int) day, xxx);

		 }

		 Int2DoubleMap hospIncidenceKoeln = new Int2DoubleAVLTreeMap();
		 try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("../public-svn/matsim/scenarios/countries/de/episim/original-data/hospital-cases/cologne/KoelnHospIncidence.csv")),
				 CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {

			 for (CSVRecord record : parser) {
				 String dateStr = record.get("date").split("T")[0];
				 LocalDate date = LocalDate.parse(dateStr);
				 int day = (int) startDate.until(date, ChronoUnit.DAYS);

				 double incidence;
				 try {
					 incidence = Double.parseDouble(record.get("7-Tage-KH-Inz-Koelln")) * 2 ; //TODO

				 } catch (NumberFormatException e) {
					 incidence = 0.;
				 }

				 hospIncidenceKoeln.put(day, incidence);
			 }
		 }

		 // read rki data and add to columns
		 // pink plot from covid-sim: general beds
		 //  https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/original-data/hospital-cases/cologne/KoelnAllgemeinpatienten.csv (pinke Linie)
		 Int2DoubleMap reportedBeds = new Int2DoubleAVLTreeMap();
		 Int2DoubleMap reportedBedsAdj = new Int2DoubleAVLTreeMap();
		 try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("../public-svn/matsim/scenarios/countries/de/episim/original-data/hospital-cases/cologne/KoelnAllgemeinpatienten.csv")),
				 CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader())) {

			 for (CSVRecord record : parser) {
				 String dateStr = record.get("date").split("T")[0];
				 LocalDate date = LocalDate.parse(dateStr);
				 int day = (int) startDate.until(date, ChronoUnit.DAYS);

				 double incidence;
				 try {
					 incidence = Double.parseDouble(record.get("allgemeinpatienten")) * 100_000. / populationCntOfficialKoelln;
				 } catch (NumberFormatException e) {
					 incidence = 0.;
				 }

				 reportedBeds.put(day, incidence);



				 double incidenceAdj;
				 if (date.isBefore(LocalDate.of(2020, 12, 10))) {
					 incidenceAdj = incidence;
				 } else if (date.isBefore(LocalDate.of(2021, 1, 11))) {
					 incidenceAdj = 23. / 16. * incidence;
				 } else if (date.isBefore(LocalDate.of(2021, 3, 22))) {
					 incidenceAdj = 8. / 6. * incidence;
				 } else if (date.isBefore(LocalDate.of(2021, 5, 3))) {
					 incidenceAdj = 15./11. * incidence;
				 } else if (date.isBefore(LocalDate.of(2021, 11, 8))) {
					 incidenceAdj = incidence;
				 } else if (date.isBefore(LocalDate.of(2021, 12, 6))) {
					 incidenceAdj = 16. / 13. * incidence;
				 } else if (date.isBefore(LocalDate.of(2022, 1, 24))) {
					 incidenceAdj = incidence;
				 } else {
					 incidenceAdj = 11./14 * incidence;
				 }
				 reportedBedsAdj.put(day, incidenceAdj);
			 }
		 }


		 //green plot from covid-sim (Ich denke, das ist die Spalte "faelle_covid_aktuell", aber ich bin nicht ganz sicher.)
		 //https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/DIVI/cologne-divi-processed.csv (grüne Linie)
		 Int2DoubleMap reportedBedsICU = new Int2DoubleAVLTreeMap();
		 try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("../public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/DIVI/cologne-divi-processed.csv")),
				 CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader())) {

			 for (CSVRecord record : parser) {
				 String dateStr = record.get("date").split("T")[0];
				 LocalDate date = LocalDate.parse(dateStr);
				 int day = (int) startDate.until(date, ChronoUnit.DAYS);

				 double incidence = 0.;
				 try {
					 incidence = Double.parseDouble(record.get("faelle_covid_aktuell")) * 100_000. / populationCntOfficialKoelln;
				 } catch (NumberFormatException ignored) {

				 }

				 reportedBedsICU.put(day, incidence);
			 }
		 }


		 //https://www.dkgev.de/dkg/coronavirus-fakten-und-infos/aktuelle-bettenbelegung/
		 Int2DoubleMap reportedBedsNrw = new Int2DoubleAVLTreeMap();
		 Int2DoubleMap reportedBedsIcuNrw = new Int2DoubleAVLTreeMap();
		 try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("/Users/jakob/Downloads/Covid_csvgesamt(2).csv")),
				 CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader())) {

			 for (CSVRecord record : parser) {

				 if (!record.get("Bundesland").equals("Nordrhein-Westfalen")) {
					 continue;
				 }

				 String dateStr = record.get("Datum");
				 LocalDate date = LocalDate.parse(dateStr);
				 int day = (int) startDate.until(date, ChronoUnit.DAYS);

				 double incidence = 0.;
				 try {
					 incidence = Double.parseDouble(record.get("Betten")) * 100_000. / populationCntOfficialNrw;
				 } catch (NumberFormatException ignored) {
				 }

				 if (record.get("Bettenart").equals("Intensivbett")) {
					 reportedBedsIcuNrw.put(day, incidence);
				 } else if (record.get("Bettenart").equals("Normalbett")) {
					 reportedBedsNrw.put(day, incidence);
				 }

			 }
		 }


		 // https://datawrapper.dwcdn.net/sjUZF/334/
		 Int2DoubleMap reportedBedsNrw2 = new Int2DoubleAVLTreeMap();
		 Int2DoubleMap reportedBedsIcuNrw2 = new Int2DoubleAVLTreeMap();
		 try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("../public-svn/matsim/scenarios/countries/de/episim/original-data/hospital-cases/cologne/nrwBettBelegung.csv")),
				 CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader())) {

			 for (CSVRecord record : parser) {


				 String dateStr = record.get("Datum");
				 LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("d.M.yyyy"));
				 int day = (int) startDate.until(date, ChronoUnit.DAYS);

				 try {
					 double incidence = Double.parseDouble(record.get("Stationär")) * 100_000. / populationCntOfficialNrw;
					 reportedBedsNrw2.put(day, incidence);
				 } catch (NumberFormatException ignored) {
				 }

				 try {
					 double incidence = Double.parseDouble(record.get("Patienten auf der <br>Intensivstation")) * 100_000. / populationCntOfficialNrw;
					 reportedBedsIcuNrw2.put(day, incidence);
				 } catch (NumberFormatException ignored) {
				 }


			 }
		 }


		 // Produce TSV w/ aggregated data as well as all rki numbers
		 try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(output.resolve("post.hospital.agg" + outputAppendix + ".tsv")), CSVFormat.DEFAULT.withDelimiter('\t'))) {

			 printer.printRecord(DAY, DATE, INTAKES_HOSP, INTAKES_ICU, OCCUPANCY_HOSP, OCCUPANCY_ICU, "rkiIncidence", "rkiHospRate", "rkiCriticalRate"); //"hospNoImmunity", "hospBaseImmunity", "hospBoosted", "incNoImmunity", "incBaseImmunity", "incBoosted");

			 double maxIteration = Double.max(Double.max(intakeHosp.lastIntKey(), intakeIcu.lastIntKey()), Double.max(occupancyHosp.lastIntKey(), occupancyIcu.lastIntKey()));

			 for (int day = 0; day <= maxIteration; day++) {
				 LocalDate date = startDate.plusDays(day);
				 printer.printRecord(
						 day,
						 date,
						 intakeHosp.get(day),
						 intakeIcu.get(day),
						 occupancyHosp.get(day),
						 occupancyIcu.get(day),
						 rkiHospIncidence.get(day),
						 reportedBeds.get(day),
						 reportedBedsICU.get(day)
//						 hospNoImmunity.get(day),
//						 hospBaseImmunity.get(day),
//						 hospBoosted.get(day),
//						 incNoImmunity.get(day),
//						 incBaseImmunity.get(day),
//						 incBoosted.get(day)
				 );
			 }
		 }

		 // PLOT 1: People admitted to hospital
		 {
			 IntColumn records = IntColumn.create("day");
			 DateColumn recordsDate = DateColumn.create("date");
			 DoubleColumn values = DoubleColumn.create("hospitalizations");
			 StringColumn groupings = StringColumn.create("scenario");

			 // model: intakeHosp
			 for (Int2DoubleMap.Entry entry : intakeHosp.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));
				 values.append(intakeHosp.getOrDefault(day, Double.NaN));
				 groupings.append("model: intakeHosp");
			 }

			 // model: intakeIcu
			 for (Int2DoubleMap.Entry entry : intakeIcu.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));
				 values.append(intakeIcu.getOrDefault(day, Double.NaN));
				 groupings.append("model: intakeICU");
			 }


			 for (Int2DoubleMap.Entry entry : rkiHospIncidence.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));
				 final double value = rkiHospIncidence.getOrDefault(day, Double.NaN);
				 if (Double.isNaN(value)) {
					 values.appendMissing();
				 } else {
					 values.append(value);
				 }
				 groupings.append("reported: intakeHosp (rki, nrw adjusted)");
			 }

			 for (Int2DoubleMap.Entry entry : rkiHospIncidenceAdj.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));
				 final double value = rkiHospIncidenceAdj.getOrDefault(day, Double.NaN);
				 if (Double.isNaN(value)) {
					 values.appendMissing();
				 } else {
					 values.append(value);
				 }
				 groupings.append("reported: intakeHosp (rki, nrw adjusted, SARI)");
			 }

			 for (Int2DoubleMap.Entry entry : hospIncidenceKoeln.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));
				 final double value = hospIncidenceKoeln.getOrDefault(day, Double.NaN);
				 if (Double.isNaN(value)) {
					 values.appendMissing();
				 } else {
					 values.append(value);
				 }
				 groupings.append("reported: intakeHosp (köln)");
			 }

			 for (Int2DoubleMap.Entry entry : reportedIcuIncidence.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));
				 final double value = reportedIcuIncidence.getOrDefault(day, Double.NaN);
				 if (Double.isNaN(value)) {
					 values.appendMissing();
				 } else {
					 values.append(value);
				 }
				 groupings.append("reported: intakeIcu (divi, nrw)");
			 }


			 producePlot(recordsDate, values, groupings, "", "7-Tage Hospitalisierungsinzidenz", "HospIncidence" + outputAppendix + ".html");
		 }


		 // PLOT 2: People taking up beds in hospital (regular and ICU)
		 {
			 IntColumn records = IntColumn.create("day");
			 DateColumn recordsDate = DateColumn.create("date");
			 DoubleColumn values = DoubleColumn.create("hospitalizations");
			 StringColumn groupings = StringColumn.create("scenario");


			 for (Int2DoubleMap.Entry entry : occupancyHosp.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(occupancyHosp.get(day));
				 groupings.append("generalBeds");
			 }

			 for (Int2DoubleMap.Entry entry : occupancyIcu.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(occupancyIcu.get(day));
				 groupings.append("ICUBeds");
			 }


			 for (Int2DoubleMap.Entry entry : reportedBeds.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(entry.getDoubleValue());
				 groupings.append("Reported: General Beds");

			 }

			 for (Int2DoubleMap.Entry entry : reportedBedsAdj.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(entry.getDoubleValue());
				 groupings.append("Reported: General Beds (SARI)");
			 }


			 for (Int2DoubleMap.Entry entry : reportedBedsICU.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(entry.getDoubleValue());
				 groupings.append("Reported: ICU Beds");

			 }


			 for (Int2DoubleMap.Entry entry : reportedBedsNrw.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(entry.getDoubleValue());
				 groupings.append("Reported: General Beds (NRW)");

			 }


			 for (Int2DoubleMap.Entry entry : reportedBedsIcuNrw.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(entry.getDoubleValue());
				 groupings.append("Reported: ICU Beds (NRW)");

			 }

			 for (Int2DoubleMap.Entry entry : reportedBedsNrw2.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(entry.getDoubleValue());
				 groupings.append("Reported: General Beds (NRW2)");

			 }

			 for (Int2DoubleMap.Entry entry : reportedBedsIcuNrw2.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(entry.getDoubleValue());
				 groupings.append("Reported: ICU Beds (NRW2)");

			 }



			 // Make plot
			 producePlot(recordsDate, values, groupings, "Filled Beds", "Beds Filled / 100k Population", "FilledBeds" + outputAppendix + ".html");
		 }


	 }

	 private void producePlot(DateColumn records, DoubleColumn values, StringColumn groupings, String title, String yAxisTitle, String filename) {
		 // Make plot
		 Table table = Table.create(title);
		 table.addColumns(records);
		 table.addColumns(values);
		 table.addColumns(groupings);

		 TableSliceGroup tables = table.splitOn(table.categoricalColumn("scenario"));

		 Axis xAxis = Axis.builder().title("Datum").build();
		 Axis yAxis = Axis.builder().range(0., 20.)
				 //				  .type(Axis.Type.LOG)
				 .title(yAxisTitle).build();

		 Layout layout = Layout.builder(title).xAxis(xAxis).yAxis(yAxis).showLegend(true).height(500).width(1000).build();

		 ScatterTrace[] traces = new ScatterTrace[tables.size()];
		 for (int i = 0; i < tables.size(); i++) {
			 List<Table> tableList = tables.asTableList();
			 traces[i] = ScatterTrace.builder(tableList.get(i).dateColumn("date"), tableList.get(i).numberColumn("hospitalizations"))
					 .showLegend(true)
					 .name(tableList.get(i).name())
					 .mode(ScatterTrace.Mode.LINE)
					 .build();
		 }
		 var figure = new Figure(layout, traces);

		 try (Writer writer = new OutputStreamWriter(new FileOutputStream(output.resolve(filename).toString()), StandardCharsets.UTF_8)) {
			 writer.write(Page.pageBuilder(figure, "target").build().asJavascript());
		 } catch (IOException e) {
			 throw new UncheckedIOException(e);
		 }
	 }

	 private int getWeeklyHospitalizations(Int2IntMap hospMap, Integer today) {
		 int weeklyHospitalizations = 0;
		 for (int i = 0; i < 7; i++) {
			 try {
				 weeklyHospitalizations += hospMap.getOrDefault(today - i, 0);
			 } catch (Exception ignored) {

			 }
		 }
		 return weeklyHospitalizations;
	 }

 }


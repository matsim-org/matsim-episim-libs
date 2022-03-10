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
 import com.google.protobuf.Internal;
 import it.unimi.dsi.fastutil.ints.*;
 import org.apache.commons.csv.CSVFormat;
 import org.apache.commons.csv.CSVParser;
 import org.apache.commons.csv.CSVRecord;
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
 import org.matsim.episim.EpisimPerson.DiseaseStatus;
 import org.matsim.episim.VirusStrainConfigGroup;
 import org.matsim.episim.events.*;
 import org.matsim.episim.model.VaccinationType;
 import org.matsim.episim.model.VirusStrain;
 import org.matsim.run.AnalysisCommand;
 import picocli.CommandLine;
 import tech.tablesaw.api.DoubleColumn;
 import tech.tablesaw.api.IntColumn;
 import tech.tablesaw.api.StringColumn;
 import tech.tablesaw.api.Table;
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
 import java.time.temporal.ChronoUnit;
 import java.util.*;


 /**
  * Calculate hospital numbers from events
  */
 @CommandLine.Command(
		 name = "hospitalNumbers",
		 description = "Calculate vaccination effectiveness from events"
 )
 public class HospitalNumbersFromEvents implements OutputAnalysis {

	 private static final Logger log = LogManager.getLogger(HospitalNumbersFromEvents.class);


	 //	 @CommandLine.Option(names = "--output", defaultValue = "./output/")
	 @CommandLine.Option(names = "--output", defaultValue = "../public-svn/matsim/scenarios/countries/de/episim/battery/cologne/2022-02-22/2/outputNewVOC")
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


	 private Config config;
	 private EpisimConfigGroup episimConfig;
	 VirusStrainConfigGroup strainConfig;

	 private static double hospitalFactor = 0.5; // This value was taken from the episim config file for the runs in question; TODO: what should this be?
	 private static double immunityFactor = 1.0; //TODO: what should this be?
	 private int populationCnt = 919_936;
	 private static int lagBetweenInfectionAndHospitalisation = 5;

	 static double factorAlpha = 1.0;
	 static double factorDelta = 1.5;
	 static double factorOmicron = 0.35;

	 private static String outputAppendix = "";


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

		 List<Double> strainFactors = List.of(factorAlpha, factorDelta, factorOmicron);

		 Double factorStrainA;
		 //		 Double factorStrainB;
		 for (Double facA : strainFactors) {
			 factorStrainA = facA;

			 outputAppendix = "_A" + factorStrainA;

			 config = ConfigUtils.createConfig(new EpisimConfigGroup());

			 episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);


			 strainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
			 strainConfig.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySick(factorAlpha);
			 strainConfig.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySick(factorDelta);
			 strainConfig.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySickVaccinated(factorDelta);
			 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySick(factorOmicron);
			 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySickVaccinated(factorOmicron);
			 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorSeriouslySick(factorOmicron);
			 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorSeriouslySickVaccinated(factorOmicron);
			 strainConfig.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(factorStrainA);
			 strainConfig.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySickVaccinated(factorStrainA);
			 //				 strainConfig.getOrAddParams(VirusStrain.STRAIN_B).setFactorSeriouslySick(factorStrainB);
			 //				 strainConfig.getOrAddParams(VirusStrain.STRAIN_B).setFactorSeriouslySickVaccinated(factorStrainB);


			 // Part 1: calculate hospitalizations and save as csv
			 // can be run once and then commented out!


			 AnalysisCommand.forEachScenario(output, scenario -> {
				 try {
					 analyzeOutput(scenario);

				 } catch (IOException e) {
					 log.error("Failed processing {}", scenario, e);
				 }
			 });

			 log.info("done");

			 // ===

			 // Part 2: aggregate over multiple seeds & produce tsv output & plot
			 List<Path> pathList = new ArrayList<>();
			 AnalysisCommand.forEachScenario(output, scenario -> {
				 pathList.add(scenario);
			 });

			 aggregateDataAndProduceTSV(output, pathList);

		 }


		 return 0;
	 }

	 @Override
	 public void analyzeOutput(Path output) throws IOException {

		 if (scenario != null)
			 population = scenario.getPopulation();


		 String id = AnalysisCommand.getScenarioPrefix(output);

		 final Path tsvPath = output.resolve(id + "post.hospital" + outputAppendix + ".tsv");

		 calculateHospitalizationsAndWriteOutput(output, tsvPath);

		 log.info("Calculated results for output {}", output);

	 }

	 private void calculateHospitalizationsAndWriteOutput(Path output, Path tsvPath) throws IOException {
		 BufferedWriter bw = Files.newBufferedWriter(tsvPath);

		 bw.write("day\tdate\tstandardHospitalizations\tpostProcessHospitalizations\tppBeds\tppBedsICU");

		 Map<Id<Person>, Holder> data = new IdMap<>(Person.class, population.getPersons().size());

		 Handler handler = new Handler(data, startDate, population, strainConfig);

		 AnalysisCommand.forEachEvent(output, s -> {
		 }, handler);

		 int maxIteration = Math.max(Math.max(
				 0, //(Int2IntAVLTreeMap) handler.standardHospitalAdmissions).keySet().lastInt(), //TODO: !!!! remove standardHospitalAdmissions from this class
				 ((Int2IntAVLTreeMap) handler.postProcessHospitalAdmissions).keySet().lastInt()),
				 Math.max(((Int2IntAVLTreeMap) handler.postProcessHospitalFilledBeds).keySet().lastInt(),
						 ((Int2IntAVLTreeMap) handler.postProcessHospitalFilledBedsICU).keySet().lastInt()));


		 for (int day = 0; day <= maxIteration; day++) {
			 LocalDate date = startDate.plusDays(day);

			 // calculates Incidence - 7day hospitalizations per 100,000 residents
			 double stdHosp = getWeeklyHospitalizations(handler.standardHospitalAdmissions, day) * 4 * 100_000. / populationCnt;
			 double ppHosp = getWeeklyHospitalizations(handler.postProcessHospitalAdmissions, day) * 4 * 100_000. / populationCnt;

			 // calculates daily hospital occupancy, per 100,000 residents
			 double ppBed = handler.postProcessHospitalFilledBeds.getOrDefault(day, 0) * 4 * 100_000. / populationCnt;
			 double ppBedICU = handler.postProcessHospitalFilledBedsICU.getOrDefault(day, 0) * 4 * 100_000. / populationCnt;

			 bw.newLine();
			 bw.write(AnalysisCommand.TSV.join(day, date, stdHosp, ppHosp, ppBed, ppBedICU));

		 }

		 bw.close();
	 }

	 private void aggregateDataAndProduceTSV(Path output, List<Path> pathList) throws IOException {


		 // read hospitalization tsv for all seeds and aggregate them!
		 // todo: NOTE: all other parameters should be the same, otherwise the results will be useless!
		 Int2DoubleMap standardHospitalizations = new Int2DoubleAVLTreeMap();
		 Int2DoubleMap postProcessHospitalizations = new Int2DoubleAVLTreeMap();
		 Int2DoubleMap ppBeds = new Int2DoubleAVLTreeMap();
		 Int2DoubleMap ppBedsICU = new Int2DoubleAVLTreeMap();


		 for (Path path : pathList) {

			 String id = AnalysisCommand.getScenarioPrefix(path);

			 final Path tsvPath = path.resolve(id + "post.hospital" + outputAppendix + ".tsv");

			 BufferedReader br = Files.newBufferedReader(tsvPath);
			 CSVParser parser = new CSVParser(br,
					 CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader());


			 for (CSVRecord record : parser) {

				 int day = Integer.parseInt(record.get("day"));
				 double stdHosp = standardHospitalizations.getOrDefault(day, 0) / pathList.size() + Double.parseDouble(record.get("standardHospitalizations"));
				 double ppHosp = postProcessHospitalizations.getOrDefault(day, 0) / pathList.size() + Double.parseDouble(record.get("postProcessHospitalizations"));
				 double ppBed = ppBeds.getOrDefault(day, 0) / pathList.size() + Double.parseDouble(record.get("ppBeds"));
				 double ppICU = ppBedsICU.getOrDefault(day, 0) / pathList.size() + Double.parseDouble(record.get("ppBedsICU"));


				 standardHospitalizations.put(day, stdHosp);
				 postProcessHospitalizations.put(day, ppHosp);
				 ppBeds.put(day, ppBed);
				 ppBedsICU.put(day, ppICU);

			 }

			 br.close();
			 parser.close();
		 }

		 // read rki data and add to tsv
		 Int2DoubleMap rkiHospIncidence = new Int2DoubleAVLTreeMap();
		 {

			 CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("../covid-sim/src/assets/rki-deutschland-hospitalization.csv")),
					 CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader());

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
					 incidence = 0.;
				 }

				 rkiHospIncidence.put(day, incidence);
			 }
		 }

		 // read rki data and add to columns
		 // pink plot from covid-sim: general beds
		 //  https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/original-data/hospital-cases/cologne/KoelnAllgemeinpatienten.csv (pinke Linie)
		 Int2DoubleMap reportedBeds = new Int2DoubleAVLTreeMap();
		 {

			 CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("../public-svn/matsim/scenarios/countries/de/episim/original-data/hospital-cases/cologne/KoelnAllgemeinpatienten.csv")),
					 CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader());

			 for (CSVRecord record : parser) {
				 String dateStr = record.get("date").split("T")[0];
				 LocalDate date = LocalDate.parse(dateStr);
				 int day = (int) startDate.until(date, ChronoUnit.DAYS);

				 double incidence;
				 try {
					 incidence = Double.parseDouble(record.get("allgemeinpatienten")) * 100_000. / populationCnt;
				 } catch (NumberFormatException e) {
					 incidence = 0.;
				 }

				 reportedBeds.put(day, incidence);
			 }
		 }


		 //green plot from covid-sim (Ich denke, das ist die Spalte "faelle_covid_aktuell", aber ich bin nicht ganz sicher.)
		 //https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/DIVI/cologne-divi-processed.csv (grüne Linie)
		 Int2DoubleMap reportedBedsICU = new Int2DoubleAVLTreeMap();
		 {
			 CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("../public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/DIVI/cologne-divi-processed.csv")),
					 CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader());

			 for (CSVRecord record : parser) {
				 String dateStr = record.get("date").split("T")[0];
				 LocalDate date = LocalDate.parse(dateStr);
				 int day = (int) startDate.until(date, ChronoUnit.DAYS);

				 double incidence = 0.;
				 try {
					 incidence = Double.parseDouble(record.get("faelle_covid_aktuell")) * 100_000. / populationCnt;
				 } catch (NumberFormatException e) {

				 }

				 reportedBedsICU.put(day, incidence);
			 }
		 }


		 // Produce TSV w/ aggregated data as well as all rki numbers

		 {
			 BufferedWriter bw = Files.newBufferedWriter(output.resolve("post.hospital.agg" + outputAppendix + ".tsv"));//todo

			 bw.write("day\tdate\tmodelIncidenceBase\tmodelIncidencePost\tmodelHospRate\tmodelCriticalRate\trkiIncidence\trkiHospRate\trkiCriticalRate");

			 double maxIteration = standardHospitalizations.size(); //todo

			 for (int day = 0; day <= maxIteration; day++) {
				 LocalDate date = startDate.plusDays(day);

				 bw.newLine();
				 bw.write(AnalysisCommand.TSV.join(
						 day,
						 date,
						 standardHospitalizations.getOrDefault(day, 0.),
						 postProcessHospitalizations.getOrDefault(day, 0.),
						 ppBeds.getOrDefault(day, 0.),
						 ppBedsICU.getOrDefault(day, 0.),
						 rkiHospIncidence.getOrDefault(day, 0.),
						 reportedBeds.getOrDefault(day, 0.),
						 reportedBedsICU.getOrDefault(day, 0.))
				 );

			 }

			 bw.close();


		 }


		 // PLOT 1: People admitted to hospital
		 {
			 IntColumn records = IntColumn.create("day");
			 DoubleColumn values = DoubleColumn.create("hospitalizations");
			 StringColumn groupings = StringColumn.create("scenario");

			 //			 // standard hospitalizations from episim
			 //			 for (Map.Entry entry : standardHospitalizations.entrySet()) {
			 //				 int today = (int) entry.getKey();
			 //				 records.append(today);
			 //				 values.append(standardHospitalizations.getOrDefault(today, 0.));
			 //				 groupings.append("baseCase");
			 //			 }

			 // post-processed hospitalizations from episim
			 for (Map.Entry entry : postProcessHospitalizations.entrySet()) {
				 int today = (int) entry.getKey();
				 records.append(today);
				 values.append(postProcessHospitalizations.getOrDefault(today, 0.));
				 groupings.append("postProcess");
			 }


			 for (Map.Entry entry : rkiHospIncidence.entrySet()) {
				 int today = (int) entry.getKey();
				 records.append(today);
				 values.append(rkiHospIncidence.getOrDefault(today, 0.));
				 groupings.append("RKI NRW Adjusted");
			 }


			 producePlot(records, values, groupings, "Hospitalization Incidence", "7-Day Hospital Admissions / 100k Population", "HospitalizationComparisonIncidence" + outputAppendix + ".html");
		 }


		 // PLOT 2: People taking up beds in hospital (regular and ICU)
		 {
			 IntColumn records = IntColumn.create("day");
			 DoubleColumn values = DoubleColumn.create("hospitalizations");
			 StringColumn groupings = StringColumn.create("scenario");


			 for (Map.Entry entry : ppBeds.entrySet()) {
				 Integer today = (Integer) entry.getKey();
				 records.append(today);

				 values.append(ppBeds.get(today));
				 groupings.append("generalBeds");
			 }

			 for (Map.Entry entry : ppBedsICU.entrySet()) {
				 Integer today = (Integer) entry.getKey();
				 records.append(today);

				 values.append(ppBedsICU.get(today));
				 groupings.append("ICUBeds");
			 }


			 for (Map.Entry entry : reportedBeds.entrySet()) {
				 records.append((Integer) entry.getKey());
				 values.append((Double) entry.getValue());
				 groupings.append("Reported: General Beds");
			 }


			 for (Map.Entry entry : reportedBedsICU.entrySet()) {
				 records.append((Integer) entry.getKey());
				 values.append((Double) entry.getValue());
				 groupings.append("Reported: ICU Beds");
			 }


			 // Make plot
			 producePlot(records, values, groupings, "Filled Beds", "Filled Beds", "FilledBeds" + outputAppendix + ".html");
		 }


	 }

	 private void producePlot(IntColumn records, DoubleColumn values, StringColumn groupings, String s, String s2, String s3) {
		 // Make plot
		 Table table = Table.create(s);
		 table.addColumns(records);
		 table.addColumns(values);
		 table.addColumns(groupings);

		 TableSliceGroup tables = table.splitOn(table.categoricalColumn("scenario"));

		 Axis yAxis = Axis.builder().type(Axis.Type.LOG).build();

		 Layout layout = Layout.builder(s2, "Day", "Incidence").yAxis(yAxis).showLegend(true).build();

		 ScatterTrace[] traces = new ScatterTrace[tables.size()];
		 for (int i = 0; i < tables.size(); i++) {
			 List<Table> tableList = tables.asTableList();
			 traces[i] = ScatterTrace.builder(tableList.get(i).numberColumn("day"), tableList.get(i).numberColumn("hospitalizations"))
					 .showLegend(true)
					 .name(tableList.get(i).name())
					 .mode(ScatterTrace.Mode.LINE)
					 .build();
		 }
		 var figure = new Figure(layout, traces);

		 try (Writer writer = new OutputStreamWriter(new FileOutputStream(output.resolve(s3).toString()), StandardCharsets.UTF_8)) {
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
			 } catch (Exception e) {

			 }
		 }
		 return weeklyHospitalizations;
	 }


	 private class Handler implements EpisimPersonStatusEventHandler, EpisimVaccinationEventHandler, EpisimInfectionEventHandler {

		 private final Map<Id<Person>, Holder> data;
		 private final LocalDate startDate;
		 private final Population population;
		 private final Random rnd;
		 private final VirusStrainConfigGroup strainConfig;

		 private final Int2IntMap standardHospitalAdmissions;
		 private final Int2IntMap postProcessHospitalAdmissions;
		 private final Int2IntMap postProcessHospitalFilledBeds;
		 private final Int2IntMap postProcessHospitalFilledBedsICU;


		 public Handler(Map<Id<Person>, Holder> data, LocalDate startDate, Population population, VirusStrainConfigGroup strainConfig) {
			 this.data = data;
			 this.startDate = startDate;
			 this.population = population;
			 this.rnd = new Random(1234);
			 this.strainConfig = strainConfig;

			 this.standardHospitalAdmissions = new Int2IntAVLTreeMap();
			 this.postProcessHospitalAdmissions = new Int2IntAVLTreeMap();
			 this.postProcessHospitalFilledBeds = new Int2IntAVLTreeMap();
			 this.postProcessHospitalFilledBedsICU = new Int2IntAVLTreeMap();

		 }

		 @Override
		 public void handleEvent(EpisimInfectionEvent event) {

			 Id<Person> personId = event.getPersonId();
			 Holder person = data.computeIfAbsent(personId, Holder::new);

			 int day = (int) (event.getTime() / 86_400);

			 person.infections.add(day);
			 person.strains.add(event.getVirusStrain());

			 updateHospitalizationsPost(personId, person, event.getStrain(), day);


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

			 if (status.equals(DiseaseStatus.seriouslySick)) {

				 if (population.getPersons().get(event.getPersonId()).getAttributes().getAttribute("district").equals(district)) {
					 int hospitalizationCnt = this.standardHospitalAdmissions.getOrDefault(day, 0);
					 this.standardHospitalAdmissions.put(day, ++hospitalizationCnt);
				 }

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

		 private void updateHospitalizationsPost(Id<Person> personId, Holder person, VirusStrain strain, int infectionIteration) {
			 if (person.infections.size() > 0) {

				 int age = (int) population.getPersons().get(personId).getAttributes().getAttribute("microm:modeled:age");

				 String district = (String) population.getPersons().get(personId).getAttributes().getAttribute("district");

				 if (!district.equals("Köln")) {
					 return;
				 }


				 if (goToHospital(person, strain, age)) {

					 int hospitalizationIteration = infectionIteration + lagBetweenInfectionAndHospitalisation;

					 // newly addmited to hospital
					 int hospitalizationCnt = postProcessHospitalAdmissions.getOrDefault(hospitalizationIteration, 0);
					 postProcessHospitalAdmissions.put(hospitalizationIteration, ++hospitalizationCnt);


					 if (goToICU(strain, age)) {

						 // 29 days in regular part of hospital
						 for (int i = 0; i < 29; i++) {
							 int day = hospitalizationIteration + i;
							 int bedCnt = postProcessHospitalFilledBeds.getOrDefault(day, 0);
							 postProcessHospitalFilledBeds.put(day, ++bedCnt);
						 }

						 //21 days in ICU (critical) starting with the day after admitten into hospital
						 for (int i = 1; i < 22; i++) {
							 int day = hospitalizationIteration + i;
							 int bedCnt = postProcessHospitalFilledBedsICU.getOrDefault(day, 0);
							 postProcessHospitalFilledBedsICU.put(day, ++bedCnt);
						 }


					 } else {
						 //14 days in regular part of hospital
						 for (int i = 0; i < 14; i++) {
							 int day = hospitalizationIteration + i;
							 int bedCnt = postProcessHospitalFilledBeds.getOrDefault(day, 0);
							 postProcessHospitalFilledBeds.put(day, ++bedCnt);
						 }
					 }
				 }

			 }

		 }

		 private boolean goToICU(VirusStrain strain, int age) {
			 return rnd.nextDouble() < getProbaOfTransitioningToCritical(age) * strainConfig.getParams(strain).getFactorCritical()
					 * getCriticalFactor();
		 }

		 /**
		  * calculates the probability that agent goes to hospital given an infection.
		  */
		 private boolean goToHospital(Holder person, VirusStrain strain, int age) {

			 double ageFactor = getProbaOfTransitioningToSeriouslySick(age);
			 double vaccinationFactor = person.vaccine != null ?
					 strainConfig.getParams(strain).getFactorSeriouslySickVaccinated() :
					 strainConfig.getParams(strain).getFactorSeriouslySick();

			 // checks whether agents goes to hospital
			 return rnd.nextDouble() < ageFactor
					 * vaccinationFactor
					 * getSeriouslySickFactor(person, strain);
		 }

		 /**
		  * Adapted from AgeDependentDiseaseStatusTransitionModel, signature changed.
		  */
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

		 /**
		  * Adapted from AgeDependentDiseaseStatusTransitionModel
		  */
		 protected double getProbaOfTransitioningToCritical(int age) {
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


		 /**
		  * Adapted from AntibodyDependentTransitionModel.
		  * changed inputs & immunity factor
		  */

		 public double getSeriouslySickFactor(Holder person, VirusStrain strain) {


			 int numVaccinations = 0;

			 if (person.boosterDate != null) {
				 numVaccinations = 2; //TODO: should this be 3?
			 } else if (person.vaccinationDate != null) {
				 numVaccinations = 1; //TODO: should this be 2?
			 }

			 int numInfections = person.infections.size() - 1;

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

			 // In InfectionModelWithAntibodies, immunityFactor = 1.0 / (1.0 + Math.pow(relativeAntibodyLevelTarget, vaccinationConfig.getBeta()));
			 // I don't know how easy it will be to get the relativeAntibody level in post-processing -jr 2022-02-18
			 // todo: implement immunity factor
			 double factorInf = immunityFactor;

			 double factorSeriouslySick = (1.0 - veSeriouslySick) / factorInf;

			 factorSeriouslySick = Math.min(1.0, factorSeriouslySick);
			 factorSeriouslySick = Math.max(0.0, factorSeriouslySick);

			 return factorSeriouslySick;
		 }
	 }

	 public static double getCriticalFactor() { //todo: is this right?
		 return 1.0;
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





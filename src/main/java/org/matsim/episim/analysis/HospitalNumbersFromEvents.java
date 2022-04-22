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
 import org.matsim.episim.EpisimConfigGroup;
 import org.matsim.episim.VirusStrainConfigGroup;
 import org.matsim.episim.events.*;
 import org.matsim.episim.model.ImmunityEvent;
 import org.matsim.episim.model.VirusStrain;
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
 import java.time.temporal.ChronoUnit;
 import java.util.*;
 import java.util.stream.Collectors;


 /**
  * Calculate hospital numbers from events
  */
 @CommandLine.Command(
		 name = "hospitalNumbers",
		 description = "Calculate vaccination effectiveness from events"
 )
 public class HospitalNumbersFromEvents implements OutputAnalysis {

	 private static final Logger log = LogManager.getLogger(HospitalNumbersFromEvents.class);


//	 	 @CommandLine.Option(names = "--output", defaultValue = "./output/")
	 @CommandLine.Option(names = "--output", defaultValue = "/Users/jakob/git/public-svn/matsim/scenarios/countries/de/episim/battery/jakob/2022-04-14-Analysis/1-0-reduce/")
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

	 private Population population;
	 private List<Id<Person>> filteredPopulationIds;

	 private VirusStrainConfigGroup strainConfig;

	 private static final double beta = 1.2;
	 private final int populationCntOfficial = 919_936;

	 private static final Object2IntMap<VirusStrain> lagBetweenInfectionAndHospitalisation = new Object2IntAVLTreeMap<>(
			 Map.of(VirusStrain.SARS_CoV_2, 10,
					 VirusStrain.ALPHA, 10,
					 VirusStrain.DELTA, 10,
					 VirusStrain.OMICRON_BA1, 10,
					 VirusStrain.OMICRON_BA2, 10,
					 VirusStrain.STRAIN_A, 10
			 ));

	 private static final Object2IntMap<VirusStrain> lagBetweenHospitalizationAndICU = new Object2IntAVLTreeMap<>(
			 Map.of(VirusStrain.SARS_CoV_2, 3,
					 VirusStrain.ALPHA, 3,
					 VirusStrain.DELTA, 1,
					 VirusStrain.OMICRON_BA1, 1,
					 VirusStrain.OMICRON_BA2, 1,
					 VirusStrain.STRAIN_A, 1
			 ));

	 private static final Object2IntMap<VirusStrain> daysInHospitalGivenNoICU = new Object2IntAVLTreeMap<>(
			 Map.of(VirusStrain.SARS_CoV_2, 22,
					 VirusStrain.ALPHA, 22,
					 VirusStrain.DELTA, 14,
					 VirusStrain.OMICRON_BA1, 14,
					 VirusStrain.OMICRON_BA2, 14,
					 VirusStrain.STRAIN_A, 14
			 ));

	 private static final Object2IntMap<VirusStrain> daysInHospitalGivenICU = new Object2IntAVLTreeMap<>(
			 Map.of(VirusStrain.SARS_CoV_2, 38,
					 VirusStrain.ALPHA, 38,
					 VirusStrain.DELTA, 20,
					 VirusStrain.OMICRON_BA1, 20,
					 VirusStrain.OMICRON_BA2, 20,
					 VirusStrain.STRAIN_A, 29
			 ));

	 private static final Object2IntMap<VirusStrain> daysInICU = new Object2IntAVLTreeMap<>(
			 Map.of(VirusStrain.SARS_CoV_2, 28,
					 VirusStrain.ALPHA, 28,
					 VirusStrain.DELTA, 17,
					 VirusStrain.OMICRON_BA1, 8,
					 VirusStrain.OMICRON_BA2, 8,
					 VirusStrain.STRAIN_A, 21
			 ));

	 private static final Map<VirusStrain, Double> antibodyMultiplier = new HashMap<>(
			 Map.of(
					 VirusStrain.SARS_CoV_2, 1.,
					 VirusStrain.ALPHA, 1.,
					 VirusStrain.DELTA, 1.,
					 VirusStrain.OMICRON_BA1, 4.,
					 VirusStrain.OMICRON_BA2, 4.,
					 VirusStrain.STRAIN_A, 4.
			 ));



	 private static final double factorWildAndAlpha = 0.25;
	 private static final double factorDelta = 0.45;
	 private static final double factorOmicron = 0.07;

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

		 // filter population by location and/or age
		 filteredPopulationIds = population.getPersons().values().stream()
				 .filter(x -> x.getAttributes().getAttribute("district").equals(district)
//				 ((int) x.getAttributes().getAttribute("microm:modeled:age")) >= 18 &&
//				 ((int) x.getAttributes().getAttribute("microm:modeled:age")) <= 59
				 )
				 .map(x -> x.getId())
				 .collect(Collectors.toList());



		 // Here we define values factorSeriouslySickStrainA should have
//		 List<Double> strainFactors = List.of(factorOmicron, factorDelta);
		 List<Double> strainFactors = List.of(factorOmicron);

		 for (Double facA : strainFactors) {

			 if (facA == factorWildAndAlpha) {
				 outputAppendix = "_Alpha";
			 } else if (facA == factorDelta) {
				 outputAppendix = "_Delta";
			 } else if (facA == factorOmicron) {
				 outputAppendix = "_Omicron";
			 } else {
				 throw new RuntimeException("not clear what to do");
			 }


			 // configure factorSeriouslySick for each strain
			 Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
			 strainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
			 strainConfig.getOrAddParams(VirusStrain.SARS_CoV_2).setFactorSeriouslySick(factorWildAndAlpha);
			 strainConfig.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySick(factorWildAndAlpha);
			 strainConfig.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySick(factorDelta);
			 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySick(factorOmicron);
			 strainConfig.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorSeriouslySick(factorOmicron);
			 strainConfig.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(facA);


			 // Part 1: calculate hospitalizations for each seed and save as csv
			 AnalysisCommand.forEachScenario(output, scenario -> {
				 try {
					 analyzeOutput(scenario);

				 } catch (IOException e) {
					 log.error("Failed processing {}", scenario, e);
				 }
			 });

			 log.info("done");

			 // Part 2: aggregate over multiple seeds & produce tsv output & plot
			 List<Path> pathList = new ArrayList<>();
			 AnalysisCommand.forEachScenario(output, pathList::add);

			 aggregateDataAndProduceTSV(output, pathList);

		 }


		 return 0;
	 }

	 @Override
	 public void analyzeOutput(Path output) throws IOException {

		 String id = AnalysisCommand.getScenarioPrefix(output);

		 final Path tsvPath = output.resolve(id + "post.hospital" + outputAppendix + ".tsv");

		 calculateHospitalizationsAndWriteOutput(output, tsvPath);

		 log.info("Calculated results for output {}", output);

	 }

	 private void calculateHospitalizationsAndWriteOutput(Path output, Path tsvPath) throws IOException {
		 BufferedWriter bw = Files.newBufferedWriter(tsvPath);

		 bw.write("day\tdate\tpostProcessHospitalizations\tppBeds\tppBedsICU\thospNoImmunity\thospBaseImmunity\thospBoosted\tincNoImmunity\tincBaseImmunity\tincBoosted");

		 Map<Id<Person>, Handler.Holder> data = new IdMap<>(Person.class, population.getPersons().size());

		 Handler handler = new Handler(data, startDate, population, strainConfig);

		 AnalysisCommand.forEachEvent(output, s -> {
		 }, handler);

		 int maxIteration = Math.max(
				 handler.postProcessHospitalAdmissions.keySet().lastInt(),
				 Math.max(handler.postProcessHospitalFilledBeds.keySet().lastInt(),
						 handler.postProcessHospitalFilledBedsICU.keySet().lastInt()));


		 double popSize = filteredPopulationIds.size();
		 double totNoImmunity = popSize;
		 double totbaseImmunity = 0;
		 double totBoostered = 0;

		 for (int day = 0; day <= maxIteration; day++) {
			 LocalDate date = startDate.plusDays(day);

			 // calculates Incidence - 7day hospitalizations per 100,000 residents
			 double ppHosp = getWeeklyHospitalizations(handler.postProcessHospitalAdmissions, day)  * 100_000. / popSize;

			 // calculates daily hospital occupancy, per 100,000 residents
			 double ppBed = handler.postProcessHospitalFilledBeds.getOrDefault(day, 0) * 100_000. / popSize;
			 double ppBedICU = handler.postProcessHospitalFilledBedsICU.getOrDefault(day, 0) * 100_000. / popSize;

			 //
			 totNoImmunity += handler.changeNoImmunity.get(day);
			 totbaseImmunity += handler.changeBaseImmunity.get(day);
			 totBoostered += handler.changeBoostered.get(day);

			 double hospNoImmunity = getWeeklyHospitalizations(handler.hospNoImmunity, day) * 100_000. / totNoImmunity;
			 double hospBaseImmunity = getWeeklyHospitalizations(handler.hospBaseImmunity, day) * 100_000. / totbaseImmunity;
			 double hospBoosted = getWeeklyHospitalizations(handler.hospBoostered, day) * 100_000. / totBoostered;

			 double incNoImmunity = getWeeklyHospitalizations(handler.incNoImmunity, day) * 100_000. / totNoImmunity;
			 double incBaseImmunity = getWeeklyHospitalizations(handler.incBaseImmunity, day) * 100_000. / totbaseImmunity;
			 double incBoosted = getWeeklyHospitalizations(handler.incBoostered, day) * 100_000. / totBoostered;


			 bw.newLine();
			 bw.write(AnalysisCommand.TSV.join(day, date, ppHosp, ppBed, ppBedICU, hospNoImmunity, hospBaseImmunity, hospBoosted,incNoImmunity,incBaseImmunity,incBoosted));

		 }

		 bw.close();
	 }

	 private void aggregateDataAndProduceTSV(Path output, List<Path> pathList) throws IOException {


		 // read hospitalization tsv for all seeds and aggregate them!
		 // NOTE: all other parameters should be the same, otherwise the results will be useless!
		 Int2DoubleSortedMap postProcessHospitalizations = new Int2DoubleAVLTreeMap();
		 Int2DoubleSortedMap ppBeds = new Int2DoubleAVLTreeMap();
		 Int2DoubleSortedMap ppBedsICU = new Int2DoubleAVLTreeMap();

		 Int2DoubleSortedMap hospNoImmunity =  new Int2DoubleAVLTreeMap();
		 Int2DoubleSortedMap hospBaseImmunity =  new Int2DoubleAVLTreeMap();
		 Int2DoubleSortedMap hospBoosted =  new Int2DoubleAVLTreeMap();

		 Int2DoubleSortedMap incNoImmunity =  new Int2DoubleAVLTreeMap();
		 Int2DoubleSortedMap incBaseImmunity =  new Int2DoubleAVLTreeMap();
		 Int2DoubleSortedMap incBoosted =  new Int2DoubleAVLTreeMap();




		 for (Path path : pathList) {

			 String id = AnalysisCommand.getScenarioPrefix(path);

			 final Path tsvPath = path.resolve(id + "post.hospital" + outputAppendix + ".tsv");

			 try (CSVParser parser = new CSVParser(Files.newBufferedReader(tsvPath), CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader())) {

				 for (CSVRecord record : parser) {

					 int day = Integer.parseInt(record.get("day"));

					 postProcessHospitalizations.mergeDouble(day, Double.parseDouble(record.get("postProcessHospitalizations")) / pathList.size(), Double::sum);
					 ppBeds.mergeDouble(day, Double.parseDouble(record.get("ppBeds")) / pathList.size(), Double::sum);
					 ppBedsICU.mergeDouble(day, Double.parseDouble(record.get("ppBedsICU")) / pathList.size(), Double::sum);

					 hospNoImmunity.mergeDouble(day, Double.parseDouble(record.get("hospNoImmunity")) / pathList.size(), Double::sum);
					 hospBaseImmunity.mergeDouble(day, Double.parseDouble(record.get("hospBaseImmunity")) / pathList.size(), Double::sum);
					 hospBoosted.mergeDouble(day, Double.parseDouble(record.get("hospBoosted")) / pathList.size(), Double::sum);

					 incNoImmunity.mergeDouble(day, Double.parseDouble(record.get("incNoImmunity")) / pathList.size(), Double::sum);
					 incBaseImmunity.mergeDouble(day, Double.parseDouble(record.get("incBaseImmunity")) / pathList.size(), Double::sum);
					 incBoosted.mergeDouble(day, Double.parseDouble(record.get("incBoosted")) / pathList.size(), Double::sum);
				 }
			 }
		 }

		 // read rki data and add to tsv
		 Int2DoubleMap rkiHospIncidence = new Int2DoubleAVLTreeMap();
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
			 }
		 }

		 // read rki data and add to columns
		 // pink plot from covid-sim: general beds
		 //  https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/original-data/hospital-cases/cologne/KoelnAllgemeinpatienten.csv (pinke Linie)
		 Int2DoubleMap reportedBeds = new Int2DoubleAVLTreeMap();
		 try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("../public-svn/matsim/scenarios/countries/de/episim/original-data/hospital-cases/cologne/KoelnAllgemeinpatienten.csv")),
				 CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader())) {

			 for (CSVRecord record : parser) {
				 String dateStr = record.get("date").split("T")[0];
				 LocalDate date = LocalDate.parse(dateStr);
				 int day = (int) startDate.until(date, ChronoUnit.DAYS);

				 double incidence;
				 try {
					 incidence = Double.parseDouble(record.get("allgemeinpatienten")) * 100_000. / populationCntOfficial;
				 } catch (NumberFormatException e) {
					 incidence = 0.;
				 }

				 reportedBeds.put(day, incidence);
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
					 incidence = Double.parseDouble(record.get("faelle_covid_aktuell")) * 100_000. / populationCntOfficial;
				 } catch (NumberFormatException ignored) {

				 }

				 reportedBedsICU.put(day, incidence);
			 }
		 }

		 // Produce TSV w/ aggregated data as well as all rki numbers
		 try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(output.resolve("post.hospital.agg" + outputAppendix + ".tsv")), CSVFormat.DEFAULT.withDelimiter('\t'))) {

			 printer.printRecord("day", "date", "modelIncidencePost", "modelHospRate", "modelCriticalRate", "rkiIncidence", "rkiHospRate", "rkiCriticalRate","hospNoImmunity", "hospBaseImmunity", "hospBoosted", "incNoImmunity", "incBaseImmunity", "incBoosted");

			 double maxIteration = Double.max(postProcessHospitalizations.lastIntKey(), Double.max(ppBeds.lastIntKey(), ppBedsICU.lastIntKey()));

			 for (int day = 0; day <= maxIteration; day++) {
				 LocalDate date = startDate.plusDays(day);
				 printer.printRecord(
						 day,
						 date,
						 postProcessHospitalizations.get(day),
						 ppBeds.get(day),
						 ppBedsICU.get(day),
						 rkiHospIncidence.get(day),
						 reportedBeds.get(day),
						 reportedBedsICU.get(day),
						 hospNoImmunity.get(day),
						 hospBaseImmunity.get(day),
						 hospBoosted.get(day),
						 incNoImmunity.get(day),
						 incBaseImmunity.get(day),
						 incBoosted.get(day)
				 );
			 }
		 }

		 // PLOT 1: People admitted to hospital
		 {
			 IntColumn records = IntColumn.create("day");
			 DateColumn recordsDate = DateColumn.create("date");
			 DoubleColumn values = DoubleColumn.create("hospitalizations");
			 StringColumn groupings = StringColumn.create("scenario");

			 // post-processed hospitalizations from episim
			 for (Int2DoubleMap.Entry entry : postProcessHospitalizations.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));
				 values.append(postProcessHospitalizations.getOrDefault(day, Double.NaN));
				 groupings.append("postProcess");
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
				 groupings.append("RKI NRW Adjusted");
			 }


			 producePlot(recordsDate, values, groupings, "", "7-Tage Hospitalisierungsinzidenz", "HospIncidence" + outputAppendix + ".html");
		 }


		 // PLOT 2: People taking up beds in hospital (regular and ICU)
		 {
			 IntColumn records = IntColumn.create("day");
			 DateColumn recordsDate = DateColumn.create("date");
			 DoubleColumn values = DoubleColumn.create("hospitalizations");
			 StringColumn groupings = StringColumn.create("scenario");


			 for (Int2DoubleMap.Entry entry : ppBeds.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(ppBeds.get(day));
				 groupings.append("generalBeds");
			 }

			 for (Int2DoubleMap.Entry entry : ppBedsICU.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(ppBedsICU.get(day));
				 groupings.append("ICUBeds");
			 }


			 for (Int2DoubleMap.Entry entry : reportedBeds.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(entry.getDoubleValue());
				 groupings.append("Reported: General Beds");
			 }


			 for (Int2DoubleMap.Entry entry : reportedBedsICU.int2DoubleEntrySet()) {
				 int day = entry.getIntKey();
				 records.append(day);
				 recordsDate.append(startDate.plusDays(day));

				 values.append(entry.getDoubleValue());
				 groupings.append("Reported: ICU Beds");
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
		 Axis yAxis = Axis.builder().range(0., 150.)
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


	 private static class Handler implements EpisimVaccinationEventHandler, EpisimInfectionEventHandler {

		 private final Map<Id<Person>, Holder> data;
		 private final LocalDate startDate;
		 private final Population population;
		 private final Random rnd;
		 private final VirusStrainConfigGroup strainConfig;

		 private final Int2IntSortedMap postProcessHospitalAdmissions;
		 private final Int2IntSortedMap postProcessHospitalFilledBeds;
		 private final Int2IntSortedMap postProcessHospitalFilledBedsICU;

		 private final Int2IntSortedMap changeBaseImmunity;
		 private final Int2IntMap changeNoImmunity;
		 private final Int2IntMap changeBoostered;
		 private final Int2IntMap hospNoImmunity;
		 private final Int2IntMap hospBoostered;
		 private final Int2IntMap hospBaseImmunity;
		 public final Int2IntMap incNoImmunity;
		 public final Int2IntMap incBaseImmunity;
		 public final Int2IntMap incBoostered;


		 public Handler(Map<Id<Person>, Holder> data, LocalDate startDate, Population population, VirusStrainConfigGroup strainConfig) {
			 this.data = data;
			 this.startDate = startDate;
			 this.population = population;
			 this.rnd = new Random(1234);
			 this.strainConfig = strainConfig;

			 this.postProcessHospitalAdmissions = new Int2IntAVLTreeMap();
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
		 }

		 @Override
		 public void handleEvent(EpisimInfectionEvent event) {

			 Holder person = data.computeIfAbsent(event.getPersonId(), Holder::new);

			 String district = (String) population.getPersons().get(person.personId).getAttributes().getAttribute("district");
			 int age = (int) population.getPersons().get(person.personId).getAttributes().getAttribute("microm:modeled:age");

			 if (!district.equals("Köln")){// || age < 18 || age > 59) {
				 return;
			 }

			 int day = (int) (event.getTime() / 86_400);

			 person.immunityDays.add(day);
			 person.immunityEvents.add(event.getVirusStrain());
			 person.antibodies = event.getAntibodies();

			 updateHospitalizationsPost(person, event.getVirusStrain(), day);

			 if (person.immunityStatus == Holder.ImmunityStatus.no) {
				 incNoImmunity.mergeInt(day, 1, Integer::sum);
			 } else if (person.immunityStatus == Holder.ImmunityStatus.base) {
				 incBaseImmunity.mergeInt(day, 1, Integer::sum);
			 } else {
				 incBoostered.mergeInt(day, 1, Integer::sum);
			 }

		 }

		 @Override
		 public void handleEvent(EpisimVaccinationEvent event) {
			 Holder person = data.computeIfAbsent(event.getPersonId(), Holder::new);

			 String district = (String) population.getPersons().get(person.personId).getAttributes().getAttribute("district");
			 int age = (int) population.getPersons().get(person.personId).getAttributes().getAttribute("microm:modeled:age");

			 if (!district.equals("Köln")){ // || age < 18 || age > 59) {
				 return;
			 }

			 int day = (int) (event.getTime() / 86_400);

			 if (person.immunityStatus == Holder.ImmunityStatus.no) {
				 person.immunityStatus = Holder.ImmunityStatus.base;
				 changeBaseImmunity.mergeInt(day, 1, Integer::sum);
				 changeNoImmunity.mergeInt(day, -1, Integer::sum);
			 } else if (person.immunityStatus == Holder.ImmunityStatus.base) {
				 person.immunityStatus = Holder.ImmunityStatus.boost;
				 changeBoostered.mergeInt(day, 1, Integer::sum);
				 changeBaseImmunity.mergeInt(day, -1, Integer::sum);
			 }


			 person.immunityDays.add(day);
			 person.immunityEvents.add(event.getVaccinationType());
		 }

		 private void updateHospitalizationsPost(Holder person, VirusStrain strain, int infectionIteration) {

			 int age = (int) population.getPersons().get(person.personId).getAttributes().getAttribute("microm:modeled:age");


			 if (goToHospital(person, strain, age)) {

				 // newly admitted to hospital
				 int inHospital = infectionIteration + lagBetweenInfectionAndHospitalisation.getInt(strain);
				 postProcessHospitalAdmissions.mergeInt(inHospital, 1, Integer::sum);


				 if (person.immunityStatus == Holder.ImmunityStatus.no) {
					 hospNoImmunity.mergeInt(inHospital, 1, Integer::sum);
				 } else if (person.immunityStatus == Holder.ImmunityStatus.base) {
					 hospBaseImmunity.mergeInt(inHospital, 1, Integer::sum);
				 } else {
					 hospBoostered.mergeInt(inHospital, 1, Integer::sum);
				 }


				 if (goToICU(strain, age)) {

					 int inICU = inHospital + lagBetweenHospitalizationAndICU.getInt(strain);
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

		 private boolean goToICU(VirusStrain strain, int age) {
			 return rnd.nextDouble() < getProbaOfTransitioningToCritical(age) * strainConfig.getParams(strain).getFactorCritical()
					 * getCriticalFactor();
		 }

		 /**
		  * calculates the probability that agent goes to hospital given an infection.
		  */
		 private boolean goToHospital(Holder person, VirusStrain strain, int age) {

			 double ageFactor = getProbaOfTransitioningToSeriouslySick(age);
			 double strainFactor = strainConfig.getParams(strain).getFactorSeriouslySick();

			 // checks whether agents goes to hospital
			 return rnd.nextDouble() < ageFactor
					 * strainFactor
					 * getSeriouslySickFactor(person, strain);
		 }

		 /**
		  * Adapted from AgeDependentDiseaseStatusTransitionModel, signature changed.
		  */
		 protected double getProbaOfTransitioningToSeriouslySick(int age) {

			 double proba;

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

			 return proba; //* hospitalFactor;
		 }

		 /**
		  * Adapted from AgeDependentDiseaseStatusTransitionModel
		  */
		 protected double getProbaOfTransitioningToCritical(int age) {
			 double proba;

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

			 // Antibodies at time of infection
			 Double antibodiesAtTimeOfInfection = person.antibodies;

			 int numImmunityEvents = person.immunityDays.size();

			 if (numImmunityEvents > 1) {
				 int currentImmunityEvent = person.immunityDays.getInt(numImmunityEvents - 1);
				 int previousImmunityEvent = person.immunityDays.getInt(numImmunityEvents - 2);
				 int daysSincePreviousImmunityEvent = currentImmunityEvent - previousImmunityEvent;
				 double halfLife_days = 60.;
				 // reverse exponential decay
				 double antibodiesAfterPreviousImmunityEvent = antibodiesAtTimeOfInfection * Math.pow(2., daysSincePreviousImmunityEvent / halfLife_days);
//				 antibodiesAfterPreviousImmunityEvent = person.immunityEvents.stream().filter(x -> x instanceof VaccinationType).count() > 1 ? 2 * antibodiesAfterPreviousImmunityEvent : antibodiesAfterPreviousImmunityEvent;
				 antibodiesAfterPreviousImmunityEvent *= antibodyMultiplier.get(strain);
				 return 1. / (1. + Math.pow(0.5 * antibodiesAfterPreviousImmunityEvent, beta));

			 }

			 // if agent didn't experience immunity events prior to current immunity event, they do don't benefit from any protection.
			 return 1.;



		 }

		 public static double getCriticalFactor() { //todo: is this right?
			 return 1.0;
		 }


		 /**
		  * Data holder for attributes
		  */
		 private static final class Holder {

			 public enum ImmunityStatus {no, base, boost}

			 private Double antibodies = null;
			 private final Id<Person> personId;
			 private final IntList immunityDays = new IntArrayList();
			 private final ObjectList<ImmunityEvent> immunityEvents = new ObjectArrayList();
			 private ImmunityStatus immunityStatus;


			 private Holder(Id<Person> personId) {
				 this.personId = personId;
				 this.immunityStatus = ImmunityStatus.no;
			 }

		 }

	 }

 }


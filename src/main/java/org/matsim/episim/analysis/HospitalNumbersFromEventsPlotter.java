package org.matsim.episim.analysis;

import it.unimi.dsi.fastutil.ints.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.matsim.episim.model.VirusStrain;
import org.matsim.run.AnalysisCommand;
import tech.tablesaw.api.*;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.components.Page;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.table.TableSliceGroup;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

public class HospitalNumbersFromEventsPlotter {
	private static final String DATE = "date";
	private static final String DAY = "day";
	final static String INTAKES_HOSP = "intakesHosp";
	final static String INTAKES_ICU = "intakesIcu";
	final static String OCCUPANCY_HOSP = "occupancyHosp";
	final static String OCCUPANCY_ICU = "occupancyIcu";

	private final static double populationCntOfficialNrw = 17_930_000;
	private final static double populationCntOfficialKoelln = 919_936;




	static void aggregateAndProducePlots(Path output, List<Path> pathList, String outputAppendix, LocalDate startDate, String scenarioToPlot) throws IOException {


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

			final Path tsvPath = path.resolve(id + "post.hospital.tsv");

			try (CSVParser parser = new CSVParser(Files.newBufferedReader(tsvPath), CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader())) {

				for (CSVRecord record : parser) {

					if (!record.get("severity").equals(scenarioToPlot)) {
						continue;
					}

					int day = Integer.parseInt(record.get(DAY));

					double n = Double.parseDouble(record.get("n"));
					switch (record.get("measurement")) {
						case INTAKES_HOSP:
							intakeHosp.mergeDouble(day, n / pathList.size(), Double::sum);
							break;
						case INTAKES_ICU:
							intakeIcu.mergeDouble(day, n / pathList.size(), Double::sum);
							break;
						case OCCUPANCY_HOSP:
							occupancyHosp.mergeDouble(day, n / pathList.size(), Double::sum);
							break;
						case OCCUPANCY_ICU:
							occupancyIcu.mergeDouble(day, n / pathList.size(), Double::sum);
							break;
						default:
							throw new RuntimeException("not valid measurement");
					}

//					intakeHosp.mergeDouble(day, Double.parseDouble(record.get(INTAKES_HOSP)) / pathList.size(), Double::sum);
//					intakeIcu.mergeDouble(day, Double.parseDouble(record.get(INTAKES_ICU)) / pathList.size(), Double::sum);
//					occupancyHosp.mergeDouble(day, Double.parseDouble(record.get(OCCUPANCY_HOSP)) / pathList.size(), Double::sum);
//					occupancyIcu.mergeDouble(day, Double.parseDouble(record.get(OCCUPANCY_ICU)) / pathList.size(), Double::sum);

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

		// read rki COVID-SARI data (new) and add to tsv

		Int2DoubleMap covidSariHospIncidence = new Int2DoubleAVLTreeMap();
		URL url = new URL("https://raw.githubusercontent.com/robert-koch-institut/COVID-SARI-Hospitalisierungsinzidenz/main/COVID-SARI-Hospitalisierungsinzidenz.tsv");
		try (CSVParser parser = new CSVParser(new BufferedReader(new InputStreamReader(url.openStream())),
			CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader())) {

			for (CSVRecord record : parser) {
				if (!record.get("agegroup").equals("00+")) {
					continue;
				}

				DateTimeFormatter formatter = new DateTimeFormatterBuilder()
					.appendPattern("YYYY-'W'ww")
					.parseDefaulting(ChronoField.DAY_OF_WEEK, DayOfWeek.THURSDAY.getValue())
					.toFormatter(Locale.GERMAN);
				LocalDate date = LocalDate.parse(record.get("date"), formatter);
				System.out.println(date);


				int day = (int) startDate.until(date, ChronoUnit.DAYS);
				System.out.println(day);

				double incidence;
				try {
					incidence = Double.parseDouble(record.get("sari_covid19_incidence"));
				} catch (NumberFormatException e) {
					incidence = Double.NaN;
				}

				covidSariHospIncidence.put(day, incidence);
			}
		}


		// read rki data and add to tsv
		Int2DoubleMap rkiHospIncidence = new Int2DoubleAVLTreeMap();
		Int2DoubleMap rkiHospIncidenceAdj = new Int2DoubleAVLTreeMap();
		// 		try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("../covid-sim/COVID-Hospitalization/Aktuell_Deutschland_adjustierte-COVID-19-Hospitalisierungen.csv")),
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
				if (date.isBefore(LocalDate.of(2022, 11, 1))) {
					incidenceAdj = incidence * 2 / 3;
				} else{
					incidenceAdj = incidence / 3;
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

			double xxx = HospitalNumbersFromEvents.getWeeklyHospitalizations(reportedIcuCases, day) * 100_000. / populationCntOfficialNrw;
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
//		Int2DoubleMap reportedBedsNrw = new Int2DoubleAVLTreeMap();
//		Int2DoubleMap reportedBedsIcuNrw = new Int2DoubleAVLTreeMap();
//		try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("/Users/jakob/Downloads/Covid_csvgesamt(2).csv")),
//				CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader())) {
//
//			for (CSVRecord record : parser) {
//
//				if (!record.get("Bundesland").equals("Nordrhein-Westfalen")) {
//					continue;
//				}
//
//				String dateStr = record.get("Datum");
//				LocalDate date = LocalDate.parse(dateStr);
//				int day = (int) startDate.until(date, ChronoUnit.DAYS);
//
//				double incidence = 0.;
//				try {
//					incidence = Double.parseDouble(record.get("Betten")) * 100_000. / populationCntOfficialNrw;
//				} catch (NumberFormatException ignored) {
//				}
//
//				if (record.get("Bettenart").equals("Intensivbett")) {
//					reportedBedsIcuNrw.put(day, incidence);
//				} else if (record.get("Bettenart").equals("Normalbett")) {
//					reportedBedsNrw.put(day, incidence);
//				}
//
//			}
//		}


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
		try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(output.resolve("post.hospital.agg.tsv")), CSVFormat.DEFAULT.withDelimiter('\t'))) {

			printer.printRecord(DAY, DATE, INTAKES_HOSP, INTAKES_ICU, OCCUPANCY_HOSP, OCCUPANCY_ICU, "rkiIncidence", "rkiHospRate", "rkiCriticalRate"); //"hospNoImmunity", "hospBaseImmunity", "hospBoosted", "incNoImmunity", "incBaseImmunity", "incBoosted");

			double maxIteration = Double.max(Double.max(intakeHosp.lastIntKey(), intakeIcu.lastIntKey()), Double.max(occupancyHosp.lastIntKey(), occupancyIcu.lastIntKey()));

			for (int day = 0; day <= maxIteration; day++) {
				LocalDate date = startDate.plusDays(day);
				printer.printRecord(
						day,
						date,
						intakeHosp.get(day),
						intakeIcu.get(day),
						covidSariHospIncidence.get(day),
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

			for (Int2DoubleMap.Entry entry : covidSariHospIncidence.int2DoubleEntrySet()) {
				int day = entry.getIntKey();
				records.append(day);
				recordsDate.append(startDate.plusDays(day));
				values.append(covidSariHospIncidence.getOrDefault(day, Double.NaN));
				groupings.append("obs: covid-sari");
			}

			// model: intakeIcu
//			for (Int2DoubleMap.Entry entry : intakeIcu.int2DoubleEntrySet()) {
//				int day = entry.getIntKey();
//				records.append(day);
//				recordsDate.append(startDate.plusDays(day));
//				values.append(intakeIcu.getOrDefault(day, Double.NaN));
//				groupings.append("model: intakeICU");
//			}

//
//			for (Int2DoubleMap.Entry entry : rkiHospIncidence.int2DoubleEntrySet()) {
//				int day = entry.getIntKey();
//				records.append(day);
//				recordsDate.append(startDate.plusDays(day));
//				final double value = rkiHospIncidence.getOrDefault(day, Double.NaN);
//				if (Double.isNaN(value)) {
//					values.appendMissing();
//				} else {
//					values.append(value);
//				}
//				groupings.append("reported: intakeHosp (rki, nrw adjusted) WITH Covid");
//			}
//
//			for (Int2DoubleMap.Entry entry : rkiHospIncidenceAdj.int2DoubleEntrySet()) {
//				int day = entry.getIntKey();
//				records.append(day);
//				recordsDate.append(startDate.plusDays(day));
//				final double value = rkiHospIncidenceAdj.getOrDefault(day, Double.NaN);
//				if (Double.isNaN(value)) {
//					values.appendMissing();
//				} else {
//					values.append(value);
//				}
//				groupings.append("reported: intakeHosp (rki, nrw adjusted) FROM Covid");
//			}
//
//			for (Int2DoubleMap.Entry entry : hospIncidenceKoeln.int2DoubleEntrySet()) {
//				int day = entry.getIntKey();
//				records.append(day);
//				recordsDate.append(startDate.plusDays(day));
//				final double value = hospIncidenceKoeln.getOrDefault(day, Double.NaN);
//				if (Double.isNaN(value)) {
//					values.appendMissing();
//				} else {
//					values.append(value);
//				}
//				groupings.append("reported: intakeHosp (köln)");
//			}
//
//			for (Int2DoubleMap.Entry entry : reportedIcuIncidence.int2DoubleEntrySet()) {
//				int day = entry.getIntKey();
//				records.append(day);
//				recordsDate.append(startDate.plusDays(day));
//				final double value = reportedIcuIncidence.getOrDefault(day, Double.NaN);
//				if (Double.isNaN(value)) {
//					values.appendMissing();
//				} else {
//					values.append(value);
//				}
//				groupings.append("reported: intakeIcu (divi, nrw)");
//			}


			producePlot(recordsDate, values, groupings, "", "7-Tage Hospitalisierungsinzidenz", "HospIncidence" + outputAppendix + ".html", output);
		}


		// PLOT 2: People taking up beds in hospital (regular and ICU)
//		{
//			IntColumn records = IntColumn.create("day");
//			DateColumn recordsDate = DateColumn.create("date");
//			DoubleColumn values = DoubleColumn.create("hospitalizations");
//			StringColumn groupings = StringColumn.create("scenario");
//
//
//			for (Int2DoubleMap.Entry entry : occupancyHosp.int2DoubleEntrySet()) {
//				int day = entry.getIntKey();
//				records.append(day);
//				recordsDate.append(startDate.plusDays(day));
//
//				values.append(occupancyHosp.get(day));
//				groupings.append("generalBeds");
//			}
//
//			for (Int2DoubleMap.Entry entry : occupancyIcu.int2DoubleEntrySet()) {
//				int day = entry.getIntKey();
//				records.append(day);
//				recordsDate.append(startDate.plusDays(day));
//
//				values.append(occupancyIcu.get(day));
//				groupings.append("ICUBeds");
//			}
//
//
//			for (Int2DoubleMap.Entry entry : reportedBeds.int2DoubleEntrySet()) {
//				int day = entry.getIntKey();
//				records.append(day);
//				recordsDate.append(startDate.plusDays(day));
//
//				values.append(entry.getDoubleValue());
//				groupings.append("Reported: General Beds");
//
//			}
//
//			for (Int2DoubleMap.Entry entry : reportedBedsAdj.int2DoubleEntrySet()) {
//				int day = entry.getIntKey();
//				records.append(day);
//				recordsDate.append(startDate.plusDays(day));
//
//				values.append(entry.getDoubleValue());
//				groupings.append("Reported: General Beds (SARI)");
//			}
//
//
//			for (Int2DoubleMap.Entry entry : reportedBedsICU.int2DoubleEntrySet()) {
//				int day = entry.getIntKey();
//				records.append(day);
//				recordsDate.append(startDate.plusDays(day));
//
//				values.append(entry.getDoubleValue());
//				groupings.append("Reported: ICU Beds");
//
//			}
//
//
////			for (Int2DoubleMap.Entry entry : reportedBedsNrw.int2DoubleEntrySet()) {
////				int day = entry.getIntKey();
////				records.append(day);
////				recordsDate.append(startDate.plusDays(day));
////
////				values.append(entry.getDoubleValue());
////				groupings.append("Reported: General Beds (NRW)");
////
////			}
//
//
////			for (Int2DoubleMap.Entry entry : reportedBedsIcuNrw.int2DoubleEntrySet()) {
////				int day = entry.getIntKey();
////				records.append(day);
////				recordsDate.append(startDate.plusDays(day));
////
////				values.append(entry.getDoubleValue());
////				groupings.append("Reported: ICU Beds (NRW)");
////
////			}
//
//			for (Int2DoubleMap.Entry entry : reportedBedsNrw2.int2DoubleEntrySet()) {
//				int day = entry.getIntKey();
//				records.append(day);
//				recordsDate.append(startDate.plusDays(day));
//
//				values.append(entry.getDoubleValue());
//				groupings.append("Reported: General Beds (NRW2)");
//
//			}
//
//			for (Int2DoubleMap.Entry entry : reportedBedsIcuNrw2.int2DoubleEntrySet()) {
//				int day = entry.getIntKey();
//				records.append(day);
//				recordsDate.append(startDate.plusDays(day));
//
//				values.append(entry.getDoubleValue());
//				groupings.append("Reported: ICU Beds (NRW2)");
//
//			}
//
//
//
//			// Make plot
//			producePlot(recordsDate, values, groupings, "Filled Beds", "Beds Filled / 100k Population", "FilledBeds" + outputAppendix + ".html", output);
//		}


	}

	private static void producePlot(DateColumn records, DoubleColumn values, StringColumn groupings, String title, String yAxisTitle, String filename, Path output) {
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

}

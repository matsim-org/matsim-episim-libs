/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

import com.google.common.base.Joiner;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;

import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * @author: rewert This class reads the SENOZON data for every day and analyzes
 *          the moved ranges. The data is filtered by the zip codes of every
 *          area. The results for every day are the percentile of the changes
 *          compared to the base.
 */
@CommandLine.Command(name = "analyzeSnzData", description = "Aggregate snz mobility data.")
class AnalyzeSnzRange implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(AnalyzeSnzRange.class);

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final DateTimeFormatter FMT_holiday = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final Joiner JOIN = Joiner.on(";");

	private enum AnalyseAreas {
		Germany, Berlin, Munich, Heinsberg, Bonn, Mannheim, Wolfsburg, BerlinDistricts, Test, Berchtesgaden, Hamburg,
		Bundeslaender, Landkreise, AnyArea, Collogne, Frankfurt
	}

	private enum AnalyseOptions {
		onlyWeekdays, onlySaturdays, onlySundays, weeklyResultsOfAllDays, onlyWeekends, dailyResults, Mo_Do
	};

	@CommandLine.Parameters(defaultValue = "../shared-svn/projects/episim/data/Bewegungsdaten/")
	private Path inputFolder;

	@CommandLine.Option(names = "--output", defaultValue = "output")
	private Path outputFolder;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalyzeSnzRange()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		AnalyseAreas selectedArea = AnalyseAreas.Landkreise;
		AnalyseOptions selectedOptionForAnalyse = AnalyseOptions.onlyWeekends;
		String anyArea = "Berlin";
		analyseData(selectedArea, selectedOptionForAnalyse, anyArea);

		log.info("Done!");

		return 0;
	}

	private void analyseData(AnalyseAreas selectedArea, AnalyseOptions selectedOptionForAnalyse, String anyArea)
			throws IOException {
		HashMap<String, IntSet> zipCodes = new HashMap<String, IntSet>();

		switch (selectedArea) {
		case AnyArea:
			zipCodes = findZipCodesForAnyArea(anyArea);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		case Bundeslaender:
			zipCodes = findZIPCodesForBundeslaender();
			outputFolder = Path.of("../public-svn/matsim/scenarios/countries/de/episim/mobilityData/bundeslaender/");
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, outputFolder);
			break;
		case Landkreise:
			zipCodes = findZIPCodesForLandkreise();
			outputFolder = Path.of("../public-svn/matsim/scenarios/countries/de/episim/mobilityData/landkreise/");
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, outputFolder);
			break;
		case Berchtesgaden:
			IntSet zipCodesBerchtesgaden = new IntOpenHashSet(List.of(83317, 83364, 83395, 83404, 83410, 83416, 83435,
					83451, 83454, 83457, 83458, 83471, 83483, 83486, 83487));
			zipCodes.put("Berchtesgaden", zipCodesBerchtesgaden);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		case Berlin:
			IntSet zipCodesBerlin = new IntOpenHashSet();
			for (int i = 10115; i <= 14199; i++)
				zipCodesBerlin.add(i);
			zipCodes.put("Berlin", zipCodesBerlin);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		case BerlinDistricts:
			zipCodes.put("Mitte", new IntOpenHashSet(List.of(10115, 10559, 13355, 10117, 10623, 13357, 10119, 10785,
					13359, 10787, 10557, 13353, 10555, 13351, 13349, 10551, 13347)));
			zipCodes.put("Friedrichshain_Kreuzberg", new IntOpenHashSet(List.of(10179, 10967, 10243, 10969, 10245,
					10997, 10247, 10999, 12045, 10961, 10963, 10965, 10178)));
			zipCodes.put("Pankow", new IntOpenHashSet(List.of(10249, 10405, 10407, 10409, 10435, 10437, 10439, 13051,
					13053, 13086, 13088, 13089, 13125, 13127, 13129, 13156, 13158, 13159, 13187, 13189)));
			zipCodes.put("Charlottenburg_Wilmersdorf",
					new IntOpenHashSet(List.of(10553, 10585, 10587, 10589, 10625, 10627, 10629, 10707, 10709, 10711,
							10713, 10715, 10717, 10719, 10789, 13597, 13627, 14050, 14053, 14055, 14057, 14059)));
			zipCodes.put("Spandau", new IntOpenHashSet(
					List.of(13581, 13583, 13585, 13587, 13589, 13591, 13593, 13595, 13599, 14052, 14089)));
			zipCodes.put("Steglitz_Zehlendorf", new IntOpenHashSet(List.of(12163, 12165, 12167, 12169, 12203, 12205,
					12207, 12209, 12247, 12279, 14109, 14129, 14163, 14165, 14167, 14169, 14193, 14195, 14199)));
			zipCodes.put("Tempelhof_Schoeneberg", new IntOpenHashSet(List.of(10777, 10779, 10781, 10783, 10823, 10825,
					10827, 14197, 10829, 12101, 12103, 12105, 12109, 12157, 12159, 12161, 12249, 12277, 12307, 12309)));
			zipCodes.put("Neukoelln", new IntOpenHashSet(List.of(12043, 12047, 12049, 12051, 12053, 12055, 12057, 12059,
					12099, 12107, 12305, 12347, 12349, 12351, 12353, 12355, 12357, 12359)));
			zipCodes.put("Treptow_Koepenick", new IntOpenHashSet(List.of(12435, 12437, 12439, 12459, 12487, 12489,
					12524, 12526, 12527, 12555, 12557, 12559, 12587, 12589, 12623)));
			zipCodes.put("Marzahn_Hellersdorf",
					new IntOpenHashSet(List.of(12619, 12621, 12627, 12629, 12679, 12681, 12683, 12685, 12687, 12689)));
			zipCodes.put("Lichtenberg",
					new IntOpenHashSet(List.of(10315, 13057, 10317, 10318, 10319, 10365, 10367, 10369, 13055, 13059)));
			zipCodes.put("Reinickendorf", new IntOpenHashSet(List.of(13403, 13405, 13407, 13409, 13435, 13437, 13439,
					13465, 13467, 13469, 13503, 13505, 13507, 13509, 13629)));
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		case Bonn:
			IntSet zipCodesBonn = new IntOpenHashSet();
			for (int i = 53100; i <= 53299; i++)
				zipCodesBonn.add(i);
			zipCodes.put("Bonn", zipCodesBonn);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		case Germany:
			IntSet zipCodesGER = new IntOpenHashSet();
			for (int i = 0; i <= 99999; i++)
				zipCodesGER.add(i);
			zipCodes.put("Germany", zipCodesGER);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		case Hamburg:
			IntSet zipCodesHamburg = new IntOpenHashSet();
			for (int i = 22000; i <= 22999; i++)
				zipCodesHamburg.add(i);
			zipCodes.put("Hamburg", zipCodesHamburg);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		case Heinsberg:
			IntSet zipCodesHeinsberg = new IntOpenHashSet(
					List.of(41812, 52538, 52511, 52525, 41836, 52538, 52531, 41849, 41844));
			zipCodes.put("Heinsberg", zipCodesHeinsberg);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		case Collogne:
			IntSet zipCodesCollogne = new IntOpenHashSet(List.of(50667, 50668, 50670, 50672, 50674, 50676, 50677, 50678,
					50679, 50733, 50735, 50737, 50739, 50765, 50767, 50769, 50823, 50825, 50827, 50829, 50858, 50859,
					50931, 50933, 50935, 50937, 50939, 50968, 50969, 50996, 50997, 50999, 51061, 51063, 51065, 51067,
					51069, 51103, 51105, 51107, 51109, 51143, 51145, 51147, 51149));
			zipCodes.put("Collogne", zipCodesCollogne);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		case Frankfurt:
			IntSet zipCodesFrankfurt = new IntOpenHashSet();
			for (int i = 60306; i <= 60599; i++)
				zipCodesFrankfurt.add(i);
			zipCodesFrankfurt.addAll(List.of(65929, 65931, 65933, 65934, 65936));
			zipCodes.put("FrankfurtMain", zipCodesFrankfurt);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
		case Mannheim:
			IntSet zipCodesMannheim = new IntOpenHashSet(List.of(68159, 68161, 68163, 68165, 68167, 68169, 68199, 68219,
					68229, 68239, 68259, 68305, 68307, 68309));
			zipCodes.put("Mannheim", zipCodesMannheim);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		case Munich:
			IntSet zipCodesMunich = new IntOpenHashSet();
			for (int i = 80331; i <= 81929; i++)
				zipCodesMunich.add(i);
			zipCodes.put("Munich", zipCodesMunich);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		case Test:
			IntSet zipCodesTest = new IntOpenHashSet(List.of(1067));
			zipCodes.put("Test", zipCodesTest);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		case Wolfsburg:
			IntSet zipCodesWolfsburg = new IntOpenHashSet(List.of(38440, 38442, 38444, 38446, 38448));
			zipCodes.put("Wolfsburg", zipCodesWolfsburg);
			analyzeDataForCertainAreas(zipCodes, selectedOptionForAnalyse, null);
			break;
		default:
			break;

		}
	}

	/**
	 * Analyze the data for one certain area.
	 * 
	 * @param area
	 * @param zipCodes
	 * @throws IOException
	 */
	private void analyzeDataForCertainAreas(HashMap<String, IntSet> zipCodes, AnalyseOptions selectedOptionForAnalyse,
			Path selectedOutputFolder) throws IOException {

		log.info("Searching for files in the folder: " + inputFolder);
		List<File> filesWithData = findInputFiles(inputFolder.toFile());
		Collections.sort(filesWithData);
		log.info("Amount of found files: " + filesWithData.size());

		Path outputFile = null;
		if (selectedOutputFolder != null)
			if (selectedOutputFolder.toString().contains("bundeslaender"))
				outputFile = selectedOutputFolder.resolve("BL_" + "Range_until.csv");
			else if (selectedOutputFolder.toString().contains("landkreise"))
				outputFile = selectedOutputFolder.resolve("LK_" + "Range_until.csv");
			else
				outputFile = selectedOutputFolder.resolve("Range_until.csv");
		else if (zipCodes.size() == 1)
			outputFile = outputFolder.resolve(zipCodes.keySet().iterator().next() + "Range_until.csv");
		else
			outputFile = selectedOutputFolder.resolve("Range_until.csv");

		HashMap<String, Set<LocalDate>> allHolidays = readBankHolidays();
		HashMap<String, Set<String>> lkAssignemt = createLKAssignmentToBL();

		BufferedWriter writer = IOUtils.getBufferedWriter(outputFile.toUri().toURL(), StandardCharsets.UTF_8, true);
		try {

			JOIN.appendTo(writer, Types.values());
			writer.write("\n");

			Map<String, Object2DoubleMap<String>> sums = new HashMap<>();
			for (String certainArea : zipCodes.keySet())
				sums.put(certainArea, new Object2DoubleOpenHashMap<>());

			int countingDays = 1;
			HashMap<String, Integer> anaylzedDaysPerAreaAndPeriod = new HashMap<String, Integer>();

			// will contain the last parsed date
			String dateString = "";

			// Analyzes all files with the mobility data
			for (File file : filesWithData) {

				dateString = file.getName().split("_")[0];
				LocalDate date = LocalDate.parse(dateString, FMT);
				DayOfWeek day = date.getDayOfWeek();

				switch (selectedOptionForAnalyse) {
				case weeklyResultsOfAllDays:
					readDataOfTheDay(zipCodes, sums, file, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
					if (day.equals(DayOfWeek.SUNDAY)) {
						writeOutput(selectedOptionForAnalyse, writer, anaylzedDaysPerAreaAndPeriod, dateString, sums);
						for (String area : sums.keySet())
							sums.get(area).clear();
					}
					break;
				case onlyWeekdays:
					if (!day.equals(DayOfWeek.SATURDAY) && !day.equals(DayOfWeek.SUNDAY)) {
						List<String> areasWithBankHoliday = new ArrayList<>();
						getAreasWithBankHoliday(areasWithBankHoliday, allHolidays, date);
						readDataOfTheDay(zipCodes, sums, file, areasWithBankHoliday, anaylzedDaysPerAreaAndPeriod,
								lkAssignemt);
					}
					if (day.equals(DayOfWeek.FRIDAY)) {
						writeOutput(selectedOptionForAnalyse, writer, anaylzedDaysPerAreaAndPeriod, dateString, sums);
						for (String area : sums.keySet())
							sums.get(area).clear();
					}
					break;
				case onlySaturdays:
					if (day.equals(DayOfWeek.SATURDAY)) {
						readDataOfTheDay(zipCodes, sums, file, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
						writeOutput(selectedOptionForAnalyse, writer, anaylzedDaysPerAreaAndPeriod, dateString, sums);
						for (String area : sums.keySet())
							sums.get(area).clear();
					}
					break;
				case onlySundays:
					if (day.equals(DayOfWeek.SUNDAY)) {
						readDataOfTheDay(zipCodes, sums, file, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
						writeOutput(selectedOptionForAnalyse, writer, anaylzedDaysPerAreaAndPeriod, dateString, sums);
						for (String area : sums.keySet())
							sums.get(area).clear();
					}
					break;
				case onlyWeekends:
					if (day.equals(DayOfWeek.SATURDAY) || day.equals(DayOfWeek.SUNDAY))
						readDataOfTheDay(zipCodes, sums, file, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
					if (day.equals(DayOfWeek.SUNDAY)) {
						writeOutput(selectedOptionForAnalyse, writer, anaylzedDaysPerAreaAndPeriod, dateString, sums);
						for (String area : sums.keySet())
							sums.get(area).clear();
						;
					}
					break;
				case dailyResults:
					readDataOfTheDay(zipCodes, sums, file, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
					writeOutput(selectedOptionForAnalyse, writer, anaylzedDaysPerAreaAndPeriod, dateString, sums);
					for (String area : sums.keySet())
						sums.get(area).clear();
					break;
				case Mo_Do:
					if (!day.equals(DayOfWeek.SATURDAY) && !day.equals(DayOfWeek.SUNDAY)
							&& !day.equals(DayOfWeek.FRIDAY)) {
						List<String> areasWithBankHoliday = new ArrayList<>();
						getAreasWithBankHoliday(areasWithBankHoliday, allHolidays, date);
						readDataOfTheDay(zipCodes, sums, file, areasWithBankHoliday, anaylzedDaysPerAreaAndPeriod,
								lkAssignemt);
					}
					if (day.equals(DayOfWeek.THURSDAY)) {
						writeOutput(selectedOptionForAnalyse, writer, anaylzedDaysPerAreaAndPeriod, dateString, sums);
						for (String area : sums.keySet())
							sums.get(area).clear();
					}
					break;
				default:
					break;

				}

				if (countingDays % 7 == 0)
					log.info("Finished week " + countingDays / 7);

				countingDays++;
			}
			writer.close();

			Path finalPath = null;
			switch (selectedOptionForAnalyse) {
			case weeklyResultsOfAllDays:
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_weekly"));
				break;
			case onlyWeekdays:
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_weekdays"));
				break;
			case onlySaturdays:
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_saturdays"));
				break;
			case onlySundays:
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_sundays"));
				break;
			case onlyWeekends:
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_weekends"));
				break;
			case dailyResults:
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_daily"));
				break;
			case Mo_Do:
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_Mo-Do"));
				break;
			default:
				break;

			}
			Files.move(outputFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
			log.info("Write analyze of " + countingDays + " is writen to " + finalPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** Writes the output of the analyzed time period.
	 * @param selectedOptionForAnalyse
	 * @param writer
	 * @param anaylzedDaysPerAreaAndPeriod
	 * @param dateString
	 * @param sums
	 * @throws IOException
	 */
	private void writeOutput(AnalyseOptions selectedOptionForAnalyse, BufferedWriter writer,
			HashMap<String, Integer> anaylzedDaysPerAreaAndPeriod, String dateString,
			Map<String, Object2DoubleMap<String>> sums) throws IOException {

		for (String certainArea : sums.keySet()) {
			for (String dataElement : sums.get(certainArea).keySet()) {
				sums.get(certainArea).put(dataElement, Math.round((sums.get(certainArea).getDouble(dataElement)
						/ anaylzedDaysPerAreaAndPeriod.get(certainArea))));
			}
		}
		for (String certainArea : sums.keySet()) {
		List<String> row = new ArrayList<>();
		row.add(dateString);
		row.add(certainArea);
		row.add(String.valueOf((int)sums.get(certainArea).getDouble("nPersons")));
		row.add(String.valueOf(round2Decimals(sums.get(certainArea).getDouble("nMobilePersons")
				/ sums.get(certainArea).getDouble("nPersons")) * 100));
		row.add(String.valueOf(round2Decimals(sums.get(certainArea).getDouble("dailyRangeSum")
				/ sums.get(certainArea).getDouble("nPersons"))));

		JOIN.appendTo(writer, row);
		writer.write("\n");
		}
	}

	/**
	 * Reads all bank holidays for Germany and each Bundesland
	 * 
	 * @return
	 * @throws IOException
	 */
	private HashMap<String, Set<LocalDate>> readBankHolidays() throws IOException {

		String bankHolidayFile = "src/main/resources/bankHolidays.csv";
		HashMap<String, Set<LocalDate>> bankHolidaysForBL = new HashMap<String, Set<LocalDate>>();

		try (BufferedReader reader = IOUtils.getBufferedReader(bankHolidayFile)) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				String[] BlWithBankHolidy = record.get("Bundesland").split(";");
				LocalDate date = LocalDate.parse(record.get("bankHoliday"), FMT_holiday);
				for (String bundesland : BlWithBankHolidy) {

					if (bankHolidaysForBL.containsKey(bundesland)) {
						if (!bankHolidaysForBL.get(bundesland).contains(date))
							bankHolidaysForBL.get(bundesland).add(date);
					} else
						bankHolidaysForBL.put(bundesland, new HashSet<LocalDate>(Arrays.asList(date)));
				}
			}
			Set<LocalDate> germanyBankHolidays = bankHolidaysForBL.get("Germany");

			for (String bundesland : bankHolidaysForBL.keySet())
				if (!bundesland.contains("Germany"))
					bankHolidaysForBL.get(bundesland).addAll(germanyBankHolidays);
		}
		return bankHolidaysForBL;
	}

	/**
	 * Creates an assignment of the landkreise to the Bundeslaender
	 * 
	 * @return
	 * @throws IOException
	 */
	private HashMap<String, Set<String>> createLKAssignmentToBL() throws IOException {

		String zipCodeFile = "../shared-svn/projects/episim/data/PLZ/zuordnung_plz_ort_landkreis.csv";
		HashMap<String, Set<String>> lKAssignment = new HashMap<String, Set<String>>();

		try (BufferedReader reader = IOUtils.getBufferedReader(zipCodeFile)) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				String nameLK = record.get("landkreis");
				if (nameLK.isEmpty())
					nameLK = record.get("ort");
				if (lKAssignment.containsKey(record.get("bundesland"))) {
					if (!lKAssignment.get(record.get("bundesland")).contains(nameLK))
						lKAssignment.get(record.get("bundesland")).add(String.valueOf(nameLK));
				} else
					lKAssignment.put(record.get("bundesland"), new HashSet<>(Arrays.asList(nameLK)));
			}
		}
		return lKAssignment;
	}

	/**
	 * Returns the Bundesland where is Landkreis is located
	 * 
	 * @param area
	 * @param lKAssignment
	 * @return
	 */
	private static String getRelatedBundesland(String area, HashMap<String, Set<String>> lKAssignment) {

		if (lKAssignment.containsKey(area))
			return area;

		for (String bundesland : lKAssignment.keySet())
			if (lKAssignment.get(bundesland).contains(area))
				return bundesland;
		return null;
	}


	/**
	 * Reads the file with the person statistics for all lists of zip Codes and
	 * saves the result in separate sums
	 * 
	 * @param zipCodesForAreas
	 * @param sums2
	 * @param file
	 * @param areasWithBankHoliday
	 * @param lkAssignemt
	 * @param anaylzedDaysPerAreaAndPeriod
	 * @throws IOException
	 */
	private void readDataOfTheDay(HashMap<String, IntSet> zipCodesOfAreas, Map<String, Object2DoubleMap<String>> sums,
			File file, List<String> areasWithBankHoliday, HashMap<String, Integer> anaylzedDaysPerAreaAndPeriod,
			HashMap<String, Set<String>> lkAssignemt) throws IOException {

		if (anaylzedDaysPerAreaAndPeriod.isEmpty())
			for (String nameArea : zipCodesOfAreas.keySet()) {
				anaylzedDaysPerAreaAndPeriod.put(nameArea, 0);
			}

		for (String area : anaylzedDaysPerAreaAndPeriod.keySet())
			if (areasWithBankHoliday == null || !areasWithBankHoliday.contains(getRelatedBundesland(area, lkAssignemt)))
				anaylzedDaysPerAreaAndPeriod.put(area, anaylzedDaysPerAreaAndPeriod.get(area) + 1);
		CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()
				.parse(IOUtils.getBufferedReader(file.toString()));

		for (CSVRecord record : parse) {
			for (String certainArea : zipCodesOfAreas.keySet()) {
				if (areasWithBankHoliday == null
						|| !areasWithBankHoliday.contains(getRelatedBundesland(certainArea, lkAssignemt))) {
					if (!record.get("zipCode").contains("NULL")) {
						int zipCode = Integer.parseInt(record.get("zipCode"));
						if (zipCodesOfAreas.get(certainArea).contains(zipCode)) {

							int nPersons = Integer.parseInt(record.get("nPersons"));
							double dailyRangeSum = Double.parseDouble(record.get("dailyRangeSum"));
							int nStayHome = Integer.parseInt(record.get("nStayHomes"));
							int nMobilePersons = Integer.parseInt(record.get("nMobilePersons"));

							sums.get(certainArea).mergeDouble("nStayHomes", nStayHome, Double::sum);
							sums.get(certainArea).mergeDouble("nMobilePersons", nMobilePersons, Double::sum);
							sums.get(certainArea).mergeDouble("nPersons", nPersons, Double::sum);
							sums.get(certainArea).mergeDouble("dailyRangeSum", dailyRangeSum, Double::sum);
						}
					}
				}
			}
		}
	}

	/**
	 * Finds all bundeslaender with bank holiday on this day
	 * 
	 * @param areasWithBankHoliday
	 * @param allHolidays
	 * @param date
	 */
	private void getAreasWithBankHoliday(List<String> areasWithBankHoliday, HashMap<String, Set<LocalDate>> allHolidays,
			LocalDate date) {

		for (String certainArea : allHolidays.keySet()) {
			if (allHolidays.get(certainArea).contains(date))
				areasWithBankHoliday.add(certainArea);
		}
	}

	/**
	 * This method searches all files with an certain name in a given folder.
	 */
	private static List<File> findInputFiles(File inputFolder) {
		List<File> fileData = new ArrayList<File>();

		for (File folder : Objects.requireNonNull(inputFolder.listFiles())) {
			if (folder.isDirectory()) {
				for (File file : Objects.requireNonNull(folder.listFiles())) {
					if (file.getName().contains("_personStats.csv.gz"))
						fileData.add(file);
				}
			}
		}
		return fileData;
	}

	/**
	 * Finds all zipCodes for all Bundesländer in Germany
	 * 
	 * @return
	 * @throws IOException
	 */
	private HashMap<String, IntSet> findZIPCodesForBundeslaender() throws IOException {

		String zipCodeFile = "../shared-svn/projects/episim/data/PLZ/zuordnung_plz_ort_landkreis.csv";
		HashMap<String, IntSet> zipCodesBL = new HashMap<String, IntSet>();
		zipCodesBL.put("Deutschland", new IntOpenHashSet());

		try (BufferedReader reader = IOUtils.getBufferedReader(zipCodeFile)) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				if (zipCodesBL.containsKey(record.get("bundesland"))) {
					zipCodesBL.get(record.get("bundesland")).add(Integer.parseInt(record.get("plz")));
					zipCodesBL.get("Deutschland").add(Integer.parseInt(record.get("plz")));

				} else {
					zipCodesBL.put(record.get("bundesland"),
							new IntOpenHashSet(List.of(Integer.parseInt(record.get("plz")))));
					zipCodesBL.get("Deutschland").add(Integer.parseInt(record.get("plz")));
				}
			}
		}
		return zipCodesBL;
	}

	/**
	 * Finds all zipCodes for all Landkreise in Germany
	 * 
	 * @return
	 * @throws IOException
	 */
	private HashMap<String, IntSet> findZIPCodesForLandkreise() throws IOException {

		String zipCodeFile = "../shared-svn/projects/episim/data/PLZ/zuordnung_plz_ort_landkreis.csv";
		HashMap<String, IntSet> zipCodesLK = new HashMap<String, IntSet>();
		zipCodesLK.put("Deutschland", new IntOpenHashSet());

		try (BufferedReader reader = IOUtils.getBufferedReader(zipCodeFile)) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				String nameLK = record.get("landkreis");
				if (nameLK.isEmpty())
					nameLK = record.get("ort");
				if (zipCodesLK.containsKey(nameLK)) {
					zipCodesLK.get(nameLK).add(Integer.parseInt(record.get("plz")));
					zipCodesLK.get("Deutschland").add(Integer.parseInt(record.get("plz")));

				} else {
					zipCodesLK.put(nameLK, new IntOpenHashSet(List.of(Integer.parseInt(record.get("plz")))));
					zipCodesLK.get("Deutschland").add(Integer.parseInt(record.get("plz")));
				}
			}
		}
		return zipCodesLK;
	}

	/**
	 * Finds the zipCodes for any Area. If more than one area contains the input
	 * String an exception is thrown.
	 * 
	 * @param anyArea
	 * @return
	 * @throws IOException
	 */
	public HashMap<String, IntSet> findZipCodesForAnyArea(String anyArea) throws IOException {
		String zipCodeFile = "../shared-svn/projects/episim/data/PLZ/zuordnung_plz_ort_landkreis.csv";
		HashMap<String, IntSet> zipCodes = new HashMap<String, IntSet>();
		List<String> possibleAreas = new ArrayList<String>();

		try (BufferedReader reader = IOUtils.getBufferedReader(zipCodeFile)) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				String nameLK = record.get("landkreis");
				if (nameLK.isEmpty())
					nameLK = record.get("ort");

				if (nameLK.contains(anyArea)) {

					if (!possibleAreas.contains(nameLK))
						possibleAreas.add(nameLK);
					if (zipCodes.containsKey(nameLK)) {
						zipCodes.get(nameLK).add(Integer.parseInt(record.get("plz")));
					} else
						zipCodes.put(nameLK, new IntOpenHashSet(List.of(Integer.parseInt(record.get("plz")))));
				}
			}
		}
		if (possibleAreas.size() > 1)
			if (possibleAreas.contains(anyArea)) {
				IntSet finalZipCodes = zipCodes.get(anyArea);
				zipCodes.clear();
				zipCodes.put(anyArea, finalZipCodes);
			} else
				throw new RuntimeException(
						"For the choosen area " + anyArea + " more the following districts are possible: "
								+ possibleAreas.toString() + " Choose one and start again.");
		return zipCodes;
	}

	/**
	 * Rounds the number 2 places after the comma
	 * 
	 */
	static double round2Decimals(double number) {
		return Math.round(number * 100) * 0.01;
	}

	private enum Types {
		date, area, persons, sharePersonLeavingHome, dailyRangePerPerson
	}

}

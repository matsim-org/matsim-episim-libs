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
import com.google.common.io.Resources;

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
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * @author:rewert This class reads analysis the SENEZON timeline data for every
 *                day. The data is filtered by the zip codes of every area.
 */
@CommandLine.Command(name = "AnalyzeSnzPersonsStat", description = "Aggregate snz timeline of the daily mobility data.")
class AnalyzeSnzDataTimeline implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(AnalyzeSnzDataTimeline.class);

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final DateTimeFormatter FMT_output = DateTimeFormatter.ofPattern("dd.MM.yy");
	private static final DateTimeFormatter FMT_holiday = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final Joiner JOIN = Joiner.on(";");

	private enum AnalyseOptions {
		onlyWeekdays, onlySaturdays, onlySundays, weeklyResultsOfAllDays, onlyWeekends, dailyResults, Mo_Do, Fr_Sa
	};

	private enum AnalyseAreas {
		Germany, Berlin, Munich, Heinsberg, Bonn, Mannheim, Wolfsburg, BerlinDistricts, Test, Berchtesgaden, Hamburg,
		Collogne, Frankfurt, AnyArea, Bundeslaender, Landkreise, UpdateMobilityDashboardData
	}

	private enum OutputData {
		allDataTypes, StartHome, EndHome, StartNonHome, EndNonHome, EndNonHome22_5

	}

	@CommandLine.Parameters(defaultValue = "../shared-svn/projects/episim/data/Bewegungsdaten/")
	private Path inputFolder;

	@CommandLine.Option(names = "--output", defaultValue = "output")
	private Path outputFolder;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalyzeSnzDataTimeline()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		boolean getPercentageResults = false;
		boolean outputShareOutdoor = false;
		AnalyseAreas selectedArea = AnalyseAreas.UpdateMobilityDashboardData;
		AnalyseOptions selectedOptionForAnalyse = AnalyseOptions.onlyWeekends;
		OutputData selectedOutputData = OutputData.EndNonHome;
		String startDateStillUsingBaseDays = ""; // set in this format YYYYMMDD
		String anyArea = "Köln";
		boolean ignoreDates = true; // true for mobilityDashboard

		Set<String> datesToIgnore = Resources
				.readLines(Resources.getResource("mobilityDatesToIgnore.txt"), StandardCharsets.UTF_8).stream()
				.map(String::toString).collect(Collectors.toSet());
		if (ignoreDates == false)
			datesToIgnore.clear();

		analyseData(selectedArea, selectedOptionForAnalyse, selectedOutputData, getPercentageResults,
				outputShareOutdoor, anyArea, startDateStillUsingBaseDays, datesToIgnore);

		log.info("Done!");

		return 0;
	}

	private void analyseData(AnalyseAreas selectedArea, AnalyseOptions selectedOptionForAnalyse,
			OutputData selectedOutputData, boolean getPercentageResults, boolean outputShareOutdoor, String anyArea,
			String startDateStillUsingBaseDays, Set<String> datesToIgnore) throws IOException {
		HashMap<String, IntSet> zipCodes = new HashMap<String, IntSet>();

		switch (selectedArea) {
		case AnyArea:
			zipCodes = findZipCodesForAnyArea(anyArea);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Bundeslaender:
			zipCodes = findZIPCodesForBundeslaender();
			outputFolder = Path.of("output/bundeslaender");
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, outputFolder, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Landkreise:
			zipCodes = findZIPCodesForLandkreise();
			outputFolder = Path.of("output/landkreise");
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, outputFolder, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Berchtesgaden:
			IntSet zipCodesBerchtesgaden = new IntOpenHashSet(List.of(83317, 83364, 83395, 83404, 83410, 83416, 83435,
					83451, 83454, 83457, 83458, 83471, 83483, 83486, 83487));
			zipCodes.put("Berchtesgaden", zipCodesBerchtesgaden);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Berlin:
			zipCodes = findZipCodesForAnyArea("Berlin");
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
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
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Bonn:
			IntSet zipCodesBonn = new IntOpenHashSet();
			for (int i = 53100; i <= 53299; i++)
				zipCodesBonn.add(i);
			zipCodes.put("Bonn", zipCodesBonn);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Germany:
			IntSet zipCodesGER = new IntOpenHashSet();
			for (int i = 0; i <= 99999; i++)
				zipCodesGER.add(i);
			zipCodes.put("Germany", zipCodesGER);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Hamburg:
			IntSet zipCodesHamburg = new IntOpenHashSet();
			for (int i = 22000; i <= 22999; i++)
				zipCodesHamburg.add(i);
			zipCodes.put("Hamburg", zipCodesHamburg);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Heinsberg:
			IntSet zipCodesHeinsberg = new IntOpenHashSet(
					List.of(41812, 52538, 52511, 52525, 41836, 52538, 52531, 41849, 41844));
			zipCodes.put("Heinsberg", zipCodesHeinsberg);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Collogne:
			IntSet zipCodesCollogne = new IntOpenHashSet(List.of(50667, 50668, 50670, 50672, 50674, 50676, 50677, 50678,
					50679, 50733, 50735, 50737, 50739, 50765, 50767, 50769, 50823, 50825, 50827, 50829, 50858, 50859,
					50931, 50933, 50935, 50937, 50939, 50968, 50969, 50996, 50997, 50999, 51061, 51063, 51065, 51067,
					51069, 51103, 51105, 51107, 51109, 51143, 51145, 51147, 51149));
			zipCodes.put("Collogne", zipCodesCollogne);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Frankfurt:
			IntSet zipCodesFrankfurt = new IntOpenHashSet();
			for (int i = 60306; i <= 60599; i++)
				zipCodesFrankfurt.add(i);
			zipCodesFrankfurt.addAll(List.of(65929, 65931, 65933, 65934, 65936));
			zipCodes.put("FrankfurtMain", zipCodesFrankfurt);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
		case Mannheim:
			IntSet zipCodesMannheim = new IntOpenHashSet(List.of(68159, 68161, 68163, 68165, 68167, 68169, 68199, 68219,
					68229, 68239, 68259, 68305, 68307, 68309));
			zipCodes.put("Mannheim", zipCodesMannheim);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Munich:
			IntSet zipCodesMunich = new IntOpenHashSet();
			for (int i = 80331; i <= 81929; i++)
				zipCodesMunich.add(i);
			zipCodes.put("Munich", zipCodesMunich);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Test:
			IntSet zipCodesTest = new IntOpenHashSet(List.of(1067));
			zipCodes.put("Test", zipCodesTest);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Wolfsburg:
			IntSet zipCodesWolfsburg = new IntOpenHashSet(List.of(38440, 38442, 38444, 38446, 38448));
			zipCodes.put("Wolfsburg", zipCodesWolfsburg);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, null, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case UpdateMobilityDashboardData:
			zipCodes = findZIPCodesForLandkreise();
			selectedOutputData = OutputData.EndNonHome22_5;
			outputFolder = Path.of("../public-svn/matsim/scenarios/countries/de/episim/mobilityData/landkreise/");
			selectedOptionForAnalyse = AnalyseOptions.weeklyResultsOfAllDays;
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, outputFolder, startDateStillUsingBaseDays, datesToIgnore);
			selectedOptionForAnalyse = AnalyseOptions.onlyWeekdays;
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, outputFolder, startDateStillUsingBaseDays, datesToIgnore);
			selectedOptionForAnalyse = AnalyseOptions.onlyWeekends;
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					selectedOutputData, outputFolder, startDateStillUsingBaseDays, datesToIgnore);
		default:
			break;

		}
	}

	/**
	 * Analyzes the data and writes the results for certain areas.
	 * 
	 * @param zipCodes
	 * @param getPercentageResults
	 * @param outputShareOutdoor
	 * @param selectedOptionForAnalyse
	 * @param selectedOutputData
	 * @param selectedOutputFolder
	 * @param datesToIgnore
	 * @throws IOException
	 */
	private void analyzeDataForCertainAreas(HashMap<String, IntSet> zipCodes, boolean getPercentageResults,
			boolean outputShareOutdoor, AnalyseOptions selectedOptionForAnalyse, OutputData selectedOutputData,
			Path selectedOutputFolder, String startDateStillUsingBaseDays, Set<String> datesToIgnore)
			throws IOException {

		log.info("Searching for files in the folder: " + inputFolder);
		List<File> filesWithData = findInputFiles(inputFolder.toFile());
		log.info("Amount of found files: " + filesWithData.size());

		Path outputFile = null;
		Path finalPath = null;

		if (selectedOutputFolder != null && selectedOutputFolder.endsWith("mobilityData/landkreise/")) {
			if (selectedOptionForAnalyse.toString().contains("weekly"))
				finalPath = selectedOutputFolder.resolve("LK_nightHoursSum_weekly.csv");
			else if (selectedOptionForAnalyse.toString().contains("Weekdays"))
				finalPath = selectedOutputFolder.resolve("LK_nightHoursSum_weekdays.csv");
			else if (selectedOptionForAnalyse.toString().contains("Weekends"))
				finalPath = selectedOutputFolder.resolve("LK_nightHoursSum_weekends.csv");
		} else {
			if (selectedOutputFolder != null)
				if (selectedOutputFolder.toString().contains("bundeslaender"))
					outputFile = selectedOutputFolder.resolve("BL_" + "Timeline_until.csv");
				else if (selectedOutputFolder.toString().contains("landkreise"))
					outputFile = selectedOutputFolder.resolve("LK_nightHoursSum_new.csv");
				else
					outputFile = selectedOutputFolder.resolve("Timeline_until.csv");
			else if (zipCodes.size() == 1)
				outputFile = outputFolder.resolve(zipCodes.keySet().iterator().next() + "SnzDataTimeline_until.csv");
			else
				outputFile = outputFolder.resolve("Timeline_until.csv");
		}
		HashMap<String, Set<LocalDate>> allHolidays = readBankHolidays();
		HashMap<String, Set<String>> lkAssignemt = createLKAssignmentToBL();

		startDateStillUsingBaseDays = findNextDateToContinueFile(startDateStillUsingBaseDays, filesWithData, finalPath);

		BufferedWriter writer = null;
		if (finalPath == null)
			writer = IOUtils.getBufferedWriter(outputFile.toUri().toURL(), StandardCharsets.UTF_8, true);
		else
			writer = IOUtils.getBufferedWriter(finalPath.toUri().toURL(), StandardCharsets.UTF_8, true);

		Path outputFileShare = null;
		BufferedWriter writerShare = null;
		if (outputShareOutdoor) {
			outputFileShare = Path.of(outputFile.toString().replace("Timeline", "Outdoorshare"));
			writerShare = IOUtils.getBufferedWriter(outputFileShare.toUri().toURL(), StandardCharsets.UTF_8, true);
		}
		try {
			String[] header;
			switch (selectedOutputData) {
			case EndNonHome22_5:
				header = new String[] { "date", "area", "type", "total", "22-5" };
				break;
			default:
				header = new String[] { "date", "area", "type", "total", "<0h", "0-1h", "1-2h", "2-3h", "3-4h", "4-5h",
						"5-6h", "6-7h", "7-8h", "8-9h", "9-10h", "10-11h", "11-12h", "12-13h", "13-14h", "14-15h",
						"15-16h", "16-17h", "17-18h", "18-19h", "19-20h", "20-21h", "21-22h", "22-23h", "23-24h",
						"24-25h", "25-26h", "26-27h", "27-28h", "28-29h", "29-30h", ">30h", "22-5" };
				break;
			}
			if (finalPath == null) {
				JOIN.appendTo(writer, header);
				writer.write("\n");
			}
			if (outputShareOutdoor) {
				JOIN.appendTo(writerShare, header);
				writerShare.write("\n");
			}
			// base level for different types
			Map<String, Map<String, Object2DoubleMap<String>>> baseForAreas = new HashMap<>();
			Object2DoubleMap<String> baseStartHomeActs = new Object2DoubleOpenHashMap<>();
			Object2DoubleMap<String> baseEndHomeActs = new Object2DoubleOpenHashMap<>();
			Object2DoubleMap<String> baseStartNonHomeActs = new Object2DoubleOpenHashMap<>();
			Object2DoubleMap<String> baseEndNonHomeActs = new Object2DoubleOpenHashMap<>();
			Map<String, Object2DoubleMap<String>> base = new HashMap<String, Object2DoubleMap<String>>();
			base.put("startHomeActs", baseStartHomeActs);
			base.put("endHomeActs", baseEndHomeActs);
			base.put("startNonHomeActs", baseStartNonHomeActs);
			base.put("endNonHomeActs", baseEndNonHomeActs);

			Map<String, Object2DoubleMap<String>> sumsHomeStart = new HashMap<>();
			Map<String, Object2DoubleMap<String>> sumsHomeEnd = new HashMap<>();
			Map<String, Object2DoubleMap<String>> sumsNonHomeStart = new HashMap<>();
			Map<String, Object2DoubleMap<String>> sumsNonHomeEnd = new HashMap<>();

			for (String certainArea : zipCodes.keySet()) {
				sumsHomeStart.put(certainArea, new Object2DoubleOpenHashMap<>());
				sumsHomeEnd.put(certainArea, new Object2DoubleOpenHashMap<>());
				sumsNonHomeStart.put(certainArea, new Object2DoubleOpenHashMap<>());
				sumsNonHomeEnd.put(certainArea, new Object2DoubleOpenHashMap<>());
				baseForAreas.put(certainArea, base);
			}
			int countingDays = 1;
			boolean reachedStartDate = false;
			HashMap<String, Integer> anaylzedDaysPerAreaAndPeriod = new HashMap<String, Integer>();
			Map<String, Integer> personsInThisArea = getPersonsInThisZIPCodes(zipCodes, inputFolder.toFile());

			// will contain the last parsed date
			String dateString = "";

			for (File file : filesWithData) {

				dateString = file.getName().split("_")[0];
				LocalDate date = LocalDate.parse(dateString, FMT);
				DayOfWeek day = date.getDayOfWeek();

				if (startDateStillUsingBaseDays.equals("") || dateString.equals(startDateStillUsingBaseDays))
					reachedStartDate = true;

				if (reachedStartDate) {

					switch (selectedOptionForAnalyse) {
					case weeklyResultsOfAllDays:
						if (!datesToIgnore.contains(dateString))
							readDataOfTheDay(zipCodes, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									file, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
						if (day.equals(DayOfWeek.SUNDAY) && !anaylzedDaysPerAreaAndPeriod.values().isEmpty()) {
							writeOutput(getPercentageResults, outputShareOutdoor, selectedOutputData, writer,
									writerShare, anaylzedDaysPerAreaAndPeriod, header, baseForAreas, dateString,
									sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd, personsInThisArea);
							clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									anaylzedDaysPerAreaAndPeriod);
						}
						break;
					case onlyWeekdays:
						if (!day.equals(DayOfWeek.SATURDAY) && !day.equals(DayOfWeek.SUNDAY)
								&& !datesToIgnore.contains(dateString)) {
							List<String> areasWithBankHoliday = new ArrayList<>();
							getAreasWithBankHoliday(areasWithBankHoliday, allHolidays, date);
							readDataOfTheDay(zipCodes, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									file, areasWithBankHoliday, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
						}
						if (day.equals(DayOfWeek.FRIDAY) && !anaylzedDaysPerAreaAndPeriod.values().isEmpty()) {
							writeOutput(getPercentageResults, outputShareOutdoor, selectedOutputData, writer,
									writerShare, anaylzedDaysPerAreaAndPeriod, header, baseForAreas, dateString,
									sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd, personsInThisArea);
							clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									anaylzedDaysPerAreaAndPeriod);
						}
						break;
					case onlySaturdays:
						if (day.equals(DayOfWeek.SATURDAY) && !datesToIgnore.contains(dateString)) {
							readDataOfTheDay(zipCodes, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									file, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
							writeOutput(getPercentageResults, outputShareOutdoor, selectedOutputData, writer,
									writerShare, anaylzedDaysPerAreaAndPeriod, header, baseForAreas, dateString,
									sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd, personsInThisArea);
							clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									anaylzedDaysPerAreaAndPeriod);
						}
						break;
					case onlySundays:
						if (day.equals(DayOfWeek.SUNDAY) && !datesToIgnore.contains(dateString)) {
							readDataOfTheDay(zipCodes, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									file, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
							writeOutput(getPercentageResults, outputShareOutdoor, selectedOutputData, writer,
									writerShare, anaylzedDaysPerAreaAndPeriod, header, baseForAreas, dateString,
									sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd, personsInThisArea);
							clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									anaylzedDaysPerAreaAndPeriod);
						}
						break;
					case onlyWeekends:
						if ((day.equals(DayOfWeek.SATURDAY) || day.equals(DayOfWeek.SUNDAY))
								&& !datesToIgnore.contains(dateString))
							readDataOfTheDay(zipCodes, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									file, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
						if (day.equals(DayOfWeek.SUNDAY) && !anaylzedDaysPerAreaAndPeriod.values().isEmpty()) {
							writeOutput(getPercentageResults, outputShareOutdoor, selectedOutputData, writer,
									writerShare, anaylzedDaysPerAreaAndPeriod, header, baseForAreas, dateString,
									sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd, personsInThisArea);
							clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									anaylzedDaysPerAreaAndPeriod);
						}
						break;
					case dailyResults:
						if (!datesToIgnore.contains(dateString)) {
							readDataOfTheDay(zipCodes, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									file, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
							writeOutput(getPercentageResults, outputShareOutdoor, selectedOutputData, writer,
									writerShare, anaylzedDaysPerAreaAndPeriod, header, baseForAreas, dateString,
									sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd, personsInThisArea);
							clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									anaylzedDaysPerAreaAndPeriod);
						}
						break;
					case Mo_Do:
						if (!day.equals(DayOfWeek.SATURDAY) && !day.equals(DayOfWeek.SUNDAY)
								&& !day.equals(DayOfWeek.FRIDAY) && !datesToIgnore.contains(dateString)) {
							List<String> areasWithBankHoliday = new ArrayList<>();
							getAreasWithBankHoliday(areasWithBankHoliday, allHolidays, date);
							readDataOfTheDay(zipCodes, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									file, areasWithBankHoliday, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
						}
						if (day.equals(DayOfWeek.THURSDAY) && !anaylzedDaysPerAreaAndPeriod.values().isEmpty()) {
							writeOutput(getPercentageResults, outputShareOutdoor, selectedOutputData, writer,
									writerShare, anaylzedDaysPerAreaAndPeriod, header, baseForAreas, dateString,
									sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd, personsInThisArea);
							clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									anaylzedDaysPerAreaAndPeriod);
						}
						break;
					case Fr_Sa:
						if ((day.equals(DayOfWeek.SATURDAY) || day.equals(DayOfWeek.FRIDAY))
								&& !datesToIgnore.contains(dateString)) {

							readDataOfTheDay(zipCodes, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									file, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
						}
						if (day.equals(DayOfWeek.SATURDAY) && !anaylzedDaysPerAreaAndPeriod.values().isEmpty()) {
							writeOutput(getPercentageResults, outputShareOutdoor, selectedOutputData, writer,
									writerShare, anaylzedDaysPerAreaAndPeriod, header, baseForAreas, dateString,
									sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd, personsInThisArea);
							clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
									anaylzedDaysPerAreaAndPeriod);
						}
						break;
					default:
						break;

					}
				}
				if (countingDays % 7 == 0)
					log.info("Finished week " + countingDays / 7 + " of "
							+ (int) Math.floor((double) filesWithData.size() / 7) + " weeks");

				countingDays++;
			}

			writer.close();
			if (outputShareOutdoor)
				writerShare.close();
			if (finalPath == null) {
				if (outputFile.toString().contains("until")) {
					switch (selectedOptionForAnalyse) {
					case weeklyResultsOfAllDays:
						if (!getPercentageResults)
							finalPath = Path.of(
									outputFile.toString().replace("until", "until" + dateString + "_WeeklyNumbers"));
						else
							finalPath = Path
									.of(outputFile.toString().replace("until", "until" + dateString + "_Weekly"));
						break;
					case onlyWeekdays:
						if (!getPercentageResults)
							finalPath = Path.of(
									outputFile.toString().replace("until", "until" + dateString + "_WeekdaysNumbers"));
						else
							finalPath = Path
									.of(outputFile.toString().replace("until", "until" + dateString + "_Weekdays"));
						break;
					case onlySaturdays:
						if (!getPercentageResults)
							finalPath = Path.of(
									outputFile.toString().replace("until", "until" + dateString + "_SaturdaysNumbers"));
						else
							finalPath = Path
									.of(outputFile.toString().replace("until", "until" + dateString + "_Saturdays"));
						break;
					case onlySundays:
						if (!getPercentageResults)
							finalPath = Path.of(
									outputFile.toString().replace("until", "until" + dateString + "_SundaysNumbers"));
						else
							finalPath = Path
									.of(outputFile.toString().replace("until", "until" + dateString + "_Sundays"));
						break;
					case onlyWeekends:
						if (!getPercentageResults)
							finalPath = Path.of(
									outputFile.toString().replace("until", "until" + dateString + "_WeekendsNumbers"));
						else
							finalPath = Path
									.of(outputFile.toString().replace("until", "until" + dateString + "_Weekends"));
						break;
					case dailyResults:
						if (!getPercentageResults)
							finalPath = Path
									.of(outputFile.toString().replace("until", "until" + dateString + "_DailyNumbers"));
						else
							finalPath = Path
									.of(outputFile.toString().replace("until", "until" + dateString + "_Daily"));
						break;
					case Mo_Do:
						if (!getPercentageResults)
							finalPath = Path
									.of(outputFile.toString().replace("until", "until" + dateString + "_Mo-DoNumbers"));
						else
							finalPath = Path
									.of(outputFile.toString().replace("until", "until" + dateString + "_Mo-Do"));
						break;
					case Fr_Sa:
						if (!getPercentageResults)
							finalPath = Path
									.of(outputFile.toString().replace("until", "until" + dateString + "_Fr-SaNumbers"));
						else
							finalPath = Path
									.of(outputFile.toString().replace("until", "until" + dateString + "_Fr-Sa"));
						break;
					default:
						break;

					}
				} else {
					switch (selectedOptionForAnalyse) {
					case weeklyResultsOfAllDays:
						finalPath = Path.of(outputFile.toString().replace("new", "weekly"));
						break;
					case onlyWeekdays:
						finalPath = Path.of(outputFile.toString().replace("new", "weekdays"));
						break;
					case onlySaturdays:
						finalPath = Path.of(outputFile.toString().replace("new", "saturdays"));
						break;
					case onlySundays:
						finalPath = Path.of(outputFile.toString().replace("new", "sundays"));
						break;
					case onlyWeekends:
						finalPath = Path.of(outputFile.toString().replace("new", "weekends"));
						break;
					case dailyResults:
						finalPath = Path.of(outputFile.toString().replace("new", "daily"));
						break;
					case Mo_Do:
						finalPath = Path.of(outputFile.toString().replace("new", "Mo-Do"));
						break;
					case Fr_Sa:
						finalPath = Path.of(outputFile.toString().replace("new", "Fr-Sa"));
						break;
					default:
						break;
					}
				}
				if (outputShareOutdoor)
					Files.move(outputFileShare, Path.of(finalPath.toString().replace("Timeline", "Outdoorshare")),
							StandardCopyOption.REPLACE_EXISTING);
				Files.move(outputFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
			}
			log.info("Write analyze of " + countingDays + " is writen to " + finalPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Reads the data and saves the data in the different sums types
	 * 
	 * @param file
	 * @throws IOException
	 * 
	 */
	private void readDataOfTheDay(HashMap<String, IntSet> zipCodesOfAreas,
			Map<String, Object2DoubleMap<String>> sumsHomeStart, Map<String, Object2DoubleMap<String>> sumsHomeEnd,
			Map<String, Object2DoubleMap<String>> sumsNonHomeStart,
			Map<String, Object2DoubleMap<String>> sumsNonHomeEnd, File file, List<String> areasWithBankHoliday,
			HashMap<String, Integer> anaylzedDaysPerAreaAndPeriod, HashMap<String, Set<String>> lkAssignemt)
			throws IOException {

		if (anaylzedDaysPerAreaAndPeriod.isEmpty())
			for (String nameArea : zipCodesOfAreas.keySet()) {
				anaylzedDaysPerAreaAndPeriod.put(nameArea, 0);
			}

		for (String area : anaylzedDaysPerAreaAndPeriod.keySet())
			if (areasWithBankHoliday == null || !areasWithBankHoliday.contains(getRelatedBundesland(area, lkAssignemt)))
				anaylzedDaysPerAreaAndPeriod.put(area, anaylzedDaysPerAreaAndPeriod.get(area) + 1);
		List<String> hours22_5 = Arrays.asList("<0h", "0-1h", "1-2h", "2-3h", "3-4h", "4-5h", "22-23h", "23-24h",
				"24-25h", "25-26h", "26-27h");
		CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()
				.parse(IOUtils.getBufferedReader(file.toString()));
		for (CSVRecord record : parse) {
			for (String certainArea : zipCodesOfAreas.keySet()) {
				if (areasWithBankHoliday == null
						|| !areasWithBankHoliday.contains(getRelatedBundesland(certainArea, lkAssignemt))) {
					if (!record.get("zipCode").contains("NULL")) {
						int zipCode = Integer.parseInt(record.get("zipCode"));
						if (zipCodesOfAreas.get(certainArea).contains(zipCode)) {
							for (String string : record.getParser().getHeaderNames()) {
								if (!string.contains("date") && !string.contains("type") && !string.contains("area")
										&& !string.contains("zipCode")) {
									if (record.get("type").contains("startHomeActs"))
										sumsHomeStart.get(certainArea).mergeDouble(string,
												Integer.parseInt(record.get(string)), Double::sum);
									if (record.get("type").contains("endHomeActs"))
										sumsHomeEnd.get(certainArea).mergeDouble(string,
												Integer.parseInt(record.get(string)), Double::sum);
									if (record.get("type").contains("startNonHomeActs"))
										sumsNonHomeStart.get(certainArea).mergeDouble(string,
												Integer.parseInt(record.get(string)), Double::sum);
									if (record.get("type").contains("endNonHomeActs"))
										sumsNonHomeEnd.get(certainArea).mergeDouble(string,
												Integer.parseInt(record.get(string)), Double::sum);
									if (record.get("type").contains("endNonHomeActs") && hours22_5.contains(string))
										sumsNonHomeEnd.get(certainArea).mergeDouble("22-5",
												Integer.parseInt(record.get(string)), Double::sum);
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Writes the output of the analyzed time period.
	 * 
	 * @param getPercentageResults
	 * @param outputShareOutdoor
	 * @param selectedOutputData
	 * @param writer
	 * @param writerShare
	 * @param anaylzedDaysPerAreaAndPeriod
	 * @param header
	 * @param baseForAreas
	 * @param dateString
	 * @param sumsHomeStart
	 * @param sumsHomeEnd
	 * @param sumsNonHomeStart
	 * @param sumsNonHomeEnd
	 * @param personsInThisArea
	 * @throws IOException
	 */
	private void writeOutput(boolean getPercentageResults, boolean outputShareOutdoor, OutputData selectedOutputData,
			BufferedWriter writer, BufferedWriter writerShare, HashMap<String, Integer> anaylzedDaysPerAreaAndPeriod,
			String[] header, Map<String, Map<String, Object2DoubleMap<String>>> baseForAreas, String dateString,
			Map<String, Object2DoubleMap<String>> sumsHomeStart, Map<String, Object2DoubleMap<String>> sumsHomeEnd,
			Map<String, Object2DoubleMap<String>> sumsNonHomeStart,
			Map<String, Object2DoubleMap<String>> sumsNonHomeEnd, Map<String, Integer> personsInThisArea)
			throws IOException {

		for (String certainArea : sumsNonHomeStart.keySet()) {
			Object2DoubleMap<String> sumsNonHomeEndArea = sumsNonHomeEnd.get(certainArea);
			Object2DoubleMap<String> sumsNonHomeStartArea = sumsNonHomeStart.get(certainArea);
			Object2DoubleMap<String> sumsHomeStartArea = sumsHomeStart.get(certainArea);
			Object2DoubleMap<String> sumsHomeEndArea = sumsHomeEnd.get(certainArea);

			for (String hour : sumsNonHomeEndArea.keySet()) {
				sumsNonHomeEndArea.put(hour, Math
						.round((sumsNonHomeEndArea.getDouble(hour) / anaylzedDaysPerAreaAndPeriod.get(certainArea))));
				sumsNonHomeStartArea.put(hour, Math
						.round((sumsNonHomeStartArea.getDouble(hour) / anaylzedDaysPerAreaAndPeriod.get(certainArea))));
				sumsHomeStartArea.put(hour, Math
						.round((sumsHomeStartArea.getDouble(hour) / anaylzedDaysPerAreaAndPeriod.get(certainArea))));
				sumsHomeEndArea.put(hour,
						Math.round((sumsHomeEndArea.getDouble(hour) / anaylzedDaysPerAreaAndPeriod.get(certainArea))));
			}
			if (baseForAreas.get(certainArea).get("startHomeActs").isEmpty()) {
				baseForAreas.get(certainArea).get("startHomeActs").putAll(sumsHomeStart.get(certainArea));
				baseForAreas.get(certainArea).get("endHomeActs").putAll(sumsHomeEnd.get(certainArea));
				baseForAreas.get(certainArea).get("startNonHomeActs").putAll(sumsNonHomeStart.get(certainArea));
				baseForAreas.get(certainArea).get("endNonHomeActs").putAll(sumsNonHomeEnd.get(certainArea));
			}
		}
		for (String certainArea : sumsNonHomeStart.keySet()) {
			if (!sumsNonHomeEnd.get(certainArea).isEmpty()) {
				List<String> rowEndHome = new ArrayList<>();
				List<String> rowStartHome = new ArrayList<>();
				List<String> rowEndNonHome = new ArrayList<>();
				List<String> rowStartNonHome = new ArrayList<>();
				List<String> rowShareOutdoor = new ArrayList<>();
				rowEndHome.add(LocalDate.parse(dateString, FMT).format(FMT_output));
				rowEndHome.add(certainArea);
				rowEndHome.add("endHomeActs");
				rowStartHome.add(LocalDate.parse(dateString, FMT).format(FMT_output));
				rowStartHome.add(certainArea);
				rowStartHome.add("startHomeActs");
				rowEndNonHome.add(LocalDate.parse(dateString, FMT).format(FMT_output));
				rowEndNonHome.add(certainArea);
				rowEndNonHome.add("endNonHomeActs");
				rowStartNonHome.add(LocalDate.parse(dateString, FMT).format(FMT_output));
				rowStartNonHome.add(certainArea);
				rowStartNonHome.add("startNonHomeActs");
				rowShareOutdoor.add(LocalDate.parse(dateString, FMT).format(FMT_output));
				rowShareOutdoor.add(certainArea);
				rowShareOutdoor.add("shareOutdoor");

				double shareBefore = 0.;
				double baseNumberHomeStart = 0;
				boolean started = false;

				for (String string : header) {
					if (!string.contains("date") && !string.contains("type") && !string.contains("area")) {
						if (getPercentageResults) {
							rowEndHome.add(String.valueOf(
									round2DecimalsAndConvertToProcent((sumsHomeEnd.get(certainArea).getDouble(string)
											/ baseForAreas.get(certainArea).get("endHomeActs").getDouble(string)
											- 1))));
							rowStartHome.add(String.valueOf(
									round2DecimalsAndConvertToProcent((sumsHomeStart.get(certainArea).getDouble(string)
											/ baseForAreas.get(certainArea).get("startHomeActs").getDouble(string)
											- 1))));
							rowEndNonHome.add(String.valueOf(
									round2DecimalsAndConvertToProcent((sumsNonHomeEnd.get(certainArea).getDouble(string)
											/ baseForAreas.get(certainArea).get("endNonHomeActs").getDouble(string)
											- 1))));
							rowStartNonHome.add(String.valueOf(round2DecimalsAndConvertToProcent(
									(sumsNonHomeStart.get(certainArea).getDouble(string)
											/ baseForAreas.get(certainArea).get("startNonHomeActs").getDouble(string)
											- 1))));
						} else {
							rowEndHome.add(String
									.valueOf(round2Decimals((double) sumsHomeEnd.get(certainArea).getDouble(string)
											/ personsInThisArea.get(certainArea) * 1000)));
							rowStartHome.add(String
									.valueOf(round2Decimals((double) sumsHomeStart.get(certainArea).getDouble(string)
											/ personsInThisArea.get(certainArea) * 1000)));
							rowEndNonHome.add(String
									.valueOf(round2Decimals((double) sumsNonHomeEnd.get(certainArea).getDouble(string)
											/ personsInThisArea.get(certainArea) * 1000)));
							rowStartNonHome.add(String
									.valueOf(round2Decimals((double) sumsNonHomeStart.get(certainArea).getDouble(string)
											/ personsInThisArea.get(certainArea) * 1000)));
						}
						if (outputShareOutdoor) {
							if (string.contains("total")) {
								rowShareOutdoor.add("0");
							} else {
								if (sumsHomeStart.get(certainArea).getDouble(string) != 0. && !started) {
									baseNumberHomeStart = sumsHomeStart.get(certainArea).getDouble(string);
									shareBefore = sumsHomeEnd.get(certainArea).getDouble(string) / baseNumberHomeStart;
									rowShareOutdoor.add(String.valueOf(round2DecimalsAndConvertToProcent(shareBefore)));
									started = true;
								} else if (sumsHomeEnd.get(certainArea).getDouble(string) != 0. && started) {
									shareBefore = (shareBefore * baseNumberHomeStart
											- sumsHomeStart.get(certainArea).getDouble(string)
											+ sumsHomeEnd.get(certainArea).getDouble(string)) / baseNumberHomeStart;
									rowShareOutdoor.add(String.valueOf(round2DecimalsAndConvertToProcent(shareBefore)));
								} else
									rowShareOutdoor.add("0");
							}
						}
					}
				}
				switch (selectedOutputData) {
				case EndHome:
					JOIN.appendTo(writer, rowEndHome);
					writer.write("\n");
					break;
				case EndNonHome:
					JOIN.appendTo(writer, rowEndNonHome);
					writer.write("\n");
					break;
				case EndNonHome22_5:
					JOIN.appendTo(writer, rowEndNonHome);
					writer.write("\n");
					break;
				case StartHome:
					JOIN.appendTo(writer, rowStartHome);
					writer.write("\n");
					break;
				case StartNonHome:
					JOIN.appendTo(writer, rowStartNonHome);
					writer.write("\n");
					break;
				case allDataTypes:
					JOIN.appendTo(writer, rowStartHome);
					writer.write("\n");
					JOIN.appendTo(writer, rowEndHome);
					writer.write("\n");
					JOIN.appendTo(writer, rowStartNonHome);
					writer.write("\n");
					JOIN.appendTo(writer, rowEndNonHome);
					writer.write("\n");
					break;
				default:
					break;

				}

				if (outputShareOutdoor) {
					JOIN.appendTo(writerShare, rowShareOutdoor);
					writerShare.write("\n");
				}
			}
		}

	}

	/**
	 * Clears all sums to start a next period for the analysis
	 * 
	 * @param sumsHomeStart
	 * @param sumsHomeEnd
	 * @param sumsNonHomeStart
	 * @param sumsNonHomeEnd
	 * @param anaylzedDaysPerAreaAndPeriod
	 */
	private void clearSums(Map<String, Object2DoubleMap<String>> sumsHomeStart,
			Map<String, Object2DoubleMap<String>> sumsHomeEnd, Map<String, Object2DoubleMap<String>> sumsNonHomeStart,
			Map<String, Object2DoubleMap<String>> sumsNonHomeEnd,
			HashMap<String, Integer> anaylzedDaysPerAreaAndPeriod) {

		for (String area : sumsHomeStart.keySet()) {
			sumsHomeStart.get(area).clear();
			sumsHomeEnd.get(area).clear();
			sumsNonHomeStart.get(area).clear();
			sumsNonHomeEnd.get(area).clear();
		}
		anaylzedDaysPerAreaAndPeriod.clear();
	}

	/**
	 * This method searches all files with an certain name in a given folder.
	 */
	static List<File> findInputFiles(File inputFolder) {
		List<File> fileData = new ArrayList<>();

		for (File folder : Objects.requireNonNull(inputFolder.listFiles())) {
			if (folder.isDirectory()) {
				for (File file : Objects.requireNonNull(folder.listFiles())) {
					if (file.getName().contains("tagesgang"))
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
	 * Finds the date after the last day in the given output file.
	 * 
	 * @param startDateStillUsingBaseDays
	 * @param filesWithData
	 * @param finalPath
	 * @return
	 * @throws IOException
	 */
	private String findNextDateToContinueFile(String startDateStillUsingBaseDays, List<File> filesWithData,
			Path finalPath) throws IOException {
		if (finalPath != null) {
			List<String> existingData = Files.readAllLines(finalPath);
			startDateStillUsingBaseDays = existingData.get(existingData.size() - 1).split(";")[0];
			startDateStillUsingBaseDays = LocalDate.parse(startDateStillUsingBaseDays, FMT_output).format(FMT);
		}
		boolean nextDateIsStartDate = false;
		for (File file : filesWithData) {
			String test = file.getName().split("_")[0];
			if (nextDateIsStartDate) {
				startDateStillUsingBaseDays = test;
				break;
			}
			if (startDateStillUsingBaseDays.equals(test))
				nextDateIsStartDate = true;
		}
		return startDateStillUsingBaseDays;
	}

	/**
	 * Searches the number of persons in all the different areas
	 * 
	 * @param zipCodesForAreas
	 * @param inputPulder
	 * @return
	 */
	static Map<String, Integer> getPersonsInThisZIPCodes(HashMap<String, IntSet> zipCodesForAreas, File inputPulder) {
		File fileWithPersonData = findPersonStatInputFile(inputPulder);
		Map<String, Integer> personsPerArea = new HashMap<>();
		for (String area : zipCodesForAreas.keySet()) {
			personsPerArea.put(area, 0);
		}
		CSVParser parse;
		try {
			parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()
					.parse(IOUtils.getBufferedReader(fileWithPersonData.toString()));
			for (CSVRecord record : parse) {
				for (Entry<String, IntSet> certainArea : zipCodesForAreas.entrySet()) {
					if (!record.get("zipCode").contains("NULL")) {
						String nameArea = certainArea.getKey();
						int readZipCode = Integer.parseInt(record.get("zipCode"));
						if (certainArea.getValue().contains(readZipCode))
							personsPerArea.put(nameArea,
									personsPerArea.get(nameArea) + Integer.parseInt(record.get("nPersons")));
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return personsPerArea;
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
	 * This method searches a files with personStat.
	 */
	static File findPersonStatInputFile(File inputFolder) {
		File personStatFile = null;
		for (File folder : Objects.requireNonNull(inputFolder.listFiles())) {
			if (folder.isDirectory()) {
				for (File file : Objects.requireNonNull(folder.listFiles())) {
					String string = file.getName().split("_")[0];
					if (file.getName().contains("_personStats.csv.gz") && string.contains("20210531")) {
						personStatFile = file;
						break;
					}
				}
			}
			if (personStatFile != null)
				break;
		}
		return personStatFile;
	}

	/**
	 * Rounds the number 2 places after the comma
	 * 
	 */
	static double round2DecimalsAndConvertToProcent(double number) {
		return Math.round(number * 10000) * 0.01;
	}

	/**
	 * Rounds the number 2 places after the comma
	 * 
	 */
	static double round2Decimals(double number) {
		return Math.round(number * 100) * 0.01;
	}

}
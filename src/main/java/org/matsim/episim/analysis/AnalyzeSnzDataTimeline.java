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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * @author:rewert This class reads analysis the SENEZON timeline data for every
 *                day. The data is filtered by the zip codes of every area.
 */
@CommandLine.Command(name = "AnalyzeSnzPersonsStat", description = "Aggregate snz timeline of the daily mobility data.")
class AnalyzeSnzDataTimeline implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(AnalyzeSnzDataTimeline.class);

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final DateTimeFormatter FMT2 = DateTimeFormatter.ofPattern("dd.MM.yy");
	private static final Joiner JOIN = Joiner.on("\t");

	private enum AnalyseOptions {
		onlyWeekdays, onlySaturdays, onlySundays, weeklyResultsOfAllDays, onlyWeekends, dailyResults
	};

	private enum AnalyseAreas {
		Germany, Berlin, Munich, Heinsberg, Bonn, Mannheim, Wolfsburg, BerlinDistricts, Test, Berchtesgaden, Hamburg,
		Collogne, Frankfurt, AnyArea, Bundeslaender, Landkreise
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
		AnalyseOptions selectedOptionForAnalyse = AnalyseOptions.weeklyResultsOfAllDays;
		AnalyseAreas selectedArea = AnalyseAreas.Berlin;
		String anyArea = "Berlin";

		analyseData(selectedArea, selectedOptionForAnalyse, getPercentageResults, outputShareOutdoor, anyArea);

		log.info("Done!");

		return 0;
	}

	private void analyseData(AnalyseAreas selectedArea, AnalyseOptions selectedOptionForAnalyse,
			boolean getPercentageResults, boolean outputShareOutdoor, String anyArea) throws IOException {
		HashMap<String, IntSet> zipCodes = new HashMap<String, IntSet>();

		switch (selectedArea) {
		case AnyArea:
			zipCodes = findZipCodesForAnyArea(anyArea);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
			break;
		case Bundeslaender:
			zipCodes = findZIPCodesForBundeslaender();
			outputFolder = Path.of("../public-svn/matsim/scenarios/countries/de/episim/mobilityData/bundeslaender/");
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					outputFolder);
			break;
		case Landkreise:
			zipCodes = findZIPCodesForLandkreise();
			outputFolder = Path.of("../public-svn/matsim/scenarios/countries/de/episim/mobilityData/landkreise/");
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					outputFolder);
			break;
		case Berchtesgaden:
			IntSet zipCodesBerchtesgaden = new IntOpenHashSet(List.of(83317, 83364, 83395, 83404, 83410, 83416, 83435,
					83451, 83454, 83457, 83458, 83471, 83483, 83486, 83487));
			zipCodes.put("Berchtesgaden", zipCodesBerchtesgaden);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
			break;
		case Berlin:
			IntSet zipCodesBerlin = new IntOpenHashSet();
			for (int i = 10115; i <= 14199; i++)
				zipCodesBerlin.add(i);
			zipCodes.put("Berlin", zipCodesBerlin);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
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
					null);
			break;
		case Bonn:
			IntSet zipCodesBonn = new IntOpenHashSet();
			for (int i = 53100; i <= 53299; i++)
				zipCodesBonn.add(i);
			zipCodes.put("Bonn", zipCodesBonn);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
			break;
		case Germany:
			IntSet zipCodesGER = new IntOpenHashSet();
			for (int i = 0; i <= 99999; i++)
				zipCodesGER.add(i);
			zipCodes.put("Germany", zipCodesGER);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
			break;
		case Hamburg:
			IntSet zipCodesHamburg = new IntOpenHashSet();
			for (int i = 22000; i <= 22999; i++)
				zipCodesHamburg.add(i);
			zipCodes.put("Hamburg", zipCodesHamburg);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
			break;
		case Heinsberg:
			IntSet zipCodesHeinsberg = new IntOpenHashSet(
					List.of(41812, 52538, 52511, 52525, 41836, 52538, 52531, 41849, 41844));
			zipCodes.put("Heinsberg", zipCodesHeinsberg);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
			break;
		case Collogne:
			IntSet zipCodesCollogne = new IntOpenHashSet(List.of(50667, 50668, 50670, 50672, 50674, 50676, 50677, 50678,
					50679, 50733, 50735, 50737, 50739, 50765, 50767, 50769, 50823, 50825, 50827, 50829, 50858, 50859,
					50931, 50933, 50935, 50937, 50939, 50968, 50969, 50996, 50997, 50999, 51061, 51063, 51065, 51067,
					51069, 51103, 51105, 51107, 51109, 51143, 51145, 51147, 51149));
			zipCodes.put("Collogne", zipCodesCollogne);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
			break;
		case Frankfurt:
			IntSet zipCodesFrankfurt = new IntOpenHashSet();
			for (int i = 60306; i <= 60599; i++)
				zipCodesFrankfurt.add(i);
			zipCodesFrankfurt.addAll(List.of(65929, 65931, 65933, 65934, 65936));
			zipCodes.put("FrankfurtMain", zipCodesFrankfurt);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
		case Mannheim:
			IntSet zipCodesMannheim = new IntOpenHashSet(List.of(68159, 68161, 68163, 68165, 68167, 68169, 68199, 68219,
					68229, 68239, 68259, 68305, 68307, 68309));
			zipCodes.put("Mannheim", zipCodesMannheim);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
			break;
		case Munich:
			IntSet zipCodesMunich = new IntOpenHashSet();
			for (int i = 80331; i <= 81929; i++)
				zipCodesMunich.add(i);
			zipCodes.put("Munich", zipCodesMunich);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
			break;
		case Test:
			IntSet zipCodesTest = new IntOpenHashSet(List.of(1067));
			zipCodes.put("Test", zipCodesTest);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
			break;
		case Wolfsburg:
			IntSet zipCodesWolfsburg = new IntOpenHashSet(List.of(38440, 38442, 38444, 38446, 38448));
			zipCodes.put("Wolfsburg", zipCodesWolfsburg);
			analyzeDataForCertainAreas(zipCodes, getPercentageResults, outputShareOutdoor, selectedOptionForAnalyse,
					null);
			break;
		default:
			break;

		}
	}

	/** Analyzes the data and writes the results for certain areas.
	 * @param zipCodes
	 * @param getPercentageResults
	 * @param outputShareOutdoor
	 * @param selectedOptionForAnalyse
	 * @param selectedOutputFolder
	 * @throws IOException
	 */
	private void analyzeDataForCertainAreas(HashMap<String, IntSet> zipCodes, boolean getPercentageResults,
			boolean outputShareOutdoor, AnalyseOptions selectedOptionForAnalyse, Path selectedOutputFolder)
			throws IOException {

		log.info("Searching for files in the folder: " + inputFolder);
		List<File> filesWithData = findInputFiles(inputFolder.toFile());
		log.info("Amount of found files: " + filesWithData.size());

		Path outputFile = null;
		if (selectedOutputFolder != null)
			if (selectedOutputFolder.toString().contains("bundeslaender"))
				outputFile = selectedOutputFolder.resolve("BL_" + "Timeline_until.csv");
			else if (selectedOutputFolder.toString().contains("landkreise"))
				outputFile = selectedOutputFolder.resolve("LK_" + "Timeline_until.csv");
			else
				outputFile = selectedOutputFolder.resolve("Timeline_until.csv");
		else if (zipCodes.size() == 1)
			outputFile = outputFolder.resolve(zipCodes.keySet().iterator().next() + "SnzDataTimeline_until.csv");
		else
			outputFile = selectedOutputFolder.resolve("Timeline_until.csv");

		BufferedWriter writer = IOUtils.getBufferedWriter(outputFile.toString());
		Path outputFileShare = null;
		BufferedWriter writerShare = null;
		if (outputShareOutdoor) {
			outputFileShare = Path.of(outputFile.toString().replace("Timeline", "Outdoorshare"));
			writerShare = IOUtils.getBufferedWriter(outputFileShare.toString());
		}
		try {
			String[] header = new String[] { "date", "area", "type", "total", "<0h", "0-1h", "1-2h", "2-3h", "3-4h",
					"4-5h", "5-6h", "6-7h", "7-8h", "8-9h", "9-10h", "10-11h", "11-12h", "12-13h", "13-14h", "14-15h",
					"15-16h", "16-17h", "17-18h", "18-19h", "19-20h", "20-21h", "21-22h", "22-23h", "23-24h", "24-25h",
					"25-26h", "26-27h", "27-28h", "28-29h", "29-30h", ">30h" };

			JOIN.appendTo(writer, header);
			writer.write("\n");
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
			int countingWeek = 1;

			// will contain the last parsed date
			String dateString = "";

			for (File file : filesWithData) {

				if (countingWeek == 8) {

					countingWeek = 1;
				}
				dateString = file.getName().split("_")[0];
				LocalDate date = LocalDate.parse(dateString, FMT);
				DayOfWeek day = date.getDayOfWeek();

				switch (selectedOptionForAnalyse) {
				case weeklyResultsOfAllDays:
					readDataOfTheDay(zipCodes, header, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
							file);
					if (day.equals(DayOfWeek.SUNDAY)) {
						writeOutput(getPercentageResults, outputShareOutdoor, writer, writerShare, 7, header,
								baseForAreas, dateString, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
						clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
					}
					break;
				case onlyWeekdays:
					if (!day.equals(DayOfWeek.SATURDAY) && !day.equals(DayOfWeek.SUNDAY))
						readDataOfTheDay(zipCodes, header, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
								file);
					if (day.equals(DayOfWeek.FRIDAY)) {
						writeOutput(getPercentageResults, outputShareOutdoor, writer, writerShare, 5, header,
								baseForAreas, dateString, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
						clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
					}
					break;
				case onlySaturdays:
					if (day.equals(DayOfWeek.SATURDAY)) {
						readDataOfTheDay(zipCodes, header, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
								file);
						writeOutput(getPercentageResults, outputShareOutdoor, writer, writerShare, 1, header,
								baseForAreas, dateString, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
						clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
					}
					break;
				case onlySundays:
					if (day.equals(DayOfWeek.SUNDAY)) {
						readDataOfTheDay(zipCodes, header, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
								file);
						writeOutput(getPercentageResults, outputShareOutdoor, writer, writerShare, 1, header,
								baseForAreas, dateString, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
						clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
					}
					break;
				case onlyWeekends:
					if (day.equals(DayOfWeek.SATURDAY) || day.equals(DayOfWeek.SUNDAY))
						readDataOfTheDay(zipCodes, header, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
								file);
					if (day.equals(DayOfWeek.SUNDAY)) {
						writeOutput(getPercentageResults, outputShareOutdoor, writer, writerShare, 2, header,
								baseForAreas, dateString, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
						clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
					}
					break;
				case dailyResults:
					readDataOfTheDay(zipCodes, header, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
							file);
					writeOutput(getPercentageResults, outputShareOutdoor, writer, writerShare, 1, header, baseForAreas,
							dateString, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
					clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
					break;
				default:
					break;

				}

				if (countingDays == 1 || countingDays % 5 == 0)
					log.info("Finished day " + countingDays);

				countingDays++;
				countingWeek++;
			}

			writer.close();
			if (outputShareOutdoor)
				writerShare.close();

			Path finalPath = null;
			switch (selectedOptionForAnalyse) {
			case weeklyResultsOfAllDays:
				if (!getPercentageResults)
					finalPath = Path
							.of(outputFile.toString().replace("until", "until" + dateString + "_WeeklyNumbers"));
				else
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_Weekly"));
				break;
			case onlyWeekdays:
				if (!getPercentageResults)
					finalPath = Path
							.of(outputFile.toString().replace("until", "until" + dateString + "_WeekdaysNumbers"));
				else
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_Weekdays"));
				break;
			case onlySaturdays:
				if (!getPercentageResults)
					finalPath = Path
							.of(outputFile.toString().replace("until", "until" + dateString + "_SaturdaysNumbers"));
				else
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_Saturdays"));
				break;
			case onlySundays:
				if (!getPercentageResults)
					finalPath = Path
							.of(outputFile.toString().replace("until", "until" + dateString + "_SundaysNumbers"));
				else
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_Sundays"));
				break;
			case onlyWeekends:
				if (!getPercentageResults)
					finalPath = Path
							.of(outputFile.toString().replace("until", "until" + dateString + "_WeekendsNumbers"));
				else
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_Weekends"));
				break;
			case dailyResults:
				if (!getPercentageResults)
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_DailyNumbers"));
				else
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_Daily"));
				break;
			default:
				break;

			}
			if (outputShareOutdoor)
				Files.move(outputFileShare, Path.of(finalPath.toString().replace("Timeline", "Outdoorshare")),
						StandardCopyOption.REPLACE_EXISTING);
			Files.move(outputFile, finalPath, StandardCopyOption.REPLACE_EXISTING);

			log.info("Write analyze of " + countingDays + " is writen to " + finalPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Clears all sums to start a next period for the analysis
	 * 
	 * @param sumsHomeStart
	 * @param sumsHomeEnd
	 * @param sumsNonHomeStart
	 * @param sumsNonHomeEnd
	 */
	private void clearSums(Map<String, Object2DoubleMap<String>> sumsHomeStart,
			Map<String, Object2DoubleMap<String>> sumsHomeEnd, Map<String, Object2DoubleMap<String>> sumsNonHomeStart,
			Map<String, Object2DoubleMap<String>> sumsNonHomeEnd) {

		for (String area : sumsHomeStart.keySet()) {
			sumsHomeStart.get(area).clear();
			sumsHomeEnd.get(area).clear();
			sumsNonHomeStart.get(area).clear();
			sumsNonHomeEnd.get(area).clear();
		}
	}

	private void writeOutput(boolean getPercentageResults, boolean outputShareOutdoor, BufferedWriter writer,
			BufferedWriter writerShare, int analyzedDays, String[] header,
			Map<String, Map<String, Object2DoubleMap<String>>> baseForAreas, String dateString,
			Map<String, Object2DoubleMap<String>> sumsHomeStart, Map<String, Object2DoubleMap<String>> sumsHomeEnd,
			Map<String, Object2DoubleMap<String>> sumsNonHomeStart,
			Map<String, Object2DoubleMap<String>> sumsNonHomeEnd) throws IOException {

		for (String certainArea : sumsNonHomeStart.keySet())
			if (baseForAreas.get(certainArea).get("startHomeActs").isEmpty()) {
				baseForAreas.get(certainArea).get("startHomeActs").putAll(sumsHomeStart.get(certainArea));
				baseForAreas.get(certainArea).get("endHomeActs").putAll(sumsHomeEnd.get(certainArea));
				baseForAreas.get(certainArea).get("startNonHomeActs").putAll(sumsNonHomeStart.get(certainArea));
				baseForAreas.get(certainArea).get("endNonHomeActs").putAll(sumsNonHomeEnd.get(certainArea));
			}
		for (String certainArea : sumsNonHomeStart.keySet()) {
			if (!sumsHomeStart.get(certainArea).isEmpty()) {
				List<String> rowEndHome = new ArrayList<>();
				List<String> rowStartHome = new ArrayList<>();
				List<String> rowEndNonHome = new ArrayList<>();
				List<String> rowStartNonHome = new ArrayList<>();
				List<String> rowShareOutdoor = new ArrayList<>();
				rowEndHome.add(LocalDate.parse(dateString, FMT).format(FMT2));
				rowEndHome.add(certainArea);
				rowEndHome.add("endHomeActs");
				rowStartHome.add(LocalDate.parse(dateString, FMT).format(FMT2));
				rowStartHome.add(certainArea);
				rowStartHome.add("startHomeActs");
				rowEndNonHome.add(LocalDate.parse(dateString, FMT).format(FMT2));
				rowEndNonHome.add(certainArea);
				rowEndNonHome.add("endNonHomeActs");
				rowStartNonHome.add(LocalDate.parse(dateString, FMT).format(FMT2));
				rowStartNonHome.add(certainArea);
				rowStartNonHome.add("startNonHomeActs");
				rowShareOutdoor.add(LocalDate.parse(dateString, FMT).format(FMT2));
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
							rowEndHome.add(String.valueOf((int) sumsHomeEnd.get(certainArea).getDouble(string)));
							rowStartHome.add(String.valueOf((int) sumsHomeStart.get(certainArea).getDouble(string)));
							rowEndNonHome.add(String.valueOf((int) sumsNonHomeEnd.get(certainArea).getDouble(string)));
							rowStartNonHome
									.add(String.valueOf((int) sumsNonHomeStart.get(certainArea).getDouble(string)));
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
				JOIN.appendTo(writer, rowStartHome);
				writer.write("\n");
				JOIN.appendTo(writer, rowEndHome);
				writer.write("\n");
				JOIN.appendTo(writer, rowStartNonHome);
				writer.write("\n");
				JOIN.appendTo(writer, rowEndNonHome);
				writer.write("\n");
				if (outputShareOutdoor) {
					JOIN.appendTo(writerShare, rowShareOutdoor);
					writerShare.write("\n");
				}
			}
		}

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
	 * Reads the data and saves the data in the different sums types
	 * 
	 * @param file
	 * @throws IOException
	 * 
	 */
	private void readDataOfTheDay(HashMap<String, IntSet> zipCodesOfAreas, String[] header,
			Map<String, Object2DoubleMap<String>> sumsHomeStart, Map<String, Object2DoubleMap<String>> sumsHomeEnd,
			Map<String, Object2DoubleMap<String>> sumsNonHomeStart,
			Map<String, Object2DoubleMap<String>> sumsNonHomeEnd, File file) throws IOException {

		CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()
				.parse(IOUtils.getBufferedReader(file.toString()));
		for (CSVRecord record : parse) {
			for (String certainArea : zipCodesOfAreas.keySet()) {
				if (!record.get("zipCode").contains("NULL")) {
					int zipCode = Integer.parseInt(record.get("zipCode"));
					if (zipCodesOfAreas.get(certainArea).contains(zipCode)) {
						for (String string : header) {
							if (!string.contains("date") && !string.contains("type") && !string.contains("area")) {
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
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Rounds the number 2 places after the comma
	 * 
	 */
	static double round2DecimalsAndConvertToProcent(double number) {
		return Math.round(number * 10000) * 0.01;
	}
}
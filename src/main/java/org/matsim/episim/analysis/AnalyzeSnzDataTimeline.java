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
		onlyWeekdays, onlySaturdays, onlySundays, weeklyResultsOfAllDays, onlyWeekends
	};

	@CommandLine.Parameters(defaultValue = "../shared-svn/projects/episim/data/Bewegungsdaten/")
	private Path inputFolder;

	@CommandLine.Option(names = "--output", defaultValue = "output")
	private Path outputFolder;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalyzeSnzDataTimeline()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		log.info("Searching for files in the folder: " + inputFolder);
		List<File> filesWithData = findInputFiles(inputFolder.toFile());
		log.info("Amount of found files: " + filesWithData.size());

		boolean getPercentageResults = false;
		AnalyseOptions selectedOptionForAnalyse = AnalyseOptions.weeklyResultsOfAllDays;
//		analyzeDataForCertainArea("Test", filesWithData, getPercentageResults, selectedOptionForAnalyse);
//		analyzeDataForCertainArea("Germany", filesWithData, getPercentageResults, selectedOptionForAnalyse);
//		analyzeDataForCertainArea("Berlin", filesWithData, getPercentageResults, selectedOptionForAnalyse);
//		analyzeDataForCertainArea("Munich", filesWithData, getPercentageResults, selectedOptionForAnalyse);
//		analyzeDataForCertainArea("Heinsberg", filesWithData, getPercentageResults, selectedOptionForAnalyse);
//		analyzeDataForCertainArea("Bonn", filesWithData, getPercentageResults, selectedOptionForAnalyse);
//		analyzeDataForCertainArea("Mannheim", filesWithData, getPercentageResults, selectedOptionForAnalyse);
//		analyzeDataForCertainArea("Wolfsburg", filesWithData, getPercentageResults, selectedOptionForAnalyse);
		List<String> districtsBerlin = Arrays.asList("Mitte", "Friedrichshain_Kreuzberg", "Pankow",
				"Charlottenburg_Wilmersdorf", "Spandau", "Steglitz_Zehlendorf", "Tempelhof_Schoeneberg", "Neukoelln",
				"Treptow_Koepenick", "Marzahn_Hellersdorf", "Lichtenberg", "Reinickendorf");
		for (String district : districtsBerlin)
			analyzeDataForCertainArea(district, filesWithData, getPercentageResults, selectedOptionForAnalyse);

		log.info("Done!");

		return 0;
	}

	private IntSet createAreasWithZIPCodes(String area) {
		IntSet zipCodes = null;
		// zip codes for testing
		if (area.equals("Test")) {
			zipCodes = new IntOpenHashSet(List.of(1067));
			return zipCodes;
		}
		// zip codes for Germany
		if (area.equals("Germany")) {
			zipCodes = new IntOpenHashSet();
			for (int i = 0; i <= 99999; i++)
				zipCodes.add(i);
			return zipCodes;
		}
		// zip codes for Berlin
		if (area.equals("Berlin")) {
			zipCodes = new IntOpenHashSet();
			for (int i = 10115; i <= 14199; i++)
				zipCodes.add(i);
			return zipCodes;
		}
		// zip codes for Munich
		if (area.equals("Munich")) {
			zipCodes = new IntOpenHashSet();
			for (int i = 80331; i <= 81929; i++)
				zipCodes.add(i);
			return zipCodes;
		}
		// zip codes for Hamburg
		if (area.equals("Hamburg")) {
			zipCodes = new IntOpenHashSet();
			for (int i = 22000; i <= 22999; i++)
				zipCodes.add(i);
			return zipCodes;
		}
		// zip codes for Bonn
		if (area.equals("Bonn")) {
			zipCodes = new IntOpenHashSet();
			for (int i = 53100; i <= 53299; i++)
				zipCodes.add(i);
			return zipCodes;
		}
		// zip codes for the district "Kreis Heinsberg"
		if (area.equals("Heinsberg")) {
			zipCodes = new IntOpenHashSet(List.of(41812, 52538, 52511, 52525, 41836, 52538, 52531, 41849, 41844));
			return zipCodes;
		}
		// zip codes for district "Berchtesgadener Land"
		if (area.equals("Berchtesgaden")) {
			zipCodes = new IntOpenHashSet(List.of(83317, 83364, 83395, 83404, 83410, 83416, 83435, 83451, 83454, 83457,
					83458, 83471, 83483, 83486, 83487));
			return zipCodes;
		}
		// zip codes for the district "Mannheim"
		if (area.equals("Mannheim")) {
			zipCodes = new IntOpenHashSet(List.of(68159, 68161, 68163, 68165, 68167, 68169, 68199, 68219, 68229, 68239,
					68259, 68305, 68307, 68309));
			return zipCodes;
		}
		// zip codes for the district "Wolfsburg"
		if (area.equals("Wolfsburg")) {
			zipCodes = new IntOpenHashSet(List.of(38440, 38442, 38444, 38446, 38448));
			return zipCodes;
		}

		// zip codes for districts in Berlin
		if (area.equals("Mitte")) {
			zipCodes = new IntOpenHashSet(List.of(10115, 10559, 13355, 10117, 10623, 13357, 10119, 10785, 13359, 10787,
					10557, 13353, 10555, 13351, 13349, 10551, 13347));
			return zipCodes;
		}
		if (area.equals("Friedrichshain_Kreuzberg")) {
			zipCodes = new IntOpenHashSet(
					List.of(10179, 10967, 10243, 10969, 10245, 10997, 10247, 10999, 12045, 10961, 10963, 10965, 10178));
			return zipCodes;
		}
		if (area.equals("Pankow")) {
			zipCodes = new IntOpenHashSet(List.of(10249, 10405, 10407, 10409, 10435, 10437, 10439, 13051, 13053, 13086,
					13088, 13089, 13125, 13127, 13129, 13156, 13158, 13159, 13187, 13189));
			return zipCodes;
		}
		if (area.equals("Charlottenburg_Wilmersdorf")) {
			zipCodes = new IntOpenHashSet(List.of(10553, 10585, 10587, 10589, 10625, 10627, 10629, 10707, 10709, 10711,
					10713, 10715, 10717, 10719, 10789, 13597, 13627, 14050, 14053, 14055, 14057, 14059));
			return zipCodes;
		}
		if (area.equals("Spandau")) {
			zipCodes = new IntOpenHashSet(
					List.of(13581, 13583, 13585, 13587, 13589, 13591, 13593, 13595, 13599, 14052, 14089));
			return zipCodes;
		}
		if (area.equals("Steglitz_Zehlendorf")) {
			zipCodes = new IntOpenHashSet(List.of(12163, 12165, 12167, 12169, 12203, 12205, 12207, 12209, 12247, 12279,
					14109, 14129, 14163, 14165, 14167, 14169, 14193, 14195, 14199));
			return zipCodes;
		}
		if (area.equals("Tempelhof_Schoeneberg")) {
			zipCodes = new IntOpenHashSet(List.of(10777, 10779, 10781, 10783, 10823, 10825, 10827, 14197, 10829, 12101,
					12103, 12105, 12109, 12157, 12159, 12161, 12249, 12277, 12307, 12309));
			return zipCodes;
		}
		if (area.equals("Neukoelln")) {
			zipCodes = new IntOpenHashSet(List.of(12043, 12047, 12049, 12051, 12053, 12055, 12057, 12059, 12099, 12107,
					12305, 12347, 12349, 12351, 12353, 12355, 12357, 12359));
			return zipCodes;
		}
		if (area.equals("Treptow_Koepenick")) {
			zipCodes = new IntOpenHashSet(List.of(12435, 12437, 12439, 12459, 12487, 12489, 12524, 12526, 12527, 12555,
					12557, 12559, 12587, 12589, 12623));
			return zipCodes;
		}
		if (area.equals("Marzahn_Hellersdorf")) {
			zipCodes = new IntOpenHashSet(
					List.of(12619, 12621, 12627, 12629, 12679, 12681, 12683, 12685, 12687, 12689));
			return zipCodes;
		}
		if (area.equals("Lichtenberg")) {
			zipCodes = new IntOpenHashSet(
					List.of(10315, 13057, 10317, 10318, 10319, 10365, 10367, 10369, 13055, 13059));
			return zipCodes;
		}
		if (area.equals("Reinickendorf")) {
			zipCodes = new IntOpenHashSet(List.of(13403, 13405, 13407, 13409, 13435, 13437, 13439, 13465, 13467, 13469,
					13503, 13505, 13507, 13509, 13629));
			return zipCodes;
		}
		return zipCodes;

	}

	private void analyzeDataForCertainArea(String area, List<File> filesWithData, boolean getPercentageResults,
			AnalyseOptions selectedOptionForAnalyse) throws IOException {

		log.info("Analyze data for " + area);

		IntSet zipCodes = createAreasWithZIPCodes(area);
		Path outputFile = outputFolder.resolve(area + "SnzDataTimeline_until.csv");
		BufferedWriter writer = IOUtils.getBufferedWriter(outputFile.toString());
		try {
			String[] header = new String[] { "date", "type", "total", "3-4h", "4-5h", "5-6h", "6-7h", "7-8h", "8-9h",
					"9-10h", "10-11h", "11-12h", "12-13h", "13-14h", "14-15h", "15-16h", "16-17h", "17-18h", "18-19h",
					"19-20h", "20-21h", "21-22h", "22-23h", "23-24h", "24-25h", "25-26h", "26-27h" };

			JOIN.appendTo(writer, header);
			writer.write("\n");
			// base activity level for different days
			Object2DoubleMap<String> baseStartHomeActs = new Object2DoubleOpenHashMap<>();
			Object2DoubleMap<String> baseEndHomeActs = new Object2DoubleOpenHashMap<>();
			Object2DoubleMap<String> baseStartNonHomeActs = new Object2DoubleOpenHashMap<>();
			Object2DoubleMap<String> baseEndNonHomeActs = new Object2DoubleOpenHashMap<>();

			Map<String, Object2DoubleMap<String>> base = new HashMap<String, Object2DoubleMap<String>>();

			// working days share the same base
			base.put("startHomeActs", baseStartHomeActs);
			base.put("endHomeActs", baseEndHomeActs);
			base.put("startNonHomeActs", baseStartNonHomeActs);
			base.put("endNonHomeActs", baseEndNonHomeActs);

			int countingDays = 1;
			int countingWeek = 1;

			// will contain the last parsed date
			String dateString = "";

			Object2DoubleOpenHashMap<String> sumsHomeStart = new Object2DoubleOpenHashMap<>();
			Object2DoubleOpenHashMap<String> sumsHomeEnd = new Object2DoubleOpenHashMap<>();
			Object2DoubleOpenHashMap<String> sumsNonHomeStart = new Object2DoubleOpenHashMap<>();
			Object2DoubleOpenHashMap<String> sumsNonHomeEnd = new Object2DoubleOpenHashMap<>();

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
						writeOutput(getPercentageResults, writer, header, base, dateString, sumsHomeStart, sumsHomeEnd,
								sumsNonHomeStart, sumsNonHomeEnd);
						clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
					}
					break;
				case onlyWeekdays:
					if (!day.equals(DayOfWeek.SATURDAY) && !day.equals(DayOfWeek.SUNDAY))
						readDataOfTheDay(zipCodes, header, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
								file);
					if (day.equals(DayOfWeek.FRIDAY)) {
						writeOutput(getPercentageResults, writer, header, base, dateString, sumsHomeStart, sumsHomeEnd,
								sumsNonHomeStart, sumsNonHomeEnd);
						clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
					}
					break;
				case onlySaturdays:
					if (day.equals(DayOfWeek.SATURDAY)) {
						readDataOfTheDay(zipCodes, header, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
								file);
						writeOutput(getPercentageResults, writer, header, base, dateString, sumsHomeStart, sumsHomeEnd,
								sumsNonHomeStart, sumsNonHomeEnd);
						clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
					}
					break;
				case onlySundays:
					if (day.equals(DayOfWeek.SUNDAY)) {
						readDataOfTheDay(zipCodes, header, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
								file);
						writeOutput(getPercentageResults, writer, header, base, dateString, sumsHomeStart, sumsHomeEnd,
								sumsNonHomeStart, sumsNonHomeEnd);
						clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
					}
					break;
				case onlyWeekends:
					if (day.equals(DayOfWeek.SATURDAY) || day.equals(DayOfWeek.SUNDAY))
						readDataOfTheDay(zipCodes, header, sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd,
								file);
					if (day.equals(DayOfWeek.SUNDAY)) {
						writeOutput(getPercentageResults, writer, header, base, dateString, sumsHomeStart, sumsHomeEnd,
								sumsNonHomeStart, sumsNonHomeEnd);
						clearSums(sumsHomeStart, sumsHomeEnd, sumsNonHomeStart, sumsNonHomeEnd);
					}
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

			Path finalPath = null;
			switch (selectedOptionForAnalyse) {
			case weeklyResultsOfAllDays:
				if (!getPercentageResults)
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_numbers"));
				else
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString));
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
			default:
				break;

			}

			Files.move(outputFile, finalPath, StandardCopyOption.REPLACE_EXISTING);

			log.info("Write analyze of " + countingDays + " is writen to " + finalPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void clearSums(Object2DoubleOpenHashMap<String> sumsHomeStart, Object2DoubleOpenHashMap<String> sumsHomeEnd,
			Object2DoubleOpenHashMap<String> sumsNonHomeStart, Object2DoubleOpenHashMap<String> sumsNonHomeEnd) {
		sumsHomeStart.clear();
		sumsHomeEnd.clear();
		sumsNonHomeStart.clear();
		sumsNonHomeEnd.clear();
	}

	private void writeOutput(boolean getPercentageResults, BufferedWriter writer, String[] header,
			Map<String, Object2DoubleMap<String>> base, String dateString,
			Object2DoubleOpenHashMap<String> sumsHomeStart, Object2DoubleOpenHashMap<String> sumsHomeEnd,
			Object2DoubleOpenHashMap<String> sumsNonHomeStart, Object2DoubleOpenHashMap<String> sumsNonHomeEnd)
			throws IOException {
		if (base.get("startHomeActs").isEmpty()) {
			base.get("startHomeActs").putAll(sumsHomeStart);
			base.get("endHomeActs").putAll(sumsHomeEnd);
			base.get("startNonHomeActs").putAll(sumsNonHomeStart);
			base.get("endNonHomeActs").putAll(sumsNonHomeEnd);
		}
		if (!sumsHomeStart.isEmpty()) {
			List<String> rowEndHome = new ArrayList<>();
			List<String> rowStartHome = new ArrayList<>();
			List<String> rowEndNonHome = new ArrayList<>();
			List<String> rowStartNonHome = new ArrayList<>();
			rowEndHome.add(LocalDate.parse(dateString, FMT).format(FMT2));
			rowEndHome.add("endHomeActs");
			rowStartHome.add(LocalDate.parse(dateString, FMT).format(FMT2));
			rowStartHome.add("startHomeActs");
			rowEndNonHome.add(LocalDate.parse(dateString, FMT).format(FMT2));
			rowEndNonHome.add("endNonHomeActs");
			rowStartNonHome.add(LocalDate.parse(dateString, FMT).format(FMT2));
			rowStartNonHome.add("startNonHomeActs");

			for (String string : header) {
				if (!string.contains("date") && !string.contains("type")) {
					if (getPercentageResults) {
						rowEndHome.add(String.valueOf(Math.round(
								(sumsHomeEnd.getDouble(string) / base.get("endHomeActs").getDouble(string) - 1) * 10000)
								* 0.01));
						rowStartHome.add(String.valueOf(Math.round(
								(sumsHomeStart.getDouble(string) / base.get("startHomeActs").getDouble(string) - 1)
										* 10000)
								* 0.01));
						rowEndNonHome.add(String.valueOf(Math.round(
								(sumsNonHomeEnd.getDouble(string) / base.get("endNonHomeActs").getDouble(string) - 1)
										* 10000)
								* 0.01));
						rowStartNonHome
								.add(String
										.valueOf(Math
												.round((sumsNonHomeStart.getDouble(string)
														/ base.get("startNonHomeActs").getDouble(string) - 1) * 10000)
												* 0.01));
					} else {
						rowEndHome.add(String.valueOf(sumsHomeEnd.getDouble(string)));
						rowStartHome.add(String.valueOf(sumsHomeStart.getDouble(string)));
						rowEndNonHome.add(String.valueOf(sumsNonHomeEnd.getDouble(string)));
						rowStartNonHome.add(String.valueOf(sumsNonHomeStart.getDouble(string)));
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
	 * @param file
	 * @throws IOException
	 * 
	 */
	private void readDataOfTheDay(IntSet zipCodes, String[] header, Object2DoubleOpenHashMap<String> sumsHomeStart,
			Object2DoubleOpenHashMap<String> sumsHomeEnd, Object2DoubleOpenHashMap<String> sumsNonHomeStart,
			Object2DoubleOpenHashMap<String> sumsNonHomeEnd, File file) throws IOException {

		CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()
				.parse(IOUtils.getBufferedReader(file.toString()));
		for (CSVRecord record : parse) {
			if (!record.get("zipCode").contains("NULL")) {
				int zipCode = Integer.parseInt(record.get("zipCode"));
				if (zipCodes.contains(zipCode)) {
					for (String string : header) {
						if (!string.contains("date") && !string.contains("type")) {
							if (record.get("type").contains("startHomeActs"))
								sumsHomeStart.mergeDouble(string, Integer.parseInt(record.get(string)), Double::sum);
							if (record.get("type").contains("endHomeActs"))
								sumsHomeEnd.mergeDouble(string, Integer.parseInt(record.get(string)), Double::sum);
							if (record.get("type").contains("startNonHomeActs"))
								sumsNonHomeStart.mergeDouble(string, Integer.parseInt(record.get(string)), Double::sum);
							if (record.get("type").contains("endNonHomeActs"))
								sumsNonHomeEnd.mergeDouble(string, Integer.parseInt(record.get(string)), Double::sum);
						}
					}
				}
			}
		}
	}
}
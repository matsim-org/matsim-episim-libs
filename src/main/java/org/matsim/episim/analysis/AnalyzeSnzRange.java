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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * @author: rewert 
 * 			This class reads the SENOZON data for every day and analyzes the moved ranges. The data is
 *          filtered by the zip codes of every area. The results for every day are the percentile of the
 *          changes compared to the base.
 */
@CommandLine.Command(name = "analyzeSnzData", description = "Aggregate snz mobility data.")
class AnalyzeSnzRange implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(AnalyzeSnzRange.class);

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final Joiner JOIN = Joiner.on("\t");

	@CommandLine.Parameters(defaultValue = "../shared-svn/projects/episim/data/Bewegungsdaten/")
	private Path inputFolder;

	@CommandLine.Option(names = "--output", defaultValue = "output")
	private Path outputFolder;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalyzeSnzRange()).execute(args));
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

	@Override
	public Integer call() throws Exception {

		log.info("Searching for files in the folder: " + inputFolder);
		List<File> filesWithData = findInputFiles(inputFolder.toFile());
		Collections.sort(filesWithData);
		log.info("Amount of found files: " + filesWithData.size());

		// zip codes for Germany
		IntSet zipCodesGER = new IntOpenHashSet();
		for (int i = 0; i <= 99999; i++)
			zipCodesGER.add(i);

		// zip codes for Berlin
		IntSet zipCodesBerlin = new IntOpenHashSet();
		for (int i = 10115; i <= 14199; i++)
			zipCodesBerlin.add(i);

		// zip codes for Munich
		IntSet zipCodesMunich = new IntOpenHashSet();
		for (int i = 80331; i <= 81929; i++)
			zipCodesMunich.add(i);

		// zip codes for Hamburg
		IntSet zipCodesHamburg = new IntOpenHashSet();
		for (int i = 22000; i <= 22999; i++)
			zipCodesHamburg.add(i);

		// zip codes for Bonn
		IntSet zipCodesBonn = new IntOpenHashSet();
		for (int i = 53100; i <= 53299; i++)
			zipCodesBonn.add(i);

		// zip codes for the district "Kreis Heinsberg"
		IntSet zipCodesHeinsberg = new IntOpenHashSet(
				List.of(41812, 52538, 52511, 52525, 41836, 52538, 52531, 41849, 41844));

		// zip codes for district "Berchtesgadener Land"
		IntSet zipCodesBerchtesgaden = new IntOpenHashSet(List.of(83317, 83364, 83395, 83404, 83410, 83416, 83435,
				83451, 83454, 83457, 83458, 83471, 83483, 83486, 83487));

		// getPercentageResults: set to true if you want percentages compared to the
		boolean getPercentageResults = true;

//		analyzeDataForCertainArea(zipCodesGER, "Germany", filesWithData, getPercentageResults);
		analyzeDataForCertainArea(zipCodesBerlin, "Berlin", filesWithData, getPercentageResults);
//		analyzeDataForCertainArea(zipCodesMunich, "Munich", filesWithData, getPercentageResults);
//		analyzeDataForCertainArea(zipCodesHamburg, "Hamburg", filesWithData, getPercentageResults);
//		analyzeDataForCertainArea(zipCodesBonn, "Bonn", filesWithData, getPercentageResults);
//		analyzeDataForCertainArea(zipCodesBerchtesgaden, "Berchtesgaden", filesWithData, getPercentageResults);
//		analyzeDataForCertainArea(zipCodesHeinsberg, "Heinsberg", filesWithData, getPercentageResults);

		log.info("Done!");

		return 0;
	}

	private void analyzeDataForCertainArea(IntSet zipCodes, String area, List<File> filesWithData,
			boolean getPercentageResults) throws IOException {

		log.info("Analyze data for " + area);

		Path outputFile = outputFolder.resolve(area + "SnzDataRange_daily_until.csv");

		BufferedWriter writer = IOUtils.getBufferedWriter(outputFile.toString());
		try {

			JOIN.appendTo(writer, Types.values());
			writer.write("\n");

			int countingDays = 1;

			// will contain the last parsed date
			String dateString = "";

			// Analyzes all files with the mobility data
			for (File file : filesWithData) {

				Object2DoubleMap<String> sums = new Object2DoubleOpenHashMap<>();

				dateString = file.getName().split("_")[0];

				CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()
						.parse(IOUtils.getBufferedReader(file.toString()));

				for (CSVRecord record : parse) {

					int zipCode = Integer.parseInt(record.get("zipCode"));
					if (zipCodes.contains(zipCode)) {

						int nPersons = Integer.parseInt(record.get("nPersons"));
						double dailyRangeSum = Double.parseDouble(record.get("dailyRangeSum"));

						sums.mergeDouble("nPersons", nPersons, Double::sum);
						sums.mergeDouble("dailyRangeSum", dailyRangeSum, Double::sum);
					}
				}

				List<String> row = new ArrayList<>();
				row.add(dateString);
				row.add(String.valueOf((int) sums.getDouble("nPersons")));
				row.add(String.valueOf(sums.getDouble("dailyRangeSum")));
				row.add(String.valueOf(sums.getDouble("dailyRangeSum") / sums.getDouble("nPersons")));
				
				JOIN.appendTo(writer, row);
				writer.write("\n");

				if (countingDays == 1 || countingDays % 5 == 0)
					log.info("Finished day " + countingDays);

				countingDays++;
			}
			writer.close();

			Path finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString));
			Files.move(outputFile, finalPath, StandardCopyOption.REPLACE_EXISTING);

			log.info("Write analyze of " + countingDays + " is writen to " + finalPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private enum Types {
		date, nPersons, dailyRangeSum, dailyRangePerPerson
	}
}

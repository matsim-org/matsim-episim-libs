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
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * @author:rewert
 * This class reads the SENEZON personStat data for every day. The data is filtered by the
 * zip codes of every area. The results for every day are the amount of people.
 */
@CommandLine.Command(
		name = "AnalyzeSnzPersonsStat",
		description = "Aggregate snz person statistics of the daily mobility data."
)
class AnalyzeSnzPersonsStat implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(AnalyzeSnzPersonsStat.class);

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final Joiner JOIN = Joiner.on("\t");

	@CommandLine.Parameters(defaultValue = "../shared-svn/projects/episim/data/Bewegungsdaten/")
	private Path inputFolder;

	@CommandLine.Option(names = "--output", defaultValue = "output")
	private Path outputFolder;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalyzeSnzPersonsStat()).execute(args));
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
		log.info("Amount of found files: " + filesWithData.size());

		// zip codes for Test
				IntSet zipCodesTest = new IntOpenHashSet();	
				zipCodesTest.add(1067);
		
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

		// zip codes for the district "Kreis Heinsberg"
		IntSet zipCodesHeinsberg = new IntOpenHashSet(List.of(41812, 52538, 52511, 52525, 41836, 52538, 52531, 41849, 41844));

//		analyzeDataForCertainArea(zipCodesTest, "Test", filesWithData);
//		analyzeDataForCertainArea(zipCodesDE, "Germany", filesWithData);
		analyzeDataForCertainArea(zipCodesBerlin, "Berlin", filesWithData);
//		analyzeDataForCertainArea(zipCodesMunich, "Munich", filesWithData);
//		analyzeDataForCertainArea(zipCodesHeinsberg, "Heinsberg", filesWithData);

		log.info("Done!");

		return 0;
	}

	private void analyzeDataForCertainArea(IntSet zipCodes, String area, List<File> filesWithData) throws IOException {

		log.info("Analyze data for " + area);

		Path outputFile = outputFolder.resolve(area + "SnzDataPersonStats_daily_until.csv");

		BufferedWriter writer = IOUtils.getBufferedWriter(outputFile.toString());
		try {

			JOIN.appendTo(writer, Types.values());
			writer.write("\n");

			int countingDays = 1;

			// will contain the last parsed date
			String dateString = "";

			for (File file : filesWithData) {

				Object2IntOpenHashMap<String> sums = new Object2IntOpenHashMap<>();

				dateString = file.getName().split("_")[0];

				CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(IOUtils.getBufferedReader(file.toString()));

				for (CSVRecord record : parse) {

					int zipCode = Integer.parseInt(record.get("zipCode"));
					if (zipCodes.contains(zipCode)) {

						int nStayHome = Integer.parseInt(record.get("nStayHomes"));
						int nMobilePersons = Integer.parseInt(record.get("nMobilePersons"));

						sums.mergeInt("shareStayHome",nStayHome, Integer::sum);
						sums.mergeInt("shareMobilePersons",nMobilePersons, Integer::sum);
						sums.mergeInt("nPersons",nStayHome+nMobilePersons, Integer::sum);
						
					}
				}

				List<String> row = new ArrayList<>();
				row.add(dateString);
				row.add(String.valueOf(sums.getInt("nPersons")));
				for (int i = 2; i < Types.values().length; i++) {

					String type = Types.values()[i].toString();
					row.add(String.valueOf(Math.round((double)sums.getInt(type)/(double)sums.getInt("nPersons")*100)));
				}

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
		date, nPersons, shareStayHome, shareMobilePersons
	}
}

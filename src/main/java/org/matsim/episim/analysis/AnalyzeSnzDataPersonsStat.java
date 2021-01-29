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
import java.util.Map.Entry;
import java.util.concurrent.Callable;

/**
 * @author:rewert This class reads the SENEZON personStat data for every day.
 *                The data is filtered by the zip codes of every area. The
 *                results for every day are the amount of people.
 */
@CommandLine.Command(name = "AnalyzeSnzPersonsStat", description = "Aggregate snz person statistics of the daily mobility data.")
class AnalyzeSnzDataPersonsStat implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(AnalyzeSnzDataPersonsStat.class);

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final Joiner JOIN = Joiner.on("\t");

	@CommandLine.Parameters(defaultValue = "../shared-svn/projects/episim/data/Bewegungsdaten/")
	private Path inputFolder;

	@CommandLine.Option(names = "--output", defaultValue = "output")
	private Path outputFolder;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalyzeSnzDataPersonsStat()).execute(args));
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

		// zip codes for districts in Berlin
		HashMap<String, IntSet> berlinDistricts = new HashMap<String, IntSet>();
		berlinDistricts.put("Mitte", new IntOpenHashSet(List.of(10115, 10559, 13355, 10117, 10623, 13357, 10119, 10785, 13359, 10787, 10557, 13353, 10555, 13351, 13349, 10551, 13347)));
		berlinDistricts.put("Friedrichshain_Kreuzberg", new IntOpenHashSet(List.of(10179, 10967, 10243, 10969, 10245, 10997, 10247, 10999, 12045, 10961, 10963, 10965, 10178)));
		berlinDistricts.put("Pankow", new IntOpenHashSet(List.of(10249, 10405, 10407, 10409, 10435, 10437, 10439, 13051, 13053, 13086, 13088, 13089, 13125, 13127, 13129, 13156, 13158, 13159, 13187, 13189)));
		berlinDistricts.put("Charlottenburg_Wilmersdorf", new IntOpenHashSet(List.of(10553, 10585, 10587, 10589, 10625, 10627, 10629, 10707, 10709, 10711, 10713, 10715, 10717, 10719, 10789, 13597, 13627, 14050, 14053, 14055, 14057, 14059)));
		berlinDistricts.put("Spandau", new IntOpenHashSet(List.of(13581, 13583, 13585, 13587, 13589, 13591, 13593, 13595, 13599, 14052, 14089)));
		berlinDistricts.put("Steglitz_Zehlendorf", new IntOpenHashSet(List.of(12163, 12165, 12167, 12169, 12203, 12205, 12207, 12209, 12247, 12279, 14109, 14129, 14163, 14165, 14167, 14169, 14193, 14195, 14199)));
		berlinDistricts.put("Tempelhof_Schoeneberg", new IntOpenHashSet(List.of(10777, 10779, 10781, 10783, 10823, 10825, 10827, 14197, 10829, 12101, 12103, 12105, 12109, 12157, 12159, 12161, 12249, 12277, 12307, 12309)));
		berlinDistricts.put("Neukoelln", new IntOpenHashSet(List.of(12043, 12047, 12049, 12051, 12053, 12055, 12057, 12059, 12099, 12107, 12305, 12347, 12349, 12351, 12353, 12355, 12357, 12359)));
		berlinDistricts.put("Treptow_Koepenick", new IntOpenHashSet(List.of(12435, 12437, 12439, 12459, 12487, 12489, 12524, 12526, 12527, 12555, 12557, 12559, 12587, 12589, 12623)));
		berlinDistricts.put("Marzahn_Hellersdorf", new IntOpenHashSet(List.of(12619, 12621, 12627, 12629, 12679, 12681, 12683, 12685, 12687, 12689)));
		berlinDistricts.put("Lichtenberg", new IntOpenHashSet(List.of(10315, 13057, 10317, 10318, 10319, 10365, 10367, 10369, 13055, 13059)));
		berlinDistricts.put("Reinickendorf", new IntOpenHashSet(List.of(13403, 13405, 13407, 13409, 13435, 13437, 13439, 13465, 13467, 13469, 13503, 13505, 13507, 13509, 13629)));	

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
		IntSet zipCodesHeinsberg = new IntOpenHashSet(
				List.of(41812, 52538, 52511, 52525, 41836, 52538, 52531, 41849, 41844));

//		analyzeDataForCertainArea(zipCodesTest, "Test", filesWithData);
//		analyzeDataForCertainArea(zipCodesDE, "Germany", filesWithData);
		analyzeDataForCertainArea(zipCodesBerlin, "Berlin", filesWithData);
//		analyzeDataForCertainArea(zipCodesMunich, "Munich", filesWithData);
//		analyzeDataForCertainArea(zipCodesHeinsberg, "Heinsberg", filesWithData);
		for (Entry<String, IntSet> district : berlinDistricts.entrySet())
			analyzeDataForCertainArea(district.getValue(), district.getKey(), filesWithData);

		log.info("Done!");

		return 0;
	}

	private void analyzeDataForCertainArea(IntSet zipCodes, String area, List<File> filesWithData) throws IOException {

		log.info("Analyze data for " + area);

		Path outputFile = outputFolder.resolve(area + "SnzDataPersonStats_until.csv");

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

				CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()
						.parse(IOUtils.getBufferedReader(file.toString()));

				for (CSVRecord record : parse) {
					if (!record.get("zipCode").contains("NULL")) {
						int zipCode = Integer.parseInt(record.get("zipCode"));
						if (zipCodes.contains(zipCode)) {

							int nStayHome = Integer.parseInt(record.get("nStayHomes"));
							int nMobilePersons = Integer.parseInt(record.get("nMobilePersons"));

							sums.mergeInt("shareStayHome", nStayHome, Integer::sum);
							sums.mergeInt("shareMobilePersons", nMobilePersons, Integer::sum);
							sums.mergeInt("nPersons", nStayHome + nMobilePersons, Integer::sum);

						}
					}
				}

				List<String> row = new ArrayList<>();
				row.add(dateString);
				row.add(String.valueOf(sums.getInt("nPersons")));
				for (int i = 2; i < Types.values().length; i++) {

					String type = Types.values()[i].toString();
					row.add(String
							.valueOf(Math.round((double) sums.getInt(type) / (double) sums.getInt("nPersons") * 100)));
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

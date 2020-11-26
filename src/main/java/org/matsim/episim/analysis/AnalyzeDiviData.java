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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;

import com.google.common.base.Joiner;
import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import picocli.CommandLine;

/**
 * @author: rewert
 * This class reads the DIVI data for every day. The data is filtered for every Bundesland. 
 */
@CommandLine.Command(name = "analyzeDIVIData", description = "Analyze DIVI data.")
public class AnalyzeDiviData implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(AnalyzeDiviData.class);
	private static final Joiner JOIN = Joiner.on("\t");

	@CommandLine.Parameters(defaultValue = "../public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/DIVI/Daily_reports/")
	private Path inputFolder;

	@CommandLine.Option(names = "--output", defaultValue = "../public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/DIVI/")
	private Path outputFolder;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalyzeDiviData()).execute(args));
	}

	/**
	 * This method searches all files with an certain name in a given folder.
	 */
	private static List<File> findInputFiles(File inputFolder) {
		List<File> fileData = new ArrayList<File>();

		for (File file : Objects.requireNonNull(inputFolder.listFiles())) {
			if (file.getName().contains("DIVI"))
				fileData.add(file);
		}
		return fileData;
	}

	@Override
	public Integer call() throws Exception {
		
		HashMap<Integer, String> bundeslandCodes = new HashMap<Integer, String>();
		bundeslandCodes.put(1, "Schleswig-Holstein");
		bundeslandCodes.put(2, "Hamburg");
		bundeslandCodes.put(3, "Niedersachsen");
		bundeslandCodes.put(4, "Bremen");
		bundeslandCodes.put(5, "Nordrhein-Westfalen");
		bundeslandCodes.put(6, "Hessen");
		bundeslandCodes.put(7, "Rheinland-Pfalz");
		bundeslandCodes.put(8, "Baden-Wuertenberg");
		bundeslandCodes.put(9, "Bayern");
		bundeslandCodes.put(10, "Saarland");
		bundeslandCodes.put(11, "Berlin");
		bundeslandCodes.put(12, "Brandenburg");
		bundeslandCodes.put(13, "Mecklenburg-Vorpommern");
		bundeslandCodes.put(14, "Sachsen");
		bundeslandCodes.put(15, "Sachsen-Anhalt");
		bundeslandCodes.put(16, "Thueringen");

		log.info("Searching for files in the folder: " + inputFolder);
		List<File> filesWithData = findInputFiles(inputFolder.toFile());
		log.info("Amount of found files: " + filesWithData.size());

		analyzeDataForCertainArea("Bundesland", filesWithData, bundeslandCodes);

		log.info("Done!");

		return 0;
	}

	private void analyzeDataForCertainArea(String area, List<File> filesWithData, HashMap<Integer, String> bundeslandCodes) throws IOException {

		log.info("Analyze data for " + area);

		Path outputFile = outputFolder.resolve(area + "-divi-processed_until.csv");

		BufferedWriter writer = IOUtils.getBufferedWriter(outputFile.toString());
		try {

			JOIN.appendTo(writer, HeaderTable.values());
			writer.write("\n");

			int countingDays = 1;

			// will contain the last parsed date
			String dateString = "";

			for (File file : filesWithData) {

				Object2IntMap<Integer> sums_casesCovidICU = new Object2IntLinkedOpenHashMap<>();
				Object2IntMap<Integer> sums_beatmet = new Object2IntLinkedOpenHashMap<>();
				Object2DoubleMap<Integer> sums_freeBeds = new Object2DoubleLinkedOpenHashMap<>();
				Object2DoubleMap<Integer> sums_occupiedBeds = new Object2DoubleLinkedOpenHashMap<>();

				dateString = file.getName().split("_")[1];

				CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()
						.parse(IOUtils.getBufferedReader(file.toString()));

				for (CSVRecord record : parse) {
					
					int bundeslandCode = Integer.parseInt(record.get("bundesland"));
					int aktuellICU = Integer.parseInt(record.get("faelle_covid_aktuell"));
					int aktuellBeatmet = Integer.parseInt(record.get("faelle_covid_aktuell_beatmet"));
					double freeBeds = Double.parseDouble(record.get("betten_frei"));
					double occupiedBeds = Double.parseDouble(record.get("betten_belegt"));

					sums_casesCovidICU.mergeInt(bundeslandCode, aktuellICU, Integer::sum);
					sums_beatmet.mergeInt(bundeslandCode, aktuellBeatmet, Integer::sum);
					sums_freeBeds.mergeDouble(bundeslandCode, freeBeds, Double::sum);
					sums_occupiedBeds.mergeDouble(bundeslandCode, occupiedBeds, Double::sum);

				}

				for (int bundeslandCode : sums_beatmet.keySet()) {
					
					List<String> row = new ArrayList<>();
					row.add(dateString);
					
					row.add(String.valueOf(bundeslandCodes.get(bundeslandCode)));
					row.add(String.valueOf(sums_casesCovidICU.getInt(bundeslandCode)));
					row.add(String.valueOf(sums_beatmet.getInt(bundeslandCode)));
					row.add(String.valueOf(sums_freeBeds.getDouble(bundeslandCode)));
					row.add(String.valueOf(sums_occupiedBeds.getDouble(bundeslandCode)));
					
					JOIN.appendTo(writer, row);
					writer.write("\n");
				}		

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

	private enum HeaderTable {
		date, Bundesland, covid_faelle_aktuell, covid_aktuell_beatmet, bettenFrei, bettenBelegt
	}
}

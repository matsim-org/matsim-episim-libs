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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
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
	private static final Joiner JOIN = Joiner.on("\t");

	private enum AnalyseAreas {
		Germany, Berlin, Munich, Heinsberg, Bonn, Mannheim, Wolfsburg, BerlinDistricts, Test, Berchtesgaden, Hamburg, Bundeslaender
	}
	
	@CommandLine.Parameters(defaultValue = "../shared-svn/projects/episim/data/Bewegungsdaten/")
	private Path inputFolder;

	@CommandLine.Option(names = "--output", defaultValue = "output")
	private Path outputFolder;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalyzeSnzRange()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		AnalyseAreas selectedArea = AnalyseAreas.Bundeslaender;
		analyseData(selectedArea);

		log.info("Done!");

		return 0;
	}

	private void analyseData(AnalyseAreas selectedArea) throws IOException {

		switch (selectedArea) {
		case Berchtesgaden:
			IntSet zipCodesBerchtesgaden = new IntOpenHashSet(List.of(83317, 83364, 83395, 83404, 83410, 83416, 83435,
					83451, 83454, 83457, 83458, 83471, 83483, 83486, 83487));
			analyzeDataForCertainArea("Berchtesgaden", zipCodesBerchtesgaden);
			break;
		case Berlin:
			IntSet zipCodesBerlin = new IntOpenHashSet();
			for (int i = 10115; i <= 14199; i++)
				zipCodesBerlin.add(i);
			analyzeDataForCertainArea("Berlin", zipCodesBerlin);
			break;
		case BerlinDistricts:
			HashMap<String, IntSet> berlinDistricts = new HashMap<String, IntSet>();
			berlinDistricts.put("Mitte", new IntOpenHashSet(List.of(10115, 10559, 13355, 10117, 10623, 13357, 10119,
					10785, 13359, 10787, 10557, 13353, 10555, 13351, 13349, 10551, 13347)));
			berlinDistricts.put("Friedrichshain_Kreuzberg", new IntOpenHashSet(List.of(10179, 10967, 10243, 10969,
					10245, 10997, 10247, 10999, 12045, 10961, 10963, 10965, 10178)));
			berlinDistricts.put("Pankow", new IntOpenHashSet(List.of(10249, 10405, 10407, 10409, 10435, 10437, 10439,
					13051, 13053, 13086, 13088, 13089, 13125, 13127, 13129, 13156, 13158, 13159, 13187, 13189)));
			berlinDistricts.put("Charlottenburg_Wilmersdorf",
					new IntOpenHashSet(List.of(10553, 10585, 10587, 10589, 10625, 10627, 10629, 10707, 10709, 10711,
							10713, 10715, 10717, 10719, 10789, 13597, 13627, 14050, 14053, 14055, 14057, 14059)));
			berlinDistricts.put("Spandau", new IntOpenHashSet(
					List.of(13581, 13583, 13585, 13587, 13589, 13591, 13593, 13595, 13599, 14052, 14089)));
			berlinDistricts.put("Steglitz_Zehlendorf", new IntOpenHashSet(List.of(12163, 12165, 12167, 12169, 12203,
					12205, 12207, 12209, 12247, 12279, 14109, 14129, 14163, 14165, 14167, 14169, 14193, 14195, 14199)));
			berlinDistricts.put("Tempelhof_Schoeneberg",
					new IntOpenHashSet(List.of(10777, 10779, 10781, 10783, 10823, 10825, 10827, 14197, 10829, 12101,
							12103, 12105, 12109, 12157, 12159, 12161, 12249, 12277, 12307, 12309)));
			berlinDistricts.put("Neukoelln", new IntOpenHashSet(List.of(12043, 12047, 12049, 12051, 12053, 12055, 12057,
					12059, 12099, 12107, 12305, 12347, 12349, 12351, 12353, 12355, 12357, 12359)));
			berlinDistricts.put("Treptow_Koepenick", new IntOpenHashSet(List.of(12435, 12437, 12439, 12459, 12487,
					12489, 12524, 12526, 12527, 12555, 12557, 12559, 12587, 12589, 12623)));
			berlinDistricts.put("Marzahn_Hellersdorf",
					new IntOpenHashSet(List.of(12619, 12621, 12627, 12629, 12679, 12681, 12683, 12685, 12687, 12689)));
			berlinDistricts.put("Lichtenberg",
					new IntOpenHashSet(List.of(10315, 13057, 10317, 10318, 10319, 10365, 10367, 10369, 13055, 13059)));
			berlinDistricts.put("Reinickendorf", new IntOpenHashSet(List.of(13403, 13405, 13407, 13409, 13435, 13437,
					13439, 13465, 13467, 13469, 13503, 13505, 13507, 13509, 13629)));
			for (Entry<String, IntSet> district : berlinDistricts.entrySet())
				analyzeDataForCertainArea(district.getKey(), district.getValue());
			break;
		case Bonn:
			IntSet zipCodesBonn = new IntOpenHashSet();
			for (int i = 53100; i <= 53299; i++)
				zipCodesBonn.add(i);
			analyzeDataForCertainArea("Bonn", zipCodesBonn);
			break;
		case Germany:
			IntSet zipCodesGER = new IntOpenHashSet();
			for (int i = 0; i <= 99999; i++)
				zipCodesGER.add(i);
			analyzeDataForCertainArea("Germany", zipCodesGER);
			break;
		case Hamburg:
			IntSet zipCodesHamburg = new IntOpenHashSet();
			for (int i = 22000; i <= 22999; i++)
				zipCodesHamburg.add(i);
			analyzeDataForCertainArea("Hamburg", zipCodesHamburg);
			break;
		case Heinsberg:
			IntSet zipCodesHeinsberg = new IntOpenHashSet(
					List.of(41812, 52538, 52511, 52525, 41836, 52538, 52531, 41849, 41844));
			analyzeDataForCertainArea("Heinsberg", zipCodesHeinsberg);
			break;
		case Mannheim:
			IntSet zipCodesMannheim = new IntOpenHashSet(List.of(68159, 68161, 68163, 68165, 68167, 68169, 68199, 68219,
					68229, 68239, 68259, 68305, 68307, 68309));
			analyzeDataForCertainArea("Mannheim", zipCodesMannheim);
			break;
		case Munich:
			IntSet zipCodesMunich = new IntOpenHashSet();
			for (int i = 80331; i <= 81929; i++)
				zipCodesMunich.add(i);
			analyzeDataForCertainArea("Munich", zipCodesMunich);
			break;
		case Test:
			IntSet zipCodesTest = new IntOpenHashSet(List.of(1067));
			analyzeDataForCertainArea("Test", zipCodesTest);
			break;
		case Wolfsburg:
			IntSet zipCodesWolfsburg = new IntOpenHashSet(List.of(38440, 38442, 38444, 38446, 38448));
			analyzeDataForCertainArea("Wolfsburg", zipCodesWolfsburg);
			break;
		case Bundeslaender:	
			outputFolder = Path.of("../public-svn/matsim/scenarios/countries/de/episim/mobilityData/bundeslaender/");
			writeBundeslandDataForPublic(outputFolder);
			break;
		default:
			break;

		}
	}

	private void analyzeDataForCertainArea(String area, IntSet zipCodes) throws IOException {
		log.info("Analyze data for " + area);

		log.info("Searching for files in the folder: " + inputFolder);
		List<File> filesWithData = findInputFiles(inputFolder.toFile());
		Collections.sort(filesWithData);
		log.info("Amount of found files: " + filesWithData.size());
		
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
					if (!record.get("zipCode").contains("NULL")) {
						int zipCode = Integer.parseInt(record.get("zipCode"));
						if (zipCodes.contains(zipCode)) {

							int nPersons = Integer.parseInt(record.get("nPersons"));
							double dailyRangeSum = Double.parseDouble(record.get("dailyRangeSum"));
							int nStayHome = Integer.parseInt(record.get("nStayHomes"));
							int nMobilePersons = Integer.parseInt(record.get("nMobilePersons"));

							sums.mergeDouble("nStayHomes", nStayHome, Double::sum);
							sums.mergeDouble("nMobilePersons", nMobilePersons, Double::sum);
							sums.mergeDouble("nPersons", nPersons, Double::sum);
							sums.mergeDouble("dailyRangeSum", dailyRangeSum, Double::sum);
						}
					}
				}

				List<String> row = new ArrayList<>();
				row.add(dateString);
				row.add(String.valueOf((int) sums.getDouble("nPersons")));
				row.add(String.valueOf((int) sums.getDouble("nStayHomes")));
				row.add(String.valueOf((int) sums.getDouble("nMobilePersons")));
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
	
	/**
	 * Analyze range data and write result to {@code outputFile}. The result contains data for all Bundeslaender.
	 */
	public void writeBundeslandDataForPublic(Path outputFile) throws IOException {

		List<File> filesWithData = findInputFiles(inputFolder.toFile());

		Path thisOutputFile = outputFile.resolve("range_OverviewBL.csv");

		Collections.sort(filesWithData);
		log.info("Searching for files in the folder: " + inputFolder);
		log.info("Amount of found files: " + filesWithData.size());

		BufferedWriter writer = IOUtils.getBufferedWriter(thisOutputFile.toString());
		try {
			
			JOIN.appendTo(writer, TypesBL.values());
			writer.write("\n");

			HashMap<String, IntSet> zipCodesBL = findZIPCodesForBundeslaender();

			int countingDays = 1;

			// will contain the last parsed date
			String dateString = "";

			// Analyzes all files with the mobility data
			for (File file : filesWithData) {
				for (Entry<String, IntSet> bundesland : zipCodesBL.entrySet()) {
					IntSet zipCodes = bundesland.getValue();
					String nameBundesland = bundesland.getKey();

					Object2DoubleMap<String> sums = new Object2DoubleOpenHashMap<>();

					dateString = file.getName().split("_")[0];

					CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()
							.parse(IOUtils.getBufferedReader(file.toString()));

					for (CSVRecord record : parse) {
						if (!record.get("zipCode").contains("NULL")) {
							int zipCode = Integer.parseInt(record.get("zipCode"));
							if (zipCodes.contains(zipCode)) {

								int nPersons = Integer.parseInt(record.get("nPersons"));
								double dailyRangeSum = Double.parseDouble(record.get("dailyRangeSum"));
								int nStayHome = Integer.parseInt(record.get("nStayHomes"));
								int nMobilePersons = Integer.parseInt(record.get("nMobilePersons"));

								sums.mergeDouble("nStayHomes", nStayHome, Double::sum);
								sums.mergeDouble("nMobilePersons", nMobilePersons, Double::sum);
								sums.mergeDouble("nPersons", nPersons, Double::sum);
								sums.mergeDouble("dailyRangeSum", dailyRangeSum, Double::sum);
							}
						}
					}

					List<String> row = new ArrayList<>();
					row.add(dateString);
					row.add(nameBundesland);
					row.add(String.valueOf(round2Decimals(sums.getDouble("nMobilePersons")/sums.getDouble("nPersons"))*100));
					row.add(String.valueOf(round2Decimals(sums.getDouble("dailyRangeSum") / sums.getDouble("nPersons"))));
					JOIN.appendTo(writer, row);
					writer.write("\n");
				}

				if (countingDays == 1 || countingDays % 5 == 0)
					log.info("Finished day " + countingDays);

				countingDays++;
			}
			writer.close();
			log.info("Write analyze of " + countingDays + " is writen to " + thisOutputFile);
		} catch (IOException e) {
			e.printStackTrace();
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
	
	private HashMap<String, IntSet> findZIPCodesForBundeslaender() throws IOException {

		String zipCodeFile = "../shared-svn/projects/episim/data/PLZ/OpenGeoDB_bundesland_plz_ort_de.csv";
		HashMap<String, IntSet> zipCodesBL = new HashMap<String, IntSet>();
		zipCodesBL.put("Deutschland", new IntOpenHashSet());

		try (BufferedReader reader = IOUtils.getBufferedReader(zipCodeFile)) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				if (zipCodesBL.containsKey(record.get("BL"))) {
					zipCodesBL.get(record.get("BL")).add(Integer.parseInt(record.get("PLZ")));
					zipCodesBL.get("Deutschland").add(Integer.parseInt(record.get("PLZ")));
					
				} else {
					zipCodesBL.put(record.get("BL"), new IntOpenHashSet(List.of(Integer.parseInt(record.get("PLZ")))));
					zipCodesBL.get("Deutschland").add(Integer.parseInt(record.get("PLZ")));
				}
			}
		}
		return zipCodesBL;
	}

	/**
	 * Rounds the number 2 places after the comma
	 * 
	 */
	static double round2Decimals(double number) {
		return Math.round(number * 100) * 0.01;
	}
	private enum TypesBL {
		date, BundeslandID, sharePersonLeavingHome, dailyRangePerPerson
	}private enum Types {
		date, nPersons, nStayHomes, nMobilePersons, dailyRangeSum, dailyRangePerPerson
	}
}

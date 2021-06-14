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

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.episim.model.input.CreateRestrictionsFromSnz;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

/**
 * @author: rewert This class reads the SENOZON data for every day. The data is
 *          filtered by the zip codes of every area. The base line is always the
 *          first day. The results for every day are the percentile of the
 *          changes compared to the base.
 */
@CommandLine.Command(name = "analyzeSnzData", description = "Aggregate snz mobility data.")
class AnalyzeSnzData implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(AnalyzeSnzData.class);

	private enum AnalyseAreas {
		Germany, Berlin, BerlinDistrcits, Munich, Hamburg, Bonn, Heinsberg, Berchtesgaden, Mannheim, Wolfsburg, Test,
		Bundeslaender, Tuebingen, Landkreise, AnyArea
	};

	private enum AnalyseOptions {
		onlyWeekdays, weeklyResultsOfAllDays, onlyWeekends, dailyResults
	};

	private enum BaseDaysForComparison {
		March2020, Sebtember2020, days2018
	};

	@CommandLine.Parameters(defaultValue = "../shared-svn/projects/episim/data/Bewegungsdaten/")
	private Path inputFolder;

	@CommandLine.Option(names = "--output", defaultValue = "output")
	private Path outputFolder;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalyzeSnzData()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		AnalyseAreas selectedArea = AnalyseAreas.Berlin;
		BaseDaysForComparison selectedBase = BaseDaysForComparison.March2020;
		AnalyseOptions selectedOutputOptions = AnalyseOptions.onlyWeekends;
		String anyArea = "Berlin";
		
		// getPercentageResults: set to true if you want percentages compared to the base, if you select false you get the total amounts 
		boolean getPercentageResults = false;

		writeData(selectedArea, getPercentageResults, selectedBase, anyArea, selectedOutputOptions);

		log.info("Done!");

		return 0;
	}

	private void writeData(AnalyseAreas selectedArea, boolean getPercentageResults, BaseDaysForComparison selectedBase,
			String anyArea, AnalyseOptions selectedOutputOptions) throws IOException {
		CreateRestrictionsFromSnz snz = new CreateRestrictionsFromSnz();
		snz.setInput(inputFolder);
		List<String> baseDays = Arrays.asList();
		String outputOption = null;
		
		switch (selectedBase) {
		case March2020:
			break;
		case days2018:
			String weekdayBase2018 = "20180131";
			String saturdayBase2018 = "20180127";
			String sundayBase2018 = "20180114";
			baseDays = Arrays.asList(weekdayBase2018, saturdayBase2018, sundayBase2018);
			break;
		case Sebtember2020:
			String weekdayBase2020 = "20200911";
			String saturdayBase2020 = "20200912";
			String sundayBase2020 = "20200913";
			baseDays = Arrays.asList(weekdayBase2020, saturdayBase2020, sundayBase2020);
			break;
		}

		switch (selectedArea) {
		case AnyArea:
			HashMap<String, IntSet> zipCodesAnyArea = snz.findZipCodesForAnyArea(anyArea);
			snz.writeDataForCertainArea(
					outputFolder.resolve(zipCodesAnyArea.keySet().iterator().next() + "SnzData_daily_until.csv"),
					zipCodesAnyArea.values().iterator().next(), getPercentageResults, baseDays);
			break;
		case Berchtesgaden:
			IntSet zipCodesBerchtesgaden = new IntOpenHashSet(List.of(83317, 83364, 83395, 83404, 83410, 83416, 83435,
					83451, 83454, 83457, 83458, 83471, 83483, 83486, 83487));
			snz.writeDataForCertainArea(outputFolder.resolve("BerchtesgadenSnzData_daily_until.csv"),
					zipCodesBerchtesgaden, getPercentageResults, baseDays);
			break;
		case Berlin:
			IntSet zipCodesBerlin = new IntOpenHashSet();
			for (int i = 10115; i <= 14199; i++)
				zipCodesBerlin.add(i);
			snz.writeDataForCertainArea(outputFolder.resolve("BerlinSnzData_daily_until.csv"), zipCodesBerlin,
					getPercentageResults, baseDays);
			break;
		case BerlinDistrcits:
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
				snz.writeDataForCertainArea(outputFolder.resolve(district.getKey() + "SnzData_daily_until.csv"),
						district.getValue(), getPercentageResults, baseDays);
			break;
		case Bonn:
			IntSet zipCodesBonn = new IntOpenHashSet();
			for (int i = 53100; i <= 53299; i++)
				zipCodesBonn.add(i);
			snz.writeDataForCertainArea(outputFolder.resolve("BonnSnzData_daily_until.csv"), zipCodesBonn,
					getPercentageResults, baseDays);
			break;
		case Germany:
			IntSet zipCodesGER = new IntOpenHashSet();
			for (int i = 0; i <= 99999; i++)
				zipCodesGER.add(i);
			snz.writeDataForCertainArea(outputFolder.resolve("GermanySnzData_daily_until.csv"), zipCodesGER,
					getPercentageResults, baseDays);
			break;
		case Hamburg:
			IntSet zipCodesHamburg = new IntOpenHashSet();
			for (int i = 22000; i <= 22999; i++)
				zipCodesHamburg.add(i);
			snz.writeDataForCertainArea(outputFolder.resolve("HamburgSnzData_daily_until.csv"), zipCodesHamburg,
					getPercentageResults, baseDays);
			break;
		case Heinsberg:
			IntSet zipCodesHeinsberg = new IntOpenHashSet(
					List.of(41812, 52538, 52511, 52525, 41836, 52538, 52531, 41849, 41844));
			snz.writeDataForCertainArea(outputFolder.resolve("HeinsbergSnzData_daily_until.csv"), zipCodesHeinsberg,
					getPercentageResults, baseDays);

			break;
		case Mannheim:
			IntSet zipCodesMannheim = new IntOpenHashSet(List.of(68159, 68161, 68163, 68165, 68167, 68169, 68199, 68219,
					68229, 68239, 68259, 68305, 68307, 68309));
			snz.writeDataForCertainArea(outputFolder.resolve("MannheimSnzData_daily_until.csv"), zipCodesMannheim,
					getPercentageResults, baseDays);
			break;
		case Munich:
			IntSet zipCodesMunich = new IntOpenHashSet();
			for (int i = 80331; i <= 81929; i++)
				zipCodesMunich.add(i);
			snz.writeDataForCertainArea(outputFolder.resolve("MunichSnzData_daily_until.csv"), zipCodesMunich,
					getPercentageResults, baseDays);
			break;
		case Test:
			IntSet zipCodesTest = new IntOpenHashSet(List.of(1067));
			snz.writeDataForCertainArea(outputFolder.resolve("TestSnzData_daily_until.csv"), zipCodesTest,
					getPercentageResults, baseDays);
			break;
		case Tuebingen:
			IntSet zipCodesTuebingen = new IntOpenHashSet(List.of(72070, 72072, 72074, 72076));
			snz.writeDataForCertainArea(outputFolder.resolve("TuebingenSnzData_daily_until.csv"), zipCodesTuebingen,
					getPercentageResults, baseDays);
			break;
		case Wolfsburg:
			IntSet zipCodesWolfsburg = new IntOpenHashSet(List.of(38440, 38442, 38444, 38446, 38448));
			snz.writeDataForCertainArea(outputFolder.resolve("WolfsburgSnzData_daily_until.csv"), zipCodesWolfsburg,
					getPercentageResults, baseDays);
			break;
		case Bundeslaender:
			outputFolder = Path.of("../public-svn/matsim/scenarios/countries/de/episim/mobilityData/bundeslaender/");
			switch (selectedOutputOptions) {
			case weeklyResultsOfAllDays:
				outputOption = "weekly";
				break;
			case dailyResults:
				outputOption = "daily";
				break;
			case onlyWeekdays:
				outputOption = "weekdays";
				break;
			case onlyWeekends:
				outputOption = "weekends";
				break;
			default:
				break;
			}
			snz.writeBundeslandDataForPublic(outputFolder);//TODO add Feiertage
			break;
		case Landkreise:
			outputFolder = Path.of("../public-svn/matsim/scenarios/countries/de/episim/mobilityData/landkreise/");
			
			switch (selectedOutputOptions) {
			case weeklyResultsOfAllDays:
				outputOption = "weekly";
				break;
			case dailyResults:
				outputOption = "daily";
				break;
			case onlyWeekdays:
				outputOption = "weekdays";
				break;
			case onlyWeekends:
				outputOption = "weekends";
				break;
			default:
				break;
			}
			snz.writeLandkreisDataForPublic(outputFolder, outputOption);
			break;
		default:
			break;

		}
	}
}

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

import com.google.common.io.Resources;

import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * @author: Ricardo Ewert This class reads the SENOZON mobility data for every
 *          day. The data is filtered by the zip codes of every area.
 */
@CommandLine.Command(name = "analyzeSnzData", description = "Aggregate snz mobility data.")
class AnalyzeSnzData implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(AnalyzeSnzData.class);

	private enum AnalyseAreas {
		Germany, Berlin, BerlinDistricts, Test, Bundeslaender, Landkreise, AnyArea, UpdateMobilityDashboardData, Koeln, Brandenburg
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

		AnalyseAreas selectedArea = AnalyseAreas.Brandenburg;
		BaseDaysForComparison selectedBase = BaseDaysForComparison.March2020;
		AnalyseOptions selectedOutputOption = AnalyseOptions.dailyResults; // only for the analysis of Bundeslaender or Landkreise
		String startDateStillUsingBaseDays = ""; // set in this format YYYYMMDD, only for Bundeslaender and Landkreise
		String anyArea = ""; // you can select a certain Landkreis and the zip codes are collected automatically

		boolean getPercentageResults = true; // false: duration output, true: results compared to base days
		boolean ignoreDates = true; // true for mobilityDashboard, false for simulation input

		Set<String> datesToIgnore = Resources
				.readLines(Resources.getResource("mobilityDatesToIgnore.txt"), StandardCharsets.UTF_8).stream()
				.map(String::toString).collect(Collectors.toSet());
		if (ignoreDates == false)
			datesToIgnore.clear();

		Files.createDirectories(outputFolder);

		writeData(selectedArea, getPercentageResults, selectedBase, anyArea, selectedOutputOption,
				startDateStillUsingBaseDays, datesToIgnore);

		log.info("Done!");

		return 0;
	}

	private void writeData(AnalyseAreas selectedArea, boolean getPercentageResults, BaseDaysForComparison selectedBase,
			String anyArea, AnalyseOptions selectedOutputOption, String startDateStillUsingBaseDays,
			Set<String> datesToIgnore) throws IOException {
		CreateRestrictionsFromSnz snz = new CreateRestrictionsFromSnz();
		snz.setInput(inputFolder);
		List<String> baseDays = Arrays.asList();
		String outputOption = null;

		// sets the selected base days
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
					zipCodesAnyArea.values().iterator().next(), getPercentageResults, baseDays, datesToIgnore);
			break;
		case Berlin:
			HashMap<String, IntSet> zipCodesBerlin = snz.findZipCodesForAnyArea("Berlin");
			snz.writeDataForCertainArea(
					outputFolder.resolve(zipCodesBerlin.keySet().iterator().next() + "SnzData_daily_until.csv"),
					zipCodesBerlin.values().iterator().next(), getPercentageResults, baseDays, datesToIgnore);
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
				snz.writeDataForCertainArea(outputFolder.resolve(district.getKey() + "SnzData_daily_until.csv"),
						district.getValue(), getPercentageResults, baseDays, datesToIgnore);
			break;
		case Germany:
			IntSet zipCodesGER = new IntOpenHashSet();
			for (int i = 0; i <= 99999; i++)
				zipCodesGER.add(i);
			snz.writeDataForCertainArea(outputFolder.resolve("GermanySnzData_daily_until.csv"), zipCodesGER,
					getPercentageResults, baseDays, datesToIgnore);
			break;
		case Test:
			IntSet zipCodesTest = new IntOpenHashSet(List.of(1067));
			snz.writeDataForCertainArea(outputFolder.resolve("TestSnzData_daily_until.csv"), zipCodesTest,
					getPercentageResults, baseDays, datesToIgnore);
			break;
		case Koeln:
			HashMap<String, IntSet> zipCodesAnyCologne = snz.findZipCodesForAnyArea("Köln");
			snz.writeDataForCertainArea(outputFolder.resolve("CologneSnzData_daily_until.csv"),
					zipCodesAnyCologne.values().iterator().next(), getPercentageResults, baseDays, datesToIgnore);
			break;
		case Brandenburg:
			HashMap<String, IntSet> zipCodesAnyBrandenburg = snz.findZipCodesForAnyArea("Brandenburg", true);
			snz.writeDataForCertainArea(outputFolder.resolve("BrandenburgSnzData_daily_until.csv"),
				zipCodesAnyBrandenburg.values().iterator().next(), getPercentageResults, baseDays, datesToIgnore);
			break;

		case Bundeslaender:
			switch (selectedOutputOption) {
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
			snz.writeBundeslandDataForPublic(outputFolder, outputOption, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case Landkreise:
			switch (selectedOutputOption) {
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
			snz.writeLandkreisDataForPublic(outputFolder, outputOption, startDateStillUsingBaseDays, datesToIgnore);
			break;
		case UpdateMobilityDashboardData:
			datesToIgnore.clear();
			outputFolder = Path.of("../public-svn/matsim/scenarios/countries/de/episim/mobilityData/bundeslaender/");
			outputOption = "weekly";
			snz.writeBundeslandDataForPublic(outputFolder, outputOption, startDateStillUsingBaseDays, datesToIgnore);
			outputOption = "weekdays";
			snz.writeBundeslandDataForPublic(outputFolder, outputOption, startDateStillUsingBaseDays, datesToIgnore);
			outputOption = "weekends";
			snz.writeBundeslandDataForPublic(outputFolder, outputOption, startDateStillUsingBaseDays, datesToIgnore);
			outputFolder = Path.of("../public-svn/matsim/scenarios/countries/de/episim/mobilityData/landkreise/");
			outputOption = "weekly";
			snz.writeLandkreisDataForPublic(outputFolder, outputOption, startDateStillUsingBaseDays, datesToIgnore);
			outputOption = "weekdays";
			snz.writeLandkreisDataForPublic(outputFolder, outputOption, startDateStillUsingBaseDays, datesToIgnore);
			outputOption = "weekends";
			snz.writeLandkreisDataForPublic(outputFolder, outputOption, startDateStillUsingBaseDays, datesToIgnore);
			break;
		default:
			break;

		}
	}
}

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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

/**
 * @author: rewert
 * This class reads the SENOZON data for every day. The data is filtered by the
 * zip codes of every area. The base line is always the first day. The results
 * for every day are the percentile of the changes compared to the base.
 */
@CommandLine.Command(
		name = "analyzeSnzData",
		description = "Aggregate snz mobility data."
)
class AnalyzeSnzData implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(AnalyzeSnzData.class);

	@CommandLine.Parameters(defaultValue = "../shared-svn/projects/episim/data/Bewegungsdaten/")
	private Path inputFolder;

	@CommandLine.Option(names = "--output", defaultValue = "output")
	private Path outputFolder;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalyzeSnzData()).execute(args));
	}


	@Override
	public Integer call() throws Exception {

		// zip codes for Germany
		IntSet zipCodesGER = new IntOpenHashSet();
		for (int i = 0; i <= 99999; i++)
			zipCodesGER.add(i);

		// zip codes for Berlin
		IntSet zipCodesBerlin = new IntOpenHashSet();
		for (int i = 10115; i <= 14199; i++)
			zipCodesBerlin.add(i);
		
		IntSet zipCodesBerlinInnenstadt = new IntOpenHashSet();
		for (int i = 10000; i <= 10999; i++)
			zipCodesBerlinInnenstadt.add(i);
		
		IntSet zipCodesBerlinSuedOsten = new IntOpenHashSet();
		for (int i = 12000; i <= 12999; i++)
			zipCodesBerlinSuedOsten.add(i);
		
		IntSet zipCodesBerlinNorden = new IntOpenHashSet();
		for (int i = 13000; i <= 13999; i++)
			zipCodesBerlinNorden.add(i);
		
		IntSet zipCodesBerlinSueWesten = new IntOpenHashSet();
		for (int i = 14000; i <= 14999; i++)
			zipCodesBerlinSueWesten.add(i);
		
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
		IntSet zipCodesHeinsberg = new IntOpenHashSet(List.of(41812, 52538, 52511, 52525, 41836, 52538, 52531, 41849, 41844));

		// zip codes for district "Berchtesgadener Land"
		IntSet zipCodesBerchtesgaden = new IntOpenHashSet(List.of(83317, 83364, 83395, 83404, 83410, 83416, 83435,83451, 83454, 83457, 83458, 83471, 83483, 83486, 83487));
		
		// zip codes for the district "Mannheim"
		IntSet zipCodesMannheim = new IntOpenHashSet(
				List.of(68159, 68161, 68163, 68165, 68167, 68169, 68199, 68219, 68229, 68239, 68259, 68305, 68307, 68309));
		
		// zip codes for the district "Wolfsburg"
		IntSet zipCodesWolfsburg = new IntOpenHashSet(
				List.of(38440, 38442, 38444, 38446, 38448));
		
		// getPercentageResults: set to true if you want percentages compared to the base, if you select false you get the total amounts
		boolean getPercentageResults = true;
		// setBaseIn2018: set true if you use selected days of 2018 as base days
		boolean setBaseIn2018 = false;

		CreateRestrictionsFromSnz snz = new CreateRestrictionsFromSnz();
		snz.setInput(inputFolder);

//		snz.writeDataForCertainArea(outputFolder.resolve("GermanySnzData_daily_until.csv"), zipCodesGER, getPercentageResults, setBaseIn2018);
		snz.writeDataForCertainArea(outputFolder.resolve("BerlinSnzData_daily_until.csv"), zipCodesBerlin, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("MunichSnzData_daily_until.csv"), zipCodesMunich, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("HamburgSnzData_daily_until.csv"), zipCodesHamburg, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("BonnSnzData_daily_until.csv"), zipCodesBonn, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("BerchtesgadenSnzData_daily_until.csv"), zipCodesBerchtesgaden, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("HeinsbergSnzData_daily_until.csv"), zipCodesHeinsberg, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("MannheimSnzData_daily_until.csv"), zipCodesMannheim, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("WolfsburgSnzData_daily_until.csv"), zipCodesWolfsburg, getPercentageResults, setBaseIn2018);
		
		
//		snz.writeDataForCertainArea(outputFolder.resolve("BerlinInnenstadtSnzData_daily_until.csv"), zipCodesBerlinInnenstadt, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("BerlinSuedOstenSnzData_daily_until.csv"), zipCodesBerlinSuedOsten, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("BerlinNordenSnzData_daily_until.csv"), zipCodesBerlinNorden, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("BerlinSueWestenSnzData_daily_until.csv"), zipCodesBerlinSueWesten, getPercentageResults, setBaseIn2018);
		
//		for (Entry<String, IntSet> district : berlinDistricts.entrySet()) 
//			snz.writeDataForCertainArea(outputFolder.resolve(district.getKey() + "SnzData_daily_until.csv"), district.getValue(), getPercentageResults, setBaseIn2018);
		


		log.info("Done!");

		return 0;
	}

}

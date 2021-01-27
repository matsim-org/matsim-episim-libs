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
import java.util.List;
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

		// getPercentageResults: set to true if you want percentages compared to the base, if you select false you get the total amounts
		boolean getPercentageResults = true;
		// setBaseIn2018: set true if you use selected days of 2018 as base days
		boolean setBaseIn2018 = false;

		CreateRestrictionsFromSnz snz = new CreateRestrictionsFromSnz();
		snz.setInput(inputFolder);

//		analyzeDataForCertainArea(zipCodesGER, "Germany", filesWithData,getPercentageResults, setBaseIn2018);
		snz.writeDataForCertainArea(outputFolder.resolve("BerlinSnzData_daily_until.csv"), zipCodesBerlin, getPercentageResults, setBaseIn2018);
//		analyzeDataForCertainArea(zipCodesMunich, "Munich", filesWithData, getPercentageResults, setBaseIn2018);
//		analyzeDataForCertainArea(zipCodesHamburg, "Hamburg", filesWithData, getPercentageResults, setBaseIn2018);
//		analyzeDataForCertainArea(zipCodesBonn, "Bonn", filesWithData, getPercentageResults, setBaseIn2018);
//		analyzeDataForCertainArea(zipCodesBerchtesgaden, "Berchtesgaden", filesWithData, getPercentageResults, setBaseIn2018);
//		analyzeDataForCertainArea(zipCodesHeinsberg, "Heinsberg", filesWithData, getPercentageResults, setBaseIn2018);
		
//		snz.writeDataForCertainArea(outputFolder.resolve("BerlinInnenstadtSnzData_daily_until.csv"), zipCodesBerlinInnenstadt, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("BerlinSuedOstenSnzData_daily_until.csv"), zipCodesBerlinSuedOsten, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("BerlinNordenSnzData_daily_until.csv"), zipCodesBerlinNorden, getPercentageResults, setBaseIn2018);
//		snz.writeDataForCertainArea(outputFolder.resolve("BerlinSueWestenSnzData_daily_until.csv"), zipCodesBerlinSueWesten, getPercentageResults, setBaseIn2018);


		log.info("Done!");

		return 0;
	}

}

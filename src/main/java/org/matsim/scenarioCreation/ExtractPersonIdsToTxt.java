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

package org.matsim.scenarioCreation;


import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * This class writes all personIds from a population file into a .txt, one id per line.
 * In this project, we need this for the use case of Berlin-Snz (as a step 'backwards') in order to have a consistent scenarioCreation process over all use cases.
 * Furthermore, in the given Berlin population file, people use multiple educ_secondary facilities which we want to aggregate and merge.
 *
 * The id list is later taken as the basis for events filtering.
 */
public class ExtractPersonIdsToTxt {

	private static final String INPUT_POPULATION = "../../svn/shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_optimizedPopulation_adults_withoutNetworkInfo.xml.gz";
	private static final String OUTPUT_ID_TXT = "../../svn/shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_optimizedPopulation_adults_idList.txt";

	public static void main(String[] args)  {

		Population population = PopulationUtils.readPopulation(INPUT_POPULATION);

		BufferedWriter writer = IOUtils.getBufferedWriter(OUTPUT_ID_TXT);

		boolean firstLine = true;

			try {
				for(Id<Person> id : population.getPersons().keySet()) {
					if(!firstLine) writer.newLine();
					firstLine = false;
					writer.write(id.toString());
				}
				writer.flush();
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}

}

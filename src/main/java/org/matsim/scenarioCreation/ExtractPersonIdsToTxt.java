/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.scenarioCreation;


import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * This class writes all personIds from a population file into a .txt, one id per line.
 * In this project, we need this for the use case of Berlin-Snz (as a step 'backwards') in order to have a consistent scenarioCreation process over all use cases.
 * Furthermore, in the given Berlin population file, people use multiple educ_secondary facilities which we want to aggregate and merge.
 * <p>
 * The id list is later taken as the basis for events filtering.
 */
public class ExtractPersonIdsToTxt {

	private static final String DEFAULT_INPUT_POPULATION = "../../svn/shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_optimizedPopulation_adults_withoutNetworkInfo.xml.gz";
	private static final String DEFAULT_OUTPUT_ID_TXT = "../../svn/shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_optimizedPopulation_adults_idList.txt";

	public static void main(String[] args) {

		String input = DEFAULT_INPUT_POPULATION;
		String output = DEFAULT_OUTPUT_ID_TXT;

		if(args.length > 0){
			input = args[0];
			output = args[1];
		}

		Population population = PopulationUtils.readPopulation(input);

		BufferedWriter writer = IOUtils.getBufferedWriter(output);

		boolean firstLine = true;

		try {
			for (Id<Person> id : population.getPersons().keySet()) {
				if (!firstLine) writer.newLine();
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

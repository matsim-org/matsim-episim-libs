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

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SearchAgents {
	private static final String workingDir = "../shared-svn/projects/episim/matsim-files/";
	private static final String inputPopulationAdult = workingDir
			+ "snz/Heinsberg/Heinsberg/processed-data/he_snz_adults_population_stayHomePlans.xml.gz";
	private static final String inputPopulationU14 = workingDir
			+ "snz/Heinsberg/Heinsberg/processed-data/he_u14population_noPlans.xml.gz";
	private static final String outIdList = workingDir + "snz/Heinsberg/Heinsberg/episim-input/he_infected_idList.txt";

	private SearchAgents() {
	}

	public static void main(String[] args) {

		Population population = PopulationUtils.readPopulation(inputPopulationAdult);
		Population populationU14 = PopulationUtils.readPopulation(inputPopulationU14);
		for (Person p : populationU14.getPersons().values())
			population.addPerson(p);
		Coord coordHeisberg = MGC.point2Coord(MGC.xy2Point(296479.943046, 5660850.296617));
		List<String> listPersonIDsHeisberg = new ArrayList<String>();
		for (Person p : population.getPersons().values()) {
			double x = (double) p.getAttributes().getAttribute("homeX");
			double y = (double) p.getAttributes().getAttribute("homeY");
			Coord coord2 = MGC.point2Coord(MGC.xy2Point(x, y));
			double distance = CoordUtils.calcProjectedEuclideanDistance(coordHeisberg, coord2);
			if (distance < 300) {
				System.out.println(p.getId().toString() + " homeX: " + x + " homeY " + y + " age: " + p.getAttributes().getAttribute("age"));
				listPersonIDsHeisberg.add(p.getId().toString());
			}
		}
		System.out.println("Anzahl Personen: " + listPersonIDsHeisberg.size());
		FileWriter writer;
		File file;
		file = new File(outIdList);
		try {
			writer = new FileWriter(file, true);
			for (String id : listPersonIDsHeisberg)
				writer.write(id + "\n");
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}

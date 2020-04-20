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

import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordUtils;

public class CreateStayHomePlans {

	private static String INPUT_PLANS = "D:/svn/shared-svn/projects/episim/matsim-files/snz/Munich/processed-data/mu_u14population_noPlans.xml.gz";
	private static String OUTPUT_PLANS = "D:/svn/shared-svn/projects/episim/matsim-files/snz/Munich/processed-data/mu_u14population_stayHomePlans.xml.gz";

	public static void main(String[] args) {

		String input = INPUT_PLANS;
		String output = OUTPUT_PLANS;

		if(args.length > 0){
			input = args[0];
			output = args[1];
		}


		Population population = PopulationUtils.readPopulation(input);

		PopulationFactory factory = population.getFactory();

		population.getPersons().values().parallelStream().forEach(person -> {
			double x = (double) person.getAttributes().getAttribute("homeX");
			double y = (double) person.getAttributes().getAttribute("homeY");

			Plan plan = factory.createPlan();
			plan.addActivity(factory.createActivityFromCoord("home", CoordUtils.createCoord(x,y)));

			person.addPlan(plan);
			person.setSelectedPlan(plan);
		});

		PopulationUtils.writePopulation(population, output);
	}
}

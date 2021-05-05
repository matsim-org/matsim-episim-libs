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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.util.PartialSort;
import org.matsim.contrib.util.distance.DistanceUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacility;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class creates plans for the school population of a scenario and integrates them into the existing adult population.
 *
 * @author smueller, tschlenther
 */

public final class BuildSchoolPlans {

	private static final Logger log = LogManager.getLogger(BuildSchoolPlans.class);

	private static final String INPUT_POPULATION_FILE_DEFAULT = "../../svn/shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/processed-data/he_small_snz_u14population_emptyPlans.xml.gz";

	private static final String INPUT_FACILITIES_FILE_DEFAULT = "../../svn/shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/processed-data/he_small_snz_educationFacilities.txt";

	private static final String OUTPUT_POPULATION_FILE_DEFAULT = "../../svn/shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/processed-data/he_small_snz_u14population_schoolPlans.xml.gz";

	private static final Random RND = new Random(1);

	private BuildSchoolPlans() {
	}

	public static void main(String[] args) throws IOException {

		String inputPopulationFile = INPUT_POPULATION_FILE_DEFAULT;
		String inputFacilitiesFile = INPUT_FACILITIES_FILE_DEFAULT;
		String outputPopulationFile = OUTPUT_POPULATION_FILE_DEFAULT;

		if(args.length > 0){
			inputPopulationFile = args[0];
			inputFacilitiesFile = args[1];
			outputPopulationFile = args[2];
		}

		Population schoolPopulation = PopulationUtils.readPopulation(inputPopulationFile);

		buildSchoolPlans(schoolPopulation, inputFacilitiesFile, null);
		PopulationUtils.writePopulation(schoolPopulation, outputPopulationFile);
	}

	public static void buildSchoolPlans(Population schoolPopulation, String schoolFacilitiesFile, CoordinateTransformation facilityCoordTransformer) throws IOException {
		log.info("start reading school facilities");
		Set<EducFacility> allFacilities = EducFacility.readEducFacilities(schoolFacilitiesFile, facilityCoordTransformer);
		log.info("start building school plans");
		process(schoolPopulation, allFacilities);
	}

	private static void process(Population schoolPopulation, Set<EducFacility> allFacilities) {

		PopulationFactory pf = schoolPopulation.getFactory();

		Set<EducFacility> kigasList = allFacilities.stream().filter(e -> e.isEducKiga()).collect(Collectors.toSet());
		Set<EducFacility> primaryList = allFacilities.stream().filter(e -> e.isEducPrimary()).collect(Collectors.toSet());
		Set<EducFacility> secondaryList = allFacilities.stream().filter(e -> e.isEducSecondary()).collect(Collectors.toSet());

		Counter counter = new Counter("building school plan nr ");
		for (Person person : schoolPopulation.getPersons().values()) {
			counter.incCounter();
			person.getAttributes().putAttribute("subpopulation", "berlin");
			Plan plan = pf.createPlan();
			person.addPlan(plan);
			double age = (int) person.getAttributes().getAttribute("age");
			double homeX = (double) person.getAttributes().getAttribute("homeX");
			double homeY = (double) person.getAttributes().getAttribute("homeY");
			Coord homeCoord = CoordUtils.createCoord(homeX, homeY);
			Activity homeAct1 = pf.createActivityFromCoord("home", homeCoord);
			plan.addActivity(homeAct1);

			if (person.getAttributes().getAttribute("homeId") != null) {
				String facilityIdString = (String) person.getAttributes().getAttribute("homeId");
				Id<ActivityFacility> homeFacilityId = Id.create(facilityIdString, ActivityFacility.class);
				homeAct1.setFacilityId(homeFacilityId);
			}

			homeAct1.setStartTime(0);
			homeAct1.setEndTime(6.5 * 3600 + RND.nextInt(3600));

			if (age < 2) {
				continue;
			}

			String eduActType;

			Set<EducFacility> setToSearchIn;
			if (age > 1 && age <= 5) {
				setToSearchIn = kigasList;
				eduActType = "educ_kiga";
			} else if (age > 5 && age <= 12) {
				setToSearchIn = primaryList;
				eduActType = "educ_primary";
			} else {
				setToSearchIn = secondaryList;
				eduActType = "educ_secondary";
			}

			List<EducFacility> closest10Facilities = findClosestKFacilities(setToSearchIn, homeCoord, 10);

			//get a random facility within 5 km or just take the closest one
			EducFacility facility = closest10Facilities.stream()
					.filter(f -> DistanceUtils.calculateDistance(f.getCoord(), homeCoord) < 5000)
					.findAny()
					.orElse(closest10Facilities.get(0));

			//have to calc distance again :(
			double distance = CoordUtils.calcEuclideanDistance(facility.getCoord(), homeCoord);

			if (distance > 5000)
				log.warn("assigned a " + eduActType + " facility with distance " + distance + " to person " + person);

			Leg leg = pf.createLeg(getLegMode(distance));
			plan.addLeg(leg);

			Activity eduAct = pf.createActivityFromCoord(eduActType, facility.getCoord());
			plan.addActivity(eduAct);
			eduAct.setStartTime(8 * 3600);
			eduAct.setEndTime(13 * 3600 + RND.nextInt(4 * 3600));

			//if person had info about facilities, we also want to use the info in the activie
			//i.e. this is the differentiation between snz and berlin. might be improved later
			if (person.getAttributes().getAttribute("homeId") != null) {
				eduAct.setFacilityId(facility.getId());
			}

			plan.addLeg(leg);

			Activity homeAct2 = pf.createActivityFromCoord("home", homeCoord);
			plan.addActivity(homeAct2);

			if (person.getAttributes().getAttribute("homeId") != null) {
				String facilityIdString = (String) person.getAttributes().getAttribute("homeId");
				Id<ActivityFacility> homeFacilityId = Id.create(facilityIdString, ActivityFacility.class);
				homeAct2.setFacilityId(homeFacilityId);
			}
			homeAct2.setStartTime(14 * 3600); //this does not necessarily correspond to end time of eduAct.. not too bad?

		}
	}

	private static List<EducFacility> findClosestKFacilities(Set<EducFacility> facilitiesSet, Coord homeCoord, int k) {
		return PartialSort.kSmallestElements(k, facilitiesSet.stream(), Comparator.comparingDouble(
				fac -> DistanceUtils.calculateSquaredDistance(homeCoord, fac.getCoord())));
	}

	private static String getLegMode(double distance) {

		if (distance < 1000) {
			return "walk";
		} else if (RND.nextDouble() < 0.8) {
			return "pt";
		} else {
			return "ride";
		}

	}

}

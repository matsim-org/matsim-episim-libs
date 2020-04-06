/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.contrib.util.PartialSort;
import org.matsim.contrib.util.distance.DistanceUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacility;

/**
 * This class creates plans for the school population of a scenario and integrates them into the existing adult population
* @author smueller, tschlenther
*/

public class SchoolPopulationDestinationChoiceAndIntegration {

	private static final Logger log = Logger.getLogger(SchoolPopulationDestinationChoiceAndIntegration.class);

	private static final String inputPopulationFile = "../../svn/shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_u14population_noPlans.xml.gz";

	private static final String inputFacilitiesFile = "../../svn/shared-svn/projects/episim/matsim-files/snz/Berlin/scenario-input/be_educFacilities_optimated.txt";

	private static final String originalPopulationFile = "../../svn/shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_optimizedPopulation_adults_withoutNetworkInfo.xml.gz";

	private static final String outputPopulationFile = "../../svn/shared-svn/projects/episim/matsim-files/snz/Berlin/scenario-input/be_snz_plans_25pct.xml.gz";

	private final static Random rnd = new Random(1);

	private static List<EducFacility> educList = new ArrayList<>();

	public static void main(String[] args) throws IOException {

		Population schoolPopulation = PopulationUtils.readPopulation(inputPopulationFile);

		run(schoolPopulation, originalPopulationFile, inputFacilitiesFile, null,  outputPopulationFile);
	}

	public static void run(Population schoolPopulation, String adultPopulationFile, String schoolFacilitiesFile, CoordinateTransformation facilityCoordTransformer, String outputPopulationFile) throws IOException {
		log.info("start reading school facilities");
		readEducFacilites(schoolFacilitiesFile, facilityCoordTransformer);
		log.info("start building school plans");
		buildSchoolPlans(schoolPopulation);

		Population originalPopulation = PopulationUtils.readPopulation(adultPopulationFile);

		log.info("start integrating school population into original population");
		schoolPopulation.getPersons().values().forEach(person -> originalPopulation.addPerson(person));

		PopulationUtils.writePopulation(originalPopulation, outputPopulationFile);
	}

	private static void readEducFacilites(String educFacilitiesFile, CoordinateTransformation transformation) throws IOException {

		BufferedReader reader = new BufferedReader(new FileReader(educFacilitiesFile));

		int ii = -1;

		for (String line = reader.readLine(); line != null; line = reader.readLine()) {

			ii++;

			if (ii == 0) {
				continue;
			}

			String[] parts = line.split("\t");

			Id<EducFacility> id = Id.create(parts[0], EducFacility.class);
			double x = Double.parseDouble(parts[1]);
			double y = Double.parseDouble(parts[2]);

			String educKiga = parts[3];
			boolean isEducKiga = false;
			if (!educKiga.equals("0")) {
				isEducKiga = true;
			}

			String educPrimary = parts[4];
			boolean isEducPrimary = false;
			if (!educPrimary.equals("0.0")) {
				isEducPrimary = true;
			}

			String educSecondary = parts[5];
			boolean isEducSecondary = false;
			if (!educSecondary.equals("0.0")) {
				isEducSecondary = true;
			}

			Coord coord = CoordUtils.createCoord(x, y);

			if(transformation != null){
				coord = transformation.transform(coord);
			}

			EducFacility educFacility = new EducFacility(id, coord, isEducKiga, isEducPrimary, isEducSecondary);

			educList.add(educFacility);
		}

		reader.close();

	}

	private static void buildSchoolPlans(Population schoolPopulation) {

		PopulationFactory pf = schoolPopulation.getFactory();

		List<EducFacility> kigasList = educList.stream().filter(e -> e.isEducKiga()).collect(Collectors.toList());
		List<EducFacility> primaryList = educList.stream().filter(e -> e.isEducPrimary()).collect(Collectors.toList());
		List<EducFacility> secondaryList = educList.stream().filter(e -> e.isEducSecondary()).collect(Collectors.toList());

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

			if(person.getAttributes().getAttribute("homeId") != null){
				String facilityIdString = (String) person.getAttributes().getAttribute("homeId");
				Id<ActivityFacility> homeFacilityId = Id.create(facilityIdString, ActivityFacility.class);
				homeAct1.setFacilityId(homeFacilityId);
			}

			homeAct1.setStartTime(0);
			homeAct1.setEndTime(6.5 * 3600 + rnd.nextInt(3600));

			if (age < 2) {
				continue;
			}

			String eduActType;

			List<EducFacility> listToSearchIn;
			if (age > 1 && age <= 5) {
				listToSearchIn = kigasList;
				eduActType = "educ_kiga";
			} else	if (age > 5 && age <= 12) {
				listToSearchIn = primaryList;
				eduActType = "educ_primary";
			} else {
				listToSearchIn = secondaryList;
				eduActType = "educ_secondary";
			}

			List<EducFacility> closest10Facilities = findClosestKFacilities(listToSearchIn, homeCoord, 10);

			//get a random facility within 5 km or just take the closest one
			EducFacility facility = closest10Facilities.stream()
					.filter(f -> DistanceUtils.calculateDistance(f.getCoord(), homeCoord) < 5000)
					.findAny()
					.orElse(closest10Facilities.get(0));

			//have to calc distance again :(
			double distance = CoordUtils.calcEuclideanDistance(facility.getCoord(), homeCoord);

			if(distance > 5000)
			log.warn("assigned a " + eduActType + " facility with distance " + distance + " to person " + person);

			Leg leg = pf.createLeg(getLegMode(distance));
			plan.addLeg(leg);

			Activity eduAct = pf.createActivityFromCoord(eduActType, facility.getCoord());
			plan.addActivity(eduAct);
			eduAct.setStartTime(8 * 3600);
			eduAct.setEndTime(13 * 3600 + rnd.nextInt(4 * 3600));

			//if person had info about facilities, we also want to use the info in the activie
			//i.e. this is the differentiation between snz and berlin. might be improved later
			if (person.getAttributes().getAttribute("homeId") != null){
				eduAct.setFacilityId(facility.getId());
			}

			plan.addLeg(leg);

			Activity homeAct2 = pf.createActivityFromCoord("home", homeCoord);
			plan.addActivity(homeAct2);

			if(person.getAttributes().getAttribute("homeId") != null){
				String facilityIdString = (String) person.getAttributes().getAttribute("homeId");
				Id<ActivityFacility> homeFacilityId = Id.create(facilityIdString, ActivityFacility.class);
			}
			homeAct2.setStartTime(14 * 3600); //this does not necessarily correspond to end time of eduAct.. not too bad?

		}
	}

	private static List<EducFacility> findClosestKFacilities(List<EducFacility> kigasList, Coord homeCoord, int k) {
		return PartialSort.kSmallestElements(k, kigasList.stream(),
				fac -> DistanceUtils.calculateSquaredDistance(homeCoord, fac.getCoord()));
	}

	private static String getLegMode(double distance) {

		if (distance < 1000) {
			return "walk";
		}
		else if(rnd.nextDouble() < 0.8) {
			return "pt";
		}
		else {
			return "ride";
		}

	}

}

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


package org.matsim.schools;

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
import org.matsim.contrib.util.StraightLineKnnFinder;
import org.matsim.contrib.util.distance.DistanceUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacility;

/**
 * This class creates plans for the school population of a scenario and integrates them into the existing adult population
* @author smueller
*/

public class CreateSchoolPopulation {

	private static final Logger log = Logger.getLogger(CreateSchoolPopulation.class);

	private static final String workingDir = "../shared-svn/projects/episim/matsim-files/snz/Berlin/";
	
	private static final String inputPopulationFile = workingDir + "be_population_fromPopulationAttributes_BerlinOnly.xml.gz";
	
	private static final String originalPopulationFile = workingDir + "be_optimizedPopulation_withoutNetworkInfo.xml.gz";
	
	private static final String outputPopulationFile = workingDir + "be_population_fromPopulationAttributes_BerlinOnly_withPlans.xml.gz";

	private static final double SCHOOL_POP_SAMPLE_SIZE = 0.25;

	private final static Random rnd = new Random(1);
	
	private static List<EducFacility> educList = new ArrayList<>();

	public static void main(String[] args) throws IOException {

		Population schoolPopulation = PopulationUtils.readPopulation(inputPopulationFile);

		run(schoolPopulation, SCHOOL_POP_SAMPLE_SIZE, originalPopulationFile, workingDir + "educFacilities_optimated.txt", outputPopulationFile);
	}

	public static void run(Population schoolPopulation, double schoolPopSampleSize, String adultPopulationFile, String schoolFacilitiesFile, String outputPopulationFile) throws IOException {
		if(schoolPopSampleSize > 1.0 || schoolPopSampleSize < 0.){
			throw new IllegalArgumentException("unvalid sample size for school population : " + schoolPopSampleSize);
		}
		log.info("start reading school facilities");
		readEducFacilites(schoolFacilitiesFile);
		log.info("start building school plans");
		buildSchoolPlans(schoolPopulation);


		Population originalPopulation = PopulationUtils.readPopulation(adultPopulationFile);

		log.info("start integrating school population into original population");
		integrateIntoOriginalPopulation(schoolPopulation, originalPopulation, schoolPopSampleSize);

		PopulationUtils.writePopulation(originalPopulation, outputPopulationFile);
	}

	private static void integrateIntoOriginalPopulation(Population population, Population originalPopulation,
			double sample) {
		int ii = 0;

		log.info("nr of persons in school population : " + population.getPersons().size());
		for (Person person : population.getPersons().values()) {
			if (rnd.nextDouble() < sample) {
				originalPopulation.addPerson(person);
				ii++;
			}
		}
		log.info("integrated " + ii + " students into original population ");

	}

	private static void readEducFacilites(String educFacilitiesFile) throws IOException {
		
		BufferedReader reader = new BufferedReader(new FileReader(educFacilitiesFile));
		
		int ii = -1;
		
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			
			ii++;
			
			if (ii == 0) {
				continue;
			}
		    
			String[] parts = line.split("\t");
			
			Id<ActivityFacility> id = Id.create(parts[0], ActivityFacility.class);
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
		
			EducFacility educFacility = new EducFacility(id, x, y, isEducKiga, isEducPrimary, isEducSecondary);
			
			educList.add(educFacility);
		}
		
		reader.close();
		
	}

	private static void buildSchoolPlans(Population schoolPopulation) {
		
		PopulationFactory pf = schoolPopulation.getFactory();

		List<EducFacility> kigasList = educList.stream().filter(e -> e.isEducKiga).collect(Collectors.toList());
		List<EducFacility> primaryList = educList.stream().filter(e -> e.isEducPrimary).collect(Collectors.toList());
		List<EducFacility> secondaryList = educList.stream().filter(e -> e.isEducSecondary).collect(Collectors.toList());

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
			
			EducFacility educFacility = null;
			String eduActType = null;
			boolean foundEducFacility = false;
			double distance;


			int cnt = 0;
			do {
				cnt ++;
				foundEducFacility = true;

				if (age > 1 && age <= 5) {
					educFacility = kigasList.get(rnd.nextInt(kigasList.size()));
					eduActType = "educ_kiga";
				}
				if (age > 5 && age <= 12) {
					educFacility = primaryList.get(rnd.nextInt(primaryList.size()));
					eduActType = "educ_primary";
				}

				if (age > 12) {
					educFacility = secondaryList.get(rnd.nextInt(secondaryList.size()));
					eduActType = "educ_secondary";
				}

				// to do: make this better (Gravitationsmodell?)
				distance = CoordUtils.calcEuclideanDistance(educFacility.getCoord(), homeCoord);
				if (distance > 5000) {
					foundEducFacility = false;
					if (cnt > 1000) {
						throw new RuntimeException("could not find facility for person " + person + "\t and homeCoord = " + homeCoord);
					}
				}

			}while(!foundEducFacility);

			Leg leg = pf.createLeg(getLegMode(distance));
			plan.addLeg(leg);
			
			Activity eduAct = pf.createActivityFromCoord(eduActType, educFacility.getCoord());
			plan.addActivity(eduAct);
			eduAct.setStartTime(8 * 3600);
			eduAct.setEndTime(13 * 3600 + rnd.nextInt(4 * 3600));
			eduAct.setFacilityId(educFacility.getId());
			
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
	
	private static class EducFacility {
		private Id<ActivityFacility> id;
		private Coord coord;
		private boolean isEducKiga;
		private boolean isEducPrimary;
		private boolean isEducSecondary;
		private double noOfPupils = 0;

		EducFacility(Id<ActivityFacility> id, double x, double y, boolean isEducKiga, boolean isEducPrimary, boolean isEducSecondary) {
			this.setId(id);
			this.setCoord(CoordUtils.createCoord(x, y));
			this.setEducKiga(isEducKiga);
			this.setEducPrimary(isEducPrimary);
			this.setEducSecondary(isEducSecondary);
		}
		public Id<ActivityFacility> getId() {
			return id;
		}
		public void setId(Id<ActivityFacility> id) {
			this.id = id;
		}
		
		public boolean isEducKiga() {
			return isEducKiga;
		}
		public void setEducKiga(boolean isEducKiga) {
			this.isEducKiga = isEducKiga;
		}
		public boolean isEducPrimary() {
			return isEducPrimary;
		}
		public void setEducPrimary(boolean isEducPrimary) {
			this.isEducPrimary = isEducPrimary;
		}
		public boolean isEducSecondary() {
			return isEducSecondary;
		}
		public void setEducSecondary(boolean isEducSecondary) {
			this.isEducSecondary = isEducSecondary;
		}
		public Coord getCoord() {
			return coord;
		}
		public void setCoord(Coord coord) {
			this.coord = coord;
		}

	}

}

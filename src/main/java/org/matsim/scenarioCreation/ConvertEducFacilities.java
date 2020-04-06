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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.misc.StringUtils;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * Class uses the given education facility file. We only need the kindergarten
 * and primary school facilities, thats why they are filtered. Afterwards all
 * facilities are connected to one facility when they have a distance of maximum
 * 100m between each other, because the input data has information about every
 * building and one school can have different buildings, but we want them as one
 * facility.
 *
 * @author rewert
 */

public class ConvertEducFacilities {
	static final Logger log = Logger.getLogger(ConvertEducFacilities.class);

	private static final String workingDir = "../shared-svn/projects/episim/matsim-files/snz/";
	private static final String pathOfEduFacilities = workingDir + "educFacilities.txt";
	private static final String outputFileDir = workingDir + "educFacilities_optimated.txt";
	private static final String pathinputPopulation = workingDir
			+ "population_fromPopulationAttributes_BerlinOnly.xml.gz";
	private static final String pathOutputPopulation = workingDir
			+ "population_fromPopulationAttributes_BerlinOnly_V2.xml.gz";

	public static void main(String[] args) throws IOException {

		Multimap<String, String[]> inputFile = ArrayListMultimap.create();
		Multimap<String, String[]> outputFile = ArrayListMultimap.create();
		HashMap<String, Coord> coordsOfFacilities = new HashMap<String, Coord>();

		log.info("Read inputfile...");
		readFile(inputFile);
		createPointsOfFacilities(coordsOfFacilities, inputFile);
		findNearFacilitiesWithSameType(coordsOfFacilities, inputFile, outputFile);
		Population population = PopulationUtils.readPopulation(pathinputPopulation);
		findEducFacilityForEachPerson(population, coordsOfFacilities, outputFile);
	//	PopulationUtils.writePopulation(population, pathOutputPopulation);
		writeOutputFile(outputFile);
		log.info("Finished!");
	}

	private static void findEducFacilityForEachPerson(Population population, HashMap<String, Coord> coordsOfFacilities,
			Multimap<String, String[]> outputFile) {

		for (Person singlePerson : population.getPersons().values()) {
			int age = (int) singlePerson.getAttributes().getAttribute("age");

			if (0 <= age && age <= 1) {
				// stay at home
				// TODO
			}
			if (2 <= age && age <= 5) {
				double homeX = (double) singlePerson.getAttributes().getAttribute("homeX");
				double homeY = (double) singlePerson.getAttributes().getAttribute("homeY");
				Coord coord = MGC.point2Coord(MGC.xy2Point(homeX, homeY));
				String nearestFacilityId = findNearestFacility(coord, coordsOfFacilities, outputFile, 3);
				// TODO

			}
			if (6 <= age && age <= 12) {
				double homeX = (double) singlePerson.getAttributes().getAttribute("homeX");
				double homeY = (double) singlePerson.getAttributes().getAttribute("homeY");
				Coord coord = MGC.point2Coord(MGC.xy2Point(homeX, homeY));
				String nearestFacilityId = findNearestFacility(coord, coordsOfFacilities, outputFile, 4);
				// TODO
			}
			if (13 <= age && age <= 14) {
				// in existing educ_facilities
				// TODO
			}
		}
	}

	private static String findNearestFacility(Coord coord, HashMap<String, Coord> coordsOfFacilities,
			Multimap<String, String[]> outputFile, int facilityType) {

		HashMap<String, Double> facilityWithMinimalDistance = new HashMap<String, Double>();
		for (String[] facilityId : outputFile.values()) {
			facilityWithMinimalDistance.clear();
			facilityWithMinimalDistance.put("noFacility", Double.MAX_VALUE);
			if (Double.parseDouble(facilityId[facilityType]) > 0) {
				Coord facilityCoord2 = coordsOfFacilities.get(facilityId[0]);
				double distance = CoordUtils.calcProjectedEuclideanDistance(coord, facilityCoord2);
				if (distance < facilityWithMinimalDistance.values().iterator().next()) {
					facilityWithMinimalDistance.clear();
					facilityWithMinimalDistance.put(facilityId[0], distance);
				}
			}
		}

		return facilityWithMinimalDistance.keySet().iterator().next();
	}

	private static void readFile(Multimap<String, String[]> inputFile) throws IOException {

		FileReader fileReader = new FileReader(pathOfEduFacilities);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String row = bufferedReader.readLine();
		String[] values = null;
		int countFacilities = 0;
		boolean headlines = true;

		while (row != null && row != "") {
			if (row != null && row != "") {
				values = StringUtils.explode(row, '\t');
				if (values.length > 1 && headlines == false) {
					countFacilities++;
					if (Double.parseDouble(values[3]) > 0 || Double.parseDouble(values[4]) > 0)
						inputFile.put(values[0], values);
				}
				if (values.length > 1 && headlines == true) {
					headlines = false;
				}
			}
			row = bufferedReader.readLine();
		}
		bufferedReader.close();
		fileReader.close();
		log.info("Read input-Facilities: " + countFacilities);
		log.info("Amount of needed facilities (Kita or primary school): " + inputFile.size());
	}

	private static void createPointsOfFacilities(HashMap<String, Coord> pointsOf,
			Multimap<String, String[]> inputFile) {

		log.info("Create Point for each facility...");
		for (String[] facilityInformation : inputFile.values()) {
			Point p;
			double xCoordinate = Double.parseDouble(facilityInformation[1]);
			double yCoordinate = Double.parseDouble(facilityInformation[2]);
			p = MGC.xy2Point(xCoordinate, yCoordinate);
			Coord coord = MGC.point2Coord(p);
			pointsOf.put(facilityInformation[0], coord);
		}
	}

	private static void findNearFacilitiesWithSameType(HashMap<String, Coord> coordsOfFacilities,
			Multimap<String, String[]> inputFile, Multimap<String, String[]> outputFile) {
		List<String> facilitiesToConnect = new ArrayList<String>();
		List<String> facilitiesNotToCheck = new ArrayList<String>();
		int numberOfConections = 0;

		log.info("Find near facilities and connect them...");
		log.info("Amount facilities (input): " + inputFile.size());

		for (String[] typeFacility1 : inputFile.values()) {

			String facilityId = typeFacility1[0];
			if (facilitiesNotToCheck.contains(facilityId) == false) {
				facilitiesNotToCheck.add(facilityId);
				Coord facilityCoord = coordsOfFacilities.get(facilityId);
				for (String[] typeFacility2 : inputFile.values()) {
					String facilityId2 = typeFacility2[0];
					if (facilitiesNotToCheck.contains(facilityId2) == false) {
						Coord facilityCoord2 = coordsOfFacilities.get(facilityId2);
						double distance = CoordUtils.calcProjectedEuclideanDistance(facilityCoord, facilityCoord2);
						if (distance < 100) {
							if ((Double.parseDouble(typeFacility1[3]) > 0 && Double.parseDouble(typeFacility2[3]) > 0)
									|| (Double.parseDouble(typeFacility1[4]) > 0
											&& Double.parseDouble(typeFacility2[4]) > 0)) {
								facilitiesToConnect.add(facilityId2);
								facilitiesNotToCheck.add(facilityId2);
								coordsOfFacilities.remove(facilityId2);
								numberOfConections++;
							}
						}
					}
				}
				double amountOfFacilitySquareKita = Double.parseDouble(typeFacility1[3]);
				double amountOfFacilitySquarePrimarySchool = Double.parseDouble(typeFacility1[4]);

				for (String facilityToAdd : facilitiesToConnect) {
					for (String[] newtypeFacility1 : inputFile.values()) {
						if (newtypeFacility1[0].contains(facilityToAdd)) {
							amountOfFacilitySquareKita = amountOfFacilitySquareKita
									+ Double.parseDouble(newtypeFacility1[3]);
							amountOfFacilitySquarePrimarySchool = amountOfFacilitySquarePrimarySchool
									+ Double.parseDouble(newtypeFacility1[4]);
							break;
						}
					}
				}
				String[] values = new String[5];

				values[0] = typeFacility1[0];
				values[1] = typeFacility1[1];
				values[2] = typeFacility1[2];
				values[3] = Double.toString(amountOfFacilitySquareKita);
				values[4] = Double.toString(amountOfFacilitySquarePrimarySchool);

				outputFile.put(facilityId, values);
				facilitiesToConnect.clear();
			}
		}

		log.info("Amount facilities: " + outputFile.size());
		log.info("Amount of integrated facilities: " + numberOfConections);
		log.info("Finished connecting facilities!");

	}

	private static void writeOutputFile(Multimap<String, String[]> outputFile) {

		FileWriter writer;
		File file;
		file = new File(outputFileDir);
		try {
			writer = new FileWriter(file, true);
			writer.write("id\tx\ty\teduc_kiga\teduc_primary\n");
			for (String[] facilityId : outputFile.values()) {
				writer.write(facilityId[0] + "\t" + facilityId[1] + "\t" + facilityId[2] + "\t"
						+ Double.parseDouble(facilityId[3]) + "\t" + Double.parseDouble(facilityId[4]) + "\n");
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.info("Wrote result file under " + outputFileDir);
	}
}

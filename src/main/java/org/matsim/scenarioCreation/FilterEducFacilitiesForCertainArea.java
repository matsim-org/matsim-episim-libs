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
import java.util.Collection;
import java.util.List;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacility;
import org.opengis.feature.simple.SimpleFeature;

/**
 * This class filters all educational facilities for a certain area and transforms coordinates, if necessary.
 * It is also possible to aggregate facilities having a distance of less than 100 meters between each other, so
 * that they are used as one facility.
 *
 * @author rewert
 */

public class FilterEducFacilitiesForCertainArea {

	static final Logger log = Logger.getLogger(FilterEducFacilitiesForCertainArea.class);

	private static final String workingDir = "../../svn/shared-svn/projects/episim/matsim-files/";
	private static final String pathOfEduFacilitiesGER = workingDir + "snz/Deutschland/de_facilities.education.xy";
	private static final String ShapeFile = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/input/shapefiles/2016/gemeinden_Planungsraum_GK4.shp";;
	private static final String outputEducFileDir = workingDir + "open_berlin/input/educFacilities.txt";
	private static List<EducFacility> educList = new ArrayList<>();
	private static List<EducFacility> educListNewArea = new ArrayList<>();
	private static List<EducFacility> educListNewAreaForOutput = new ArrayList<>();

	public static void main(String[] args) throws IOException {

		boolean coordToTransform = true;
		boolean aggregateFacilities = true;

		CoordinateTransformation transformation = null;
		if(coordToTransform){
			TransformationFactory.getCoordinateTransformation("EPSG:25832", TransformationFactory.DHDN_GK4);
		}

		EducFacilities.readEducFacilites(pathOfEduFacilitiesGER, transformation);

		Collection<SimpleFeature> shapefileCertainArea = ShapeFileReader.getAllFeatures(ShapeFile);

		educList.forEach((educFacility) -> filterEducFacilitiesForCertainArea(educFacility, shapefileCertainArea));
		log.info("Found facilities in certain area: " + educListNewArea.size());
		if (aggregateFacilities)
			findNearFacilitiesWithSameType();
		else
			educListNewAreaForOutput.addAll(educListNewArea);
		writeOutputFile();
	}

	private static void filterEducFacilitiesForCertainArea(EducFacility educFacility,
			Collection<SimpleFeature> shapefileCertainArea) {
		for (SimpleFeature singleFeature : shapefileCertainArea) {
			Geometry geometryOfCertainArea = (Geometry) singleFeature.getDefaultGeometry();
			if (geometryOfCertainArea.contains(MGC.coord2Point(educFacility.getCoord())))
				educListNewArea.add(educFacility);
		}

	}

	private static void findNearFacilitiesWithSameType() {
		List<String> facilitiesNotToCheck = new ArrayList<String>();
		int numberOfConections = 0;

		log.info("Find near facilities and connect them...");
		log.info("Amount facilities (input): " + educListNewArea.size());

		Counter counter = new Counter("aggregate status - dealing facility nr =  ");

		for (EducFacility educFacility1 : educListNewArea) {
			counter.incCounter();
			boolean iskiga = educFacility1.isEducKiga();
			boolean isPrimary = educFacility1.isEducPrimary();
			boolean isSecondary = educFacility1.isEducSecondary();
			String facilityId = educFacility1.getId().toString();
			if ( ! facilitiesNotToCheck.contains(facilityId)) {
				facilitiesNotToCheck.add(facilityId);
				Coord facilityCoord = educFacility1.getCoord();
				for (EducFacility educFacility2 : educListNewArea) {
					String facilityId2 = educFacility2.getId().toString();
					if ( ! facilitiesNotToCheck.contains(facilityId2)) {
						Coord facilityCoord2 = educFacility2.getCoord();
						double distance = CoordUtils.calcProjectedEuclideanDistance(facilityCoord, facilityCoord2);
						if (distance < 100) {
							if (educFacility1.isEducKiga() == educFacility2.isEducKiga()
									|| educFacility1.isEducPrimary() == educFacility2.isEducPrimary()
									|| educFacility1.isEducSecondary() == educFacility2.isEducSecondary()) {
								if (iskiga || educFacility2.isEducKiga())
									iskiga = true;
								if (isPrimary || educFacility2.isEducPrimary())
									isPrimary = true;
								if (isSecondary || educFacility2.isEducSecondary())
									isSecondary = true;
								facilitiesNotToCheck.add(facilityId2);
								educFacility1.addContainedEducFacility(educFacility2.getId());
								numberOfConections++;
							}
						}
					}
				}

				educFacility1.setEducKiga(iskiga);
				educFacility1.setEducPrimary(isPrimary);
				educFacility1.setEducSecondary(isSecondary);
				educListNewAreaForOutput.add(educFacility1);
			}
		}

		log.info("Amount facilities after aggregation: " + educListNewAreaForOutput.size());
		log.info("Amount of integrated facilities: " + numberOfConections);
		log.info("Finished connecting facilities!");

	}

	private static void writeOutputFile() {

		FileWriter writer;
		File file;
		file = new File(outputEducFileDir);
		try {
			writer = new FileWriter(file, true);
			writer.write("id\tx\ty\teduc_kiga\teduc_primary\teduc_secondary\tmergedFacilityIds\n");
			for (EducFacility educFacility : educListNewAreaForOutput) {
				int isKiga = 0;
				int isPrimary = 0;
				int isSecondary = 0;
				String mergedFacilites = "";
				if (educFacility.isEducKiga())
					isKiga = 1;
				if (educFacility.isEducPrimary())
					isPrimary = 1;
				if (educFacility.isEducSecondary())
					isSecondary = 1;
				for(Id<ActivityFacility> otherFac : educFacility.getContainedFacilities()){
					mergedFacilites += otherFac.toString() + ";";
				}
				writer.write(educFacility.getId() + "\t" + educFacility.getCoord().getX() + "\t"
						+ educFacility.getCoord().getY() + "\t" + isKiga + "\t" + isPrimary + "\t" + isSecondary
						+ "\t" + mergedFacilites + "\n");
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.info("Wrote result file under " + outputEducFileDir);
	}

}

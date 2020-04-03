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
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.facilities.ActivityFacility;
import org.opengis.feature.simple.SimpleFeature;

/**
 * This class filters all educFacilities for a certain area. If necessary a
 * coordinate transformation is possible. It is also possible to aggregate
 * facilities having a distance of less than 100 meters between each other, so
 * that they are used as one facility. The shapefile can have different geometries.
 * 
 * @author rewert
 */

public class FilterEducFacilitisForCertainArea {

	static final Logger log = Logger.getLogger(AnalyzeU14Population.class);

	private static final String workingDir = "../shared-svn/projects/episim/matsim-files/";
	private static final String pathOfEduFacilitiesGER = workingDir + "snz/Deutschland/de_facilities.education.xy";
	private static final String ShapeFile = "../shared-svn/studies/countries/de/open_berlin_scenario/input/shapefiles/2016/gemeinden_Planungsraum_GK4.shp";;
	private static final String outputEducFileDir = workingDir + "open_berlin/input/educFacilities.txt";
	private static List<EducFacility> educList = new ArrayList<>();
	private static List<EducFacility> educListNewArea = new ArrayList<>();
	private static List<EducFacility> educListNewAreaForOutput = new ArrayList<>();

	public static void main(String[] args) throws IOException {

		boolean coordToTransform = true;
		boolean aggregateFacilities = true;
		readEducFacilityFile(coordToTransform);

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

	private static void readEducFacilityFile(boolean coordToTransform) throws IOException {

		log.info("Read inputfile...");

		BufferedReader reader = new BufferedReader(new FileReader(pathOfEduFacilitiesGER));

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

			String educKiga = parts[7];
			boolean isEducKiga = false;
			if (!educKiga.equals("0.0")) {
				isEducKiga = true;
			}

			String educPrimary = parts[8];
			boolean isEducPrimary = false;
			if (!educPrimary.equals("0.0")) {
				isEducPrimary = true;
			}

			String educSecondary = parts[9];
			boolean isEducSecondary = false;
			if (!educSecondary.equals("0.0")) {
				isEducSecondary = true;
			}

			EducFacility educFacility = new EducFacility(id, x, y, isEducKiga, isEducPrimary, isEducSecondary);

			if (coordToTransform == true)
				educFacility.setCoord(coordTransformation(educFacility.getCoord()));
			educList.add(educFacility);
		}
		reader.close();
		log.info("Read input-Facilities: " + educList.size());

	}

	private static Coord coordTransformation(Coord coord) {

		return TransformationFactory.getCoordinateTransformation("EPSG:25832", TransformationFactory.DHDN_GK4)
				.transform(coord);
	}

	private static void findNearFacilitiesWithSameType() {
		List<String> facilitiesNotToCheck = new ArrayList<String>();
		int numberOfConections = 0;

		log.info("Find near facilities and connect them...");
		log.info("Amount facilities (input): " + educListNewArea.size());

		for (EducFacility educFacility1 : educListNewArea) {
			boolean iskiga = educFacility1.isEducKiga();
			boolean isPrimary = educFacility1.isEducPrimary();
			boolean isSecondary = educFacility1.isEducSecondary();
			String facilityId = educFacility1.getId().toString();
			if (facilitiesNotToCheck.contains(facilityId) == false) {
				facilitiesNotToCheck.add(facilityId);
				Coord facilityCoord = educFacility1.getCoord();
				for (EducFacility educFacility2 : educListNewArea) {
					String facilityId2 = educFacility2.getId().toString();
					if (facilitiesNotToCheck.contains(facilityId2) == false) {
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
								numberOfConections++;
							}
						}
					}
				}

				EducFacility educFacility = new EducFacility(educFacility1.getId(), educFacility1.getCoord().getX(),
						educFacility1.getCoord().getY(), iskiga, isPrimary, isSecondary);
				educListNewAreaForOutput.add(educFacility);
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
			writer.write("id\tx\ty\teduc_kiga\teduc_primary\teduc_secondary\n");
			for (EducFacility educFacility : educListNewAreaForOutput) {
				int isKiga = 0;
				int isPrimary = 0;
				int isSecondary = 0;
				if (educFacility.isEducKiga)
					isKiga = 1;
				if (educFacility.isEducPrimary)
					isPrimary = 1;
				if (educFacility.isEducSecondary)
					isSecondary = 1;
				writer.write(educFacility.getId() + "\t" + educFacility.getCoord().getX() + "\t"
						+ educFacility.getCoord().getY() + "\t" + isKiga + "\t" + isPrimary + "\t" + isSecondary
						+ "\n");
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.info("Wrote result file under " + outputEducFileDir);
	}

	private static class EducFacility {
		private Id<ActivityFacility> id;
		private Coord coord;
		private boolean isEducKiga;
		private boolean isEducPrimary;
		private boolean isEducSecondary;
		private double noOfPupils = 0;

		EducFacility(Id<ActivityFacility> id, double x, double y, boolean isEducKiga, boolean isEducPrimary,
				boolean isEducSecondary) {
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

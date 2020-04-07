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

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.facilities.ActivityFacility;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;


class EducFacilities {

	private static Logger log = Logger.getLogger(EducFacilities.class);

	static Set<EducFacility> readEducFacilites(String educFacilitiesFile, CoordinateTransformation transformation) throws IOException {
		log.info("Start to read educFacilities file " + educFacilitiesFile);

		Set<EducFacility> educFacilities = new HashSet<>();
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

			Coord coord = CoordUtils.createCoord(x, y);

			if (transformation != null) {
				coord = transformation.transform(coord);
			}

			EducFacility educFacility = new EducFacility(id, coord, isEducKiga, isEducPrimary, isEducSecondary);

			if (parts.length >= 7) {
				if (!parts[6].equals("")) {
					String[] containedFacilities;
					containedFacilities = parts[6].split(";");
					for (String containedFacility : containedFacilities) {
						educFacility.addContainedEducFacility(Id.create(containedFacility, ActivityFacility.class));
					}
				}

			}

			educFacilities.add(educFacility);
		}
		reader.close();

		log.info("Done with reading...");
		log.info("number of EducFacilities = " + educFacilities.size());
		return educFacilities;
	}

}

class EducFacility {
	private Id<ActivityFacility> id;
	private Coord coord;
	private boolean isEducKiga;
	private boolean isEducPrimary;
	private boolean isEducSecondary;
	private double noOfPupils = 0;

	//contains id s of other EducFacilities which are deleted in the process of aggregation
	private Set<Id<ActivityFacility>> containedFacilities = new HashSet<>();

	EducFacility(Id<ActivityFacility> id, Coord coord, boolean isEducKiga, boolean isEducPrimary,
				 boolean isEducSecondary) {
		this.setId(id);
		this.setCoord(coord);
		this.setEducKiga(isEducKiga);
		this.setEducPrimary(isEducPrimary);
		this.setEducSecondary(isEducSecondary);
	}

	boolean isEducKiga() {
		return isEducKiga;
	}

	void setEducKiga(boolean isEducKiga) {
		this.isEducKiga = isEducKiga;
	}

	boolean isEducPrimary() {
		return isEducPrimary;
	}

	void setEducPrimary(boolean isEducPrimary) {
		this.isEducPrimary = isEducPrimary;
	}

	boolean isEducSecondary() {
		return isEducSecondary;
	}

	void setEducSecondary(boolean isEducSecondary) {
		this.isEducSecondary = isEducSecondary;
	}

	Coord getCoord() {
		return coord;
	}

	void setCoord(Coord coord) {
		this.coord = coord;
	}

	Set<Id<ActivityFacility>> getContainedFacilities() {
		return containedFacilities;
	}

	boolean addContainedEducFacility(Id<ActivityFacility> educFacilityId) {
		return this.containedFacilities.add(educFacilityId);
	}

	public Id<ActivityFacility> getId() {
		return id;
	}

	void setId(Id<ActivityFacility> id) {
		this.id = id;
	}
}

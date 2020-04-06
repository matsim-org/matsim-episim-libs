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

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.facilities.ActivityFacility;

import java.util.HashSet;
import java.util.Set;

class EducFacility implements Identifiable {
	private Id<EducFacility> id;
	private Coord coord;
	private boolean isEducKiga;
	private boolean isEducPrimary;
	private boolean isEducSecondary;
	private double noOfPupils = 0;

	//contains id s of other EducFacilities which are deleted in the process of aggregation
	private Set<Id<EducFacility>> containedFacilities = new HashSet<>();

	EducFacility(Id<EducFacility> id, Coord coord, boolean isEducKiga, boolean isEducPrimary,
				 boolean isEducSecondary) {
		this.setId(id);
		this.setCoord(coord);
		this.setEducKiga(isEducKiga);
		this.setEducPrimary(isEducPrimary);
		this.setEducSecondary(isEducSecondary);
	}

	void setId(Id<EducFacility> id) {
		this.id = id;
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

	Set<Id<EducFacility>> getContainedFacilities() {return containedFacilities;}

	boolean addContainedEducFacility(Id<EducFacility> educFacilityId){
		return this.containedFacilities.add(educFacilityId);
	}

	@Override
	public Id getId() {
		return id;
	}
}

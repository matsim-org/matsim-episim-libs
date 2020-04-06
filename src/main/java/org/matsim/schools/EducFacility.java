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

package org.matsim.schools;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.ActivityFacility;

class EducFacility {
	private Id<ActivityFacility> id;
	private Coord coord;
	private boolean isEducKiga;
	private boolean isEducPrimary;
	private boolean isEducSecondary;
	private double noOfPupils = 0;

	EducFacility(Id<ActivityFacility> id, Coord coord, boolean isEducKiga, boolean isEducPrimary,
				 boolean isEducSecondary) {
		this.setId(id);
		this.setCoord(coord);
		this.setEducKiga(isEducKiga);
		this.setEducPrimary(isEducPrimary);
		this.setEducSecondary(isEducSecondary);
	}

	Id<ActivityFacility> getId() {
		return id;
	}

	void setId(Id<ActivityFacility> id) {
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

}

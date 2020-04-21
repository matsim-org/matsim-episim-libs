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
		int positionKiga = Integer.MAX_VALUE;
		int positionPrimary = Integer.MAX_VALUE;
		int positionSecondary = Integer.MAX_VALUE;
		int positionMergedFacilities = Integer.MAX_VALUE;
		int positionId = Integer.MAX_VALUE;
		int positionX = Integer.MAX_VALUE;
		int positionY = Integer.MAX_VALUE;

		for (String line = reader.readLine(); line != null; line = reader.readLine()) {

			ii++;


			String[] parts = line.split("\t");

			if (ii == 0) {

				int count = 0;

				while(count<parts.length) {
					if(parts[count].equals("id"))
						positionId = count;
					if(parts[count].equals("x"))
						positionX = count;
					if(parts[count].equals("y"))
						positionY = count;
					if(parts[count].equals("educ_kiga"))
						positionKiga = count;
					if(parts[count].equals("educ_primary"))
						positionPrimary = count;
					if(parts[count].equals("educ_secondary"))
						positionSecondary = count;
					if(parts[count].equals("mergedFacilityIds"))
						positionMergedFacilities = count;
					count++;
				}
				continue;
			}



			Id<ActivityFacility> id = Id.create(parts[positionId], ActivityFacility.class);
			double x = Double.parseDouble(parts[positionX]);
			double y = Double.parseDouble(parts[positionY]);

			String educKiga = parts[positionKiga];
			boolean isEducKiga = false;
			if (!(educKiga.equals("0.0") || educKiga.equals("0"))) {
				isEducKiga = true;
			}

			String educPrimary = parts[positionPrimary];
			boolean isEducPrimary = false;
			if (!(educPrimary.equals("0.0") || educPrimary.equals("0"))) {
				isEducPrimary = true;
			}

			String educSecondary = parts[positionSecondary];
			boolean isEducSecondary = false;
			if (!(educSecondary.equals("0.0") || educSecondary.equals("0"))) {
				isEducSecondary = true;
			}

			Coord coord = CoordUtils.createCoord(x, y);

			if (transformation != null) {
				coord = transformation.transform(coord);
			}
			if(isEducKiga || isEducPrimary || isEducSecondary) {
				EducFacility educFacility = new EducFacility(id, coord, isEducKiga, isEducPrimary, isEducSecondary);

					if (positionMergedFacilities<Integer.MAX_VALUE && positionMergedFacilities < parts.length) {
						String[] containedFacilities;
						containedFacilities = parts[positionMergedFacilities].split(";");
						for (String containedFacility : containedFacilities) {
							educFacility.addContainedEducFacility(Id.create(containedFacility, ActivityFacility.class));
						}
					}

				educFacilities.add(educFacility);
			}


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

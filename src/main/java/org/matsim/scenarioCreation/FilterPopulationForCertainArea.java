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

import java.util.ArrayList;
import java.util.Collection;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Counter;
import org.opengis.feature.simple.SimpleFeature;

/**
 * Filters the population file of complete Germany only for the persons in Berlin.
* @author rewert
 *
 *
 * TODO : delete
*/
public class FilterPopulationForCertainArea {

	static final Logger log = Logger.getLogger(FilterPopulationForCertainArea.class);

	private static final String workingDir = "../../svn/shared-svn/projects/episim/matsim-files/snz/Berlin/";
	private static final String pathOfPopulation = workingDir + "population_fromPopulationAttributes.xml.gz";
	private static final String berlinShapeFile = workingDir + "shape-File/berlin-area_EPSG25832.shp";
	private static final String pathOutputPopulation = workingDir
			+ "population_fromPopulationAttributes_BerlinOnly.xml.gz";

	public static void main(String[] args) {

		Population population = PopulationUtils.readPopulation(pathOfPopulation);

		System.out.println("Old Population: " + population.getPersons().size());
		filterPopulationForBerlin(population, null);
		System.out.println("New Berlin Population: " + population.getPersons().size());
		PopulationUtils.writePopulation(population, pathOutputPopulation);

	}

	public static void filterPopulationForBerlin(Population population, CoordinateTransformation transformation) {
		// Coordinatesystem of shape: EPGS 25832
		// CoordinateSytem of population: Atlantis

		Collection<SimpleFeature> berlinShape = ShapeFileReader.getAllFeatures(berlinShapeFile);
		Geometry geometryOfBerlin = (Geometry) berlinShape.iterator().next().getDefaultGeometry();

		ArrayList<Id<Person>> personsToDelete = new ArrayList<Id<Person>>();

		int personTotal = population.getPersons().values().size();
		Counter counter = new Counter("Check location of person nr " , " out of " + personTotal + " persons");
		for (Person singlePerson : population.getPersons().values()) {
			Point homeLocation = createCoordinateOfPersonHome(singlePerson, transformation);
			if (!geometryOfBerlin.contains(homeLocation)) {
				personsToDelete.add(singlePerson.getId());
			}
			counter.incCounter();
		}
		log.info("Finished check of locations of persons");

		log.info("Number of persons to delete : " + personsToDelete.size());
		for (Id<Person> personId : personsToDelete) {
			population.removePerson(personId);
		}
	}

	private static Point createCoordinateOfPersonHome(Person singlePerson, CoordinateTransformation transformation) {
		Point p;
		double homeX = (double) singlePerson.getAttributes().getAttribute("homeX");
		double homeY = (double) singlePerson.getAttributes().getAttribute("homeY");
		p = MGC.xy2Point(homeX, homeY);

		if(transformation != null){
			p = MGC.coord2Point(transformation.transform(MGC.point2Coord(p)));
		}

		return p;
	}

}

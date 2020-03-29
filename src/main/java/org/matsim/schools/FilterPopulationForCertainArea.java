package org.matsim.schools;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

public class FilterPopulationForCertainArea {

	static final Logger log = Logger.getLogger(FilterPopulationForCertainArea.class);

	private static final String workingDir = "../shared-svn/projects/episim/matsim-files/snz/";
	private static final String pathOfPopulation = workingDir + "population_fromPopulationAttributes.xml.gz";
	private static final String berlinShapeFile = workingDir + "shp-berlin/berlin-area_EPSG25832.shp";
	private static final String pathOutputPopulation = workingDir
			+ "population_fromPopulationAttributes_BerlinOnly.xml.gz";

	public static void main(String[] args) {

		Population population = PopulationUtils.readPopulation(pathOfPopulation);

		System.out.println("Old Population: " + population.getPersons().size());
		filterPopulationForBerlin(population);
		System.out.println("New Berlin Population: " + population.getPersons().size());
		PopulationUtils.writePopulation(population, pathOutputPopulation);

	}

	private static void filterPopulationForBerlin(Population population) {
		// Coordinatesystem of shape: EPGS 25832
		// CoordinateSytem of population: Atlantis

		Collection<SimpleFeature> berlinShape = ShapeFileReader.getAllFeatures(berlinShapeFile);
		Geometry geometryOfBerlin = (Geometry) berlinShape.iterator().next().getDefaultGeometry();

		ArrayList<Id<Person>> personsToDelete = new ArrayList<Id<Person>>();
		int personCount = 0;
		int personTotal = population.getPersons().values().size();
		int count = 0;
		for (Person singlePerson : population.getPersons().values()) {
			personCount++;
			count++;
			Point homeLocation = createCoordinateOfPersonHome(singlePerson);
			if (!geometryOfBerlin.contains(homeLocation)) {
				personsToDelete.add(singlePerson.getId());
			}
			if (count == 1) {
				log.info("Check location of person" + personCount + " of " + personTotal + " total persons");
				count = 0;
			}
		}
		log.info("Finished check of locations of persons");
		for (Id<Person> personId : personsToDelete) {
			population.removePerson(personId);
		}
	}

	private static Point createCoordinateOfPersonHome(Person singlePerson) {
		Point p;
		double homeX = (double) singlePerson.getAttributes().getAttribute("homeX");
		double homeY = (double) singlePerson.getAttributes().getAttribute("homeY");
		p = MGC.xy2Point(homeX, homeY);
		return p;
	}

}

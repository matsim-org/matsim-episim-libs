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
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacility;
import org.opengis.feature.simple.SimpleFeature;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO update this javadoc...
 *
 *
 * <p><b>This documentation is deprecated!! Needs an update (shape file filtering....)</b></p>
 *
 *
 *
 * This class aims to cut a school population down to an area of interest by looking up the corresponding shape file and filtering the children population in a first processing step.
 * Secondly, the home facilities of the children are compared to the adult population, such that the output will only contain children living in facilities in which adults live.
 * It is assumed that the input children file can have a higher sample size than the adult file. To account for that, the resulting children population
 * is sampled down by the given sample size.
 * <p>
 * <b>parameters:</b> <br>
 * input <br>
 * (1) population containing <b>school population</b> for germany  (snz scenario u14 population)<br>
 * (2) population containing <b>adult population</b> for use case (snz scenario o14 population for bln, munich, heinsberg)<br>
 * (3) path to shape file determining the use case area
 * (4) <b>sample size for children</b><br>
 * output:<br>
 * (4) path for population containing school population for use case in the same sample size as the adults are, no plans but only attributes<br>
 * (5) path for population containing ALL persons with no plans in the target sample size<br>
 * <p>
 * Next step in the process would be to run {@code BuildSchoolPlans} which
 * builds home-school-home plans for the children and integrates them into the adult population.
 *
 * @author tschlenther
 */
class CutSchoolPopulationFitting2UseCaseAdultPopulation {

	private static final String INPUT_SCHOOL_POPULATION_GER = "../../svn/shared-svn/projects/episim/matsim-files/snz/Deutschland/de_populationU14_fromPopulationAttributes.xml.gz";
	private static final String INPUT_ADULT_POPULATION_USECASE = "../../svn/shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/processed-data/he_small_snz_adults_emptyPlans.xml.gz";
	private static final String INPUT_SHAPE_USECASE = "../../svn/shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/Shape-File/dilutionArea.shp";
	private static final double SAMPLE_SIZE_FOR_CHILDREN_IN_SHAPE = 0.25d;
	//name of the attribute in children population that is supposed to match facility id of parent
	private static final String HOME_FACILITY_ATTRIBUTE_NAME = "homeId";
	private static final String OUTPUT_SCHOOL_POPULATION_USECASE = "../../svn/shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/processed-data/he_small_snz_u14population_emptyPlans.xml.gz";
	private static final String OUTPUT_ENTIRE_POPULATION_USECASE = "../../svn/shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/processed-data/he_small_snz_entirePopulation_emptyPlans.xml.gz";

	private static Logger log = Logger.getLogger(CutSchoolPopulationFitting2UseCaseAdultPopulation.class);

	public static void main(String[] args) {


		String inputChildren = INPUT_SCHOOL_POPULATION_GER;
		String inputAdults = INPUT_ADULT_POPULATION_USECASE;
		String inputShape = INPUT_SHAPE_USECASE;
		double sampleRatio = SAMPLE_SIZE_FOR_CHILDREN_IN_SHAPE;
		String outputChildren = OUTPUT_SCHOOL_POPULATION_USECASE;
		String outputPopulation = OUTPUT_ENTIRE_POPULATION_USECASE;

		if(args.length > 0){
			inputChildren = args[0];
			inputAdults = args[1];
			inputShape = args[2];
			sampleRatio = Double.valueOf(args[3]);
			outputChildren = args[4];
			outputPopulation = args[5];
		}

		Population children = PopulationUtils.readPopulation(inputChildren);
			Population adults = PopulationUtils.readPopulation(inputAdults);

		log.info("nr of children in input file = " + children.getPersons().size());
		log.info("nr of adults in input file = " + adults.getPersons().size());

		Set<Id<ActivityFacility>> allHomeActFacilities = new HashSet<>();

		for (Person adult : adults.getPersons().values()) {
			Object homeAttribute = adult.getAttributes().getAttribute(HOME_FACILITY_ATTRIBUTE_NAME);
			if (homeAttribute != null) {
				allHomeActFacilities.add(Id.create((String) homeAttribute, ActivityFacility.class));
			} else {
				Set<Id<ActivityFacility>> personsHomeActFacilities = adult.getSelectedPlan().getPlanElements().stream()
						.filter(e -> e instanceof Activity)
						.filter(act -> ((Activity) act).getType().startsWith("home"))
						.map(home -> ((Activity) home).getFacilityId())
						.collect(Collectors.toSet());

				if (personsHomeActFacilities.size() != 1) {
					throw new IllegalStateException("person " + adult + " has invalid number of home facilities (" + personsHomeActFacilities.size() + ")");
				}
				allHomeActFacilities.addAll(personsHomeActFacilities);
			}

		}

		log.info("number of home facilities in adults file = " + allHomeActFacilities.size());

		int nrOfChildrenInsideShape = 0;
		Collection<SimpleFeature> shapefile = ShapeFileReader.getAllFeatures(inputShape);

		log.info("checking how many children have a home inside of shape...");
		Set<Id<Person>> childsNotInShape = new HashSet<>();
		Counter counter = new Counter("checking home coord of child ");
		for (Person child : children.getPersons().values()) {
			counter.incCounter();
			boolean childInShape = isChildsHomeInShape(shapefile, child);
			if(childInShape){
				nrOfChildrenInsideShape++;
			} else {
				childsNotInShape.add(child.getId());
			}
		}

		log.info("nr of children that have their home within the scope of the shape = " + nrOfChildrenInsideShape);

		double childrenToBeCreated = Math.floor(nrOfChildrenInsideShape * sampleRatio);

		log.info("that means that " + childrenToBeCreated + " children will have to be contained in the result...");

		childsNotInShape.forEach(child -> children.removePerson(child));

		List<Person> childrenToDeleteForBeingHomeAlone = new ArrayList<>();
		for (Person child : children.getPersons().values()) {

			Object attribute = child.getAttributes().getAttribute(HOME_FACILITY_ATTRIBUTE_NAME);
			if (attribute == null) throw new IllegalStateException("child " + child + " has no attribute " + HOME_FACILITY_ATTRIBUTE_NAME);

			Id<ActivityFacility> facilityId = Id.create((String) attribute, ActivityFacility.class);
			if (!allHomeActFacilities.contains(facilityId)) {
				childrenToDeleteForBeingHomeAlone.add(child);
			}
		}


		log.info("removing " + childrenToDeleteForBeingHomeAlone.size() + " children because their home facility is not contained in adults home facilities...");
		childrenToDeleteForBeingHomeAlone.forEach(c -> children.removePerson(c.getId()));
		int nrOfChildrenFullFillingConditions = children.getPersons().size();
		log.info("remaining number of children = " + nrOfChildrenFullFillingConditions);

		if(nrOfChildrenFullFillingConditions < childrenToBeCreated){
			throw new RuntimeException("can not create a " + sampleRatio + " sample of children because there arent enough children inside the shape having a homeId that an adult also has..\n" +
					"nrOfChildrenFullFillingConditions=" + nrOfChildrenFullFillingConditions +
					"\nchildrentToBeCreated=" + childrenToBeCreated);
		}

		sampleRatio = childrenToBeCreated/nrOfChildrenFullFillingConditions;

		log.info("scaling down children...");
		PopulationUtils.sampleDown(children, sampleRatio);
		log.info("remaining number of children = " + children.getPersons().size());

		log.info("writing school population containing only children with no plans...");
		PopulationUtils.writePopulation(children, outputChildren);

		log.info("merge empty adult plans with empty children plans...");
		//finally, merge adult population and school population and write out the result
		children.getPersons().values().forEach(person -> adults.addPerson(person));

		log.info("writing entire population with no plans...");
		PopulationUtils.writePopulation(adults, outputPopulation);
	}

	private static boolean isChildsHomeInShape(Collection<SimpleFeature> shapefile, Person child) {
		double homeX = (double) child.getAttributes().getAttribute("homeX");
		double homeY = (double) child.getAttributes().getAttribute("homeY");
		Coord homeCoord = CoordUtils.createCoord(homeX, homeY);
		boolean childInShape = false;
		for (SimpleFeature singleFeature : shapefile) {
			Geometry geometryOfCertainArea = (Geometry) singleFeature.getDefaultGeometry();
			if (geometryOfCertainArea.contains(MGC.coord2Point(homeCoord)))
				childInShape = true;
					break;
		}
		return childInShape;
	}


}

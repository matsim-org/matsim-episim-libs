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

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.facilities.ActivityFacility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class aims to cut a school population down to an area of interest by looking up the corresponding adult population and matching the id of the home facility.
 * This means, the output will contain all children living in facilities in which adults live.
 * It is assumed that the input children file can have a higher sample size than the adult file. To account for that, the resulting children population
 * is sampled down.
 *
 * 	input: 	(1) population containing school population for germany  (snz scenario u14 population)
 * 			(2) population containing adult population for use case (snz scenario o14 population for bln, munich, heinsberg)
 * 			(3) attribute name for the matching of households.
 * 			(4) sample size ratio (input children sample size / input adult sample size)
 * 	output:  population containing school population for use case in the same sample size as the adults are
 *
 * 	Next step in the process would be to run {@code SchoolPopulationDestinationChoiceAndIntegration} which
 * 	builds home-school-home plans for the children and integrates them into the adult population.
 *
 * 	@author tschlenther
 */
class CutSchoolPopulationFitting2UseCaseAdultPopulation {

	private static Logger log = Logger.getLogger(CutSchoolPopulationFitting2UseCaseAdultPopulation.class);

	private static final String INPUT_SCHOOL_POPULATION_GER = "../../svn/shared-svn/projects/episim/matsim-files/snz/Deutschland/de_populationU14_fromPopulationAttributes.xml.gz";
	private static final String INPUT_ADULT_POPULATION_USECASE = "../../svn/shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_optimizedPopulation_adults_withoutNetworkInfo.xml.gz";

	private static final double SAMPLE_SIZE_RATIO = 0.25d;

	//name of the attribute in children population that is supposed to match facility id of parent
	private static final String CHILDREN_HOME_FAC_ATTRIBUTE_NAME = "homeId";
	private static final String OUTPUT_SCHOOL_POPULATION_USECASE = "../../svn/shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_u14population_noPlans.xml.gz";

	public static void main(String[] args) {

		Population children = PopulationUtils.readPopulation(INPUT_SCHOOL_POPULATION_GER);
		Population adults = PopulationUtils.readPopulation(INPUT_ADULT_POPULATION_USECASE);

		log.info("nr of children in input file = " + children.getPersons().size());
		log.info("nr of adults in input file = " + adults.getPersons().size());

		Set<Id<ActivityFacility>> allHomeActFacilities = new HashSet<>();

		for (Person adult : adults.getPersons().values()) {
			Set<Id<ActivityFacility>> personsHomeActFacilities = adult.getSelectedPlan().getPlanElements().stream()
					.filter(e -> e instanceof Activity)
					.filter(act -> ((Activity) act).getType().startsWith("home"))
					.map(home -> ((Activity) home).getFacilityId())
					.collect(Collectors.toSet());

			if (personsHomeActFacilities.size() != 1){
				throw new IllegalStateException("person " + adult + " has invalid number of home facilities (" + personsHomeActFacilities.size() + ")");
			}

			allHomeActFacilities.addAll(personsHomeActFacilities);
		}

		log.info("number of home facilities in adults file = " + allHomeActFacilities.size());


		List<Person> childrenToDelete = new ArrayList<>();
		for (Person child : children.getPersons().values()) {

			Object attribute = child.getAttributes().getAttribute(CHILDREN_HOME_FAC_ATTRIBUTE_NAME);
			if(attribute == null) throw new IllegalStateException("child " + child + " has no attribute " + CHILDREN_HOME_FAC_ATTRIBUTE_NAME);

			Id<ActivityFacility> facilityId = Id.create((String) attribute, ActivityFacility.class);
			if(!allHomeActFacilities.contains(facilityId)){
				childrenToDelete.add(child);
			}
		}

		log.info("removing " + childrenToDelete.size() + " children because their home facility is not contained in adults home facilities...");
		childrenToDelete.forEach(c -> children.removePerson(c.getId()));
		log.info("remaining number of children = " + children.getPersons().size());

		log.info("scaling down children...");
		PopulationUtils.sampleDown(children, SAMPLE_SIZE_RATIO);
		log.info("remaining number of children = " + children.getPersons().size());

		PopulationUtils.writePopulation(children, OUTPUT_SCHOOL_POPULATION_USECASE);

	}



}

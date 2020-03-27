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

import java.net.URL;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;

/**
 * Loads person attributes file and population file of snz scenario and adds persons to population if they only occur in attributes file.
 * Then copies all attributes from attributes file to population.
* @author smueller
*/

public class ConvertPersonAttributes {
	private static final String workingDir = "../shared-svn/projects/episim/matsim-files/snz/";
	private static final String pathToOriginalPopulation = workingDir + "optimizedPopulation_withoutNetworkInfo.xml.gz";
//	private static final String pathToOriginalAttributes = workingDir + "optimizedPersonAttributes.xml.gz";
	private static final String pathToOriginalAttributes = workingDir + "de_populationAttributes.xml.gz";
//	private static final String pathOutputPopulation = workingDir + "optimizedPopulation_withoutNetworkInfo_withAttributes.xml.gz";
	private static final String pathOutputPopulation = workingDir + "de_population_fromPopulationAttributes.xml.gz";

	public static void main(String[] args) {
				
		Population populationFromAttributes = buildPopulationFromAttributes(pathToOriginalAttributes);
		
		PopulationUtils.writePopulation(populationFromAttributes, pathOutputPopulation);
		
//		includeMissingAgentsIntoOrigPopulation(populationFromAttributes);
		
		Config config = ConfigUtils.createConfig();
		
		config.plans().setInputPersonAttributeFile(pathToOriginalAttributes);
		
		config.plans().setInputFile(pathOutputPopulation);
		
		config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		PopulationUtils.writePopulation(scenario.getPopulation(), pathOutputPopulation);
	}



	private static void includeMissingAgentsIntoOrigPopulation(Population populationFromAttributes) {
		Population population = PopulationUtils.readPopulation(pathToOriginalPopulation);
		for (Person person : populationFromAttributes.getPersons().values()) {
			if (!population.getPersons().containsKey(person.getId())) {
				population.addPerson(person);
			}
		}
		PopulationUtils.writePopulation(population, pathOutputPopulation);
	}



	private static Population buildPopulationFromAttributes(String attributesFile) {
		
		Config config = ConfigUtils.createConfig();
		config.plans().setInputPersonAttributeFile(attributesFile);
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Population population = scenario.getPopulation();
		PopulationFactory popFac = population.getFactory();
		URL personAttributesURL = config.plans().getInputPersonAttributeFileURL(config.getContext());
		final ObjectAttributes attributes = new ObjectAttributes();
		ObjectAttributesXmlReader reader = new ObjectAttributesXmlReader(attributes);
		reader.parse(personAttributesURL);
		String[] attributesAsString = attributes.toString().split("\n");

		for (String s : attributesAsString) {

				String[] line = s.split(";");
				Id<Person> personId = Id.createPersonId(line[0].split("=")[1]);
				Person person = popFac.createPerson(personId);
				population.addPerson(person);
		}
		
		return population;
		
	}

}

package org.matsim.scenarioCreation;

import org.apache.log4j.Logger;
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


/**
 * probably not needed any more...?!
 * <p>
 * tschlenther, 06 of april
 */
public class FilterPersonsForCertainArea {

	static final Logger log = Logger.getLogger(FilterPersonsForCertainArea.class);

	private static final String workingDir = "../shared-svn/projects/episim/matsim-files/snz/";
	private static final String neededIds = workingDir + "Munich/mu_personIds.txt";
	private static final String pathToOriginalAttributes = workingDir + "Deutschland/populationAttributes.xml.gz";
	private static final String pathOutputPopulation = workingDir + "Munich/populationAttributes.xml.gz";

	public static void main(String[] args) throws IOException {
		String outputLocationPopulation;
		String originalAttributesLocation;
		String inputIds;


		for (String arg : args) {
			log.info(arg);
		}
		if (args.length == 0) {
			outputLocationPopulation = pathOutputPopulation;
			originalAttributesLocation = pathToOriginalAttributes;
			inputIds = neededIds;

		} else {
			outputLocationPopulation = args[0];
			originalAttributesLocation = args[1];
			inputIds = args[2];
		}

		List<String> idList = new ArrayList<String>();

		readIdFile(idList, inputIds);

		Population populationFromAttributes = buildPopulationFromAttributes(originalAttributesLocation, idList);

		PopulationUtils.writePopulation(populationFromAttributes, outputLocationPopulation);

//			includeMissingAgentsIntoOrigPopulation(populationFromAttributes);

		Config config = ConfigUtils.createConfig();

		config.plans().setInputPersonAttributeFile(originalAttributesLocation);

		config.plans().setInputFile(outputLocationPopulation);

		config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);

		Scenario scenario = ScenarioUtils.loadScenario(config);

		PopulationUtils.writePopulation(scenario.getPopulation(), outputLocationPopulation);
	}

	private static void readIdFile(List<String> idList, String inputIds) throws IOException {
		FileReader fileReader = new FileReader(inputIds);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String row = bufferedReader.readLine();

		while (row != null && row != "") {
			idList.add(row);
			row = bufferedReader.readLine();
		}
		bufferedReader.close();
		fileReader.close();
		log.info("Read input-Ids: " + idList.size());
	}

	private static Population buildPopulationFromAttributes(String attributesFile, List<String> idList) {

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
			if (idList.contains(personId.toString())) {
				Person person = popFac.createPerson(personId);
				population.addPerson(person);
			}
		}
		return population;
	}
}

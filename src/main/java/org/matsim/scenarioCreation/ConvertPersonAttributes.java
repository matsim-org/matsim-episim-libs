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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;


@CommandLine.Command(
		name = "convertPersonAttributes",
		description = "Reads in one population attributes file and one optional .txt file containing person id's, one per line. " +
				"Population attributes are filtered by the id set and then written out in plans file format.",
		mixinStandardHelpOptions = true
)
public class ConvertPersonAttributes implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(ConvertPersonAttributes.class);

	@CommandLine.Parameters(paramLabel = "file", arity = "1", description = "Path to attribute file")
	private Path input;

	@CommandLine.Option(names = "--ids", description = "Optional path to person ids to filter for.")
	private Path personIds;

	@CommandLine.Option(names = "--output", description = "Output population file", defaultValue = "output/filteredPopulation.xml.gz")
	private Path output;

	public static void main(String[] args) {
		System.exit(new CommandLine(new ConvertPersonAttributes()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		if (!Files.exists(input)) {
			log.error("Input does not exist.");
			return 2;
		}

		Set<String> personIds = null;
		if (this.personIds != null && Files.exists(this.personIds)) {
			log.info("Filtering by person ids: {}", this.personIds);
			personIds = CreationUtils.readIdFile(this.personIds);
		}

		FilteredObjectAttributes attributes = readAndFilterAttributes(input, personIds);
		Population populationFromAttributes = buildPopulationFromAttributes(attributes, personIds);

		PopulationUtils.writePopulation(populationFromAttributes, output.toString());

		String attributesFileForConversion = input.toString();
		if(personIds != null){
			String outputPath = output.toAbsolutePath().toString();
			attributesFileForConversion = outputPath.substring(0, outputPath.lastIndexOf('\\')) + "/filtered_" + input.getFileName() ;
			new ObjectAttributesXmlWriter(attributes).writeFile(attributesFileForConversion);
		}


//			includeMissingAgentsIntoOrigPopulation(populationFromAttributes);

		Config config = ConfigUtils.createConfig();

		// load and write population one time for internal conversion
		config.plans().setInputPersonAttributeFile(attributesFileForConversion);

		config.plans().setInputFile(output.toString());

		config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);

		Scenario scenario = ScenarioUtils.loadScenario(config);
		PopulationUtils.writePopulation(scenario.getPopulation(), output.toString());

		return 0;
	}

	private Population buildPopulationFromAttributes(FilteredObjectAttributes attributes, Set<String> personIds) {

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Population population = scenario.getPopulation();
		PopulationFactory popFac = population.getFactory();

		for (String entry : attributes.getAttributes().keySet()) {
			Id<Person> personId = Id.createPersonId(entry);
			Person person = popFac.createPerson(personId);
			population.addPerson(person);
		}

		return population;
	}

	private FilteredObjectAttributes readAndFilterAttributes(Path attributesFile, Set<String> personIds) {
		Config config = ConfigUtils.createConfig();
		config.plans().setInputPersonAttributeFile(attributesFile.toString());
		String personAttributes = config.plans().getInputPersonAttributeFile();
		FilteredObjectAttributes attributes = new FilteredObjectAttributes(personIds);
		ObjectAttributesXmlReader reader = new ObjectAttributesXmlReader(attributes);
		reader.parse(IOUtils.getInputStream(IOUtils.getFileUrl(personAttributes)));
		return attributes;
	}

	/**
	 * Filter for certain persons. This class should work but is badly designed because of API limitations.
	 */
	private static final class FilteredObjectAttributes extends ObjectAttributes {

		private final Set<String> ids;

		private FilteredObjectAttributes(@Nullable Set<String> ids) {
			this.ids = ids;
		}

		@SuppressWarnings("unchecked")
		private Map<String, Map<String, Object>> getAttributes() {
			try {
				Field field = ObjectAttributes.class.getDeclaredField("attributes");
				field.setAccessible(true);
				return (Map<String, Map<String, Object>>) field.get(this);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Could not retrieve attributes");
			}
		}

		@Override
		public Object putAttribute(String objectId, String attribute, Object value) {
			if (ids == null || ids.contains(objectId))
				return super.putAttribute(objectId, attribute, value);

			// It appears this return value is never used
			return null;
		}
	}
}

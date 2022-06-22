package org.matsim.scenarioCreation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.objectattributes.ObjectAttributes;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "filterPersons",
		description = "Filter persons based on visited facilities in an area.")
public class FilterPersons implements Callable<Integer>, BasicEventHandler {

	private static final Logger log = LogManager.getLogger(FilterPersons.class);

	@CommandLine.Parameters(arity = "1", description = "Input event file")
	private Path events;

	@CommandLine.Option(names = "--facilities", description = "Path to facility file", required = true)
	private Path facilityPath;

	@CommandLine.Option(names = "--shape-file", description = "Path to shp file", required = true)
	private Path shp;

	@CommandLine.Option(names = "--attributes", description = "Path to population attributes", required = false)
	private Path attributes;

	@CommandLine.Option(names = "--shape-crs", description = "Path to shp file", defaultValue = "EPSG:4326")
	private String shpCrs;

	@CommandLine.Option(names = "--output", description = "Output path", required = true)
	private Path output;

	private Set<Id<Person>> persons;
	Map<Id<ActivityFacility>, ActivityFacility> facilities;

	public static void main(String[] args) {
		new CommandLine(new FilterPersons()).execute(args);
	}

	@Override
	public Integer call() throws Exception {

		DistrictLookup.Index index = new DistrictLookup.Index(shp.toFile(), TransformationFactory.getCoordinateTransformation("EPSG:25832", shpCrs), "plz");

		persons = new HashSet<>();

		if (attributes != null) {
			log.info("Adding persons by home coordinates");

			FilteredObjectAttributes attr = ConvertPersonAttributes.readAndFilterAttributes(attributes, new FilteredObjectAttributes(index));

			for (Map.Entry<String, Map<String, Object>> e : attr.getAttributes().entrySet()) {
				persons.add(Id.createPersonId(e.getKey()));
			}

			attr.clear();

			log.info("Added {} persons", persons.size());
		}

		facilities = loadFacilities(index);

		EventsManager manager = EventsUtils.createEventsManager();
		manager.addHandler(this);
		manager.initProcessing();
		EventsUtils.readEvents(manager, events.toString());
		manager.finishProcessing();

		log.info("Filtered {} persons", persons.size());

		try (BufferedWriter writer = IOUtils.getBufferedWriter(output.toString())) {
			for (Id<Person> p : persons) {
				writer.write(p.toString());
				writer.write("\n");
			}
		}

		return 0;
	}

	private Map<Id<ActivityFacility>, ActivityFacility> loadFacilities(DistrictLookup.Index index) {

		// load scenario with only facilities
		Config config = ConfigUtils.createConfig();
		config.facilities().setInputFile(facilityPath.toString());
		Scenario scenario = ScenarioUtils.loadScenario(config);

		Map<Id<ActivityFacility>, ? extends ActivityFacility> facilities = scenario.getActivityFacilities().getFacilities();
		IdMap<ActivityFacility, ActivityFacility> filtered = new IdMap<>(ActivityFacility.class);

		for (ActivityFacility value : facilities.values()) {

			try {
				// throws exception when not found
				index.query(value.getCoord().getX(), value.getCoord().getY());
				filtered.put(value.getId(), value);

			} catch (NoSuchElementException e) {
				// not found
			}

		}

		log.info("Using {} out {} facilities", filtered.size(), facilities.size());

		return filtered;
	}

	@Override
	public void handleEvent(Event event) {

		if (event instanceof ActivityStartEvent) {
			ActivityStartEvent ev = (ActivityStartEvent) event;
			if (facilities.containsKey(ev.getFacilityId()))
				persons.add(ev.getPersonId());


		} else if (event instanceof ActivityEndEvent) {
			ActivityEndEvent ev = (ActivityEndEvent) event;
			if (facilities.containsKey(ev.getFacilityId()))
				persons.add(ev.getPersonId());

		}

	}

	@Override
	public void reset(int iteration) {

	}

	/**
	 * Filter for certain persons. This class should work but is badly designed because of API limitations.
	 */
	static final class FilteredObjectAttributes extends ObjectAttributes {


		private final DistrictLookup.Index index;

		private FilteredObjectAttributes(DistrictLookup.Index index) {
			this.index = index;
		}

		@SuppressWarnings("unchecked")
		Map<String, Map<String, Object>> getAttributes() {
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

			// It appears this return value is never used
			if (!attribute.equals("homeX") && !attribute.equals("homeY"))
				return null;

			// assume homeY is always second
			if (attribute.equals("homeY")) {

				try {
					// throws exception when not found
					index.query((Double) getAttribute(objectId, "homeX"), (Double) value);
					return super.putAttribute(objectId, attribute, value);

				} catch (NoSuchElementException e) {
					// not found
					getAttributes().remove(objectId);
					return null;
				}

			}

			return super.putAttribute(objectId, attribute, value);
		}
	}

}

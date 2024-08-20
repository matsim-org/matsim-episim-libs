package org.matsim.episim.analysis;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.HasFacilityId;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.facilities.ActivityFacility;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Util to create an origin destination matrix.
 */
@CommandLine.Command(
		name = "createODMatrix",
		description = "Creates an origin destination matrix using the trips from a event file and facility file as lookup."
)
class CreateODMatrix implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(CreateODMatrix.class);

	@CommandLine.Option(names = "--events", defaultValue = "de_2020_snz_episim_events_100pt.xml.gz")
	private Path events;

	@CommandLine.Option(names = "--facilities", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/Deutschland/facilities_assigned_simplified.xml.gz")
	private Path facilities;

	@CommandLine.Option(names = "--shapeFile", defaultValue = "../public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/landkreise-in-germany.shp")
	private Path shapeFile;

	@CommandLine.Option(names = "--output", defaultValue = "od.txt")
	private Path output;

	public static void main(String[] args) {
		System.exit(new CommandLine(new CreateODMatrix()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		Config config = ConfigUtils.createConfig();
		config.facilities().setInputFile(facilities.toString());

		// name_1 is the Bundesland
		DistrictLookup.Index index = new DistrictLookup.Index(shapeFile.toFile(), TransformationFactory.getCoordinateTransformation("EPSG:25832", "EPSG:4326"), "name_1");

		Scenario scenario = ScenarioUtils.loadScenario(config);

		log.info("Read {} facilities", scenario.getActivityFacilities().getFacilities().size());

		// map region name to its index
		Object2IntMap<String> regions = new Object2IntOpenHashMap<>();

		// Map facility id to region index
		Int2IntMap facilities = new Int2IntOpenHashMap();

		int warn = 0;
		for (Map.Entry<Id<ActivityFacility>, ? extends ActivityFacility> e : scenario.getActivityFacilities().getFacilities().entrySet()) {
			try {
				String target = index.query(e.getValue().getCoord().getX(), e.getValue().getCoord().getY()).intern();
				int region = regions.computeIntIfAbsent(target, k -> regions.size());

				log.trace("Matched {} to {}", e.getKey(), target);

				facilities.put(e.getKey().index(), region);

			} catch (NoSuchElementException exc) {
				warn++;
				log.debug("Could not match facility {}", e.getKey(), exc);
			}
		}

		log.info("Done loading facilities. {} could not be matched", warn);

		EventsManager manager = EventsUtils.createEventsManager();

		Int2IntMap lastRegion = new Int2IntOpenHashMap();

		RealMatrix m = MatrixUtils.createRealMatrix(regions.size(), regions.size());

		manager.addHandler((BasicEventHandler) event -> {

			if (event instanceof HasFacilityId && event instanceof HasPersonId) {
				if (((HasPersonId) event).getPersonId() == null || ((HasFacilityId) event).getFacilityId() == null)
					return;

				int person = ((HasPersonId) event).getPersonId().index();
				int facility = ((HasFacilityId) event).getFacilityId().index();
				int region = facilities.getOrDefault(facility, -1);

				if (region == -1) return;

				int last = lastRegion.getOrDefault(person, -1);

				if (last != region) {
					lastRegion.put(person, region);
					if (last != -1) {
						m.addToEntry(last, region, 1);
					}
				} else if (event instanceof ActivityEndEvent) {
					// count all end events if on same region
					m.addToEntry(last, region, 1);
				}
			}
		});

		EventsUtils.readEvents(manager, events.toString());

		log.info("Finished reading events");

		Files.write(Path.of(output.toString() + ".index"),
				regions.object2IntEntrySet().stream()
						.sorted(Comparator.comparingInt(Object2IntMap.Entry::getIntValue))
						.map(Map.Entry::getKey)
						.collect(Collectors.toList()));

		BufferedWriter out = Files.newBufferedWriter(output);
		for (int i = 0; i < regions.size(); i++) {
			for (int j = 0; j < regions.size(); j++) {
				out.write(String.valueOf(m.getEntry(i, j)));
				out.write("\t");
			}

			out.write("\n");
		}

		out.close();

		return 0;
	}
}

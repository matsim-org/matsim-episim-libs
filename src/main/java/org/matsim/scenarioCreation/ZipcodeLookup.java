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

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.data.FeatureReader;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.index.strtree.STRtree;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.facilities.*;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;

/**
 * Preprocessing step that takes the home ids of each person in a population and looks up their district from a shape file.
 * Writes a new population file with the additional "district" attribute as result.
 */
@CommandLine.Command(
		name = "zipcodeLookup",
		description = "Calculate and attach zipcode information to a population.",
		mixinStandardHelpOptions = true
)
public class ZipcodeLookup implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(ZipcodeLookup.class);

	@CommandLine.Parameters(paramLabel = "file", arity = "1", description = "Population file", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/samples/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_1pt_split.xml.gz")
	//"../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/episim-input/he_small_2020_snz_entirePopulation_noPlans.xml.gz")
	private Path input;

	@CommandLine.Option(names = "--shp", description = "Shapefile containing district information", defaultValue = "D:/Dropbox/Documents/VSP/plz/plz-gebiete.shp")
	private Path shapeFile;

	@CommandLine.Option(names = "--output", description = "Output population file", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/samples/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_andNeighborhood_1pt_split.xml.gz")
	private Path output;

	@CommandLine.Option(names = "--attr", description = "Attribute name in the shapefile, which contains the district name", defaultValue = "plz")
	private String attr;

	@CommandLine.Option(names = "--input-crs", description = "Overwrite CRS of the population home coordinates. " +
			"If not given it will be read from the population attributes.", defaultValue = "EPSG:25832")
	private String inputCRS;

	// because of the own transformation classes of matsim, the crs can not be easily read from the shapefile, but has to be explicitly defined
	@CommandLine.Option(names = "--crs", description = "CRS of the shapefile.", defaultValue = "EPSG:4326")
	private String shapeCRS;

	public static void main(String[] args) {
		System.exit(new CommandLine(new ZipcodeLookup()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		//Berlin Neighborhood Zipcodes
		HashMap<String, IntSet> berlinDistricts = new HashMap<String, IntSet>();
		berlinDistricts.put("Mitte", new IntOpenHashSet(List.of(10115, 10559, 13355, 10117, 10623, 13357, 10119, 10785, 13359, 10787, 10557, 13353, 10555, 13351, 13349, 10551, 13347)));
		berlinDistricts.put("Friedrichshain_Kreuzberg", new IntOpenHashSet(List.of(10179, 10967, 10243, 10969, 10245, 10997, 10247, 10999, 12045, 10961, 10963, 10965, 10178)));
		berlinDistricts.put("Pankow", new IntOpenHashSet(List.of(10249, 10405, 10407, 10409, 10435, 10437, 10439, 13051, 13053, 13086, 13088, 13089, 13125, 13127, 13129, 13156, 13158, 13159, 13187, 13189)));
		berlinDistricts.put("Charlottenburg_Wilmersdorf", new IntOpenHashSet(List.of(10553, 10585, 10587, 10589, 10625, 10627, 10629, 10707, 10709, 10711, 10713, 10715, 10717, 10719, 10789, 13597, 13627, 14050, 14053, 14055, 14057, 14059)));
		berlinDistricts.put("Spandau", new IntOpenHashSet(List.of(13581, 13583, 13585, 13587, 13589, 13591, 13593, 13595, 13599, 14052, 14089)));
		berlinDistricts.put("Steglitz_Zehlendorf", new IntOpenHashSet(List.of(12163, 12165, 12167, 12169, 12203, 12205, 12207, 12209, 12247, 12279, 14109, 14129, 14163, 14165, 14167, 14169, 14193, 14195, 14199)));
		berlinDistricts.put("Tempelhof_Schoeneberg", new IntOpenHashSet(List.of(10777, 10779, 10781, 10783, 10823, 10825, 10827, 14197, 10829, 12101, 12103, 12105, 12109, 12157, 12159, 12161, 12249, 12277, 12307, 12309)));
		berlinDistricts.put("Neukoelln", new IntOpenHashSet(List.of(12043, 12047, 12049, 12051, 12053, 12055, 12057, 12059, 12099, 12107, 12305, 12347, 12349, 12351, 12353, 12355, 12357, 12359)));
		berlinDistricts.put("Treptow_Koepenick", new IntOpenHashSet(List.of(12435, 12437, 12439, 12459, 12487, 12489, 12524, 12526, 12527, 12555, 12557, 12559, 12587, 12589, 12623)));
		berlinDistricts.put("Marzahn_Hellersdorf", new IntOpenHashSet(List.of(12619, 12621, 12627, 12629, 12679, 12681, 12683, 12685, 12687, 12689)));
		berlinDistricts.put("Lichtenberg", new IntOpenHashSet(List.of(10315, 13057, 10317, 10318, 10319, 10365, 10367, 10369, 13055, 13059)));
		berlinDistricts.put("Reinickendorf", new IntOpenHashSet(List.of(13403, 13405, 13407, 13409, 13435, 13437, 13439, 13465, 13467, 13469, 13503, 13505, 13507, 13509, 13629)));




		Population population = PopulationUtils.readPopulation(input.toString());

		String crs = inputCRS.equals("null") ? (String) population.getAttributes().getAttribute("coordinateReferenceSystem") : inputCRS;

		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(crs, shapeCRS);

		Index index = new Index(shapeFile.toFile(), ct, attr);

		// Count errors
		int unknown = 0;
		for (Person p : population.getPersons().values()) {

			try {
				double x = (double) p.getAttributes().getAttribute("homeX");
				double y = (double) p.getAttributes().getAttribute("homeY");

				String plz = index.query(x, y);
				p.getAttributes().putAttribute("zipcode", plz);

				for (String neighborhoodName : berlinDistricts.keySet()) {
					if (berlinDistricts.get(neighborhoodName).contains(Integer.parseInt(plz))) {
						p.getAttributes().putAttribute("berlin-neighborhood", neighborhoodName);
					}
				}


			} catch (RuntimeException e) {
				unknown++;
			}
		}

		// Simple check if the lookup might be wrong
		if (unknown >= population.getPersons().size() * 0.5) {
			log.error("zipcode lookup failed for {} out of {} persons.", unknown, population.getPersons().size());
			return 1;
		}

		log.info("Finished with failed lookup for {} out of {} persons.", unknown, population.getPersons().size());

		PopulationUtils.writePopulation(population, output.toString());


		//Facilities

		Config config = ConfigUtils.createConfig();
		config.facilities().setInputFile("D:/Dropbox/Documents/VSP/episim/be_2020-facilities_assigned_simplified_grid.xml.gz");
		Scenario scenario = ScenarioUtils.loadScenario(config);

		// Count errors
		int unknownFac = 0;
		for (Facility f : scenario.getActivityFacilities().getFacilities().values()) {

			try {
				double x = f.getCoord().getX();
				double y = f.getCoord().getY();

				String plz = index.query(x, y);
				((Attributable) f).getAttributes().putAttribute("zipcode", plz);


				for (String neighborhoodName : berlinDistricts.keySet()) {
					if (berlinDistricts.get(neighborhoodName).contains(Integer.parseInt(plz))) {
						((Attributable) f).getAttributes().putAttribute("berlin-neighborhood", neighborhoodName);
					}
				}
			} catch (RuntimeException e) {
				unknownFac++;
			}
		}

		new FacilitiesWriter(scenario.getActivityFacilities()).write("D:/Dropbox/Documents/VSP/episim/be_2020-facilities_assigned_simplified_grid_WithNeighborhoodAndPLZ.xml.gz");


		return 0;
	}

	/**
	 * Helper class to provide an index for a shapefile lookup.
	 */
	public static final class Index {

		private final STRtree index = new STRtree();
		private final CoordinateTransformation ct;
		private final String attr;

		/**
		 * Constructor.
		 *
		 * @param ct   coordinate transform from query to target crs
		 * @param attr attribute for the result of {@link #query(double, double)}
		 */
		public Index(File shapeFile, CoordinateTransformation ct, String attr)
				throws IOException {
			ShapefileDataStore ds = (ShapefileDataStore) FileDataStoreFinder.getDataStore(shapeFile);
			ds.setCharset(StandardCharsets.UTF_8);

			FeatureReader<SimpleFeatureType, SimpleFeature> it = ds.getFeatureReader();
			while (it.hasNext()) {
				SimpleFeature ft = it.next();
				MultiPolygon polygon = (MultiPolygon) ft.getDefaultGeometry();

				Envelope env = polygon.getEnvelopeInternal();
				index.insert(env, ft);
			}

			it.close();
			ds.dispose();

			log.info("Created index with size: {}, depth: {}", index.size(), index.depth());

			this.ct = ct;
			this.attr = attr;
		}

		/**
		 * Query the index for first feature including a certain point.
		 *
		 * @throws NoSuchElementException when no entry matched the query.
		 */
		@SuppressWarnings("unchecked")
		public String query(double x, double y) {
			Coord coord = new Coord(x, y);

			// Because we can not easily transform the feature geometry with MATSim we have to do it the other way around...
			Coordinate p = MGC.coord2Coordinate(ct.transform(coord));

			List<SimpleFeature> result = index.query(new Envelope(p));
			for (SimpleFeature ft : result) {
				MultiPolygon polygon = (MultiPolygon) ft.getDefaultGeometry();
				if (polygon.contains(MGC.coordinate2Point(p)))
					return (String) ft.getAttribute(attr);
			}

			throw new NoSuchElementException(String.format("No matching entry found for x:%f y:%f %s", x, y, p));
		}
	}
}

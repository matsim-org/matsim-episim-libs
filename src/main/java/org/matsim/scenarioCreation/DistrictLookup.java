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
import org.geotools.data.FeatureReader;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.index.strtree.STRtree;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;

/**
 * Preprocessing step that takes the home ids of each person in a population and looks up their district from a shape file.
 * Writes a new population file with the additional "district" attribute as result.
 */
@CommandLine.Command(
		name = "districtLookup",
		description = "Calculate and attach district information to a population.",
		mixinStandardHelpOptions = true
)
public class DistrictLookup implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(DistrictLookup.class);

	@CommandLine.Parameters(paramLabel = "file", arity = "1", description = "Population file", defaultValue  = "../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/episim-input/he_small_2020_snz_entirePopulation_noPlans.xml.gz")
	private Path input;

	@CommandLine.Option(names = "--shp", description = "Shapefile containing district information", defaultValue = "../public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/landkreise-in-germany.shp")
	private Path shapeFile;

	@CommandLine.Option(names = "--output", description = "Output population file", defaultValue  = "../shared-svn/projects/episim/matsim-files/snz/Heinsberg/Heinsberg_smallerArea/episim-input/he_small_2020_snz_entirePopulation_noPlans_withDistricts.xml.gz")
	private Path output;

	@CommandLine.Option(names = "--attr", description = "Attribute name in the shapefile, which contains the district name", defaultValue = "name_2")
	private String attr;

	@CommandLine.Option(names = "--input-crs", description = "Overwrite CRS of the population home coordinates. " +
			"If not given it will be read from the population attributes.", defaultValue = "null")
	private String inputCRS;

	// because of the own transformation classes of matsim, the crs can not be easily read from the shapefile, but has to be explicitly defined
	@CommandLine.Option(names = "--crs", description = "CRS of the shapefile.", defaultValue = "EPSG:25832")
	private String shapeCRS;

	public static void main(String[] args) {
		System.exit(new CommandLine(new DistrictLookup()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

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

				String district = index.query(x, y);

				p.getAttributes().putAttribute("district", district);
			} catch (RuntimeException e) {
				unknown++;
			}
		}

		// Simple check if the lookup might be wrong
		if (unknown >= population.getPersons().size() * 0.5) {
			log.error("District lookup failed for {} out of {} persons.", unknown, population.getPersons().size());
			return 1;
		}

		log.info("Finished with failed lookup for {} out of {} persons.", unknown, population.getPersons().size());

		PopulationUtils.writePopulation(population, output.toString());

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

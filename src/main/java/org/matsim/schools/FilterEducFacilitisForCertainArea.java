package org.matsim.schools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.facilities.ActivityFacility;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.collect.Multimap;

public class FilterEducFacilitisForCertainArea {

	static final Logger log = Logger.getLogger(AnalyzeU14Population.class);

	private static final String workingDir = "../shared-svn/projects/episim/matsim-files/snz/";
	private static final String pathOfEduFacilitiesGER = workingDir + "Deutschland/de_facilities.education.xy";
	private static final String ShapeFile = workingDir + "Munich/Shape-File/dilutionArea.shp";
	private static final String outputEducFileDir = workingDir + "Munich/educFacilities.txt";
	private static List<EducFacility> educList = new ArrayList<>();
	private static List<EducFacility> educListNewArea = new ArrayList<>();

	public static void main(String[] args) throws IOException {

		readEducFacilityFile();

		Collection<SimpleFeature> shapefileCertainArea = ShapeFileReader.getAllFeatures(ShapeFile);
		Geometry geometryOfCertainArea = (Geometry) shapefileCertainArea.iterator().next().getDefaultGeometry();
		educList.forEach((educFacility) -> filterEducFacilitiesForCertainArea(educFacility,geometryOfCertainArea));
		log.info("Found facilities in certain area: " + educListNewArea.size());
		writeOutputFile();
	}

	private static void filterEducFacilitiesForCertainArea(EducFacility educFacility, Geometry geometryOfCertainArea) {
		if (geometryOfCertainArea.contains(MGC.coord2Point(educFacility.getCoord())))
			educListNewArea.add(educFacility);
	}

	private static void readEducFacilityFile() throws IOException {

		log.info("Read inputfile...");

		BufferedReader reader = new BufferedReader(new FileReader(pathOfEduFacilitiesGER));

		int ii = -1;

		for (String line = reader.readLine(); line != null; line = reader.readLine()) {

			ii++;

			if (ii == 0) {
				continue;
			}

			String[] parts = line.split("\t");

			Id<ActivityFacility> id = Id.create(parts[0], ActivityFacility.class);
			double x = Double.parseDouble(parts[1]);
			double y = Double.parseDouble(parts[2]);

			String educKiga = parts[7];
			boolean isEducKiga = false;
			if (!educKiga.equals("0.0")) {
				isEducKiga = true;
			}

			String educPrimary = parts[8];
			boolean isEducPrimary = false;
			if (!educPrimary.equals("0.0")) {
				isEducPrimary = true;
			}

			String educSecondary = parts[9];
			boolean isEducSecondary = false;
			if (!educSecondary.equals("0.0")) {
				isEducSecondary = true;
			}

			EducFacility educFacility = new EducFacility(id, x, y, isEducKiga, isEducPrimary, isEducSecondary);

			educList.add(educFacility);
		}
		reader.close();
		log.info("Read input-Facilities: " + educList.size());

	}
	private static void writeOutputFile() {

		FileWriter writer;
		File file;
		file = new File(outputEducFileDir);
		try {
			writer = new FileWriter(file, true);
			writer.write("id\tx\ty\teduc_kiga\teduc_primary\teduc_secondary\n");
			for(EducFacility educFacility: educListNewArea) {
				int isKiga =0;
				int isPrimary =  0;
				int isSecondary = 0;
				if(educFacility.isEducKiga)
					isKiga=1;
				if (educFacility.isEducPrimary)
					isPrimary=1;
				if (educFacility.isEducSecondary)
					isSecondary=1;
				writer.write(educFacility.getId() + "\t" + educFacility.getCoord().getX() + "\t" + educFacility.getCoord().getY() + "\t"
						+ isKiga + "\t" + isPrimary + "\t"+isSecondary+"\n");
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.info("Wrote result file under " + outputEducFileDir);
	}
	private static class EducFacility {
		private Id<ActivityFacility> id;
		private Coord coord;
		private boolean isEducKiga;
		private boolean isEducPrimary;
		private boolean isEducSecondary;
		private double noOfPupils = 0;

		EducFacility(Id<ActivityFacility> id, double x, double y, boolean isEducKiga, boolean isEducPrimary,
				boolean isEducSecondary) {
			this.setId(id);
			this.setCoord(CoordUtils.createCoord(x, y));
			this.setEducKiga(isEducKiga);
			this.setEducPrimary(isEducPrimary);
			this.setEducSecondary(isEducSecondary);
		}

		public Id<ActivityFacility> getId() {
			return id;
		}

		public void setId(Id<ActivityFacility> id) {
			this.id = id;
		}

		public boolean isEducKiga() {
			return isEducKiga;
		}

		public void setEducKiga(boolean isEducKiga) {
			this.isEducKiga = isEducKiga;
		}

		public boolean isEducPrimary() {
			return isEducPrimary;
		}

		public void setEducPrimary(boolean isEducPrimary) {
			this.isEducPrimary = isEducPrimary;
		}

		public boolean isEducSecondary() {
			return isEducSecondary;
		}

		public void setEducSecondary(boolean isEducSecondary) {
			this.isEducSecondary = isEducSecondary;
		}

		public Coord getCoord() {
			return coord;
		}

		public void setCoord(Coord coord) {
			this.coord = coord;
		}

	}

}

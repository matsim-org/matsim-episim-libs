package org.matsim.schools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class findEducSecondaryFacilities {

	static final Logger log = Logger.getLogger(findEducSecondaryFacilities.class);

	private static final String workingDir = "../shared-svn/projects/episim/matsim-files/snz/";
	private static final String pathOfUsedPopulation = workingDir + "optimizedPopulation_withoutNetworkInfo.xml.gz";
	private static final String outputFileDir = workingDir + "educ_secondrayFacilities.txt";

	public static void main(String[] args) {

		Population populationAll = PopulationUtils.readPopulation(pathOfUsedPopulation);
		Multimap<String, String[]> educ_secondaryFacilities = ArrayListMultimap.create();
		for (Person singlePerson : populationAll.getPersons().values()) {
			int count = 0;
			while (singlePerson.getSelectedPlan().getPlanElements().size() > count) {
				if (count % 2 == 0 || count == 0) {
					Activity h = (Activity) singlePerson.getSelectedPlan().getPlanElements().get(count);
					if (h.getType().contains("educ_secondary")) {
						String facilityId = h.getFacilityId().toString();
						if (!educ_secondaryFacilities.containsKey(facilityId)) {
							String[] values = new String[3];

							values[0] = facilityId;
							values[1] = Double.toString(h.getCoord().getX());
							values[2] = Double.toString(h.getCoord().getY());
							educ_secondaryFacilities.put(facilityId, values);
						}
						break;
					}
				}
				count++;
			}
		}
		log.info("Number of secondary facilities: " + educ_secondaryFacilities.size());
		writeOutputFile(educ_secondaryFacilities);
	}

	private static void writeOutputFile(Multimap<String, String[]> educ_secondaryFacilities) {

		FileWriter writer;
		File file;
		file = new File(outputFileDir);
		try {
			writer = new FileWriter(file, true);
			writer.write("id\tx\ty\n");
			for (String[] facilityId : educ_secondaryFacilities.values()) {
				writer.write(facilityId[0] + "\t" + facilityId[1] + "\t" + facilityId[2] + "\n");
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.info("Wrote result file under " + outputFileDir);
	}
}

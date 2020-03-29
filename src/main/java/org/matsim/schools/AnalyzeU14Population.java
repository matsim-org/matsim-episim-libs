package org.matsim.schools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;

public class AnalyzeU14Population {
	
	static final Logger log = Logger.getLogger(AnalyzeU14Population.class);

	private static final String workingDir = "../shared-svn/projects/episim/matsim-files/snz/";
	private static final String pathOfBerlinU14Population = workingDir
			+ "population_fromPopulationAttributes_BerlinOnly.xml.gz";
	private static final String outputResultFile = workingDir +"analyzeU14population.txt"; 

	public static void main(String[] args) {

		Population population = PopulationUtils.readPopulation(pathOfBerlinU14Population);
		log.info("Start analyzing the population...");
		analyzePopulationAndWriteResult(population);
	}

	private static void analyzePopulationAndWriteResult(Population population) {

		int persons0_1 = 0;
		int persons2_5 = 0;
		int persons6_12 = 0;
		int persons13_14 = 0;

		for (Person singlePerson : population.getPersons().values()) {
			int age = (int) singlePerson.getAttributes().getAttribute("age");

			if (0 <= age && age <= 1) {
				persons0_1++;
			}
			if (2 <= age && age <= 5) {
				persons2_5++;
			}
			if (6 <= age && age <= 12) {
				persons6_12++;
			}
			if (13 <= age && age <= 14) {
				persons13_14++;
			}
		}
		log.info("Finished analyzing the population.");
		log.info("Start analyzing the population...");
		FileWriter writer;
		File file;
		file = new File(outputResultFile);
		try {
			writer = new FileWriter(file, true);
			writer.write("Amount of persons in population file: " + population.getPersons().values().size() + "\n\n");
			writer.write("Amount of counted persons from age 0 to 1: " + persons0_1 + "  --> stay at home for childhood\n\n");
			writer.write("Amount of counted persons from age 2 to 5: " + persons2_5 + "  --> going to the kindergarden\n\n");
			writer.write("Amount of counted persons from age 6 to 12: " + persons6_12 + "   --> going to the primary school\n\n");
			writer.write("Amount of counted persons from age 13 to 14: " + persons13_14 + "   --> going to the secondary school\n\n");
			writer.write("Amount of counted persons: " + (persons0_1 + persons2_5 + persons6_12 + persons13_14));
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.info("Wrote result file under "+outputResultFile);

	}

}

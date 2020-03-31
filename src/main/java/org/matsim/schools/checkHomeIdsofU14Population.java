package org.matsim.schools;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;

public class checkHomeIdsofU14Population {

	static final Logger log = Logger.getLogger(AnalyzeU14Population.class);

	private static final String workingDir = "../shared-svn/projects/episim/matsim-files/snz/";
	private static final String pathOfBerlinU14Population = workingDir
			+ "population_fromPopulationAttributes_BerlinOnly.xml.gz";
	private static final String pathOfUsedPopulation = workingDir + "optimizedPopulation_withoutNetworkInfo.xml.gz";

	public static void main(String[] args) {

		List<String> homeIdsU14Population = new ArrayList<String>();
		homeIdsU14Population = getHomeIdsU14(homeIdsU14Population);

		List<String> homeIdsPopulationAll = new ArrayList<String>();
		homeIdsPopulationAll = getHomeIdsAll(homeIdsPopulationAll);

		int numberOfHomeIdsNotInAllPopulation = 0;
		int count = 0;
		while (count < homeIdsU14Population.size()) {
			if (!homeIdsPopulationAll.contains(homeIdsU14Population.get(count)))
				numberOfHomeIdsNotInAllPopulation++;
			count++;
		}
		log.info("U14homeIds not in all population: " + numberOfHomeIdsNotInAllPopulation);
		log.info("Finished");
	}

	private static List<String> getHomeIdsAll(List<String> homeIdsPopulationAll) {
		Population populationAll = PopulationUtils.readPopulation(pathOfUsedPopulation);
		for (Person singlePerson : populationAll.getPersons().values()) {
			Activity h = (Activity) singlePerson.getSelectedPlan().getPlanElements().get(0);
			if (h.getType().contains("home")) {
				String homeId = h.getFacilityId().toString();
				if (!homeIdsPopulationAll.contains(homeId))
					homeIdsPopulationAll.add(homeId);
			} else
				new RuntimeException("find no home activity");
		}
		log.info("Amount of total persons: " + populationAll.getPersons().size());
		log.info("Amount of different homeIds of all persons: " + homeIdsPopulationAll.size());
		return homeIdsPopulationAll;
	}

	private static List<String> getHomeIdsU14(List<String> homeIdsU14Population) {
		Population populationU14 = PopulationUtils.readPopulation(pathOfBerlinU14Population);
		for (Person singlePerson : populationU14.getPersons().values()) {
			String homeId = (String) singlePerson.getAttributes().getAttribute("homeId");
			if (!homeIdsU14Population.contains(homeId))
				homeIdsU14Population.add(homeId);
		}
		log.info("Amount of U14 persons: " + populationU14.getPersons().size());
		log.info("Amount of different homeIds of U14: " + homeIdsU14Population.size());
		return homeIdsU14Population;
	}

}

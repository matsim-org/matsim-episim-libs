package org.matsim.scenarioCreation;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;

import java.io.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MergeAttributesIntoExistingPopulation {


	public static void main(String[] args) throws IOException {


		File attributesFile = new File("D:/Dropbox/Documents/VSP/episim/de2020-gsm-wt-100pct_populationAttributes.xml");
		String inputPopulation = "C:/Users/jakob/projects/shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_andNeighborhood_25pt_split.xml.gz";
		String outputPopulation = "C:/Users/jakob/projects/shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_andNeighborhood_andIncome_25pt_split.xml.gz";

		// Read Population
//		Population population = PopulationUtils.readPopulation(inputPopulation);
//		Set<String> agentsInPopulation = new HashSet<>();
//		for (Map.Entry<Id<Person>, ? extends Person> personEntry : population.getPersons().entrySet()) {
//			agentsInPopulation.add(personEntry.getKey().toString());
//		}


		// Read Attributes File
		BufferedReader atrReader = new BufferedReader(new FileReader(attributesFile));
		String atrStrCurrentLine;

		long lineCnt = 0;
		long agentCnt = 0;
		int matchCnt = 0;
		int matchCnt1 = 0;
		int matchCnt2 = 0;
		Person relevantAgent = null;
		Pattern pattern = Pattern.compile(">(.*?)</attribute");
		while ((atrStrCurrentLine = atrReader.readLine()) != null) {
			lineCnt++;

			if (atrStrCurrentLine.contains("<object id=")) {
				String personId = atrStrCurrentLine.split("\"")[1];

//				if (agentsInPopulation.contains(personId)) {
//					matchCnt++;
//					relevantAgent = population.getPersons().get(Id.createPersonId(personId));
//				}
				agentCnt++;
				if (agentCnt % 1_000_000 == 0) {
					System.out.println(agentCnt);
				}

			} else if (atrStrCurrentLine.contains("</object>")) {
				relevantAgent = null;
			} else if (relevantAgent != null && atrStrCurrentLine.contains("MiD:hheink_gr2")) {
				Matcher matcher = pattern.matcher(atrStrCurrentLine);
				if (matcher.find()) {
					String att = matcher.group(1);
					relevantAgent.getAttributes().putAttribute("MiD:hheink_gr2", att);
					matchCnt1++;
				} else {
					System.out.println("Attribute not found for agent " + relevantAgent.getId().toString());
				}

			} else if (relevantAgent != null && atrStrCurrentLine.contains("MiD:hhgr_gr")) {
				Matcher matcher = pattern.matcher(atrStrCurrentLine);
				if (matcher.find()) {
					String att = matcher.group(1);
					relevantAgent.getAttributes().putAttribute("MiD:hhgr_gr", att);
					matchCnt2++;
				} else {
					System.out.println("Attribute not found for agent " + relevantAgent.getId().toString());
				}


			}


		}


//		int popSize = population.getPersons().size();
//		int noMatchCnt = popSize - matchCnt;


		// Checks:
		System.out.println("line count: " + lineCnt);

		System.out.println("attribute size: " + agentCnt);
//		System.out.println("population size: " + popSize);


//		System.out.println("match count: " + matchCnt);
//		System.out.println("incorrec cnt: " + noMatchCnt);
//
//		System.out.println("att 1: " + matchCnt1);
//		System.out.println("att 2: " + matchCnt2);

//		PopulationUtils.writePopulation(population, outputPopulation);
	}
}


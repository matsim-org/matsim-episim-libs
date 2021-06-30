package org.matsim.scenarioCreation;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DistrictLookupBerlinPopulation {

	public static void main(String[] args) throws IOException {

		boolean addAttributeToPopulationFile = false;
		boolean countAgentsInEachDistrict = true;
		boolean printPersonToDistrictMap = false;

		String inputPopulation = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz";
		String outputPopulation = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_andNeighborhood_25pt_split.xml.gz";

		String shapeFile = "../public-svn/matsim/scenarios/countries/de/episim/original-data/PLZ-in-germany/plz-gebiete.shp";
		String shapeCRS = "EPSG:4326";


		//Berlin Neighborhood Zipcodes
		HashMap<String, IntSet> subdistricts = new HashMap<>();
		subdistricts.put("Mitte", new IntOpenHashSet(List.of(10115, 10559, 13355, 10117, 10623, 13357, 10119, 10785, 13359, 10787, 10557, 13353, 10555, 13351, 13349, 10551, 13347)));
		subdistricts.put("Friedrichshain_Kreuzberg", new IntOpenHashSet(List.of(10179, 10967, 10243, 10969, 10245, 10997, 10247, 10999, 12045, 10961, 10963, 10965, 10178)));
		subdistricts.put("Pankow", new IntOpenHashSet(List.of(10249, 10405, 10407, 10409, 10435, 10437, 10439, 13051, 13053, 13086, 13088, 13089, 13125, 13127, 13129, 13156, 13158, 13159, 13187, 13189)));
		subdistricts.put("Charlottenburg_Wilmersdorf", new IntOpenHashSet(List.of(10553, 10585, 10587, 10589, 10625, 10627, 10629, 10707, 10709, 10711, 10713, 10715, 10717, 10719, 10789, 13597, 13627, 14050, 14053, 14055, 14057, 14059)));
		subdistricts.put("Spandau", new IntOpenHashSet(List.of(13581, 13583, 13585, 13587, 13589, 13591, 13593, 13595, 13599, 14052, 14089)));
		subdistricts.put("Steglitz_Zehlendorf", new IntOpenHashSet(List.of(12163, 12165, 12167, 12169, 12203, 12205, 12207, 12209, 12247, 12279, 14109, 14129, 14163, 14165, 14167, 14169, 14193, 14195, 14199)));
		subdistricts.put("Tempelhof_Schoeneberg", new IntOpenHashSet(List.of(10777, 10779, 10781, 10783, 10823, 10825, 10827, 14197, 10829, 12101, 12103, 12105, 12109, 12157, 12159, 12161, 12249, 12277, 12307, 12309)));
		subdistricts.put("Neukoelln", new IntOpenHashSet(List.of(12043, 12047, 12049, 12051, 12053, 12055, 12057, 12059, 12099, 12107, 12305, 12347, 12349, 12351, 12353, 12355, 12357, 12359)));
		subdistricts.put("Treptow_Koepenick", new IntOpenHashSet(List.of(12435, 12437, 12439, 12459, 12487, 12489, 12524, 12526, 12527, 12555, 12557, 12559, 12587, 12589, 12623)));
		subdistricts.put("Marzahn_Hellersdorf", new IntOpenHashSet(List.of(12619, 12621, 12627, 12629, 12679, 12681, 12683, 12685, 12687, 12689)));
		subdistricts.put("Lichtenberg", new IntOpenHashSet(List.of(10315, 13057, 10317, 10318, 10319, 10365, 10367, 10369, 13055, 13059)));
		subdistricts.put("Reinickendorf", new IntOpenHashSet(List.of(13403, 13405, 13407, 13409, 13435, 13437, 13439, 13465, 13467, 13469, 13503, 13505, 13507, 13509, 13629)));


		Population population = PopulationUtils.readPopulation(inputPopulation);

		String populationCRS = "EPSG:25832";

		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(populationCRS, shapeCRS);

		DistrictLookup.Index index = new DistrictLookup.Index(new File(shapeFile), ct, "plz");


		Map<String, String> personIdToNeighborhoodMap = new HashMap<>();

		// Count errors
		int unknown = 0;
		for (Person p : population.getPersons().values()) {

			try {
				double x = (double) p.getAttributes().getAttribute("homeX");
				double y = (double) p.getAttributes().getAttribute("homeY");

				String plz = index.query(x, y);
				p.getAttributes().putAttribute("zipcode", plz);

				for (String neighborhoodName : subdistricts.keySet()) {
					if (subdistricts.get(neighborhoodName).contains(Integer.parseInt(plz))) {
						p.getAttributes().putAttribute("subdistrict", neighborhoodName);
						personIdToNeighborhoodMap.put(p.getId().toString(), neighborhoodName);
					}
				}


			} catch (RuntimeException e) {
				unknown++;
			}
		}

		//		 Simple check if the lookup might be wrong
//		if (unknown >= population.getPersons().size() * 0.5) {
//			log.error("zipcode lookup failed for {} out of {} persons.", unknown, population.getPersons().size());
//			return 1;
//		}

//		log.info("Finished with failed lookup for {} out of {} persons.", unknown, population.getPersons().size());

		if (addAttributeToPopulationFile) {

			PopulationUtils.writePopulation(population, outputPopulation);

		}


		if (printPersonToDistrictMap) {
			FileWriter writer = new FileWriter("PersonToDistrictMap.txt");
			BufferedWriter writer1 = new BufferedWriter(writer);
			for (Map.Entry<String, String> personIdToNeighbhorhood : personIdToNeighborhoodMap.entrySet()) {
				writer1.write(personIdToNeighbhorhood.getKey() + ";" + personIdToNeighbhorhood.getValue());
				writer1.newLine();
			}
			writer1.close();
			writer.close();
		}

		Map<String, Integer> agentCntPerDistrict = new HashMap<>();
		if (countAgentsInEachDistrict) {
			for (String district : personIdToNeighborhoodMap.values()) {
				Integer cnt = agentCntPerDistrict.getOrDefault(district, 0) + 1;
				agentCntPerDistrict.put(district, cnt);
			}

			FileWriter writer = new FileWriter("AgentCntPerDistrict.txt");
			BufferedWriter writer1 = new BufferedWriter(writer);
			for (Map.Entry<String, Integer> entry : agentCntPerDistrict.entrySet()) {
				String str = entry.getKey() + ";" + entry.getValue();
				System.out.println(str);
				writer1.write(str);
				writer1.newLine();
			}
			writer1.close();
			writer.close();

		}



	}

}


/* *********************************************************************** *
b	 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.episim.analysis;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.population.PopulationUtils;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Class counts the persons in the events starting and ending an activity type
 * and calculates the average of the duration of for each type.
 *
 * @author rewert
 */
@CommandLine.Command(
		name = "activity-duration",
		description = "Counts and calculates average duration for activity types."
)
public class REAcitivityDurationAnalysis implements Callable<Integer> {

	@CommandLine.Option(names = "--events", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/CologneV2/episim-input/cologne_snz_episim_events_wt_25pt_split.xml.gz")
	private Path eventFile;

	@CommandLine.Option(names = "--population", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/CologneV2/episim-input/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz")
	private Path populationFile;

	@CommandLine.Option(names = "--district", defaultValue = "Köln")
	private String district;

	@CommandLine.Option(names = "--output")
	private Path output;


	private final Map<String, Object2DoubleMap<String>> startedActivitiesMap = new HashMap<>();
	private final Map<String, Object2DoubleMap<String>> finishedActivitiesMap = new HashMap<>();
	private final Map<String, Object2DoubleMap<String>> personActivitiesMap = new HashMap<>();
	private final Object2IntMap<String> countActivities = new Object2IntOpenHashMap<>();

	public static void main(String[] args) {
		System.exit(new CommandLine(new REAcitivityDurationAnalysis()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		Population population = PopulationUtils.readPopulation(populationFile.toString());

		EventsManager events = EventsUtils.createEventsManager();

		REActivityDurationEventHandler reActivityDurationEventHandler = new REActivityDurationEventHandler(population, startedActivitiesMap,
				finishedActivitiesMap, personActivitiesMap, countActivities, district);

		events.addHandler(reActivityDurationEventHandler);

		MatsimEventsReader reader = new MatsimEventsReader(events);

		reader.readFile(eventFile.toString());

		// if activity has not finished until 2 am they will finished at 2 am
		for (String activityType : startedActivitiesMap.keySet()) {
			for (String personId : startedActivitiesMap.get(activityType).keySet()) {
				countActivities.replace(activityType, countActivities.get(activityType) + 1);
				if (finishedActivitiesMap.get(activityType).containsKey(personId)) {
					double durationBefore = finishedActivitiesMap.get(activityType).get(personId);
					double additiotionalDuration = 93600. - startedActivitiesMap.get(activityType).get(personId);
					finishedActivitiesMap.get(activityType).replace(personId, durationBefore + additiotionalDuration);
					personActivitiesMap.get(personId).replace(activityType, durationBefore + additiotionalDuration);
				} else {
					double additiotionalDuration = 93600. - startedActivitiesMap.get(activityType).get(personId);
					finishedActivitiesMap.get(activityType).put(personId, additiotionalDuration);
					personActivitiesMap.get(personId).put(activityType, additiotionalDuration);
				}
			}
		}

		for (String personID : personActivitiesMap.keySet()) {
			double sumAct = 0;
			for (double sum : personActivitiesMap.get(personID).values())
				sumAct = sumAct + sum;
			if (sumAct != 86400.) {
				throw new RuntimeException("Day of Person has not 24h");
			}
		}
		int countPersons = 0;
		for (Person person : population.getPersons().values()) {
			if (person.getAttributes().getAttribute("district") != null
					&& person.getAttributes().getAttribute("district").toString().equals(district))
				countPersons++;
			else
				continue;

			// checks if a person had no activities and add a 24h home activity
			boolean containsPerson = false;
			if (!countActivities.containsKey("onlyHome"))
				countActivities.put("onlyHome", 0);
			for (Object2DoubleMap<String> personEndSet : finishedActivitiesMap.values())
				if (personEndSet.containsKey(person.getId().toString()))
					containsPerson = true;
			if (!containsPerson) {
				countActivities.replace("onlyHome", countActivities.get("onlyHome") + 1);
				if (!finishedActivitiesMap.containsKey("onlyHome")) {
					Object2DoubleMap<String> personSet = new Object2DoubleOpenHashMap<>();
					finishedActivitiesMap.put("onlyHome", personSet);
				}
				String personId = person.getId().toString();
				Object2DoubleMap<String> personSetEnd = finishedActivitiesMap.get("onlyHome");
				double fullDayTime = 86400.;
				personSetEnd.put(personId, fullDayTime);
			}
		}

		if (output == null)
			output = Path.of(district + "Activities.csv");

		try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(output), CSVFormat.DEFAULT)) {

			printer.printRecord("Aktivität", "Anzahl Personen", "Mittlere Dauer pro Person mit dieser Aktivität", "Mittlere Dauer Population" , "Anzahl Aktivitäten", "Mittlere Dauer pro Aktivität");

			for (String activityType : finishedActivitiesMap.keySet()) {
				double sumDurations = 0;
				for (String personId : finishedActivitiesMap.get(activityType).keySet()) {
					sumDurations = sumDurations + finishedActivitiesMap.get(activityType).get(personId);
				}

				printer.printRecord(
						activityType,
						finishedActivitiesMap.get(activityType).size() * 4 ,
						sumDurations / finishedActivitiesMap.get(activityType).size() / 3600,
						finishedActivitiesMap.get(activityType).size() * (sumDurations / finishedActivitiesMap.get(activityType).size() / 3600) / countPersons,
						countActivities.get(activityType) * 4, (sumDurations / countActivities.get(activityType) / 3600));
			}

			System.out.printf("Number of persons in population in %s: %d\n", district, countPersons * 4);

		}

		return 0;
	}
}

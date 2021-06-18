package org.matsim.episim.analysis;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.population.PopulationUtils;

public class REAnalyzeTimelineOfEvents {

	private final static Map<String, Map<Integer, Integer>> mapOfActivityEnds = new HashMap<>();
	private static int numberEndAct = 0;
	private static Population population;

	public static void main(String[] args) {
//		String inputFileEvents = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_episim_events_wt_25pt_split.xml.gz";
		String inputFileEvents = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_episim_events_sa_25pt_split.xml.gz";
//		String inputFileEvents = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_episim_events_so_25pt_split.xml.gz";
		String inputFilePop = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz";

		population = PopulationUtils.readPopulation(inputFilePop);

		EventsManager events = EventsUtils.createEventsManager();

		REActivityTimelinenEventHandler reActivityDurationEventHandler = new REActivityTimelinenEventHandler();

		events.addHandler(reActivityDurationEventHandler);

		MatsimEventsReader reader = new MatsimEventsReader(events);

		reader.readFile(inputFileEvents);

		for (int i = 0; i < Types.values().length; i++) {
			System.out.print(Types.values()[i].toString() + ";");
		}

		for (Integer hours = 0; hours < 30; hours++) {
			System.out.println();
			System.out.print(hours + ";");
			for (int i = 0; i < Types.values().length; i++) {

				String actType = Types.values()[i].toString();
				if (mapOfActivityEnds.get(actType).containsKey(hours))
					System.out.print(mapOfActivityEnds.get(actType).get(hours) * 4 + ";");
				else
					System.out.print(0 + ";");
			}
			System.out.println();
		}
		System.out.println("Gesamt: " + numberEndAct * 4);

	}

	private static class REActivityTimelinenEventHandler implements ActivityEndEventHandler {

		@Override
		public void handleEvent(ActivityEndEvent event) {

			if (population.getPersons().get(event.getPersonId()).getAttributes().getAttribute("district") != null
					&& population.getPersons().get(event.getPersonId()).getAttributes().getAttribute("district")
							.toString().equals("Berlin")) {
				numberEndAct++;
				Integer beginningHour = Integer.valueOf((int) Math.floor(event.getTime() / 3600));
				if (mapOfActivityEnds.containsKey(event.getActType()))
					if (mapOfActivityEnds.get(event.getActType()).containsKey(beginningHour))
						mapOfActivityEnds.get(event.getActType()).put(beginningHour,
								mapOfActivityEnds.get(event.getActType()).get(beginningHour) + 1);
					else {
						mapOfActivityEnds.get(event.getActType()).put(beginningHour, 1);
					}
				else {
					mapOfActivityEnds.put(event.getActType(), new HashMap<>());
					mapOfActivityEnds.get(event.getActType()).put(beginningHour, 1);
				}
			}
		}
	}

	private enum Types {
		educ_kiga, business, errands, home, leisure, shop_daily, shop_other, visit, work, educ_higher, educ_tertiary,
		educ_other, educ_primary, educ_secondary
	}
}

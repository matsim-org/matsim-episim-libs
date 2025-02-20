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
package org.matsim.episim.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.episim.ReplayHandler;
import org.matsim.facilities.ActivityFacilitiesImpl;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.run.modules.SnzBrandenburgProductionScenario;
import org.matsim.scenarioCreation.FilterHandler;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static picocli.CommandLine.*;


public class AnalyzeCommuterPopulation {





	public static void main(String[] args) {

		Path basePath = Path.of("../shared-svn/projects/episim/matsim-files/snz/BerlinBrandenburg/episim-input");
		Path populationPath = basePath.resolve("bb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered_split.xml.gz");


		Path output = Path.of("tmp_output");

		List<Path> eventFiles = List.of(basePath.resolve("bb_2020-week_snz_episim_events_wt_100pt_split.xml.gz"), basePath.resolve("bb_2020-week_snz_episim_events_sa_100pt_split.xml.gz"),basePath.resolve("bb_2020-week_snz_episim_events_so_100pt_split.xml.gz"));

		Path facilitiesPath = basePath.resolve("bb_2020-week_snz_episim_facilities_100pt_withDistricts.xml.gz");

//		if (!Files.exists(populationPath)) {
//			throw new RuntimeException("no path");
//		}
//
//		if (!Files.exists(output)) {
//			try {
//				Files.createDirectories(output);
//			} catch (IOException e) {
//				throw new RuntimeException(e);
//			}
//		}
//

		Population population = PopulationUtils.readPopulation(populationPath.toString());

		Set<Id<Person>> berlinDwellers = new HashSet<>();
		Set<Id<Person>> brandDwellers = new HashSet<>();

		// Categorize by home location

		for (Person person : population.getPersons().values()) {
			String district = (String) person.getAttributes().getAttribute("district");
			if (district == null) {
				continue;
			} else if (Objects.equals(district, "Berlin")) {
				berlinDwellers.add(person.getId());
			} else if (SnzBrandenburgProductionScenario.BRANDENBURG_LANDKREISE.contains(district)) {
				brandDwellers.add(person.getId());
			}
		}


		System.out.println("Berlin Dwellers: " + berlinDwellers.size());
		System.out.println("Brandenburg Dwellers: " + brandDwellers.size());



		// now let us categorize the facilities
		if (!Files.exists(facilitiesPath)) {
			throw new RuntimeException();
		}

		ActivityFacilitiesImpl facilities = new ActivityFacilitiesImpl();
		MatsimFacilitiesReader fReader = new MatsimFacilitiesReader(null, null, facilities);
		fReader.parse(IOUtils.getInputStream(IOUtils.getFileUrl(facilitiesPath.toString())));

		System.out.println(facilities.getFacilities().size());

		Set<Id<ActivityFacility>> berlinFacs = new HashSet<>();
		Set<Id<ActivityFacility>> brandFacs = new HashSet<>();

		for (ActivityFacility fac: facilities.getFacilities().values()) {
			String district = (String) fac.getAttributes().getAttribute("district");
			if (district == null) {
				continue;
			} else if (Objects.equals(district, "Berlin")) {
				berlinFacs.add(fac.getId());
			} else if (SnzBrandenburgProductionScenario.BRANDENBURG_LANDKREISE.contains(district)) {
				brandFacs.add(fac.getId());
			}
		}



		System.out.println("Berlin Facilities: " + berlinFacs.size());
		System.out.println("Brandenburg Facilities: " + brandFacs.size());


		// and now the events

		if (!eventFiles.stream().allMatch(Files::exists)) {
			throw new RuntimeException();
		}

		Set<Id<Person>> personWithActsInBerlin = new HashSet<>();
		Set<Id<Person>> personWithActsInBrand = new HashSet<>();
		for (Path events : eventFiles) {

			EventsManager manager = EventsUtils.createEventsManager();
			CommuterHandler handler = new CommuterHandler(berlinFacs, brandFacs);
			manager.addHandler(handler);
			EventsUtils.readEvents(manager, events.toString());

			personWithActsInBerlin.addAll(handler.getPersonsWithBizInBerlin());
			personWithActsInBrand.addAll(handler.getPersonsWithBizInBrand());
		}

		System.out.println("People with acts in Berlin: " + personWithActsInBerlin.size());
		System.out.println("People with acts in Brandenburg: " + personWithActsInBrand.size());


		// and now the cross

		Set<Id<Person>> commutersBerlinToBrand = new HashSet<>(berlinDwellers);
		commutersBerlinToBrand.retainAll(personWithActsInBrand);
		System.out.println("Commuters Berlin -> Brandenburg: " + commutersBerlinToBrand.size());

		Set<Id<Person>> commutersBrandToBerlin = new HashSet<>(brandDwellers);
		commutersBrandToBerlin.retainAll(personWithActsInBerlin);
		System.out.println("Commuters Brandenburg -> Berlin: " + commutersBrandToBerlin.size());

	}

	public static class CommuterHandler implements ActivityEndEventHandler, ActivityStartEventHandler {



		private final Set<Id<Person>> personsWithBizInBerlin;
		private final Set<Id<Person>> personsWithBizInBrand;

		private final Set<Id<ActivityFacility>> berlinFacs;
		private final Set<Id<ActivityFacility>> brandFacs;


		/**
		 * Constructor.
		 */
		public CommuterHandler(Set<Id<ActivityFacility>> berlinFacs, Set<Id<ActivityFacility>> brandFacs) {
			this.personsWithBizInBerlin = new HashSet<>();
			this.personsWithBizInBrand = new HashSet<>();

			this.berlinFacs = berlinFacs;
			this.brandFacs = brandFacs;
		}

		public Set<Id<Person>> getPersonsWithBizInBerlin() {
			return personsWithBizInBerlin;
		}

		public Set<Id<Person>> getPersonsWithBizInBrand() {
			return personsWithBizInBrand;
		}


		@Override
		public void handleEvent(ActivityEndEvent event) {

			if (berlinFacs.contains(event.getFacilityId())) {
				personsWithBizInBerlin.add(event.getPersonId());
			} else if (brandFacs.contains(event.getFacilityId())) {
				personsWithBizInBrand.add(event.getPersonId());
			}
		}

		@Override
		public void handleEvent(ActivityStartEvent event) {
			if (berlinFacs.contains(event.getFacilityId())) {
				personsWithBizInBerlin.add(event.getPersonId());
			} else if (brandFacs.contains(event.getFacilityId())) {
				personsWithBizInBrand.add(event.getPersonId());
			}
		}

	}


}

package org.matsim.episim.model.listener;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.model.SimulationListener;
import org.matsim.episim.model.VirusStrain;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import java.io.BufferedWriter;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Writes contacts for each day.
 */
public class WriteContactsPerDay implements SimulationListener {

	private Map<Id<Person>, EpisimPerson> persons;
	@Inject
	private EpisimReporting reporting;
	private BufferedWriter writer;

	@Override
	public void init(SplittableRandom rnd, Map<Id<Person>, EpisimPerson> persons, Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> facilities, Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicles) {
		this.persons = persons;
	}

	@Override
	public void onIterationEnd(int iteration, LocalDate date) {

		if (writer == null) {
			writer = reporting.registerWriter("contactsPerDay.tsv");
			reporting.writeAsync(writer, "date\tavgContacts\n");
		}

		reporting.writeAsync(writer, date + "\t" + reporting.getTotalContacts() / (double) persons.size() + "\n");

	}
}

package org.matsim.episim.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

public class InitRecoveredPersons implements SimulationListener {

	private static final Logger log = LogManager.getLogger(InitRecoveredPersons.class);

	/**
	 * Scale the number of recovered persons.
	 */
	private final double amount;

	public InitRecoveredPersons(double amount) {
		this.amount = amount;
	}

	@Override
	public void onSnapshotLoaded(int iteration, SplittableRandom rnd, Map<Id<Person>, EpisimPerson> persons, Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> facilities, Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicles) {

		long recovered = persons.values().stream().filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.recovered).count();

		log.info("Scaling {} recovered persons to {}", recovered, recovered * amount);

		List<EpisimPerson> candidates = persons.values().stream()
				.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
				.collect(Collectors.toList());

		int numRecovered = (int) (recovered * amount);

		if (candidates.size() < recovered) {
			log.warn("Not enough persons match the initial recovered requirement...");
		}

		while (numRecovered > 0 && candidates.size() > 0) {
			EpisimPerson randomPerson = candidates.remove(rnd.nextInt(candidates.size()));

			//randomPerson.setInitialInfection(0, VirusStrain.SARS_CoV_2);
			randomPerson.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.recovered);

			numRecovered--;
		}

	}
}

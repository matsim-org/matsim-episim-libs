package org.matsim.episim.model.vaccination;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.model.SimulationListener;
import org.matsim.episim.model.VaccinationType;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import java.time.LocalDate;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Vaccination model executed every iteration.
 */
public interface VaccinationModel extends SimulationListener {

	@Override
	default void init(SplittableRandom rnd, Map<Id<Person>, EpisimPerson> persons, Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> facilities, Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicles) {
		// Nothing done by default
	}

	/**
	 * Perform vaccinations on the given persons
	 *
	 * @param persons               all existing persons.
	 * @param reVaccination         whether this is for vaccination
	 * @param availableVaccinations number of vaccinations available for this day.
	 * @param date                  current date
	 * @param iteration             current iteration
	 * @param now                   current time (start of day)
	 * @return number of people vaccinated
	 */
	int handleVaccination(Map<Id<Person>, EpisimPerson> persons, boolean reVaccination, int availableVaccinations, LocalDate date, int iteration, double now);

	/**
	 * Set vaccination status of a person.
	 */
	default void vaccinate(EpisimPerson p, int iteration, VaccinationType type, boolean reVaccination) {
		if (reVaccination)
			p.setReVaccinationStatus(EpisimPerson.VaccinationStatus.yes, iteration);
		else
			p.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, type, iteration);
	}

	/**
	 * For the first vaccination, chose the {@link VaccinationType} a person is getting.
	 */
	static VaccinationType chooseVaccinationType(Map<VaccinationType, Double> prob, SplittableRandom rnd) {
		double p = rnd.nextDouble();
		for (Map.Entry<VaccinationType, Double> e : prob.entrySet()) {
			if (p < e.getValue())
				return e.getKey();
		}

		return VaccinationType.generic;
	}

}

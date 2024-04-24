package org.matsim.episim.model.vaccination;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.model.SimulationListener;
import org.matsim.episim.model.VaccinationType;

import java.time.LocalDate;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Vaccination model executed every iteration.
 * For historical reasons, this interfaces declares two different methods.
 * One method is used when the model is run with the value specified from config.
 * This is called when class is bound directly to its interface like this:
 * <code>bind(VaccinationModel.class).to(RandomVaccination.class).in(Singleton.class);</code>
 * Only one class can be bound this way.
 *
 * The alternative is to use the set binder, which allows binding multiple vaccination models.
 * In this case {@link #handleVaccination(Map, LocalDate, int, double)} is called instead.
 */
public interface VaccinationModel extends SimulationListener {

	/**
	 * Perform vaccinations on the given persons.
	 *
	 * @param persons   all existing persons.
	 * @param date      current date
	 * @param iteration current iteration
	 * @param now       current time (start of day)
	 */
	default void handleVaccination(Map<Id<Person>, EpisimPerson> persons, LocalDate date, int iteration, double now) {

	}

	/**
	 * Perform vaccinations on the given persons with values supplied from config.
	 *
	 * @param persons               all existing persons.
	 * @param reVaccination         whether this is for vaccination
	 * @param availableVaccinations number of vaccinations available for this day.
	 * @param date                  current date
	 * @param iteration             current iteration
	 * @param now                   current time (start of day)
	 * @return number of people vaccinated
	 */
	default int handleVaccination(Map<Id<Person>, EpisimPerson> persons, boolean reVaccination, int availableVaccinations, LocalDate date, int iteration, double now) {
		return 0;
	}

	/**
	 * Set vaccination status of a person.
	 */
	default void vaccinate(EpisimPerson p, int iteration, VaccinationType type) {
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

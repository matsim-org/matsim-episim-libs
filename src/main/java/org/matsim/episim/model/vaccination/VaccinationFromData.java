package org.matsim.episim.model.vaccination;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.VaccinationConfigGroup;

import java.time.LocalDate;
import java.util.Map;
import java.util.SplittableRandom;

public class VaccinationFromData extends VaccinationByAge {

	public VaccinationFromData(SplittableRandom rnd, VaccinationConfigGroup vaccinationConfig) {
		super(rnd, vaccinationConfig);
	}

	@Override
	public int handleVaccination(Map<Id<Person>, EpisimPerson> persons, boolean reVaccination, int availableVaccinations, LocalDate date, int iteration, double now) {

		// Booster are handled by the default model
		if (reVaccination)
			return super.handleVaccination(persons, true, availableVaccinations, date, iteration, now);




		return 0;
	}

}

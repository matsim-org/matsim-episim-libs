package org.matsim.episim.model.progression;

import org.matsim.episim.EpisimPerson;
import org.matsim.episim.Immunizable;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.VaccinationType;

/**
 * Model to calculate the next disease status of a person. Duration will be drawn from a different model.
 */
@FunctionalInterface
public interface DiseaseStatusTransitionModel {

	/**
	 * Calculate the next disease status of a person.
	 *
	 * @param person the person
	 * @param status current disease status
	 * @param day    current day (iteration number)
	 * @return the next disease status.
	 */
	EpisimPerson.DiseaseStatus decideNextState(EpisimPerson person, EpisimPerson.DiseaseStatus status, int day);


	/**
	 * Factor for showing symptoms depending on vaccination or immunity.
	 */
	default double getShowingSymptomsFactor(EpisimPerson person, VaccinationConfigGroup vaccinationConfig, int day) {

		double a = vaccinationConfig.getMinFactor(person, day, VaccinationConfigGroup.VaccinationParams::getFactorShowingSymptoms);

		double b = (person.getNumInfections() > 0 && person.hadDiseaseStatus(EpisimPerson.DiseaseStatus.recovered) && vaccinationConfig.hasParams(VaccinationType.natural))
				? vaccinationConfig.getParams(VaccinationType.natural).getFactorShowingSymptoms(person.getVirusStrain(), person.daysSince(EpisimPerson.DiseaseStatus.recovered, day))
				: 1d;

		return Math.min(a, b);
	}

	/**
	 * Factor for showing symptoms depending on vaccination or immunity.
	 */
	default double getSeriouslySickFactor(Immunizable person, VaccinationConfigGroup vaccinationConfig, int day) {

		double a = vaccinationConfig.getMinFactor((EpisimPerson) person, day, VaccinationConfigGroup.VaccinationParams::getFactorSeriouslySick);

		double b = (person.getNumInfections() > 0 && person.hadDiseaseStatus(EpisimPerson.DiseaseStatus.recovered) && vaccinationConfig.hasParams(VaccinationType.natural))
				? vaccinationConfig.getParams(VaccinationType.natural).getFactorSeriouslySick(person.getVirusStrain(), person.daysSince(EpisimPerson.DiseaseStatus.recovered, day))
				: 1d;

		return Math.min(a, b);
	}

	/**
	 * Factor for showing symptoms depending on vaccination or immunity.
	 */
	default double getCriticalFactor(Immunizable person, VaccinationConfigGroup vaccinationConfig, int day) {

		double a = vaccinationConfig.getMinFactor((EpisimPerson) person, day, VaccinationConfigGroup.VaccinationParams::getFactorCritical);

		double b = (person.getNumInfections() > 0 && person.hadDiseaseStatus(EpisimPerson.DiseaseStatus.recovered) && vaccinationConfig.hasParams(VaccinationType.natural))
				? vaccinationConfig.getParams(VaccinationType.natural).getFactorCritical(person.getVirusStrain(), person.daysSince(EpisimPerson.DiseaseStatus.recovered, day))
				: 1d;

		return Math.min(a, b);
	}


}

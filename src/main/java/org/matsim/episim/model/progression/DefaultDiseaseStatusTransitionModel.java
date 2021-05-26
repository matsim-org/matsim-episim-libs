package org.matsim.episim.model.progression;

import com.google.inject.Inject;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;

import java.util.SplittableRandom;

public class DefaultDiseaseStatusTransitionModel implements DiseaseStatusTransitionModel {

	private final SplittableRandom rnd;
	private final VaccinationConfigGroup vaccinationConfig;
	private final VirusStrainConfigGroup strainConfig;

	@Inject
	public DefaultDiseaseStatusTransitionModel(SplittableRandom rnd, VaccinationConfigGroup vaccinationConfig,
	                                           VirusStrainConfigGroup strainConfigGroup) {
		this.rnd = rnd;
		this.vaccinationConfig = vaccinationConfig;
		this.strainConfig = strainConfigGroup;
	}

	@Override
	public final EpisimPerson.DiseaseStatus decideNextState(EpisimPerson person, EpisimPerson.DiseaseStatus status) {

		switch (status) {
			case infectedButNotContagious:
				return EpisimPerson.DiseaseStatus.contagious;

			case contagious:
				if (rnd.nextDouble() < getProbaOfTransitioningToShowingSymptoms(person))
					return EpisimPerson.DiseaseStatus.showingSymptoms;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case showingSymptoms:
				if (rnd.nextDouble() < getProbaOfTransitioningToSeriouslySick(person)
						* (person.getVaccinationStatus() == EpisimPerson.VaccinationStatus.yes ?
						strainConfig.getParams(person.getVirusStrain()).getFactorSeriouslySickVaccinated() :
						strainConfig.getParams(person.getVirusStrain()).getFactorSeriouslySick())
						* (person.getVaccinationStatus() == EpisimPerson.VaccinationStatus.yes ? vaccinationConfig.getFactorSeriouslySick() : 1.0))
					return EpisimPerson.DiseaseStatus.seriouslySick;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case seriouslySick:
				if (!person.hadDiseaseStatus(EpisimPerson.DiseaseStatus.critical)
						&& rnd.nextDouble() < getProbaOfTransitioningToCritical(person))
					return EpisimPerson.DiseaseStatus.critical;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case critical:
				return EpisimPerson.DiseaseStatus.seriouslySickAfterCritical;

			case seriouslySickAfterCritical:
				return EpisimPerson.DiseaseStatus.recovered;

			case recovered:
				return EpisimPerson.DiseaseStatus.susceptible;

			default:
				throw new IllegalStateException("No state transition defined for " + person.getDiseaseStatus());
		}
	}

	/**
	 * Probability that a persons transitions from {@code showingSymptoms} to {@code seriouslySick}.
	 */
	protected double getProbaOfTransitioningToSeriouslySick(EpisimPerson person) {
		return 0.05625;
	}

	/**
	 * Probability that a persons transitions from {@code seriouslySick} to {@code critical}.
	 */
	protected double getProbaOfTransitioningToCritical(EpisimPerson person) {
		return 0.25;
	}

	protected double getProbaOfTransitioningToContagious(EpisimPerson person) {
		return 1.;
	}

	protected double getProbaOfTransitioningToShowingSymptoms(EpisimPerson person) {
		return 0.8;
	}

}

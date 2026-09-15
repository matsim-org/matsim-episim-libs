package org.matsim.episim.model.progression;

import com.google.inject.Inject;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.PathogenConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;

import org.matsim.episim.util.EpisimSplittableRandom;

/**
 * Default disease-status transition model. The base transition probabilities are read from
 * {@link PathogenConfigGroup} (age-independent variant).
 */
public class DefaultDiseaseStatusTransitionModel implements DiseaseStatusTransitionModel {

	private final EpisimSplittableRandom rnd;
	private final VaccinationConfigGroup vaccinationConfig;
	private final VirusStrainConfigGroup strainConfig;
	private final PathogenConfigGroup pathogenConfig;

	@Inject
	public DefaultDiseaseStatusTransitionModel(EpisimSplittableRandom rnd, VaccinationConfigGroup vaccinationConfig,
	                                           VirusStrainConfigGroup strainConfigGroup, PathogenConfigGroup pathogenConfig) {
		this.rnd = rnd;
		this.vaccinationConfig = vaccinationConfig;
		this.strainConfig = strainConfigGroup;
		this.pathogenConfig = pathogenConfig;

		// fail now instead of at the first transition of a strain without the age-independent progression
		pathogenConfig.checkProgressionConfigured(strainConfigGroup.getConfiguredStrains(), false, getClass().getSimpleName());
	}

	/**
	 * Age-independent base disease-progression probabilities of the pathogen the person is infected with.
	 */
	private PathogenConfigGroup.ProgressionParams progressionParams(EpisimPerson person) {
		return pathogenConfig.getProgressionParams(person.getVirusStrain().getPathogen(), false);
	}

	@Override
	public final EpisimPerson.DiseaseStatus decideNextState(EpisimPerson person, EpisimPerson.DiseaseStatus status, int day) {

		switch (status) {
			case infectedButNotContagious:
				return EpisimPerson.DiseaseStatus.contagious;

			case contagious:
				if (rnd.nextDouble() < getProbaOfTransitioningToShowingSymptoms(person) * getShowingSymptomsFactor(person, vaccinationConfig, day))
					return EpisimPerson.DiseaseStatus.showingSymptoms;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case showingSymptoms:
				if (rnd.nextDouble() < getProbaOfTransitioningToSeriouslySick(person)
						* (person.getVaccinationStatus() == EpisimPerson.VaccinationStatus.yes ?
						strainConfig.getParams(person.getVirusStrain()).getFactorSeriouslySickVaccinated() :
						strainConfig.getParams(person.getVirusStrain()).getFactorSeriouslySick())
						* getSeriouslySickFactor(person, vaccinationConfig, day))
//						* (person.getNumInfections() > 1 ? getFactorRecovered(person, day) : 1.0))
					return EpisimPerson.DiseaseStatus.seriouslySick;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case seriouslySick:
				if (!person.hadDiseaseStatus(EpisimPerson.DiseaseStatus.critical)
						&& (rnd.nextDouble() < getProbaOfTransitioningToCritical(person) * strainConfig.getParams(person.getVirusStrain()).getFactorCritical()
						* getCriticalFactor(person, vaccinationConfig, day)))
					return EpisimPerson.DiseaseStatus.critical;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case critical:
				double proba = getProbaOfTransitioningToDeceased(person);
				if (proba != 0 && rnd.nextDouble() < proba)
					return DiseaseStatus.deceased;
				else
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
	 * Probability that a person transitions from {@code showingSymptoms} to {@code seriouslySick} when person was already infected.
	 */
	protected double getFactorRecovered(EpisimPerson person, int day) {

		int daysSince = person.daysSince(DiseaseStatus.recovered, day);

		//we assume about 20% loss of protection against severe progression every year
		return Math.min(0.2 * (daysSince / 365), 1.0);
	}

	/**
	 * Probability that a persons transitions from {@code showingSymptoms} to {@code seriouslySick}.
	 */
	protected double getProbaOfTransitioningToSeriouslySick(EpisimPerson person) {
		return progressionParams(person).getSeriouslySickProbability(person.getAgeOrDefault(0));
	}

	/**
	 * Probability that a persons transitions from {@code seriouslySick} to {@code critical}.
	 */
	protected double getProbaOfTransitioningToCritical(EpisimPerson person) {
		return progressionParams(person).getCriticalProbability(person.getAgeOrDefault(0));
	}

	protected double getProbaOfTransitioningToShowingSymptoms(EpisimPerson person) {
		return progressionParams(person).getShowingSymptomsProbability(person.getAgeOrDefault(0));
	}

	protected double getProbaOfTransitioningToDeceased(EpisimPerson person) {
		return progressionParams(person).getDeathProbability(person.getAgeOrDefault(0));
	}

}

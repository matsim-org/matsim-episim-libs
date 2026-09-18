package org.matsim.episim.model.progression;

import com.google.inject.Inject;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.PathogenConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.model.ImmunityModel;

import org.matsim.episim.util.EpisimSplittableRandom;

/**
 * Default disease-status transition model. The base transition probabilities are read from
 * {@link PathogenConfigGroup} (age-independent variant).
 */
public class DefaultDiseaseStatusTransitionModel implements DiseaseStatusTransitionModel {

	private final EpisimSplittableRandom rnd;
	private final ImmunityModel immunityModel;
	private final VaccinationConfigGroup vaccinationConfig;
	private final VirusStrainConfigGroup strainConfig;
	private final PathogenConfigGroup pathogenConfig;

	@Inject
	public DefaultDiseaseStatusTransitionModel(EpisimSplittableRandom rnd, ImmunityModel immunityModel,
	                                           VaccinationConfigGroup vaccinationConfig,
	                                           VirusStrainConfigGroup strainConfigGroup, PathogenConfigGroup pathogenConfig) {
		this.rnd = rnd;
		this.immunityModel = immunityModel;
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
				if (rnd.nextDouble() < getProbaOfTransitioningToShowingSymptoms(person) * immunity(person, DiseaseStatus.showingSymptoms, day))
					return EpisimPerson.DiseaseStatus.showingSymptoms;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case showingSymptoms:
				if (rnd.nextDouble() < getProbaOfTransitioningToSeriouslySick(person)
						* (person.getVaccinationStatus() == EpisimPerson.VaccinationStatus.yes ?
						strainConfig.getParams(person.getVirusStrain()).getFactorSeriouslySickVaccinated() :
						strainConfig.getParams(person.getVirusStrain()).getFactorSeriouslySick())
						* immunity(person, DiseaseStatus.seriouslySick, day))
//						* (person.getNumInfections() > 1 ? getFactorRecovered(person, day) : 1.0))
					return EpisimPerson.DiseaseStatus.seriouslySick;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case seriouslySick:
				if (!person.hadDiseaseStatus(EpisimPerson.DiseaseStatus.critical)
						&& (rnd.nextDouble() < getProbaOfTransitioningToCritical(person) * strainConfig.getParams(person.getVirusStrain()).getFactorCritical()
						* immunity(person, DiseaseStatus.critical, day)))
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
	 * Remaining risk of the transition, given what the person's immune history protects them against.
	 */
	private double immunity(EpisimPerson person, DiseaseStatus target, int day) {
		return immunityModel.getFactor(person, person.getVirusStrain(), target, day);
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

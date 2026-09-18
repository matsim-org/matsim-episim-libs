package org.matsim.episim.model.progression;

import com.google.inject.Inject;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.Immunizable;
import org.matsim.episim.PathogenConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.model.ImmunityModel;
import org.matsim.episim.model.VirusStrain;

import java.util.NavigableMap;

import org.matsim.episim.util.EpisimSplittableRandom;

/**
 * Disease-status transition model that accounts for antibody levels.
 */
public class AntibodyDependentTransitionModel implements DiseaseStatusTransitionModel {

	private final EpisimSplittableRandom rnd;
	private final ImmunityModel immunityModel;
	private final VaccinationConfigGroup vaccinationConfig;
	private final VirusStrainConfigGroup strainConfig;
	private final PathogenConfigGroup pathogenConfig;

	/**
	 * Whether this model reads the age-dependent pathogen configuration.
	 */
	private final boolean ageDependent;

	@Inject
	public AntibodyDependentTransitionModel(EpisimSplittableRandom rnd, ImmunityModel immunityModel,
	                                        VaccinationConfigGroup vaccinationConfig,
	                                        VirusStrainConfigGroup strainConfigGroup, PathogenConfigGroup pathogenConfig) {
		this(rnd, immunityModel, vaccinationConfig, strainConfigGroup, pathogenConfig, false);
	}

	/**
	 * Constructor for subclasses with age-dependent transitions.
	 *
	 * @param ageDependent whether the age-dependent pathogen configuration is used
	 * @throws IllegalStateException if a configured strain lacks this progression variant, so that this does not fail at
	 *                               the first transition during the simulation
	 */
	protected AntibodyDependentTransitionModel(EpisimSplittableRandom rnd, ImmunityModel immunityModel,
	                                           VaccinationConfigGroup vaccinationConfig,
	                                           VirusStrainConfigGroup strainConfigGroup, PathogenConfigGroup pathogenConfig,
	                                           boolean ageDependent) {
		this.rnd = rnd;
		this.immunityModel = immunityModel;
		this.vaccinationConfig = vaccinationConfig;
		this.strainConfig = strainConfigGroup;
		this.pathogenConfig = pathogenConfig;
		this.ageDependent = ageDependent;

		pathogenConfig.checkProgressionConfigured(strainConfigGroup.getConfiguredStrains(), ageDependent, getClass().getSimpleName());
	}

	/**
	 * Whether this model reads the age-dependent pathogen configuration.
	 */
	protected final boolean isAgeDependentTransition() {
		return ageDependent;
	}

	/**
	 * Base disease-progression probabilities of the pathogen the person is currently infected with.
	 */
	protected final PathogenConfigGroup.ProgressionParams progressionParams(Immunizable person) {
		return pathogenConfig.getProgressionParams(person.getVirusStrain().getPathogen(), isAgeDependentTransition());
	}

	/**
	 * Age used for the probability lookup in the given profile. A profile with a single bucket does not depend on
	 * age, so the age is not read at all (persons without age are fine, as for the previously constant probabilities).
	 * Otherwise the age-dependent variant requires a real age, while the age-independent variant falls back to
	 * {@code 0} for a missing age.
	 */
	private int lookupAge(Immunizable person, NavigableMap<Integer, Double> profile) {
		if (profile.size() <= 1){
			return 0;}

		if (isAgeDependentTransition()){
			return person.getAge();}

		return person instanceof EpisimPerson ? ((EpisimPerson) person).getAgeOrDefault(0) : person.getAge();
	}

	@Override
	public final EpisimPerson.DiseaseStatus decideNextState(EpisimPerson person, EpisimPerson.DiseaseStatus status, int day) {

		switch (status) {
			case infectedButNotContagious:
				return EpisimPerson.DiseaseStatus.contagious;
			case contagious:

				if (rnd.nextDouble() < getProbaOfTransitioningToShowingSymptoms(person) * immunity(person, EpisimPerson.DiseaseStatus.showingSymptoms, day))
					return EpisimPerson.DiseaseStatus.showingSymptoms;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case showingSymptoms:
				if (rnd.nextDouble() < getProbaOfTransitioningToSeriouslySick(person)
						* (person.getVaccinationStatus() == EpisimPerson.VaccinationStatus.yes ?
						strainConfig.getParams(person.getVirusStrain()).getFactorSeriouslySickVaccinated() :
						strainConfig.getParams(person.getVirusStrain()).getFactorSeriouslySick())
						* immunity(person, EpisimPerson.DiseaseStatus.seriouslySick, day))
//						* (person.getNumInfections() > 1 ? getFactorRecovered(person, day) : 1.0))
					return EpisimPerson.DiseaseStatus.seriouslySick;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case seriouslySick:
				if (!person.hadDiseaseStatus(EpisimPerson.DiseaseStatus.critical)
						&& (rnd.nextDouble() < getProbaOfTransitioningToCritical(person) * strainConfig.getParams(person.getVirusStrain()).getFactorCritical()
						* immunity(person, EpisimPerson.DiseaseStatus.critical, day)))
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
	public double getProbaOfTransitioningToSeriouslySick(Immunizable person) {
		PathogenConfigGroup.ProgressionParams params = progressionParams(person);
		return params.getSeriouslySickProbability(lookupAge(person, params.getSeriouslySickProbabilityByAge()));
	}

	/**
	 * Probability that a persons transitions from {@code seriouslySick} to {@code critical}.
	 */
	public double getProbaOfTransitioningToCritical(Immunizable person) {
		PathogenConfigGroup.ProgressionParams params = progressionParams(person);
		return params.getCriticalProbability(lookupAge(person, params.getCriticalProbabilityByAge()));
	}

	protected double getProbaOfTransitioningToShowingSymptoms(EpisimPerson person) {
		PathogenConfigGroup.ProgressionParams params = progressionParams(person);
		return params.getShowingSymptomsProbability(lookupAge(person, params.getShowingSymptomsProbabilityByAge()));
	}

	/**
	 * Remaining risk of the transition, given what the person's immune history protects them against.
	 */
	private double immunity(EpisimPerson person, EpisimPerson.DiseaseStatus target, int day) {
		return immunityModel.getFactor(person, person.getVirusStrain(), target, day);
	}

	protected double getProbaOfTransitioningToDeceased(EpisimPerson person) {
		PathogenConfigGroup.ProgressionParams params = progressionParams(person);
		return params.getDeathProbability(lookupAge(person, params.getDeathProbabilityByAge()));
	}


}

package org.matsim.episim.model.progression;

import com.google.inject.Inject;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.Immunizable;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.model.VirusStrain;

import java.util.SplittableRandom;

public class AntibodyDependentTransitionModel implements DiseaseStatusTransitionModel {

	private final SplittableRandom rnd;
	private final VaccinationConfigGroup vaccinationConfig;
	private final VirusStrainConfigGroup strainConfig;

	@Inject
	public AntibodyDependentTransitionModel(SplittableRandom rnd, VaccinationConfigGroup vaccinationConfig,
	                                           VirusStrainConfigGroup strainConfigGroup) {
		this.rnd = rnd;
		this.vaccinationConfig = vaccinationConfig;
		this.strainConfig = strainConfigGroup;
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
	public double getProbaOfTransitioningToSeriouslySick(Immunizable person) {
		return 0.05625;
	}

	/**
	 * Probability that a persons transitions from {@code seriouslySick} to {@code critical}.
	 */
	public double getProbaOfTransitioningToCritical(Immunizable person) {
		return 0.25;
	}

	protected double getProbaOfTransitioningToShowingSymptoms(EpisimPerson person) {
		return 0.8;
	}

	@Override
	public double getShowingSymptomsFactor(EpisimPerson person, VaccinationConfigGroup vaccinationConfig, int day) {
		return 1.0;
	}

	@Override
	public double getSeriouslySickFactor(Immunizable person, VaccinationConfigGroup vaccinationConfig, int day) {


		int numVaccinations = person.getNumVaccinations();
		int numInfections = person.getNumInfections() - 1;

		if (numVaccinations == 0 && numInfections == 0)
			return 1.0;

		VirusStrain strain = person.getVirusStrain();

		double abNoWaning = person.getMaxAntibodies(strain);

		// Two modifications to antibody level below:
		// a) we multiply the antibody level by 4 if the agent is boostered
		if (numVaccinations > 1) {
			abNoWaning *= 4;
		}
		// b) if strain is omicron, an additional factor of 3.7 is applied
		if (strain.equals(VirusStrain.OMICRON_BA1) || strain.equals(VirusStrain.OMICRON_BA2) || strain.equals(VirusStrain.OMICRON_BA5) || strain.equals(VirusStrain.STRAIN_A) || strain.equals(VirusStrain.STRAIN_B)
				|| strain.equals(VirusStrain.STRAIN_C) || strain.equals(VirusStrain.STRAIN_D) || strain.equals(VirusStrain.STRAIN_E) || strain.equals(VirusStrain.STRAIN_F) || strain.equals(VirusStrain.STRAIN_G)
				|| strain.equals(VirusStrain.STRAIN_H) || strain.equals(VirusStrain.STRAIN_I) || strain.equals(VirusStrain.STRAIN_J) || strain.equals(VirusStrain.STRAIN_K)) {
			abNoWaning *= 3.7;
		}

		// returns remaining risk of infection (1 is full risk, 0 is no risk), opposite of vaccine effectiveness
		return 1. / (1. + Math.pow(abNoWaning,vaccinationConfig.getBeta()));

	}

	@Override
	public double getCriticalFactor(Immunizable person, VaccinationConfigGroup vaccinationConfig, int day) {

		int numVaccinations = person.getNumVaccinations();
		int numInfections = person.getNumInfections() - 1;

		if (numVaccinations == 0 && numInfections == 0)
			return 1.0;

		VirusStrain strain = person.getVirusStrain();

		double abNoWaning = person.getMaxAntibodies(strain);

		// Two modifications to antibody level below:
		// a) we multiply the antibody level by 4 if the agent is boostered
		if (numVaccinations > 1) {
			abNoWaning *= 4;
		}
		// b) if strain is omicron, an additional factor of 3.7 is applied
		if (strain.equals(VirusStrain.OMICRON_BA1) || strain.equals(VirusStrain.OMICRON_BA2)) {
			abNoWaning *= 3.7;
		}

		return 1. / (1. + Math.pow(abNoWaning, vaccinationConfig.getBeta()));

	}


	protected double getProbaOfTransitioningToDeceased(EpisimPerson person) {
		return 0.0;
	}


}

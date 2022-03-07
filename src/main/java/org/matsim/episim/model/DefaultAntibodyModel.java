package org.matsim.episim.model;


import com.google.inject.Inject;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.EpisimPerson.VaccinationStatus;

public class DefaultAntibodyModel implements AntibodyModel {

	private final AntibodyModel.Config antibodyConfig;


	@Inject
	DefaultAntibodyModel(AntibodyModel.Config antibodyConfig) {
		this.antibodyConfig = antibodyConfig;
	}

	@Override
	public void updateAntibodies(EpisimPerson person, int day) {

		if (day == 0) {
			for (VirusStrain strain : VirusStrain.values()) {
				person.setAntibodies(strain, 0.0);
			}
		}

		if (person.getNumVaccinations() == 0 && !person.hadDiseaseStatus(DiseaseStatus.recovered)) {
			return;
		}


		if (person.getVaccinationStatus().equals(VaccinationStatus.yes) && person.daysSince(VaccinationStatus.yes, day) == 1) {
			handleVaccination(person);
			return;
		}

		// at the moment infections are only handled if there is no vaccination on the same day. Maybe we should change this.
		if (person.hadDiseaseStatus(DiseaseStatus.recovered)) {
			if (person.daysSince(DiseaseStatus.recovered, day) == 1) {
				handleInfection(person);
				return;
			}
		}

		// if no immunity event: exponential decay, day by day:
		for (VirusStrain strain : VirusStrain.values()) {
			double halfLife_days = 80.;
			double oldAntibodyLevel = person.getAntibodies(strain);
			person.setAntibodies(strain, oldAntibodyLevel * Math.pow( 0.5, 1 / halfLife_days ));
		}

	}

	private void handleInfection(EpisimPerson person) {
		VirusStrain strain = person.getVirusStrain(person.getNumInfections() - 1);

		boolean firstImmunization = checkFirstImmunization(person);

		// 1st immunization:
		if (firstImmunization) {

			for (VirusStrain strain2 : VirusStrain.values()) {
				double antibodies = antibodyConfig.initialAntibodies.get(strain).get(strain2);
				person.setAntibodies(strain2, antibodies);
			}


		}
		else {
			for (VirusStrain strain2 : VirusStrain.values()) {
				double refreshFactor = antibodyConfig.antibodyRefreshFactors.get(strain).get(strain2);
				double antibodies = Math.min( 150., person.getAntibodies(strain2) * refreshFactor);
				double initialAntibodies = antibodyConfig.initialAntibodies.get(strain).get(strain2);
				antibodies = Math.max(antibodies, initialAntibodies);
				person.setAntibodies(strain2, antibodies);
			}

		}

	}

	private void handleVaccination(EpisimPerson person) {
		VaccinationType vaccinationType = person.getVaccinationType(person.getNumVaccinations() - 1);

		boolean firstImmunization = checkFirstImmunization(person);

		// 1st immunization:
		if (firstImmunization) {
			for (VirusStrain strain2 : VirusStrain.values()) {
				double antibodies = antibodyConfig.initialAntibodies.get(vaccinationType).get(strain2);
				person.setAntibodies(strain2, antibodies);
			}


		}
		else {
			for (VirusStrain strain2 : VirusStrain.values()) {
				double refreshFactor = antibodyConfig.antibodyRefreshFactors.get(vaccinationType).get(strain2);
				double antibodies = Math.min( 150., person.getAntibodies(strain2) * refreshFactor);
				double initialAntibodies = antibodyConfig.initialAntibodies.get(vaccinationType).get(strain2);
				antibodies = Math.max(antibodies, initialAntibodies);
				person.setAntibodies(strain2, antibodies);
			}

		}

	}

	private boolean checkFirstImmunization(EpisimPerson person) {
		boolean firstImmunization = true;
		for (double abLevel : person.getAntibodies().values()) {
			if (abLevel > 0) {
				firstImmunization = false;
				break;
			}
		}
		return firstImmunization;
	}
}

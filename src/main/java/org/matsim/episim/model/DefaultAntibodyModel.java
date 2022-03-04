package org.matsim.episim.model;


import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.EpisimPerson.VaccinationStatus;

import com.google.inject.Inject;

public class DefaultAntibodyModel implements AntibodyModel {

	private final VaccinationConfigGroup vaccinationConfig;

	@Inject
	DefaultAntibodyModel(Config config) {
		this.vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
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
			switch( strain ) {
				case SARS_CoV_2:
				case ALPHA:
				case DELTA:
					initializeFor1stGenVaccines( person, 1.0, vaccinationConfig );
					person.setAntibodies(VirusStrain.DELTA, 1.0/ vaccinationConfig.getAk50PerStrain().get(VirusStrain.SARS_CoV_2)); //???
					break;
				case OMICRON_BA1:
					person.setAntibodies(VirusStrain.SARS_CoV_2, 0.01); //???
					person.setAntibodies(VirusStrain.ALPHA, 0.01); //???
					person.setAntibodies(VirusStrain.DELTA, 0.2 / 6.4); //???
					
					person.setAntibodies(VirusStrain.OMICRON_BA1, 0.2); //???
					person.setAntibodies(VirusStrain.OMICRON_BA2, 0.2 / 1.4); //???
					break;
				case OMICRON_BA2:
					person.setAntibodies(VirusStrain.SARS_CoV_2, 0.01); //???
					person.setAntibodies(VirusStrain.ALPHA, 0.01); //???
					person.setAntibodies(VirusStrain.DELTA, 0.2 / 6.4); //???

					person.setAntibodies(VirusStrain.OMICRON_BA1, 0.2 / 1.4); //???
					person.setAntibodies(VirusStrain.OMICRON_BA2, 0.2); //???
					break;
				default:
					throw new IllegalStateException( "Unexpected value: " + strain );
			}
		}
		//boost:
		else {
			switch( strain ) {
				case SARS_CoV_2:
				case ALPHA:
				case DELTA:
					refresh(person, 10, 1.0, vaccinationConfig);
					break;
				case OMICRON_BA1:
					refresh(person, 10, 0.01, vaccinationConfig); //???
					break;
				case OMICRON_BA2:
					refresh(person, 10, 0.01, vaccinationConfig); //???
					break;
				default:
					throw new IllegalStateException( "Unexpected value: " + strain );
				}
			}
		}

	private void handleVaccination(EpisimPerson person) {
		VaccinationType vaccinationType = person.getVaccinationType(person.getNumVaccinations() - 1);

		boolean firstImmunization = checkFirstImmunization(person);

		// 1st immunization:
		if (firstImmunization) {
			switch( vaccinationType ) {
				case generic:
					initializeFor1stGenVaccines( person, 1.0, vaccinationConfig );
					break;
				case mRNA:
					initializeFor1stGenVaccines( person, 2.0, vaccinationConfig );
					break;
				case vector:
					initializeFor1stGenVaccines( person, 0.5, vaccinationConfig );
					break;
				case omicronUpdate:
					initializeFor1stGenVaccines( person, 2.0, vaccinationConfig );
					person.setAntibodies(VirusStrain.OMICRON_BA1, 2.0/ vaccinationConfig.getAk50PerStrain().get(VirusStrain.SARS_CoV_2));
					person.setAntibodies(VirusStrain.OMICRON_BA2, 2.0/ vaccinationConfig.getAk50PerStrain().get(VirusStrain.SARS_CoV_2));
					break;
				default:
					throw new IllegalStateException( "Unexpected value: " + vaccinationType );
			}
		}
		//boost:
		else {
			switch( vaccinationType ) {
				case generic:
					refresh(person, 10, 1.0, vaccinationConfig);
					break;
				case mRNA:
					refresh(person, 15, 2.0, vaccinationConfig); //Previously: 20
					break;
				case vector:
					refresh(person, 5, 0.5, vaccinationConfig);
					break;
				case omicronUpdate:
					refresh(person, 15, 2.0, vaccinationConfig);
					break;
				default:
					throw new IllegalStateException( "Unexpected value: " + vaccinationType );
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

	private static void refresh( EpisimPerson person, int vaccineTypeFactor, double initialAntibodies, VaccinationConfigGroup vaccinationConfig ){
		var ak50PerStrain = vaccinationConfig.getAk50PerStrain();
		double refreshed;
		double initial;
		for( VirusStrain strain : VirusStrain.values() ){
			switch( strain ) {
				case SARS_CoV_2:
					refreshed = Math.min( 150., person.getAntibodies(strain) * vaccineTypeFactor);
					initial = initialAntibodies / ak50PerStrain.get(strain);
					//ab level always reaches at least the initial protection
					person.setAntibodies(strain, Math.max(initial, refreshed));
					break;
				case ALPHA:
					refreshed = Math.min( 150., person.getAntibodies(strain) * vaccineTypeFactor);
					initial = initialAntibodies / ak50PerStrain.get(strain);
					//ab level always reaches at least the initial protection
					person.setAntibodies(strain, Math.max(initial, refreshed));
					break;
				case DELTA:
					refreshed = Math.min( 150., person.getAntibodies(strain) * vaccineTypeFactor);
					initial = initialAntibodies / ak50PerStrain.get(strain);
					//ab level always reaches at least the initial protection
					person.setAntibodies(strain, Math.max(initial, refreshed));
					break;
				case OMICRON_BA1:
					refreshed = Math.min( 150., person.getAntibodies(strain) * vaccineTypeFactor);
					initial = initialAntibodies / ak50PerStrain.get(strain);
					//ab level always reaches at least the initial protection
					person.setAntibodies(strain, Math.max(initial, refreshed));
					break;
				case OMICRON_BA2:
					refreshed = Math.min( 150., person.getAntibodies(strain) * vaccineTypeFactor);
					initial = initialAntibodies / ak50PerStrain.get(strain);
					//ab level always reaches at least the initial protection
					person.setAntibodies(strain, Math.max(initial, refreshed));
					break;
				default:
					person.setAntibodies(strain, Double.NaN);
			}
		}
	}
	private static void initializeFor1stGenVaccines( EpisimPerson person, double initialAntibodies, VaccinationConfigGroup vaccinationConfig ){
		var ak50PerStrain = vaccinationConfig.getAk50PerStrain();
		for( VirusStrain strain1 : VirusStrain.values() ){
			switch ( strain1 ){
				case SARS_CoV_2:
				case ALPHA:
					person.setAntibodies(strain1, initialAntibodies / ak50PerStrain.get(strain1 )); // those two lead to same result according to what Sebastian had before
					break;
				case DELTA:
					person.setAntibodies(strain1, initialAntibodies / ak50PerStrain.get(strain1 ));
					break;
				case OMICRON_BA1:
					person.setAntibodies(strain1, initialAntibodies / ak50PerStrain.get(strain1 ));
					break;
				case OMICRON_BA2:
					person.setAntibodies(strain1, initialAntibodies / ak50PerStrain.get(strain1 ));
					break;
				default:
					person.setAntibodies(strain1, Double.NaN );
			}
		}
	}

}

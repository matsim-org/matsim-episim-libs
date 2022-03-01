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
		
		
		if (person.daysSince(VaccinationStatus.yes, day) == 1) {
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
					break;
				case OMICRON_BA1:
					initializeFor1stGenVaccines( person, 0.0, vaccinationConfig );
					person.setAntibodies(VirusStrain.OMICRON_BA1, 1.0/0.2); //???
					person.setAntibodies(VirusStrain.OMICRON_BA2, 1.0/0.2); //???
					break;
				case OMICRON_BA2:
					initializeFor1stGenVaccines( person, 0.0, vaccinationConfig );
					person.setAntibodies(VirusStrain.OMICRON_BA1, 1.0/0.2); //???
					person.setAntibodies(VirusStrain.OMICRON_BA2, 1.0/0.2); //???
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
					refresh(person, 10, vaccinationConfig);
					break;
				case OMICRON_BA1:
					refresh(person, 10, vaccinationConfig); //???
					break;
				case OMICRON_BA2:
					refresh(person, 10, vaccinationConfig); //???
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
					person.setAntibodies(VirusStrain.OMICRON_BA1, 2.0/0.2);
					person.setAntibodies(VirusStrain.OMICRON_BA2, 2.0/0.2);
					break;
				default:
					throw new IllegalStateException( "Unexpected value: " + vaccinationType );
			}
		}
		//boost:
		else {
			switch( vaccinationType ) {
				case generic:
					refresh(person, 10, vaccinationConfig);
					break;
				case mRNA:
					refresh(person, 20, vaccinationConfig);
					break;
				case vector:
					refresh(person, 5, vaccinationConfig);
					break;
				case omicronUpdate:
					refresh(person, 20, vaccinationConfig);
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
	
	private static void refresh( EpisimPerson person, int vaccineTypeFactor, VaccinationConfigGroup vaccinationConfig ){
		var ak50PerStrain = vaccinationConfig.getAk50PerStrain();
		for( VirusStrain strain : VirusStrain.values() ){
			switch( strain ) {
				case SARS_CoV_2:
					person.setAntibodies(strain, Math.min( 20., person.getAntibodies(strain) * ak50PerStrain.get(strain))  * vaccineTypeFactor / ak50PerStrain.get(strain) ) ;
					break;
				case ALPHA:
					person.setAntibodies(strain, Math.min( 20., person.getAntibodies(strain) * ak50PerStrain.get(strain))  * vaccineTypeFactor / ak50PerStrain.get(strain) ) ;
					break;
				case DELTA:
					person.setAntibodies(strain, Math.min( 20., person.getAntibodies(strain) * ak50PerStrain.get(strain))  * vaccineTypeFactor / ak50PerStrain.get(strain) ) ;
					break;
				case OMICRON_BA1:
					person.setAntibodies(strain, Math.min( 20., person.getAntibodies(strain) * ak50PerStrain.get(strain))  * vaccineTypeFactor / ak50PerStrain.get(strain) ) ;
					break;
				case OMICRON_BA2:
					person.setAntibodies(strain, Math.min( 20., person.getAntibodies(strain) * ak50PerStrain.get(strain))  * vaccineTypeFactor / ak50PerStrain.get(strain) ) ;
					break;
				default:
					person.setAntibodies(strain, Double.NaN);
			}
		}
	}
	private static void initializeFor1stGenVaccines( EpisimPerson person, double vaccineTypeFactor, VaccinationConfigGroup vaccinationConfig ){
		var ak50PerStrain = vaccinationConfig.getAk50PerStrain();
		for( VirusStrain strain1 : VirusStrain.values() ){
			switch ( strain1 ){
				case SARS_CoV_2:
				case ALPHA:
					person.setAntibodies(strain1, vaccineTypeFactor / ak50PerStrain.get(strain1 )); // those two lead to same result according to what Sebastian had before
					break;
				case DELTA:
					person.setAntibodies(strain1, vaccineTypeFactor / ak50PerStrain.get(strain1 ));
					break;
				case OMICRON_BA1:
					person.setAntibodies(strain1, vaccineTypeFactor / ak50PerStrain.get(strain1 ));
					break;
				case OMICRON_BA2:
					person.setAntibodies(strain1, vaccineTypeFactor / ak50PerStrain.get(strain1 ));
					break;
				default:
					person.setAntibodies(strain1, Double.NaN );
			}
		}
	}

}

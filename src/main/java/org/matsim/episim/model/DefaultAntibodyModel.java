package org.matsim.episim.model;


import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.jfree.util.Log;
import org.matsim.core.config.Config;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.EpisimPerson.VaccinationStatus;

import com.google.inject.Inject;

public class DefaultAntibodyModel implements AntibodyModel {

	private final AntibodyConfig antibodyConfig;


	@Inject
	DefaultAntibodyModel(Config config, AntibodyConfig antibodyConfig) {
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
				double antibodies = antibodyConfig.initialAntobodies.get(strain).get(strain2);
				person.setAntibodies(strain2, antibodies);
			}

			
		}
		else {
			for (VirusStrain strain2 : VirusStrain.values()) {
				double refreshFactor = antibodyConfig.antibodyRefreshFactors.get(strain).get(strain2);
				double antibodies = Math.min( 150., person.getAntibodies(strain2) * refreshFactor);
				double initialAntibodies = antibodyConfig.initialAntobodies.get(strain).get(strain2);
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
				double antibodies = antibodyConfig.initialAntobodies.get(vaccinationType).get(strain2);
				person.setAntibodies(strain2, antibodies);
			}

			
		}
		else {
			for (VirusStrain strain2 : VirusStrain.values()) {
				double refreshFactor = antibodyConfig.antibodyRefreshFactors.get(vaccinationType).get(strain2);
				double antibodies = Math.min( 150., person.getAntibodies(strain2) * refreshFactor);
				double initialAntibodies = antibodyConfig.initialAntobodies.get(vaccinationType).get(strain2);
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
	
	public static class AntibodyConfig {
				
		Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntobodies;
		Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors;

		public AntibodyConfig() {
			
			//initial antibodies
			Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntobodies = new HashMap<>();

			for (VaccinationType immunityType : VaccinationType.values()) {
				initialAntobodies.put(immunityType, new EnumMap<>( VirusStrain.class ) );
				for (VirusStrain virusStrain : VirusStrain.values()) {
					
					if (immunityType == VaccinationType.mRNA) {
						initialAntobodies.get(immunityType).put(virusStrain, 10.0);
					}
					else if (immunityType == VaccinationType.vector) {
						initialAntobodies.get(immunityType).put(virusStrain, 2.5);
					}
					else {
						initialAntobodies.get(immunityType).put(virusStrain, 5.0);
					}
				}
			}
			
			for (VirusStrain immunityType : VirusStrain.values()) {
				initialAntobodies.put(immunityType, new EnumMap<>( VirusStrain.class ) );
				for (VirusStrain virusStrain : VirusStrain.values()) {
					initialAntobodies.get(immunityType).put(virusStrain, 5.0);
				}
			}
			
			//DELTA
			initialAntobodies.get(VaccinationType.mRNA).put(VirusStrain.DELTA, 4.0);
			initialAntobodies.get(VaccinationType.vector).put(VirusStrain.DELTA, 1.0);
			initialAntobodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.DELTA, 2.0);
			initialAntobodies.get(VirusStrain.ALPHA).put(VirusStrain.DELTA, 2.0);
			initialAntobodies.get(VirusStrain.DELTA).put(VirusStrain.DELTA, 5.0);
			initialAntobodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.DELTA, 2.0);
			initialAntobodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.DELTA, 2.0);

			//BA.1
			initialAntobodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA1, 0.8);
			initialAntobodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA1, 0.2);
			initialAntobodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA1, 0.01);
			initialAntobodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA1, 0.01);
			initialAntobodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA1, 0.2 / 6.4);
			initialAntobodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA1, 0.2);
			initialAntobodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA1, 0.2 / 1.4);
			
			//BA.2
			initialAntobodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA2, 0.8 / 1.4);
			initialAntobodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA2, 0.2 / 1.4);
			initialAntobodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA2, 0.01);
			initialAntobodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA2, 0.01);
			initialAntobodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA2, 0.2 / 6.4);
			initialAntobodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA2, 0.2 / 1.4);
			initialAntobodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA2, 0.2);
			
						
			//refresh factors
			Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();
			
			for (VaccinationType immunityType : VaccinationType.values()) {
				antibodyRefreshFactors.put(immunityType, new EnumMap<>( VirusStrain.class ) );
				for (VirusStrain virusStrain : VirusStrain.values()) {
					
					if (immunityType == VaccinationType.mRNA) {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
					}
					else if (immunityType == VaccinationType.vector) {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 5.0);
					}
					else {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 10.0);
					}
					
				}
			}
			
			for (VirusStrain immunityType : VirusStrain.values()) {
				antibodyRefreshFactors.put(immunityType, new EnumMap<>( VirusStrain.class ) );
				for (VirusStrain virusStrain : VirusStrain.values()) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 10.0);
				}
			}

			this.initialAntobodies = initialAntobodies;
			this.antibodyRefreshFactors = antibodyRefreshFactors;
		}
		
		public AntibodyConfig(Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntobodies, Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors) {
			this.initialAntobodies = initialAntobodies;
			this.antibodyRefreshFactors = antibodyRefreshFactors;
		}

		
	}

}

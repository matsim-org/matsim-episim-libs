package org.matsim.episim.model;


import com.google.inject.Inject;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;

import java.util.Collection;
import java.util.SplittableRandom;

public class DefaultAntibodyModel implements AntibodyModel {

	private final AntibodyModel.Config antibodyConfig;
	private final SplittableRandom localRnd;


	@Inject
	DefaultAntibodyModel(AntibodyModel.Config antibodyConfig) {
		this.antibodyConfig = antibodyConfig;
		localRnd = new SplittableRandom(2938); // todo: should it be a fixed seed, i.e not change btwn snapshots
	}

	@Override
	public void init(Collection<EpisimPerson> persons, int iteration) {
		// Divide population into groups based on antibody response to immunity events
		// e.g. low responders could gain fewer antibodies following infection than high responders
		if (2 * antibodyConfig.getImmuneShare() > 1.) {
			throw new RuntimeException("Sum of immune population shares cannot be > 1.0");
		}

		for (EpisimPerson person : persons) {
			double rand = localRnd.nextDouble();
			if (rand < antibodyConfig.getImmuneShare()) {
				person.setImmuneResponse(EpisimPerson.ImmuneResponse.low);
			} else if (rand < 2 * antibodyConfig.getImmuneShare()) {
				person.setImmuneResponse(EpisimPerson.ImmuneResponse.high);
			} else {
				person.setImmuneResponse(EpisimPerson.ImmuneResponse.normal);
			}

			for (VirusStrain strain : VirusStrain.values()) {
				person.setAntibodies(strain, 0.0);
			}

			if (iteration > 1) {
				for (int it = 1; it < iteration; it++) {
					updateAntibodies(person, it);
				}

			}
		}


	}

	/**
	 * Updates the antibody levels for person. If an immunity event occurs (vaccination or infection) on the previous
	 * day, antibodies will increase. If not, they will decrease. This method was designed to also recalculate antibodies
	 * when the simulation is started from snapshot.
	 *
	 * @param person person whose antibodies to update
	 * @param day    current day / iteration
	 */
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

		//handle vaccination
		if (person.getVaccinationDates().contains(day - 1)) {
			int vaccinationIndex = person.getVaccinationDates().indexOf(day - 1);
			VaccinationType vaccinationType = person.getVaccinationType(vaccinationIndex);
			handleVaccination(person, vaccinationType);
			return;
		}

		// handle infection
		double dayDouble = (day - 1.) * 24. * 60. * 60.; // second -> day format
		if (person.getInfectionDates().contains(dayDouble)) {
			int infectionIndex = person.getInfectionDates().indexOf(dayDouble);
			VirusStrain virusStrain = person.getVirusStrain(infectionIndex);
			handleInfection(person, virusStrain);
			return;
		}

		// if no immunity event: exponential decay, day by day:
		for (VirusStrain strain : VirusStrain.values()) {
			double halfLife_days = 60.;
			double oldAntibodyLevel = person.getAntibodies(strain);
			person.setAntibodies(strain, oldAntibodyLevel * Math.pow(0.5, 1 / halfLife_days));
		}

	}

	private void handleInfection(EpisimPerson person, VirusStrain strain) {

		boolean firstImmunization = checkFirstImmunization(person);
		// 1st immunization:
		if (firstImmunization) {

			for (VirusStrain strain2 : VirusStrain.values()) {
				double antibodies = antibodyConfig.initialAntibodies.get(strain).get(strain2);

				antibodies = adjustAntibodiesBasedOnImmuneResponse(person, antibodies);

				person.setAntibodies(strain2, antibodies);
			}


		} else {
			for (VirusStrain strain2 : VirusStrain.values()) {
				double refreshFactor = antibodyConfig.antibodyRefreshFactors.get(strain).get(strain2);
				double antibodies = Math.min(150., person.getAntibodies(strain2) * refreshFactor);
				double initialAntibodies = antibodyConfig.initialAntibodies.get(strain).get(strain2);


				antibodies = Math.max(antibodies, initialAntibodies);

				antibodies = adjustAntibodiesBasedOnImmuneResponse(person, antibodies);

				person.setAntibodies(strain2, antibodies);
			}

		}
	}

	private void handleVaccination(EpisimPerson person, VaccinationType vaccinationType) {

		boolean firstImmunization = checkFirstImmunization(person);

		// 1st immunization:
		if (firstImmunization) {
			for (VirusStrain strain2 : VirusStrain.values()) {
				double antibodies = antibodyConfig.initialAntibodies.get(vaccinationType).get(strain2);

				antibodies = adjustAntibodiesBasedOnImmuneResponse(person, antibodies);

				person.setAntibodies(strain2, antibodies);
			}


		} else {
			for (VirusStrain strain2 : VirusStrain.values()) {
				double refreshFactor = antibodyConfig.antibodyRefreshFactors.get(vaccinationType).get(strain2);
				double antibodies = Math.min(150., person.getAntibodies(strain2) * refreshFactor);
				double initialAntibodies = antibodyConfig.initialAntibodies.get(vaccinationType).get(strain2);
				antibodies = Math.max(antibodies, initialAntibodies);

				antibodies = adjustAntibodiesBasedOnImmuneResponse(person, antibodies);

				person.setAntibodies(strain2, antibodies);
			}

		}
	}

	private double adjustAntibodiesBasedOnImmuneResponse(EpisimPerson person, double antibodies) {
		if (person.getImmuneResponse().equals(EpisimPerson.ImmuneResponse.high)) {
			antibodies *= antibodyConfig.getImmuneResponseMultiplier().get(EpisimPerson.ImmuneResponse.high);
		} else if (person.getImmuneResponse().equals(EpisimPerson.ImmuneResponse.low)) {
			antibodies *= antibodyConfig.getImmuneResponseMultiplier().get(EpisimPerson.ImmuneResponse.low);
		}
		return antibodies;
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

	private static double getIgA(EpisimPerson person, int day, VirusStrain strain) {

		if (!person.hadStrain(strain)) {
			return 1.0;
		} else {
			int lastInfectionWithStrain = 0;
			for (int ii = 0; ii < person.getNumInfections(); ii++) {
				if (person.getVirusStrain(ii) == strain) {
					lastInfectionWithStrain = ii;
				}
			}
			return 1.0 - Math.pow(0.5, person.daysSinceInfection(lastInfectionWithStrain, day) / 30.0);
		}

	}
}

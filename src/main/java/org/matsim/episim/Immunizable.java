package org.matsim.episim;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import org.matsim.api.core.v01.events.HasPersonId;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;

/**
 * An synthetic person who can be subject to immunization events (i.e. vaccinations and infections)
 */
public interface Immunizable extends HasPersonId {


	/**
	 * Getter for total rounds of vaccination agent has received.
	 * Note: the first and second mRNA vaccination is often seen as a single vaccination round.
	 */
	int getNumVaccinations();

	/**
	 * Getter for total number of infections agent has been subject to.
	 */
	int getNumInfections();

	/**
	 * Getter for the virus strain of the most recent infection.
	 */
	VirusStrain getVirusStrain();

	/**
	 * Getter for the virus strain of one infection.
	 *
	 * @param idx index of the infection, starting at 0 with the oldest one
	 * @throws RuntimeException if the agent did not have an infection with this index
	 */
	VirusStrain getVirusStrain(int idx);

	/**
	 * Days elapsed since one infection.
	 *
	 * @param idx index of the infection, starting at 0 with the oldest one
	 * @param day day to count backwards from, not necessarily the current iteration
	 * @throws RuntimeException if the agent did not have an infection with this index
	 */
	int daysSinceInfection(int idx, int day);

	/**
	 * Getter for the product of one vaccination.
	 *
	 * @param idx index of the vaccination, starting at 0 with the oldest one
	 * @throws RuntimeException if the agent did not receive a vaccination with this index
	 */
	VaccinationType getVaccinationType(int idx);

	/**
	 * Days elapsed since one vaccination.
	 *
	 * @param idx index of the vaccination, starting at 0 with the oldest one
	 * @param day day to count backwards from, not necessarily the current iteration
	 * @throws RuntimeException if the agent did not receive a vaccination with this index
	 */
	int daysSinceVaccination(int idx, int day);

	/**
	 * Getter for list of iterations on which agent was vaccinated (oldest first).
	 */
	IntList getVaccinationDates();

	/**
	 * Getter for list of time-stamps at which agent was infected (oldest first).
	 * Time-stamps are measured in seconds from simulation begin.
	 * Iteration of infection = ((int) infectionSecond) / (24 * 60 * 60)
	 */
	DoubleList getInfectionDates();


	/**
	 * Legacy, for the antibody based immunity model only. Newer immunity models do not read antibodies.
	 *
	 * Getter for neutralizing antibody level at the time of the most recent infection and with respect to the virus
	 * strain of the most recent infection. This antibody level does NOT include the increase in antibodies caused by
	 * the infection itself; it refers to the antibodies that (unsuccessfully) protected the agent against the previous infection
	 */
	double getAntibodyLevelAtInfection();

	/**
	 * Legacy, for the antibody based immunity model only.
	 * Returns highest antibody level that agent has had in their past for all strains.
	 */
	Object2DoubleMap<VirusStrain> getMaxAntibodies();

	/**
	 * Legacy, for the antibody based immunity model only.
	 * Returns highest antibody level that agent has had in their past for a specific strain.
	 */
	double getMaxAntibodies(VirusStrain strain);

	/**
	 * Legacy, for the antibody based immunity model only.
	 * sets max antibody level that agent has had in their past for a specific strain.
	 */
	void updateMaxAntibodies(VirusStrain strain, double maxAb);
	/**
	 * Legacy. Reads the status history of the current episode, which is reset on re-infection; immunity models
	 * have to use the infection and vaccination history instead.
	 * Returns whether agent has experienced given disease status at any time in the course of the simulation.
	 */
	boolean hadDiseaseStatus(EpisimPerson.DiseaseStatus status);

	/**
	 * Legacy. Reads the status history of the current episode, which is reset on re-infection; immunity models
	 * have to use {@link #daysSinceInfection(int, int)} or {@link #daysSinceVaccination(int, int)} instead.
	 * Returns the number of days since the given disease status was reached.
	 *
	 * @param status DiseaseStatus (i.e. recovered)
	 * @param day iteration number from which to count backwards from to specified DiseaseStatus
	 * @return number of days since the status was reached
	 */
	int daysSince(EpisimPerson.DiseaseStatus status, int day);

	/**
	 * Getter for agent's age.
	 */
	int getAge();
}

package org.matsim.episim;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import org.matsim.core.api.internal.HasPersonId;
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
	 * Getter for the virus strain of the most recent infection
	 */
	VirusStrain getVirusStrain();

	/**
	 * Getter for list of iterations on which agent was vaccinated (in descending order)
	 */
	IntList getVaccinationDates();

	/**
	 * Getter for list of time-stamps at which agent was infected (in descending order).
	 * Time-stamps are measured in seconds from simulation begin.
	 * Iteration of infection = ((int) infectionSecond) / (24 * 60 * 60)
	 */
	DoubleList getInfectionDates();


	/**
	 * Getter for neutralizing antibody level at the time of the most recent infection and with respect to the virus
	 * strain of the most recent infection. This antibody level does NOT include the increase in antibodies caused by
	 * the infection itself; it refers to the antibodies that (unsuccessfully) protected the agent against the previous infection
	 */
	double getAntibodyLevelAtInfection();

	/**
	 * Returns highest antibody level that agent has had in their past.
	 */
	Object2DoubleMap<VirusStrain> getMaximalAntibodyLevel();

	/**
	 * Returns whether agent has experienced given disease status at any time in the course of the simulation
	 */
	boolean hadDiseaseStatus(EpisimPerson.DiseaseStatus status);

	/**
	 *
	 * @param status DiseaseStatus (i.e. recovered)
	 * @param day iteration number from which to count backwards from to specified DiseaseStatus
	 * @return
	 */
	int daysSince(EpisimPerson.DiseaseStatus status, int day);

	/**
	 * Getter for agent's age.
	 */
	int getAge();
}

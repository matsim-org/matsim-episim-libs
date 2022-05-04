package org.matsim.episim;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.episim.model.VirusStrain;

public interface Immunizable extends HasPersonId {


	int getNumVaccinations();

	int getNumInfections();

	VirusStrain getVirusStrain();


	IntList getVaccinationDates();

	DoubleList getInfectionDates();


	double getAntibodyLevelAtInfection();


	boolean hadDiseaseStatus(EpisimPerson.DiseaseStatus status);

	int daysSince(EpisimPerson.DiseaseStatus status, int day);

	int getAge();
}

package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.model.AntibodyModel;
import org.matsim.episim.model.ProgressionModel;

import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.matsim.episim.EpisimUtils.DAY;

public final class InitialImmunizationHandler implements EpisimVaccinationEventHandler, EpisimInfectionEventHandler, EpisimInitialInfectionEventHandler, EpisimStartEventHandler {

	private final Map<Id<Person>, EpisimPerson> personMap;
	private final EpisimConfigGroup episimConfig;
	private final AntibodyModel antibodyModel;
	private final ProgressionModel progressionModel;

	private Double startTimeOffset = null;

	private int iterationOffset;

	int maxIterationReachedSoFar = 0;


	// 0		1		2		3		4
	// 0		DAY		2Day	3Day	4Day
	//			startDate
	// 							startDate2
	//			-1		0		1		2
	//			-86..	0		86..
	public InitialImmunizationHandler(Map<Id<Person>, EpisimPerson> personMap, EpisimConfigGroup episimConfig, AntibodyModel antibodyModel, ProgressionModel progressionModel) {
		this.personMap = personMap;
		this.episimConfig = episimConfig;
		this.antibodyModel = antibodyModel;
		this.progressionModel = progressionModel;
	}

	@Override
	public void handleEvent(EpisimStartEvent event) {
		this.iterationOffset = (int) ChronoUnit.DAYS.between(event.getStartDate(), episimConfig.getStartDate());
		this.startTimeOffset = this.iterationOffset * DAY;
	}



	@Override
	public void handleEvent(EpisimInfectionEvent event) {
		int currentIteration = (int) (event.getTime() / EpisimUtils.DAY);
		if (currentIteration >= iterationOffset + 1) {
			return;
		} else if (maxIterationReachedSoFar < currentIteration) {
			newDay(currentIteration);
		}

		personMap.get(event.getPersonId()).setInitialInfection(event.getTime() - startTimeOffset, event.getVirusStrain());
	}

	@Override
	public void handleEvent(EpisimInitialInfectionEvent event) {
		handleEvent(event.asInfectionEvent());
	}

	@Override
	public void handleEvent(EpisimVaccinationEvent event) {
		int currentIteration = (int) (event.getTime() / EpisimUtils.DAY);
		if (currentIteration >= iterationOffset + 1) {

			return;
		} else if (maxIterationReachedSoFar < currentIteration) {
			newDay(currentIteration);
		}

		personMap.get(event.getPersonId()).setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, event.getVaccinationType(), currentIteration - iterationOffset);
	}

	public void newDay(int currentIteration) {
		while (this.maxIterationReachedSoFar <= currentIteration) {
			this.maxIterationReachedSoFar++;
			for (EpisimPerson person : personMap.values()) {
				antibodyModel.updateAntibodies(person, this.maxIterationReachedSoFar);
				progressionModel.updateState(person, this.maxIterationReachedSoFar);
			}
		}
	}
}

package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.model.AntibodyModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.model.VirusStrain;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.Function;

import static org.matsim.episim.EpisimUtils.DAY;

public final class InitialImmunizationHandler implements Function<String, Boolean>,
		EpisimVaccinationEventHandler, EpisimInfectionEventHandler,
		EpisimInitialInfectionEventHandler, EpisimStartEventHandler {

	private final Map<Id<Person>, EpisimPerson> personMap;
	private final EpisimConfigGroup episimConfig;
	private final AntibodyModel antibodyModel;
	private final ProgressionModel progressionModel;

	private Double startTimeOffset = null;

	private int iterationOffset;
	private boolean continueProcessingEvents = true;

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
		if (currentIteration >= iterationOffset + 1) { // starts returning when current iteration = 5
			continueProcessingEvents = false;
			return;
		} else if (maxIterationReachedSoFar < currentIteration) {
			newDay(currentIteration);
		}

		//TODO why should this be an initial infection?
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
			continueProcessingEvents = false;
			return;
		} else if (maxIterationReachedSoFar < currentIteration) {
			newDay(currentIteration);
		}

		personMap.get(event.getPersonId()).setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, event.getVaccinationType(), currentIteration - iterationOffset);
	}

	public void newDay(int currentIteration) {
		while (this.maxIterationReachedSoFar < currentIteration) {
			this.maxIterationReachedSoFar++;
			for (EpisimPerson person : personMap.values()) {
				antibodyModel.updateAntibodies(person, this.maxIterationReachedSoFar - this.iterationOffset);
				progressionModel.updateState(person, this.maxIterationReachedSoFar - this.iterationOffset);

//				if (person.getPersonId().toString().equals("1280b24")) {
//					System.out.println("it " + currentIteration + ", " + person.getAntibodies(VirusStrain.SARS_CoV_2));
//				}
//					System.out.println("		" + person.getDiseaseStatus());
//					System.out.println("		" + person.getQuarantineStatus());
//
//				}
			}
		}
	}

	@Override
	public Boolean apply(String s) {
		return continueProcessingEvents;
	}

	public boolean isContinueProcessingEvents() {
		return continueProcessingEvents;
	}
}


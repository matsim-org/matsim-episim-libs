package org.matsim.episim.model;

import org.matsim.episim.EpisimPerson;

/**
 * This class models the {@link org.matsim.episim.EpisimPerson.DiseaseStatus} state transitions at the end of the day.
 * The model should also update the {@link org.matsim.episim.EpisimPerson.QuarantineStatus} of affected persons.
 */
public interface ProgressionModel {

    /**
     * Called at the end of the day to update the state of a persons.
     */
    void updateState(EpisimPerson person, int day);

}

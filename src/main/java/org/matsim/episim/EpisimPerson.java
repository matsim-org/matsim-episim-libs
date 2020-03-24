package org.matsim.episim;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persons current state in the simulation.
 */
class EpisimPerson {

    private final Id<Person> personId;
    private final Set<EpisimPerson> traceableContactPersons = new LinkedHashSet<>();
    private final List<String> trajectory = new ArrayList<>();

    /**
     * The {@link EpisimContainer} the person is currently located in.
     */
    private EpisimContainer<?> currentContainer = null;

    /**
     * Current {@link InfectionEventHandler.DiseaseStatus}.
     */
    private InfectionEventHandler.DiseaseStatus status = InfectionEventHandler.DiseaseStatus.susceptible;

    /**
     * Current {@link InfectionEventHandler.QuarantineStatus}.
     */
    private InfectionEventHandler.QuarantineStatus quarantineStatus = InfectionEventHandler.QuarantineStatus.no;

    /**
     * Iteration when this person got infected.
     */
    private int infectionDate;

    /**
     * Iteration when this person got into quarantine.
     */
    private int quarantineDate;
    private int currentPositionInTrajectory;

    /**
     * The last visited {@link org.matsim.facilities.ActivityFacility}.
     */
    private String lastFacilityId;

    private String firstFacilityId;

    EpisimPerson(Id<Person> personId) {
        this.personId = personId;
    }

    Id<Person> getPersonId() {
        return personId;
    }

    InfectionEventHandler.DiseaseStatus getDiseaseStatus() {
        return status;
    }

    void setDiseaseStatus(InfectionEventHandler.DiseaseStatus status) {
        this.status = status;
    }

    InfectionEventHandler.QuarantineStatus getQuarantineStatus() {
        return quarantineStatus;
    }

    void setQuarantineStatus(InfectionEventHandler.QuarantineStatus quarantineStatus) {
        this.quarantineStatus = quarantineStatus;
    }

    int getInfectionDate() {
        return this.infectionDate;
    }

    void setInfectionDate(int date) {
        this.infectionDate = date;
    }

    int getQuarantineDate() {
        return this.quarantineDate;
    }

    void setQuarantineDate(int date) {
        this.quarantineDate = date;
    }

    String getLastFacilityId() {
        return this.lastFacilityId;
    }

    void setLastFacilityId(String lastFacilityId) {
        this.lastFacilityId = lastFacilityId;
    }

    void addTracableContactPerson(EpisimPerson personWrapper) {
        traceableContactPersons.add(personWrapper);
    }

    Set<EpisimPerson> getTraceableContactPersons() {
        return traceableContactPersons;
    }

    void addToTrajectory(String trajectoryElement) {
        trajectory.add(trajectoryElement);
    }

    List<String> getTrajectory() {
        return trajectory;
    }

    int getCurrentPositionInTrajectory() {
        return this.currentPositionInTrajectory;
    }

    void setCurrentPositionInTrajectory(int currentPositionInTrajectory) {
        this.currentPositionInTrajectory = currentPositionInTrajectory;
    }

    public EpisimContainer<?> getCurrentContainer() {
        return currentContainer;
    }

    /**
     * Set the container the person is currently contained in. {@link #removeCurrentContainer(EpisimContainer)} must be called before a new
     * container can be set.
     */
    public void setCurrentContainer(EpisimContainer<?> container) {
        if (this.currentContainer != null)
            throw new IllegalStateException(String.format("Person in more than one container at once. Person=%s in %s and %s",
                    this.getPersonId(), container.getContainerId(), this.currentContainer.getContainerId()));


        this.currentContainer = container;
    }

    /**
     * Whether person is currently in a container.
     */
    public boolean isInContainer() {
        return currentContainer != null;
    }

    public void removeCurrentContainer(EpisimContainer<?> container) {
        if (this.currentContainer != container)
            throw new IllegalStateException(String.format("Person is currently in %s, but not in removed one %s", currentContainer, container));

        this.currentContainer = null;
    }

    String getFirstFacilityId() {
        return firstFacilityId;
    }

    void setFirstFacilityId(String firstFacilityId) {
        this.firstFacilityId = firstFacilityId;
    }
}

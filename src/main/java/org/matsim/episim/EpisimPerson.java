package org.matsim.episim;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.episim.events.EpisimPersonStatusEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persons current state in the simulation.
 */
public final class EpisimPerson {

    private final Id<Person> personId;
    private final EventsManager eventsManager;
    private final Set<EpisimPerson> traceableContactPersons = new LinkedHashSet<>();
    private final List<String> trajectory = new ArrayList<>();
    /**
     * The {@link EpisimContainer} the person is currently located in.
     */
    private EpisimContainer<?> currentContainer = null;
    /**
     * Current {@link DiseaseStatus}.
     */
    private DiseaseStatus status = DiseaseStatus.susceptible;
    /**
     * Current {@link QuarantineStatus}.
     */
    private QuarantineStatus quarantineStatus = QuarantineStatus.no;
    /**
     * Iteration when this person got infected. Negative if person was never infected.
     */
    private int infectionDate = -1;
    /**
     * Iteration when this person got into quarantine. Negative if person was never quarantined.
     */
    private int quarantineDate = -1;
    private int currentPositionInTrajectory;
    /**
     * The last visited {@link org.matsim.facilities.ActivityFacility}.
     */
    private String lastFacilityId;
    private String firstFacilityId;

    EpisimPerson( Id<Person> personId, EventsManager eventsManager ) {
        this.personId = personId;
        this.eventsManager = eventsManager;
    }

    public Id<Person> getPersonId() {
        return personId;
    }

    public DiseaseStatus getDiseaseStatus() {
        return status;
    }

    public void setDiseaseStatus( double now, DiseaseStatus status ) {
        this.status = status;
        eventsManager.processEvent( new EpisimPersonStatusEvent( now, personId, status ) );
    }

    public QuarantineStatus getQuarantineStatus() {
        return quarantineStatus;
    }

    public void setQuarantineStatus(QuarantineStatus quarantineStatus) {
        this.quarantineStatus = quarantineStatus;
    }

    int getInfectionDate() {
        return this.infectionDate;
    }

    public void setInfectionDate(int date) {
        // yyyy should be part of status change. kai, apr'20


        if (this.infectionDate > -1)
            throw new IllegalStateException("Infection date already set");

        this.infectionDate = date;
    }

    /**
     * Days since infection (if any).
     */
    public int daysSinceInfection(int currentIteration) {
        if (infectionDate < 0) throw new IllegalStateException("Person was never infected");

        return currentIteration - infectionDate;
    }

    public int daysSinceQuarantine(int currentIteration) {
        // yyyy I would prefer to just take note of the dates of the status change, something like
        // getDateOf( DiseaseStatus status )
        // would be a much more stable API, since one would not need a separate method for each new status.  kai, apr'20

        // yyyy would make sense to keep track of the exact time steps (not just the days).  How to approach that: seconds, just count up, or keep track of
        //  days
        // separately?  kai, apr'20

        // yyyyyy since this API is so unstable, I would prefer to have the class non-public.  kai, apr'20

        if (quarantineDate < 0) throw new IllegalStateException("Person was never quarantined");

        return currentIteration - quarantineDate;
    }

    int getQuarantineDate() {
        return this.quarantineDate;
    }

    public void setQuarantineDate(int date) {
        this.quarantineDate = date;
    }

    String getLastFacilityId() {
        return this.lastFacilityId;
    }

    void setLastFacilityId(String lastFacilityId) {
        this.lastFacilityId = lastFacilityId;
    }

    public void addTraceableContactPerson(EpisimPerson personWrapper) {
        traceableContactPersons.add(personWrapper);
    }

    public Set<EpisimPerson> getTraceableContactPersons() {
        return traceableContactPersons;
    }

    void addToTrajectory(String trajectoryElement) {
        trajectory.add(trajectoryElement);
    }

    public List<String> getTrajectory() {
        return trajectory;
    }

    public int getCurrentPositionInTrajectory() {
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

    public enum DiseaseStatus {susceptible, infectedButNotContagious, contagious, seriouslySick, critical, recovered}

    public enum QuarantineStatus {full, atHome, no}
}

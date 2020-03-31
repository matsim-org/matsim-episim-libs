package org.matsim.episim.model;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.population.PopulationUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.ShutdownPolicy;
import org.matsim.vis.snapshotwriters.AgentSnapshotInfo;

import java.util.Map;
import java.util.Random;

/**
 * This class models the infection dynamics of persons staying in the same place for a certain time.
 */
public abstract class InfectionModel {

    protected final Scenario scenario = null;
    protected final Random rnd;
    protected final EpisimConfigGroup episimConfig;
    private final EpisimReporting reporting;
    private final EventsManager eventsManager;
    protected int iteration;
    private Map<String, ShutdownPolicy.Restriction> restrictions;

    protected InfectionModel( Random rnd, EpisimConfigGroup episimConfig, EpisimReporting reporting,
                              EventsManager eventsManager ) {
        this.rnd = rnd;
        this.episimConfig = episimConfig;
        this.reporting = reporting;
        this.eventsManager = eventsManager;
    }


    /**
     * Set the iteration number and restrictions that are in place.
     */
    public final void setRestrictionsForIteration(int iteration, Map<String, ShutdownPolicy.Restriction> restrictions) {
        this.iteration = iteration;
        this.restrictions = restrictions;
    }

    /**
     * This method is called when a persons leave a vehicle at {@code now}.
     */
    abstract public void infectionDynamicsVehicle(EpisimPerson personLeavingVehicle, InfectionEventHandler.EpisimVehicle vehicle, double now);

    /**
     * This method is called when a persons leaves a facility at {@code now.}
     */
    abstract public void infectionDynamicsFacility(EpisimPerson personLeavingFacility, InfectionEventHandler.EpisimFacility facility, double now, String actType);

    /**
     * Sets the infection status of a person and reports the event.
     */
    protected void infectPerson(EpisimPerson personWrapper, EpisimPerson infector, double now, String infectionType) {

        if (personWrapper.getDiseaseStatus() != EpisimPerson.DiseaseStatus.susceptible) {
            throw new IllegalStateException("Person to be infected is not susceptible. Status is=" + personWrapper.getDiseaseStatus());
        }
        if (infector.getDiseaseStatus() != EpisimPerson.DiseaseStatus.contagious) {
            throw new IllegalStateException("Infector is not contagious. Status is=" + infector.getDiseaseStatus());
        }
        if (personWrapper.getQuarantineStatus() != EpisimPerson.QuarantineStatus.no) {
            throw new IllegalStateException("Person to be infected is in quarantine.");
        }
        if (infector.getQuarantineStatus() != EpisimPerson.QuarantineStatus.no) {
            throw new IllegalStateException("Infector is in quarantine.");
        }
        if (!personWrapper.getCurrentContainer().equals(infector.getCurrentContainer())) {
            throw new IllegalStateException("Person and infector are not in same container!");
        }

        personWrapper.setDiseaseStatus( now, EpisimPerson.DiseaseStatus.infectedButNotContagious );
        if (scenario != null) {
            final Person person = PopulationUtils.findPerson(personWrapper.getPersonId(), scenario);
            if (person != null) {
                person.getAttributes().putAttribute(AgentSnapshotInfo.marker, true);
            }
        }

        personWrapper.setInfectionDate(iteration);

        reporting.reportInfection(personWrapper, infector, now, infectionType);

//        eventsManager.processEvent( new EpisimPersonStatusEvent(now, personWrapper.getPersonId(), EpisimPerson.DiseaseStatus.infectedButNotContagious ) );
        // done in EpisimPerson!
    }

    /**
     * Checks whether a person is quarantine and whether the current trip/activity is relevant for infectionDynamics. This function also considers the restrictions in place.
     * TODO rename
     */
    protected boolean isPersonActuallyOnTheGo(EpisimPerson person, EpisimContainer<?> container) {
        if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.full) {
            return false;
        }
        if (container instanceof InfectionEventHandler.EpisimFacility && activityRelevantForInfectionDynamics(person)) {
            return true;
        }
        if (container instanceof InfectionEventHandler.EpisimVehicle && tripRelevantForInfectionDynamics(person)) {
            return true;
        }
        return false;
    }

    private boolean activityRelevantForInfectionDynamics(EpisimPerson person) {
        String act = person.getTrajectory().get(person.getCurrentPositionInTrajectory());
        return actIsRelevant(act);
    }

    private boolean actIsRelevant(String act) {
        for (EpisimConfigGroup.InfectionParams infectionParams : episimConfig.getContainerParams().values()) {
            if (infectionParams.includesActivity(act)) {
                ShutdownPolicy.Restriction r = restrictions.get(infectionParams.getContainerName());
                // avoid use of rnd if outcome is known beforehand
                if (r.getRemainingFraction() == 1)
                    return true;
                if (r.getRemainingFraction() == 0)
                    return false;

                return rnd.nextDouble() < r.getRemainingFraction();
            }
        }

        throw new IllegalStateException(String.format("No restrictions known for activity %s. Please add prefix to one infection parameter.", act));
    }

    private boolean tripRelevantForInfectionDynamics(EpisimPerson person) {
        String lastAct = "";
        if (person.getCurrentPositionInTrajectory() != 0) {
            lastAct = person.getTrajectory().get(person.getCurrentPositionInTrajectory() - 1);
        }

        String nextAct = person.getTrajectory().get(person.getCurrentPositionInTrajectory());

        // TODO: tr is a hardcoded activity for "pt"
        // last activity is only considered if present
        return actIsRelevant("tr") && actIsRelevant(nextAct) && (lastAct.isEmpty() || actIsRelevant(lastAct));

    }
}

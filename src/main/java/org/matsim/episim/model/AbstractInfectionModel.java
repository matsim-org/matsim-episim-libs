package org.matsim.episim.model;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.ShutdownPolicy;
import org.matsim.vis.snapshotwriters.AgentSnapshotInfo;

import java.util.Map;
import java.util.Random;


/**
 * Base implementation for infection dynamics.
 */
public abstract class AbstractInfectionModel implements InfectionModel {

    protected final Scenario scenario = null;
    protected final Random rnd;
    protected final EpisimConfigGroup episimConfig;
    private final EpisimReporting reporting;
    protected int iteration;
    private Map<String, ShutdownPolicy.Restriction> restrictions;

    protected AbstractInfectionModel(Random rnd, EpisimConfigGroup episimConfig, EpisimReporting reporting) {
        this.rnd = rnd;
        this.episimConfig = episimConfig;
        this.reporting = reporting;
    }


    /**
     * Set the iteration number and restrictions that are in place.
     */
    @Override
    public final void setRestrictionsForIteration(int iteration, Map<String, ShutdownPolicy.Restriction> restrictions) {
        this.iteration = iteration;
        this.restrictions = restrictions;
    }

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

        personWrapper.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.infectedButNotContagious);

        // TODO: Currently not in use, is it still needed?
        if (scenario != null) {
            final Person person = PopulationUtils.findPerson(personWrapper.getPersonId(), scenario);
            if (person != null) {
                person.getAttributes().putAttribute(AgentSnapshotInfo.marker, true);
            }
        }

        personWrapper.setInfectionDate(iteration);

        reporting.reportInfection(personWrapper, infector, now, infectionType);
    }
    public Map<String, ShutdownPolicy.Restriction> getRestrictions(){
        return restrictions;
    }

    private static boolean activityRelevantForInfectionDynamics( EpisimPerson person, EpisimConfigGroup episimConfig,
                                                                 Map<String, ShutdownPolicy.Restriction> restrictions, Random rnd ) {
        String act = person.getTrajectory().get(person.getCurrentPositionInTrajectory());
        return actIsRelevant(act, episimConfig, restrictions, rnd );
    }

    private static boolean actIsRelevant( String act, EpisimConfigGroup episimConfig,
                                          Map<String, ShutdownPolicy.Restriction> restrictions, Random rnd ) {
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

    private static boolean tripRelevantForInfectionDynamics( EpisimPerson person, EpisimConfigGroup episimConfig,
                                                             Map<String, ShutdownPolicy.Restriction> restrictions, Random rnd ) {
        String lastAct = "";
        if (person.getCurrentPositionInTrajectory() != 0) {
            lastAct = person.getTrajectory().get(person.getCurrentPositionInTrajectory() - 1);
        }

        String nextAct = person.getTrajectory().get(person.getCurrentPositionInTrajectory());

        // TODO: tr is a hardcoded activity for "pt"
        // last activity is only considered if present
        return actIsRelevant("tr", episimConfig, restrictions, rnd ) && actIsRelevant(nextAct, episimConfig, restrictions,
                        rnd ) && (lastAct.isEmpty() || actIsRelevant(lastAct, episimConfig,
                        restrictions, rnd ));

    }

    /**
     * Checks whether a persons and container is relevant for the infection dynamics. This function also considers the restrictions in place.
     */
    static boolean personRelevantForInfectionDynamics( EpisimPerson person, EpisimContainer<?> container, EpisimConfigGroup episimConfig,
                                                       Map<String, ShutdownPolicy.Restriction> restrictions, Random rnd ) {
        if (!EpisimUtils.hasStatusRelevantForInfectionDynamics(person)) {
            return false;
        }
        if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.full) {
            return false;
        }
        if (container instanceof InfectionEventHandler.EpisimFacility && activityRelevantForInfectionDynamics(person, episimConfig, restrictions, rnd )) {
            return true;
        }
        if (container instanceof InfectionEventHandler.EpisimVehicle && tripRelevantForInfectionDynamics(person, episimConfig, restrictions, rnd )) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether person is relevant for tracking (and also infection dynamics).
     * Basically, a person is relevant for tracking if it is relevant for infection except that persons in status of {@code DiseaseStatus.infectedButNotContagious} are also considered.
     * @see #personRelevantForInfectionDynamics(EpisimPerson, EpisimContainer, EpisimConfigGroup, Map, Random)
     * @noinspection BooleanMethodIsAlwaysInverted
     */
    static boolean personRelevantForTracking( EpisimPerson person, EpisimContainer<?> container, EpisimConfigGroup episimConfig,
                                              Map<String, ShutdownPolicy.Restriction> restrictions, Random rnd ) {

        // Infected but not contagious persons are considered additionally
        if (!EpisimUtils.hasStatusRelevantForInfectionDynamics(person) &&
                person.getDiseaseStatus() != EpisimPerson.DiseaseStatus.infectedButNotContagious)
            return false;

        if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.full) {
            return false;
        }

        if (container instanceof InfectionEventHandler.EpisimFacility && activityRelevantForInfectionDynamics(person, episimConfig, restrictions, rnd )) {
            return true;
        }
        if (container instanceof InfectionEventHandler.EpisimVehicle && tripRelevantForInfectionDynamics(person, episimConfig, restrictions, rnd )) {
            return true;
        }
        return false;
    }

}

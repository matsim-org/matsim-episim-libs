package org.matsim.episim.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.episim.*;

import java.util.ArrayList;
import java.util.Random;

/**
 * This infection model calculates the joint time two persons have been at the same place and calculates a infection probability according to:
 * <pre>
 *    1 - e^(calibParam * contactIntensity * jointTimeInContainer)
 * </pre>
 */
public class DefaultInfectionModel extends InfectionModel {

    private static final Logger log = LogManager.getLogger(DefaultInfectionModel.class);

    private enum InfectionSituation {Vehicle, Facility}

    public DefaultInfectionModel(Random rnd, EpisimConfigGroup episimConfig, EpisimReporting reporting) {
        super(rnd, episimConfig, reporting);
    }

    @Override
    public void infectionDynamicsVehicle(EpisimPerson personLeavingVehicle, InfectionEventHandler.EpisimVehicle vehicle, double now) {
//        infectionDynamicsGeneralized(personLeavingVehicle, vehicle, now, vehicle.getContainerId().toString());
        infectionDynamicsGeneralized(personLeavingVehicle, vehicle, now, InfectionSituation.Vehicle);
    }

    @Override
    public void infectionDynamicsFacility(EpisimPerson personLeavingFacility, InfectionEventHandler.EpisimFacility facility, double now, String actType) {
        infectionDynamicsGeneralized(personLeavingFacility, facility, now, InfectionSituation.Facility);
    }

    private void infectionDynamicsGeneralized(EpisimPerson personLeavingContainer, EpisimContainer<?> container, double now, InfectionSituation infectionSituation) {

        if (iteration == 0) {
            return;
        }

        if (!isRelevantForInfectionDynamics(personLeavingContainer, container)) {
            return;
        }

        ArrayList<EpisimPerson> personsToInteractWith = new ArrayList<>(container.getPersons());
        personsToInteractWith.remove(personLeavingContainer);


        // For the time being, will just assume that the first 10 persons are the ones we interact with.  Note that because of
        // shuffle, those are 10 different persons every day.
        // as sample size is 25%, 10 persons means 3 agents here
        int contactPersonsToFind = Math.max((int) (episimConfig.getSampleSize() * 10), 3);
        for ( int ii = 0 ; ii< personsToInteractWith.size(); ii++ ) {
            //as we now forbid infection for certain activity type pairs, we do need the separate counter. schlenther, march 27

            //if we have seen enough, break, no matter what
            if(contactPersonsToFind <= 0){
                break;
            }

            // (this is "-1" because we can't interact with "self")

            // we are essentially looking at the situation when the person leaves the container.  Interactions with other persons who have
            // already left the container were treated then.  In consequence, we have some "circle of persons around us" (yyyy which should
            //  depend on the density), and then a probability of infection in either direction.

            //TODO the way we iterate here, chances exist that we do draw the same otherPerson twice, right? schlenther, march 27
            int idx = rnd.nextInt(personsToInteractWith.size());
            EpisimPerson otherPerson = personsToInteractWith.get(idx);

            // (we count "quarantine" as well since they essentially represent "holes", i.e. persons who are no longer there and thus the
            // density in the transit container goes down.  kai, mar'20)

            if (personLeavingContainer.getDiseaseStatus() == otherPerson.getDiseaseStatus()) {
                // (if they have the same status, then nothing can happen between them)
                continue;
            }

            if (!isRelevantForInfectionDynamics(otherPerson, container)) {
                continue;
            }

            String leavingPersonsActivity = personLeavingContainer.getTrajectory().get(personLeavingContainer.getCurrentPositionInTrajectory());
            String otherPersonsActivity = otherPerson.getTrajectory().get(otherPerson.getCurrentPositionInTrajectory());

            String infectionType = leavingPersonsActivity + "_" + otherPersonsActivity;

            //forbid certain cross-activity infections, keep track of contacts
            //we can not track contact persons in vehicles
            if (infectionSituation.equals(InfectionSituation.Facility)){
                //home can only interact with home or leisure
                if (infectionType.contains("home") && ! (infectionType.contains("leisure") || infectionType.equals("home_home"))  ){
                    continue;
                } else if (infectionType.contains("edu") && ! infectionType.contains("work") && ! ( leavingPersonsActivity.contains("edu") && otherPersonsActivity.contains("edu") )){
                    //edu can only interact with work or edu
                    continue;
                }

                trackContactPerson(personLeavingContainer, otherPerson, leavingPersonsActivity);
            }

            contactPersonsToFind --;

            Double containerEnterTimeOfPersonLeaving = container.getContainerEnteringTime(personLeavingContainer.getPersonId());
            Double containerEnterTimeOfOtherPerson = container.getContainerEnteringTime(otherPerson.getPersonId());

            // persons leaving their first-ever activity have no starting time for that activity.  Need to hedge against that.  Since all persons
            // start healthy (the first seeds are set at enterVehicle), we can make some assumptions.
            if (containerEnterTimeOfPersonLeaving == null && containerEnterTimeOfOtherPerson == null) {
                throw new IllegalStateException("should not happen");
                // null should only happen at first activity.  However, at first activity all persons are susceptible.  So the only way we
                // can get here is if an infected person entered the container and is now leaving again, while the other person has been in the
                // container from the beginning.  ????  kai, mar'20
            }
            if (containerEnterTimeOfPersonLeaving == null) {
                containerEnterTimeOfPersonLeaving = Double.NEGATIVE_INFINITY;
            }
            if (containerEnterTimeOfOtherPerson == null) {
                containerEnterTimeOfOtherPerson = Double.NEGATIVE_INFINITY;
            }

            double jointTimeInContainer = now - Math.max(containerEnterTimeOfPersonLeaving, containerEnterTimeOfOtherPerson);
            if (jointTimeInContainer < 0 || jointTimeInContainer > 86400) {
                log.warn(containerEnterTimeOfPersonLeaving);
                log.warn(containerEnterTimeOfOtherPerson);
                log.warn(now);
                throw new IllegalStateException("joint time in container is not plausible for personLeavingContainer=" + personLeavingContainer.getPersonId() + " and otherPerson=" + otherPerson.getPersonId() + ". Joint time is=" + jointTimeInContainer);
            }

            double contactIntensity = getContactIntensity(container, infectionSituation, leavingPersonsActivity, otherPersonsActivity);


            double infectionProba = 1 - Math.exp(-episimConfig.getCalibrationParameter() * contactIntensity * jointTimeInContainer);
            // note that for 1pct runs, calibParam is of the order of one, which means that for typical times of 100sec or more,
            // exp( - 1 * 1 * 100 ) \approx 0, and thus the infection proba becomes 1.  Which also means that changes in contactIntensity has
            // no effect.  kai, mar'20

            if (rnd.nextDouble() < infectionProba) {
                if (personLeavingContainer.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible) {
                    infectPerson(personLeavingContainer, otherPerson, now, infectionType);
                    return;
                } else {
                    infectPerson(otherPerson, personLeavingContainer, now, infectionType);
                }
            }
        }
    }

    private double getContactIntensity(EpisimContainer<?> container, InfectionSituation infectionSituation, String leavingPersonsActivity, String otherPersonsActivity) {
        double contactIntensity = -1;
        if(infectionSituation.equals(InfectionSituation.Vehicle)){
            if(! (container instanceof InfectionEventHandler.EpisimVehicle) ){
                throw new IllegalArgumentException();
            }
            String containerIdString = container.getContainerId().toString();

            for (EpisimConfigGroup.InfectionParams infectionParams : episimConfig.getContainerParams().values()) {
                if(infectionParams.includesActivity(containerIdString)){
                    contactIntensity = infectionParams.getContactIntensity();
                }
            }
            if(contactIntensity < 0.){
                throw new IllegalStateException("contactIntensity not defined for vehicle container=" + containerIdString + ".  There needs to be a config entry for each activity type.");
            }
        } else{
            double contactIntensityLeavingPerson = -1;
            double contactIntensityOtherPerson = -1;
            for (EpisimConfigGroup.InfectionParams infectionParams : episimConfig.getContainerParams().values()) {
                if (infectionParams.includesActivity(leavingPersonsActivity)) {
                    contactIntensityLeavingPerson = infectionParams.getContactIntensity();
                }
                if(infectionParams.includesActivity(otherPersonsActivity)){
                    contactIntensityOtherPerson = infectionParams.getContactIntensity();
                }
            }
            if (contactIntensityLeavingPerson < 0. || contactIntensityOtherPerson < 0.) {
                throw new IllegalStateException("contactIntensity not defined either for activityType=" + contactIntensityLeavingPerson + " or for activityType= " + otherPersonsActivity
                        + ".  There needs to be a config entry for each activity type.");
            }

            contactIntensity = Math.max(contactIntensityLeavingPerson, contactIntensityOtherPerson);
        }
        return contactIntensity;
    }

    private void trackContactPerson(EpisimPerson personLeavingContainer, EpisimPerson otherPerson, String leavingPersonsActivity) {
        if (leavingPersonsActivity.contains("home") || leavingPersonsActivity.contains("work") || (leavingPersonsActivity.contains("leisure") && rnd.nextDouble() < 0.8)) {
            if (!personLeavingContainer.getTraceableContactPersons().contains(otherPerson)) {
                personLeavingContainer.addTraceableContactPerson(otherPerson);
            }
            if (!otherPerson.getTraceableContactPersons().contains(personLeavingContainer)) {
                otherPerson.addTraceableContactPerson(personLeavingContainer);
            }
        }
    }

}

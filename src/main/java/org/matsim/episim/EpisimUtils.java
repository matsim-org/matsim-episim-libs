package org.matsim.episim;

public class EpisimUtils {

    /**
     * Calculates the time based on the current iteration.
     *
     * @param time time relative to start of day
     */
    public static double getCorrectedTime( double time, long iteration ) {
        return Math.min(time, 3600. * 24) + iteration * 24. * 3600;
    }

    public static boolean hasStatusRelevantForInfectionDynamics(EpisimPerson personWrapper) {
        switch (personWrapper.getDiseaseStatus()) {
            case susceptible:
                return true;
            case infectedButNotContagious:
                return false;
            case contagious:
                return true;
            case seriouslySick:
                return false; // assume is in hospital
            case critical:
                return false; // assume is in hospital
            case recovered:
                return false;
            default:
                throw new IllegalStateException("Unexpected value: " + personWrapper.getDiseaseStatus());
        }
    }
}

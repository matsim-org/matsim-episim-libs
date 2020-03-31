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
            case contagious:
                return true;

            case infectedButNotContagious:
            case recovered:
            case seriouslySick: // assume is in hospital
            case critical:
                return false;

            default:
                throw new IllegalStateException("Unexpected value: " + personWrapper.getDiseaseStatus());
        }
    }

    /**
     * this method checks whether person1 and person2 have relevant disease status for infection dynamics. If not or if both have the same disease status, the return value is false.
     * @param person1
     * @param person2
     * @return
     */
    public static boolean canPersonsInfectEachOther(EpisimPerson person1, EpisimPerson person2) {
        if(person1.getDiseaseStatus() == person2.getDiseaseStatus()) return false;
        return (hasStatusRelevantForInfectionDynamics(person1) && hasStatusRelevantForInfectionDynamics(person2));
    }
}

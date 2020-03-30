package org.matsim.episim.model;

import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.EpisimUtils;

import java.util.Random;

/**
 * Default progression model with deterministic (but random) state transitions at fixed days.
 */
public class DefaultProgressionModel implements ProgressionModel {

    private final Random rnd;
    private final EpisimConfigGroup episimConfig;

    public DefaultProgressionModel(Random rnd, EpisimConfigGroup episimConfig) {
        this.rnd = rnd;
        this.episimConfig = episimConfig;
    }

    @Override
    public void updateState(EpisimPerson person, int day) {
        double now = EpisimUtils.getCorrectedTime( 24.*3600, day );
        switch (person.getDiseaseStatus()) {
            case susceptible:
                break;
            case infectedButNotContagious:
                if (person.daysSinceInfection(day) >= 4) {
                    person.setDiseaseStatus( now , EpisimPerson.DiseaseStatus.contagious );
                }
                break;
            case contagious:
                if (person.daysSinceInfection(day) == 6) {
                    final double nextDouble = rnd.nextDouble();
                    if (nextDouble < 0.2) {
                        // 20% recognize that they are sick and go into quarantine:

                        person.setQuarantineDate(day);
                        // yyyy date needs to be qualified by status (or better, add iteration into quarantine status setter)

                        person.setQuarantineStatus(EpisimPerson.QuarantineStatus.full);
                        // yyyy this should become "home"!  kai, mar'20

                        if (episimConfig.getPutTracablePersonsInQuarantine() == EpisimConfigGroup.PutTracablePersonsInQuarantine.yes) {
                            for (EpisimPerson pw : person.getTraceableContactPersons()) {
                                if (pw.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no) {

                                    pw.setQuarantineStatus(EpisimPerson.QuarantineStatus.full);
                                    // yyyy this should become "home"!  kai, mar'20

                                    pw.setQuarantineDate(day);
                                    // yyyy date needs to be qualified by status (or better, add iteration into
                                    // quarantine status setter)

                                }
                            }
                        }

                    }
                } else if (person.daysSinceInfection(day) == 10) {
                    if (rnd.nextDouble() < 0.045) {
                        // (4.5% get seriously sick.  This is taken from all infected persons, not just those the have shown
                        // symptoms before)
                        person.setDiseaseStatus( now, EpisimPerson.DiseaseStatus.seriouslySick );
                    }
                } else if (person.daysSinceInfection(day) >= 16) {
                    person.setDiseaseStatus( now, EpisimPerson.DiseaseStatus.recovered );
                }
                break;
            case seriouslySick:
                if (person.daysSinceInfection(day) == 11) {
                    if (rnd.nextDouble() < 0.25) {
                        // (25% of persons who are seriously sick transition to critical)
                        person.setDiseaseStatus( now, EpisimPerson.DiseaseStatus.critical );
                    }
                } else if (person.daysSinceInfection(day) >= 23) {
                    person.setDiseaseStatus( now, EpisimPerson.DiseaseStatus.recovered );
                }
                break;
            case critical:
                if (person.daysSinceInfection(day) == 20) {
                    // (transition back to seriouslySick.  Note that this needs to be earlier than sSick->recovered, otherwise
                    // they stay in sSick.  Problem is that we need differentiation between intensive care beds and normal
                    // hospital beds.)
                    person.setDiseaseStatus( now, EpisimPerson.DiseaseStatus.seriouslySick );
                }
                break;
            case recovered:
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + person.getDiseaseStatus());
        }
        if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.full && person.daysSinceQuarantine(day) >= 14) {
            person.setQuarantineStatus(EpisimPerson.QuarantineStatus.no);
        }
        person.getTraceableContactPersons().clear();
    }


    @Override
    public boolean canProgress(EpisimReporting.InfectionReport report) {
        return report.nTotalInfected > 0 || report.nInQuarantine > 0;
    }
}

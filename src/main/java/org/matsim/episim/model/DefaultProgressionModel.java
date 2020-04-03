package org.matsim.episim.model;

import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.EpisimUtils;

import java.util.Random;

/**
 * Default progression model with deterministic (but random) state transitions at fixed days.
 */
public final class DefaultProgressionModel implements ProgressionModel {

    private final Random rnd;
    private final EpisimConfigGroup episimConfig;

    public DefaultProgressionModel(Random rnd, EpisimConfigGroup episimConfig) {
        this.rnd = rnd;
        this.episimConfig = episimConfig;
    }

    @Override
    public void updateState(EpisimPerson person, int day) {
        // Called at the beginning of iteration
        double now = EpisimUtils.getCorrectedTime(0, day);
        switch (person.getDiseaseStatus()) {
            case susceptible:
                break;
            case infectedButNotContagious:
                if (person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) >= 4) {
                    person.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.contagious);
                }
                break;
            case contagious:
                if (person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) == 6) {
                    final double nextDouble = rnd.nextDouble();
                    if (nextDouble < 0.2) {
                        // 20% recognize that they are sick and go into quarantine:

                        // Diamond Princess study: (only) 18% show no symptoms.

                        person.setQuarantineStatus(EpisimPerson.QuarantineStatus.full, day);
                        // yyyy this should become "home"!  kai, mar'20

                        if (episimConfig.getPutTracablePersonsInQuarantine() == EpisimConfigGroup.PutTracablePersonsInQuarantine.yes) {
                            for (EpisimPerson pw : person.getTraceableContactPersons()) {
                                if (pw.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no) { //what if tracked person has recovered

                                    pw.setQuarantineStatus(EpisimPerson.QuarantineStatus.full, day);
                                    // yyyy this should become "home"!  kai, mar'20

                                }
                            }
                        }

                    }
                } else if (person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) == 10) {
                    if (rnd.nextDouble() < 0.045) {
                        // (4.5% get seriously sick.  This is taken from all infected persons, not just those the have shown
                        // symptoms before)
                        person.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.seriouslySick);
                    }
                } else if (person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) >= 16) {
                    person.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.recovered);
                }
                break;
            case seriouslySick:
                if (person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) == 11) {
                    if (rnd.nextDouble() < 0.25) {
                        // (25% of persons who are seriously sick transition to critical)
                        person.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.critical);
                    }
                } else if (person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) >= 23) {
                    person.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.recovered);
                }
                break;
            case critical:
                if (person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) == 20) {
                    // (transition back to seriouslySick.  Note that this needs to be earlier than sSick->recovered, otherwise
                    // they stay in sSick.  Problem is that we need differentiation between intensive care beds and normal
                    // hospital beds.)
                    person.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.seriouslySick);
                }
                break;
            case recovered:
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + person.getDiseaseStatus());
        }
        if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.full && person.daysSinceQuarantine(day) >= 14) {
            // TODO overwrites previous quarantine date, but this is not used at the moment
            person.setQuarantineStatus(EpisimPerson.QuarantineStatus.no, day);
        }
        person.getTraceableContactPersons().clear(); //so we can only track contact persons over 1 day
    }


    @Override
    public boolean canProgress(EpisimReporting.InfectionReport report) {
        return report.nTotalInfected > 0 || report.nInQuarantine > 0;
    }
}

package org.matsim.episim.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.EpisimUtils;

import java.util.Random;

/**
 * Default progression model with deterministic (but random) state transitions at fixed days.
 */
public final class DefaultProgressionModel implements ProgressionModel {

	private static final Logger log = LogManager.getLogger(ProgressionModel.class);
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

                        // Diamond Princess study: (only) 18% show no symptoms.

                        person.setQuarantineDate(day);
                        // yyyy date needs to be qualified by status (or better, add iteration into quarantine status setter)

                        person.setQuarantineStatus(EpisimPerson.QuarantineStatus.full);
                        // yyyy this should become "home"!  kai, mar'20

                        if (episimConfig.getPutTracablePersonsInQuarantine() == EpisimConfigGroup.PutTracablePersonsInQuarantine.yes) {
                            for (EpisimPerson pw : person.getTraceableContactPersons()) {
                                if (pw.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no) { //what if tracked person has recovered

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
                	double proba = getAgeDependantProbaOfTransitioningToSeriouslySick(person , now);
                	if (rnd.nextDouble() < proba) {
                        person.setDiseaseStatus( now, EpisimPerson.DiseaseStatus.seriouslySick );
                    }
                } else if (person.daysSinceInfection(day) >= 16) {
                    person.setDiseaseStatus( now, EpisimPerson.DiseaseStatus.recovered );
                }
                break;
            case seriouslySick:
                if (person.daysSinceInfection(day) == 11) {
                    double proba = getAgeDependantProbaOfTransitioningToCritical(person, now);
                	if (rnd.nextDouble() < proba) {
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
        person.getTraceableContactPersons().clear(); //so we can only track contact persons over 1 day
    }


    private double getAgeDependantProbaOfTransitioningToSeriouslySick(EpisimPerson person, double now) {
		
    	double proba = -1;
    	
    	if (person.getAttributes().getAsMap().containsKey("age")) {
    		int age = (int) person.getAttributes().getAttribute("age");
    		
    		if (age < 20) {
    			proba = 0.004;
    		}
    		else if (age < 45) {
    			proba = 0.031;
    		}
    		else if (age < 55) {
    			proba = 0.043;
    		}
    		else if (age < 65) {
    			proba = 0.044;
    		}
    		else if (age < 75) {
    			proba = 0.063;
    		}
    		else if (age < 85) {
    			proba = 0.078;
    		}
    		else {
    			proba = 0.089;
    		}	
			
		}
		else {
//			log.warn("Person=" + person.getPersonId().toString() + " has no age. Transition to seriusly sick is not age dependent.");
			proba = 0.045;
		}
    	
    	return proba;	
	}
    
    private double getAgeDependantProbaOfTransitioningToCritical(EpisimPerson person, double now) {
		
    	double proba = -1;
    	
    	if (person.getAttributes().getAsMap().containsKey("age")) {
    		int age = (int) person.getAttributes().getAttribute("age");
    		
    		if (age < 20) {
    			proba = 0.;
    		}
    		else if (age < 45) {
    			proba = 0.182;
    		}
    		else if (age < 55) {
    			proba = 0.328;
    		}
    		else if (age < 65) {
    			proba = 0.323;
    		}
    		else if (age < 75) {
    			proba = 0.384;
    		}
    		else if (age < 85) {
    			proba = 0.479;
    		}
    		else {
    			proba = 0.357;
    		}	
			
		}
		else {
//			log.warn("Person=" + person.getPersonId().toString() + " has no age. Transition to critical is not age dependent.");
			proba = 0.25;
		}
    	
    	return proba;	
	}

	@Override
    public boolean canProgress(EpisimReporting.InfectionReport report) {
        return report.nTotalInfected > 0 || report.nInQuarantine > 0;
    }
}

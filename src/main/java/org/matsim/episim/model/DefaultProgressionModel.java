package org.matsim.episim.model;

import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.EpisimUtils;

import java.util.SplittableRandom;

/**
 * Default progression model with deterministic (but random) state transitions at fixed days.
 */
public final class DefaultProgressionModel implements ProgressionModel {

	private final SplittableRandom rnd;
	private final EpisimConfigGroup episimConfig;

	public DefaultProgressionModel(SplittableRandom rnd, EpisimConfigGroup episimConfig) {
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
					if (nextDouble < 0.8) {
						// 80% show symptoms and go into quarantine
						// Diamond Princess study: (only) 18% show no symptoms.
						person.setDiseaseStatus(now, DiseaseStatus.showingSymptoms);
						person.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);

						if (episimConfig.getPutTracablePersonsInQuarantine() == EpisimConfigGroup.PutTracablePersonsInQuarantine.yes) {
							for (EpisimPerson pw : person.getTraceableContactPersons()) {
								if (pw.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no) { //what if tracked person has recovered

									pw.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);

								}
							}
						}

					}
				}
				else if (person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) >= 16) {
					person.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.recovered);
				}
				break;
			case showingSymptoms:
				if (person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) == 10) {
					double proba = getAgeDependantProbaOfTransitioningToSeriouslySick(person, now);
					if (rnd.nextDouble() < proba) {
						person.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.seriouslySick);
					}
				}
				else if (person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) >= 16) {
					person.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.recovered);
				}
				break;
			case seriouslySick:
				if (person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) == 11) {
					double proba = getAgeDependantProbaOfTransitioningToCritical(person, now);
					if (rnd.nextDouble() < proba) {
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
				// one day after recovering person is released from quarantine
				if (person.getQuarantineStatus() != EpisimPerson.QuarantineStatus.no)
					person.setQuarantineStatus(EpisimPerson.QuarantineStatus.no, day);

				break;
			default:
				throw new IllegalStateException("Unexpected value: " + person.getDiseaseStatus());
		}

		person.getTraceableContactPersons().clear(); //so we can only track contact persons over 1 day
	}


	private double getAgeDependantProbaOfTransitioningToSeriouslySick(EpisimPerson person, double now) {

		double proba = -1;

		if (person.getAttributes().getAsMap().containsKey("age")) {
			int age = (int) person.getAttributes().getAttribute("age");

			if (age < 20) {
				proba = 0.005;
			} else if (age < 45) {
				proba = 0.039;
			} else if (age < 55) {
				proba = 0.054;
			} else if (age < 65) {
				proba = 0.056;
			} else if (age < 75) {
				proba = 0.079;
			} else if (age < 85) {
				proba = 0.098;
			} else {
				proba = 0.112;
			}

		} else {
//			log.warn("Person=" + person.getPersonId().toString() + " has no age. Transition to seriusly sick is not age dependent.");
			proba = 0.05625;
		}

		return proba;
	}

	private double getAgeDependantProbaOfTransitioningToCritical(EpisimPerson person, double now) {

		double proba = -1;

		if (person.getAttributes().getAsMap().containsKey("age")) {
			int age = (int) person.getAttributes().getAttribute("age");

			if (age < 20) {
				proba = 0.;
			} else if (age < 45) {
				proba = 0.182;
			} else if (age < 55) {
				proba = 0.328;
			} else if (age < 65) {
				proba = 0.323;
			} else if (age < 75) {
				proba = 0.384;
			} else if (age < 85) {
				proba = 0.479;
			} else {
				proba = 0.357;
			}

		} else {
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

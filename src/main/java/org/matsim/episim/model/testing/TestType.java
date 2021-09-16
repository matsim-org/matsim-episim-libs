package org.matsim.episim.model.testing;


import org.matsim.episim.EpisimPerson;

/**
 * Type of tests that can be configured individually.
 */
public enum TestType {

	/**
	 * A rapid test.
	 */
	RAPID_TEST,

	/**
	 * Test based on polymerase chain reaction.
	 */
	PCR;


	/**
	 * Whether the test can detect a positive result correctly.
	 */
	public boolean canDetectPositive(EpisimPerson person, int day) {

		EpisimPerson.DiseaseStatus status = person.getDiseaseStatus();

		if (this == RAPID_TEST) {
			return (status == EpisimPerson.DiseaseStatus.contagious && person.daysSince(EpisimPerson.DiseaseStatus.contagious, day) >= 2) || status == EpisimPerson.DiseaseStatus.showingSymptoms;
		} else if (this == PCR) {
			return status == EpisimPerson.DiseaseStatus.contagious || status == EpisimPerson.DiseaseStatus.showingSymptoms ||
					(status == EpisimPerson.DiseaseStatus.infectedButNotContagious && person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) >= 2);
		}

		throw new IllegalStateException("No testing procedure implemented.");
	}

	/**
	 * Whether the test should normally detect a negative result.
	 */
	public boolean shouldDetectNegative(EpisimPerson person, int day) {

		EpisimPerson.DiseaseStatus status = person.getDiseaseStatus();

		if (this == RAPID_TEST) {
			return (status == EpisimPerson.DiseaseStatus.contagious && person.daysSince(EpisimPerson.DiseaseStatus.contagious, day) < 1)  || status == EpisimPerson.DiseaseStatus.infectedButNotContagious
					|| status == EpisimPerson.DiseaseStatus.susceptible || status == EpisimPerson.DiseaseStatus.recovered;

		} else if (this == PCR) {
			return status == EpisimPerson.DiseaseStatus.susceptible || status == EpisimPerson.DiseaseStatus.recovered ||
					(status == EpisimPerson.DiseaseStatus.infectedButNotContagious && person.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, day) < 2);
		}

		throw new IllegalStateException("No testing procedure implemented.");
	}


}

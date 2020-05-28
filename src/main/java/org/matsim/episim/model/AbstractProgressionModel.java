package org.matsim.episim.model;

import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.EpisimUtils;

import javax.inject.Inject;
import java.util.SplittableRandom;

/**
 * Abstract base implementation for a progression model that stores and updates state transitions.
 * It does *not* contain any decision logic when and to which state the disease will progress.
 */
abstract class AbstractProgressionModel implements ProgressionModel {

	protected final SplittableRandom rnd;
	protected final EpisimConfigGroup episimConfig;

	/**
	 * Stores the next state a person will attain.
	 */
	private final MutableIntIntMap nextStates = new IntIntHashMap();

	/**
	 * The day at which a transition to {@link #nextStates} happens.
	 */
	private final MutableIntIntMap transitionDays = new IntIntHashMap();

	@Inject
	AbstractProgressionModel(SplittableRandom rnd, EpisimConfigGroup episimConfig) {
		this.rnd = rnd;
		this.episimConfig = episimConfig;
	}


	@Override
	public void updateState(EpisimPerson person, int day) {

		EpisimPerson.DiseaseStatus status = person.getDiseaseStatus();

		// No transitions from susceptible
		if (status == EpisimPerson.DiseaseStatus.susceptible)
			return;

		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, day);
		int index = person.getPersonId().index();

		if (status == EpisimPerson.DiseaseStatus.recovered) {
			// one day after recovering person is released from quarantine
			if (person.getQuarantineStatus() != EpisimPerson.QuarantineStatus.no)
				person.setQuarantineStatus(EpisimPerson.QuarantineStatus.no, day);

			return;
		}

		int transitionDay = transitionDays.getIfAbsent(index, -1);
		if (transitionDay > -1) {
			int nextState = nextStates.get(index);
			int daysSince = person.daysSince(status, day);
			if (transitionDay == daysSince) {
				EpisimPerson.DiseaseStatus next = EpisimPerson.DiseaseStatus.values()[nextState];
				person.setDiseaseStatus(now, next);
				onTransition(person, now, day, status, next);

				if (next != EpisimPerson.DiseaseStatus.recovered) {
					if (updateNext(person, index, next))
						updateState(person, day);
				}
			}
		} else {
			if (updateNext(person, index, status))
				updateState(person, day);
		}
	}

	/**
	 * Set next transition state and day for a person.
	 *
	 * @return true when there should be an immediate update again
	 */
	private boolean updateNext(EpisimPerson person, int index, EpisimPerson.DiseaseStatus from) {
		EpisimPerson.DiseaseStatus next = decideNextState(person);
		int nextTransitionDay = decideTransitionDay(person, from, next);

		nextStates.put(index, next.ordinal());
		transitionDays.put(index, nextTransitionDay);

		// allow multiple updates on the same day
		return nextTransitionDay == 0;
	}

	/**
	 * Choose the next state a person will attain.
	 */
	protected abstract EpisimPerson.DiseaseStatus decideNextState(EpisimPerson person);

	/**
	 * Chose how long a person stays in {@code from} until the disease changes to {@code to}.
	 */
	protected abstract int decideTransitionDay(EpisimPerson person, EpisimPerson.DiseaseStatus from, EpisimPerson.DiseaseStatus to);

	/**
	 * Arbitrary function that can be overwritten to perform actions on state transitions.
	 */
	protected void onTransition(EpisimPerson person, double now, int day, EpisimPerson.DiseaseStatus from, EpisimPerson.DiseaseStatus to) {
	}

	@Override
	public boolean canProgress(EpisimReporting.InfectionReport report) {
		return report.nTotalInfected > 0 || report.nInQuarantine > 0;
	}

}

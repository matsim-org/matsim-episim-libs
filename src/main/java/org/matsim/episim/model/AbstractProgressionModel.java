package org.matsim.episim.model;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.EpisimUtils;

import javax.inject.Inject;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.SplittableRandom;

/**
 * Abstract base implementation for a progression model that stores and updates state transitions.
 * It does *not* contain any decision logic when and to which state the disease will progress.
 */
abstract class AbstractProgressionModel implements ProgressionModel, Externalizable {

	protected final SplittableRandom rnd;
	protected final EpisimConfigGroup episimConfig;

	/**
	 * Stores the next state and after which day. (int & int) = 64bit
	 */
	private final Object2LongMap<Id<Person>> nextStateAndDay = new Object2LongOpenHashMap<>();

	@Inject
	AbstractProgressionModel(SplittableRandom rnd, EpisimConfigGroup episimConfig) {
		this.rnd = rnd;
		this.episimConfig = episimConfig;
	}

	/**
	 * Stores two ints in one long value.
	 */
	private static long compoundLong(int x, int y) {
		return (((long) x) << 32) | (y & 0xffffffffL);
	}

	@Override
	public void updateState(EpisimPerson person, int day) {

		EpisimPerson.DiseaseStatus status = person.getDiseaseStatus();

		// No transitions from susceptible
		if (status == EpisimPerson.DiseaseStatus.susceptible)
			return;

		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, day);
		Id<Person> id = person.getPersonId();

		if (status == EpisimPerson.DiseaseStatus.recovered) {
			// one day after recovering person is released from quarantine
			if (person.getQuarantineStatus() != EpisimPerson.QuarantineStatus.no)
				person.setQuarantineStatus(EpisimPerson.QuarantineStatus.no, day);

			return;
		}

		// 0 is empty transition
		long value = nextStateAndDay.getOrDefault(id, 0);

		if (value != 0) {

			// reverse of compound long
			int transitionDay = (int) value;
			int nextState = (int) (value >> 32);

			int daysSince = person.daysSince(status, day);
			if (daysSince >= transitionDay) {
				EpisimPerson.DiseaseStatus next = EpisimPerson.DiseaseStatus.values()[nextState];
				person.setDiseaseStatus(now, next);
				onTransition(person, now, day, status, next);

				if (next != EpisimPerson.DiseaseStatus.recovered) {
					if (updateNext(person, id, next))
						updateState(person, day);
				}
			}
		} else {
			if (updateNext(person, id, status))
				updateState(person, day);
		}
	}

	/**
	 * Set next transition state and day for a person.
	 *
	 * @return true when there should be an immediate update again
	 */
	private boolean updateNext(EpisimPerson person, Id<Person> id, EpisimPerson.DiseaseStatus from) {
		EpisimPerson.DiseaseStatus next = decideNextState(person);
		int nextTransitionDay = decideTransitionDay(person, from, next);

		nextStateAndDay.put(id, compoundLong(next.ordinal(), nextTransitionDay));

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
		return report.nTotalInfected > 0 || report.nInQuarantineFull + report.nInQuarantineHome > 0;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(nextStateAndDay.size());
		for (Object2LongMap.Entry<Id<Person>> entry : nextStateAndDay.object2LongEntrySet()) {
			EpisimUtils.writeChars(out, entry.getKey().toString());
			out.writeLong(entry.getLongValue());
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException {
		int n = in.readInt();
		for (int i = 0; i < n; i++) {
			Id<Person> key = Id.createPersonId(EpisimUtils.readChars(in));
			nextStateAndDay.put(key, in.readLong());
		}
	}
}

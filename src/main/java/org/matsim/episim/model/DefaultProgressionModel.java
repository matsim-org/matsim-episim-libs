/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.episim.model;

import com.google.inject.Inject;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;
import org.matsim.episim.*;
import org.matsim.episim.EpisimPerson.DiseaseStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Default progression model with deterministic (but random) state transitions at fixed days.
 * This class in designed to for subclassing to support defining different transition probabilities.
 */
public class DefaultProgressionModel implements ProgressionModel {

	private static final double DAY = 24. * 3600;

	/**
	 * Definition of state transitions from {@code status} to next state.
	 *
	 * @see Transition
	 */
	private static final Map<DiseaseStatus, Transition> STATES = new EnumMap<>(DiseaseStatus.class);

	static {
		STATES.putAll(Map.of(
				DiseaseStatus.infectedButNotContagious, Transition.fixed(4),
				DiseaseStatus.contagious, Transition.fixed(2),
				DiseaseStatus.showingSymptoms, Transition.fixed(4),
				DiseaseStatus.seriouslySick, Transition.fixed(1),
				DiseaseStatus.critical, Transition.fixed(9)
		));
	}

	private final SplittableRandom rnd;
	private final EpisimConfigGroup episimConfig;
	private final TracingConfigGroup tracingConfig;

	/**
	 * Maps person id to the day of next state transition.
	 */
	private final MutableIntIntMap transitions = new IntIntHashMap();

	@Inject
	public DefaultProgressionModel(SplittableRandom rnd, EpisimConfigGroup episimConfig, TracingConfigGroup tracingConfig) {
		this.rnd = rnd;
		this.episimConfig = episimConfig;
		this.tracingConfig = tracingConfig;
	}

	@Override
	public final void updateState(EpisimPerson person, int day) {
		// Called at the beginning of iteration
		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, day);
		switch (person.getDiseaseStatus()) {
			case susceptible:

				// A healthy quarantined person is dismissed from quarantine after some time
				if (person.getQuarantineStatus() != EpisimPerson.QuarantineStatus.no && person.daysSinceQuarantine(day) > 14) {
					person.setQuarantineStatus(EpisimPerson.QuarantineStatus.no, day);
				}

				break;
			case infectedButNotContagious:
				if (shouldTransition(person, DiseaseStatus.infectedButNotContagious, day)) {
					person.setDiseaseStatus(now, DiseaseStatus.contagious);
				}
				break;
			case contagious:

				if (shouldTransition(person, DiseaseStatus.contagious, day)) {
					final double nextDouble = rnd.nextDouble();
					if (nextDouble < 0.8) {
						// 80% show symptoms and go into quarantine
						// Diamond Princess study: (only) 18% show no symptoms.
						person.setDiseaseStatus(now, DiseaseStatus.showingSymptoms);
						person.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);

						// Perform tracing immediately if there is no delay, otherwise needs to be done when person shows symptoms
						if (tracingConfig.getTracingDelay() == 0) {
							performTracing(person, now, day);
						}
					}

				} else if (person.daysSince(DiseaseStatus.contagious, day) >= 12) {
					person.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.recovered);
				}
				break;
			case showingSymptoms:

				// person switches to showing symptoms exactly at day 6, so we account for the delay here
				if (person.daysSince(DiseaseStatus.showingSymptoms, day) == tracingConfig.getTracingDelay()) {
					performTracing(person, now - tracingConfig.getTracingDelay() * DAY, day);
				}

				if (shouldTransition(person, DiseaseStatus.showingSymptoms, day)) {
					double proba = getProbaOfTransitioningToSeriouslySick(person, now);
					if (rnd.nextDouble() < proba) {
						person.setDiseaseStatus(now, DiseaseStatus.seriouslySick);
					}

				} else if (person.daysSince(DiseaseStatus.showingSymptoms, day) >= 10) {
					person.setDiseaseStatus(now, DiseaseStatus.recovered);
				}
				break;
			case seriouslySick:
				if (!person.hadDiseaseStatus(DiseaseStatus.critical) &&
						shouldTransition(person, DiseaseStatus.seriouslySick, day)) {
					double proba = getProbaOfTransitioningToCritical(person, now);
					if (rnd.nextDouble() < proba) {
						person.setDiseaseStatus(now, DiseaseStatus.critical);
					}
				} else if (person.daysSince(DiseaseStatus.seriouslySick, day) >= 13) {
					person.setDiseaseStatus(now, DiseaseStatus.recovered);
				}
				break;
			case critical:
				if (shouldTransition(person, DiseaseStatus.critical, day)) {
					// (transition back to seriouslySick.  Note that this needs to be earlier than sSick->recovered, otherwise
					// they stay in sSick.  Problem is that we need differentiation between intensive care beds and normal
					// hospital beds.)
					person.setDiseaseStatus(now, DiseaseStatus.seriouslySick);
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

		// clear tracing if not relevant anymore
		person.clearTraceableContractPersons(now - (tracingConfig.getTracingDelay() + tracingConfig.getTracingDayDistance() + 1) * DAY);
	}

	/**
	 * Probability that a persons transitions from {@code showingSymptoms} to {@code seriouslySick}.
	 */
	protected double getProbaOfTransitioningToSeriouslySick(EpisimPerson person, double now) {
		return 0.05625;
	}

	/**
	 * Probability that a persons transitions from {@code seriouslySick} to {@code critical}.
	 */
	protected double getProbaOfTransitioningToCritical(EpisimPerson person, double now) {
		return 0.25;
	}

	/**
	 * Determine whether a person disease status should transition to next status.
	 */
	private boolean shouldTransition(EpisimPerson person, DiseaseStatus status, int day) {
		int index = person.getPersonId().index();
		int daysSince = person.daysSince(status, day);
		int transitionDay = transitions.getIfAbsent(index, -1);

		if (transitionDay > -1 && transitionDay == daysSince) {
			transitions.remove(index);
			return true;
		}

		Transition t = STATES.get(status);
		transitionDay = t.getTransitionDay(rnd);

		// transition day is drawn one time and then cached
		if (transitionDay == daysSince)
			return true;

		transitions.put(index, transitionDay);
		return false;
	}

	/**
	 * Perform the tracing procedure for a person. Also ensures if enabled for current day.
	 */
	private void performTracing(EpisimPerson person, double now, int day) {

		if (day < tracingConfig.getPutTraceablePersonsInQuarantineAfterDay()) return;

		String homeId = null;

		// quarantine household flag controls direct household and 2nd order household
		if (tracingConfig.getQuarantineHousehold())
			homeId = (String) person.getAttributes().getAttribute("homeId");

		// TODO: tracing household members makes always sense, no app or anything needed..
		// they might not appear as contact persons under certain circumstances

		for (EpisimPerson pw : person.getTraceableContactPersons(now - tracingConfig.getTracingDayDistance() * DAY)) {

			// don't draw random number when tracing is practically off
			if (tracingConfig.getTracingProbability() == 0 && homeId == null)
				continue;

			// Persons of the same household are always traced successfully
			if ((homeId != null && homeId.equals(pw.getAttributes().getAttribute("homeId")))
					|| rnd.nextDouble() < tracingConfig.getTracingProbability())

				quarantinePerson(pw, day);

		}
	}

	private void quarantinePerson(EpisimPerson p, int day) {

		if (p.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no && p.getDiseaseStatus() != DiseaseStatus.recovered) {
			p.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);
		}
	}

	@Override
	public final boolean canProgress(EpisimReporting.InfectionReport report) {
		return report.nTotalInfected > 0 || report.nInQuarantine > 0;
	}


}

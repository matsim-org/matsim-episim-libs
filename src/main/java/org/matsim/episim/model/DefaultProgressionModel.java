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

	private static final double DAY = 24. * 3600;
	private final SplittableRandom rnd;
	private final EpisimConfigGroup episimConfig;

	@Inject
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

				// A healthy quarantined person is dismissed from quarantine after some time
				if (person.getQuarantineStatus() != EpisimPerson.QuarantineStatus.no && person.daysSinceQuarantine(day) > 14) {
					person.setQuarantineStatus(EpisimPerson.QuarantineStatus.no, day);
				}

				break;
			case infectedButNotContagious:
				if (person.daysSince(DiseaseStatus.infectedButNotContagious, day) >= 4) {
					person.setDiseaseStatus(now, DiseaseStatus.contagious);
				}
				break;
			case contagious:

				if (day >= episimConfig.getPutTraceablePersonsInQuarantineAfterDay()) {

					// 10% chance of getting randomly tested and detected each day
					// TODO: actually rather independent from tracing...
					if (rnd.nextDouble() < 0.1) {
						onInfectionDetected(person, now, day);
					}
				}

				if (person.daysSince(DiseaseStatus.infectedButNotContagious, day) == 6) {
					final double nextDouble = rnd.nextDouble();
					if (nextDouble < 0.8) {
						// 80% show symptoms and go into quarantine
						// Diamond Princess study: (only) 18% show no symptoms.
						person.setDiseaseStatus(now, DiseaseStatus.showingSymptoms);
						onInfectionDetected(person, now, day);
					}

				} else if (person.daysSince(DiseaseStatus.infectedButNotContagious, day) >= 16) {
					person.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.recovered);
				}
				break;
			case showingSymptoms:
				if (person.daysSince(DiseaseStatus.infectedButNotContagious, day) == 10) {
					double proba = 0.05625;
					if (rnd.nextDouble() < proba) {
						person.setDiseaseStatus(now, DiseaseStatus.seriouslySick);
					}

				} else if (person.daysSince(DiseaseStatus.infectedButNotContagious, day) >= 16) {
					person.setDiseaseStatus(now, DiseaseStatus.recovered);
				}
				break;
			case seriouslySick:
				if (person.daysSince(DiseaseStatus.infectedButNotContagious, day) == 11) {
					double proba = 0.25;
					if (rnd.nextDouble() < proba) {
						person.setDiseaseStatus(now, DiseaseStatus.critical);
					}
				} else if (person.daysSince(DiseaseStatus.infectedButNotContagious, day) >= 23) {
					person.setDiseaseStatus(now, DiseaseStatus.recovered);
				}
				break;
			case critical:
				if (person.daysSince(DiseaseStatus.infectedButNotContagious, day) == 20) {
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

		// clear tracing older than 7 days
		person.clearTraceableContractPersons(now - 7 * DAY);
	}

	/**
	 * Called when the infection of a person is known, either through testing or symptoms.
	 */
	private void onInfectionDetected(EpisimPerson person, double now, int day) {

		person.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);

		// perform tracing
		if (day >= episimConfig.getPutTraceablePersonsInQuarantineAfterDay()) {

			String homeId = (String) person.getAttributes().getAttribute("homeId");

			// TODO: tracing household members makes always sense, no app or anything needed..
			// they might not appear as contact persons under certain circumstances

			for (EpisimPerson pw : person.getTraceableContactPersons(now - episimConfig.getTracingDayDistance() * DAY)) {

				// Persons of the same household are always traced successfully
				if ((homeId != null && homeId.equals(pw.getAttributes().getAttribute("homeId")))
						|| rnd.nextDouble() < episimConfig.getTracingProbability())

					quarantinePerson(pw, day);

			}
		}
	}

	private void quarantinePerson(EpisimPerson p, int day) {


		if (p.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no && p.getDiseaseStatus() != DiseaseStatus.recovered) {
			p.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);

			String homeId = (String) p.getAttributes().getAttribute("homeId");

			// put every member of household into quarantine
			if (homeId != null)
				for (EpisimPerson other : p.getTraceableContactPersons(0)) {

					String otherHome = (String) other.getAttributes().getAttribute("homeId");
					if (homeId.equals(otherHome) && other.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no
							&& other.getDiseaseStatus() != DiseaseStatus.recovered)

						p.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);
				}
		}
	}

	@Override
	public boolean canProgress(EpisimReporting.InfectionReport report) {
		return report.nTotalInfected > 0 || report.nInQuarantine > 0;
	}
}

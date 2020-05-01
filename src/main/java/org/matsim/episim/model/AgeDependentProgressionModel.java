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

import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.TracingConfigGroup;

import com.google.inject.Inject;

import java.util.SplittableRandom;

/**
 * Works exactly as the {@link DefaultProgressionModel}, but with age dependent transitions.
 */
public final class AgeDependentProgressionModel extends DefaultProgressionModel {

	/**
	 * Constructor as in {@link DefaultProgressionModel}.
	 */
	@Inject
	public AgeDependentProgressionModel(SplittableRandom rnd, EpisimConfigGroup episimConfig, TracingConfigGroup tracingConfig) {
		super(rnd, episimConfig, tracingConfig);
	}

	@Override
	protected double getProbaOfTransitioningToSeriouslySick(EpisimPerson person, double now) {

		double proba = -1;

		if (person.getAttributes().getAsMap().containsKey("age")) {
			int age = (int) person.getAttributes().getAttribute("age");

			if (age < 0 || age > 120) {
				throw new RuntimeException("Age of person=" + person.getPersonId().toString() + " is not plausible. Age is=" + age);
			}

			if (age < 10) {
				proba = 0.06 / 100;
			} else if (age < 20) {
				proba = 0.19 / 100;
			} else if (age < 30) {
				proba = 0.77 / 100;
			} else if (age < 40) {
				proba = 2.06 / 100;
			} else if (age < 50) {
				proba = 3.16 / 100;
			} else if (age < 60) {
				proba = 6.57 / 100;
			} else if (age < 70) {
				proba = 10.69 / 100;
			} else if (age < 80) {
				proba = 15.65 / 100;
			} else {
				proba = 17.58 / 100;
			}

		} else {
			throw new RuntimeException("Person=" + person.getPersonId().toString() + " has no age. Age dependent progression is not possible.");
		}

		return proba;
	}

	@Override
	protected double getProbaOfTransitioningToCritical(EpisimPerson person, double now) {
		double proba = -1;

		if (person.getAttributes().getAsMap().containsKey("age")) {
			int age = (int) person.getAttributes().getAttribute("age");

			if (age < 0 || age > 120) {
				throw new RuntimeException("Age of person=" + person.getPersonId().toString() + " is not plausible. Age is=" + age);
			}

			if (age < 40) {
				proba = 5. / 100;
			} else if (age < 50) {
				proba = 6.3 / 100;
			} else if (age < 60) {
				proba = 12.2 / 100;
			} else if (age < 70) {
				proba = 27.4 / 100;
			} else if (age < 80) {
				proba = 43.2 / 100;
			} else {
				proba = 70.9 / 100;
			}

		} else {
			throw new RuntimeException("Person=" + person.getPersonId().toString() + " has no age. Age dependent progression is not possible.");
		}

		return proba;
	}

}

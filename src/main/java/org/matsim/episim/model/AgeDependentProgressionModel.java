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

import org.matsim.episim.*;

import com.google.inject.Inject;

import java.util.SplittableRandom;

/**
 * Works exactly as the {@link ConfigurableProgressionModel}, but with age dependent transitions.
 */
public class AgeDependentProgressionModel extends ConfigurableProgressionModel {

	/**
	 * Constructor as in {@link ConfigurableProgressionModel}.
	 */
	@Inject
	public AgeDependentProgressionModel(SplittableRandom rnd, EpisimConfigGroup episimConfig, TracingConfigGroup tracingConfig, VirusStrainConfigGroup strainConfig) {
		super(rnd, episimConfig, tracingConfig, strainConfig);
	}

	@Override
	protected double getProbaOfTransitioningToContagious(EpisimPerson person) {

		double proba = 1.;

//		int age = EpisimUtils.getAge( person );
//
//		if (age < 10) {
//			proba = 0.1 / 100;
//		} else if (age < 20) {
//			proba = 0.3 / 100;
//		} else if (age < 30) {
//			proba = 1.2 / 100;
//		} else if (age < 40) {
//			proba = 3.2 / 100;
//		} else if (age < 50) {
//			proba = 4.9 / 100;
//		} else if (age < 60) {
//			proba = 10.2 / 100;
//		} else if (age < 70) {
//			proba = 16.6 / 100;
//		} else if (age < 80) {
//			proba = 24.3 / 100;
//		} else {
//			proba = 27.3 / 100;
//		}

		return proba ;
	}

	@Override
	protected double getProbaOfTransitioningToShowingSymptoms (EpisimPerson person) {

		double proba = 0.8;

//		int age = EpisimUtils.getAge( person );
//
//		if (age < 10) {
//			proba = 0.1 / 100;
//		} else if (age < 20) {
//			proba = 0.3 / 100;
//		} else if (age < 30) {
//			proba = 1.2 / 100;
//		} else if (age < 40) {
//			proba = 3.2 / 100;
//		} else if (age < 50) {
//			proba = 4.9 / 100;
//		} else if (age < 60) {
//			proba = 10.2 / 100;
//		} else if (age < 70) {
//			proba = 16.6 / 100;
//		} else if (age < 80) {
//			proba = 24.3 / 100;
//		} else {
//			proba = 27.3 / 100;
//		}

		return proba;
	}

	@Override
	protected double getProbaOfTransitioningToSeriouslySick(EpisimPerson person) {

		double proba = -1;

		int age = person.getAge();

		if (age < 10) {
			proba = 0.1 / 100;
		} else if (age < 20) {
			proba = 0.3 / 100;
		} else if (age < 30) {
			proba = 1.2 / 100;
		} else if (age < 40) {
			proba = 3.2 / 100;
		} else if (age < 50) {
			proba = 4.9 / 100;
		} else if (age < 60) {
			proba = 10.2 / 100;
		} else if (age < 70) {
			proba = 16.6 / 100;
		} else if (age < 80) {
			proba = 24.3 / 100;
		} else {
			proba = 27.3 / 100;
		}

		return proba * episimConfig.getHospitalFactor();
	}

	@Override
	protected double getProbaOfTransitioningToCritical(EpisimPerson person) {
		double proba = -1;

		int age = person.getAge();

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

		return proba;
	}


}

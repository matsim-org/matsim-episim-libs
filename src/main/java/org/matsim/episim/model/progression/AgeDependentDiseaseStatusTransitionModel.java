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
package org.matsim.episim.model.progression;

import org.matsim.episim.*;

import com.google.inject.Inject;
import org.matsim.episim.model.ConfigurableProgressionModel;

import java.util.SplittableRandom;

/**
 * Works exactly as the {@link ConfigurableProgressionModel}, but with age dependent transitions.
 */
public class AgeDependentDiseaseStatusTransitionModel extends AntibodyDependentTransitionModel {

	private final EpisimConfigGroup episimConfig;

	@Inject
	public AgeDependentDiseaseStatusTransitionModel(SplittableRandom rnd, EpisimConfigGroup episimConfig,
	                                                VaccinationConfigGroup vaccinationConfig, VirusStrainConfigGroup strainConfigGroup) {
		super(rnd, vaccinationConfig, strainConfigGroup);
		this.episimConfig = episimConfig;
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
	public double getProbaOfTransitioningToSeriouslySick(Immunizable person) {

		double proba;

		int age = person.getAge();

		// source 3
		if (age < 5) {
			proba = 4.0 / 100;
		} else if (age < 15) {
			proba = 1.1 / 100;
		} else if (age < 35) {
			proba = 2.4 / 100;
		} else if (age < 60) {
			proba = 5.6 / 100;
		} else if (age < 80) {
			proba = 23. / 100;
		} else {
			proba = 36. / 100;
		}

		return proba * episimConfig.getHospitalFactor();


		// source 2
//		if (age < 10) {
//			proba = 0.1 / 100;
//		} else if (age < 20) {
//			proba = 0.2 / 100;
//		} else if (age < 30) {
//			proba = 0.5 / 100;
//		} else if (age < 40) {
//			proba = 1.0 / 100;
//		} else if (age < 50) {
//			proba = 2.1 / 100;
//		} else if (age < 60) {
//			proba = 4.1 / 100;
//		} else if (age < 70) {
//			proba = 8.9 / 100;
//		} else if (age < 80) {
//			proba = 17.1 / 100;
//		} else {
//			proba = 30.3 / 100;
//		}

		// source 1
//		if (age < 5) {
//			proba = 12.9 / 100;
//		} else if (age < 20) {
//			proba = 4.0 / 100;
//		} else if (age < 40) {
//			proba = 5.9 / 100;
//		} else if (age < 60) {
//			proba = 13.0 / 100;
//		} else if (age < 80) {
//			proba = 43.2 / 100;
//		} else {
//			proba = 64.8 / 100;
//		}


		//old
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


	}

	@Override
	public double getProbaOfTransitioningToCritical(Immunizable person) {
		double proba = -1;

		int age = person.getAge();

		// source 3
		if (age < 5) {
			proba = 7.0 / 100;
		} else if (age < 15) {
			proba = 0.0 / 100;
		} else if (age < 35) {
			proba = 15.0 / 100;
		} else if (age < 60) {
			proba = 30.0 / 100;
		} else if (age < 80) {
			proba = 41. / 100;
		} else {
			proba = 27. / 100;
		}


		// source 2
//		if (age < 10) {
//			proba = 8.5 / 100;
//		} else if (age < 20) {
//			proba = 10.9 / 100;
//		} else if (age < 30) {
//			proba = 13.4 / 100;
//		} else if (age < 40) {
//			proba = 17.2 / 100;
//		} else if (age < 50) {
//			proba = 21.9 / 100;
//		} else if (age < 60) {
//			proba = 27.3 / 100;
//		} else if (age < 70) {
//			proba = 37.1 / 100;
//		} else if (age < 80) {
//			proba = 48.5/ 100;
//		} else {
//			proba = 64.0 / 100;
//		}

////		// source 1
//		if (age < 5) {
//			proba = 3.5 / 100;
//		} else if (age < 20) {
//			proba = 1.7 / 100;
//		} else if (age < 40) {
//			proba = 7.3 / 100;
//		} else if (age < 60) {
//			proba = 18.0 / 100;
//		} else if (age < 80) {
//			proba = 37.0 / 100;
//		} else {
//			proba = 42.9 / 100;
//		}
//

		// old
//		if (age < 40) {
//			proba = 5. / 100;
//		} else if (age < 50) {
//			proba = 6.3 / 100;
//		} else if (age < 60) {
//			proba = 12.2 / 100;
//		} else if (age < 70) {
//			proba = 27.4 / 100;
//		} else if (age < 80) {
//			proba = 43.2 / 100;
//		} else {
//			proba = 70.9 / 100;
//		}

		return proba;
	}


}

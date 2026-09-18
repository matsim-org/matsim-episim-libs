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
import org.matsim.episim.model.ImmunityModel;

import org.matsim.episim.util.EpisimSplittableRandom;

/**
 * Works exactly as the {@link ConfigurableProgressionModel}, but with age dependent transitions.
 *
 * <p>The base (age-dependent) probabilities are read from {@link PathogenConfigGroup}; the only modifier that
 * stays in this model is the {@code hospitalFactor} applied to the {@code seriouslySick} transition.</p>
 */
public class AgeDependentDiseaseStatusTransitionModel extends AntibodyDependentTransitionModel {

	private final EpisimConfigGroup episimConfig;

	@Inject
	public AgeDependentDiseaseStatusTransitionModel(EpisimSplittableRandom rnd, ImmunityModel immunityModel,
	                                                EpisimConfigGroup episimConfig,
	                                                VaccinationConfigGroup vaccinationConfig, VirusStrainConfigGroup strainConfigGroup,
	                                                PathogenConfigGroup pathogenConfig) {
		super(rnd, immunityModel, vaccinationConfig, strainConfigGroup, pathogenConfig, true);
		this.episimConfig = episimConfig;
	}

	@Override
	public double getProbaOfTransitioningToSeriouslySick(Immunizable person) {
		// https://docs.google.com/spreadsheets/d/1jmaerl27LKidD1uk3azdIL1LmvHuxazNQlhVo9xO1z8/edit#gid=802030488
		// age table taken from https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8353925/, now configured in PathogenConfigGroup.
		return super.getProbaOfTransitioningToSeriouslySick(person) * episimConfig.getHospitalFactor();
	}

}

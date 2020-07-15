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
package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.AbstractSnzScenario;

/**
 * Older scenario with now outdated information.
 */
public final class SchoolClosure implements BatchRun<SchoolClosure.Params> {

	@Override
	public Config baseCase(int id) {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		config.plans().setInputFile("../be_entirePopulation_noPlans_withDistrict.xml.gz");


		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../be_snz_episim_events.xml.gz");
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.000002);
		episimConfig.setInitialInfections(5);

		AbstractSnzScenario.addParams(episimConfig);
		AbstractSnzScenario.setContactIntensities(episimConfig);

		return config;
	}

	@Override
	public Config prepareConfig(int id, SchoolClosure.Params params) {

		Config config = baseCase(id);
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		com.typesafe.config.Config policyConf = FixedPolicy.config()
				.restrict(23 + params.offset, params.remainingFractionLeisure1, "leisure")
				.restrict(23 + params.offset, 0.1, "educ_primary", "educ_kiga")
				.restrict(23 + params.offset, 0., "educ_secondary", "educ_higher")
				.restrict(32 + params.offset, params.remainingFractionLeisure2, "leisure")
				.restrict(32 + params.offset, params.remainingFractionWork, "work")
				.restrict(32 + params.offset, params.remainingFractionShoppingBusinessErrands, "shopping", "errands", "business")
				.restrict(60 + params.offset, params.remainingFractionKiga, "educ_kiga")
				.restrict(60 + params.offset, params.remainingFractionPrima, "educ_primary")
				.restrict(60 + params.offset, params.remainingFractionSecon, "educ_secondary")
				.build();

		String policyFileName = "input/policy" + id + ".conf";
		episimConfig.setOverwritePolicyLocation(policyFileName);
		episimConfig.setPolicy(FixedPolicy.class, policyConf);

		return config;
	}

	public static final class Params {

		@IntParameter({-6, -3, 0})
		int offset;

		@Parameter({0.5, 0.1})
		double remainingFractionKiga;

		@Parameter({0.5, 0.1})
		double remainingFractionPrima;

		@Parameter({0.5, 0.})
		double remainingFractionSecon;

		@Parameter({0.8, 0.6, 0.4, 0.2})
		double remainingFractionLeisure1;

		@Parameter({0.2, 0})
		double remainingFractionLeisure2;

		@Parameter({0.8, 0.6, 0.4})
		double remainingFractionWork;

		@Parameter({0.4, 0.2})
		double remainingFractionShoppingBusinessErrands;

	}

}

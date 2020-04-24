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
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzScenario;

import java.util.List;
import java.util.Map;

public final class SchoolClosureAndMasks implements BatchRun<SchoolClosureAndMasks.Params> {

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

		SnzScenario.addParams(episimConfig);
		SnzScenario.setContactIntensities(episimConfig);

		return config;
	}

	@Override
	public Config prepareConfig(int id, SchoolClosureAndMasks.Params params) {

		Config config = baseCase(id);
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		com.typesafe.config.Config policyConf = FixedPolicy.config()
				.restrict(23 + params.offset, 0.6, "leisure")
				.restrict(23 + params.offset, 0.1, "educ_primary", "educ_kiga")
				.restrict(23 + params.offset, 0., "educ_secondary", "educ_higher")
				.restrict(32 + params.offset, 0., "leisure")
				.restrict(32 + params.offset, 0.6, "work")
				.restrict(32 + params.offset, 0.4, "shopping", "errands", "business")
				.restrict(67 + params.offset, Restriction.of(1, FaceMask.CLOTH), params.maskHome)
				.restrict(67 + params.offset, Restriction.of(params.remainingFractionLeisure, FaceMask.CLOTH), "leisure")
				.restrict(67 + params.offset, Restriction.of(params.remainingFractionWork, FaceMask.CLOTH), "work")
				.restrict(67 + params.offset, Restriction.of(params.remainingFractionShoppingBusinessErrands, FaceMask.CLOTH), "shopping", "errands", "business")
				.restrict(67 + params.offset, Restriction.of(0.1, FaceMask.CLOTH), "educ_primary", "educ_kiga")
				.restrict(67 + params.offset, Restriction.of(1, FaceMask.CLOTH), "pt", "tr")
				.restrict(74 + params.offset, Restriction.of(params.remainingFractionKiga, FaceMask.CLOTH), "educ_kiga")
				.restrict(74 + params.offset, Restriction.of(params.remainingFractionPrima, FaceMask.CLOTH), "educ_primary")
				.build();

		String policyFileName = "input/policy" + id + ".conf";
		episimConfig.setOverwritePolicyLocation(policyFileName);
		episimConfig.setPolicy(FixedPolicy.class, policyConf);
		episimConfig.setMaskCompliance(params.maskCompliance);
		return config;
	}

	@Override
	public Map<Integer, List<String>> getMeasures() {
		return Map.of(
				23, List.of("remainingFractionLeisure1"),
				32, List.of("remainingFractionLeisure2", "remainingFractionWork", "remainingFractionShoppingBusinessErrands"),
				60, List.of("remainingFractionKiga", "remainingFractionPrima", "remainingFractionSecon")
		);
	}

	public static final class Params {

		@IntParameter({3, 0, 6})
		int offset;
		
		@Parameter({0., 0.5, 0.9, 1.})
		double maskCompliance;
		
		@StringParameter({"noHome", "home"})
		String maskHome;

		@Parameter({0.5, 0.1})
		double remainingFractionKiga;

		@Parameter({0.5, 0.1})
		double remainingFractionPrima;

		@Parameter({0.2, 0.})
		double remainingFractionLeisure;

		@Parameter({0.8, 0.6})
		double remainingFractionWork;

		@Parameter({0.6, 0.4})
		double remainingFractionShoppingBusinessErrands;

	}

}

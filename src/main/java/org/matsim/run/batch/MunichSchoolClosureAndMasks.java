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

import java.time.LocalDate;
import java.util.List;

/**
 * See {@link BerlinSchoolClosureAndMasks}, but for munich.
 */
public final class MunichSchoolClosureAndMasks implements BatchRun<MunichSchoolClosureAndMasks.Params> {
	@Override
	public LocalDate getDefaultStartDate() {
		return LocalDate.of(2020, 2, 21);
	}

	@Override
	public Config baseCase(int id) {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		config.plans().setInputFile("../mu_entirePopulation_noPlans_withDistricts.xml.gz");


		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../mu_snz_episim_events.xml.gz");
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.000_001_7);
		episimConfig.setInitialInfections(50);
		episimConfig.setInitialInfectionDistrict("München");

		SnzScenario.addParams(episimConfig);
		SnzScenario.setContactIntensities(episimConfig);

		return config;
	}

	@Override
	public List<Option> getOptions() {
		return BerlinSchoolClosureAndMasks.OPTIONS;
	}

	@Override
	public Config prepareConfig(int id, MunichSchoolClosureAndMasks.Params params) {

		Config config = baseCase(id);
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		int offset = params.offset;
		FaceMask wornMask = FaceMask.valueOf(params.mask);
		episimConfig.setMaskCompliance(params.maskCompliance);


		com.typesafe.config.Config policyConf = FixedPolicy.config()
				//taken from Google mobility report

				//holidays
				.restrict(1 - offset, 0.95, "work")
				.restrict(2 - offset, 0.9, "work")
				.restrict(3 - offset, 0.85, "work")
				.restrict(4 - offset, 0.75, "work")
				.restrict(6 - offset, 0.85, "work")
				.restrict(9 - offset, 0.9, "work")
				.restrict(10 - offset, 0.95, "work")
				.restrict(11 - offset, 1.0, "work")

				//..
				.restrict(21 - offset, 0.95, "work")
				.restrict(23 - offset, 0.9, "work")
				.restrict(24 - offset, 0.85, "work")
				.restrict(25 - offset, 0.8, "work")
				.restrict(26 - offset, 0.75, "work")
				.restrict(27 - offset, 0.7, "work")
				.restrict(28 - offset, 0.65, "work")
				.restrict(30 - offset, 0.6, "work")
				.restrict(32 - offset, 0.55, "work")

				//other
				.restrict(6 - offset, 0.95, "shopping", "errands", "business")
				.open(7 - offset, "shopping", "errands", "business")
				.restrict(14 - offset, 0.95, "shopping", "errands", "business")
				.open(15 - offset, "shopping", "errands", "business")
				.restrict(19 - offset, 0.95, "shopping", "errands", "business")
				.open(20 - offset, "shopping", "errands", "business")
				.restrict(26 - offset, 0.90, "shopping", "errands", "business")
				.restrict(27 - offset, 0.80, "shopping", "errands", "business")
				.restrict(28 - offset, 0.75, "shopping", "errands", "business")
				.restrict(29 - offset, 0.80, "shopping", "errands", "business")
				.restrict(30 - offset, 0.70, "shopping", "errands", "business")
				.restrict(31 - offset, 0.65, "shopping", "errands", "business")
				.restrict(32 - offset, 0.55, "shopping", "errands", "business")
				.restrict(36 - offset, 0.6, "shopping", "errands", "business")
				.restrict(44 - offset, 0.65, "shopping", "errands", "business")
				.restrict(46 - offset, 0.7, "shopping", "errands", "business")
				.restrict(48 - offset, 0.75, "shopping", "errands", "business")
				.restrict(49 - offset, 0.8, "shopping", "errands", "business")
				.restrict(51 - offset, 0.70, "shopping", "errands", "business")
				.restrict(55 - offset, 0.65, "shopping", "errands", "business")

				//leisure
				.restrict(18 - offset, 0.94, "leisure")
				.restrict(19 - offset, 0.87, "leisure")
				.restrict(20 - offset, 0.81, "leisure")
				.restrict(21 - offset, 0.74, "leisure")
				.restrict(22 - offset, 0.68, "leisure")
				.restrict(23 - offset, 0.61, "leisure")
				.restrict(24 - offset, 0.55, "leisure")
				.restrict(25 - offset, 0.49, "leisure")
				.restrict(26 - offset, 0.42, "leisure")
				.restrict(27 - offset, 0.36, "leisure")
				.restrict(28 - offset, 0.29, "leisure")
				.restrict(29 - offset, 0.23, "leisure")
				.restrict(30 - offset, 0.16, "leisure")
				.restrict(31 - offset, 0.1, "leisure")

				//edu
				.restrict(23 - offset, 0.1, "educ_primary", "educ_kiga")
				.restrict(23 - offset, 0., "educ_secondary", "educ_higher")

				// Google mobility data currently stops at day 58 (18.04.2020)
//				.restrict(58 - offset, params.remainingFractionWork, "work")
//				.restrict(58 - offset, params.remainingFractionShoppingBusinessErrands, "shopping", "errands", "business")
//				.restrict(58 - offset, params.remainingFractionLeisure, "leisure")
				// masks are worn from day 67 onwards (27.04.2020); compliance is set via config
				.restrict(67 - offset, Restriction.of(params.remainingFractionWork, wornMask), "work")
				.restrict(67 - offset, Restriction.of(params.remainingFractionShoppingBusinessErrands, wornMask), "shopping", "errands", "business")
				.restrict(67 - offset, Restriction.of(params.remainingFractionLeisure, wornMask), "leisure")
				.restrict(67 - offset, Restriction.of(1, wornMask), "pt", "tr")
				.restrict(67 - offset, Restriction.of(0.1, wornMask), "educ_primary", "educ_kiga")
				// edu facilities can be reopend from day 74 (04.05.2020)
				.restrict(74 - offset, Restriction.of(params.remainingFractionKiga, wornMask), "educ_kiga")
				.restrict(74 - offset, Restriction.of(params.remainingFractionPrima, wornMask), "educ_primary")
				.restrict(74 - offset, Restriction.of(params.remainingFractionSeconHigher, wornMask), "educ_secondary", "educ_higher")
				.build();

		String policyFileName = "input/policy" + id + ".conf";
		episimConfig.setOverwritePolicyLocation(policyFileName);
		episimConfig.setPolicy(FixedPolicy.class, policyConf);

		return config;
	}

	public static final class Params {

		@IntParameter({-6, 6})
		int offset;

		@Parameter({0.5, 0.1})
		double remainingFractionKiga;

		@Parameter({0.5, 0.1})
		double remainingFractionPrima;

		@Parameter({0.5, 0.})
		double remainingFractionSeconHigher;

		@Parameter({0.1, 0.3})
		double remainingFractionLeisure;

		@Parameter({0.55, 0.75})
		double remainingFractionWork;

		@Parameter({0.65, 0.85})
		double remainingFractionShoppingBusinessErrands;

		@StringParameter({"CLOTH", "SURGICAL"})
		String mask;

		@Parameter({0., 0.5, 0.9, 1.})
		double maskCompliance;

	}

}

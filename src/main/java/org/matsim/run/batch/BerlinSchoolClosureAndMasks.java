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
import org.matsim.run.modules.SnzBerlinScenario;

import java.time.LocalDate;
import java.util.List;

/**
 * Batch run for Berlin using different school closure timing and mask options.
 */
public final class BerlinSchoolClosureAndMasks implements BatchRun<BerlinSchoolClosureAndMasks.Params> {

	public static final List<Option> OPTIONS = List.of(
			Option.of("Worn masks", 67)
					.measure("Mask type", "mask")
					.measure("Mask compliance", "maskCompliance"),

			Option.of("Out-of-home activities limited", "By type and percent (%)", 67)
					.measure("Work activities", "remainingFractionWork")
					.measure("Other activities", "remainingFractionShoppingBusinessErrands")
					.measure("Leisure activities", "remainingFractionLeisure"),

			Option.of("Reopening of educational facilities", "Students returning (%)", 74)
					.measure("Going to primary school", "remainingFractionPrima")
					.measure("Going to kindergarten", "remainingFractionKiga")
					.measure("Going to secondary/univ.", "remainingFractionSeconHigher")
	);

	@Override
	public LocalDate getDefaultStartDate() {
		return LocalDate.of(2020, 2, 21);
	}

	@Override
	public Config baseCase(int id) {

		SnzBerlinScenario module = new SnzBerlinScenario();

		Config config = module.config();
		config.plans().setInputFile("../be_v2_snz_entirePopulation_emptyPlans_withDistricts.xml.gz");

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../be_v2_snz_episim_events.xml.gz");

		return config;
	}

	@Override
	public List<Option> getOptions() {
		return OPTIONS;
	}

	@Override
	public Config prepareConfig(int id, BerlinSchoolClosureAndMasks.Params params) {

		Config config = baseCase(id);
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		int offset = 0;
		FaceMask wornMask = FaceMask.valueOf(params.mask);
		episimConfig.setMaskCompliance(params.maskCompliance);
		episimConfig.setStartDate(params.startDate);

		// TODO replace offset with actual dates

		com.typesafe.config.Config policyConf = SnzBerlinScenario.basePolicy()

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

		@StartDates("2020-02-21")
		LocalDate startDate;

		@Parameter({0.5, 0.1})
		double remainingFractionKiga;

		@Parameter({0.5, 0.1})
		double remainingFractionPrima;

		@Parameter({0.5, 0.})
		double remainingFractionSeconHigher;

		@Parameter({0.1, 0.3})
		double remainingFractionLeisure;

		@Parameter({0.45, 0.65})
		double remainingFractionWork;

		@Parameter({0.7, 0.9})
		double remainingFractionShoppingBusinessErrands;

		@StringParameter({"NONE", "CLOTH", "SURGICAL"})
		String mask;

		@Parameter({0., 0.5, 0.9, 1.})
		double maskCompliance;

	}

}

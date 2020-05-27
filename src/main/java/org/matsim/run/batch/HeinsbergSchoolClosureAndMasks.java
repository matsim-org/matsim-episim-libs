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
import org.matsim.run.modules.SnzHeinsbergScenario;

import java.time.LocalDate;
import java.util.List;

/**
 * See {@link BerlinSchoolClosureAndMasks}, but for Heinsberg.
 */
public final class HeinsbergSchoolClosureAndMasks implements BatchRun<HeinsbergSchoolClosureAndMasks.Params> {

	public static final List<Option> OPTIONS = List.of(
			Option.of("Worn masks", 72)
					.measure("Mask type", "mask")
					.measure("Mask compliance", "maskCompliance"),

			Option.of("Out-of-home activities limited", "By type and percent (%)", 72)
					.measure("Work activities", "remainingFractionWork")
					.measure("Other activities", "remainingFractionShoppingBusinessErrands")
					.measure("Leisure activities", "remainingFractionLeisure"),

			Option.of("Reopening of educational facilities", "Students returning (%)", 79)
					.measure("Going to primary school", "remainingFractionPrima")
					.measure("Going to kindergarten", "remainingFractionKiga")
					.measure("Going to secondary/univ.", "remainingFractionSeconHigher")
	);

	@Override
	public LocalDate getDefaultStartDate() {
		return LocalDate.of(2020, 3, 16);
	}

	@Override
	public Config baseCase(int id) {

		SnzHeinsbergScenario module = new SnzHeinsbergScenario();

		Config config = module.config();
		config.plans().setInputFile("../he_small_snz_populationWithDistrict.xml.gz");

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../he_small_snz_eventsForEpisim.xml.gz");

		return config;
	}

	@Override
	public List<Option> getOptions() {
		return OPTIONS;
	}

	@Override
	public Config prepareConfig(int id, HeinsbergSchoolClosureAndMasks.Params params) {

		Config config = baseCase(id);
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		int offset = params.offset;
		FaceMask wornMask = FaceMask.valueOf(params.mask);

		com.typesafe.config.Config policyConf = SnzHeinsbergScenario.basePolicy(offset)

				// masks are worn from day 72 onwards (27.04.2020); compliance is set via config
				.restrict(72 - offset, Restriction.of(params.remainingFractionWork, wornMask, params.maskCompliance), "work")
				.restrict(72 - offset, Restriction.of(params.remainingFractionShoppingBusinessErrands, wornMask, params.maskCompliance), "shopping", "errands", "business")
				.restrict(72 - offset, Restriction.of(params.remainingFractionLeisure, wornMask, params.maskCompliance), "leisure")
				.restrict(72 - offset, Restriction.of(1, wornMask, params.maskCompliance), "pt", "tr")
				.restrict(72 - offset, Restriction.of(0.1, wornMask, params.maskCompliance), "educ_primary", "educ_kiga")
				// edu facilities can be reopend from day 79 (04.05.2020)
				.restrict(79 - offset, Restriction.of(params.remainingFractionKiga, wornMask, params.maskCompliance), "educ_kiga")
				.restrict(79 - offset, Restriction.of(params.remainingFractionPrima, wornMask, params.maskCompliance), "educ_primary")
				.restrict(79 - offset, Restriction.of(params.remainingFractionSeconHigher, wornMask, params.maskCompliance), "educ_secondary", "educ_higher")
				.build();

		String policyFileName = "input/policy" + id + ".conf";
		episimConfig.setOverwritePolicyLocation(policyFileName);
		episimConfig.setPolicy(FixedPolicy.class, policyConf);

		return config;
	}

	public static final class Params {

		@IntParameter({-6})
		int offset;

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

		@StringParameter({"CLOTH", "SURGICAL"})
		String mask;

		@Parameter({0., 0.5, 0.9, 1.})
		double maskCompliance;

	}

}

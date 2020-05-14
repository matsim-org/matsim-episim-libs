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
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinScenario;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Batch run for Berlin executing different school closure and tracing options.
 */
public final class BerlinSchoolClosureAndTracing implements BatchRun<BerlinSchoolClosureAndTracing.Params> {

	public static final List<Option> OPTIONS = List.of(
			Option.of("Contact tracing", 67)
					.measure("Tracing Distance", "tracingDayDistance")
					.measure("Tracing Probability", "tracingProbability"),

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
	public Config prepareConfig(int id, BerlinSchoolClosureAndTracing.Params params) {

		Config config = baseCase(id);
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		episimConfig.setStartDate(params.startDate);

		// +1 because end date is exclusive
		int offset = (int) (ChronoUnit.DAYS.between(params.startDate, LocalDate.parse("2020-04-27")) + 1);

		// should be day 67
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(offset);
		tracingConfig.setTracingProbability(params.tracingProbability);
		tracingConfig.setTracingDayDistance(params.tracingDayDistance);

		com.typesafe.config.Config policyConf = SnzBerlinScenario.basePolicy()
				// Google mobility data currently stops at day 58 (18.04.2020)
//				.restrict(58 - offset, params.remainingFractionWork, "work")
//				.restrict(58 - offset, params.remainingFractionShoppingBusinessErrands, "shopping", "errands", "business")
//				.restrict(58 - offset, params.remainingFractionLeisure, "leisure")
				// masks are worn from day 67 onwards (27.04.2020); compliance is set via config
				.restrict("2020-04-27", params.remainingFractionWork, "work")
				.restrict("2020-04-27", params.remainingFractionShoppingBusinessErrands, "shopping", "errands", "business")
				.restrict("2020-04-27", params.remainingFractionLeisure, "leisure")
				// edu facilities can be reopend from day 74 (04.05.2020)
				.restrict("2020-04-05", params.remainingFractionKiga, "educ_kiga")
				.restrict("2020-04-05", params.remainingFractionPrima, "educ_primary")
				.restrict("2020-04-05", params.remainingFractionSeconHigher, "educ_secondary", "educ_higher")
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

		@IntParameter({1, 3, 5})
		int tracingDayDistance;

		@Parameter({1.0, 0.75, 0.5})
		double tracingProbability;

	}

}


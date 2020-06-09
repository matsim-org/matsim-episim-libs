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
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.Transition;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Batch run for {@link org.matsim.run.modules.SnzBerlinScenario25pct2020} and different tracing options.
 */
public final class Berlin2020Tracing implements BatchRun<Berlin2020Tracing.Params> {

	public static final List<Option> OPTIONS = List.of(
			Option.of("Contact tracing")
					.measure("Tracing period", "tracingPeriod")
					.measure("Tracing delay", "tracingDelay")
					.measure("Tracing capacity per day", "tracingCapacity")
					.measure("Tracing probability", "tracingProbability"),

			Option.of("Activity Participation Trend")
					.measure("Extrapolation type", "extrapolation"),

			Option.of("Reopening of educational facilities", "Operational mode", 119)
					.measure("Before summer holidays", "eduBeforeHolidays")
					.measure("After summer holidays", "eduAfterHolidays"),

			Option.of("Options")
					.measure("Max. interactions", "maxInteractions")
					.measure("Seed", "seed")
	);

	@Override
	public LocalDate getDefaultStartDate() {
		return LocalDate.of(2020, 2, 10);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "tracing2");
	}

	@Override
	public Config baseCase(int id) {

		SnzBerlinScenario25pct2020 module = new SnzBerlinScenario25pct2020();
		Config config = module.config();

		config.plans().setInputFile(BatchRun.resolveForCluster(SnzBerlinScenario25pct2020.INPUT,
				"be_2020_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz"));

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile(BatchRun.resolveForCluster(SnzBerlinScenario25pct2020.INPUT,
				"be_2020_snz_episim_events_25pt.xml.gz"));

		episimConfig.setProgressionConfig(
				SnzBerlinScenario25pct2020.baseProgressionConfig(Transition.config("input/progression" + id + ".conf")).build()
		);

		return config;
	}

	@Override
	public List<Option> getOptions() {
		return OPTIONS;
	}

	@Override
	public Config prepareConfig(int id, Berlin2020Tracing.Params params) {

		Config config = baseCase(id);

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		episimConfig.setMaxInteractions(params.maxInteractions);
		if (params.maxInteractions == 10) {
			episimConfig.setCalibrationParameter(0.000_001);
		}

		tracingConfig.setTracingProbability(params.tracingProbability);
		tracingConfig.setTracingMemory_days(params.tracingPeriod);
		tracingConfig.setTracingCapacity_pers_per_day(params.tracingCapacity);
		tracingConfig.setTracingDelay_days(params.tracingDelay);


		double alpha = 1.4;
		double ciCorrection = 0.3;
		Path csv = SnzBerlinScenario25pct2020.INPUT.resolve("BerlinSnzData_daily_until20200531.csv");
		String dateOfCiChange = "2020-03-08";

		FixedPolicy.ConfigBuilder policyConf;
		try {
			policyConf = SnzBerlinScenario25pct2020.basePolicy(episimConfig, csv.toFile(), alpha, ciCorrection, dateOfCiChange,
					EpisimUtils.Extrapolation.valueOf(params.extrapolation));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		double remainingPrimaKiga;
		double remainingOther;
		if (params.eduBeforeHolidays.equals("limitedOperation")) {
			remainingPrimaKiga = 0.3;
			remainingOther = 0.2;
		} else if (params.eduBeforeHolidays.equals("fullOpening")) {
			remainingPrimaKiga = 1;
			remainingOther = 1;
		} else throw new IllegalArgumentException("Unknown operation type");

		double remainingPrimaKigaAfterHoliday;
		double remainingOtherAfterHoliday;
		if (params.eduAfterHolidays.equals("limitedOperation")) {
			remainingPrimaKigaAfterHoliday = 0.3;
			remainingOtherAfterHoliday = 0.2;
		} else if (params.eduAfterHolidays.equals("fullOpening")) {
			remainingPrimaKigaAfterHoliday = 1;
			remainingOtherAfterHoliday = 1;
		} else throw new IllegalStateException("Unknown operation type");

		policyConf
				.restrict("2020-06-08", remainingPrimaKiga, "educ_primary", "educ_kiga")
				.restrict("2020-06-08", remainingOther, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")

				// Sommerferien
				.restrict("2020-06-25", 0.3, "educ_primary", "educ_kiga")
				.restrict("2020-06-25", 0.2, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")

				// Ende der Sommerferien
				.restrict("2020-08-10", remainingPrimaKigaAfterHoliday, "educ_primary", "educ_kiga")
				.restrict("2020-08-10", remainingOtherAfterHoliday, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")

				// Herbstferien
				.restrict("2020-10-10", 0.3, "educ_primary", "educ_kiga")
				.restrict("2020-10-10", 0.2, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")

				// Ende der Herbstferien
				.restrict("2020-10-26", remainingPrimaKigaAfterHoliday, "educ_primary", "educ_kiga")
				.restrict("2020-10-26", remainingOtherAfterHoliday, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")

				// Weihnachtsferien
				.restrict("2020-12-19", 0.3, "educ_primary", "educ_kiga")
				.restrict("2020-12-19", 0.2, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")

				// Ende der Weihnachtsferien
				.restrict("2021-01-04", remainingPrimaKigaAfterHoliday, "educ_primary", "educ_kiga")
				.restrict("2021-01-04", remainingOtherAfterHoliday, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")

				/*
				// Winterferien
				.restrict("2021-01-30", 0.3, "educ_primary")
				.restrict("2021-01-30", 0.2, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
				.restrict("2021-01-30", 0.3, "educ_kiga")

				// Ende der Winterferien 2021
				.restrict("2021-02-08", params.remainingFractionPrima, "educ_primary")
				.restrict("2021-02-08", params.remainingFractionKiga, "educ_kiga")
				.restrict("2021-02-08", params.remainingFractionSecondary, "educ_secondary")
				.restrict("2021-02-08", params.remainingFractionHigherOther, "educ_higher", "educ_tertiary", "educ_other")

				// Osterferien 2021
				.restrict("2021-03-27", 0.3, "educ_primary")
				.restrict("2021-03-27", 0.2, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
				.restrict("2021-03-27", 0.3, "educ_kiga")

				// Ende der Osterferien 2021
				.restrict("2021-04-12", params.remainingFractionPrima, "educ_primary")
				.restrict("2021-04-12", params.remainingFractionKiga, "educ_kiga")
				.restrict("2021-04-12", params.remainingFractionSecondary, "educ_secondary")
				.restrict("2021-04-12", params.remainingFractionHigherOther, "educ_higher", "educ_tertiary", "educ_other")

				// Sommerferien 2021
				.restrict("2021-06-25", 0.3, "educ_primary")
				.restrict("2021-06-25", 0.2, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
				.restrict("2021-06-25", 0.3, "educ_kiga")

				// Ende der Sommerferien 2021
				.restrict("2021-08-09", params.remainingFractionPrima, "educ_primary")
				.restrict("2021-08-09", params.remainingFractionKiga, "educ_kiga")
				.restrict("2021-08-09", params.remainingFractionSecondary, "educ_secondary")
				.restrict("2021-08-09", params.remainingFractionHigherOther, "educ_higher", "educ_tertiary", "educ_other")
*/
		;

		String policyFileName = "input/policy" + id + ".conf";
		episimConfig.setOverwritePolicyLocation(policyFileName);
		episimConfig.setPolicy(FixedPolicy.class, policyConf.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(3)
		long seed;

		@StringParameter({"limitedOperation", "fullOpening"})
		String eduBeforeHolidays;

		@StringParameter({"limitedOperation", "fullOpening"})
		String eduAfterHolidays;

		@IntParameter({14})
		int tracingPeriod;

		@IntParameter({2})
		int tracingDelay;

		@IntParameter({30, 50, Integer.MAX_VALUE})
		int tracingCapacity;

		@Parameter({0.75})
		double tracingProbability;

		@StringParameter({"linear", "exponential"})
		String extrapolation;

		@IntParameter({3, 10})
		int maxInteractions;

	}

}


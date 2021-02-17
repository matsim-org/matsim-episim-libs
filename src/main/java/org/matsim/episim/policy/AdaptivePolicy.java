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
package org.matsim.episim.policy;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import it.unimi.dsi.fastutil.objects.*;
import org.matsim.episim.EpisimReporting;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This policy enforces restrictions based on the number of available intensive care beds
 * and the number of persons that are in critical health state.
 */
public class AdaptivePolicy extends ShutdownPolicy {

	/**
	 * Amount of days incidence has to stay below the trigger to lift restrictions.
	 */
	private static final int INTERVAL_DAY = 14;

	/**
	 * Incidences triggers for configured activities.
	 */
	private final Config incidenceTriggers;

	/**
	 * Policy applied at the start.
	 */
	private final Config initialPolicy;

	/**
	 * Policy when shutdown is in effect.
	 */
	private final Config restrictedPolicy;

	/**
	 * Policy when everything is open.
	 */
	private final Config openPolicy;

	/**
	 * Store incidence for each day.
	 */
	private final Object2DoubleSortedMap<LocalDate> cumCases = new Object2DoubleAVLTreeMap<>();

	/**
	 * Whether currently in lockdown.
	 */
	private Object2BooleanMap<String> inLockdown = new Object2BooleanOpenHashMap<>();

	/**
	 * Constructor from config.
	 */
	public AdaptivePolicy(Config config) {
		super(config);
		incidenceTriggers = config.getConfig("incidences");
		restrictedPolicy = config.getConfig("restricted-policy");
		openPolicy = config.getConfig("open-policy");
		initialPolicy = config.hasPath("init-policy") ? config.getConfig("init-policy") : null;
	}

	/**
	 * Create a config builder for {@link AdaptivePolicy}.
	 */
	public static ConfigBuilder config() {
		return new ConfigBuilder();
	}

	@Override
	public void init(LocalDate start, ImmutableMap<String, Restriction> restrictions) {
		if (initialPolicy != null && !initialPolicy.isEmpty()) {

			for (Map.Entry<String, Restriction> e : restrictions.entrySet()) {
				updateRestrictions(start, initialPolicy, e.getKey(), e.getValue());
			}
		}
	}

	@Override
	public void updateRestrictions(EpisimReporting.InfectionReport report, ImmutableMap<String, Restriction> restrictions) {

		LocalDate date = LocalDate.parse(report.date);

		calculateCases(report);
		Object2DoubleSortedMap<LocalDate> cases = cumCases.tailMap(date.minus(INTERVAL_DAY + 6, ChronoUnit.DAYS));

		Object2DoubleSortedMap<LocalDate> incidence = new Object2DoubleAVLTreeMap<>();

		for (Object2DoubleMap.Entry<LocalDate> from : cases.object2DoubleEntrySet()) {
			LocalDate until = from.getKey().plusDays(7);
			if (cases.containsKey(until)) {
				incidence.put(until, cases.getDouble(until) - cases.getDouble(from.getKey()));
			} else
				// if until was not contained, the next ones will not be either
				break;
		}

		// for first 7 days, restrictions will stay the same
		if (incidence.isEmpty())
			return;

		for (Map.Entry<String, ConfigValue> e : incidenceTriggers.entrySet()) {

			String act = e.getKey();
			List<Double> trigger = (List<Double>) e.getValue().unwrapped();

			if (inLockdown.getBoolean(act)) {
				if (incidence.values().stream().allMatch(inc -> inc <= trigger.get(0))) {
					updateRestrictions(date, openPolicy, act, restrictions.get(act));
					inLockdown.put(act, false);
				}

			} else {
				if (incidence.getDouble(incidence.lastKey()) >= trigger.get(1)) {
					updateRestrictions(date, restrictedPolicy, act, restrictions.get(act));
					inLockdown.put(act, true);
				}
			}
		}
	}

	/**
	 * Calculate incidence depending
	 */
	private void calculateCases(EpisimReporting.InfectionReport report) {
		double cases = report.nShowingSymptomsCumulative * (100_000d / report.nTotal());
		this.cumCases.put(LocalDate.parse(report.date), cases);
	}

	private void updateRestrictions(LocalDate start, Config policy, String act, Restriction restriction) {

		// activity name
		if (!policy.hasPath(act))
			return;

		Config actConfig = policy.getConfig(act);

		for (Map.Entry<String, ConfigValue> days : actConfig.root().entrySet()) {
			if (days.getKey().startsWith("day")) continue;

			LocalDate date = LocalDate.parse(days.getKey());
			if (date.isBefore(start)) {
				Restriction r = Restriction.fromConfig(actConfig.getConfig(days.getKey()));
				restriction.update(r);
			}
		}
	}

	/**
	 * Config builder for {@link AdaptivePolicy}.
	 */
	@SuppressWarnings("unchecked")
	public static final class ConfigBuilder extends ShutdownPolicy.ConfigBuilder<Object> {

		/**
		 * Use {@link #AdaptivePolicy#config()}.
		 */
		private ConfigBuilder() {
			params.put("start-in-lockdown", false);
		}

		/**
		 * Define trigger for weekly incidence and individual activities.
		 */
		public ConfigBuilder incidenceTrigger(double openAt, double restrictAt, String... activities) {

			if (restrictAt < openAt)
				throw new IllegalArgumentException("Restrict threshold must be larger than open threshold");

			if (activities.length == 0)
				throw new IllegalArgumentException("Activities can not be empty");


			Map<String, List<Double>> incidences = (Map<String, List<Double>>) params.computeIfAbsent("incidences", (k) -> new HashMap<>());
			for (String act : activities) {
				incidences.put(act, List.of(openAt, restrictAt));
			}

			return this;
		}

		/**
		 * Set the initial policy that is always applied.
		 */
		public ConfigBuilder initialPolicy(FixedPolicy.ConfigBuilder policy) {
			params.put("init-policy", policy.params);
			return this;
		}

		/**
		 * See {@link AdaptivePolicy#openPolicy}.
		 */
		public ConfigBuilder openPolicy(FixedPolicy.ConfigBuilder policy) {
			params.put("open-policy", policy.params);
			return this;
		}

		/**
		 * See {@link AdaptivePolicy#restrictedPolicy}.
		 */
		public ConfigBuilder restrictedPolicy(FixedPolicy.ConfigBuilder policy) {
			params.put("restricted-policy", policy.params);
			return this;
		}
	}
}

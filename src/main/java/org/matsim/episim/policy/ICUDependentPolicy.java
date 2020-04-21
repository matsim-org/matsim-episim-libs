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
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import org.matsim.episim.EpisimReporting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This policy enforces restrictions based on the number of available intensive care beds
 * and the number of persons that are in critical health state.
 */
public class ICUDependentPolicy extends ShutdownPolicy {

	/**
	 * Number of beds available for persons in critical state.
	 */
	private final int beds;

	/**
	 * Percentage of occupied beds when policy will trigger.
	 */
	private final double shutdownTrigger;

	/**
	 * Trigger to open all activities again as proportion of shutdown trigger.
	 */
	private final double openAllTrigger;

	/**
	 * Interval of days on which restrictions are performed independent of critical persons.
	 */
	private final List<Integer> shutdownDays;

	/**
	 * Config with restrictions for activities.
	 */
	private final Config rConfig;

	/**
	 * Reopen trigger as proportion to shutdown for individual activities.
	 */
	private final Map<String, Double> reopenTrigger = new HashMap<>();

	public ICUDependentPolicy(Config config) {
		super(config.withFallback(ConfigFactory.load("icu")));
		this.beds = this.config.getInt("beds");
		this.shutdownTrigger = this.config.getDouble("shutdown-trigger");
		this.shutdownDays = this.config.getIntList("shutdown-days");
		this.openAllTrigger = this.config.getDouble("open-all");
		this.rConfig = this.config.getConfig("restrictions");
		Config triggers = this.config.getConfig("open-activities");
		triggers.entrySet().forEach(e ->
				reopenTrigger.put(e.getKey(), triggers.getDouble(e.getKey()))
		);
	}

	public static ConfigBuilder config() {
		return new ConfigBuilder();
	}

	@Override
	public void updateRestrictions(EpisimReporting.InfectionReport report, ImmutableMap<String, Restriction> restrictions) {

		if (report.nCritical >= beds * shutdownTrigger)
			enforce(restrictions);
		else if (report.nCritical <= beds * shutdownTrigger * openAllTrigger)
			restrictions.values().forEach(Restriction::open);

		// re open some activities individually
		reopenTrigger.forEach((k, v) -> {
			if (report.nCritical <= beds * shutdownTrigger * v)
				restrictions.get(k).open();

		});

		// Additionally enforce shutdown on the given interval
		if (shutdownDays.get(0) <= report.day && report.day < shutdownDays.get(1))
			enforce(restrictions);


		// freight is always off
		restrictions.get("freight").fullShutdown();
	}

	/**
	 * Applies the restriction config.
	 */
	private void enforce(ImmutableMap<String, Restriction> r) {
		for (Map.Entry<String, ConfigValue> e : rConfig.entrySet()) {
			r.get(e.getKey()).setRemainingFraction(rConfig.getDouble(e.getKey()));
		}
	}

	@SuppressWarnings("unchecked")
	public static final class ConfigBuilder extends ShutdownPolicy.ConfigBuilder {

		/**
		 * @see ICUDependentPolicy#beds
		 */
		public ConfigBuilder beds(int n) {
			params.put("beds", n);
			return this;
		}

		/**
		 * @see ICUDependentPolicy#shutdownTrigger
		 */
		public ConfigBuilder shutdownAt(double fraction) {
			params.put("shutdown-trigger", fraction);
			return this;
		}

		/**
		 * @see ICUDependentPolicy#openAllTrigger
		 */
		public ConfigBuilder openAt(double proportion) {
			params.put("open-all", proportion);
			return this;
		}

		/**
		 * @see ICUDependentPolicy#shutdownDays
		 */
		public ConfigBuilder shutDownDays(int from, int to) {
			params.put("shutdown-days", Lists.newArrayList(from, to));
			return this;
		}

		/**
		 * @see ICUDependentPolicy#reopenTrigger
		 */
		public ConfigBuilder openAt(double proportion, String... activities) {
			Map<String, Double> map = (Map<String, Double>) params.computeIfAbsent("open-activities", k -> new HashMap<>());
			for (String act : activities) {
				map.put(act, proportion);
			}
			return this;
		}

		/**
		 * @see ICUDependentPolicy#rConfig
		 */
		public ConfigBuilder restrict(double fraction, String... activities) {
			Map<String, Double> map = (Map<String, Double>) params.computeIfAbsent("restrictions", k -> new HashMap<>());
			for (String act : activities) {
				map.put(act, fraction);
			}
			return this;
		}
	}
}

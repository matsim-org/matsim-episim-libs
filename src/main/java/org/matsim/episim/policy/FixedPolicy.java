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
import org.matsim.episim.EpisimReporting;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

/**
 * Set the restrictions based on fixed rules with day and {@link Restriction#getRemainingFraction()}.
 */
public class FixedPolicy extends ShutdownPolicy {

	/**
	 * Constructor.
	 */
	public FixedPolicy(Config config) {
		super(config);
	}

	/**
	 * Config builder for fixed policy.
	 */
	public static ConfigBuilder config() {
		return new ConfigBuilder();
	}

	@Override
	public void updateRestrictions(EpisimReporting.InfectionReport report, ImmutableMap<String, Restriction> restrictions) {
		for (Map.Entry<String, Restriction> entry : restrictions.entrySet()) {
			// activity name
			if (!config.hasPath(entry.getKey())) continue;

			Config actConfig = this.config.getConfig(entry.getKey());
			String dayKey = "day-" + report.day;
			String dateKey = report.date;

			// check for day or date config
			Config dayConfig = null;
			if (actConfig.hasPath(dayKey))
				dayConfig = actConfig.getConfig(dayKey);
			else if (actConfig.hasPath(dateKey))
				dayConfig = actConfig.getConfig(dateKey);

			if (dayConfig != null) {

				Restriction r = Restriction.fromConfig(dayConfig);

				entry.getValue().setRemainingFraction(r.getRemainingFraction());
				entry.getValue().setExposure(r.getExposure());
				entry.getValue().setRequireMask(r.getRequireMask());
			}
		}
	}

	/**
	 * Builder for {@link FixedPolicy} config.
	 */
	public static final class ConfigBuilder extends ShutdownPolicy.ConfigBuilder {


		@SuppressWarnings("unchecked")
		private ConfigBuilder restrict(String key, Restriction restriction, String... activities) {

			for (String act : activities) {
				Map<String, Map<String, Object>> p = (Map<String, Map<String, Object>>) params.computeIfAbsent(act, m -> new HashMap<>());
				p.put(key, restriction.asMap());
			}

			return this;
		}

		/**
		 * Restrict activities at specific day relative to simulation start.
		 *
		 * @param day         the day/iteration when it will be in effect
		 * @param restriction restriction to apply
		 * @param activities  activities to restrict
		 */
		public ConfigBuilder restrict(long day, Restriction restriction, String... activities) {
			return restrict("day-" + day, restriction, activities);
		}

		/**
		 * Restrict activities at specific date time.
		 *
		 * @see #restrict(long, Restriction, String...)
		 */
		public ConfigBuilder restrict(LocalDate date, Restriction restriction, String... activities) {
			return restrict(date.toString(), restriction, activities);
		}

		/**
		 * Same as {@link #restrict(LocalDate, Restriction, String...)} with default values.
		 */
		public ConfigBuilder restrict(LocalDate date, double fraction, String... activities) {
			return restrict(date.toString(), Restriction.of(fraction), activities);
		}

		/**
		 * Same as {@link #restrict(long, Restriction, String...)}  with default values.
		 */
		public ConfigBuilder restrict(long day, double fraction, String... activities) {
			return restrict(day, Restriction.of(fraction), activities);
		}

		/**
		 * Shutdown activities completely after certain day.
		 */
		public ConfigBuilder shutdown(long day, String... activities) {
			return this.restrict(day, Restriction.of(0), activities);
		}

		/**
		 * Open activities freely after certain day.
		 */
		public ConfigBuilder open(long day, String... activities) {
			return this.restrict(day, Restriction.none(), activities);
		}


		/**
		 * Create a config entry with linear interpolated {@link Restriction#getRemainingFraction()}. All start and end values are inclusive.
		 *
		 * @param start       starting date
		 * @param end         end tate
		 * @param restriction starting restriction and fraction
		 * @param fractionEnd remaining fraction at end date
		 * @param activities  activities to restrict
		 */
		public ConfigBuilder interpolate(LocalDate start, LocalDate end, Restriction restriction, double fractionEnd, String... activities) {
			double day = 0;
			int diff = Period.between(start, end).getDays();

			LocalDate today = start;
			while (today.isBefore(end) || today.isEqual(end)) {
				double r = restriction.getRemainingFraction() + (fractionEnd - restriction.getRemainingFraction()) * (day / diff);

				restrict(today, Restriction.of(r, restriction.getExposure(), restriction.getRequireMask()), activities);
				today = today.plusDays(1);
				day++;
			}

			return this;
		}

	}

}

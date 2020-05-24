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
import org.matsim.episim.EpisimReporting;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
	public void init(LocalDate start, ImmutableMap<String, Restriction> restrictions) {

		// Init restrictions that are before simulation start
		for (Map.Entry<String, Restriction> entry : restrictions.entrySet()) {

			// activity name
			if (!config.hasPath(entry.getKey())) continue;

			Config actConfig = this.config.getConfig(entry.getKey());

			for (Map.Entry<String, ConfigValue> days : actConfig.root().entrySet()) {

				if (days.getKey().startsWith("day")) continue;

				LocalDate date = LocalDate.parse(days.getKey());
				if (date.isBefore(start)) {
					Restriction r = Restriction.fromConfig(actConfig.getConfig(days.getKey()));

					entry.getValue().setRemainingFraction(r.getRemainingFraction());
					entry.getValue().setExposure(r.getExposure());
					entry.getValue().setRequireMask(r.getRequireMask());
				}
			}
		}
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

		/**
		 * Restrict activities at specific date in absolute time.
		 *
		 * @param date        the date (yyyy-mm-dd) when it will be in effect
		 * @param restriction restriction to apply
		 * @param activities  activities to restrict
		 */
		@SuppressWarnings("unchecked")
		public ConfigBuilder restrict(String date, Restriction restriction, String... activities) {

			for (String act : activities) {
				Map<String, Map<String, Object>> p = (Map<String, Map<String, Object>>) params.computeIfAbsent(act, m -> new HashMap<>());
				p.put(date, restriction.asMap());
			}

			return this;
		}

		/**
		 * Same as {@link #restrict(String, Restriction, String...)} with default values.
		 */
		public ConfigBuilder restrict(LocalDate date, double fraction, String... activities) {
			return restrict(date.toString(), Restriction.of(fraction), activities);
		}

		public ConfigBuilder restrict(LocalDate date, Restriction restriction, String... activities) {
			return restrict(date.toString(), restriction, activities);
		}

		/**
		 * Same as {@link #restrict(String, Restriction, String...)} with default values.
		 */
		public ConfigBuilder restrict(String date, double fraction, String... activities) {
			// check if date is valid
			return restrict(LocalDate.parse(date), fraction, activities);
		}

		/**
		 * See {@link #restrict(String, Restriction, String...)}.
		 */
		public ConfigBuilder restrict(long day, Restriction restriction, String... activities) {
			if (day <= 0) throw new IllegalArgumentException("Day must be larger than 0");

			return restrict("day-" + day, restriction, activities);
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
		 * @param restriction starting restriction at start date
		 * @param fractionEnd remaining fraction at end date
		 * @param activities  activities to restrict
		 */
		public ConfigBuilder interpolate(LocalDate start, LocalDate end, Restriction restriction, double fractionEnd, String... activities) {
			double day = 0;

			long diff = ChronoUnit.DAYS.between(start, end);

			LocalDate today = start;
			while (today.isBefore(end) || today.isEqual(end)) {
				double r = restriction.getRemainingFraction() + (fractionEnd - restriction.getRemainingFraction()) * (day / diff);

				restrict(today.toString(), Restriction.of(r, restriction.getExposure(), restriction.getRequireMask()), activities);
				today = today.plusDays(1);
				day++;
			}

			return this;
		}

		/**
		 * See {@link #interpolate(LocalDate, LocalDate, Restriction, double, String...)}.
		 */
		public ConfigBuilder interpolate(String start, String end, Restriction restriction, double fractionEnd, String... activities) {
			return interpolate(LocalDate.parse(start), LocalDate.parse(end), restriction, fractionEnd, activities);
		}

	}

}

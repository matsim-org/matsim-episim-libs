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

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.episim.EpisimReporting;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Set the restrictions based on fixed rules with day and {@link Restriction#getRemainingFraction()}.
 */
public final class FixedPolicy extends ShutdownPolicy {

	private static final Logger log = LogManager.getLogger(FixedPolicy.class);

	private final double hospitalScale;

	/**
	 * Constructor.
	 */
	@Inject
	public FixedPolicy(@Named("policy") Config config) {
		super(config);

		if (config.hasPath("hospital")) {
			Config c = config.getConfig("hospital");
			if (c.hasPath("scale"))
				hospitalScale = c.getDouble("scale");
			else
				hospitalScale = 1d;
		} else
			hospitalScale = 1d;
	}

	/**
	 * Config builder for fixed policy.
	 */
	public static ConfigBuilder config() {
		return new ConfigBuilder();
	}

	/**
	 * Create a config builder with an existing config.
	 */
	public static ConfigBuilder parse(Config config) {
		return new ConfigBuilder(config);
	}

	@Override
	public void init(LocalDate start, ImmutableMap<String, Restriction> restrictions) {
		initRestrictions(start, restrictions, config);
	}

	/**
	 * Init restrictions that are before simulation start
	 */
	static void initRestrictions(LocalDate start, ImmutableMap<String, Restriction> restrictions, Config config) {
		for (Map.Entry<String, Restriction> entry : restrictions.entrySet()) {

			// activity name
			if (!config.hasPath(entry.getKey())) continue;

			Config actConfig = config.getConfig(entry.getKey());

			for (Map.Entry<String, ConfigValue> days : actConfig.root().entrySet()) {

				if (days.getKey().startsWith("day")) continue;

				LocalDate date = LocalDate.parse(days.getKey());
				if (date.isBefore(start)) {
					Restriction r = Restriction.fromConfig(actConfig.getConfig(days.getKey()));
					entry.getValue().update(r);

					if (ShutdownPolicy.REG_HOSPITAL.equals(r.getRemainingFraction()))
						entry.getValue().setExtrapolate(true);

				}
			}
		}
	}

	@Override
	public void updateRestrictions(EpisimReporting.InfectionReport report, ImmutableMap<String, Restriction> restrictions) {
		for (Map.Entry<String, Restriction> entry : restrictions.entrySet()) {
			// activity name
			if (!config.hasPath(entry.getKey())) continue;

			Restriction r = readForDay(report, config, entry.getKey());
			if (r != null) {

				if (ShutdownPolicy.REG_HOSPITAL.equals(r.getRemainingFraction()))
					entry.getValue().setExtrapolate(true);

				entry.getValue().update(r);
			}

			if (entry.getValue().isExtrapolate()) {

				double hospital = (report.nCritical + report.nSeriouslySick) * (100_000d / report.nTotal());
				double rf = 1 - (1 - Math.exp(-hospital / (3838 * (100_000d / report.nTotal()))));

				if (rf < 0)
					log.warn("Remaining fraction smaller 0: {} (critical/sick: {}/{}, total: {})", rf, report.nCritical, report.nSeriouslySick, report.nTotal());

				entry.getValue().setRemainingFraction(rf / hospitalScale);
			}

		}
	}

	/**
	 * Read restriction from map.
	 */
	@Nullable
	static Restriction readForDay(EpisimReporting.InfectionReport report, Config config, String act) {

		if (!config.hasPath(act))
			return null;

		Config actConfig = config.getConfig(act);
		String dayKey = "day-" + report.day;
		String dateKey = report.date;

		// check for day or date config
		Config dayConfig = null;
		if (actConfig.hasPath(dayKey))
			dayConfig = actConfig.getConfig(dayKey);
		else if (actConfig.hasPath(dateKey))
			dayConfig = actConfig.getConfig(dateKey);

		if (dayConfig != null) {
			return Restriction.fromConfig(dayConfig);
		}
		return null;
	}

	/**
	 * Builder for {@link FixedPolicy} config.
	 */
	public static final class ConfigBuilder extends ShutdownPolicy.ConfigBuilder<Map<String, ?>> {

		private ConfigBuilder() {
		}

		private ConfigBuilder(Config config) {
			for (Map.Entry<String, ConfigValue> e : config.root().entrySet()) {
				Object value = config.getValue(e.getKey()).unwrapped();
				params.put(e.getKey(), (Map<String, ?>) value);
			}
		}

		/**
		 * Removes all specified restrictions after or equal to {@code date}. Restriction before are sill valid and will be continued if not
		 * overwritten explicitly!.
		 */
		public ConfigBuilder clearAfter(String date) {
			params.keySet().forEach(k -> this.clearAfter(date, k));
			return this;
		}

		/**
		 * See {@link #clearAfter(String)}, but for specific activities.
		 */
		public ConfigBuilder clearAfter(String date, String... activities) {

			LocalDate ref = LocalDate.parse(date);

			for (String activity : activities) {
				Map<String, Object> map = (Map<String, Object>) params.get(activity);

				if (map == null) {
					log.warn("Activity {} not set", activity);
					continue;
				}

				Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String, Object> e = it.next();
					LocalDate other = LocalDate.parse(e.getKey());
					if (other.isEqual(ref) || other.isAfter(ref))
						it.remove();

				}
			}

			return this;
		}

		/**
		 * Restrict activities at specific date in absolute time.
		 *
		 * @param date        the date (yyyy-mm-dd) when it will be in effect
		 * @param restriction restriction to apply
		 * @param activities  activities to restrict
		 * @deprecated -- discouraged syntax; rather use {@link #restrict(LocalDate, Restriction, String...)}
		 */
		@SuppressWarnings("unchecked")
		public ConfigBuilder restrict(String date, Restriction restriction, String... activities) {

			if (activities.length == 0)
				throw new IllegalArgumentException("No activities given");

			for (String act : activities) {
				Map<String, Map<String, Object>> p = (Map<String, Map<String, Object>>) params.computeIfAbsent(act, m -> new HashMap<>());

				// Because of merging, each activity needs a separate restriction
				Restriction clone = Restriction.clone(restriction);

				// merge if there is an entry already
				if (p.containsKey(date))
					clone.merge(p.get(date));

				p.put(date, clone.asMap());
			}

			return this;
		}

		/**
		 * Restrict activities at specific date in absolute time.
		 *
		 * @param date        the date (yyyy-mm-dd) when it will be in effect
		 * @param restriction restriction to apply
		 * @param activities  activities to restrict
		 * @deprecated -- discouraged syntax; rather use {@link #restrict(LocalDate, Restriction, String...)}
		 */
		@SuppressWarnings("unchecked")
		public ConfigBuilder restrictWithDistrict(String date, Restriction restriction, String... activities) {

			if (activities.length == 0)
				throw new IllegalArgumentException("No activities given");

			Map<String, Double> locationBasedRf = restriction.getLocationBasedRf();

			for (String act : activities) {
				Map<String, Map<String, Object>> p = (Map<String, Map<String, Object>>) params.computeIfAbsent(act, m -> new HashMap<>());

				// Because of merging, each activity needs a separate restriction
				Restriction clone = Restriction.clone(restriction);
				clone.setLocationBasedRf(locationBasedRf);

				// merge if there is an entry already
				if (p.containsKey(date))
					clone.merge(p.get(date));

				Map<String, Object> value = clone.asMap();
				p.put(date, value);
			}

			return this;
		}


		/**
		 * Same as {@link #restrict(String, Restriction, String...)} with default values.
		 */
		public ConfigBuilder restrict(LocalDate date, double fraction, String... activities) {
			return restrict(date.toString(), Restriction.of(fraction), activities);
		}

		public ConfigBuilder restrictWithDistrict(LocalDate date, Map<String, Double> districtSpecificValue, double fraction, String... activities) {
			Restriction restriction = Restriction.of(fraction);
			restriction.setLocationBasedRf(districtSpecificValue);

			return restrictWithDistrict(date.toString(), restriction, activities);
		}

		/**
		 * See {@link #restrict(String, Restriction, String...)}.
		 */
		public ConfigBuilder restrict(LocalDate date, Restriction restriction, String... activities) {
			return restrict(date.toString(), restriction, activities);
		}

		/**
		 * Same as {@link #restrict(String, Restriction, String...)} with default values.
		 *
		 * @deprecated -- discouraged syntax; rather use {@link #restrict(LocalDate, double, String...)}
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

		public ConfigBuilder restrictWithDistrict(long day, Map<String, Double> locationBasedRf, double fraction, String... activities) {
			Restriction restriction = Restriction.of(fraction);
			restriction.setLocationBasedRf(locationBasedRf);

			return restrictWithDistrict("day-" + day, restriction, activities);
		}

		/**
		 * Same as {@link #restrict(long, Restriction, String...)}  with default values.
		 *
		 * @deprecated -- discouraged syntax; rather use {@link #restrict(LocalDate, double, String...)}
		 */
		public ConfigBuilder restrict(long day, double fraction, String... activities) {
			return restrict(day, Restriction.of(fraction), activities);
		}

		/**
		 * Shutdown activities completely after certain day.
		 *
		 * @deprecated -- discouraged syntax; rather use {@link #restrict(LocalDate, double, String...)}
		 */
		public ConfigBuilder shutdown(long day, String... activities) {
			return this.restrict(day, Restriction.of(0), activities);
		}

		/**
		 * Open activities freely after certain day.
		 *
		 * @deprecated -- discouraged syntax; rather use {@link #restrict(LocalDate, double, String...)}
		 */
		public ConfigBuilder open(long day, String... activities) {
			return this.restrict(day, Restriction.none(), activities);
		}


		/**
		 * Create a config entry with linear interpolated {@link Restriction#getRemainingFraction()} and {@link Restriction#getCiCorrection()} ()}.
		 * If any of these is not defined the interpolation will also be undefined.
		 * Required mask is always the same as in first parameter {@code restriction}.
		 * All start and end values are inclusive.
		 *
		 * @param start          starting date
		 * @param end            end date
		 * @param restriction    starting restriction at start date
		 * @param restrictionEnd remaining fraction / ci corr at end date
		 * @param activities     activities to restrict
		 */
		public ConfigBuilder interpolate(LocalDate start, LocalDate end, Restriction restriction, Restriction restrictionEnd, String... activities) {
			double day = 0;

			long diff = ChronoUnit.DAYS.between(start, end);

			double rf = Objects.requireNonNullElse(restriction.getRemainingFraction(), Double.NaN);
			double rfEnd = Objects.requireNonNullElse(restrictionEnd.getRemainingFraction(), Double.NaN);

			double exp = Objects.requireNonNullElse(restriction.getCiCorrection(), Double.NaN);
			double expEnd = Objects.requireNonNullElse(restrictionEnd.getCiCorrection(), Double.NaN);

			LocalDate today = start;
			while (today.isBefore(end) || today.isEqual(end)) {
				double r = rf + (rfEnd - rf) * (day / diff);
				double e = exp + (expEnd - exp) * (day / diff);

				if (Double.isNaN(r) && Double.isNaN(e))
					throw new IllegalArgumentException("The interpolation is invalid. RemainingFraction and contact intensity correction are undefined.");

				restrict(today.toString(), new Restriction(Double.isNaN(r) ? null : r, Double.isNaN(e) ? null : e,
						null, null, null, null, null, new HashMap<String, Double>(), null, restriction), activities);
				today = today.plusDays(1);
				day++;
			}

			return this;
		}

		/**
		 * Interpolation for {@link Restriction#getRemainingFraction()} only.
		 * See {@link #interpolate(LocalDate, LocalDate, Restriction, Restriction, String...)}.
		 */
		public ConfigBuilder interpolate(String start, String end, Restriction restriction, Restriction restrictionEnd, String... activities) {
			return interpolate(LocalDate.parse(start), LocalDate.parse(end), restriction, restrictionEnd, activities);
		}

		/**
		 * Applies a function on the raw remaining fraction for certain activities. Note that, if no fractions are set then nothing will be executed.
		 *
		 * @param from       from date (inclusive)
		 * @param to         to date (inclusive)
		 * @param f          function to apply, first parameter is the date, second is the current remaining fraction
		 * @param activities activities where to apply
		 */
		public ConfigBuilder applyToRf(String from, String to, BiFunction<LocalDate, Double, Double> f, String... activities) {

			LocalDate fromDate = LocalDate.parse(from);
			LocalDate toDate = LocalDate.parse(to);

			for (String act : activities) {
				Map<String, Map<String, Object>> p = (Map<String, Map<String, Object>>) params.get(act);

				for (Map.Entry<String, Map<String, Object>> e : p.entrySet()) {
					LocalDate other = LocalDate.parse(e.getKey());

					Double rf = (Double) e.getValue().get("fraction");

					// skip empty and special values
					if (rf == null || rf < -100)
						continue;

					if ((other.isEqual(fromDate) || other.isAfter(fromDate)) && (other.isEqual(toDate) || other.isBefore(toDate)))
						e.getValue().put("fraction", f.apply(other, rf));

				}
			}

			return this;

		}

		/**
		 * Applies a function on the raw config for certain activities.
		 *
		 * @param from       from date (inclusive)
		 * @param to         to date (inclusive)
		 * @param activities activities where to apply
		 * @implNote Unstable API that might be removed,
		 * @deprecated unstable API
		 */
		@Beta
		public ConfigBuilder apply(String from, String to, BiConsumer<LocalDate, Map<String, Object>> f, String... activities) {

			LocalDate fromDate = LocalDate.parse(from);
			LocalDate toDate = LocalDate.parse(to);

			for (String act : activities) {
				Map<String, Map<String, Object>> p = (Map<String, Map<String, Object>>) params.get(act);

				for (Map.Entry<String, Map<String, Object>> e : p.entrySet()) {
					LocalDate other = LocalDate.parse(e.getKey());

					if ((other.isEqual(fromDate) || other.isAfter(fromDate)) && (other.isEqual(toDate) || other.isBefore(toDate)))
						f.accept(other, e.getValue());

				}
			}

			return this;
		}


		/**
		 * Set scaling for remaining fraction when regression is used.
		 */
		public ConfigBuilder setHospitalScale(double scale) {
			params.put("hospital", Map.of("scale", scale));
			return this;
		}

	}

}

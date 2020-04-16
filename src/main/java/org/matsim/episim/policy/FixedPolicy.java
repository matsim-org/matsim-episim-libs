package org.matsim.episim.policy;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import org.matsim.episim.EpisimReporting;

import java.util.HashMap;
import java.util.Map;

/**
 * Set the restrictions based on fixed rules with day and {@link Restriction#getRemainingFraction()}.
 */
public class FixedPolicy extends ShutdownPolicy {

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
		long day = report.day;

		for (Map.Entry<String, Restriction> entry : restrictions.entrySet()) {
			if (!config.hasPath(entry.getKey())) continue;

			Config subConfig = this.config.getConfig(entry.getKey());
			String key = String.valueOf(day);
			if (subConfig.hasPath(key)) {

				Restriction r = Restriction.fromConfig(subConfig.getConfig(key));

				entry.getValue().setRemainingFraction(r.getRemainingFraction());
				entry.getValue().setExposure(r.getExposure());
				entry.getValue().setRequireMask(r.getRequireMask());
			}
		}
	}

	/**
	 * Build fixed config.
	 */
	public static final class ConfigBuilder extends ShutdownPolicy.ConfigBuilder {

		/**
		 * Restrict activities at specific point of time.
		 *
		 * @param day         the day/iteration when it will be in effect
		 * @param restriction restriction to apply
		 * @param activities  activities to restrict
		 */
		@SuppressWarnings("unchecked")
		public ConfigBuilder restrict(long day, Restriction restriction, String... activities) {

			for (String act : activities) {
				Map<String, Map<String, Object>> p = (Map<String, Map<String, Object>>) params.computeIfAbsent(act, m -> new HashMap<>());
				p.put(String.valueOf(day), restriction.asMap());
			}

			return this;
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

	}

}

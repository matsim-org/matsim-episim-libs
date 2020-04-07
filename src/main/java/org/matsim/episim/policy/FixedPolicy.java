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
			if (subConfig.hasPath(key))
				entry.getValue().setRemainingFraction(subConfig.getDouble(key));

		}
	}

	/**
	 * Build fixed config.
	 */
	public static final class ConfigBuilder extends ShutdownPolicy.ConfigBuilder {

		/**
		 * Restrict activities at specific point of time.
		 *
		 * @param activities activities to restrict
		 * @param day        the day/iteration when it will be in effect
		 * @param fraction   fraction of remaining allowed activity
		 */
		@SuppressWarnings("unchecked")
		public ConfigBuilder restrict(long day, double fraction, String... activities) {

			for (String act : activities) {
				Map<String, Double> p = (Map<String, Double>) params.computeIfAbsent(act, m -> new HashMap<>());
				p.put(String.valueOf(day), fraction);
			}

			return this;
		}

		/**
		 * Shutdown activities completely after certain day.
		 */
		public ConfigBuilder shutdown(long day, String... activities) {
			return this.restrict(day, 0d, activities);
		}

		/**
		 * Open activities freely after certain day.
		 */
		public ConfigBuilder open(long day, String... activities) {
			return this.restrict(day, 1d, activities);
		}

	}

}

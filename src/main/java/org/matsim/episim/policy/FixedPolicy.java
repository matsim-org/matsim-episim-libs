package org.matsim.episim.policy;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.model.FaceMask;

import java.util.HashMap;
import java.util.List;
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

				List<Double> remainAndExposure = subConfig.getDoubleList(key);
				entry.getValue().setRemainingFraction(remainAndExposure.get(0));
				entry.getValue().setExposure(remainAndExposure.get(1));

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
		 * @param day        the day/iteration when it will be in effect
		 * @param fraction   fraction of remaining allowed activity
		 * @param exposure   exposure parameter for this activity
		 * @param mask       mask required to wear
		 * @param activities activities to restrict
		 */
		@SuppressWarnings("unchecked")
		public ConfigBuilder restrict(long day, double fraction, double exposure, FaceMask mask, String... activities) {

			for (String act : activities) {
				// TODO: store differently, add mask
				Map<String, List<Double>> p = (Map<String, List<Double>>) params.computeIfAbsent(act, m -> new HashMap<>());
				p.put(String.valueOf(day), Lists.newArrayList(fraction, exposure));
			}

			return this;
		}

		/**
		 * Same as {@link #restrict(long, double, double, FaceMask, String...)} with default values.
		 */
		public ConfigBuilder restrict(long day, double fraction, String... activities) {
			return restrict(day, fraction, 1d, FaceMask.NONE, activities);
		}

		/**
		 * Shutdown activities completely after certain day.
		 */
		public ConfigBuilder shutdown(long day, String... activities) {
			return this.restrict(day, 0d, 1d, FaceMask.NONE, activities);
		}

		/**
		 * Open activities freely after certain day.
		 */
		public ConfigBuilder open(long day, String... activities) {
			return this.restrict(day, 1d, 1d, FaceMask.NONE, activities);
		}

	}

}

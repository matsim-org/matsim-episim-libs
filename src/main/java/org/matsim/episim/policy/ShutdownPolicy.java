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
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.episim.EpisimReporting;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;


/**
 * Abstract base class for policies which are supposed to modify {@link Restriction}s at the end of each day.
 */
public abstract class ShutdownPolicy {

	private static final Logger log = LogManager.getLogger(ShutdownPolicy.class);

	protected final Config config;

	/**
	 * Constructor from config.
	 */
	protected ShutdownPolicy(Config config) {
		this.config = config;
		log.info("Using policy {} with config: {}", getClass(), config.root().render(ConfigRenderOptions.concise().setJson(false)));
	}


	/**
	 * Initialized the policies at start of simulation.
	 * @param start simulation start date
	 * @param restrictions unrestricted and uninitialized restrictions
	 */
	public abstract void init(LocalDate start, ImmutableMap<String, Restriction> restrictions);

	/**
	 * Update the restrictions at the start of the day based on the report.
	 * The map is immutable, use setters of {@link Restriction}.
	 *
	 * @param report       infections statistics of the day
	 * @param restrictions restrictions in place during the day
	 */
	public abstract void updateRestrictions(EpisimReporting.InfectionReport report, ImmutableMap<String, Restriction> restrictions);


	/**
	 * Helper base class for config builders.
	 */
	public static class ConfigBuilder<T> {

		/**
		 * Maps activities to config objects.
		 */
		protected Map<String, T> params = new HashMap<>();

		/**
		 * Public inheritance is forbidden.
		 */
		ConfigBuilder() {
		}

		public Config build() {
			return ConfigFactory.parseMap(params);
		}

	}
}

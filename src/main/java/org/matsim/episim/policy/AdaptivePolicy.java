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
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import it.unimi.dsi.fastutil.objects.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimReporting;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
	 * Date after which restricted policy can be applied.
	 */
	private final LocalDate startDate;
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
	private final Map<String, Object2DoubleSortedMap<LocalDate>> cumCases = new HashMap<>(); // new Object2DoubleAVLTreeMap<>();

	/**
	 * Whether currently following initial, restricted (lockdown) or open policies
	 */
	private final Map<String, Map<String, RestrictionStatus>> restrictionStatus = new HashMap<>();

	/**
	 * List of subdistricts
	 */
	private List<String> subdistricts;

	public enum RestrictionStatus {
		initial,
		restricted,
		open
	}

	public enum RestrictionScope {
		local,
		global
	}

	private final RestrictionScope restrictionScope;

	private final String TOTAL = "total";

	@Inject
	AdaptivePolicy(@Named("policy") Config policyConfig, org.matsim.core.config.Config config) {
		this(policyConfig);
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.subdistricts = episimConfig.getDistricts();
	}


	AdaptivePolicy(Config policyConfig, List<String> subdistricts) {
		this(policyConfig);
		this.subdistricts = subdistricts;
	}

	/**
	 * Constructor from config.
	 */
	AdaptivePolicy(Config config) {
		super(config);
		incidenceTriggers = config.getConfig("incidences");
		restrictedPolicy = config.getConfig("restricted-policy");
		openPolicy = config.getConfig("open-policy");
		initialPolicy = config.hasPath("init-policy") ? config.getConfig("init-policy") : null;
		startDate = config.hasPath("start-date") ? LocalDate.parse(String.valueOf(config.getValue("start-date").unwrapped())) : LocalDate.MIN;
		restrictionScope = config.hasPath("restriction-scope") ? config.getEnum(RestrictionScope.class, "restriction-scope") : RestrictionScope.global;
		this.subdistricts = null;
	}

	public Map<String, Map<String, RestrictionStatus>> getRestrictionStatus() {
		return restrictionStatus;
	}

	/**
	 * Create a config builder for {@link AdaptivePolicy}.
	 */
	public static ConfigBuilder config() {
		return new ConfigBuilder();
	}

	@Override
	public void init(LocalDate start, ImmutableMap<String, Restriction> restrictions) {
		// for restriction scope of global and local:
		restrictionStatus.putIfAbsent(TOTAL, new HashMap<>());
		for (String act : restrictions.keySet()) {
			restrictionStatus.get(TOTAL).put(act, RestrictionStatus.initial);
		}

		if (initialPolicy != null && !initialPolicy.isEmpty()) {
			for (Map.Entry<String, Restriction> e : restrictions.entrySet()) {
				updateRestrictions(start, initialPolicy, e.getKey(), e.getValue(), TOTAL);
			}
		}


		if (restrictionScope.equals(RestrictionScope.local)) {
			for (String district : subdistricts) {
				restrictionStatus.putIfAbsent(district, new HashMap<>());
				for (String act : restrictions.keySet()) {
					restrictionStatus.get(district).put(act, RestrictionStatus.initial);
				}
		if (initialPolicy != null && !initialPolicy.isEmpty()) {

			for (Map.Entry<String, Restriction> e : restrictions.entrySet()) {
						updateRestrictions(start, initialPolicy, e.getKey(), e.getValue(), district);
					}
				}
			}
		}
	}

	@Override
	public void restore(LocalDate start, ImmutableMap<String, Restriction> restrictions) {
		init(start, restrictions);
	}

	// when RestrictionScope == global
	@Override
	public void updateRestrictions(EpisimReporting.InfectionReport report, ImmutableMap<String, Restriction> restrictions) {

		LocalDate date = LocalDate.parse(report.date);
		String location = TOTAL;

		// This allows the initial policy to change day to day
		if (initialPolicy != null && !initialPolicy.isEmpty()) {
			for (String act : restrictions.keySet()) {
				if (restrictionStatus.get(location).get(act).equals(RestrictionStatus.initial)) {
					updateRestrictions(date, initialPolicy, act, restrictions.get(act), location);
				}
			}
		}

		updateRestrictionsForLocation(report, restrictions, date, location);
	}

	// RestrictionScope == local
	public void updateRestrictions(Map<String, EpisimReporting.InfectionReport> reportsLocal, ImmutableMap<String, Restriction> restrictions) {


		for (String location : reportsLocal.keySet()) {

			if (!restrictionStatus.containsKey(location)) {
				continue;
			}
			EpisimReporting.InfectionReport report = reportsLocal.get(location);
			LocalDate date = LocalDate.parse(report.date);

			// This allows the initial policy to change day to day
			if (initialPolicy != null && !initialPolicy.isEmpty()) {
				for (String act : restrictions.keySet()) {
					if (restrictionStatus.get(location).get(act).equals(RestrictionStatus.initial)) {
						updateRestrictions(date, initialPolicy, act, restrictions.get(act), location);
					}
				}
			}

			updateRestrictionsForLocation(report, restrictions, date, location);

		}

	}

	private void updateRestrictionsForLocation(EpisimReporting.InfectionReport report, ImmutableMap<String, Restriction> restrictions, LocalDate date, String location) {
		calculateCases(report, location);
		Object2DoubleSortedMap<LocalDate> cases = cumCases.get(location).tailMap(date.minus(INTERVAL_DAY + 6, ChronoUnit.DAYS));

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

		// if startDate has not yet been reached, only initial restrictions can apply.
		if (date.isBefore(startDate)) {
			return;
		}
		// TODO: use first incidence to decide whether in lockdown or not

		for (Map.Entry<String, ConfigValue> e : incidenceTriggers.entrySet()) {

			String act = e.getKey();
			List<Double> trigger = (List<Double>) e.getValue().unwrapped();

			if (restrictionStatus.get(location).get(act).equals(RestrictionStatus.restricted)) {
				if (incidence.values().stream().allMatch(inc -> inc <= trigger.get(0))) {
					updateRestrictions(date, openPolicy, act, restrictions.get(act), location);
					restrictionStatus.get(location).put(act, RestrictionStatus.open);
				}

			} else {
				if (incidence.getDouble(incidence.lastKey()) >= trigger.get(1)) {
					updateRestrictions(date, restrictedPolicy, act, restrictions.get(act), location);
					restrictionStatus.get(location).put(act, RestrictionStatus.restricted);
					// TODO: what happens if restrictions aren't updated because restriction date has not occured yet; restrictionStatus should not change in this case
				}
			}
		}
	}

	/**
	 * Calculate incidence
	 */
	private void calculateCases(EpisimReporting.InfectionReport report, String location) {
		double cases = report.nShowingSymptomsCumulative * (100_000d / report.nTotal());
		this.cumCases.putIfAbsent(location, new Object2DoubleAVLTreeMap());
		this.cumCases.get(location).put(LocalDate.parse(report.date), cases);
	}

	private void updateRestrictions(LocalDate start, Config policy, String act, Restriction restriction, String location) {

		// activity name
		if (!policy.hasPath(act))
			return;

		Config actConfig = policy.getConfig(act);

		// the restriction should be updated using the closest date before the current date. That is why the keys
		// are first sorted in descending order, and the loop is exited when a fitting value is found.
		NavigableSet<String> descendingDates = (new TreeSet<>(actConfig.root().keySet())).descendingSet();
		for (String date : descendingDates) {
			if (date.startsWith("day")) continue;

			if (LocalDate.parse(date).isBefore(start)) {
				Restriction r = Restriction.fromConfig(actConfig.getConfig(date));

				// if RestrictionScope == global, then the old Restriction is updated entirely by the new Restriction
				// if RestrictionScope == local and location == total --> only total parameters are changed, local ones are let be
				// if RestrictionScope == local and location == Kreuzberg, etc. --> then that neighborhood is changed, but total is left intact.

				if (this.restrictionScope.equals(RestrictionScope.global)) {
					restriction.update(r); //update automatically clears the location based rf
				} else if (this.restrictionScope.equals(RestrictionScope.local)) {
					if (location.equals(TOTAL)) {
						restriction.updateGlobalValuesOnly(r);
					} else {
						Map<String, Double> locationBasedRf = restriction.getLocationBasedRf();
						Map<String, Double> updater = r.getLocationBasedRf();
						if (updater.containsKey(location)) {
							locationBasedRf.put(location, updater.get(location));
						}
					}

				}
				break; // this break is needed so that closest date to current date is chosen.
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
		/**
		 * Define date after which restricted policy can be applied
		 */
		public ConfigBuilder startDate(String startDate) {
			params.put("start-date", startDate);
			return this;
		}

		/**
		 * Define scope: global or local
		 */
		public ConfigBuilder restrictionScope(String restrictionScope) {
			params.put("restriction-scope", restrictionScope);
			return this;
	}
}
}

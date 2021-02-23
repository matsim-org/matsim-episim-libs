package org.matsim.episim.policy;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import it.unimi.dsi.fastutil.objects.Object2DoubleAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.ReplayHandler;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Policy that takes given administrative restrictions and automatically adjust remaining activities based on mobility data.
 */
@SuppressWarnings("unchecked")
public final class AdjustedPolicy extends ShutdownPolicy {

	private static final Logger log = LogManager.getLogger(AdjustedPolicy.class);

	/**
	 * Handler with all events.
	 */
	private final ReplayHandler handler;

	/**
	 * Out-of-home durations for all days.
	 */
	private final SortedMap<LocalDate, Double> outOfHome = new Object2DoubleAVLTreeMap<>();

	/**
	 * Base duration of activities in the simulation.
	 */
	private final Map<DayOfWeek, Object2DoubleMap<String>> simDurations = new EnumMap<>(DayOfWeek.class);

	/**
	 * Activities to administrative periods. (dates as edges)
	 */
	private final Map<String, SortedSet<LocalDate>> periods = new HashMap<>();

	/**
	 * Activities excluded from reduction.
	 */
	private final Set<String> excluded = new HashSet<>();

	/**
	 * Config builder for fixed policy.
	 */
	public static ConfigBuilder config() {
		return new ConfigBuilder();
	}

	@Inject
	protected AdjustedPolicy(@Named("policy") Config config, ReplayHandler handler) {
		super(config);
		this.handler = handler;
	}

	@Override
	public void init(LocalDate start, ImmutableMap<String, Restriction> restrictions) {

		for (Map.Entry<String, ConfigValue> e : config.getConfig("outOfHome").root().entrySet()) {
			LocalDate date = LocalDate.parse(e.getKey());
			outOfHome.put(date, (Double) e.getValue().unwrapped());
		}

		for (Map.Entry<String, ConfigValue> e : config.getConfig("periods").root().entrySet()) {

			SortedSet<LocalDate> dates = new TreeSet<>();

			((List<String>) e.getValue().unwrapped()).forEach(d -> dates.add(LocalDate.parse(d)));

			periods.put(e.getKey(), dates);

			if (config.getConfig("excluded").hasPath(e.getKey()))
				excluded.add(e.getKey());

		}


		// init base durations
		for (Map.Entry<DayOfWeek, List<Event>> e : handler.getEvents().entrySet()) {

			Object2DoubleMap<String> durations = new Object2DoubleOpenHashMap<>();
			Map<Id<Person>, ActivityStartEvent> enterTimes = new HashMap<>();

			for (Event event : e.getValue()) {
				if (event instanceof ActivityStartEvent) {
					enterTimes.put(((ActivityStartEvent) event).getPersonId(), (ActivityStartEvent) event);
				} else if (event instanceof ActivityEndEvent) {
					durations.mergeDouble(
							((ActivityEndEvent) event).getActType(),
							event.getTime() - enterTimes.getOrDefault(((ActivityEndEvent) event).getPersonId(),
									new ActivityStartEvent(0, null, null, null, null)).getTime(),
							Double::sum);

					enterTimes.remove(((ActivityEndEvent) event).getPersonId());
				}
			}

			// add unclosed activities
			enterTimes.forEach((k, v) -> durations.mergeDouble(v.getActType(), Math.max(0, 24 * 3600 - v.getTime()), Double::sum));
			simDurations.put(e.getKey(), durations);
		}

		FixedPolicy.initRestrictions(start, restrictions, config.getConfig("administrative"));
	}

	@Override
	public void updateRestrictions(EpisimReporting.InfectionReport report, ImmutableMap<String, Restriction> restrictions) {

		Config admin = config.getConfig("administrative");

		LocalDate today = LocalDate.parse(report.date);

		double baseDuration = simDurations.get(today.getDayOfWeek())
				.object2DoubleEntrySet().stream()
				.filter(e -> !e.getKey().contains("home"))
				.mapToDouble(Object2DoubleMap.Entry::getDoubleValue).sum();

		double outOfHomeDuration = baseDuration;

		// store administrative activities for the day
		Set<String> administrative = new HashSet<>();

		for (Map.Entry<String, Restriction> e : restrictions.entrySet()) {

			double oldFraction = e.getValue().getRemainingFraction();

			Restriction r = FixedPolicy.readForDay(report, admin, e.getKey());
			if (r != null)
				e.getValue().update(r);

			SortedSet<LocalDate> periods = this.periods.get(e.getKey());

			// check if in admin period, today must lie between two dates
			if (periods == null || periods.headSet(today.plusDays(1)).size() % 2 == 0) {
				// if not administrative, the fraction from previous day is restored
				e.getValue().setRemainingFraction(oldFraction);
				continue;
			}

			double frac = e.getValue().getRemainingFraction();

			administrative.add(e.getKey());

			if (!excluded.contains(e.getKey()))
				outOfHomeDuration -= (1 - frac) * simDurations.get(today.getDayOfWeek()).getDouble(e.getKey());

		}

		// use fraction for today or previous fraction
		double frac;
		if (outOfHome.containsKey(today))
			frac = outOfHome.get(today);
		else {
			SortedMap<LocalDate, Double> untilToday = outOfHome.headMap(today);
			if (untilToday.isEmpty())
				frac = outOfHome.get(outOfHome.firstKey());
			else
				frac = outOfHome.get(untilToday.lastKey());
		}

		double reducedTo = baseDuration * frac;

		double remaining = outOfHomeDuration - reducedTo;
		// available duration on all other activities
		double available = simDurations.get(today.getDayOfWeek())
				.object2DoubleEntrySet().stream()
				.filter(e -> !e.getKey().contains("home") && !administrative.contains(e.getKey()))
				.mapToDouble(Object2DoubleMap.Entry::getDoubleValue).sum();

		double reducedFrac;
		if (remaining < 0) {
			log.warn("Activities reduced by administrative measures above data by {}.", remaining);
			reducedFrac = 0;
		} else if (remaining > available) {
			log.warn("Activity reduction would be negative: {}.", remaining / available);
			reducedFrac = 0;
		} else {
			reducedFrac = remaining / available;
		}

		for (Map.Entry<String, Restriction> e : restrictions.entrySet()) {

			// skip administrative
			if (administrative.contains(e.getKey()) || e.getKey().contains("home") || e.getKey().equals("pt")) continue;

			e.getValue().setRemainingFraction(1 - reducedFrac);

		}
	}

	/**
	 * Builder for {@link AdjustedPolicy} config.
	 */
	public static final class ConfigBuilder extends ShutdownPolicy.ConfigBuilder<Map<String, ?>> {

		/**
		 * Set out-of-home fractions for specific dates.
		 */
		public ConfigBuilder outOfHomeFractions(Map<LocalDate, Double> fractions) {

			Map<String, Double> data = new HashMap<>();
			fractions.forEach((k, v) -> data.put(k.toString(), v));
			params.put("outOfHome", data);

			return this;
		}

		/**
		 * Set administrative restrictions. These overwrite restriction from the mobility input.
		 */
		public ConfigBuilder administrativePolicy(FixedPolicy.ConfigBuilder policy) {
			params.put("administrative", policy.params);
			return this;
		}

		/**
		 * Configure periods during which an activity will be restricted according to {@link #administrativePolicy(FixedPolicy.ConfigBuilder)}.
		 *
		 * @param periods arguments of (multiple) from and to date
		 */
		public ConfigBuilder administrativePeriod(String activity, LocalDate... periods) {
			administrativePeriod(activity, false, periods);
			return this;
		}

		/**
		 * Configure periods during which an activity will be restricted according to {@link #administrativePolicy(FixedPolicy.ConfigBuilder)}.
		 *
		 * @param excludeFromReduction the activity durations are not counted into the reduction
		 * @param periods              arguments of (multiple) from and to date
		 */
		public ConfigBuilder administrativePeriod(String activity, boolean excludeFromReduction, LocalDate... periods) {

			Map<String, List<String>> map = (Map<String, List<String>>) params.computeIfAbsent("periods", k -> new HashMap<>());
			map.put(activity, Arrays.stream(periods).map(LocalDate::toString).collect(Collectors.toList()));

			Map<String, Boolean> excluded = (Map<String, Boolean>) params.computeIfAbsent("excluded", k -> new HashMap<>());

			if (excludeFromReduction)
				excluded.put(activity, true);

			return this;
		}

	}

}

package org.matsim.episim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Protection over time after an immunity event, as used by {@code ImmunityConfigGroup}.
 *
 * <p>A piecewise-linear function of the days since the event, written as points {@code day>protection}
 * separated by {@code |}, e.g. {@code 0>0.47|90>0.40|365>0.10}. The rules are strict on purpose, because
 * every one of them was a source of silently wrong values in the vaccination curves this replaces:</p>
 * <ul>
 *     <li>the x-axis is whole days since the event, the y-axis is protection between {@code 0} (none) and
 *     {@code 1} (complete) &mdash; one scale, no mixture of protection and remaining risk;</li>
 *     <li>the first point is at day {@code 0}, so there is no rule for the time before it;</li>
 *     <li>days increase strictly in the order written; a curve is never reordered silently;</li>
 *     <li>between points the value is interpolated linearly, after the last point it is kept. A protection
 *     that wanes to nothing needs an explicit last point with {@code 0};</li>
 *     <li>there are no sentinel days and nothing depends on the order of other settings.</li>
 * </ul>
 * <p>Instances are immutable. {@link #toString()} writes the same format {@link #parse(String)} reads.</p>
 */
public final class ProtectionCurve {

	/**
	 * No protection at any time. Written out explicitly in a configuration, it states "no protection" as a
	 * decision rather than as a forgotten entry.
	 */
	public static final ProtectionCurve NONE = new ProtectionCurve(new TreeMap<>(Map.of(0, 0.0)));

	private static final String POINT_SEPARATOR = "|";
	private static final String DAY_SEPARATOR = ">";

	private final NavigableMap<Integer, Double> points;

	private ProtectionCurve(NavigableMap<Integer, Double> points) {
		this.points = Collections.unmodifiableNavigableMap(points);
	}

	/**
	 * Reads a curve such as {@code 0>0.47|90>0.40|365>0.10}.
	 *
	 * @throws IllegalArgumentException if the text is malformed or breaks one of the rules of this class; the
	 *                                  message names the offending point
	 */
	public static ProtectionCurve parse(String text) {
		Objects.requireNonNull(text, "Protection curve must not be null");
		if (text.isBlank())
			throw new IllegalArgumentException("Protection curve is empty; write '0>0.0' for no protection");

		List<Integer> days = new ArrayList<>();
		List<Double> values = new ArrayList<>();

		for (String point : text.split("\\" + POINT_SEPARATOR, -1)) {
			String[] parts = point.split(DAY_SEPARATOR, -1);
			if (parts.length != 2)
				throw new IllegalArgumentException("Point '" + point + "' of protection curve '" + text
					+ "' is not of the form day" + DAY_SEPARATOR + "protection");

			try {
				days.add(Integer.parseInt(parts[0].trim()));
				values.add(Double.parseDouble(parts[1].trim()));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Point '" + point + "' of protection curve '" + text
					+ "' needs a whole day and a number", e);
			}
		}

		return of(days, values, text);
	}

	/**
	 * Builds a curve from points given as {@code day, protection, day, protection, ...}, in increasing day order.
	 * Meant for code and tests; configurations use {@link #parse(String)}.
	 */
	public static ProtectionCurve of(double... dayProtectionPairs) {
		if (dayProtectionPairs.length == 0 || dayProtectionPairs.length % 2 != 0)
			throw new IllegalArgumentException("Protection curve needs pairs of day and protection, got "
				+ dayProtectionPairs.length + " numbers");

		List<Integer> days = new ArrayList<>();
		List<Double> values = new ArrayList<>();
		for (int i = 0; i < dayProtectionPairs.length; i += 2) {
			double day = dayProtectionPairs[i];
			if (day != Math.rint(day))
				throw new IllegalArgumentException("Day " + day + " of protection curve is not a whole day");
			days.add((int) day);
			values.add(dayProtectionPairs[i + 1]);
		}

		return of(days, values, null);
	}

	private static ProtectionCurve of(List<Integer> days, List<Double> values, String text) {
		String curve = text != null ? " '" + text + "'" : "";

		if (days.get(0) != 0)
			throw new IllegalArgumentException("Protection curve" + curve + " has to start at day 0, but starts at day "
				+ days.get(0));

		NavigableMap<Integer, Double> points = new TreeMap<>();
		for (int i = 0; i < days.size(); i++) {
			int day = days.get(i);
			double value = values.get(i);

			if (i > 0 && day <= days.get(i - 1))
				throw new IllegalArgumentException("Days of protection curve" + curve + " have to increase strictly, but day "
					+ day + " follows day " + days.get(i - 1));
			if (!(value >= 0 && value <= 1))
				throw new IllegalArgumentException("Protection " + value + " at day " + day + " of protection curve" + curve
					+ " is not between 0 and 1");

			points.put(day, value);
		}

		return new ProtectionCurve(points);
	}

	/**
	 * Protection on the given day since the immunity event.
	 *
	 * @throws IllegalArgumentException for a negative day, i.e. an event that has not happened yet
	 */
	public double protectionAt(int day) {
		if (day < 0)
			throw new IllegalArgumentException("Protection is asked for day " + day + ", before the immunity event");

		Map.Entry<Integer, Double> floor = points.floorEntry(day);
		if (floor.getKey() == day)
			return floor.getValue();

		Map.Entry<Integer, Double> ceiling = points.ceilingEntry(day);
		if (ceiling == null)
			return floor.getValue();

		double fraction = (double) (day - floor.getKey()) / (ceiling.getKey() - floor.getKey());
		return floor.getValue() + fraction * (ceiling.getValue() - floor.getValue());
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder();
		for (Map.Entry<Integer, Double> point : points.entrySet()) {
			if (text.length() > 0)
				text.append(POINT_SEPARATOR);
			text.append(point.getKey()).append(DAY_SEPARATOR).append(point.getValue());
		}
		return text.toString();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ProtectionCurve && points.equals(((ProtectionCurve) o).points);
	}

	@Override
	public int hashCode() {
		return points.hashCode();
	}
}

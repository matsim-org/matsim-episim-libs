package org.matsim.episim.policy;

import com.typesafe.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.episim.model.FaceMask;
import org.matsim.facilities.ActivityFacility;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represent the current restrictions on an activity type.
 */
public final class Restriction {

	private static final Logger log = LogManager.getLogger(Restriction.class);

	/**
	 * Percentage of activities still performed.
	 */
	@Nullable
	private Double remainingFraction;

	/**
	 * Contact intensity correction factor.
	 */
	@Nullable
	private Double ciCorrection;

	/**
	 * Maximum allowed group size of activities.
	 */
	@Nullable
	private Integer maxGroupSize;

	/**
	 * Ids of closed facilities.
	 */
	@Nullable
	private Set<Id<ActivityFacility>> closed;

	/**
	 * {@link ClosingHours} when activity is closed.
	 */
	@Nullable
	private ClosingHours closingHours;

	/**
	 * Maps mask type to percentage of persons wearing it.
	 */
	private Map<FaceMask, Double> maskUsage = new EnumMap<>(FaceMask.class);

	/**
	 * Constructor.
	 */
	private Restriction(@Nullable Double remainingFraction, @Nullable Double ciCorrection, @Nullable Integer maxGroupSize,
						@Nullable List<String> closed, @Nullable ClosingHours closingHours, @Nullable Map<FaceMask, Double> maskUsage) {

		if (remainingFraction != null && (Double.isNaN(remainingFraction) || remainingFraction < 0 || remainingFraction > 1))
			throw new IllegalArgumentException("remainingFraction must be between 0 and 1 but is=" + remainingFraction);
		if (ciCorrection != null && (Double.isNaN(ciCorrection) || ciCorrection < 0))
			throw new IllegalArgumentException("contact intensity correction must be larger than 0 but is=" + ciCorrection);
		if (maskUsage != null && maskUsage.values().stream().anyMatch(p -> p < 0 || p > 1))
			throw new IllegalArgumentException("Mask usage probabilities must be between [0, 1]");

		this.remainingFraction = remainingFraction;
		this.ciCorrection = ciCorrection;
		this.maxGroupSize = maxGroupSize;
		this.closingHours = closingHours;

		if (closed != null) {
			this.closed = closed.stream().map(s -> Id.create(s, ActivityFacility.class)).collect(Collectors.toSet());
		}

		// Compute cumulative probabilities
		if (maskUsage != null && !maskUsage.isEmpty()) {
			if (maskUsage.containsKey(FaceMask.NONE))
				throw new IllegalArgumentException("Mask usage for NONE must not be given");

			// stream must be sorted or the order is undefined, which can result in different sums
			double total = maskUsage.values().stream().sorted().mapToDouble(Double::doubleValue).sum();
			if (total > 1) throw new IllegalArgumentException("Sum of mask usage rates must be < 1");

			double sum = 1 - total;
			this.maskUsage.put(FaceMask.NONE, sum);

			for (FaceMask m : FaceMask.values()) {
				if (maskUsage.containsKey(m)) {
					sum += maskUsage.get(m);
					if (Double.isNaN(sum))
						throw new IllegalArgumentException("Mask usage contains NaN value!");

					this.maskUsage.put(m, sum);
				}
			}
		}
	}

	/**
	 * Create from other restriction.
	 *
	 * @param maskUsage will only be used of other is null
	 */
	Restriction(@Nullable Double remainingFraction, @Nullable Double ciCorrection, @Nullable Integer maxGroupSize,
				@Nullable List<String> closed, @Nullable ClosingHours closingHours, @Nullable Map<FaceMask, Double> maskUsage, Restriction other) {
		this.remainingFraction = remainingFraction;
		this.ciCorrection = ciCorrection;
		this.maxGroupSize = maxGroupSize;
		this.closingHours = closingHours;
		this.maskUsage.putAll(other != null ? other.maskUsage : maskUsage);

		if (closed != null) {
			this.closed = closed.stream().map(s -> Id.create(s, ActivityFacility.class)).collect(Collectors.toSet());
		}
	}

	/**
	 * Restriction that allows everything.
	 */
	public static Restriction none() {
		return new Restriction(1d, 1d, Integer.MAX_VALUE, null, null, Map.of());
	}

	/**
	 * Restriction only reducing the {@link #remainingFraction}.
	 */
	public static Restriction of(double remainingFraction) {
		return new Restriction(remainingFraction, null, null, null, null, null);
	}

	/**
	 * Restriction with remaining fraction and ci correction.
	 */
	public static Restriction of(double remainingFraction, double ciCorrection) {
		return new Restriction(remainingFraction, ciCorrection, null, null, null, null);
	}

	/**
	 * Restriction with remaining fraction, ci correction and mask usage.
	 */
	public static Restriction of(double remainingFraction, double ciCorrection, Map<FaceMask, Double> maskUsage) {
		return new Restriction(remainingFraction, ciCorrection, null, null, null, maskUsage);
	}

	/**
	 * Helper function for restriction with one mask compliance.
	 * See {@link #ofMask(FaceMask, double)}.
	 */
	public static Restriction of(double remainingFraction, FaceMask mask, double maskCompliance) {
		return new Restriction(remainingFraction, null, null, null, null, Map.of(mask, maskCompliance));
	}

	/**
	 * Creates a restriction with one mask type and its compliance rates.
	 *
	 * @see #ofMask(Map)
	 */
	public static Restriction ofMask(FaceMask mask, double complianceRate) {
		return new Restriction(null, null, null, null, null, Map.of(mask, complianceRate));
	}

	/**
	 * Creates a restriction with required masks and compliance rates. Sum has to be smaller than 1.
	 * Not defined probability goes into the {@link FaceMask#NONE}.
	 */
	public static Restriction ofMask(Map<FaceMask, Double> maskUsage) {
		return new Restriction(null, null, null, null, null, maskUsage);
	}

	/**
	 * Creates a restriction with certain facilities closed. Should not be combined with other restrictions.
	 */
	public static Restriction ofClosedFacilities(List<String> closed) {
		return new Restriction(null, null, null, closed, null, null);
	}

	/**
	 * Creates a restriction, which has only a contact intensity correction set.
	 */
	public static Restriction ofCiCorrection(double ciCorrection) {
		return new Restriction(null, ciCorrection, null, null, null, null);
	}

	/**
	 * Creates a restriction with limited maximum group size of activities.
	 */
	public static Restriction ofGroupSize(int maxGroupSize) {
		return new Restriction(null, null, maxGroupSize, null, null, null);
	}

	/**
	 * Creates a restriction for activity to be closed during certain hours.
	 * If {@code fromHour} is larger than {@code toHour} it is assumed that the closing hour is over midnight.
	 *
	 * @param fromHour hour of the day when activity will close
	 * @param toHour   hour of the day when activity will open again.
	 */
	public static Restriction ofClosingHours(int fromHour, int toHour) {

		ClosingHours closed = asClosingHours(List.of(fromHour * 3600, toHour * 3600));

		return new Restriction(null, null, null, null, closed, null);
	}

	/**
	 * Creates a restriction from a config entry.
	 */
	public static Restriction fromConfig(Config config) {
		// Could be integer or double
		Map<String, Number> nameMap = (Map<String, Number>) config.getValue("masks").unwrapped();

		Map<FaceMask, Double> enumMap = new EnumMap<>(FaceMask.class);

		if (nameMap != null)
			nameMap.forEach((k, v) -> enumMap.put(FaceMask.valueOf(k), v.doubleValue()));

		return new Restriction(
				config.getIsNull("fraction") ? null : config.getDouble("fraction"),
				config.getIsNull("ciCorrection") ? null : config.getDouble("ciCorrection"),
				config.getIsNull("maxGroupSize") ? null : config.getInt("maxGroupSize"),
				!config.hasPath("closed") || config.getIsNull("closed") ? null : config.getStringList("closed"),
				!config.hasPath("closingHours") || config.getIsNull("closingHours") ? null : asClosingHours(config.getIntList("closingHours")),
				enumMap, null
		);
	}

	/**
	 * Convert list of ints to closing hour instances.
	 */
	private static ClosingHours asClosingHours(List<Integer> closingHours) {

		if (closingHours == null)
			return null;

		return new ClosingHours(closingHours.get(0), closingHours.get(1));
	}

	/**
	 * Creates a copy of a restriction.
	 */
	static Restriction clone(Restriction restriction) {
		return new Restriction(restriction.remainingFraction, restriction.ciCorrection, restriction.maxGroupSize,
				restriction.closed == null ? null : restriction.closed.stream().map(Objects::toString).collect(Collectors.toList()),
				restriction.closingHours,
				null, restriction);
	}

	/**
	 * Determines / Randomly draws which mask a persons wears while this restriction is in place.
	 */
	public FaceMask determineMask(SplittableRandom rnd) {

		if (maskUsage.isEmpty()) return FaceMask.NONE;

		double p = Double.NaN;
		for (Map.Entry<FaceMask, Double> e : maskUsage.entrySet()) {

			if (e.getValue() == 1d) return e.getKey();
			else if (Double.isNaN(p))
				p = rnd.nextDouble();

			if (p < e.getValue())
				return e.getKey();

		}

		throw new IllegalStateException("Could not determine mask. Probabilities are likely wrong.");
	}

	/**
	 * Check whether one time falls into a closing hour.
	 *
	 * @param time      zero based timestamp in seconds
	 * @param adjustEnd when true result time is shifted to be later, otherwise shifted to start of closing hour
	 * @return adjusted time, unchanged when not in closing hour. Otherwise moved to closing hours
	 */
	public double adjustByClosingHour(double time, boolean adjustEnd) {
		if (closingHours == null)
			return time;

		// closing of 0-24 needs to be handled separate as time can not be adjusted
		if (closingHours.length >= 86400)
			return adjustEnd ? Integer.MAX_VALUE : Integer.MIN_VALUE;

		// seconds of day
		double sod = time % 86400;

		ClosingHours ch = closingHours;

		if (ch.overnight) {
			if (sod > ch.from)
				return adjustEnd ? time + (ch.length - (sod - ch.from)) : time - (sod - ch.from);
			else if (sod < ch.to)
				return adjustEnd ? time + (ch.to - sod) : time - (ch.length - (ch.to - sod));

		} else {
			if (sod > ch.from && sod <= ch.to)
				return adjustEnd ? time + (ch.to - sod) : time - (sod - ch.from);
		}


		return time;
	}

	/**
	 * This method is also used to write the restriction to csv.
	 */
	@Override
	public String toString() {
		return String.format(Locale.ENGLISH, "%.2f_%.2f_%s", remainingFraction, ciCorrection, maskUsage);
	}

	/**
	 * Set restriction values from other restriction update.
	 */
	void update(Restriction r) {
		// All values may be optional and are only set if present
		if (r.getRemainingFraction() != null)
			remainingFraction = r.getRemainingFraction();

		if (r.getCiCorrection() != null)
			ciCorrection = r.getCiCorrection();

		if (r.getMaxGroupSize() != null)
			maxGroupSize = r.getMaxGroupSize();

		if (r.closed != null)
			closed = r.closed;

		if (r.closingHours != null)
			closingHours = r.closingHours;

		if (!r.maskUsage.isEmpty()) {
			maskUsage.clear();
			maskUsage.putAll(r.maskUsage);
		}
	}

	/**
	 * Merges another restrictions into this one. Will fail if any attribute would be overwritten.
	 *
	 * @see #asMap()
	 */
	void merge(Map<String, Object> restriction) {

		Double otherRf = (Double) restriction.get("fraction");
		Double otherE = (Double) restriction.get("ciCorrection");
		Integer otherGroup = (Integer) restriction.get("maxGroupSize");
		ClosingHours otherClosingH = asClosingHours((List<Integer>) restriction.get("closingHours"));

		Map<FaceMask, Double> otherMasks = new EnumMap<>(FaceMask.class);
		((Map<String, Double>) restriction.get("masks"))
				.forEach((k, v) -> otherMasks.put(FaceMask.valueOf(k), v));

		if (remainingFraction != null && otherRf != null && !remainingFraction.equals(otherRf))
			log.warn("Duplicated remainingFraction " + remainingFraction + " and " + otherRf);
		else if (remainingFraction == null)
			remainingFraction = otherRf;

		if (ciCorrection != null && otherE != null && !ciCorrection.equals(otherE))
			log.warn("Duplicated ci correction " + ciCorrection + " and " + otherE);
		else if (ciCorrection == null)
			ciCorrection = otherE;

		if (maxGroupSize != null && otherGroup != null && !maxGroupSize.equals(otherGroup))
			log.warn("Duplicated max group size " + maxGroupSize + " and " + otherGroup);
		else if (maxGroupSize == null)
			maxGroupSize = otherGroup;

		if (closingHours != null && otherClosingH != null && !closingHours.equals(otherClosingH))
			log.warn("Duplicated max closing hours " + closingHours + " and " + otherClosingH);
		else if (closingHours == null)
			closingHours = otherClosingH;

		if (!maskUsage.isEmpty() && !otherMasks.isEmpty() && !maskUsage.equals(otherMasks)) {
			log.warn("Duplicated mask usage; existing value=" + maskUsage + "; new value=" + otherMasks + "; keeping existing value.");
			log.warn("(full new restriction=" + restriction + ")");
		} else if (maskUsage.isEmpty())
			maskUsage.putAll(otherMasks);

	}

	public Double getRemainingFraction() {
		return remainingFraction;
	}

	void setRemainingFraction(double remainingFraction) {
		this.remainingFraction = remainingFraction;
	}

	public Double getCiCorrection() {
		return ciCorrection;
	}

	public boolean isClosed(Id<?> containerId) {
		if (closed == null)
			return false;

		return closed.contains(containerId);
	}

	@Nullable
	public ClosingHours getClosingHours() {
		return closingHours;
	}

	@Nullable
	public Integer getMaxGroupSize() {
		return maxGroupSize;
	}

	Map<FaceMask, Double> getMaskUsage() {
		return maskUsage;
	}

	void fullShutdown() {
		remainingFraction = 0d;
	}

	void open() {
		remainingFraction = 1d;
		maskUsage = null;
	}

	/**
	 * Attributes of this restriction as map.
	 */
	public Map<String, Object> asMap() {
		Map<String, Object> map = new HashMap<>();
		map.put("fraction", remainingFraction);
		map.put("ciCorrection", ciCorrection);
		map.put("maxGroupSize", maxGroupSize);

		if (closed != null)
			map.put("closed", closed.stream().map(Object::toString).collect(Collectors.toList()));

		// Must be converted to map with strings
		Map<String, Double> nameMap = new LinkedHashMap<>();
		maskUsage.forEach((k, v) -> nameMap.put(k.name(), v));
		map.put("masks", nameMap);

		if (closingHours != null) {
			map.put("closingHours", List.of(closingHours.from, closingHours.to));
		}


		return map;
	}

	/**
	 * Hours when an activity is closed.
	 */
	public static final class ClosingHours {

		/**
		 * Starting second when activity is closed (exclusive)
		 */
		public final int from;

		/**
		 * Seconds until when activity is still closed.
		 */
		public final int to;

		/**
		 * Closed overnight.
		 */
		public final boolean overnight;

		/**
		 * Length of closing hours.
		 */
		public final int length;

		ClosingHours(int from, int to) {
			if (from == to)
				throw new IllegalArgumentException("Closing time must be different from each other.");

			this.from = from;
			this.to = to;
			this.overnight = from > to;
			this.length = overnight ? (86400 - from) + to : to - from;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ClosingHours that = (ClosingHours) o;
			return from == that.from &&
					to == that.to;
		}

		@Override
		public int hashCode() {
			return Objects.hash(from, to);
		}

		@Override
		public String toString() {
			return "ClosingHours{" +
					"from=" + from +
					", to=" + to +
					'}';
		}
	}

}

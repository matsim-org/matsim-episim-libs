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
	 * Maps mask type to percentage of persons wearing it.
	 */
	private Map<FaceMask, Double> maskUsage = new EnumMap<>(FaceMask.class);

	/**
	 * Constructor.
	 */
	private Restriction(@Nullable Double remainingFraction, @Nullable Double ciCorrection, @Nullable Integer maxGroupSize,
						@Nullable List<String> closed, @Nullable Map<FaceMask, Double> maskUsage) {

		if (remainingFraction != null && (Double.isNaN(remainingFraction) || remainingFraction < 0 || remainingFraction > 1))
			throw new IllegalArgumentException("remainingFraction must be between 0 and 1 but is=" + remainingFraction);
		if (ciCorrection != null && (Double.isNaN(ciCorrection) || ciCorrection < 0))
			throw new IllegalArgumentException("contact intensity correction must be larger than 0 but is=" + ciCorrection);
		if (maskUsage != null && maskUsage.values().stream().anyMatch(p -> p < 0 || p > 1))
			throw new IllegalArgumentException("Mask usage probabilities must be between [0, 1]");

		this.remainingFraction = remainingFraction;
		this.ciCorrection = ciCorrection;
		this.maxGroupSize = maxGroupSize;

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
				@Nullable List<String> closed, @Nullable Map<FaceMask, Double> maskUsage, Restriction other) {
		this.remainingFraction = remainingFraction;
		this.ciCorrection = ciCorrection;
		this.maxGroupSize = maxGroupSize;
		this.maskUsage.putAll(other != null ? other.maskUsage : maskUsage);

		if (closed != null) {
			this.closed = closed.stream().map(s -> Id.create(s, ActivityFacility.class)).collect(Collectors.toSet());
		}
	}

	/**
	 * Restriction that allows everything.
	 */
	public static Restriction none() {
		return new Restriction(1d, 1d, Integer.MAX_VALUE, null,Map.of());
	}

	/**
	 * Restriction only reducing the {@link #remainingFraction}.
	 */
	public static Restriction of(double remainingFraction) {
		return new Restriction(remainingFraction, null, null,  null, null);
	}

	/**
	 * Restriction with remaining fraction and ci correction.
	 */
	public static Restriction of(double remainingFraction, double ciCorrection) {
		return new Restriction(remainingFraction, ciCorrection, null,  null, null);
	}

	/**
	 * Restriction with remaining fraction, ci correction and mask usage.
	 */
	public static Restriction of(double remainingFraction, double ciCorrection, Map<FaceMask, Double> maskUsage) {
		return new Restriction(remainingFraction, ciCorrection, null,  null, maskUsage);
	}

	/**
	 * Helper function for restriction with one mask compliance.
	 * See {@link #ofMask(FaceMask, double)}.
	 */
	public static Restriction of(double remainingFraction, FaceMask mask, double maskCompliance) {
		return new Restriction(remainingFraction, null, null, null, Map.of(mask, maskCompliance));
	}

	/**
	 * Creates a restriction with one mask type and its compliance rates.
	 *
	 * @see #ofMask(Map)
	 */
	public static Restriction ofMask(FaceMask mask, double complianceRate) {
		return new Restriction(null, null, null,null,  Map.of(mask, complianceRate));
	}

	/**
	 * Creates a restriction with required masks and compliance rates. Sum has to be smaller than 1.
	 * Not defined probability goes into the {@link FaceMask#NONE}.
	 */
	public static Restriction ofMask(Map<FaceMask, Double> maskUsage) {
		return new Restriction(null, null, null, null, maskUsage);
	}

	/**
	 * Creates a restriction with certain facilities closed. Should not be combined with other restrictions.
	 */
	public static Restriction ofClosedFacilities(List<String> closed) {
		return new Restriction(null, null, null, closed, null);
	}

	/**
	 * Renamed to ci correction.
	 *
	 * @deprecated Use {@link #ofCiCorrection(double)}.
	 */
	@Deprecated
	public static Restriction ofExposure(double exposure) {
		return ofCiCorrection(exposure);
	}

	/**
	 * Creates a restriction, which has only a contact intensity correction set.
	 */
	public static Restriction ofCiCorrection(double ciCorrection) {
		return new Restriction(null, ciCorrection, null, (List) null, null);
	}

	/**
	 * Creates a restriction with limited maximum group size of activities.
	 */
	public static Restriction ofGroupSize(int maxGroupSize) {
		return new Restriction(null, null, maxGroupSize, (List) null, null);
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
				enumMap, null
		);
	}

	/**
	 * Creates a copy of a restriction.
	 */
	static Restriction clone(Restriction restriction) {
		return new Restriction(restriction.remainingFraction, restriction.ciCorrection, restriction.maxGroupSize,
				restriction.closed == null ? null : restriction.closed.stream().map(Objects::toString).collect(Collectors.toList()),
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

		return map;
	}

}

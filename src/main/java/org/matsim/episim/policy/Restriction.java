package org.matsim.episim.policy;

import com.typesafe.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.episim.model.FaceMask;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

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
	 * Exposure during this activity.
	 */
	@Nullable
	private Double exposure;

	/**
	 * Persons are required to wear a mask with this or more effective type.
	 */
	@Nullable
	private FaceMask requireMask;

	/**
	 * Compliance rate for masks. Overwrites global parameter if set.
	 */
	@Nullable
	private Double complianceRate;

	/**
	 * Constructor.
	 */
	private Restriction(@Nullable Double remainingFraction, @Nullable Double exposure,
						@Nullable FaceMask requireMask, @Nullable Double complianceRate) {

		if (remainingFraction != null && (Double.isNaN(remainingFraction) || remainingFraction < 0 || remainingFraction > 1))
			throw new IllegalArgumentException("remainingFraction must be between 0 and 1 but is=" + remainingFraction);
		if (exposure != null &&  (Double.isNaN(exposure) || exposure < 0))
			throw new IllegalArgumentException("exposure must be larger than 0 but is=" + exposure);

		this.remainingFraction = remainingFraction;
		this.exposure = exposure;
		this.requireMask = requireMask;
		this.complianceRate = complianceRate;
	}

	/**
	 * Restriction that allows everything.
	 */
	public static Restriction none() {
		return new Restriction(1d, 1d, FaceMask.NONE, null);
	}

	/**
	 * Restriction only reducing the {@link #remainingFraction}.
	 */
	public static Restriction of(double remainingFraction) {
		return new Restriction(remainingFraction, null, null, null);
	}

	/**
	 * See {@link #of(Double, Double, FaceMask)}.
	 */
	public static Restriction of(double remainingFraction, FaceMask mask) {
		return new Restriction(remainingFraction, null, mask, null);
	}

	/**
	 * Instantiate a restriction.
	 */
	public static Restriction of(Double remainingFraction, Double exposure, FaceMask mask) {
		return new Restriction(remainingFraction, exposure, mask, null);
	}

	/**
	 * Creates a restriction with required mask.
	 */
	public static Restriction ofMask(FaceMask mask) {
		return new Restriction(null, null, mask, null);
	}

	/**
	 * Creates a restriction with required mask and compliance rate.
	 */
	public static Restriction ofMask(FaceMask mask, double complianceRate) {
		return new Restriction(null, null, mask, complianceRate);
	}

	/**
	 * Creates a restriction with only exposure set.
	 */
	public static Restriction ofExposure(double exposure) {
		return new Restriction(null, exposure, null, null);
	}


	/**
	 * Creates a restriction from a config entry.
	 */
	public static Restriction fromConfig(Config config) {
		return new Restriction(
				config.getIsNull("fraction") ? null : config.getDouble("fraction"),
				config.getIsNull("exposure") ? null : config.getDouble("exposure"),
				config.getIsNull("mask") ? null : config.getEnum(FaceMask.class, "mask"),
				config.getIsNull("compliance") ? null : config.getDouble("compliance")
		);
	}

	/**
	 * Creates a copy of a restriction.
	 */
	static Restriction clone(Restriction restriction) {
		return new Restriction(restriction.remainingFraction, restriction.exposure, restriction.requireMask, restriction.complianceRate);
	}


	/**
	 * This method is also used to write the restriction to csv.
	 */
	@Override
	public String toString() {
		return String.format("%.2f_%.2f_%s", remainingFraction, exposure, requireMask);
	}

	/**
	 * Set restriction values from other restriction update.
	 */
	void update(Restriction r) {
		// All values may be optional and are only set if present
		if (r.getRemainingFraction() != null)
			setRemainingFraction(r.getRemainingFraction());

		if (r.getExposure() != null)
			setExposure(r.getExposure());

		if (r.getRequireMask() != null)
			setRequireMask(r.getRequireMask());

		if (r.getComplianceRate() != null)
			setComplianceRate(r.getComplianceRate());

	}

	/**
	 * Merges another restrictions into this one. Will fail if any attribute would be overwritten.
	 *
	 * @see #asMap()
	 */
	Restriction merge(Map<String, Object> r) {

		Double otherRf = (Double) r.get("fraction");
		Double otherE = (Double) r.get("exposure");
		Double otherComp = (Double) r.get("compliance");
		FaceMask otherMask = r.get("mask") == null ? null : FaceMask.valueOf((String) r.get("mask"));

		if (remainingFraction != null && otherRf != null && !remainingFraction.equals(otherRf))
			log.warn("Duplicated remainingFraction " + remainingFraction + " and " + otherRf);
		else if (remainingFraction == null)
			remainingFraction = otherRf;

		if (exposure != null && otherE != null && !exposure.equals(otherE))
			log.warn("Duplicated exposure " + exposure + " and " + otherE);
		else if (exposure == null)
			exposure = otherE;

		if (requireMask != null && otherMask != null && requireMask != otherMask)
			log.warn("Duplicated mask " + requireMask + " and " + otherMask);
		else if (requireMask == null)
			requireMask = otherMask;

		if (complianceRate != null && otherComp != null && !complianceRate.equals(otherComp))
			log.warn("Duplicated complianceRate " + complianceRate + " and " + otherComp);
		else if (complianceRate == null)
			complianceRate = otherComp;

		return this;
	}

	public Double getRemainingFraction() {
		return remainingFraction;
	}

	void setRemainingFraction(double remainingFraction) {
		this.remainingFraction = remainingFraction;
	}

	public Double getExposure() {
		return exposure;
	}

	public void setExposure(double exposure) {
		this.exposure = exposure;
	}

	public FaceMask getRequireMask() {
		return requireMask;
	}

	public void setRequireMask(FaceMask requireMask) {
		this.requireMask = requireMask;
	}

	@Nullable
	public Double getComplianceRate() {
		return complianceRate;
	}

	void setComplianceRate(double complianceRate) {
		this.complianceRate = complianceRate;
	}

	void fullShutdown() {
		remainingFraction = 0d;
	}

	void open() {
		remainingFraction = 1d;
		requireMask = FaceMask.NONE;
	}

	Map<String, Object> asMap() {
		Map<String, Object> map = new HashMap<>();
		map.put("fraction", remainingFraction);
		map.put("exposure", exposure);
		map.put("mask", requireMask != null ? requireMask.name() : null);
		map.put("compliance", complianceRate);
		return map;
	}

}

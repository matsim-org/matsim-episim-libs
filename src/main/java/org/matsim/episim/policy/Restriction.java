package org.matsim.episim.policy;

import com.typesafe.config.Config;
import org.matsim.episim.model.FaceMask;

import java.util.HashMap;
import java.util.Map;

/**
 * Represent the current restrictions on an activity type.
 */
public final class Restriction {

	/**
	 * Percentage of activities still performed.
	 */
	private double remainingFraction;

	/**
	 * Exposure during this activity.
	 */
	private double exposure;

	/**
	 * Persons are required to wear a mask with this or more effective type.
	 */
	private FaceMask requireMask;

	/**
	 * Constructor.
	 */
	public Restriction(double remainingFraction, double exposure, FaceMask requireMask) {
		this.remainingFraction = remainingFraction;
		this.exposure = exposure;
		this.requireMask = requireMask;
	}

	/**
	 * Restriction that allows everything.
	 */
	public static Restriction none() {
		return new Restriction(1d, 1d, FaceMask.NONE);
	}

	/**
	 * Restriction only reducing the {@link #remainingFraction}.
	 */
	public static Restriction of(double remainingFraction) {
		return new Restriction(remainingFraction, 1d, FaceMask.NONE);
	}

	/**
	 * See {@link #of(double, double, FaceMask)}.
	 */
	public static Restriction of(double remainingFraction, FaceMask mask) {
		return new Restriction(remainingFraction, 1d, mask);
	}

	/**
	 * Instantiate a restriction-
	 */
	public static Restriction of(double remainingFraction, double exposure, FaceMask mask) {
		return new Restriction(remainingFraction, exposure, mask);
	}

	/**
	 * Creates a restriction from a config entry.
	 */
	public static Restriction fromConfig(Config config) {
		return new Restriction(
				config.getDouble("fraction"),
				config.getDouble("exposure"),
				config.getEnum(FaceMask.class, "mask")
		);
	}

	/**
	 * This method is also used to write the restriction to csv.
	 */
	@Override
	public String toString() {
		return String.format("%.2f_%s", remainingFraction, requireMask);
	}

	public double getRemainingFraction() {
		return remainingFraction;
	}

	void setRemainingFraction(double remainingFraction) {
		this.remainingFraction = remainingFraction;
	}

	public double getExposure() {
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
		map.put("mask", requireMask.name());
		return map;
	}

}

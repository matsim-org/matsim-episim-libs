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

	public Restriction(double remainingFraction, double exposure, FaceMask requireMask) {
		this.remainingFraction = remainingFraction;
		this.exposure = exposure;
		this.requireMask = requireMask;
	}

	public static Restriction none() {
		return new Restriction(1d, 1d, FaceMask.NONE);
	}

	public static Restriction of(double remainingFraction) {
		return new Restriction(remainingFraction, 1d, FaceMask.NONE);
	}

	public static Restriction of(double remainingFraction, double exposure, FaceMask mask) {
		return new Restriction(remainingFraction, exposure, mask);
	}

	public static Restriction fromConfig(Config config) {
		return new Restriction(
				config.getDouble("fraction"),
				config.getDouble("exposure"),
				config.getEnum(FaceMask.class, "mask")
		);
	}

	@Override
	public String toString() {
		return String.valueOf(remainingFraction);
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
	}

	Map<String, Object> asMap() {
		Map<String, Object> map = new HashMap<>();
		map.put("fraction", remainingFraction);
		map.put("exposure", exposure);
		map.put("mask", requireMask.name());
		return map;
	}

}

package org.matsim.episim.policy;

import org.matsim.episim.model.FaceMask;

/**
 * Represent the current restrictions on an activity type.
 */
public final class Restriction {

    /**
     * Percentage of activities still performed.
     */
    private double remainingFraction = 1.;

    /**
     * Exposure during this activity.
     */
    private double exposure = 1.;

	/**
	 * Persons are required to wear a mask with this or more effective type.
	 */
	private FaceMask requireMask = FaceMask.NONE;

    private Restriction(double remainingFraction) {
        this.remainingFraction = remainingFraction;
    }

    public static Restriction newInstance() {
        return new Restriction(1d);
    }

    public static Restriction newInstance(double remainingFraction) {
        return new Restriction(remainingFraction);
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


	public void setRequireMask(FaceMask requireMask) {
		this.requireMask = requireMask;
	}

	public FaceMask getRequireMask() {
		return requireMask;
	}

	void fullShutdown() {
        remainingFraction = 0d;
    }

    void open() {
        remainingFraction = 1d;
    }

}

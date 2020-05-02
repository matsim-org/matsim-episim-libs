package org.matsim.episim.model;

/**
 * Type of face mask a person can wear, to decrease shedding rate, virus intake etc.
 */
public enum FaceMask {

	// Mask types need to be order by effectiveness
	NONE(1d, 1d),
	CLOTH(0.6, 0.5),
	SURGICAL(0.3, 0.2),
	N95(0.15, 0.025);

	public final double shedding;
	public final double intake;

	FaceMask(double shedding, double intake) {
		this.shedding = shedding;
		this.intake = intake;
	}
}

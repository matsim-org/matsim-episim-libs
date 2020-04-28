package org.matsim.episim.model;

/**
 * Type of face mask a person can wear, to decrease shedding rate, virus intake etc.
 */
public enum FaceMask {

	// Mask types need to be order by effectiveness
	NONE(1d, 1d),
	CLOTH(0.5, 0.6),
	SURGICAL(0.2, 0.3),
	N95(0.025, 0.15);

	public final double shedding;
	public final double intake;

	FaceMask(double shedding, double intake) {
		this.shedding = shedding;
		this.intake = intake;
	}
}

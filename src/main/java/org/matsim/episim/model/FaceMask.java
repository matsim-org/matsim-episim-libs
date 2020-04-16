package org.matsim.episim.model;

/**
 * Type of face mask a person can wear, to decrease shedding rate, virus intake etc.
 */
public enum FaceMask implements Comparable<FaceMask> {

	// Mask types should be order by effectiveness
	NONE,
	CLOTH,
	SURGICAL,
	FPP3,
}

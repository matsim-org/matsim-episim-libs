package org.matsim.episim.model;

/**
 * Enum for different types of vaccinations.
 */
public enum VaccinationType implements ImmunityEvent {

	generic,
	mRNA,
	vector,
	ba1Update,
	ba5Update,

	/**
	 * Not a real vaccination, but used to describe the profile for persons that have been infected and gained a natural immunity.
	 */
	natural,

	fall22,

	spring23,

	fall23,

	spring24,



}

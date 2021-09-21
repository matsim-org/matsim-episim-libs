package org.matsim.episim.model;

/**
 * Enum for different types of vaccinations.
 */
public enum VaccinationType {

	generic,
	mRNA,
	vector,

	/**
	 * Not a real vaccination, but used to describe the profile for persons that have been infected and gained a natural immunity.
	 */
	natural

}

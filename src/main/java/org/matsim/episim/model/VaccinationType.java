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

	fall24,

	spring25,

	fall25,

	spring26,

	fall26,

	spring27,

	fall27,

	spring28,

	fall28,

	spring29,

	fall29,

	spring30,

	fall30,

	spring31,

	fall31,

	spring32,

	vax_STRAIN_A,

	vax_STRAIN_B,

	vax_STRAIN_C,

	vax_STRAIN_D,

	vax_STRAIN_E,

	vax_STRAIN_F,

	vax_STRAIN_G,

	vax_STRAIN_H,

	vax_STRAIN_I,

	vax_STRAIN_J,

	vax_STRAIN_K,

	vax_STRAIN_L,

	vax_STRAIN_M,

	vax_STRAIN_N



}

package org.matsim.episim.model;

/**
 * Definition of virus strains with different characteristics.
 */
public enum VirusStrain implements ImmunityEvent {

	/**
	 * This describes the base virus strain.
	 */
	SARS_CoV_2,

	/**
	 * More "infectious" variant B.1.1.7 that has been prevalent in the UK, starting during end of 2020.
	 * Also known as VOC-202012/01.
	 */
//	B117,
	ALPHA,

	/**
	 * South-african variant also known as auch 501Y.V2.
	 */
	B1351,

	/**
	 * unknown mutation
	 */
//	MUTB,
	DELTA,

	/**
	 * VoC B.1.1.529, first reported to WHO from South Africa on 24 November 2021
	 */
//	OMICRON,
	OMICRON_BA1,

	OMICRON_BA2,

	OMICRON_BA5,

	STRAIN_A,

	STRAIN_B

}

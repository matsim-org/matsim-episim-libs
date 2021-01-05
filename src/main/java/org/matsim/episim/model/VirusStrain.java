package org.matsim.episim.model;

/**
 * Definition of virus strains with different characteristics.
 */
public enum VirusStrain {

	/**
	 * This describes the base virus strain.
	 */
	SARS_CoV_2 (1.0),

	/**
	 * More "infectious" variant B.1.1.7 that has been prevalent in the UK, starting during end of 2020.
	 * Also known as VOC-202012/01.
	 */
	B117 (1.56); // TODO

	/**
	 * Parameter controlling how infectious a strain is.
	 */
	public final double infectiousness;

	VirusStrain(double infectiousness) {
		this.infectiousness = infectiousness;
	}
}

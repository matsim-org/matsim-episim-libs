package org.matsim.episim.model;

/**
 * Definition of virus strains with different characteristics.
 */
public enum VirusStrain {

	/**
	 * This describes the base virus strain.
	 */
	SARS_CoV_2(1.0, 1.0),

	/**
	 * More "infectious" variant B.1.1.7 that has been prevalent in the UK, starting during end of 2020.
	 * Also known as VOC-202012/01.
	 */
	B117(2.0, 1.0),

	/**
	 * South-african variant also known as auch 501Y.V2.
	 */
	B1351(2.0, 0.0);
;
	/**
	 * Parameter controlling how infectious a strain is.
	 */
	public final double infectiousness;

	/**
	 * Effectiveness of vaccinations.
	 */
	public final double vaccineEffectiveness;

	VirusStrain(double infectiousness, double vaccineEffectiveness) {
		this.infectiousness = infectiousness;
		this.vaccineEffectiveness = vaccineEffectiveness;
	}
}

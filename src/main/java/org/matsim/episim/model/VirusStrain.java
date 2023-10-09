package org.matsim.episim.model;

/**
 * Definition of virus strains with different characteristics.
 */
public enum VirusStrain implements ImmunityEvent {
	/**
	 * This describes the base virus strain.
	 */
	SARS_CoV_2(null),

	/**
	 * More "infectious" variant B.1.1.7 that has been prevalent in the UK, starting during end of 2020.
	 * Also known as VOC-202012/01.
	 */
//	B117,
	ALPHA(SARS_CoV_2),

	/**
	 * South-african variant also known as auch 501Y.V2.
	 */
	B1351(SARS_CoV_2), //todo?

	/**
	 * unknown mutation
	 */
//	MUTB,
	DELTA(ALPHA),

	/**
	 * VoC B.1.1.529, first reported to WHO from South Africa on 24 November 2021
	 */
//	OMICRON,
	OMICRON_BA1(DELTA),

	OMICRON_BA2(OMICRON_BA1),

	OMICRON_BA5(OMICRON_BA2),

	XBB_15(OMICRON_BA2),

	XBB_19(OMICRON_BA2),

	BQ(OMICRON_BA5),

	EG(XBB_19),

	STRAIN_A(OMICRON_BA5),

	STRAIN_B(OMICRON_BA5),

	A_1(EG),

	A_2(A_1),

	A_3(A_2),

	A_4(A_3),

	A_5(A_4),

	A_6(A_5),

	A_7(A_6),

	A_8(A_7),

	A_9(A_8),

	A_10(A_9),

	A_11(A_10),

	A_12(A_11),

	A_13(A_12),

	A_14(A_13),

	A_15(A_14),

	A_16(A_15),

	A_17(A_16),

	A_18(A_17),

	A_19(A_18),

	A_20(A_19),

	B_1(null),

	B_2(null),

	B_3(null),

	B_4(null),

	B_5(null),

	B_6(null),

	B_7(null),

	B_8(null),

	B_9(null),

	B_10(null),

	B_11(null),

	B_12(null),

	B_13(null),

	B_14(null),

	B_15(null),

	B_16(null),

	B_17(null),

	B_18(null),

	B_19(null),

	B_20(null);

	public final VirusStrain parent;

	VirusStrain(VirusStrain parent) {
		this.parent = parent;
	}


}

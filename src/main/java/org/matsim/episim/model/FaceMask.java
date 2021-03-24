package org.matsim.episim.model;

/**
 * Type of face mask a person can wear, to decrease shedding rate, virus intake etc.
 */
public enum FaceMask {

	// Mask types need to be order by effectiveness
	// Values based on Eikenberry et al. https://arxiv.org/pdf/2004.03251.pdf, chapter 2.3 
	NONE(1d, 1d),
//	CLOTH(0.6, 0.5),
//	SURGICAL(0.3, 0.2),
//	N95(0.15, 0.025);
	
	// values based on Kriegel
	CLOTH(0.8, 0.7),
	SURGICAL(0.8, 0.7),
	N95(0.6, 0.2);
	
	// http://dx.doi.org/10.1016/S0140-6736(20)31142-9 is good, but difficult to translate into what we need.  It only treats the intake side.  I first
	// take from wikipedia
	// RR ≈ OR / ( 1 - RC + (RC * OR) )
	//with RR relative risk, OR odds ratio, RC base risk.  Base risk is reported as 17.4% ("no face mask").  With this:
	//
	//across all masks: OR=0.15, RR = 0.176
	//
	//surgical/cloth masks: OR=0.33, RR = 0.37
	//
	//N95 masks: OR=0.04, RR = 0.048
	//
	//The values for N95 vs. surgical/cloth are hidden in some footnote of some table.
	//
	// This gives support for the numbers we use, but it is not very readable.

	public final double shedding;
	public final double intake;

	FaceMask(double shedding, double intake) {
		this.shedding = shedding;
		this.intake = intake;
	}
}

package org.matsim.episim.analysis;

import java.util.Locale;

class KNTestDivisions{

	public static void main( String[] args ){

		double sum = 0.;
		double sumZaehler = 0.;
		double sumNenner = 0.;
		double cnt = 0.;
		for ( int ii=0 ; ii<1000000; ii++ ) {
			cnt++;
			double offset = 0.4;
			double alpha=0.5;
//			final double zaehler = offset+alpha*Math.random();
			final double zaehler = offset + alpha * 0.5;
			final double nenner = offset+alpha*Math.random();
			sum += zaehler / nenner;
			sumZaehler += zaehler;
			sumNenner += nenner;
		}
		System.out.printf( Locale.ENGLISH, "%f | %f\n",  sum/cnt -1., sumZaehler/sumNenner-1. );

	}

}

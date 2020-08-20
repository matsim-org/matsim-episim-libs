package org.matsim.episim.analysis;

import org.apache.commons.collections.map.TransformedSortedMap;
import org.matsim.core.router.Transit;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.Transition;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.columns.Column;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.components.*;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.plotly.traces.Trace;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.file.Files.createFile;

class KNTestLognormal{

	public static void main( String[] args ) throws IOException{

		double alpha = Math.exp(5-1);
		double median = 1.;
		double mu = Math.log( median );
		double sigma = 3.;
//		Transition abc = Transition.logNormalWithMedianAndSigma( Math.exp(mu), sigma );
		Transition abc = Transition.logNormalWithMedianAndStd( 3., 6. );
//		Transition abc = Transition.logNormalWithMean( 10., 8. );
//		Transition def = Transition.logNormalWithMedianAndStd( 2, 2 );
		SplittableRandom rnd = new SplittableRandom();

		NavigableMap<Integer,Double> map = new TreeMap<>();

		final int nCases = 10000;
		for ( int ii = 0 ; ii< nCases ; ii++ ){
//			int result = abc.getTransitionDay( rnd ) + def.getTransitionDay( rnd );
			int result = abc.getTransitionDay( rnd ) ;
			Double prev = map.get( result );
			if ( prev==null ) {
				map.put( result, 1.);
			} else {
				map.put( result, prev+1 );
			}
		}
		List<Integer> toRemove = new ArrayList<>();
		double sum = 0.;
		for( Map.Entry<Integer, Double> entry : map.descendingMap().entrySet() ){
			sum += entry.getValue();
			if ( sum < nCases*(1.-0.95)/2 ) {
//			if ( sum2 < nCases*0.25 ) {
				toRemove.add(entry.getKey());
			}
		}
		for( Integer key : toRemove ){
			map.remove( key );
		}

		Column<Double> xColumn = DoubleColumn.create( "x", map.keySet() );
		Column<Double> yColumn = DoubleColumn.create( "y", map.values() );

		Trace trace = ScatterTrace.builder( xColumn, yColumn ).name( yColumn.name() ).build();

		Trace dataTrace = null;
		{

			final DoubleColumn xColumnIqr = DoubleColumn.create( "x");
			final DoubleColumn yColumnIqr = DoubleColumn.create( "y" );
			double sum2 = 0.;
			for( Map.Entry<Integer, Double> entry : map.entrySet() ){
				sum2 += entry.getValue();
				if ( sum2 > 0.25*nCases && sum2 < 0.75*nCases ) {
					xColumnIqr.append( entry.getKey() );
					yColumnIqr.append( 0. );
				}
			}
			dataTrace = ScatterTrace.builder( xColumnIqr, yColumnIqr ).name( "IQR" ).build();
		}

//		{
//			SortedMap<Double,Double> data = new TreeMap<>();
//			data.put(1.,2.);
//			data.put(2.,1.);
//			data.put(3.,3.);
//			data.put(7.,2.);
//			data.put(14.,2.);
//			final DoubleColumn xData = DoubleColumn.create( "x", data.keySet() );
//			final DoubleColumn yData = DoubleColumn.create( "y", data.values() );
//			System.out.println( yData.print() );
//			DoubleColumn yData2 = yData.multiply( 1000. );
//			System.out.println( yData.print() );
//			dataTrace = ScatterTrace.builder( xData, yData2 ).name( "data" ).build();
//		}

		Figure fig = new Figure(trace,dataTrace);

		Axis yAxis = Axis.builder().type( Axis.Type.LINEAR).build();
		Layout layout = Layout.builder().width( 800 ).height(500 ).yAxis( yAxis ).build();
		fig.setLayout( layout );

		Plot.show(fig,"divname" , new File("output1.html") );
//		Plot.show(fig,"divname" , new File("output2.html") );
	}

}

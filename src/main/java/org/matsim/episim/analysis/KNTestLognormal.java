package org.matsim.episim.analysis;

import org.matsim.episim.model.Transition;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.columns.Column;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.components.*;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.plotly.traces.Trace;

import java.io.IOException;
import java.util.*;

class KNTestLognormal{

	public static void main( String[] args ) throws IOException{

		double alpha = Math.exp(5-1);
//		Transition abc = Transition.logNormalWithMedianAndStd( 10., 8. );
		Transition abc = Transition.logNormalWithMean( 10., 8. );
//		Transition def = Transition.logNormalWithMedianAndStd( 2., alpha*2 );
		SplittableRandom rnd = new SplittableRandom();

		NavigableMap<Integer,Double> map = new TreeMap<>();

		final int nCases = 10000;
		for ( int ii = 0 ; ii< nCases ; ii++ ){
			int result = abc.getTransitionDay( rnd ) ;//+ def.getTransitionDay( rnd );
			Double prev = map.get( result );
			if ( prev==null ) {
				map.put( result, 1.);
			} else {
				map.put( result, prev+1 );
			}
		}
		List<Integer> toRemove = new ArrayList<>();
//		double sum = 0.;
//		for( Map.Entry<Integer, Double> entry : map.entrySet() ){
//			sum += entry.getValue();
//			if ( sum < nCases*(1.-0.65)/2 ) {
//				toRemove.add(entry.getKey());
//			}
//		}
		double sum2 = 0.;
		for( Map.Entry<Integer, Double> entry : map.descendingMap().entrySet() ){
			sum2 += entry.getValue();
			if ( sum2 < nCases*(1.-0.65)/2 ) {
				toRemove.add(entry.getKey());
			}
		}
		for( Integer key : toRemove ){
			map.remove( key );
		}

		Column<Double> xColumn = DoubleColumn.create( "x", map.keySet() );
		Column<Double> yColumn = DoubleColumn.create( "y", map.values() );

		Trace trace = ScatterTrace.builder( xColumn, yColumn ).name( yColumn.name() ).build();

		Figure fig = new Figure(trace);

		Axis yAxis = Axis.builder().type( Axis.Type.LINEAR).build();
		Layout layout = Layout.builder().width( 800 ).height(500 ).yAxis( yAxis ).build();
		fig.setLayout( layout );

		Plot.show(fig,"divname" );
	}

}

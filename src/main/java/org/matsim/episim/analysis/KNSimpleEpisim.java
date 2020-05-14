package org.matsim.episim.analysis;

import org.apache.log4j.Logger;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.columns.Column;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.plotly.traces.Trace;

import java.util.ArrayList;
import java.util.List;

class KNSimpleEpisim{
	private static final Logger log = Logger.getLogger( KNSimpleEpisim.class );

	public static void main( String[] args ){

		double infected = 1 ;
		double theta = 0.4;
		List<Double> days = new ArrayList<>();
		List<Double> infecteds = new ArrayList<>();
		List<Double> infectedsCumulative = new ArrayList<>();
		List<Double> newInfectionsList = new ArrayList<>();
		infecteds.add( infected );
		infectedsCumulative.add( infected );
		newInfectionsList.add( infected );
		days.add(-1.);
		double infectedCumulative = infected;
		for ( int day=0 ; day<100 ; day++ ) {
			if ( day>=25 && day <=35 ) {
				theta -= 0.03;
			}
			if ( day==30 ) {
				theta -= 0.;
//			} else if ( day==60 ) {
//				theta = 0.1;
			}
			double newInfections = theta * infected;
			newInfectionsList.add( newInfections );
			infected += newInfections;
			infectedCumulative += newInfections;
			if ( day >=7 ) {
				infected -= newInfectionsList.get(day-7);
			}
			infecteds.add( infected );
			infectedsCumulative.add( infectedCumulative );
			days.add( 1.*day );

//			log.info("day=" + day + "; nInfected=" + infected );
		}

		log.info( infectedsCumulative );

		Column<Double> yColumn = DoubleColumn.create( "infectedCumulative", infectedsCumulative );
		Column<Double> xColumn = DoubleColumn.create( "day", days );

		DoubleColumn diffColumn = DoubleColumn.create( "newlyInfected", infectedsCumulative ).difference();

		List<String> daysAsString = new ArrayList<>();
		for( Double day : days ){
			daysAsString.add( Double.toString( day ) );
		}
		StringColumn xColumnAsString = StringColumn.create( "day", daysAsString );

		Trace trace = ScatterTrace.builder( xColumn, yColumn ).name( yColumn.name() ).build();
		Trace trace2 = BarTrace.builder( xColumnAsString, diffColumn ).name( diffColumn.name() ).build();

		Figure fig = new Figure(trace, trace2);

		Axis yAxis = Axis.builder().type( Axis.Type.LOG).build();
		Layout layout = Layout.builder().width( 800 ).height(500 ).yAxis( yAxis ).build();
		fig.setLayout( layout );

		Plot.show(fig,"divname" );


	}

}

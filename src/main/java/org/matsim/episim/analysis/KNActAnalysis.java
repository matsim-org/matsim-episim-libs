package org.matsim.episim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.scenario.ScenarioUtils;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.joining.DataFrameJoiner;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.api.VerticalBarPlot;
import tech.tablesaw.plotly.components.*;
import tech.tablesaw.plotly.components.Layout.BarMode;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.TreeMap;

class KNActAnalysis{
	private static final Logger log = Logger.getLogger( KNActAnalysis.class );

	private static final String base = "piecewise__theta2.8E-6__offset-5__work_0.75_0.45__leis_0.7_0.1__eduLower_0.1__eduHigher_0.0__other0.2/";

	public static void main( String[] args ) throws IOException{

		Table table2;
		final String yName = "frequency";
		final String xName = "infectionType";
		{
			Map<String,Double> freq = new TreeMap<>();
			Reader in = new FileReader( base + "infectionEvents.txt" );
			Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter( '\t' ).parse( in );
			for( CSVRecord record : records ){
				String xType = record.get( xName );
				Double sum = freq.computeIfAbsent( xType, ( a ) -> 0. );
				freq.put( xType, sum + 1 );
			}

			final StringColumn xData = StringColumn.create( xName, freq.keySet() );
			final DoubleColumn yData = DoubleColumn.create( yName, freq.values() );

			table2 = Table.create("table" ).addColumns( xData, yData );
		}

		// ---


		Figure fig = VerticalBarPlot.create( "title", table2, xName, BarMode.GROUP, yName );

		Axis xAxis = Axis.builder().font( Font.builder().family( Font.Family.ARIAL ).size( 5 ).color( "red" ).build() ).build();
		double scale = 1.5;
		Layout layout = Layout.builder()
				      .width( (int) (scale*800) )
				      .height( (int) (scale*400) )
				      .autosize( true )
				      .margin( Margin.builder().bottom( 200 ).build() )
				      .title( "Activity types at which infections occur" )
				      .xAxis( xAxis )
				      .build();
		fig.setLayout( layout );

		Plot.show(fig,"divname" );
	}
}

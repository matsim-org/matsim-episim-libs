package org.matsim.episim.analysis;

import com.google.common.math.IntMath;
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
import tech.tablesaw.api.NumberColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.api.ScatterPlot;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.plotly.traces.Trace;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

import static org.apache.commons.math3.special.Gamma.gamma;

class KNClusterAnalysis{
	private static final Logger log = Logger.getLogger( KNClusterAnalysis.class );

	public static void main( String[] args ) throws IOException{
//		Config config = ConfigUtils.createConfig();
//		config.plans().setInputFile( "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz");
//		Scenario scenario = ScenarioUtils.loadScenario( config );

		final String base;

//		base = "2020-06-01-18:38:53__unrestr__theta2.3E-6@3__pWSymp0.0__infectedBNC3.0_3.0__contag1.5_1.5__sShed0.0__sSusc0.0_startDate2020-02-15/";
		// useful base case with mxIA=3 (I think)

//		base = "2020-06-01_21-02-43__unrestr__theta6.9E-7@10__pWSymp0.0__infectedBNC3.0_3.0__contag1.5_1.5__sShed0.0__sSusc0.0_startDate2020-02-15/";
		// useful base case with mxIA=10 (I think)

//		base = "2020-06-01-14:34:34_unrestr_theta1.0E-7__pWSymp0.0__infectedBNC3.0_3.0__contag1.5_1.5__sShed0.0__sSusc0.0_startDate2020-02-15/";
		// useful base case with mxIA=100 (I think)

		base = "output/zz_archive-2020-06-04/2020-06-01_23-40-52__unrestr__theta1.0E-6@10__pWSymp0.0__infectedBNC3.0_3.0__contag1.5_1.5__sInfct1" +
				       ".0__sSusc0" +
				       ".0_startDate2020-02-15/";
		// useful base cased with mxIA=10 & sInfct=1

		// ---
		// for each infector, find out how many others she infected:
		double sumOfInfections = 0.;
		Map<String,Double> infectors = new LinkedHashMap<>();
		Reader in = new FileReader( base + "infectionEvents.txt" );
		Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter( '\t' ).parse( in );
		for( CSVRecord record : records ){
			if ( record.get("date").compareTo("2020-04-01")<0){
				sumOfInfections++;
				infectors.putIfAbsent( record.get( "infected" ), 0. ); // we count infected also as possible infectors!
				double sum = infectors.computeIfAbsent( record.get( "infector" ), a -> 0. );
				infectors.put( record.get( "infector" ), sum + 1 );
			} else {
				// we only count out people we already have!
				Double sum = infectors.get( record.get( "infector" ) );
				if ( sum != null ) {
					sumOfInfections++;
					infectors.put( record.get("infector"), sum+1 );
				}
			}
		}

		// ---
		// compute R0
		double cnt = 0.;
		double sum2 = 0.;
		for( Double value : infectors.values() ){
			cnt++;
			sum2 += value ;
		}
		log.info( "average reinfection rate =" + sum2/cnt );

		// ---
		// get distribution on secondary infection numbers:
		double sumOfInfectors = 0. ;
		NavigableMap<Double,Double> distribution = new TreeMap<>();
		for( Map.Entry<String, Double> entry : infectors.entrySet() ){
			sumOfInfectors++ ;
			Double sum = distribution.computeIfAbsent( entry.getValue(), a -> 0. );
			sum++ ;
			distribution.put( entry.getValue(), sum );
		}

		// ---
		// go up from small secondary infection numbers and stop when 20% was reached:
		double twentyPercent = sumOfInfections * 0.2 ;
		double ssumOfInfectors = 0.;
		double ssumOfInfections = 0.;
		double twentyPercentXxValue = Double.NaN;

		for( Map.Entry<Double, Double> entry : distribution.descendingMap().entrySet() ){
			ssumOfInfections += entry.getKey()*entry.getValue();
			ssumOfInfectors += entry.getValue();
			log.info( (int)(ssumOfInfectors/ infectors.size() *100) + " % of infectors caused " + (int)(ssumOfInfections/sumOfInfections*100 )+ " %" +
						  " of infections.");

			if ( ssumOfInfections > twentyPercent && Double.isNaN( twentyPercentXxValue )) {
				twentyPercentXxValue = entry.getKey();
//				break;
			}
		}

		List<Double> gammaVals = new ArrayList<>();
		List<Double> gammaVals2 = new ArrayList<>();
		List<Double> gammaVals3 = new ArrayList<>();
		final double R0=3.22;
		final double kappa = 0.3; // smaller = more curved
		final double normalization = 0.5*infectors.size();
		{
			final double theta = 1. + R0 / kappa;
			final double k = R0 / theta;
			for( Map.Entry<Double, Double> entry : distribution.entrySet() ){
				final double val =
						normalization / gamma( k ) / Math.pow( theta, k ) * Math.pow( entry.getKey(), k - 1. ) * Math.exp( -entry.getKey() / theta );
//				log.info( "nReinfected=" + entry.getKey() + "; gamma=" + val );
				gammaVals.add( val );
			}
		}
		{
			final double theta = 1. + R0 / (kappa/1.5);
			final double k = R0 / theta;
			for( Map.Entry<Double, Double> entry : distribution.entrySet() ){
				gammaVals2.add( normalization / gamma( k ) / Math.pow( theta, k ) * Math.pow( entry.getKey(), k - 1. ) * Math.exp(
						-entry.getKey() / theta ) );
			}
		}
		{
			final double theta = 1. + R0 / (kappa*1.5);
			final double k = R0 / theta;
			for( Map.Entry<Double, Double> entry : distribution.entrySet() ){
				gammaVals3.add( normalization / gamma( k ) / Math.pow( theta, k ) * Math.pow( entry.getKey(), k - 1. ) * Math.exp( -entry.getKey() / theta ) );
			}
		}
		// yyyy might be better to plot CDF


		// ---

		DoubleColumn xx = DoubleColumn.create( "xx", distribution.keySet() );
		DoubleColumn yy = DoubleColumn.create( "yy", distribution.values() );
		Trace trace = ScatterTrace.builder( xx, yy ).name( yy.name() ).build();

		DoubleColumn aa = DoubleColumn.create( "aa", new Double[]{twentyPercentXxValue,twentyPercentXxValue} );
		DoubleColumn bb = DoubleColumn.create( "bb", new Double[]{1.,10.} );
		Trace lineTrace = ScatterTrace.builder( aa, bb ).mode( ScatterTrace.Mode.LINE ).build();

		Trace gammaTrace =
				ScatterTrace.builder( xx, DoubleColumn.create( "gamma", gammaVals ) ).mode( ScatterTrace.Mode.LINE ).name( Double.toString( kappa ) ).build();
		Trace gamma2Trace =
				ScatterTrace.builder( xx, DoubleColumn.create( "gamma2", gammaVals2 ) ).mode( ScatterTrace.Mode.LINE ).name( "smaller" ).build();
		Trace gamma3Trace =
				ScatterTrace.builder( xx, DoubleColumn.create( "gamma3", gammaVals3 ) ).mode( ScatterTrace.Mode.LINE ).name("larger").build();

		Figure figure = Figure.builder().addTraces( trace, lineTrace, gammaTrace, gamma2Trace, gamma3Trace ).build();

		figure.setLayout( Layout.builder().yAxis( Axis.builder().type( Axis.Type.LOG ).range( Math.log10(0.1),Math.log10(1_000_000) ).build() ).build() );

		Plot.show(figure, "dada", new File("output.html") );

	}

}

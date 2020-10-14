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
		final double kappa; // smaller = more dispersed
		final double R0 = 3.;

//		base = "2020-06-01-18:38:53__unrestr__theta2.3E-6@3__pWSymp0.0__infectedBNC3.0_3.0__contag1.5_1.5__sShed0.0__sSusc0.0_startDate2020-02-15/";
		// useful base case with mxIA=3 (I think)

//		base = "2020-06-01_21-02-43__unrestr__theta6.9E-7@10__pWSymp0.0__infectedBNC3.0_3.0__contag1.5_1.5__sShed0.0__sSusc0.0_startDate2020-02-15/";
		// useful base case with mxIA=10 (I think)

//		base = "2020-06-01-14:34:34_unrestr_theta1.0E-7__pWSymp0.0__infectedBNC3.0_3.0__contag1.5_1.5__sShed0.0__sSusc0.0_startDate2020-02-15/";
		// useful base case with mxIA=100 (I think)

//		base = "output/zz_archive-2020-06-04/2020-06-01_23-40-52__unrestr__theta1.0E-6@10__pWSymp0.0__infectedBNC3.0_3.0__contag1.5_1.5__sInfct1.0__sSusc0.0_startDate2020-02-15/";
		// useful base cased with mxIA=10 & sInfct=1

//		base = "output/2020-08-30_13-35-54__original__unrestr__theta1.0E-5@3.0__trStrt46_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}/";
		base = "output/2020-08-30_18-38-38__original__unrestr__theta3.0000000000000004E-5@1.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/"; kappa=10.;
//		base = "output/2020-08-30_18-37-36__original__unrestr__theta3.3333333333333337E-6@9.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/";
//		base = "output/2020-08-30_18-37-49__original__unrestr__theta1.1111111111111112E-6@27.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/";
//		base = "output/2020-08-30_18-38-00__original__unrestr__theta3.703703703703704E-7@81.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/";

//		base = "output/2020-08-30_19-46-37__original__unrestr__theta8.100000000000001E-4@1.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/";

//		base = "output/2020-08-30_22-56-42__original__unrestr__theta1.1111111111111112E-6@81.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/";
//		kappa = 2.; // smaller = more curved

//		base = "output/2020-08-30_22-56-52__original__unrestr__theta3.3333333333333337E-6@81.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/";
//		kappa = 0.5;

//		base = "output/2020-08-30_22-57-01__original__unrestr__theta1.0E-5@81.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/"; kappa = 0.25;

//		base = "output/2020-08-31_08-37-22__original__unrestr__theta1.1111111111111112E-6@2187.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/"; kappa = 0.01;

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
		NavigableMap<Double,Double> distribution = new TreeMap<>();
		for( Map.Entry<String, Double> entry : infectors.entrySet() ){
			Double sum = distribution.computeIfAbsent( entry.getValue(), a -> 0. );
			sum++ ;
			distribution.put( entry.getValue(), sum );
		}
		{
			Double infectorsWithZeroInfections = distribution.remove( 0. );
			distribution.put( 0.01, infectorsWithZeroInfections);
			// so we get a value for the gamma distribution close to zero (?)
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
		final double normalization = 0.2*infectors.size();
		final double thetaBase = 1. + R0 / kappa;
		final double kBase = R0 / thetaBase;
		{
			for( Map.Entry<Double, Double> entry : distribution.entrySet() ){
				gammaVals.add( normalization / gamma( kBase ) / Math.pow( thetaBase, kBase ) * Math.pow( entry.getKey(), kBase - 1. ) * Math.exp( -entry.getKey() / thetaBase ) );
			}
		}
		final double thetaTwo = 1. + R0 / (kappa/1.5);
		final double kTwo = R0 / thetaTwo;
		{
			for( Map.Entry<Double, Double> entry : distribution.entrySet() ){
				gammaVals2.add( normalization / gamma( kTwo ) / Math.pow( thetaTwo, kTwo ) * Math.pow( entry.getKey(), kTwo - 1. ) * Math.exp( -entry.getKey() / thetaTwo ) );
			}
		}
		final double thetaThree = 1. + R0 / (kappa*1.5);
		final double kThree = R0 / thetaThree;
		{
			for( Map.Entry<Double, Double> entry : distribution.entrySet() ){
				gammaVals3.add( normalization / gamma( kThree ) / Math.pow( thetaThree, kThree ) * Math.pow( entry.getKey(), kThree - 1. ) * Math.exp( -entry.getKey() / thetaThree ) );
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
				ScatterTrace.builder( xx, DoubleColumn.create( "gamma", gammaVals ) ).mode( ScatterTrace.Mode.LINE ).name(
						"kappa=" + kappa + "; k=" + Math.round(100.*kBase)/100. ).build();
		Trace gamma2Trace =
				ScatterTrace.builder( xx, DoubleColumn.create( "gamma2", gammaVals2 ) ).mode( ScatterTrace.Mode.LINE ).name(
						"k=" + Math.round(100.*kTwo)/100. ).build();
		Trace gamma3Trace =
				ScatterTrace.builder( xx, DoubleColumn.create( "gamma3", gammaVals3 ) ).mode( ScatterTrace.Mode.LINE ).name(
						"k=" + Math.round( 100.*kThree )/100. ).build();

		Figure figure = Figure.builder().addTraces( trace, lineTrace, gammaTrace, gamma2Trace, gamma3Trace ).build();

		figure.setLayout( Layout.builder()
					.yAxis( Axis.builder().type( Axis.Type.LOG ).range( Math.log10(0.1),Math.log10(1_000_000) ).build() )
					.xAxis( Axis.builder().type( Axis.Type.LOG ).range( Math.log10(0.01),Math.log10(100) ).build() )
				.build() );

		Plot.show(figure, "dada", new File("output.html") );

	}

}

package org.matsim.episim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import tech.tablesaw.api.*;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.plotly.traces.Trace;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

class KNInfectionsAnalysis{
	private static final Logger log = Logger.getLogger( KNInfectionsAnalysis.class );

//		private static final String base = "../shared-svn/projects/episim/matsim-files/bmbf6/20200615-runs/runs/tracing-30-SchoolsAfterSummer/";
//		private static final String policy = "../shared-svn/projects/episim/matsim-files/bmbf6/20200615-runs/runs/tracing-30-noSchools/";

//	private static final String base = "../shared-svn/projects/episim/matsim-files/bmbf6/20200615-runs/runs/tracing-30-noSchools/";
//	private static final String policy = "../shared-svn/projects/episim/matsim-files/bmbf6/20200615-runs/runs/tracing-inf-noSchools/";

	private static final String baseDir = "/Users/kainagel/git/all-matsim/episim-matsim/output/";

	// --- base rnd
//	private static final String base = baseDir + "2020-06-28_13-24-11__unrestr__theta1.1E-5@3__trStrt46_seed4711_strtDt2020-02-16_trCap{1970-01-01=0}/";
//	private static final String policy = baseDir + "2020-06-28_15-25-40__unrestr__theta1.1E-5@3__trStrt46_seed4711_strtDt2020-02-16_trCap{1970-01-01=0}/";

	// ciEdu = 10.
//	private static final String base = baseDir + "2020-06-28_14-02-44__unrestr__theta1.1E-5@3__trStrt46_seed4711_strtDt2020-02-16_trCap{1970-01-01=0}/";


	// --- 4713

//	private static final String base = baseDir + "2020-06-28_16-21-56__unrestr__theta1.1E-5@3__trStrt46_seed4713_strtDt2020-02-16_trCap{1970-01-01=0}/";
//	private static final String policy = baseDir + "2020-06-28_16-22-05__unrestr__theta1.1E-5@3__trStrt46_seed4713_strtDt2020-02-16_trCap{1970-01-01=0}/";

	// 4715

//	private static final String base = baseDir + "2020-06-28_16-32-39__unrestr__theta1.1E-5@3__trStrt46__ciEdu1.0_seed4715_strtDt2020-02-16_trCap{1970-01-01=0}/";
//	private static final String policy = baseDir + "2020-06-28_17-03-04__unrestr__theta1.1E-5@3__trStrt46__ciEdu0.0_seed4715_strtDt2020-02-16_trCap{1970-01-01=0}/";

	// 4717

//	private static final String base = baseDir + "2020-06-28_17-01-20__unrestr__theta1.1E-5@3__trStrt46__ciEdu1.0_seed4717_strtDt2020-02-16_trCap{1970-01-01=0}/";
//	private static final String policy = baseDir + "2020-06-28_17-01-30__unrestr__theta1.1E-5@3__trStrt46__ciEdu0.0_seed4717_strtDt2020-02-16_trCap{1970-01-01=0}/";

	// 4719

	private static final String base = baseDir + "2020-06-28_17-01-46__unrestr__theta1.1E-5@3__trStrt46__ciEdu1.0_seed4719_strtDt2020-02-16_trCap{1970-01-01=0}/";
	private static final String policy = baseDir + "2020-06-28_17-02-03__unrestr__theta1.1E-5@3__trStrt46__ciEdu0.0_seed4719_strtDt2020-02-16_trCap{1970-01-01=0}/";

	private static final int windowSize = 14;
	private static final int avPeriod = 14;

	private static class PersonInfo{
		private final LocalDate infectionDay;
		private final List<LocalDate> infections = new ArrayList<>();
		PersonInfo( LocalDate infectionDay ) {
			this.infectionDay = infectionDay;
		}
		void addInfection( LocalDate day ) {
			infections.add( day );
		}
		LocalDate getInfectionDay(){
			return infectionDay;
		}
	}

	public static void main( String[] args ) throws IOException{
//		Config config = ConfigUtils.createConfig();
//		config.plans().setInputFile( "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz");
//		Scenario scenario = ScenarioUtils.loadScenario( config );

//		for ( Iterator<? extends Person> it = scenario.getPopulation().getPersons().values().iterator() ; it.hasNext() ; ) {
//			Person person = it.next();
//			final String district = (String) person.getAttributes().getAttribute( "district" );
//			if ( district==null || !district.equals( "Berlin" ) ) {
//				it.remove();
//			}
//		}

		// ===
		DateColumn dateColumn;
		Trace infectionsBase;
		DoubleColumn rBase;
		double sumReinfectionsBase;
		{
			// memorize for each infected person the reinfections:
			Map<String, PersonInfo> map = new LinkedHashMap<>();
			{
				Reader in = new FileReader( base + "infectionEvents.txt" );
				Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter( '\t' ).parse( in );
				for( CSVRecord record : records ){
					LocalDate date = LocalDate.parse( record.get( "date" ) );
					map.put( record.get( "infected" ), new PersonInfo( date ) );

					PersonInfo info = map.get( record.get( "infector" ) );
					if( info != null ){
						info.addInfection( date );
					}
				}
			}

			// compute average reinfections per person infected on day X:
			Map<LocalDate, Double> sums = new TreeMap<>();
			Map<LocalDate, Double> cnts = new TreeMap<>();
			{
				for( PersonInfo personInfo : map.values() ){
					Double sum = sums.get( personInfo.getInfectionDay() );
					Double cnt = cnts.get( personInfo.getInfectionDay() );
					if( sum == null ){
						sums.put( personInfo.getInfectionDay(), (double) personInfo.infections.size() );
						cnts.put( personInfo.getInfectionDay(), 1. );
					} else{
						sums.put( personInfo.getInfectionDay(), sum + personInfo.infections.size() );
						cnts.put( personInfo.getInfectionDay(), cnt + 1 );
					}
				}
			}

			// fill up the columns so there are no gaps:
			for ( int ii=0 ; ii<=60; ii++ ) {
				LocalDate date = LocalDate.of( 2020, 2, 15).plusDays( ii );
				sums.putIfAbsent( date, Double.NaN );
				cnts.putIfAbsent( date, Double.NaN );
			}


			dateColumn = DateColumn.create( "day", sums.keySet() );
			DoubleColumn sss = DoubleColumn.create( "sss", sums.values() );
			DoubleColumn infections = DoubleColumn.create( "ccc", cnts.values() );
			rBase = sss.divide( infections ). rolling( windowSize ).mean();
//			trace = ScatterTrace.builder( xx2,yy2 ).name("rolling").build();
			infectionsBase = ScatterTrace.builder( dateColumn, infections ).build();
			{
				sumReinfectionsBase = 0;
				double cnt = 0;
				for( int ii = 0 ; ii < avPeriod ; ii++ ){
					LocalDate date = LocalDate.of( 2020, 3, 1 ).plusDays( ii );
					final Double reinfections3 = sums.get( date );
//					log.info( "policy case; date=" + date + "; reinfections=" + reinfections3 );
					sumReinfectionsBase += reinfections3;
					cnt += cnts.get(date);
				}
				sumReinfectionsBase /= cnt;
			}
		}
		// ===
		DoubleColumn rPolicy;
		Trace infectionsPolicy;
		double sumReinfectionsPolicy;
		{
			Map<String, PersonInfo> map2 = new LinkedHashMap<>();
			{
				Reader in = new FileReader( policy + "infectionEvents.txt" );
				Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter( '\t' ).parse( in );
				for( CSVRecord record : records ){
					LocalDate date = LocalDate.parse( record.get( "date" ) );
					map2.put( record.get( "infected" ), new PersonInfo( date ) );

					PersonInfo info = map2.get( record.get( "infector" ) );
					if( info != null ){
						info.addInfection( date );
					}
				}
			}
			Map<LocalDate, Double> sums = new TreeMap<>();
			Map<LocalDate, Double> cnts = new TreeMap<>();
			{
				for( PersonInfo personInfo : map2.values() ){
					Double sum = sums.get( personInfo.getInfectionDay() );
					Double cnt = cnts.get( personInfo.getInfectionDay() );
					if( sum == null ){
						sums.put( personInfo.getInfectionDay(), (double) personInfo.infections.size() );
						cnts.put( personInfo.getInfectionDay(), 1. );
					} else{
						sums.put( personInfo.getInfectionDay(), sum + personInfo.infections.size() );
						cnts.put( personInfo.getInfectionDay(), cnt + 1 );
					}
				}
			}
			for ( int ii=0 ; ii<=60; ii++ ) {
				LocalDate date = LocalDate.of( 2020, 2, 15).plusDays( ii );
				sums.putIfAbsent( date, Double.NaN );
				cnts.putIfAbsent( date, Double.NaN );
			}

			DateColumn xx2 = DateColumn.create( "day", sums.keySet() );
			DoubleColumn reinfections = DoubleColumn.create( "sss", sums.values() );
			DoubleColumn infections = DoubleColumn.create( "ccc", cnts.values() );
			rPolicy = reinfections.divide( infections ).rolling( windowSize ).mean();
//			trace = ScatterTrace.builder( xx2,yy2 ).name("rolling").build();
			infectionsPolicy = ScatterTrace.builder( dateColumn, infections ).build();

			{
				sumReinfectionsPolicy = 0;
				double cnt = 0.;
				for( int ii = 0 ; ii < avPeriod ; ii++ ){
					LocalDate date = LocalDate.of( 2020, 3, 1 ).plusDays( ii );
					final Double reinfections2 = sums.get( date );
//				log.info( "policy case; date=" + date + "; reinfections=" + reinfections2);
					sumReinfectionsPolicy += reinfections2;
					cnt += cnts.get(date);
				}
				sumReinfectionsPolicy /= cnt;
			}
		}

		log.info( "policy/base=" + (sumReinfectionsPolicy/sumReinfectionsBase));

		final DoubleColumn policyDivBase = rPolicy.divide( rBase );

		int index = dateColumn.indexOf( LocalDate.of( 2020, 3, 1 ) );
		log.info("index=" + index);
		{
			double sum = 0.;
			for( int ii = 0 ; ii < avPeriod ; ii++ ){
				sum += policyDivBase.get(index+ii);
			}
			log.info( "policy/base =" + (sum/avPeriod) );
		}



		Trace rBaseTrace = ScatterTrace.builder( dateColumn, rBase ).build();
		Trace rPolicyTrace = ScatterTrace.builder( dateColumn, rPolicy ).build();

		Trace rPolDivRBaseTrace = ScatterTrace.builder( dateColumn, policyDivBase ).build();


//		Map<LocalDate,Double> average = new TreeMap<>();
//		double ssum = 0; ;
//		double ccnt = 0;
//		LocalDate firstDate = null ;
//		for( Map.Entry<LocalDate, Long> entry : sums.entrySet() ){
//			final LocalDate day = entry.getKey();
//			if ( firstDate==null ) {
//				firstDate = day;
//			}
//			ccnt += cnts.get( day );
//			ssum += entry.getValue();
//			if ( firstDate.until( day, ChronoUnit.DAYS ) % 14 == 0 ) {
//				average.put( day, ssum/ccnt );
//				ssum=0;
//				ccnt=0;
//			}
//		}

//		DateColumn xx = DateColumn.create( "day", average.keySet() );
//		DoubleColumn yy = DoubleColumn.create( "sums", average.values() );
//		Trace trace = ScatterTrace.builder( xx, yy ).name( "av" ).build();

		{
			Figure figure = Figure.builder().addTraces( infectionsBase, infectionsPolicy ).build();

			figure.setLayout( Layout.builder()
						.xAxis( Axis.builder().type( Axis.Type.DATE ).build() )
						.yAxis( Axis.builder().type( Axis.Type.LOG ).build() ).width( 1000 ).build() );

			Plot.show( figure, "dada", new File( "output1.html" ) );
		}
		{
			Figure figure = Figure.builder().addTraces( rBaseTrace, rPolicyTrace ).build();

			figure.setLayout( Layout.builder()
						.xAxis( Axis.builder().type( Axis.Type.DATE ).build() )
						.yAxis( Axis.builder().type( Axis.Type.LOG ).range( Math.log10(2.),Math.log10( 4. ) ).build() ).width( 1000 ).build() );

			Plot.show( figure, "dada", new File( "output2.html" ) );
		}
		{
			Figure figure = Figure.builder().addTraces( rPolDivRBaseTrace ).build();

			figure.setLayout( Layout.builder()
						.xAxis( Axis.builder().type( Axis.Type.DATE ).build() )
						.yAxis( Axis.builder().type( Axis.Type.LINEAR ).range( 0.8,1.1 ).build() ).width( 1000 ).build() );

			Plot.show( figure, "dada", new File( "output3.html" ) );
		}
	}
}

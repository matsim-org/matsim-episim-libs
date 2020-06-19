package org.matsim.episim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.numbers.NumberRollingColumn;
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
import java.time.temporal.ChronoUnit;
import java.util.*;

class KNInfectionsAnalysis{
	private static final Logger log = Logger.getLogger( KNInfectionsAnalysis.class );

	private static final String base = "../shared-svn/projects/episim/matsim-files/bmbf6/20200615-runs//tracing-inf-noSchools/";

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
		Config config = ConfigUtils.createConfig();
//		config.plans().setInputFile( "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz");
//		Scenario scenario = ScenarioUtils.loadScenario( config );

//		for ( Iterator<? extends Person> it = scenario.getPopulation().getPersons().values().iterator() ; it.hasNext() ; ) {
//			Person person = it.next();
//			final String district = (String) person.getAttributes().getAttribute( "district" );
//			if ( district==null || !district.equals( "Berlin" ) ) {
//				it.remove();
//			}
//		}

		// ---
		Reader in = new FileReader( base + "infectionEvents.txt" );
		Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter( '\t' ).parse( in );
		Map<String, PersonInfo> map = new LinkedHashMap<>();
		for( CSVRecord record : records ){
			LocalDate date = LocalDate.parse( record.get("date") );
			map.put( record.get( "infected" ), new PersonInfo( date ) );

			PersonInfo info = map.get( record.get("infector") );
			if ( info != null ){
				info.addInfection( date );
			}
		}

		// ---
		Map<LocalDate,Long> sums = new TreeMap<>();
		Map<LocalDate,Long> cnts = new TreeMap<>();
		for( PersonInfo personInfo : map.values() ){
			Long sum = sums.get( personInfo.getInfectionDay() );
			Long cnt = cnts.get( personInfo.getInfectionDay() );
			if  ( sum==null ) {
				sums.put( personInfo.getInfectionDay(), (long) personInfo.infections.size() );
				cnts.put( personInfo.getInfectionDay(), 1L );
			} else{
				sums.put( personInfo.getInfectionDay(), sum + personInfo.infections.size() );
				cnts.put( personInfo.getInfectionDay(), cnt + 1 ) ;
			}
		}

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

		DateColumn xx2 = DateColumn.create( "day", sums.keySet() );
		DoubleColumn sss = DoubleColumn.create( "sss", sums.values() );
		DoubleColumn ccc = DoubleColumn.create( "ccc", cnts.values() );
		DoubleColumn yy2 = sss.divide( ccc ).rolling( 28 ).mean();
		Trace trace2 = ScatterTrace.builder( xx2,yy2 ).name("rolling").build();

		Figure figure = Figure.builder().addTraces( trace2 ).build();

		figure.setLayout( Layout.builder().xAxis(
				Axis.builder().type( Axis.Type.DATE ).range( LocalDate.of(2020,2,15),LocalDate.of( 2021, 2, 15) ).build()
							).yAxis(
				Axis.builder().type( Axis.Type.LINEAR ).range( 0.5, 1.6 ).build()
							       ).width( 1000 ).build() );

		Plot.show(figure, "dada", new File("output3.html") );

	}
}

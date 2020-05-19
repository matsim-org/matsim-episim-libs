package org.matsim.episim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.Facility;
import org.matsim.households.Household;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.joining.DataFrameJoiner;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.api.VerticalBarPlot;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

class KNInfectionsAnalysis{
	private static final Logger log = Logger.getLogger( KNInfectionsAnalysis.class );

//	private static final String base = "piecewise__theta2.8E-6__startDate_2020-02-20__work_0.75_0.45__leis_0.7_0.1__eduLower_0.1__eduHigher_0.0__other0.2/";
//	private static final String base = "piecewise__theta2.8E-6__ciHome0.1__ciQHome0.01__startDate_2020-02-18__work_0.75_0.45__leis_0.7_0.1__eduLower_0" +
//							   ".1__eduHigher_0.0__other0.2/";
	static String base = "piecewise__theta2.8E-6__ciHome0.3__ciQHome0.1__startDate_2020-02-18__unrestricted/";
//	static String base = "piecewise__theta2.8E-6__ciHome0.3__ciQHome0.1__startDate_2020-02-18__work_0.75_0.45__leis_0.7_0.1__eduLower_0.1__eduHigher_0.0__other0.2/";
	private static class MyHousehold {
		private final String id;
		public Set<Person> otherInfecteds = new LinkedHashSet<>();
		Person firstInfected;
		Set<Person> persons = new LinkedHashSet<>( );
		MyHousehold( String id ) {
			this.id = id;
		}
		void addPerson( Person person ){
			persons.add(person );
		}
	}

	public static void main( String[] args ) throws IOException{
		Config config = ConfigUtils.createConfig();
		config.plans().setInputFile( "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz");
		Scenario scenario = ScenarioUtils.loadScenario( config );

		// --- households:
		Map<String, MyHousehold> households = new LinkedHashMap<>();
		for( Person person : scenario.getPopulation().getPersons().values() ) {
			String homeId = (String) person.getAttributes().getAttribute( "homeId" );
			MyHousehold household = households.computeIfAbsent( homeId, MyHousehold::new ) ;
			household.addPerson( person );
		}

		// ---
		Reader in = new FileReader( base + "infectionEvents.txt" );
		Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter( '\t' ).parse( in );
		for( CSVRecord record : records ){
			// check if person is first person in hh to get infected; if so, memorize:
			final Person infectedPerson = scenario.getPopulation().getPersons().get( Id.createPersonId( record.get( "infected" ) ) );
			final String infectedHomeId = (String) infectedPerson.getAttributes().getAttribute( "homeId" );
			final MyHousehold household = households.get( infectedHomeId );
			if( household.firstInfected == null ){
				household.firstInfected = infectedPerson;
			} else{
				if( record.get( "infectionType" ).equals( "home_home" ) ){
					household.otherInfecteds.add( infectedPerson );
					Person infector = scenario.getPopulation().getPersons().get( Id.createPersonId( record.get( "infector" ) ) );
					String infectorHomeId = (String) infector.getAttributes().getAttribute( "homeId" );
					MyHousehold infectorHousehold = households.get( infectorHomeId );
					Gbl.assertIf( household.equals( infectorHousehold ) );
				}
			}
		}
		// ---
		double nInfectedHouseholds = 0.;
		double nSecondaryHHInfections = 0.;
		double nOtherPersonsInInfectedHouseholds = 0.;
		for( MyHousehold household : households.values() ) {
			if ( household.firstInfected!=null ) {
				nInfectedHouseholds++;
				nSecondaryHHInfections += household.otherInfecteds.size();
				nOtherPersonsInInfectedHouseholds += household.persons.size()-1;
			}
		}
		// ---
		log.info("percentage of secondary persons in HHs that had at least one infection = " + nSecondaryHHInfections/nOtherPersonsInInfectedHouseholds );
	}
}

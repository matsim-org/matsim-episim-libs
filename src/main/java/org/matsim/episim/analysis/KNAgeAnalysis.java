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
import org.matsim.core.utils.collections.Tuple;
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
import java.util.Map;
import java.util.TreeMap;

class KNAgeAnalysis{
	private static final Logger log = Logger.getLogger( KNAgeAnalysis.class );

	private static final String base = "output/2020-10-04_16-24-49__symmetric__fromConfig__theta2.1E-5@NaN_seed0_strtDt2020-02-18_imprtOffst0_trCap{1970-01-01=0}_quStrt+5881630-08-28/";

	public static void main( String[] args ) throws IOException{
		Config config = ConfigUtils.createConfig();
		config.plans().setInputFile(
				"../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_entirePopulation_emptyPlans_withDistricts.xml.gz" );
		Scenario scenario = ScenarioUtils.loadScenario( config );

		// ---

		Table table ;
		{
			Map<String, Double> freq = new TreeMap<>();
			for( Person person : scenario.getPopulation().getPersons().values() ){
				String age = Integer.toString( (int) person.getAttributes().getAttribute( "age" ) );
				Double sum = freq.computeIfAbsent( age, ( a ) -> 0. );
				freq.put( age, sum + 0.02 );
			}
			final StringColumn ageColumn = StringColumn.create( "age", freq.keySet() );
			final DoubleColumn freqInPop = DoubleColumn.create( "freqInPop * 0.02", freq.values() );

			table = Table.create("table" ).addColumns( ageColumn, freqInPop );
		}

		// ---

		Table table2;
		final String ageFreqInInfections = "ageFreqInInfections";
		{
			Map<String,Double> freq2 = new TreeMap<>();
			Reader in = new FileReader( base + "infectionEvents.txt" );
			Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter( '\t' ).parse( in );
			for( CSVRecord record : records ){
				String infected = record.get( "infected" );
				Person person = scenario.getPopulation().getPersons().get( Id.createPersonId( infected ) );
				if ( person==null ) {
					log.warn( "infected=" + infected );
				}
				Gbl.assertNotNull( person );
				String age = Integer.toString( (int) person.getAttributes().getAttribute( "age" ) );
				Double sum = freq2.computeIfAbsent( age, ( a ) -> 0. );
				freq2.put( age, sum + 1 );
			}

			final StringColumn ageColumn = StringColumn.create( "age", freq2.keySet() );
			final DoubleColumn freqInPop = DoubleColumn.create( ageFreqInInfections, freq2.values() );

			table2 = Table.create("table" ).addColumns( ageColumn, freqInPop );
		}
		// ---


		Table result = new DataFrameJoiner( table, "age" ).fullOuter( table2 );

		Layout.BarMode barMode = Layout.BarMode.GROUP;
		Figure fig = VerticalBarPlot.create( "title", result, "age", barMode, "freqInPop * 0.02" , ageFreqInInfections );

		Layout layout = Layout.builder()
				      .width( 800 )
				      .height(500 )
				      .build();
		fig.setLayout( layout );

		Plot.show(fig,"divname" );
	}
}

package org.matsim.episim.analysis;

import com.google.inject.internal.cglib.core.$AbstractClassGenerator;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.util.CSVReaders;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.scenario.ScenarioUtils;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.joining.DataFrameJoiner;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.api.BubblePlot;
import tech.tablesaw.plotly.api.VerticalBarPlot;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class KNAnalysis{
	private static final Logger log = Logger.getLogger( KNAnalysis.class );

	private static final String base = "piecewise__theta2.8E-6__offset-4__leis_1.0_0.7_0.7_0.1__other0.2/";

	public static void main( String[] args ){
		Config config = ConfigUtils.createConfig();
		config.plans().setInputFile(
				"../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_entirePopulation_emptyPlans_withDistricts.xml.gz" );
		Scenario scenario = ScenarioUtils.loadScenario( config );

		// ---

		Table table ;
		{
			Map<String, Double> freq = new TreeMap<>();
			for( Person person : scenario.getPopulation().getPersons().values() ){
				//noinspection ConstantConditions
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
			Map<String, Double> freq2 = new TreeMap<>();
			List<String[]> result = CSVReaders.readFile( base + "infectionEvents.txt", '\t' );
			boolean firstLine = true;
			for( String[] line : result ){
				String infected = line[2];
				if( firstLine ){
					firstLine = false;
					Gbl.assertIf( infected.equals( "infected" ) );
					continue;
				}
				Person person = scenario.getPopulation().getPersons().get( Id.createPersonId( infected ) );
				if ( person==null ) {
					log.warn( "infected=" + infected );
				}
				Gbl.assertNotNull( person );
				@SuppressWarnings("ConstantConditions") String age = Integer.toString( (int) person.getAttributes().getAttribute( "age" ) );
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

package org.matsim.episim.model;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.testcases.MatsimTestUtils;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;
import tech.tablesaw.plotly.api.LinePlot;
import tech.tablesaw.plotly.api.TimeSeriesPlot;
import tech.tablesaw.plotly.components.Page;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.util.HashMap;
import java.util.Map;

public class InfectionModelWithAntibodiesTest{
	private static final Logger log = Logger.getLogger( InfectionModelWithAntibodiesTest.class );
	@Rule public MatsimTestUtils utils = new MatsimTestUtils();

	private final Map<VirusStrain, Double> ak50PerStrain = new HashMap<>();

	@Before
	public void setup() {
		ak50PerStrain.put( VirusStrain.SARS_CoV_2, 0.2 );
		ak50PerStrain.put( VirusStrain.ALPHA, 0.2 );
		ak50PerStrain.put( VirusStrain.B1351, 0.4 );
		ak50PerStrain.put( VirusStrain.DELTA, 0.4 );
		ak50PerStrain.put( VirusStrain.OMICRON_BA1, 2.4 );
		ak50PerStrain.put( VirusStrain.OMICRON_BA2, 3.0 );
		ak50PerStrain.put( VirusStrain.STRAIN_A, 3.0 * 2 );
		ak50PerStrain.put( VirusStrain.STRAIN_B, 3.0 );
	}

	@Test
	public void nordstroemEtAl() {
		var person = EpisimTestUtils.createPerson();
		person.setVaccinationStatus( EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 0 );
//		person.setVaccinationStatus( EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 300 );
		var strain = VirusStrain.DELTA;

		final String days = "day";
		Column<Integer> records = IntColumn.create( days );

		final String vaccineEfficacies = "VE";
		Column<Double> values = DoubleColumn.create( vaccineEfficacies );
		final String grouping = "grouping";
		var groupings = StringColumn.create( grouping );

		final String nordstrom = "Nordström";

		var fact = 0.001;

		for ( int ii=1; ii<600; ii++ ){
			{
				records.append( ii );
				groupings.append( nordstrom );
				if ( ii <= 30 ) {
					values.append(0.92);
				} else if ( ii <= 60 ) {
					values.append(0.89);
				} else if ( ii <= 120 ){
					values.append( 0.85 );
				} else if ( ii <= 180 ){
					values.append( 0.47 );
				} else if ( ii <= 210 ){
					values.append( 0.29 );
				} else {
					values.append( 0.23 );
				}
			}

			var nAb = relativeAbLevel( person, strain, ii );

			{
				var beta = 1.;
				double immunityFactor = 1.0 / (1.0 + Math.pow( nAb, beta ));
				final double probaWVacc = 1 - Math.exp( -fact * immunityFactor );
				final double probaWoVacc = 1 - Math.exp( -fact );
				final double ve = 1. - probaWVacc / probaWoVacc;
				log.info( ve );
				records.append( ii );
				values.append( ve );
				groupings.append( "beta=1" );
			}
			{
				var beta = 3.;
				double immunityFactor = 1.0 / (1.0 + Math.pow( nAb, beta ));
				final double probaWVacc = 1 - Math.exp( -fact * immunityFactor );
				final double probaWoVacc = 1 - Math.exp( -fact );
				final double ve = 1. - probaWVacc / probaWoVacc;
				log.info( ve );
				records.append( ii );
				values.append( ve );
				groupings.append("beta=3");
			}
		}

		Table table = Table.create("aa");
		table.addColumns( records );
		table.addColumns( values );
		table.addColumns( groupings );
		var figure = LinePlot.create("aa", table, days, vaccineEfficacies, grouping ) ;
		var divName = "target";
		var outputFile = "output.html";

		Page page = Page.pageBuilder(figure, divName ).build();
		String output = page.asJavascript();

		try ( Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
			writer.write(output);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}


	}

	@Test
	public void yuEtAl(){
		// https://doi.org/10.1101/2022.02.06.22270533


		EpisimPerson recoveredPerson = EpisimTestUtils.createPerson();
		EpisimTestUtils.infectPerson( recoveredPerson, VirusStrain.DELTA, 0 );
		{
			// I use the 658 against the wild variant as base:
			double nAbBase;
			{
				EpisimPerson basePerson = EpisimTestUtils.createPerson();
				basePerson.setVaccinationStatus( EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 0 );
				nAbBase = relativeAbLevel( basePerson, VirusStrain.SARS_CoV_2, 100 );
			}

			// only vaccinated:

			EpisimPerson person = EpisimTestUtils.createPerson();
			person.setVaccinationStatus( EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 0 );
			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 658./658., nAb/nAbBase, 0.5);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 29./658., nAb/nAbBase, 0.5 );
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA2;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 24./658., nAb/nAbBase, 0.5 );
			}

			// yyyy more to be added here ...

			{
				// the following are to print out antobody levels, but they do not test anything as of now.
				person = EpisimTestUtils.createPerson();
				person.setVaccinationStatus( EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 0 );

				VirusStrain strain = VirusStrain.SARS_CoV_2;
				log.warn( "double vaccination against " + strain.name() + "=" + relativeAbLevel( person, strain, 100 ) );

				strain = VirusStrain.DELTA;
				log.warn( "double vaccination against " + strain.name() + "=" + relativeAbLevel( person, strain, 100 ) );

				strain = VirusStrain.OMICRON_BA1;
				log.warn( "double vaccination against " + strain.name() + "=" + relativeAbLevel( person, strain, 100 ) );

				person.setVaccinationStatus( EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 200 );
				strain = VirusStrain.DELTA;
				log.warn( "triple vaccination against " + strain.name() + "=" + relativeAbLevel( person, strain, 300 ) );
				strain = VirusStrain.OMICRON_BA1;
				log.warn( "triple vaccination against " + strain.name() + "=" + relativeAbLevel( person, strain, 300 ) );
			}


		}
	}

	@Test
	public void roesslerEtAl(){
		// http://dx.doi.org/10.1101/2022.02.01.22270263 Fig.1

		EpisimPerson infector = EpisimTestUtils.createPerson();

		EpisimPerson recoveredPerson = EpisimTestUtils.createPerson();
		EpisimTestUtils.infectPerson( recoveredPerson, VirusStrain.DELTA, 0 );
		{
			// Fig.1: I use the top left (vaccinated; against wild variant) as base:
			double nAbBase;
			{
				EpisimPerson basePerson = EpisimTestUtils.createPerson();
				basePerson.setVaccinationStatus( EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 0 );
				EpisimTestUtils.infectPerson( basePerson, VirusStrain.OMICRON_BA1, 50 * 3600 * 24. );
				nAbBase = relativeAbLevel( basePerson, VirusStrain.SARS_CoV_2, 100 );
			}
			
			// Fig.1 A (vaccinated + omicron):
			EpisimPerson person = EpisimTestUtils.createPerson();
			person.setVaccinationStatus( EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 0 );
			EpisimTestUtils.infectPerson( person, VirusStrain.OMICRON_BA1, 50 * 3600 * 24. );
			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 4000./4000., nAb/nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 4000./4000., nAb/nAbBase, 0.1 );
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 4000./4000., nAb/nAbBase, 0.5 );
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 1000./4000., nAb/nAbBase, 0.5 );
			}

			// Fig1 B (only omicron):
			person = EpisimTestUtils.createPerson();
			EpisimTestUtils.infectPerson( person, VirusStrain.OMICRON_BA1, 50. * 24 * 3600. );
			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 0./4000., nAb/nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 0./4000., nAb/nAbBase, 0.1 );
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 10./4000., nAb/nAbBase, 0.1 );
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 60./4000., nAb/nAbBase, 0.1 );
			}

			// Fig1 C (vaccinated + delta + omicron):
			// (not plausible that these come out lower than without the delta infection in between)
//			person = EpisimTestUtils.createPerson();
//			person.setVaccinationStatus( EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 0 );
//			person.possibleInfection(
//					new EpisimInfectionEvent( 33 * 3600 * 24., person.getPersonId(), infector.getPersonId(), null, "dummy", 2, VirusStrain.DELTA, 1. ) );
//			person.checkInfection();
//			person.possibleInfection(
//					new EpisimInfectionEvent( 66 * 3600 * 24., person.getPersonId(), infector.getPersonId(), null, "dummy", 2, VirusStrain.OMICRON_BA1, 1. ) );
//			person.checkInfection();
//			{
//				VirusStrain strain = VirusStrain.SARS_CoV_2;
//				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
//							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
//				Assert.assertEquals( 2000./4000., nAb/nAbBase, 0.5);
//			}
//			{
//				VirusStrain strain = VirusStrain.ALPHA;
//				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
//							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
//				Assert.assertEquals( 4000./4000., nAb/nAbBase, 0.1 );
//			}
//			{
//				VirusStrain strain = VirusStrain.DELTA;
//				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
//							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
//				Assert.assertEquals( 3000./4000., nAb/nAbBase, 0.1 );
//			}
//			{
//				VirusStrain strain = VirusStrain.OMICRON_BA1;
//				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
//							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
//				Assert.assertEquals( 300./4000., nAb/nAbBase, 0.1 );
//			}

			// Fig1 D (delta + omicron):
			person = EpisimTestUtils.createPerson();
			EpisimTestUtils.infectPerson( person, VirusStrain.DELTA, 0 * 24 * 3600. );
			EpisimTestUtils.infectPerson( person, VirusStrain.OMICRON_BA1, 50 * 24 * 3600. );
			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 1000./4000., nAb/nAbBase, 0.5);
			}
			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 1000./4000., nAb/nAbBase, 0.5 );
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 2000./4000., nAb/nAbBase, 0.5 );
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = relativeAbLevel( person, strain, 100 );
				Assert.assertEquals( 500./4000., nAb/nAbBase, 0.5 );
			}


		}

	}
	private double relativeAbLevel( EpisimPerson basePerson, VirusStrain strain, int iteration ){
		return InfectionModelWithAntibodies.getRelativeAntibodyLevel(basePerson, iteration, basePerson.getNumVaccinations(), basePerson.getNumInfections(), strain, ak50PerStrain);
	}
}

package org.matsim.episim.model;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

public class InfectionModelWithAntibodiesTest{
	private static final Logger log = Logger.getLogger( InfectionModelWithAntibodiesTest.class );
	@Rule public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public void roesslerEtAl(){
		// http://dx.doi.org/10.1101/2022.02.01.22270263 Fig.1

		EpisimPerson infector = EpisimTestUtils.createPerson();

		Map<VirusStrain, Double> ak50PerStrain = new HashMap<>();

		ak50PerStrain.put(VirusStrain.SARS_CoV_2, 0.2 );
		ak50PerStrain.put(VirusStrain.ALPHA, 0.2 );
		ak50PerStrain.put(VirusStrain.B1351, 0.4 );
		ak50PerStrain.put(VirusStrain.DELTA, 0.4);
		ak50PerStrain.put(VirusStrain.OMICRON_BA1, 2.4);
		ak50PerStrain.put(VirusStrain.OMICRON_BA2, 3.0);
		ak50PerStrain.put(VirusStrain.STRAIN_A, 3.0 * 2);
		ak50PerStrain.put(VirusStrain.STRAIN_B, 3.0 );

		EpisimPerson recoveredPerson = EpisimTestUtils.createPerson();
		recoveredPerson.possibleInfection( new EpisimInfectionEvent( 0, recoveredPerson.getPersonId(), infector.getPersonId(),
				null, "dummyInfectionType", 2, VirusStrain.DELTA, 1. ) );
		recoveredPerson.checkInfection();

		{
			// Fig.1: I use the top left (vaccinated; against wild variant) as base:
			double nAbBase;
			{
				EpisimPerson basePerson = EpisimTestUtils.createPerson();
				basePerson.setVaccinationStatus( EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 0 );
				basePerson.possibleInfection(
						new EpisimInfectionEvent( 50 * 3600 * 24., basePerson.getPersonId(), infector.getPersonId(), null, "dummy", 2, VirusStrain.OMICRON_BA1, 1. ) );
				basePerson.checkInfection();
				nAbBase = InfectionModelWithAntibodies.getAntibodyLevel( basePerson, 100, basePerson.getNumVaccinations(), basePerson.getNumInfections(),
						VirusStrain.SARS_CoV_2 )
								 / InfectionModelWithAntibodies.getAk50( basePerson, VirusStrain.SARS_CoV_2, ak50PerStrain, basePerson.getNumInfections() );
			}
			
			// Fig.1 A (vaccinated + omicron):
			EpisimPerson person = EpisimTestUtils.createPerson();
			person.setVaccinationStatus( EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 0 );
			person.possibleInfection(
					new EpisimInfectionEvent( 50 * 3600 * 24., person.getPersonId(), infector.getPersonId(), null, "dummy", 2, VirusStrain.OMICRON_BA1, 1. ) );
			person.checkInfection();
			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
				Assert.assertEquals( 4000./4000., nAb/nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
				Assert.assertEquals( 4000./4000., nAb/nAbBase, 0.1 );
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
				Assert.assertEquals( 4000./4000., nAb/nAbBase, 0.5 );
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
				Assert.assertEquals( 1000./4000., nAb/nAbBase, 0.5 );
			}

			// Fig1 B (only omicron):
			person = EpisimTestUtils.createPerson();
			person.possibleInfection( new EpisimInfectionEvent( 50 * 3600 * 24., person.getPersonId(), infector.getPersonId(), null, "dummy", 2, VirusStrain.OMICRON_BA1, 1. ) );
			person.checkInfection();
			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
				Assert.assertEquals( 0./4000., nAb/nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
				Assert.assertEquals( 0./4000., nAb/nAbBase, 0.1 );
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
				Assert.assertEquals( 10./4000., nAb/nAbBase, 0.1 );
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
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
			person.possibleInfection( new EpisimInfectionEvent( 0 * 3600 * 24., person.getPersonId(), infector.getPersonId(), null, "dummy", 2, VirusStrain.DELTA, 1. ) );
			person.checkInfection();
			person.possibleInfection( new EpisimInfectionEvent( 50 * 3600 * 24., person.getPersonId(), infector.getPersonId(), null, "dummy", 2, VirusStrain.OMICRON_BA1, 1. ) );
			person.checkInfection();
			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
				Assert.assertEquals( 1000./4000., nAb/nAbBase, 0.5);
			}
			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
				Assert.assertEquals( 1000./4000., nAb/nAbBase, 0.5 );
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
				Assert.assertEquals( 2000./4000., nAb/nAbBase, 0.5 );
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
				Assert.assertEquals( 500./4000., nAb/nAbBase, 0.5 );
			}


		}

	}
}

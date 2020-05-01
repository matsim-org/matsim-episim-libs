package org.matsim.run.modules;

import com.google.inject.internal.cglib.core.$AbstractClassGenerator;
import com.typesafe.config.Config;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.testcases.MatsimTestUtils;

import java.util.Map;

public class SnzScenarioTest{
	private static final Logger log = Logger.getLogger( SnzScenarioTest.class ) ;
	@Rule public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public void testBuildPolicyBerlin(){
		SnzScenario abc = new SnzScenario();
		Config config = abc.buildPolicyBerlin( 0 );


		Config subConfig = config.getConfig("work");
		for ( long day=0 ; day <100 ; day ++ ){
			String key = String.valueOf( day );
			if( subConfig.hasPath( key ) ){
				Restriction r = Restriction.fromConfig( subConfig.getConfig( key ) );

				log.info( day + ": " + r.getRemainingFraction() );

			}
		}
		Assert.assertEquals( 0.95, Restriction.fromConfig( subConfig.getConfig( "20" ) ).getRemainingFraction(), 0.02 ) ;
		Assert.assertEquals( 0.90, Restriction.fromConfig( subConfig.getConfig( "22" ) ).getRemainingFraction(), 0.02 ) ;
		Assert.assertEquals( 0.85, Restriction.fromConfig( subConfig.getConfig( "23" ) ).getRemainingFraction(), 0.02 ) ;
		Assert.assertEquals( 0.80, Restriction.fromConfig( subConfig.getConfig( "25" ) ).getRemainingFraction(), 0.04 ) ;
		Assert.assertEquals( 0.65, Restriction.fromConfig( subConfig.getConfig( "26" ) ).getRemainingFraction(), 0.09 ) ;
		Assert.assertEquals( 0.60, Restriction.fromConfig( subConfig.getConfig( "27" ) ).getRemainingFraction(), 0.09 ) ;
		Assert.assertEquals( 0.55, Restriction.fromConfig( subConfig.getConfig( "28" ) ).getRemainingFraction(), 0.1 ) ;
		Assert.assertEquals( 0.50, Restriction.fromConfig( subConfig.getConfig( "31" ) ).getRemainingFraction(), 0.03 ) ;
		Assert.assertEquals( 0.45, Restriction.fromConfig( subConfig.getConfig( "33" ) ).getRemainingFraction(), 0.02 ) ;

	}

}

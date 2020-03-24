package org.matsim.run;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.Patch;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.testcases.MatsimTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class RunEpisimBaseTest{
        private static final Logger log = Logger.getLogger( RunEpisim.class );
        @Rule public MatsimTestUtils utils = new MatsimTestUtils();

        @Test
        public void testDiffutils() {
                List<String> expected=Arrays.asList("this is a test","a test");
                List<String> actual=Arrays.asList("this is a testfile","a test");
                Patch<String> patch = null;
                try{
                        patch = DiffUtils.diff( expected, actual );
                } catch( DiffException e ){
                        throw new RuntimeException( e );
                }
                List<String> result = UnifiedDiffUtils.generateUnifiedDiff( "expected", "actual", expected, patch, 0 );
                for( String str : result ){
                        System.out.println( str );
                }
        }

        @Test
        public void test10it() throws IOException {

                OutputDirectoryLogging.catchLogEntries();

                Config config = ConfigUtils.createConfig( new EpisimConfigGroup() );
                EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );

                episimConfig.setInputEventsFile(
                                "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct/output-berlin-v5.4-1pct/berlin-v5.4-1pct.output_events_for_episim.xml.gz" );
                // still need to push.  is faster

                episimConfig.setFacilitiesHandling( EpisimConfigGroup.FacilitiesHandling.bln );

                episimConfig.setCalibrationParameter(2);

                // pt:
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "tr" )) ;
                // regular out-of-home acts:
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "work" ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "leis" ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "edu" ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "shop" ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "errands" ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "business" ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "other" ) );
                // freight act:
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "freight" ) );
                // home act:
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "home" ) );

                config.controler().setOutputDirectory( utils.getOutputDirectory() );

                RunEpisim.runSimulation(config, 10);

                {
                        String expected = utils.getInputDirectory() + "/infections.txt";
                        String actual = utils.getOutputDirectory() + "/infections.txt";
                        Assert.assertEquals( 0, compareWithDiffUtils( expected, actual ) );
                }
                {
                        String expected = utils.getInputDirectory() + "/infectionEvents.txt";
                        String actual = utils.getOutputDirectory() + "/infectionEvents.txt";
                        Assert.assertEquals( 0, compareWithDiffUtils( expected, actual ) );
                }
                OutputDirectoryLogging.closeOutputDirLogging();
        }

        static int compareWithDiffUtils( String ORIGINAL, String REVISED ) {
                // yy one might presumably rather first test the checksum, and then do the detailed test only if there are differences.  kai, mar'20

                //build simple lists of the lines of the two testfiles
                List<String> original;
                List<String> revised;
                try{
                        original = Files.readAllLines(new File(ORIGINAL).toPath() );
                        revised = Files.readAllLines(new File(REVISED).toPath());
                } catch( IOException e ){
                        throw new RuntimeException( e );
                }

                Patch<String> patch = null;
                try{
                        patch = DiffUtils.diff( original, revised );
                } catch( DiffException e ){
                        throw new RuntimeException( e );
                }

//                for( AbstractDelta<String> delta : patch.getDeltas() ){
//                        System.out.println( delta );
//                }

//                List<String> result = UnifiedDiffUtils.generateUnifiedDiff( "expected", "actual", original, patch, 0 );
//                for( String str : result ){
//                        System.out.println( str );
//                }


//                return patch.getDeltas().size();

                DiffRowGenerator generator = DiffRowGenerator.create()
                                                             .showInlineDiffs(true)
                                                             .inlineDiffByWord(true)
                                                             .ignoreWhiteSpaces( true )
                                                             .oldTag(f -> "~")
                                                             .newTag(f -> "**")
                                                             .build();
                List<DiffRow> rows ;
                try{
                        rows = generator.generateDiffRows( original, patch );
                } catch( DiffException e ){
                        throw new RuntimeException( e );
                }

                for (DiffRow row : rows) {
                        if ( !row.getNewLine().equals( row.getOldLine() ) ){
                                System.out.println( "|" + row.getOldLine() + "|" + row.getNewLine() + "|" );
                        }
                }

                return patch.getDeltas().size();
        }
}

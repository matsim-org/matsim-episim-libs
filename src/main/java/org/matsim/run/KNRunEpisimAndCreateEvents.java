/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */


package org.matsim.run;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimConfigGroup.FacilitiesHandling;
import org.matsim.episim.InfectionEventHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class KNRunEpisimAndCreateEvents{
    private static final Logger log = Logger.getLogger( KNRunEpisimAndCreateEvents.class );

    public static void main(String[] args) throws IOException {

        OutputDirectoryLogging.catchLogEntries();

        Config config = ConfigUtils.loadConfig( "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct/input/berlin-v5.4-1pct.config.xml" );

        EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

        episimConfig.setInputEventsFile( "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct/output-berlin-v5.4-1pct/berlin-v5.4-1pct.output_events_for_episim.xml.gz" );
        episimConfig.setFacilitiesHandling(FacilitiesHandling.bln);
        episimConfig.setSampleSize(0.01);
        episimConfig.setCalibrationParameter(0.00021);


        RunEpisim.addDefaultParams(episimConfig);

        config.controler().setOutputDirectory( "output-w-events" );

        config.vspExperimental().setVspDefaultsCheckingLevel( VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn );

        // ---

        config.plans().setInputFile( null ); // save some time while not needed
        Scenario scenario = ScenarioUtils.loadScenario( config );

        // ---

        Path out = Paths.get( config.controler().getOutputDirectory() );
        if (!Files.exists(out ))
            Files.createDirectories(out);

        EventsManager events = EventsUtils.createEventsManager();

        InfectionEventHandler eventHandler = new InfectionEventHandler( config, events );
        events.addHandler(eventHandler);

        events.addHandler( new ActivityStartEventHandler(){
            @Override public void handleEvent( ActivityStartEvent event ){
                Link link = scenario.getNetwork().getLinks().get( event.getLinkId() );
                Node toNode = link.getToNode();
                Coord coord = toNode.getCoord();
                event.setCoord( coord );
            }
        } );

        events.addHandler( new EventWriterXML( "events-with-coordinates.xml.gz" ) );

        List<Event> allEvents = new ArrayList<>();
        events.addHandler(new RunEpisim.ReplayHandler(allEvents) );

        ControlerUtils.checkConfigConsistencyAndWriteToLog( config, "Just before starting iterations" );

        for ( int iteration = 0 ; iteration <= 2 ; iteration++) {
            events.resetHandlers(iteration);
            if (eventHandler.isFinished())
                break;

            if (iteration == 0)
                EventsUtils.readEvents(events, episimConfig.getInputEventsFile() );
            else
                allEvents.forEach(events::processEvent);
        }

        OutputDirectoryLogging.closeOutputDirLogging();

    }

}

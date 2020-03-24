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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.matsim.api.core.v01.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.events.EventsUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.run.RunEpisim.ReplayHandler;

/**
* @author smueller
*/

public class RunFromConfig {

	public static void main(String[] args) throws IOException{
       
		OutputDirectoryLogging.catchLogEntries();

		if ( args.length==0 ) {
			throw new RuntimeException("Need config file");
		}
		
		String[] typedArgs = Arrays.copyOfRange( args, 1, args.length );
		
		Config config = ConfigUtils.loadConfig( args[0] );
		
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
		
		ConfigUtils.applyCommandline( config, typedArgs ) ;
		
        OutputDirectoryLogging.initLoggingWithOutputDirectory( config.controler().getOutputDirectory() );
        
        EventsManager events = EventsUtils.createEventsManager();

        events.addHandler( new InfectionEventHandler( config ) );

        List<Event> allEvents = new ArrayList<>();
       
        events.addHandler(new ReplayHandler(allEvents));

        ControlerUtils.checkConfigConsistencyAndWriteToLog(config, "Just before starting iterations");
        
        for ( int iteration=0 ; iteration<=300 ; iteration++ ){
                events.resetHandlers( iteration );
                if (iteration == 0)
                		EventsUtils.readEvents( events, episimConfig.getInputEventsFile() );
                else
                        allEvents.forEach(events::processEvent);
        }


	}

}

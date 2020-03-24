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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.matsim.api.core.v01.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.events.EventsUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.EpisimConfigGroup.FacilitiesHandling;
import org.matsim.episim.EpisimConfigGroup.InfectionParams;

/**
* @author smueller
*/

public class RunParallel {
	
	private static final int MYTHREADS = 4;

	public static void main(String[] args) throws IOException{
		
		ExecutorService executor = Executors.newFixedThreadPool(MYTHREADS);
		List<Long> pt = Arrays.asList(1000L, 10L, 20L, 30L);
		List<Long> work = Arrays.asList(1000l, 10l, 20l, 30l);
		List<Long> leisure = Arrays.asList(1000l, 10l, 20l, 30l);;
		List<Long> otherExceptHome = Arrays.asList(1000l, 10l, 20l, 30l);

		
		for (long p : pt) {
			for (long w : work) {
				for (long l : leisure) {
					for (long o : otherExceptHome) {
						Runnable runnable = new MyRunnable(p, w, l, o);
						executor.execute(runnable);
					}
					while (!executor.isTerminated()) {
						 
					}
				}
			}
		}
		executor.shutdown();
		// Wait until all threads are finished
		while (!executor.isTerminated()) {
 
		}
		System.out.println("\nFinished all threads");
		
		
	}
	
	public static class MyRunnable implements Runnable {
		private final long p;
		private final long w;
		private final long l;
		private final long o;
 
		MyRunnable(long p, long w, long l, long o) {
			this.p = p;
			this.w = w;
			this.l = l;
			this.o = o;
		
		}
 
		@Override
		public void run() {
			
			OutputDirectoryLogging.catchLogEntries();

	        Config config = ConfigUtils.createConfig( new EpisimConfigGroup() );
	        EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );

	        episimConfig.setInputEventsFile( "../snzDrt220.0.events.reduced.xml.gz" );
	        episimConfig.setFacilitiesHandling( FacilitiesHandling.snz );
	        
	        episimConfig.setCalibrationParameter(0.002);

//	        int closingIteration = 10;
	        // pt:
	        episimConfig.addContainerParams( new InfectionParams( "tr" ).setContactIntensity( 10. ).setShutdownDay(this.p) );
	        // regular out-of-home acts:
	        episimConfig.addContainerParams( new InfectionParams( "business" ).setShutdownDay( this.o ).setRemainingFraction( 0. ) );
	        episimConfig.addContainerParams( new InfectionParams( "educ_higher" ).setShutdownDay( this.o ).setRemainingFraction( 0. ) );
	        episimConfig.addContainerParams( new InfectionParams( "educ_secondary" ).setShutdownDay( this.o ).setRemainingFraction( 0. ) );
	        episimConfig.addContainerParams( new InfectionParams( "errands" ).setShutdownDay( this.o ).setRemainingFraction( 0. ) );
	        episimConfig.addContainerParams( new InfectionParams( "leisure" ).setShutdownDay( this.l ).setRemainingFraction( 0. ) );
	        episimConfig.addContainerParams( new InfectionParams( "shopping" ).setShutdownDay( this.o ).setRemainingFraction( 0. ) );
	        episimConfig.addContainerParams( new InfectionParams( "work" ).setShutdownDay( this.w ).setRemainingFraction( 0. ) );
	        // home act:
	        episimConfig.addContainerParams( new InfectionParams( "home" ) );

	        config.controler().setOutputDirectory(p + "-" + w + "-" + l + "-" + o);
	        
//	        RunEpisim.setOutputDirectory(config);

//	        ConfigUtils.applyCommandline( config, Arrays.copyOfRange( args, 0, args.length ) ) ;

	        try {
				RunEpisim.runSimulation(config, 100);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
			
		
	}

}

/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
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

package org.matsim.episim.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.events.*;
import tech.tablesaw.api.DateColumn;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.plotly.traces.Trace;

import java.io.File;
import java.time.LocalDate;
import java.util.*;


/**
 * @author smueller
 * Calculates R values for all runs in given directory, dated on day of switching to contagious
 * Output is written to rValues.txt in the working directory
 */

public class KNRValuesFromEvents{
	private static final Logger log = LogManager.getLogger( KNRValuesFromEvents.class );

	private static final String WORKINGDIR = "/Users/kainagel/git/all-matsim/episim-matsim/output/";

	// --- base rnd
//	private static final String base = "2020-06-28_13-24-11__unrestr__theta1.1E-5@3__trStrt46_seed4711_strtDt2020-02-16_trCap{1970-01-01=0}/";
//	private static final String policy = "2020-06-28_15-25-40__unrestr__theta1.1E-5@3__trStrt46_seed4711_strtDt2020-02-16_trCap{1970-01-01=0}/";

	// ciEdu = 10.
//	private static final String base = baseDir + "2020-06-28_14-02-44__unrestr__theta1.1E-5@3__trStrt46_seed4711_strtDt2020-02-16_trCap{1970-01-01=0}/";


	// --- 4713

//	private static final String base = "2020-06-28_16-21-56__unrestr__theta1.1E-5@3__trStrt46_seed4713_strtDt2020-02-16_trCap{1970-01-01=0}/";
//	private static final String policy = "2020-06-28_16-22-05__unrestr__theta1.1E-5@3__trStrt46_seed4713_strtDt2020-02-16_trCap{1970-01-01=0}/";

	// 4715

//	private static final String base = "2020-06-28_16-32-39__unrestr__theta1.1E-5@3__trStrt46__ciEdu1.0_seed4715_strtDt2020-02-16_trCap{1970-01-01=0}/";
//	private static final String policy = "2020-06-28_17-03-04__unrestr__theta1.1E-5@3__trStrt46__ciEdu0.0_seed4715_strtDt2020-02-16_trCap{1970-01-01=0}/";

	// 4717

//	private static final String base = "2020-06-28_17-01-20__unrestr__theta1.1E-5@3__trStrt46__ciEdu1.0_seed4717_strtDt2020-02-16_trCap{1970-01-01=0}/";
//	private static final String policy = "2020-06-28_17-01-30__unrestr__theta1.1E-5@3__trStrt46__ciEdu0.0_seed4717_strtDt2020-02-16_trCap{1970-01-01=0}/";

	// 4719

//	private static final String base = "2020-06-28_17-01-46__unrestr__theta1.1E-5@3__trStrt46__ciEdu1.0_seed4719_strtDt2020-02-16_trCap{1970-01-01=0}/";
//	private static final String policy = "2020-06-28_17-02-03__unrestr__theta1.1E-5@3__trStrt46__ciEdu0.0_seed4719_strtDt2020-02-16_trCap{1970-01-01=0}/";

	private static final String [] dirs = {
			"2020-10-04_16-24-49__symmetric__fromConfig__theta2.1E-5@NaN_seed0_strtDt2020-02-18_imprtOffst0_trCap{1970-01-01=0}_quStrt+5881630-08-28/"
			,
			"zz_archive-2020-10-04/2020-09-20_19-20-16__symmetric__fromConfig__theta1.8E-6@NaN_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}__trStrt46/"
	};

	private static final LocalDate startDate = LocalDate.parse("2020-02-16");

	private static final HashMap<String, InfectedPerson> infectedPersons = new LinkedHashMap<String, InfectedPerson>();

	public static void main(String[] args){

		EventsManager manager = EventsUtils.createEventsManager();
		manager.addHandler( new Handler() );

		log.info("Reading " + dirs.length + " files");
		log.info(dirs);

		List<Trace> traces = new ArrayList<>(  );
		List<DoubleColumn> rColumns = new ArrayList<>(  );
		List<DateColumn> dateColumns = new ArrayList<>();

		for (String scenario : dirs) {
			log.info("starting to process directory " + scenario);
			infectedPersons.clear();
			final File eventsDir = new File( WORKINGDIR + scenario + "/events" );
			if ( !eventsDir.exists() ) {
				log.info("skipping directory " + scenario);
				continue;
			}
			File[] eventFiles = eventsDir.listFiles();
			for (File file : eventFiles) {
				if (file.getName().contains("xml.gz")){
					final String absolutePath = file.getAbsolutePath();
					new EpisimEventsReader(manager).readFile( absolutePath );
				}
			}

			DateColumn dateColumn = DateColumn.create( "date" );
			DoubleColumn rColumn = DoubleColumn.create( "r" );

			for(int ii = 0; ii <= eventFiles.length; ii++) {
				// (I think that this is a cheap trick to get the dates.  kai, oct'20)

				int noOfInfectors = 0;
				int noOfInfected = 0;
				for (InfectedPerson ip : infectedPersons.values()) {
					if (ip.getContagiousDay() == ii) {
						noOfInfectors++;
						noOfInfected = noOfInfected + ip.getNoOfInfected();
					}
				}
				if (noOfInfectors != 0) {
					double r = (double) noOfInfected / noOfInfectors;
					final LocalDate date = startDate.plusDays( ii );
					dateColumn.append( date );
					rColumn.append( r );
					log.info( "date=" + date + "; r=" + r );
				}
			}

			rColumns.add(rColumn);
			dateColumns.add(dateColumn);

			ScatterTrace trace = ScatterTrace.builder( dateColumn, rColumn.rolling( 7 ).mean() ).build();
			traces.add(trace);

			log.info("processed scenario " + scenario);
		}

		{
			Figure figure = Figure.builder().addTraces( traces.toArray( new Trace[0] ) ).build();

			figure.setLayout( Layout.builder()
						.xAxis( Axis.builder().type( Axis.Type.DATE ).build() )
						.yAxis( Axis.builder().type( Axis.Type.LOG ).range( Math.log10(0.5),Math.log10( 4. ) ).build() ).width( 1000 ).build() );

			Plot.show( figure, "dada", new File( "output1.html" ) );
		}
//		if ( rColumns.size() >= 2 ) {
//			ScatterTrace trace = ScatterTrace.builder( dateColumns.get(0), rColumns.get(1).divide( rColumns.get(0) ).rolling( 7 ).mean() ).build();
//
//			Figure figure = Figure.builder().addTraces( trace ).build();
//
//			figure.setLayout( Layout.builder()
//						.xAxis( Axis.builder().type( Axis.Type.DATE ).build() )
//						.yAxis( Axis.builder().type( Axis.Type.LINEAR ).range( 0.6,1.2 ).build() ).width( 1000 ).build() );
//
//			Plot.show( figure, "dada", new File( "output2.html" ) );
//		}


	}

	private static class InfectedPerson {

		private String id;
		private int noOfInfected;
		private int contagiousDay;

		InfectedPerson (String id) {
			this.id = id;
			this.noOfInfected= 0;
		}

		String getId() {
			return id;
		}
		void setId(String id) {
			this.id = id;
		}
		int getNoOfInfected() {
			return noOfInfected;
		}
		void increaseNoOfInfectedByOne() {
			this.noOfInfected++;
		}

		int getContagiousDay() {
			return contagiousDay;
		}

		void setContagiousDay(int contagiousDay) {
			this.contagiousDay = contagiousDay;
		}

	}

	private static class Handler implements EpisimPersonStatusEventHandler, EpisimInfectionEventHandler {
		@Override
		public void handleEvent(EpisimInfectionEvent event) {
			String infectorId = event.getInfectorId().toString();
			InfectedPerson infector = infectedPersons.computeIfAbsent(infectorId, InfectedPerson::new );
			infector.increaseNoOfInfectedByOne();
		}
		@Override
		public void handleEvent(EpisimPersonStatusEvent event) {

			if (event.getDiseaseStatus() == DiseaseStatus.contagious) {

				String personId = event.getPersonId().toString();
				InfectedPerson person = infectedPersons.computeIfAbsent(personId, InfectedPerson::new );
				person.setContagiousDay((int) event.getTime() / 86400);
			}
		}
	}
}





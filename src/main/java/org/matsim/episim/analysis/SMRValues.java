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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/**
 * Analysis code to create rValues in post processing by use of infectionEvents.txt
 */

public class SMRValues {

	public static void main(String[] args) throws IOException {
		
		HashSet<String> scenarios = new LinkedHashSet<String>(); 
		scenarios.add("output-berlin-25pct-SNZrestrictsFromCSV-split-alpha-bmbf6-schools1-2-1.0-extrapolation-linear-ciCorrection-0.32-dateOfCiChange-2020-03-08-startDate-2020-02-18-hospitalFactor-1.8-calibrParam-1.1E-5-tracingProba-0.5");
		scenarios.add("output-berlin-25pct-SNZrestrictsFromCSV-split-alpha-bmbf6-schools1-masks0108-1.0-extrapolation-linear-ciCorrection-0.32-dateOfCiChange-2020-03-08-startDate-2020-02-18-hospitalFactor-1.8-calibrParam-1.1E-5-tracingProba-0.5");
		scenarios.add("output-berlin-25pct-SNZrestrictsFromCSV-split-alpha-bmbf6-schools1-noTracing-1.0-extrapolation-linear-ciCorrection-0.32-dateOfCiChange-2020-03-08-startDate-2020-02-18-hospitalFactor-1.8-calibrParam-1.1E-5-tracingProba-0.5");

		for (String scenario : scenarios) {
			HashMap<LocalDate, Double> rvalues = readInfectionEvents(scenario);
		}
		

	}
	
	private static HashMap<LocalDate, Double> readInfectionEvents(String scenario) throws IOException {
				
		HashMap<String, InfectedPerson> infectedPersons = new LinkedHashMap<String, InfectedPerson>();
		
		BufferedReader reader = new BufferedReader(new FileReader("./" + scenario + "/infectionEvents.txt"));
	    String line;
	    int lineNo = 0;
	    while ((line = reader.readLine()) != null) {
	    	if (lineNo == 0) lineNo++;
	    	else {
		    	String[] parts = line.split("\t");
		    	InfectedPerson infectedPerson = new InfectedPerson(parts[2], LocalDate.parse(parts[4]));
		    	infectedPersons.put(infectedPerson.getId(), infectedPerson);
		    	
		    	String infector = parts[1];
		    	if (infectedPersons.containsKey(infector)) {
		    		infectedPersons.get(infector).increaseNoOfInfectedByOne();
		    	}
		    	
		    	lineNo++;
	    	}

	    }
	    	    
	    System.out.println("processed " + lineNo + " line numbers in scenario " + scenario);
	    reader.close();
	    
	    FileWriter fw = new FileWriter(new File("./" + scenario + "/rValues.txt"));
	    BufferedWriter bw = new BufferedWriter(fw);
	    bw.write("date;rValue");
	    
	    HashMap<LocalDate, Double> rValues = new LinkedHashMap<LocalDate, Double>();
	    
	    for(int i = 0; i <= 500; i++) {
	    	
	    	LocalDate date = LocalDate.parse("2020-02-18").plusDays(i);
	    	int noOfInfectors = 0;
	    	int noOfInfected = 0;
	    	for (InfectedPerson ip : infectedPersons.values()) {
	    		if (ip.getDate().equals(date)) {
	    			noOfInfectors++;
	    			noOfInfected = noOfInfected + ip.getNoOfInfected();
	    		}
	    	}
	    	double r = (double) noOfInfected / noOfInfectors;
	    	
	    	bw.newLine();
	    	bw.write(date.toString() + ";" + r);
	    	bw.flush();
	    	rValues.put(date, r);
	    }
	    bw.close();
	    return rValues;
	 
	}


}	
	
	class InfectedPerson {
		
		private String id;
		private int noOfInfected;
		private LocalDate date;
		
		InfectedPerson (String id, LocalDate date) {
			this.id = id;
			this.noOfInfected= 0;
			this.date = date;
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
		void setNoOfInfected(int noOfInfected) {
			this.noOfInfected = noOfInfected;
		}
		void increaseNoOfInfectedByOne() {
			this.noOfInfected++;
		}
		LocalDate getDate() {
			return date;
		}
		void setDate(LocalDate date) {
			this.date = date;
		}
	
}

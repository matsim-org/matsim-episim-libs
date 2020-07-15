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
	
	private static final String WORKINGDIR = "./output-indoorOutdoor/";

	public static void main(String[] args) throws IOException {
		
		
		HashSet<String> scenarios = new LinkedHashSet<String>(); 
		
		 File[] files = new File(WORKINGDIR).listFiles();
		 
		 for (File file : files) {
		        if (file.isDirectory()) {
		        	scenarios.add(file.getName());
		        }
		 }

	    FileWriter fw = new FileWriter(new File(WORKINGDIR + "rValues.txt"));
	    BufferedWriter bw = new BufferedWriter(fw);
	    bw.write("day\tdate\trValue\tscenario\tnewInfections");
	    
		for (String scenario : scenarios) {
			HashMap<LocalDate, Double> rvalues = readInfectionEvents(scenario, bw);
			bw.flush();
		}
		
		bw.close();
	}
	
	private static HashMap<LocalDate, Double> readInfectionEvents(String scenario, BufferedWriter bw2) throws IOException {
				
		HashMap<String, InfectedPerson> infectedPersons = new LinkedHashMap<String, InfectedPerson>();
		
		BufferedReader reader = new BufferedReader(new FileReader(WORKINGDIR + scenario + "/infectionEvents.txt"));
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
	    	    
	    System.out.println("processed " + lineNo + " lines in scenario " + scenario);
	    reader.close();
	    
	    FileWriter fw = new FileWriter(new File(WORKINGDIR + scenario + "/rValues.txt"));
	    BufferedWriter bw = new BufferedWriter(fw);
	    bw.write("day\tdate\trValue\tnewInfections");
	    
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

	    	if (noOfInfectors != 0) {
		    	bw.newLine();
		    	bw2.newLine();
	    		double r = (double) noOfInfected / noOfInfectors;
		    	bw.write(i + "\t" + date.toString() + "\t" + r + "\t" + noOfInfectors);
		    	bw2.write(i + "\t" + date.toString() + "\t" + r + "\t" + scenario + "\t" + noOfInfectors);
		    	rValues.put(date, r);
	    		
	    	}
	    	
	    	bw.flush();
	    	
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

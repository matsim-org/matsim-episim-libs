package org.matsim.scenarioCreation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;



public class ReducePopulationAttributesFile {

	public static void main(String[] args) throws IOException {
		
		File file = new File("../reducedAttributes.xml");    
        FileWriter fw = new FileWriter(file);
        BufferedWriter bw = new BufferedWriter(fw);
        
        BufferedReader idReader = new BufferedReader(new FileReader("../shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_adults_idList.txt"));
        ArrayList<String> list = new ArrayList<String>();
        String strCurrentLineIdReader;
        
        while ((strCurrentLineIdReader = idReader.readLine()) != null) {
        	list.add(strCurrentLineIdReader);
        }
        idReader.close();
        
        System.out.println("done reading. " + list.size());
        
        BufferedReader atrReader = new BufferedReader(new FileReader("../populationAttributes.xml"));
        String atrStrCurrentLine;
        
        bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        bw.newLine();
        bw.write("<!DOCTYPE objectAttributes SYSTEM \"http://matsim.org/files/dtd/objectattributes_v1.dtd\">");
        bw.newLine();
        bw.newLine();
        bw.write("<objectAttributes>");
        bw.newLine();
        bw.flush();

        int writeCounter = 0;
        boolean write = false;
        while ((atrStrCurrentLine = atrReader.readLine()) != null) {
        	
        	if (atrStrCurrentLine.contains("<object id=")) {
        		String[] personId = atrStrCurrentLine.split("\"");
        		if (list.contains(personId[1])) {
        			write = true;
        			writeCounter++;
        			if (writeCounter % 1000000 == 0) {
        				System.out.println(writeCounter / 1000000);
        			}
        			
        		}
        		else {
        			write = false;
        		}
         
        	}
        	
        	if (write) {
            	bw.write(atrStrCurrentLine);
            	bw.newLine();
        	}

        }
        bw.write("</objectAttributes>");
        bw.flush();
        bw.close();
        atrReader.close();

	}

}

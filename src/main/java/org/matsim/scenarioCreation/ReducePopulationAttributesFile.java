/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.scenarioCreation;

import java.io.*;
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

				} else {
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

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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.concurrent.Callable;

import static picocli.CommandLine.*;

/**
 * Executable class, see description.
 */
@Command(
		name = "downloadVaccinationData",
		description = "Download number of vaccinated people from rki.",
		mixinStandardHelpOptions = true
)
public class DownloadVaccinationData implements Callable<Integer> {

	private static final String URL = "https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Daten/Impfquotenmonitoring.xlsx?__blob=publicationFile";

	private static Logger log = LogManager.getLogger(DownloadVaccinationData.class);

	@Option(names = "--output", description = "Output file", defaultValue = "germanyVaccinations.csv")
	private Path output;


	public static void main(String[] args) {
		System.exit(new CommandLine(new DownloadVaccinationData()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		BufferedWriter writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE);
		CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withCommentMarker('#').withHeaderComments(
				"Source: https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Daten/Impfquoten-Tab.html"
		).withHeader(
				"date", "nVaccinated", "nSecondVaccination")
		);

		try (var in = new URL(URL).openStream()) {

			Workbook wb = WorkbookFactory.create(in);

			Sheet sheet = wb.getSheetAt(3);

			for (Iterator<Row> it = sheet.rowIterator(); it.hasNext(); ) {

				Row row = it.next();

				try {
					LocalDateTime date = row.getCell(0).getLocalDateTimeCellValue();
					double n = row.getCell(1).getNumericCellValue();
					double n2 = row.getCell(2).getNumericCellValue();

					printer.printRecord(date.toLocalDate().format(DateTimeFormatter.ISO_DATE), n, n2);
				} catch (Exception e) {
					// ignore
				}
			}

		}

		writer.close();

		return 0;
	}

}

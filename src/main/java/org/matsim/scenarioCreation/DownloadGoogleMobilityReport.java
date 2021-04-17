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
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

/**
 * Executable class, see description.
 */
@Command(
		name = "downloadGoogleMobilityReport",
		description = "Download google mobility report and filter specific region.",
		mixinStandardHelpOptions = true
)
public class DownloadGoogleMobilityReport implements Callable<Integer> {

	private static final String URL = "https://www.gstatic.com/covid19/mobility/Global_Mobility_Report.csv";

	private static Logger log = LogManager.getLogger(DownloadGoogleMobilityReport.class);

	@Option(names = "--region", description = "Region id", defaultValue = "DE", required = true)
	private String region;

	@Option(names = "--sub-region", description = "Sub-region id", defaultValue = "DE-BE")
	private String subRegion;

	@Option(names = "--from", description = "From date (inclusive).", defaultValue = "2020-02-01", required = true)
	private LocalDate fromDate;

	@Option(names = "--to", description = "End data (optional). If not given will use all data", required = false)
	private LocalDate toDate;

	@Option(names = "--output", description = "Output file", defaultValue = "berlinGoogleMobility.csv")
	private Path output;


	public static void main(String[] args) {
		System.exit(new CommandLine(new DownloadGoogleMobilityReport()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		log.info("Loading mobility data for {} - {}", region, subRegion);

		try (var in = new InputStreamReader(new URL(URL).openStream())) {

			CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT.withFirstRecordAsHeader());

			BufferedWriter writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE);
			CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withCommentMarker('#').withHeaderComments(
					"Source: Google - " + URL
					).withHeader(parser.getHeaderNames().toArray(new String[0]))
			);


			int i = 0;

			for (CSVRecord record : parser) {

				String regionCode = record.get("country_region_code");

				if (!regionCode.equals(region))
					continue;

				String subRegionCode = record.get("iso_3166_2_code");

				if (subRegion != null && !subRegion.equals(subRegionCode))
					continue;

				LocalDate date = LocalDate.parse(record.get("date"));

				if (date.isBefore(fromDate))
					continue;

				if (toDate != null && date.isAfter(toDate))
					continue;

				i++;
				printer.printRecord(record);
			}

			log.info("Done writing {} entries", i);

			writer.close();
		}


		return 0;
	}

}

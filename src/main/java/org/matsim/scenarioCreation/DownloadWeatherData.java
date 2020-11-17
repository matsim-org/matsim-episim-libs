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

import static picocli.CommandLine.*;

/**
 * Executable class, see description.
 */
@Command(
		name = "downloadWeatherData",
		description = "Download weather data for specific station and time interval from meteostat.",
		mixinStandardHelpOptions = true
)
public class DownloadWeatherData implements Callable<Integer> {

	private static final String ENDPOINT = "https://bulk.meteostat.net/daily/%s.csv.gz";

	private static Logger log = LogManager.getLogger(DownloadWeatherData.class);

	@Parameters(paramLabel = "STATION", arity = "1", description = "Meteostat station id.", defaultValue = "10382")
	private String station;

	@Option(names = "--from", description = "From date (inclusive).", defaultValue = "2020-02-01", required = true)
	private LocalDate fromDate;

	@Option(names = "--to", description = "End data (optional). If not given will use all data", required = false)
	private LocalDate toDate;

	@Option(names = "--output", description = "Output file", defaultValue = "berlinWeather.csv")
	private Path output;


	public static void main(String[] args) {
		System.exit(new CommandLine(new DownloadWeatherData()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		log.info("Loading weather data for station {}", station);

		BufferedWriter writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE);
		CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
				"date", "tavg", "tmin", "tmax", "prcp", "snow", "wdir", "wspd", "wpgt", "pres", "tsun")
		);

		int i = 0;

		try (var in = new InputStreamReader(new GZIPInputStream(new URL(String.format(ENDPOINT, station)).openStream()))) {

			CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT);

			for (CSVRecord record : parser) {

				LocalDate date = LocalDate.parse(record.get(0));

				if (date.isBefore(fromDate))
					continue;

				if (toDate != null && date.isAfter(toDate))
					continue;

				i++;
				printer.printRecord(record);
			}
		}

		log.info("Done writing {} days", i);

		writer.close();

		return 0;
	}

}

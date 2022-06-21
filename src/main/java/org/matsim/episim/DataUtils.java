package org.matsim.episim;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.episim.model.VirusStrain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Utils for working with various data sources.
 */
public final class DataUtils {

	private DataUtils() {
	}


	/**
	 * Read disease import as cases per day.
	 */
	public static NavigableMap<LocalDate, Double> readDiseaseImport(Path path) {

		try (CSVParser csv = new CSVParser(Files.newBufferedReader(path), CSVFormat.DEFAULT.withFirstRecordAsHeader().withCommentMarker('#'))) {

			DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yy");

			TreeMap<LocalDate, Double> result = new TreeMap<>();

			for (CSVRecord record : csv) {

				LocalDate date = LocalDate.parse(record.get(0), fmt);
				double value = Double.parseDouble(record.get(1));

				result.put(date, value);
			}

			return result;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Read
	 *
	 * @return percentage of strain for each date.
	 */
	public static NavigableMap<LocalDate, Map<VirusStrain, Double>> readVOC(Path path) {

		try (CSVParser csv = new CSVParser(Files.newBufferedReader(path), CSVFormat.DEFAULT.withFirstRecordAsHeader().withCommentMarker('#'))) {

			TreeMap<LocalDate, Map<VirusStrain, Double>> result = new TreeMap<>();


			for (CSVRecord record : csv) {

				LocalDate date = LocalDate.parse(record.get(0));
				Map<VirusStrain, Double> strains = Map.of(
						VirusStrain.SARS_CoV_2, Double.parseDouble(record.get("Wildtyp Fälle pro Tag (%)")),
						VirusStrain.DELTA, Double.parseDouble(record.get("Delta (B.1.617) Fälle pro Tag (%)")),
						VirusStrain.ALPHA, getOrZero(record, "Alpha (B.1.1.7) Fälle pro Tag (%)"),
						VirusStrain.B1351, getOrZero(record, "Beta (B.1.351) Fälle pro Tag (%)"),
						VirusStrain.OMICRON_BA1, getOrZero(record, "BA.1 Fälle pro Tag (%)"),
						VirusStrain.OMICRON_BA2, getOrZero(record, "BA.2 Fälle pro Tag (%)"),
						VirusStrain.OMICRON_BA5, getOrZero(record, "BA.5 Fälle pro Tag Deutschland (%)")
				);

				result.put(date, strains);
			}


			return result;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static double getOrZero(CSVRecord record, String column) {
		return record.isSet(column) ? Double.parseDouble(record.get(column)) : 0;
	}
}

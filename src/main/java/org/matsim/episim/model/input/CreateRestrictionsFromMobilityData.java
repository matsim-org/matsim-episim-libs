package org.matsim.episim.model.input;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.episim.policy.FixedPolicy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Create restrictions from google mobility report.
 */
public class CreateRestrictionsFromMobilityData implements RestrictionInput {

	private static final String MARKER = "_percent_change_from_baseline";

	private Path input;

	public static void main(String[] args) throws IOException {

		CreateRestrictionsFromMobilityData r = new CreateRestrictionsFromMobilityData();
		r.setInput(Path.of("berlinGoogleMobility.csv"));


		r.createPolicy();
	}

	/**
	 * Read in all changes, grouped by date and type.
	 */
	private NavigableMap<LocalDate, Object2DoubleMap<String>> readChanges() {

		try (BufferedReader reader = IOUtils.getBufferedReader(input.toString())) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withCommentMarker('#').withFirstRecordAsHeader()
					.parse(reader);

			NavigableMap<LocalDate, Object2DoubleMap<String>> result = new TreeMap<>();

			for (CSVRecord record : parse.getRecords()) {

				LocalDate date = LocalDate.parse(record.get("date"));
				Object2DoubleOpenHashMap<String> map = new Object2DoubleOpenHashMap<>();
				result.put(date, map);
				System.out.println(record);

				for (String header : parse.getHeaderNames()) {
					if (header.endsWith(MARKER)) {
						// some entries are blank
						if (!record.get(header).isBlank())
							map.put(header.substring(0, header.indexOf(MARKER)), Double.parseDouble(record.get(header)));
					}
				}
			}

			return result;

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

	}

	@Override
	public FixedPolicy.ConfigBuilder createPolicy() throws IOException {

		NavigableMap<LocalDate, Object2DoubleMap<String>> changes = readChanges();

		System.out.println(changes);

		return null;
	}

	@Override
	public CreateRestrictionsFromMobilityData setInput(Path input) {
		this.input = input;
		return this;
	}
}

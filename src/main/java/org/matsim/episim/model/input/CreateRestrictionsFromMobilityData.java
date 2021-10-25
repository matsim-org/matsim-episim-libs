package org.matsim.episim.model.input;

import com.google.common.collect.Iterables;
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
import java.util.*;

/**
 * Create restrictions from google mobility report.
 */
public class CreateRestrictionsFromMobilityData implements RestrictionInput {

	private static final String MARKER = "_percent_change_from_baseline";

	private Path input;

	private final static Map<String, List<String>> MAPPING = Map.of(
			"grocery_and_pharmacy", List.of("shop_daily", "errands"),
			"parks", List.of(""),
			"residential", List.of(""),
			"retail_and_recreation", List.of("leisure", "restaurant", "shop_other", "business"),
			"transit_stations", List.of(),
			"workplaces", List.of("work")
	);

	/**
	 * Read in all changes, grouped by date and type.
	 */
	private NavigableMap<String, Object2DoubleMap<LocalDate>> readChanges() {

		try (BufferedReader reader = IOUtils.getBufferedReader(input.toString())) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withCommentMarker('#').withFirstRecordAsHeader()
					.parse(reader);

			NavigableMap<String, Object2DoubleMap<LocalDate>> result = new TreeMap<>();

			for (CSVRecord record : parse.getRecords()) {

				LocalDate date = LocalDate.parse(record.get("date"));

				for (String header : parse.getHeaderNames()) {
					if (header.endsWith(MARKER)) {
						// some entries are blank
						if (!record.get(header).isBlank()) {
							String type = header.substring(0, header.indexOf(MARKER));

							Object2DoubleMap<LocalDate> map = result.computeIfAbsent(type, (k) -> new Object2DoubleOpenHashMap<>());
							map.put(date, Double.parseDouble(record.get(header)));
						}
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

		NavigableMap<String, Object2DoubleMap<LocalDate>> changes = readChanges();

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();

		for (Map.Entry<String, Object2DoubleMap<LocalDate>> e : changes.entrySet()) {

			Object2DoubleMap<LocalDate> values = e.getValue();
			LocalDate start = Objects.requireNonNull(Iterables.getFirst(values.keySet(), null), "CSV is empty");

			RestrictionInput.resampleAvgWeekday(values, start, (date, f) -> {

				String[] acts = MAPPING.get(e.getKey()).toArray(new String[0]);

				double frac = 1.0;
				builder.restrict(date, frac, acts);
			});

		}


		return builder;
	}

	@Override
	public CreateRestrictionsFromMobilityData setInput(Path input) {
		this.input = input;
		return this;
	}
}

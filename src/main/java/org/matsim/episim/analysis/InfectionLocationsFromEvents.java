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
 * #L%
 */
package org.matsim.episim.analysis;

import com.google.inject.Inject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.run.AnalysisCommand;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/**
 * Creates the geocoded infection sidecar consumed by the Episim infection-map viewer.
 *
 * <p>The input population uses the Cologne grid coordinates in EPSG:25832. The output contains
 * WGS84 home coordinates and is intentionally written outside the per-run ZIP archive.</p>
 */
public final class InfectionLocationsFromEvents implements OutputAnalysis {

	private static final String SOURCE_CRS = "EPSG:25832";

	private Scenario scenario;
	private LocalDate startDate;

	/** Creates an analysis whose scenario and start date are supplied by the batch injector. */
	public InfectionLocationsFromEvents() {
	}

	InfectionLocationsFromEvents(Scenario scenario, LocalDate startDate) {
		this.scenario = scenario;
		this.startDate = startDate;
	}

	@Inject
	void initialize(Scenario scenario, Config config) {
		this.scenario = scenario;
		this.startDate = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class).getStartDate();
	}

	@Override
	public Integer call() {
		throw new UnsupportedOperationException("This analysis must be run as batch post-processing");
	}

	@Override
	public void analyzeOutput(Path output) throws IOException {
		if (scenario == null || startDate == null)
			throw new IllegalStateException("Scenario and start date were not injected");

		String id = AnalysisCommand.getScenarioPrefix(output);
		Path infections = output.resolve(id + "infectionEvents.txt");
		Path target = output.resolve(id + "infectionLoc.csv.gz");
		CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation(
			SOURCE_CRS, TransformationFactory.WGS84);

		try (BufferedReader reader = Files.newBufferedReader(infections, StandardCharsets.UTF_8);
			 CSVParser parser = new CSVParser(reader,
				 CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader());
			 GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(target));
			 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8))) {

			writer.write("home_lon,home_lat,daysSinceStart,infection_type");
			writer.newLine();

			for (CSVRecord record : parser) {
				Person person = scenario.getPopulation().getPersons().get(Id.createPersonId(record.get("infected")));
				if (person == null) continue;

				Object homeX = person.getAttributes().getAttribute("homeX");
				Object homeY = person.getAttributes().getAttribute("homeY");
				if (!(homeX instanceof Number) || !(homeY instanceof Number)) continue;

				Coord coordinate = transformation.transform(new Coord(
					((Number) homeX).doubleValue(), ((Number) homeY).doubleValue()));
				long day = ChronoUnit.DAYS.between(startDate, LocalDate.parse(record.get("date")));

				writer.write(String.format(Locale.ROOT, "%.7f,%.7f,%d,normal",
					coordinate.getX(), coordinate.getY(), day));
				writer.newLine();
			}
		}
	}
}

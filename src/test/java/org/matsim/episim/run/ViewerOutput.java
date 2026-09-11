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
package org.matsim.episim.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.run.BatchOutputPacker;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Output layout of a single scenario-test run that can be opened in the Episim viewer (covid-sim).
 *
 * <p>Mirrors {@code StarterBatchOpenCologne} in {@code matsim-episim}. Below the root given by the
 * {@value #OUTPUT_ENV} environment variable &mdash; typically a local SVN working copy that is committed by
 * hand &mdash; every run gets {@code <date>/<PREFIX>-<NNNNN>/} with:</p>
 * <ul>
 *   <li>{@code output/} &mdash; the simulation output of run {@code <PREFIX>1} plus {@code _info.txt},
 *       {@code metadata.yaml} and {@code notes.md}, i.e. the layout of {@code RunParallel --write-metadata};</li>
 *   <li>{@code output-vis-keep-seeds/} and {@code output-vis-no-seeds/} &mdash; the viewer packages written by
 *       {@link BatchOutputPacker}.</li>
 * </ul>
 * <p>Without {@value #OUTPUT_ENV} the fallback root (usually the test's output directory) is used. Nothing is
 * committed to SVN from here.</p>
 */
final class ViewerOutput {

	/** Environment variable naming the root directory, as in {@code matsim-episim}. */
	static final String OUTPUT_ENV = "EPISIM_OUTPUT";

	private static final int RUN_INDEX_WIDTH = 5;

	private final String prefix;
	private final Path runDirectory;
	private final ZonedDateTime startedAt = ZonedDateTime.now();

	private ViewerOutput(String prefix, Path runDirectory) {
		this.prefix = prefix;
		this.runDirectory = runDirectory;
	}

	/**
	 * Creates the next run directory {@code <root>/<today>/<prefix>-<NNNNN>}; the root is {@value #OUTPUT_ENV} if set,
	 * otherwise {@code fallbackRoot}.
	 */
	static ViewerOutput create(String prefix, Path fallbackRoot) throws IOException {
		return create(prefix, rootFrom(System.getenv(OUTPUT_ENV), fallbackRoot), LocalDate.now());
	}

	/**
	 * Creates the next run directory {@code <root>/<date>/<prefix>-<NNNNN>}, numbering runs per prefix and date.
	 */
	static ViewerOutput create(String prefix, Path root, LocalDate date) throws IOException {
		if (prefix == null || !prefix.matches("[A-Za-z0-9]+"))
			throw new IllegalArgumentException("Run prefix may only contain letters and digits but was '" + prefix + "'");

		Path dateDirectory = Files.createDirectories(root.resolve(date.toString()));
		int next;
		try (var entries = Files.list(dateDirectory)) {
			next = entries.filter(Files::isDirectory)
					.mapToInt(directory -> parseRunIndex(directory, prefix))
					.filter(index -> index > 0)   // other prefixes and unrelated directories
					.max()
					.orElse(0) + 1;
		}

		while (true) {
			Path runDirectory = dateDirectory.resolve(
					String.format(Locale.ROOT, "%s-%0" + RUN_INDEX_WIDTH + "d", prefix, next));
			try {
				return new ViewerOutput(prefix, Files.createDirectory(runDirectory));
			} catch (FileAlreadyExistsException ignored) {
				next++;
			}
		}
	}

	/**
	 * The configured root, or {@code fallback} if none is configured.
	 */
	static Path rootFrom(String configured, Path fallback) {
		return configured == null || configured.isBlank() ? fallback : Path.of(configured.strip());
	}

	private static int parseRunIndex(Path directory, String prefix) {
		String name = directory.getFileName().toString();
		String start = prefix + "-";
		if (!name.startsWith(start))
			return -1;
		String digits = name.substring(start.length());
		return !digits.isEmpty() && digits.chars().allMatch(Character::isDigit) ? Integer.parseInt(digits) : -1;
	}

	/** {@code <root>/<date>/<prefix>-<NNNNN>}. */
	Path runDirectory() {
		return runDirectory;
	}

	/** Batch directory: simulation output plus {@code _info.txt}, {@code metadata.yaml} and {@code notes.md}. */
	Path simulation() {
		return runDirectory.resolve("output");
	}

	/** MATSim output directory of the single run. */
	Path runOutput() {
		return simulation().resolve(runId());
	}

	/** Run id and file prefix, {@code <name><id>} as in {@code RunParallel}. */
	String runId() {
		return prefix + "1";
	}

	/** Viewer package that keeps every seed as its own run. */
	Path visualizationWithSeeds() {
		return runDirectory.resolve("output-vis-keep-seeds");
	}

	/** Viewer package that averages runs differing only by seed. */
	Path visualizationWithoutSeeds() {
		return runDirectory.resolve("output-vis-no-seeds");
	}

	/**
	 * Points the MATSim output at this run and prefixes the output files with the run id, as {@code RunParallel} does.
	 * Call before the runner is built.
	 */
	void configure(Config config) throws IOException {
		Files.createDirectories(runOutput());
		config.controller().setOutputDirectory(runOutput().toString());
		config.controller().setRunId(runId());
	}

	/**
	 * Writes {@code _info.txt}, {@code metadata.yaml} and {@code notes.md} for the finished run and packs it for the
	 * viewer, once keeping seeds and once aggregated.
	 *
	 * @param region     {@code city} in the viewer metadata, e.g. {@code cologne}
	 * @param name       {@code runName} in the viewer metadata and value of the {@code pathogen} parameter
	 * @param iterations simulated days, used for the viewer end date
	 * @param district   exact district value kept from {@code infections.txt}, e.g. {@code Köln}
	 */
	void pack(Config config, String region, String name, int iterations, String district) throws IOException {
		LocalDate start = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class).getStartDate();
		long seed = config.global().getRandomSeed();

		writeInfo(seed, name);
		writeMetadata(region, name, start, iterations);
		writeNotes(name, start, iterations, seed);

		new BatchOutputPacker(simulation(), visualizationWithSeeds(), district, true).pack();
		new BatchOutputPacker(simulation(), visualizationWithoutSeeds(), district, false).pack();
	}

	private void writeInfo(long seed, String name) throws IOException {
		String info = String.join(";", "RunScript", "Config", "RunId", "Output", "seed", "pathogen") + "\n"
				+ String.join(";", "na", runId() + ".config.xml", runId(), runOutput().toAbsolutePath().toString(),
				Long.toString(seed), name) + "\n";
		Files.writeString(simulation().resolve("_info.txt"), info, StandardCharsets.UTF_8);
	}

	/**
	 * Same keys and serialisation as {@code CreateBatteryForCluster.writeMetadata} with {@code PreparedRun.getMetadata}.
	 */
	private void writeMetadata(String region, String name, LocalDate start, int iterations) throws IOException {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory()
				.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
				.registerModule(new JavaTimeModule())
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("readme", "notes.md");
		metadata.put("zip", "summaries.zip");
		metadata.put("info", "_info.txt");
		metadata.put("zipFolder", "summaries");
		metadata.put("timestamp", LocalDate.now());
		metadata.put("viewerVersion", 2);
		metadata.put("city", region);
		metadata.put("runName", name);
		metadata.put("defaultStartDate", start);
		metadata.put("endDate", start.plusDays(iterations - 1L).toString());
		metadata.put("startDates", List.of(start));

		// default option group, as generated by PreparedRun for runs without explicit options
		Map<String, Object> options = new LinkedHashMap<>();
		options.put("day", -1);
		options.put("heading", "");
		options.put("subheading", "");
		options.put("measures", List.of(measure("seed", "Seed"), measure("pathogen", "Pathogen")));
		metadata.put("optionGroups", List.of(options));

		mapper.writeValue(simulation().resolve("metadata.yaml").toFile(), metadata);
	}

	private static Map<String, Object> measure(String measure, String title) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("measure", measure);
		result.put("title", title);
		return result;
	}

	private void writeNotes(String name, LocalDate start, int iterations, long seed) throws IOException {
		ZonedDateTime finishedAt = ZonedDateTime.now();
		String notes = """
				# Episim scenario test: %s

				Single run of `%s`, written by `ViewerOutput` from the libs scenario tests.

				- Start date: `%s`
				- Iterations: `%d`
				- Seed: `%d`
				- Started by: `%s`
				- Host: `%s`
				- Started at: `%s`
				- Finished at: `%s`
				- Duration: `%s`
				""".formatted(
				name,
				runId(),
				start,
				iterations,
				seed,
				System.getProperty("user.name", "unknown"),
				resolveHostName(),
				DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(startedAt),
				DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(finishedAt),
				Duration.between(startedAt, finishedAt)
		);
		Files.writeString(simulation().resolve("notes.md"), notes, StandardCharsets.UTF_8);
	}

	private static String resolveHostName() {
		String configuredHost = System.getenv("HOSTNAME");
		if (configuredHost != null && !configuredHost.isBlank())
			return configuredHost;
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException ignored) {
			return "unknown";
		}
	}
}

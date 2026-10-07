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
package org.matsim.run;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Converts a local {@link RunParallel} output into the layout consumed by the Episim viewer.
 *
 * <p>This is the Java equivalent of the historic {@code collect.sh} followed by
 * {@code analysis/utils.py}. It creates {@code metadata.yaml}, {@code _info.txt}, and one zip
 * per visible run below {@code summaries/}. The simulation output is only read, never changed.</p>
 *
 * <p>This class has no command-line or process-exit concerns and can be called directly from a
 * scenario runner. Use {@link BatchOutputPackerRunner} for command-line execution.</p>
 */
public final class BatchOutputPacker {

	private static final Set<String> COPIED_SUFFIXES = Set.of(
		".diseaseImport.tsv",
		".outdoorFraction.tsv",
		".strains.tsv",
		".vaccinations.tsv",
		".vaccinationsDetailed.tsv",
		".secondaryAttackRate.txt",
		".config.xml"
	);

	/**
	 * Directory of the batch output holding observed data for the viewer; copied as is.
	 */
	public static final String OBSERVED_DIRECTORY = "observed";

	private final Path input;
	private final Path output;
	private final String district;
	private final boolean keepSeeds;

	/**
	 * Creates a packer that averages runs which only differ by seed.
	 */
	public BatchOutputPacker(Path input, Path output, String district) {
		this(input, output, district, false);
	}

	/**
	 * Creates a packer.
	 *
	 * @param input RunParallel output containing {@code _info.txt} and {@code metadata.yaml}
	 * @param output new or empty viewer output directory
	 * @param district exact district value to retain from {@code infections.txt}
	 * @param keepSeeds whether every seed should remain a separate viewer run
	 */
	public BatchOutputPacker(Path input, Path output, String district, boolean keepSeeds) {
		this.input = Objects.requireNonNull(input, "input").toAbsolutePath().normalize();
		this.output = Objects.requireNonNull(output, "output").toAbsolutePath().normalize();
		this.district = Objects.requireNonNull(district, "district");
		if (district.isBlank()) throw new IllegalArgumentException("District must not be blank");
		this.keepSeeds = keepSeeds;
	}

	/**
	 * Packs the configured batch output. The source directory is never modified.
	 */
	public void pack() throws IOException {

		validateInput();
		prepareOutput();

		InfoTable info = InfoTable.read(input.resolve("_info.txt"));
		List<SourceRun> runs = resolveRuns(info);
		if (runs.isEmpty())
			throw new IllegalArgumentException("No runs listed in " + input.resolve("_info.txt"));

		Files.copy(input.resolve("metadata.yaml"), output.resolve("metadata.yaml"), StandardCopyOption.COPY_ATTRIBUTES);
		copyOptional(input.resolve("notes.md"), output.resolve("notes.md"));
		copyObserved();
		Path summaries = Files.createDirectories(output.resolve("summaries"));

		if (!keepSeeds && info.header.contains("seed")) {
			packAggregated(info, runs, summaries);
		} else {
			packSeparate(info, runs, summaries);
		}

	}

	private static void copyOptional(Path source, Path target) throws IOException {
		if (Files.isRegularFile(source))
			Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
	}

	/**
	 * Copies the observed data the viewer compares the model with ({@code observed} in metadata.yaml refers to
	 * these files relative to the run folder).
	 */
	private void copyObserved() throws IOException {
		Path source = input.resolve(OBSERVED_DIRECTORY);
		if (!Files.isDirectory(source)) return;

		Path target = Files.createDirectories(output.resolve(OBSERVED_DIRECTORY));
		try (DirectoryStream<Path> files = Files.newDirectoryStream(source, Files::isRegularFile)) {
			for (Path file : files)
				Files.copy(file, target.resolve(file.getFileName()), StandardCopyOption.COPY_ATTRIBUTES);
		}
	}

	private void validateInput() {
		if (!Files.isDirectory(input))
			throw new IllegalArgumentException("Input directory does not exist: " + input);
		for (String required : List.of("_info.txt", "metadata.yaml")) {
			if (!Files.isRegularFile(input.resolve(required)))
				throw new IllegalArgumentException("Missing " + required + " in " + input);
		}
	}

	private void prepareOutput() throws IOException {
		if (Files.exists(output)) {
			if (!Files.isDirectory(output))
				throw new IllegalArgumentException("Output is not a directory: " + output);
			try (DirectoryStream<Path> entries = Files.newDirectoryStream(output)) {
				if (entries.iterator().hasNext())
					throw new IllegalArgumentException("Output directory must be empty: " + output);
			}
		} else {
			Files.createDirectories(output);
		}
	}

	private List<SourceRun> resolveRuns(InfoTable info) throws IOException {
		int runIdColumn = info.column("RunId");
		int outputColumn = info.column("Output");
		List<SourceRun> result = new ArrayList<>();

		for (List<String> row : info.rows) {
			String runId = row.get(runIdColumn);
			Path runDirectory = resolveRunDirectory(row.get(outputColumn), runId);
			Path infectionFile = findFile(runDirectory, runId, ".infections.txt");
			if (infectionFile == null)
				throw new IllegalArgumentException("Missing " + runId + ".infections.txt in " + runDirectory);
			result.add(new SourceRun(runId, row, runDirectory));
		}
		return result;
	}

	private Path resolveRunDirectory(String configuredOutput, String runId) throws IOException {
		Path configured = Path.of(configuredOutput);
		List<Path> candidates = new ArrayList<>();
		if (configured.isAbsolute()) candidates.add(configured);
		candidates.add(input.resolve(configured));
		if (configured.getNameCount() > 1 && configured.getName(0).toString().equals(input.getFileName().toString()))
			candidates.add(input.resolve(configured.subpath(1, configured.getNameCount())));

		for (Path candidate : candidates) {
			if (Files.isDirectory(candidate.normalize())) return candidate.normalize();
		}

		try (var directories = Files.list(input)) {
			return directories
				.filter(Files::isDirectory)
				.filter(dir -> findFileUnchecked(dir, runId, ".infections.txt") != null)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
					"Cannot resolve output directory for run " + runId + " from " + configuredOutput));
		}
	}

	private void packSeparate(InfoTable info, List<SourceRun> runs, Path summaries) throws IOException {
		info.write(output.resolve("_info.txt"));
		for (SourceRun run : runs) {
			try (ZipArchiveOutputStream zip = zip(summaries.resolve(run.id + ".zip"))) {
				for (SelectedFile file : selectedFiles(run)) {
					if (file.viewerSuffix.equals("infections.txt.csv")) {
						Table infections = Table.read(file.path, file.filter);
						putTable(zip, run.id + "." + file.viewerSuffix, infections);
						putTable(zip, run.id + ".infectionsPerSeed.tsv",
							Table.infectionsPerSeed(List.of(infections)));
					} else {
						putFile(zip, run.id + "." + file.viewerSuffix, file.path, file.filter);
					}
				}
			}
			copyInfectionLocations(run, summaries.resolve(run.id + ".infectionLoc.csv.gz"));
		}
	}

	private void packAggregated(InfoTable info, List<SourceRun> runs, Path summaries) throws IOException {
		List<Integer> parameterColumns = info.parameterColumns();
		int seedColumn = info.header.indexOf("seed");
		parameterColumns.remove(Integer.valueOf(seedColumn));

		Map<List<String>, List<SourceRun>> groups = new LinkedHashMap<>();
		for (SourceRun run : runs) {
			List<String> key = parameterColumns.stream().map(run.row::get).collect(Collectors.toList());
			groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(run);
		}

		writeAggregatedInfo(info, parameterColumns, groups.keySet());

		int targetId = 0;
		for (List<SourceRun> group : groups.values()) {
			writeAggregatedRun(summaries.resolve(targetId + ".zip"), Integer.toString(targetId), group);
			targetId++;
		}
	}

	private void writeAggregatedInfo(InfoTable source, List<Integer> parameterColumns,
		Collection<List<String>> keys) throws IOException {

		try (BufferedWriter writer = Files.newBufferedWriter(output.resolve("_info.txt"), StandardCharsets.UTF_8)) {
			List<String> header = new ArrayList<>(List.of("RunScript", "Config", "RunId", "Output"));
			parameterColumns.stream().map(source.header::get).forEach(header::add);
			writer.write(String.join(";", header));
			writer.newLine();

			int runId = 0;
			for (List<String> key : keys) {
				List<String> row = new ArrayList<>(List.of("na", "na", Integer.toString(runId++), "na"));
				row.addAll(key);
				writer.write(String.join(";", row));
				writer.newLine();
			}
		}
	}

	private void writeAggregatedRun(Path target, String targetId, List<SourceRun> runs) throws IOException {
		Map<String, List<SelectedFile>> bySuffix = new LinkedHashMap<>();
		for (SourceRun run : runs) {
			for (SelectedFile file : selectedFiles(run)) {
				if (file.viewerSuffix.endsWith("config.xml")) continue;
				bySuffix.computeIfAbsent(file.viewerSuffix, ignored -> new ArrayList<>()).add(file);
			}
		}

		try (ZipArchiveOutputStream zip = zip(target)) {
			for (Map.Entry<String, List<SelectedFile>> entry : bySuffix.entrySet()) {
				List<Table> tables = new ArrayList<>();
				for (SelectedFile file : entry.getValue()) tables.add(Table.read(file.path, file.filter));
				if (tables.isEmpty()) continue;

				Table aggregated = entry.getKey().endsWith("vaccinationsDetailed.tsv")
					? Table.aggregateVaccinationsDetailed(tables)
					: Table.aggregateByRow(tables);
				putTable(zip, targetId + "." + entry.getKey(), aggregated);

				if (entry.getKey().equals("infections.txt.csv"))
					putTable(zip, targetId + ".infectionsPerSeed.tsv", Table.infectionsPerSeed(tables));
			}
		}

		// A location map cannot be averaged meaningfully. Use the first seed as a
		// representative map for the aggregated run.
		for (SourceRun run : runs) {
			if (copyInfectionLocations(run, target.resolveSibling(targetId + ".infectionLoc.csv.gz")))
				break;
		}
	}

	private static boolean copyInfectionLocations(SourceRun run, Path target) throws IOException {
		Path source = findFile(run.directory, run.id, ".infectionLoc.csv.gz");
		if (source == null) return false;
		Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
		return true;
	}

	private List<SelectedFile> selectedFiles(SourceRun run) throws IOException {
		List<SelectedFile> result = new ArrayList<>();
		try (var files = Files.list(run.directory)) {
			files.filter(Files::isRegularFile)
				.sorted(Comparator.comparing(path -> path.getFileName().toString()))
				.forEach(path -> {
					String name = path.getFileName().toString();
					String suffix = suffixAfterRunId(name, run.id);
					if (suffix == null) return;

					if (suffix.equals("infections.txt")) {
						result.add(new SelectedFile(path, "infections.txt.csv", this::infectionLineSelected));
					} else if (suffix.equals("restrictions.txt") || suffix.equals("rValues.txt")) {
						result.add(new SelectedFile(path, suffix + ".csv", line -> true));
					} else if (suffix.equals("infectionsPerActivity.txt")) {
						result.add(new SelectedFile(path, suffix + ".tsv", line -> true));
					} else if (COPIED_SUFFIXES.contains("." + suffix) || suffix.startsWith("post.") || suffix.contains(".post.")) {
						result.add(new SelectedFile(path, suffix, line -> true));
					}
				});
		}
		return result;
	}

	private boolean infectionLineSelected(String line) {
		String[] columns = line.split("\t", -1);
		return columns.length > 0 && columns[columns.length - 1].equals(district);
	}

	private static String suffixAfterRunId(String fileName, String runId) {
		String prefix = runId + ".";
		return fileName.startsWith(prefix) ? fileName.substring(prefix.length()) : null;
	}

	private static Path findFile(Path directory, String runId, String suffix) throws IOException {
		Path exact = directory.resolve(runId + suffix);
		if (Files.isRegularFile(exact)) return exact;
		try (var files = Files.list(directory)) {
			return files.filter(Files::isRegularFile)
				.filter(file -> file.getFileName().toString().endsWith(suffix))
				.findFirst().orElse(null);
		}
	}

	private static Path findFileUnchecked(Path directory, String runId, String suffix) {
		try {
			return findFile(directory, runId, suffix);
		} catch (IOException e) {
			return null;
		}
	}

	private static ZipArchiveOutputStream zip(Path path) throws IOException {
		// Use a seekable file-backed writer. It rewrites sizes and CRCs into every local
		// file header instead of emitting data descriptors. The viewer's zip-loader 1.x
		// only understands this simpler ZIP layout.
		ZipArchiveOutputStream zip = new ZipArchiveOutputStream(path);
		zip.setEncoding(StandardCharsets.UTF_8.name());
		return zip;
	}

	private static void putFile(ZipArchiveOutputStream zip, String name, Path source,
		Predicate<String> lineFilter) throws IOException {

		zip.putArchiveEntry(new ZipArchiveEntry(name));
		try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
			String header = reader.readLine();
			if (header != null) {
				writer.write(header);
				writer.newLine();
			}
			String line;
			while ((line = reader.readLine()) != null) {
				if (lineFilter.test(line)) {
					writer.write(line);
					writer.newLine();
				}
			}
			writer.flush();
		}
		zip.closeArchiveEntry();
	}

	private static void putTable(ZipArchiveOutputStream zip, String name, Table table) throws IOException {
		zip.putArchiveEntry(new ZipArchiveEntry(name));
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
		table.write(writer);
		writer.flush();
		zip.closeArchiveEntry();
	}

	private static final class SourceRun {
		private final String id;
		private final List<String> row;
		private final Path directory;

		private SourceRun(String id, List<String> row, Path directory) {
			this.id = id;
			this.row = row;
			this.directory = directory;
		}
	}

	private static final class SelectedFile {
		private final Path path;
		private final String viewerSuffix;
		private final Predicate<String> filter;

		private SelectedFile(Path path, String viewerSuffix, Predicate<String> filter) {
			this.path = path;
			this.viewerSuffix = viewerSuffix;
			this.filter = filter;
		}
	}

	private static final class InfoTable {
		private final List<String> header;
		private final List<List<String>> rows;

		private InfoTable(List<String> header, List<List<String>> rows) {
			this.header = header;
			this.rows = rows;
		}

		static InfoTable read(Path path) throws IOException {
			try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				String first = reader.readLine();
				if (first == null) throw new IllegalArgumentException("Empty info file: " + path);
				List<String> header = Arrays.asList(first.split(";", -1));
				List<List<String>> rows = new ArrayList<>();
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.isBlank()) continue;
					List<String> row = Arrays.asList(line.split(";", -1));
					if (row.size() != header.size())
						throw new IllegalArgumentException("Invalid column count in " + path + ": " + line);
					rows.add(row);
				}
				InfoTable result = new InfoTable(header, rows);
				for (String required : List.of("RunId", "Output")) result.column(required);
				return result;
			}
		}

		int column(String name) {
			int index = header.indexOf(name);
			if (index < 0) throw new IllegalArgumentException("Missing column " + name + " in _info.txt");
			return index;
		}

		List<Integer> parameterColumns() {
			Set<String> fixed = Set.of("RunScript", "Config", "RunId", "Output");
			List<Integer> result = new ArrayList<>();
			for (int i = 0; i < header.size(); i++) if (!fixed.contains(header.get(i))) result.add(i);
			return result;
		}

		void write(Path path) throws IOException {
			try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				writer.write(String.join(";", header));
				writer.newLine();
				for (List<String> row : rows) {
					writer.write(String.join(";", row));
					writer.newLine();
				}
			}
		}
	}

	private static final class Table {
		private final List<String> header;
		private final List<List<String>> rows;

		private Table(List<String> header, List<List<String>> rows) {
			this.header = header;
			this.rows = rows;
		}

		static Table read(Path path, Predicate<String> filter) throws IOException {
			try (InputStream stream = Files.newInputStream(path)) {
				return read(stream, filter);
			}
		}

		static Table read(InputStream stream, Predicate<String> filter) throws IOException {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				String first = reader.readLine();
				if (first == null) return new Table(List.of(), List.of());
				List<String> header = Arrays.asList(first.split("\t", -1));
				List<List<String>> rows = new ArrayList<>();
				String line;
				while ((line = reader.readLine()) != null) {
					if (filter.test(line)) rows.add(Arrays.asList(line.split("\t", -1)));
				}
				return new Table(header, rows);
			}
		}

		static Table aggregateByRow(List<Table> tables) {
			validateCompatibleHeaders(tables);
			List<String> header = tables.get(0).header;
			int maxRows = tables.stream().mapToInt(table -> table.rows.size()).max().orElse(0);
			List<List<String>> rows = new ArrayList<>();

			for (int row = 0; row < maxRows; row++) {
				List<String> aggregated = new ArrayList<>();
				for (int column = 0; column < header.size(); column++) {
					List<String> values = valuesAt(tables, row, column);
					if (header.get(column).equals("day")) aggregated.add(values.get(0));
					else aggregated.add(meanOrFirst(values));
				}
				rows.add(aggregated);
			}
			return new Table(header, rows);
		}

		static Table aggregateVaccinationsDetailed(List<Table> tables) {
			validateCompatibleHeaders(tables);
			List<String> header = tables.get(0).header;
			List<String> keys = List.of("day", "date", "type", "number");
			if (!header.containsAll(keys) || !header.contains("amount")) return aggregateByRow(tables);

			List<Integer> keyColumns = keys.stream().map(header::indexOf).collect(Collectors.toList());
			int amount = header.indexOf("amount");
			Map<List<String>, List<String>> groupedAmounts = new LinkedHashMap<>();
			for (Table table : tables) {
				for (List<String> row : table.rows) {
					List<String> key = keyColumns.stream().map(row::get).collect(Collectors.toList());
					groupedAmounts.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row.get(amount));
				}
			}

			List<List<String>> rows = new ArrayList<>();
			for (Map.Entry<List<String>, List<String>> entry : groupedAmounts.entrySet()) {
				List<String> row = new ArrayList<>();
				for (String column : header) {
					int keyIndex = keys.indexOf(column);
					row.add(keyIndex >= 0 ? entry.getKey().get(keyIndex) : meanOrFirst(entry.getValue()));
				}
				rows.add(row);
			}
			return new Table(header, rows);
		}

		static Table infectionsPerSeed(List<Table> tables) {
			validateCompatibleHeaders(tables);
			Table first = tables.get(0);
			int day = first.header.indexOf("day");
			int date = first.header.indexOf("date");
			int cases = first.header.indexOf("nShowingSymptomsCumulative");
			if (day < 0 || date < 0 || cases < 0)
				throw new IllegalArgumentException("infections.txt lacks day, date, or nShowingSymptomsCumulative");

			List<String> header = new ArrayList<>(List.of("day", "date"));
			for (int i = 0; i < tables.size(); i++) header.add("nShowingSymptomsCumulative_" + i);
			int rows = tables.stream().mapToInt(table -> table.rows.size()).min().orElse(0);
			List<List<String>> output = new ArrayList<>();
			for (int row = 0; row < rows; row++) {
				List<String> values = new ArrayList<>(List.of(first.rows.get(row).get(day), first.rows.get(row).get(date)));
				for (Table table : tables) values.add(table.rows.get(row).get(cases));
				output.add(values);
			}
			return new Table(header, output);
		}

		private static List<String> valuesAt(List<Table> tables, int row, int column) {
			List<String> values = new ArrayList<>();
			for (Table table : tables) {
				if (row < table.rows.size() && column < table.rows.get(row).size())
					values.add(table.rows.get(row).get(column));
			}
			return values;
		}

		private static String meanOrFirst(List<String> values) {
			if (values.isEmpty()) return "";
			double sum = 0;
			for (String value : values) {
				try {
					sum += Double.parseDouble(value);
				} catch (NumberFormatException e) {
					return values.get(0);
				}
			}
			return String.format(Locale.ROOT, "%s", sum / values.size());
		}

		private static void validateCompatibleHeaders(List<Table> tables) {
			List<String> expected = tables.get(0).header;
			for (Table table : tables) {
				if (!table.header.equals(expected))
					throw new IllegalArgumentException("Cannot aggregate files with different columns");
			}
		}

		void write(BufferedWriter writer) throws IOException {
			writer.write(String.join("\t", header));
			writer.newLine();
			for (List<String> row : rows) {
				writer.write(String.join("\t", row));
				writer.newLine();
			}
		}
	}
}

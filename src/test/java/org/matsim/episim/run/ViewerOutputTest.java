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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViewerOutputTest {

	private static final LocalDate DATE = LocalDate.parse("2026-09-11");

	@TempDir
	Path temp;

	@Test
	void numbersRunsPerPrefixAndDate() throws IOException {
		Files.createDirectories(temp.resolve("2026-09-11/unrelated"));

		assertThat(ViewerOutput.create("INF", temp, DATE).runDirectory()).isEqualTo(temp.resolve("2026-09-11/INF-00001"));
		assertThat(ViewerOutput.create("INF", temp, DATE).runDirectory()).isEqualTo(temp.resolve("2026-09-11/INF-00002"));
		assertThat(ViewerOutput.create("RSV", temp, DATE).runDirectory()).isEqualTo(temp.resolve("2026-09-11/RSV-00001"));
		assertThat(ViewerOutput.create("INF", temp, DATE.plusDays(1)).runDirectory()).isEqualTo(temp.resolve("2026-09-12/INF-00001"));

		assertThatThrownBy(() -> ViewerOutput.create("INF-1", temp, DATE)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rootFallsBackWhenEnvironmentIsNotSet() {
		Path fallback = temp.resolve("fallback");

		assertThat(ViewerOutput.rootFrom(null, fallback)).isEqualTo(fallback);
		assertThat(ViewerOutput.rootFrom("  ", fallback)).isEqualTo(fallback);
		assertThat(ViewerOutput.rootFrom("/tmp/download_svn/jaro", fallback)).isEqualTo(Path.of("/tmp/download_svn/jaro"));
	}

	@Test
	void packsSingleRunForTheViewer() throws IOException {
		ViewerOutput output = ViewerOutput.create("INF", temp, DATE);

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class).setStartDate(LocalDate.parse("2022-09-26"));
		config.global().setRandomSeed(4711);
		output.configure(config);

		assertThat(config.controller().getRunId()).isEqualTo("INF1");
		assertThat(Path.of(config.controller().getOutputDirectory())).isEqualTo(output.runOutput());

		// what EpisimReporting writes with a run id: <runId>.<file>
		Files.write(output.runOutput().resolve("INF1.infections.txt"), List.of(
				"time\tday\tdate\tnShowingSymptomsCumulative\tdistrict",
				"86400.0\t1\t2022-09-26\t3\tKöln",
				"86400.0\t1\t2022-09-26\t999\tBerlin",
				"172800.0\t2\t2022-09-27\t5\tKöln"), StandardCharsets.UTF_8);
		Files.write(output.runOutput().resolve("INF1.restrictions.txt"), List.of("day\tdate", "1\t2022-09-26"), StandardCharsets.UTF_8);

		output.pack(config, "cologne", "influenza", 10, "Köln");

		assertThat(Files.readAllLines(output.simulation().resolve("_info.txt"))).containsExactly(
				"RunScript;Config;RunId;Output;seed;pathogen",
				"na;INF1.config.xml;INF1;" + output.runOutput().toAbsolutePath() + ";4711;influenza");
		assertThat(Files.readString(output.simulation().resolve("metadata.yaml")))
				.contains("zipFolder: summaries", "viewerVersion: 2", "city: cologne", "runName: influenza",
						"defaultStartDate: 2022-09-26", "endDate: 2022-10-05", "measure: pathogen");
		assertThat(Files.readString(output.simulation().resolve("notes.md"))).contains("influenza", "INF1");

		// keep seeds: one viewer run per simulation run
		Path withSeeds = output.visualizationWithSeeds();
		assertThat(withSeeds.resolve("metadata.yaml")).exists();
		assertThat(withSeeds.resolve("notes.md")).exists();
		try (ZipFile zip = new ZipFile(withSeeds.resolve("summaries/INF1.zip").toFile())) {
			assertThat(zip.stream().map(entry -> entry.getName()).collect(Collectors.toList())).containsExactlyInAnyOrder(
					"INF1.infections.txt.csv", "INF1.infectionsPerSeed.tsv", "INF1.restrictions.txt.csv");
			try (var stream = zip.getInputStream(zip.getEntry("INF1.infections.txt.csv"))) {
				assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
						.contains("172800.0\t2\t2022-09-27\t5\tKöln")
						.doesNotContain("Berlin");
			}
		}

		// no seeds: runs averaged by the remaining parameters
		Path withoutSeeds = output.visualizationWithoutSeeds();
		assertThat(Files.readAllLines(withoutSeeds.resolve("_info.txt"))).containsExactly(
				"RunScript;Config;RunId;Output;pathogen",
				"na;na;0;na;influenza");
		assertThat(withoutSeeds.resolve("summaries/0.zip")).exists();
	}
}

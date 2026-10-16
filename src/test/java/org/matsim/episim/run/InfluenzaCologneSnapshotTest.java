package org.matsim.episim.run;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimRunner;
import org.matsim.episim.run.modules.SnzCologneOpenProductionScenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Snapshots on the Cologne open scenario with influenza (see {@link InfluenzaCologneScenarioTest}).
 *
 * <ol>
 *   <li>one calculation of 30 days gives the reference result;</li>
 *   <li>three calculations of 10 days follow each other: the first starts from scratch, the second and the third start
 *   from the snapshot the previous one wrote at its end;</li>
 *   <li>the result of the third calculation (which carries the output of the earlier ones) equals the reference.</li>
 * </ol>
 *
 * <p>Like {@code InfluenzaCologneScenarioTest#runsOnCologneScenario} it downloads the Cologne input set from the VSP SVN
 * and simulates 60 days in total, which takes several minutes.</p>
 */
public class InfluenzaCologneSnapshotTest {

	private static final int DAYS = 10;

	/** Not compared: archives, configs, timings, and episodes open at a snapshot (restored without infector, other order). */
	private static final List<String> NOT_COMPARED = List.of(".zip", ".xml", ".gz", ".tar", "cputime.tsv", "infectionEpisodes.tsv");

	@TempDir
	Path root;

	@Test
	public void threeCalculationsFromSnapshotsEqualOneOf30Days() throws IOException {

		Path reference = root.resolve("reference");
		calculate(reference, 3 * DAYS, null);

		Path first = root.resolve("first");
		calculate(first, DAYS, null);

		Path second = root.resolve("second");
		calculate(second, 2 * DAYS, snapshot(first, DAYS));

		Path third = root.resolve("third");
		calculate(third, 3 * DAYS, snapshot(second, 2 * DAYS));

		assertSameResults(reference, third);
	}

	/**
	 * Calculates up to the given day (a start from a snapshot continues at the snapshot's day), writing a snapshot
	 * every {@value #DAYS} days into {@code output}.
	 */
	private static void calculate(Path output, int untilDay, Path snapshot) throws IOException {

		OutputDirectoryLogging.catchLogEntries();
		Files.createDirectories(output);

		Injector injector = Guice.createInjector(
				Modules.override(new EpisimModule()).with(new SnzCologneOpenProductionScenario.Builder().build()));

		Config config = injector.getInstance(Config.class);
		InfluenzaCologneScenarioTest.configureInfluenza(config);   // before the runner is built
		config.controller().setOutputDirectory(output.toString());

		EpisimConfigGroup episimConfig = injector.getInstance(EpisimConfigGroup.class);
		episimConfig.setSnapshotInterval(DAYS);
		if (snapshot != null)
			episimConfig.setStartFromSnapshot(snapshot.toString());

		injector.getInstance(EpisimRunner.class).run(untilDay);
	}

	/** The snapshot written at the given day; its file name carries the date as well. */
	private static Path snapshot(Path output, int day) throws IOException {
		try (Stream<Path> files = Files.list(output)) {
			return files.filter(f -> f.getFileName().toString().startsWith(String.format("episim-snapshot-%03d-", day)))
					.findFirst().orElseThrow(() -> new AssertionError("no snapshot of day " + day + " in " + output));
		}
	}

	private static void assertSameResults(Path expected, Path actual) throws IOException {

		List<Path> files;
		try (Stream<Path> list = Files.list(expected)) {
			files = list.filter(Files::isRegularFile)
					.filter(f -> NOT_COMPARED.stream().noneMatch(s -> f.getFileName().toString().endsWith(s)))
					.toList();
		}

		// guard against comparing nothing
		assertThat(files).anyMatch(f -> f.getFileName().toString().endsWith("infectionEvents.txt"));

		for (Path file : files)
			assertThat(actual.resolve(file.getFileName()).toFile()).as(file.getFileName().toString())
					.hasSameTextualContentAs(file.toFile());
	}
}

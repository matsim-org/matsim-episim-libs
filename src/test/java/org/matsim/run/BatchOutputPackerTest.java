package org.matsim.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class BatchOutputPackerTest {

	@TempDir
	Path temp;

	@Test
	void packsAndAggregatesSeeds() throws IOException {
		Path input = Files.createDirectory(temp.resolve("output"));
		Files.writeString(input.resolve("metadata.yaml"), "---\nzipFolder: summaries\n", StandardCharsets.UTF_8);
		Files.writeString(input.resolve("notes.md"), "# Hello World Cologne Based Project\n", StandardCharsets.UTF_8);
		Files.writeString(input.resolve("_info.txt"), String.join("\n",
			"RunScript;Config;RunId;Output;seed;theta;masks",
			"run.sh;c1.xml;calibration1;output/seed1;1;1.0;yes",
			"run.sh;c2.xml;calibration2;output/seed2;2;1.0;yes",
			"run.sh;c3.xml;calibration3;output/seed3;1;2.0;no",
			"") , StandardCharsets.UTF_8);

		writeRun(input.resolve("seed1"), "calibration1", 10, 20);
		writeRun(input.resolve("seed2"), "calibration2", 30, 40);
		writeRun(input.resolve("seed3"), "calibration3", 50, 60);

		Path output = temp.resolve("viewer");
		new BatchOutputPacker(input, output, "Köln").pack();

		assertThat(Files.readAllLines(output.resolve("_info.txt"))).containsExactly(
			"RunScript;Config;RunId;Output;theta;masks",
			"na;na;0;na;1.0;yes",
			"na;na;1;na;2.0;no");
		assertThat(output.resolve("summaries/0.zip")).exists();
		assertThat(output.resolve("summaries/1.zip")).exists();
		assertThat(Files.readString(output.resolve("notes.md")))
			.isEqualTo("# Hello World Cologne Based Project\n");
		assertThat(Files.readString(output.resolve("summaries/0.infectionLoc.csv.gz")))
			.isEqualTo("map-calibration1");
		assertZipUsesLocalSizes(output.resolve("summaries/0.zip"));

		try (ZipFile zip = new ZipFile(output.resolve("summaries/0.zip").toFile())) {
			assertThat(zip.stream().map(entry -> entry.getName()).collect(Collectors.toList())).containsExactlyInAnyOrder(
				"0.infections.txt.csv", "0.infectionsPerSeed.tsv", "0.restrictions.txt.csv");
			assertThat(read(zip, "0.infections.txt.csv")).contains(
				"86400.0\t1\t2020-02-25\t20.0\tKöln",
				"172800.0\t2\t2020-02-26\t30.0\tKöln")
				.doesNotContain("Berlin");
			assertThat(read(zip, "0.infectionsPerSeed.tsv")).contains(
				"nShowingSymptomsCumulative_0\tnShowingSymptomsCumulative_1",
				"1\t2020-02-25\t10\t30");
		}

		Path seedsOutput = temp.resolve("viewer-with-seeds");
		new BatchOutputPacker(input, seedsOutput, "Köln", true).pack();

		assertThat(Files.readAllLines(seedsOutput.resolve("_info.txt"))).isEqualTo(
			Files.readAllLines(input.resolve("_info.txt")));
		assertThat(Files.readString(seedsOutput.resolve("notes.md")))
			.isEqualTo("# Hello World Cologne Based Project\n");
		assertThat(Files.readString(seedsOutput.resolve("summaries/calibration1.infectionLoc.csv.gz")))
			.isEqualTo("map-calibration1");
		assertZipUsesLocalSizes(seedsOutput.resolve("summaries/calibration1.zip"));
		try (ZipFile zip = new ZipFile(seedsOutput.resolve("summaries/calibration1.zip").toFile())) {
			assertThat(zip.stream().map(entry -> entry.getName()).collect(Collectors.toList())).containsExactlyInAnyOrder(
				"calibration1.infections.txt.csv", "calibration1.infectionsPerSeed.tsv",
				"calibration1.restrictions.txt.csv");
			assertThat(read(zip, "calibration1.infections.txt.csv"))
				.contains("86400.0\t1\t2020-02-25\t10\tKöln")
				.doesNotContain("Berlin");
			assertThat(read(zip, "calibration1.infectionsPerSeed.tsv")).contains(
				"day\tdate\tnShowingSymptomsCumulative_0",
				"1\t2020-02-25\t10");
		}
	}

	private static void writeRun(Path directory, String id, int dayOne, int dayTwo) throws IOException {
		Files.createDirectory(directory);
		List<String> infections = List.of(
			"time\tday\tdate\tnShowingSymptomsCumulative\tdistrict",
			"86400.0\t1\t2020-02-25\t" + dayOne + "\tKöln",
			"86400.0\t1\t2020-02-25\t999\tBerlin",
			"172800.0\t2\t2020-02-26\t" + dayTwo + "\tKöln"
		);
		Files.write(directory.resolve(id + ".infections.txt"), infections, StandardCharsets.UTF_8);
		Files.write(directory.resolve(id + ".restrictions.txt"), List.of("day\tdate", "1\t2020-02-25"), StandardCharsets.UTF_8);
		Files.writeString(directory.resolve(id + ".infectionLoc.csv.gz"), "map-" + id, StandardCharsets.UTF_8);
	}

	private static String read(ZipFile zip, String name) throws IOException {
		try (var stream = zip.getInputStream(zip.getEntry(name))) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/**
	 * zip-loader 1.x does not support ZIP data descriptors (general-purpose bit 3).
	 * It needs compressed sizes to be present directly in every local file header.
	 */
	private static void assertZipUsesLocalSizes(Path path) throws IOException {
		ByteBuffer zip = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
		int offset = 0;
		int entries = 0;

		while (Integer.toUnsignedLong(zip.getInt(offset)) == 0x04034b50L) {
			int flags = Short.toUnsignedInt(zip.getShort(offset + 6));
			assertThat(flags & 0x08)
				.as("data-descriptor flag of local ZIP entry %s", entries)
				.isZero();

			long compressedSize = Integer.toUnsignedLong(zip.getInt(offset + 18));
			int nameLength = Short.toUnsignedInt(zip.getShort(offset + 26));
			int extraLength = Short.toUnsignedInt(zip.getShort(offset + 28));
			offset = Math.toIntExact(offset + 30L + nameLength + extraLength + compressedSize);
			entries++;
		}

		assertThat(entries).isPositive();
		assertThat(Integer.toUnsignedLong(zip.getInt(offset))).isEqualTo(0x02014b50L);
	}
}

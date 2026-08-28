package org.matsim.episim.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class InfectionLocationsFromEventsTest {

	@TempDir
	Path output;

	@Test
	void writesViewerCompatibleLocationFile() throws IOException {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Person person = scenario.getPopulation().getFactory().createPerson(Id.createPersonId("infected-1"));
		person.getAttributes().putAttribute("homeX", 356_000.0);
		person.getAttributes().putAttribute("homeY", 5_640_000.0);
		scenario.getPopulation().addPerson(person);

		Files.write(output.resolve("calibration1.infectionEvents.txt"), List.of(
			"time\tinfector\tinfected\tinfectionType\tdate\tgroupSize\tfacility\tvirusStrain\tprobability",
			"200000\tsource-1\tinfected-1\thome_home\t2020-02-27\t2\thome-1\tSARS_CoV_2\t0.5"
		), StandardCharsets.UTF_8);

		new InfectionLocationsFromEvents(scenario, LocalDate.of(2020, 2, 25)).analyzeOutput(output);

		String csv;
		try (GZIPInputStream gzip = new GZIPInputStream(
			Files.newInputStream(output.resolve("calibration1.infectionLoc.csv.gz")))) {
			csv = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
		}

		String[] lines = csv.strip().split("\\R");
		assertThat(lines[0]).isEqualTo("home_lon,home_lat,daysSinceStart,infection_type");
		String[] values = lines[1].split(",");
		assertThat(Double.parseDouble(values[0])).isBetween(6.0, 8.0);
		assertThat(Double.parseDouble(values[1])).isBetween(50.0, 52.0);
		assertThat(values[2]).isEqualTo("2");
		assertThat(values[3]).isEqualTo("normal");
	}
}

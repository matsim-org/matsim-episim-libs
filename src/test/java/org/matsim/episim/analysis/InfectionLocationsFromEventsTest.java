package org.matsim.episim.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ProjectionUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class InfectionLocationsFromEventsTest {

	private static final LocalDate START = LocalDate.of(2020, 2, 25);

	@TempDir
	Path output;

	@Test
	void writesViewerCompatibleLocationFile() throws IOException {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		addInfectedPerson(scenario, 356_000.0, 5_640_000.0);
		writeInfectionEvents("calibration1");

		new InfectionLocationsFromEvents(scenario, START).analyzeOutput(output);

		String[] values = readSingleLocation("calibration1");
		assertThat(Double.parseDouble(values[0])).isBetween(6.0, 8.0);
		assertThat(Double.parseDouble(values[1])).isBetween(50.0, 52.0);
		assertThat(values[2]).isEqualTo("2");
		assertThat(values[3]).isEqualTo("normal");
	}

	@Test
	void usesPopulationCrsAttributeOverFallback() throws IOException {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		// Coordinates are already WGS84; the fallback EPSG:25832 would move them far away.
		ProjectionUtils.putCRS(scenario.getPopulation(), TransformationFactory.WGS84);
		addInfectedPerson(scenario, 7.5, 51.5);
		writeInfectionEvents("calibration1");

		new InfectionLocationsFromEvents(scenario, START).analyzeOutput(output);

		String[] values = readSingleLocation("calibration1");
		assertThat(Double.parseDouble(values[0])).isCloseTo(7.5, within(1e-6));
		assertThat(Double.parseDouble(values[1])).isCloseTo(51.5, within(1e-6));
	}

	@Test
	void usesPlansInputCrsWhenPopulationHasNoCrs() throws IOException {
		Config config = ConfigUtils.createConfig();
		config.plans().setInputCRS(TransformationFactory.WGS84);
		Scenario scenario = ScenarioUtils.createScenario(config);
		// no CRS attribute on the population -> resolution must fall through to plans.inputCRS
		addInfectedPerson(scenario, 7.5, 51.5);
		writeInfectionEvents("calibration1");

		new InfectionLocationsFromEvents(scenario, START).analyzeOutput(output);

		String[] values = readSingleLocation("calibration1");
		assertThat(Double.parseDouble(values[0])).isCloseTo(7.5, within(1e-6));
		assertThat(Double.parseDouble(values[1])).isCloseTo(51.5, within(1e-6));
	}

	private static void addInfectedPerson(Scenario scenario, double homeX, double homeY) {
		Person person = scenario.getPopulation().getFactory().createPerson(Id.createPersonId("infected-1"));
		person.getAttributes().putAttribute("homeX", homeX);
		person.getAttributes().putAttribute("homeY", homeY);
		scenario.getPopulation().addPerson(person);
	}

	private void writeInfectionEvents(String prefix) throws IOException {
		Files.write(output.resolve(prefix + ".infectionEvents.txt"), List.of(
			"time\tinfector\tinfected\tinfectionType\tdate\tgroupSize\tfacility\tvirusStrain\tprobability",
			"200000\tsource-1\tinfected-1\thome_home\t2020-02-27\t2\thome-1\tSARS_CoV_2\t0.5"
		), StandardCharsets.UTF_8);
	}

	private String[] readSingleLocation(String prefix) throws IOException {
		String csv;
		try (GZIPInputStream gzip = new GZIPInputStream(
			Files.newInputStream(output.resolve(prefix + ".infectionLoc.csv.gz")))) {
			csv = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
		}

		String[] lines = csv.strip().split("\\R");
		assertThat(lines[0]).isEqualTo("home_lon,home_lat,daysSinceStart,infection_type");
		assertThat(lines).hasSize(2);
		return lines[1].split(",");
	}
}

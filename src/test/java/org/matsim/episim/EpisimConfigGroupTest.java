package org.matsim.episim;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.VirusStrain;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class EpisimConfigGroupTest {

	@Test
	public void lookup() {

		EpisimConfigGroup config = new EpisimConfigGroup();

		EpisimConfigGroup.InfectionParams work = config.getOrAddContainerParams("work");
		EpisimConfigGroup.InfectionParams edu = config.getOrAddContainerParams("edu_high");
		EpisimConfigGroup.InfectionParams kiga = config.getOrAddContainerParams("edu_kiga");
		EpisimConfigGroup.InfectionParams home = config.getOrAddContainerParams("home");
		config.getOrAddContainerParams("quarantine_home");

		assertThat(config.selectInfectionParams("work_4345"))
				.isSameAs(work);

		assertThat(config.selectInfectionParams("work"))
				.isSameAs(work);

		assertThat(config.selectInfectionParams("edu_high_435"))
				.isSameAs(edu);

		assertThat(config.selectInfectionParams("edu_kiga_678"))
				.isSameAs(kiga);

		assertThat(config.selectInfectionParams("home_123"))
				.isSameAs(home);

		assertThatExceptionOfType(NoSuchElementException.class)
				.isThrownBy(() -> config.selectInfectionParams("unknown"));

		assertThatExceptionOfType(NoSuchElementException.class)
				.isThrownBy(() -> config.selectInfectionParams("edu"));

	}

	@Test
	public void samePrefix() {

		EpisimConfigGroup config = new EpisimConfigGroup();

		EpisimConfigGroup.InfectionParams schoolescort = config.getOrAddContainerParams("schoolescort");

		EpisimConfigGroup.InfectionParams schhool = config.getOrAddContainerParams("school");

		assertThat(config.selectInfectionParams("school"))
				.isSameAs(schhool);

		assertThat(config.selectInfectionParams("school123"))
				.isSameAs(schhool);

		assertThat(config.selectInfectionParams("schoolescort"))
				.isSameAs(schoolescort);

		assertThat(config.selectInfectionParams("schoolescort123"))
				.isSameAs(schoolescort);

	}

	@Test
	public void input() throws IOException {

		Config root = ConfigUtils.createConfig();

		EpisimConfigGroup config = ConfigUtils.addOrGetModule(root, EpisimConfigGroup.class);
		config.setInputEventsFile("test_input.xml.gz");

		assertThat(config.getInputEventsFile()).isEqualTo("test_input.xml.gz");
		assertThat(config.getInputEventsFiles())
				.hasSize(1)
				.allMatch(ev -> ev.getPath().equals("test_input.xml.gz"))
				.allMatch(ev -> ev.getDays().size() == 7);

		config.addInputEventsFile("second.xml.gz")
				.addDays(DayOfWeek.MONDAY);

		assertThat(config.getInputEventsFiles())
				.anyMatch(ev -> ev.getDays().size() == 1)
				.hasSize(2);


		File tmp = File.createTempFile("config", "xml");
		tmp.deleteOnExit();

		ConfigUtils.writeConfig(root, tmp.toString());
		Config root2 = ConfigUtils.loadConfig(tmp.toString());

	}

	@Test
	public void initialInfections() {

		Config config = EpisimTestUtils.createTestConfig();
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		Map<LocalDate, Integer> ref = Map.of(LocalDate.of(2020, 12, 12), 10);

		episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, ref);

		String s = episimConfig.getInfectionsPerDay();

		episimConfig.setInfectionsPerDay(s);

		assertThat(episimConfig.getInfections_pers_per_day().get(VirusStrain.ALPHA))
				.isEqualTo(ref);

	}

	@Test
	public void initialInfectionsOrder() throws IOException {

		Config config = ConfigUtils.createConfig();
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		Map<LocalDate, Integer> ref = Map.of(LocalDate.of(2020, 12, 12), 10);

		// set in an arbitrary order, including a custom strain
		VirusStrain custom = VirusStrain.of("INFECTIONS_ORDER_TEST_STRAIN");
		for (VirusStrain strain : List.of(custom, VirusStrain.OMICRON_BA5, VirusStrain.DELTA, VirusStrain.ALPHA, VirusStrain.OMICRON_BA1))
			episimConfig.setInfections_pers_per_day(strain, ref);

		// initial infections are drawn in this order: standard strains as declared, then others by name
		List<VirusStrain> expected = List.of(VirusStrain.SARS_CoV_2, VirusStrain.ALPHA, VirusStrain.DELTA,
				VirusStrain.OMICRON_BA1, VirusStrain.OMICRON_BA5, custom);

		assertThat(episimConfig.getInfections_pers_per_day().keySet()).containsExactlyElementsOf(expected);

		// the order must be the same when the config is written to and read from a file
		EpisimConfigGroup read = writeAndRead(config);

		assertThat(read.getInfections_pers_per_day().keySet()).containsExactlyElementsOf(expected);
		assertThat(read.getInfections_pers_per_day()).isEqualTo(episimConfig.getInfections_pers_per_day());
	}

	@Test
	public void noInitialInfectionsFromFile() throws IOException {

		Config config = ConfigUtils.createConfig();
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		// e.g. a non-covid scenario that removes the default SARS-CoV-2 import
		episimConfig.getInfections_pers_per_day().clear();

		EpisimConfigGroup read = writeAndRead(config);

		assertThat(read.getInfections_pers_per_day()).isEmpty();
		// empty maps are written as empty values and must be readable as well
		assertThat(read.getCurfewCompliance()).isEmpty();
		assertThat(read.getInputDays()).isEmpty();
	}

	/**
	 * Writes the config to a temporary file and reads the episim config group back from it.
	 */
	private static EpisimConfigGroup writeAndRead(Config config) throws IOException {

		// the progression config is referenced by its file name, as with the progression.conf written to the output
		File progression = File.createTempFile("progression", ".conf");
		progression.deleteOnExit();
		ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class).setProgressionConfig(progression.toString());

		File tmp = File.createTempFile("config", ".xml");
		tmp.deleteOnExit();

		ConfigUtils.writeConfig(config, tmp.toString());

		EpisimConfigGroup read = new EpisimConfigGroup();
		ConfigUtils.loadConfig(tmp.toString(), read);
		return read;
	}
}

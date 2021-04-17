package org.matsim.episim;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.VirusStrain;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
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

		episimConfig.setInfections_pers_per_day(VirusStrain.B117, ref);

		String s = episimConfig.getInfectionsPerDay();

		episimConfig.setInfectionsPerDay(s);

		assertThat(episimConfig.getInfections_pers_per_day().get(VirusStrain.B117))
				.isEqualTo(ref);

	}
}

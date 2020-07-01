package org.matsim.episim;

import org.junit.Test;
import org.matsim.core.config.ConfigUtils;

import java.time.DayOfWeek;
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
	public void input() {

		EpisimConfigGroup config = new EpisimConfigGroup();
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
	}

}

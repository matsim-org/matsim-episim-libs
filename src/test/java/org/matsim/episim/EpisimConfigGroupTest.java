package org.matsim.episim;

import org.junit.Test;

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

		assertThat(config.selectInfectionParams("work_4345"))
				.isSameAs(work);

		assertThat(config.selectInfectionParams("work"))
				.isSameAs(work);

		assertThat(config.selectInfectionParams("edu_high_435"))
				.isSameAs(edu);

		assertThat(config.selectInfectionParams("edu_kiga"))
				.isSameAs(kiga);

		assertThatExceptionOfType(NoSuchElementException.class)
				.isThrownBy(() -> config.selectInfectionParams("unknown"));

		assertThatExceptionOfType(NoSuchElementException.class)
				.isThrownBy(() -> config.selectInfectionParams("edu"));

	}

}

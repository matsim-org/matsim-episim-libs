package org.matsim.episim;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TracingConfigGroupTest {


	@Test
	public void capacity() {

		TracingConfigGroup config = new TracingConfigGroup();

		config.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30,
				LocalDate.of(2020, 6, 1), 50
		));

		String s = config.getTracingCapacityString();

		Map<LocalDate, Integer> expected = config.getTracingCapacity();

		assertThat(expected)
				.isNotEmpty();

		config.setTracingCapacity(s);

		assertThat(config.getTracingCapacity())
				.isEqualTo(expected)
				.isNotEmpty();

	}

	@Test
	public void emptyValuesSurviveWriteAndRead() throws IOException {

		Config config = ConfigUtils.createConfig();
		// non-default, so a lost value would show; the param name "strategy" is aliased to "replanning" on read
		ConfigUtils.addOrGetModule(config, TracingConfigGroup.class).setStrategy(TracingConfigGroup.Strategy.NONE);

		File tmp = File.createTempFile("config", ".xml");
		tmp.deleteOnExit();
		ConfigUtils.writeConfig(config, tmp.toString());

		TracingConfigGroup read = new TracingConfigGroup();
		ConfigUtils.loadConfig(tmp.toString(), read);

		assertThat(read.getQuarantineDuration()).isEmpty();
		assertThat(read.getQuarantineStatus()).isEmpty();
		assertThat(read.getQuarantineVaccinated()).isEmpty();
		assertThat(read.getIgnoredActivities()).isEmpty();
		assertThat(read.getStrategy()).isEqualTo(TracingConfigGroup.Strategy.NONE);
	}
}

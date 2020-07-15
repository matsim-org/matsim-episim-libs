package org.matsim.episim;

import org.junit.Test;

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
}

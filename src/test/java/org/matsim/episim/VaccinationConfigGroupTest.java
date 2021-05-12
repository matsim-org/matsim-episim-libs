package org.matsim.episim;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class VaccinationConfigGroupTest {

	@Test
	public void compliance() {

		VaccinationConfigGroup config = new VaccinationConfigGroup();

		Map<Integer, Double> vaccinationCompliance = new HashMap<>();

		for (int i = 0; i < 18; i++) vaccinationCompliance.put(i, 0.);
		for (int i = 18; i < 120; i++) vaccinationCompliance.put(i, 0.5);

		config.setCompliancePerAge(vaccinationCompliance);


		assertThat(EpisimUtils.findValidEntry(config.getCompliancePerAge(), 1.0, 5))
				.isEqualTo(0);

		assertThat(EpisimUtils.findValidEntry(config.getCompliancePerAge(), 1.0, 18))
				.isEqualTo(0.5);

		assertThat(EpisimUtils.findValidEntry(config.getCompliancePerAge(), 1.0, 25))
				.isEqualTo(0.5);

	}
}

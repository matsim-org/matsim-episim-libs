package org.matsim.episim.model;

import org.assertj.core.data.Percentage;
import org.junit.Before;
import org.junit.Test;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.policy.Restriction;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultFaceMaskModelTest {

	private FaceMaskModel model;
	private EpisimConfigGroup config;

	@Before
	public void setUp() throws Exception {
		config = EpisimTestUtils.createTestConfig();
		model = new DefaultFaceMaskModel(config, new SplittableRandom(1));
	}

	private double sample(Restriction r) {
		double worn = 0;
		for (int i = 0; i < 30_000; i++) {
			EpisimPerson p = EpisimTestUtils.createPerson("work", null);
			FaceMask mask = model.getWornMask(p, config.selectInfectionParams("work"), 0, r);
			if (mask == FaceMask.CLOTH) worn++;
		}

		return worn / 30_000;
	}

	@Test
	public void compliance() {

		config.setMaskCompliance(0.5);
		assertThat(sample(Restriction.ofMask(FaceMask.CLOTH)))
				.isCloseTo(0.5, Percentage.withPercentage(2));

		config.setMaskCompliance(0.0);
		assertThat(sample(Restriction.ofMask(FaceMask.CLOTH)))
				.isCloseTo(0.0, Percentage.withPercentage(2));

	}


	@Test
	public void restriction() {

		config.setMaskCompliance(0.5);
		assertThat(sample(Restriction.ofMask(FaceMask.CLOTH, 0.3)))
				.isCloseTo(0.3, Percentage.withPercentage(2));

	}
}

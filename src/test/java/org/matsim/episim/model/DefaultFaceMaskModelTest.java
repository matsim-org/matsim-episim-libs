package org.matsim.episim.model;

import org.assertj.core.data.Percentage;
import org.junit.Before;
import org.junit.Test;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.policy.Restriction;

import java.util.Map;
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

	private double sample(Restriction r, FaceMask type) {
		double worn = 0;
		for (int i = 0; i < 30_000; i++) {
			EpisimPerson p = EpisimTestUtils.createPerson("work", null);
			FaceMask mask = model.getWornMask(p, config.selectInfectionParams("work"), 0, r);
			if (mask == type) worn++;
		}

		return worn / 30_000;
	}

	@Test
	public void compliance() {

		Percentage p = Percentage.withPercentage(2);

		assertThat(sample(Restriction.ofMask(FaceMask.CLOTH, 0.5), FaceMask.CLOTH))
				.isCloseTo(0.5, p);

		assertThat(sample(Restriction.ofMask(FaceMask.CLOTH, 0.0), FaceMask.CLOTH))
				.isCloseTo(0.0, p);

		Restriction r = Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.2, FaceMask.N95, 0.5));

		assertThat(sample(r, FaceMask.CLOTH))
				.isCloseTo(0.2, p);
		assertThat(sample(r, FaceMask.N95))
				.isCloseTo(0.5, p);
		assertThat(sample(r, FaceMask.NONE))
				.isCloseTo(0.3, p);
		assertThat(sample(r, FaceMask.SURGICAL))
				.isCloseTo(0.0, p);
	}

}

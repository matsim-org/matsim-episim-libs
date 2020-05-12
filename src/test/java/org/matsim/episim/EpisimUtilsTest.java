package org.matsim.episim;

import org.junit.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class EpisimUtilsTest {

	@Test
	public void seed() {

		SplittableRandom rnd = new SplittableRandom(1000);

		assertThat(EpisimUtils.getSeed(rnd))
				.isEqualTo(1000);

		long n = rnd.nextLong();

		assertThat(EpisimUtils.getSeed(rnd))
				.isNotEqualTo(1000);

		EpisimUtils.setSeed(rnd, 1000);
		assertThat(rnd.nextLong())
				.isEqualTo(n);

	}
}

package org.matsim.episim.util;

import org.junit.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class EpisimSplittableRandomTest {

	@Test
	public void sameSequenceAsJdkSplittableRandom() {
		long[] seeds = {0L, 1L, -1L, 42L, Long.MIN_VALUE, Long.MAX_VALUE};

		for (long seed : seeds) {
			SplittableRandom expected = new SplittableRandom(seed);
			EpisimSplittableRandom actual = new EpisimSplittableRandom(seed);

			for (int i = 0; i < 1_000; i++) {
				assertThat(actual.nextLong()).isEqualTo(expected.nextLong());
				assertThat(actual.nextInt()).isEqualTo(expected.nextInt());
				assertThat(actual.nextDouble()).isEqualTo(expected.nextDouble());
				assertThat(actual.nextBoolean()).isEqualTo(expected.nextBoolean());
				assertThat(actual.nextInt(16)).isEqualTo(expected.nextInt(16));
				assertThat(actual.nextInt(17)).isEqualTo(expected.nextInt(17));
				assertThat(actual.nextInt(-100, 100)).isEqualTo(expected.nextInt(-100, 100));
				assertThat(actual.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
						.isEqualTo(expected.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE));
				assertThat(actual.nextLong(16L)).isEqualTo(expected.nextLong(16L));
				assertThat(actual.nextLong(17L)).isEqualTo(expected.nextLong(17L));
				assertThat(actual.nextLong(-100L, 100L)).isEqualTo(expected.nextLong(-100L, 100L));
				assertThat(actual.nextLong(Long.MIN_VALUE, Long.MAX_VALUE))
						.isEqualTo(expected.nextLong(Long.MIN_VALUE, Long.MAX_VALUE));
				assertThat(actual.nextDouble(100.0)).isEqualTo(expected.nextDouble(100.0));
				assertThat(actual.nextDouble(-100.0, 100.0)).isEqualTo(expected.nextDouble(-100.0, 100.0));
			}
		}
	}

	@Test
	public void splitProducesSameSequenceAsJdkSplittableRandom() {
		SplittableRandom expected = new SplittableRandom(12345L);
		EpisimSplittableRandom actual = new EpisimSplittableRandom(12345L);

		for (int i = 0; i < 100; i++) {
			SplittableRandom expectedSplit = expected.split();
			EpisimSplittableRandom actualSplit = actual.split();

			for (int j = 0; j < 100; j++) {
				assertThat(actualSplit.nextLong()).isEqualTo(expectedSplit.nextLong());
				assertThat(actualSplit.nextInt(37)).isEqualTo(expectedSplit.nextInt(37));
				assertThat(actualSplit.nextDouble()).isEqualTo(expectedSplit.nextDouble());
			}
		}
	}

	@Test
	public void restoresSequenceFromPublicSeed() {
		EpisimSplittableRandom random = new EpisimSplittableRandom(12345L);

		random.nextLong();
		random.nextInt(17);
		long seed = random.getSeed();
		long expectedLong = random.nextLong();
		double expectedDouble = random.nextDouble();

		random.setSeed(seed);

		assertThat(random.nextLong()).isEqualTo(expectedLong);
		assertThat(random.nextDouble()).isEqualTo(expectedDouble);
	}
}

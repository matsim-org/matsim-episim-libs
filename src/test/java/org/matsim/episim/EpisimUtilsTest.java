package org.matsim.episim;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.util.FastMath;
import org.assertj.core.data.Percentage;
import org.junit.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class EpisimUtilsTest {

	private static final Percentage OFFSET = Percentage.withPercentage(1.5);

	@Test
	public void nextLogNormal() {

		SplittableRandom rnd = new SplittableRandom(1);

		double mu = 5;
		double sigma = 1.5;
		double ssigma = 1.5 * 1.5;

		double[] values = new double[2_000_000];
		for (int i = 0; i < values.length; i++) {
			values[i] = EpisimUtils.nextLogNormal(rnd, mu, sigma);
		}

		double median = new Median().evaluate(values);
		double mean = new Mean().evaluate(values);
		double std = new StandardDeviation().evaluate(values);

		// https://en.wikipedia.org/wiki/Log-normal_distribution

		assertThat(median).isCloseTo(FastMath.exp(mu), OFFSET);
		assertThat(mean).isCloseTo(FastMath.exp(mu + ssigma / 2.0), OFFSET);
		assertThat(std * std).isCloseTo((FastMath.exp(ssigma) - 1) * FastMath.exp(2 * mu + ssigma), OFFSET);

	}
}

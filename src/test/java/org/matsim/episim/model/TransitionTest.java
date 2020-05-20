package org.matsim.episim.model;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.assertj.core.data.Offset;
import org.junit.Test;
import org.matsim.episim.EpisimUtils;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class TransitionTest {

	@Test
	public void logNormalMean() {

		SplittableRandom rnd = new SplittableRandom(1);

		Transition t = Transition.logNormalWithMeanAndStd(10, 5);
		double[] values = new double[100_000];
		for (int i = 0; i < values.length; i++) {
			values[i] = t.getTransitionDay(rnd);
		}

		assertThat(new Mean().evaluate(values))
				.isCloseTo(10, Offset.offset(0.1));

		assertThat(new StandardDeviation().evaluate(values))
				.isCloseTo(5, Offset.offset(0.1));
	}

	@Test
	public void logNormalMedian() {

		SplittableRandom rnd = new SplittableRandom(1);

		double[] values = new double[100_000];

		Transition t = Transition.logNormalWithMedianAndStd(10, 3);
		for (int i = 0; i < values.length; i++) {
			values[i] = t.getTransitionDay(rnd);
		}

		assertThat(new Median().evaluate(values))
				.isCloseTo(10, Offset.offset(0.1));

		assertThat(new StandardDeviation().evaluate(values))
				.isCloseTo(3, Offset.offset(0.1));

		t = Transition.logNormalWithMedianAndStd(20, 5);
		values = new double[100_000];
		for (int i = 0; i < values.length; i++) {
			values[i] = t.getTransitionDay(rnd);
		}

		assertThat(new Median().evaluate(values))
				.isCloseTo(20, Offset.offset(0.1));

		assertThat(new StandardDeviation().evaluate(values))
				.isCloseTo(5, Offset.offset(0.1));

	}

	@Test
	public void sigmaZero() {

		SplittableRandom rnd = new SplittableRandom(1);
		Transition t = Transition.logNormalWithMeanAndStd(10, 0);
		for (int i = 0; i < 1000; i++) {
			assertThat(t.getTransitionDay(rnd)).isEqualTo(10);
		}
	}
}

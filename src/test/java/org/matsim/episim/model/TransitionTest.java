package org.matsim.episim.model;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.assertj.core.data.Offset;
import org.junit.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class TransitionTest {

	@Test
	public void logNormal() {

		SplittableRandom rnd = new SplittableRandom(1);

		Transition t = Transition.logNormalWithMean(10, 5);
		double[] values = new double[100_000];
		for (int i = 0; i < values.length; i++) {
			values[i] = t.getTransitionDay(rnd);
		}

		assertThat(new Mean().evaluate(values))
				.isCloseTo(10, Offset.offset(0.1));

		assertThat(new StandardDeviation().evaluate(values))
				.isCloseTo(5, Offset.offset(0.1));


		t = Transition.logNormalWithMedian(20, 3);
		for (int i = 0; i < values.length; i++) {
			values[i] = t.getTransitionDay(rnd);
		}

		assertThat(new Median().evaluate(values))
				.isCloseTo(20, Offset.offset(0.1));

	}

}

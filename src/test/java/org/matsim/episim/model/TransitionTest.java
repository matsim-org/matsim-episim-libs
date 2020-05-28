package org.matsim.episim.model;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.assertj.core.api.Condition;
import org.assertj.core.data.Index;
import org.assertj.core.data.Offset;
import org.junit.Test;
import org.matsim.episim.EpisimPerson.DiseaseStatus;

import java.util.Objects;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.matsim.episim.model.Transition.to;

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

	@Test
	public void builder() {

		Transition.Builder config = Transition.config()
				.from(DiseaseStatus.susceptible,
						to(DiseaseStatus.infectedButNotContagious, Transition.fixed(2)))
				.from(DiseaseStatus.infectedButNotContagious,
						to(DiseaseStatus.contagious, Transition.fixed(5)))
				.from(DiseaseStatus.contagious,
						to(DiseaseStatus.showingSymptoms, Transition.logNormalWithMeanAndStd(2, 2)),
						to(DiseaseStatus.seriouslySick, Transition.logNormalWithMean(4, 1)));

		Transition.Builder b2 = Transition.parse(config.build());

		assertThat(config.asArray())
				.contains(Transition.fixed(2), Index.atIndex(DiseaseStatus.infectedButNotContagious.ordinal()))
				.containsOnlyOnce(Transition.fixed(5))
				.contains(Transition.logNormalWithMeanAndStd(2, 2), Transition.logNormalWithMean(4, 1))
				.areExactly(4, new Condition<>(Objects::nonNull, "Not null"))
				.isEqualTo(b2.asArray());

	}
}

package org.matsim.episim.model;

import org.apache.commons.math3.util.FastMath;
import org.matsim.episim.EpisimUtils;

import java.util.SplittableRandom;

/**
 * Describes how long a person stays in a certain state.
 */
public interface Transition {

	/**
	 * Deterministic transition at day {@code day}.
	 */
	static Transition fixed(int day) {
		return new FixedTransition(day);
	}

	/**
	 * Probabilistic transition with log normal distribution.
	 * Parameter of the distribution will be calculated from given mean und standard deviation.
	 *
	 * @see LogNormalTransition
	 */
	static Transition logNormal(double mean, double std) {

		double mu = Math.log((mean * mean) / Math.sqrt(mean * mean + std * std));
		double sigma = Math.log(1 + (std * std) / (mean * mean));

		return new LogNormalTransition(mu, Math.sqrt(sigma));
	}

	/**
	 * Returns the day when the transition should occur.
	 */
	int getTransitionDay(SplittableRandom rnd);

	/**
	 * Implementation for a fixed transition.
	 */
	final class FixedTransition implements Transition {

		private final int day;

		public FixedTransition(int day) {
			this.day = day;
		}

		@Override
		public int getTransitionDay(SplittableRandom rnd) {
			return day;
		}
	}

	/**
	 * Implementation for log normal distributed transition.
	 * @see EpisimUtils#nextLogNormal(SplittableRandom, double, double)
	 */
	final class LogNormalTransition implements Transition {

		private final double mu;
		private final double sigma;

		public LogNormalTransition(double mu, double sigma) {
			this.mu = mu;
			this.sigma = sigma;
		}

		@Override
		public int getTransitionDay(SplittableRandom rnd) {
			return (int) FastMath.round(EpisimUtils.nextLogNormal(rnd, mu, sigma));
		}
	}
}

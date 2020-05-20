package org.matsim.episim.model;

import org.apache.commons.math3.util.FastMath;
import org.matsim.episim.EpisimUtils;

import java.util.SplittableRandom;

/**
 * Describes how long a person stays in a certain state.
 * This interface also provides factory methods for some common transitions.
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
	 * @param mean desired mean of the distribution
	 * @param std  desired standard deviation
	 * @see LogNormalTransition
	 */
	static Transition logNormalWithMean(double mean, double std) {

		double mu = Math.log((mean * mean) / Math.sqrt(mean * mean + std * std));
		double sigma = Math.log(1 + (std * std) / (mean * mean));

		return new LogNormalTransition(mu, Math.sqrt(sigma));
	}

	/**
	 * Same as {@link #logNormalWithMean(double, double)}
	 */
	static Transition logNormalWithMeanAndStd(double mean, double std) {
		return logNormalWithMean(mean, std);
	}

	/**
	 * Probabilistic state transition with log normal distribution.
	 *
	 * @param median desired median, i.e. exp(mu)
	 * @param sigma  sigma parameter
	 * @see LogNormalTransition
	 */
	static Transition logNormalWithMedian(double median, double sigma) {

		double mu = Math.log(median);
		return new LogNormalTransition(mu, sigma);
	}

	/**
	 * Probabilistic state transition with log normal distribution.
	 *
	 * @param median desired median, i.e. exp(mu)
	 * @param std    desired standard deviation
	 * @see LogNormalTransition
	 */
	static Transition logNormalWithMedianAndStd(double median, double std) {

		double mu = Math.log(median);

		// Given the formula for std:
		// \sqrt{\operatorname{Var}(X)}= \sqrt{\mathrm{e}^{2\mu+\sigma^{2}}(\mathrm{e}^{\sigma^{2}}-1)}=\mathrm{e}^{\mu+\frac{\sigma^{2}}{2}}\cdot\sqrt{\mathrm{e}^{\sigma^{2}}-1}

		// solve for sigma
		// https://www.wolframalpha.com/input/?i=solve+e%5E%28mu+%2B+s+%2F+2%29+*+sqrt%28e%5Es+-+1%29+%3D+x+for+s
		double ssigma = Math.log(0.5 * Math.exp(-2 * mu) * (Math.exp(2 * mu) + Math.sqrt(Math.exp(4 * mu) + 4 * Math.exp(2 * mu) * std * std)));

		return new LogNormalTransition(mu, Math.sqrt(ssigma));
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

		private FixedTransition(int day) {
			this.day = day;
		}

		@Override
		public int getTransitionDay(SplittableRandom rnd) {
			return day;
		}
	}

	/**
	 * Implementation for log normal distributed transition.
	 *
	 * @see EpisimUtils#nextLogNormal(SplittableRandom, double, double)
	 */
	final class LogNormalTransition implements Transition {

		private final double mu;
		private final double sigma;

		private LogNormalTransition(double mu, double sigma) {
			this.mu = mu;
			this.sigma = sigma;
		}

		@Override
		public int getTransitionDay(SplittableRandom rnd) {
			return (int) FastMath.round(EpisimUtils.nextLogNormal(rnd, mu, sigma));
		}
	}
}

package org.matsim.episim.model;

import com.google.common.base.Objects;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import org.apache.commons.math3.util.FastMath;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.EpisimUtils;

import java.util.*;

/**
 * Describes how long a person stays in a certain state.
 * Also provides factory methods for all available transitions.
 * <p>
 * Please note that it is not possible nor intended to inherit from this class outside of this package,
 * as this would break serialization.
 */
public abstract class Transition {

	/**
	 * Inheritance is prohibited for external classes.
	 */
	Transition() {
	}

	/**
	 * Parse Transition builder from a config file.
	 */
	public static Builder parse(Config config) {
		return new Builder(config);
	}

	/**
	 * Create a new transition config builder.
	 */
	public static Builder config() {
		return new Builder((String) null);
	}

	/**
	 * Create a new transition config builder with a filename, that will be used if the config is persisted.
	 */
	public static Builder config(String filename) {
		return new Builder(filename);
	}

	/**
	 * Creates a to transition, to be used in conjunction with the {@link Builder}.
	 *
	 * @param status target state
	 * @param t      desired transition
	 */
	public static ToHolder to(DiseaseStatus status, Transition t) {
		return new ToHolder(status, t);
	}

	/**
	 * Deterministic transition at day {@code day}.
	 */
	public static Transition fixed(int day) {
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
	public static Transition logNormalWithMean(double mean, double std) {

		// mean==median if std=0
		if (std == 0) return logNormalWithMedianAndSigma(mean, 0);

		double mu = Math.log((mean * mean) / Math.sqrt(mean * mean + std * std));
		double sigma = Math.log(1 + (std * std) / (mean * mean));

		return new LogNormalTransition(mu, Math.sqrt(sigma));
	}

	/**
	 * Same as {@link #logNormalWithMean(double, double)}.
	 */
	public static Transition logNormalWithMeanAndStd(double mean, double std) {
		return logNormalWithMean(mean, std);
	}

	/**
	 * Probabilistic state transition with log normal distribution.
	 *
	 * @param median desired median, i.e. exp(mu)
	 * @param sigma  sigma parameter
	 * @see LogNormalTransition
	 */
	public static Transition logNormalWithMedianAndSigma(double median, double sigma) {

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
	public static Transition logNormalWithMedianAndStd(double median, double std) {

		// equation below is numerical unstable for std near zero
		if (std == 0) return logNormalWithMedianAndSigma(median, 0);

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
	public abstract int getTransitionDay(SplittableRandom rnd);

	/**
	 * Implementation for a fixed transition.
	 */
	private static final class FixedTransition extends Transition {

		private final int day;

		private FixedTransition(int day) {
			this.day = day;
		}

		@Override
		public int getTransitionDay(SplittableRandom rnd) {
			return day;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			FixedTransition that = (FixedTransition) o;
			return day == that.day;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(day);
		}
	}

	/**
	 * Implementation for log normal distributed transition.
	 *
	 * @see EpisimUtils#nextLogNormal(SplittableRandom, double, double)
	 */
	private static final class LogNormalTransition extends Transition {

		private final double mu;
		private final double sigma;

		private LogNormalTransition(double mu, double sigma) {
			this.mu = mu;
			this.sigma = sigma;

			if (sigma < 0 || Double.isNaN(sigma))
				throw new IllegalArgumentException("Sigma must be >= 0");
		}

		@Override
		public int getTransitionDay(SplittableRandom rnd) {
			return (int) FastMath.round(EpisimUtils.nextLogNormal(rnd, mu, sigma));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			LogNormalTransition that = (LogNormalTransition) o;
			return Double.compare(that.mu, mu) == 0 &&
					Double.compare(that.sigma, sigma) == 0;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(mu, sigma);
		}
	}

	/**
	 * Builder for a transition config.
	 */
	public static final class Builder {

		private final String origin;
		private final Map<DiseaseStatus, Map<DiseaseStatus, Transition>> transitions = new EnumMap<>(DiseaseStatus.class);

		private Builder(String origin) {
			this.origin = origin;
		}

		/**
		 * Initialize from config.
		 */
		@SuppressWarnings("unchecked")
		private Builder(Config config) {

			for (Map.Entry<String, ConfigValue> e : config.root().entrySet()) {

				DiseaseStatus status = DiseaseStatus.valueOf(e.getKey());
				Config toConfig = config.getConfig(e.getKey());

				List<ToHolder> tos = new ArrayList<>();

				for (Map.Entry<String, ConfigValue> to : toConfig.root().entrySet()) {

					Map<String, String> params = (Map<String, String>) to.getValue().unwrapped();

					DiseaseStatus toStatus = DiseaseStatus.valueOf(to.getKey());
					Transition t;
					if (params.get("type").equals("FixedTransition"))
						t = new FixedTransition(Integer.parseInt(params.get("day")));
					else if (params.get("type").equals("LogNormalTransition"))
						t = new LogNormalTransition(Double.parseDouble(params.get("mu")), Double.parseDouble(params.get("sigma")));
					else
						throw new IllegalArgumentException("Could not parse transition: " + params);

					tos.add(to(toStatus, t));
				}

				from(status, tos.toArray(new ToHolder[0]));
			}

			this.origin = config.origin().description();
		}

		/**
		 * Defines which transitions should be taken from the state {@code} status to the states defined in {@code to}.
		 *
		 * @param status the current disease status
		 * @param to     collection of target states and their transitions.
		 * @see #to(DiseaseStatus, Transition)
		 */
		public Builder from(DiseaseStatus status, ToHolder... to) {
			if (to.length == 0) throw new IllegalArgumentException("No target states specified");

			for (ToHolder t : to) {
				transitions.computeIfAbsent(status, (k) -> new EnumMap<>(DiseaseStatus.class))
						.put(t.status, t.t);
			}
			return this;
		}

		/**
		 * Creates a config representation.
		 */
		public Config build() {

			Map<String, Object> config = new LinkedHashMap<>();

			for (Map.Entry<DiseaseStatus, Map<DiseaseStatus, Transition>> e : transitions.entrySet()) {

				Map<String, Object> toConfig = new LinkedHashMap<>();

				for (Map.Entry<DiseaseStatus, Transition> to : e.getValue().entrySet()) {
					// params of the transition
					Map<String, String> params = new LinkedHashMap<>();

					Transition t = to.getValue();

					params.put("type", t.getClass().getSimpleName());

					if (t instanceof FixedTransition) {
						params.put("day", String.valueOf(((FixedTransition) t).day));
					} else if (t instanceof LogNormalTransition) {
						params.put("mu", String.valueOf(((LogNormalTransition) t).mu));
						params.put("sigma", String.valueOf(((LogNormalTransition) t).sigma));
					} else
						throw new IllegalArgumentException("Can not serialize unknown transition " + t);

					toConfig.put(to.getKey().name(), params);
				}

				config.put(e.getKey().name(), toConfig);
			}

			return ConfigFactory.parseMap(config, origin);
		}


		/**
		 * Returns the config as matrix with entries as transition from -> to, according to {@link DiseaseStatus#ordinal()}.
		 * Not defined transitions will be null.
		 */
		public Transition[] asArray() {
			Transition[] array = new Transition[DiseaseStatus.values().length * DiseaseStatus.values().length];

			for (Map.Entry<DiseaseStatus, Map<DiseaseStatus, Transition>> e : transitions.entrySet()) {
				for (Map.Entry<DiseaseStatus, Transition> to : e.getValue().entrySet()) {
					array[e.getKey().ordinal() * DiseaseStatus.values().length + to.getKey().ordinal()] = to.getValue();
				}
			}

			return array;
		}

		@Override
		public String toString() {
			return build().root().render(ConfigRenderOptions.concise().setJson(false));
		}
	}

	/**
	 * Holder class that saves the target status and desired transition.
	 */
	public static final class ToHolder {

		public final DiseaseStatus status;
		public final Transition t;

		private ToHolder(DiseaseStatus status, Transition t) {
			this.status = status;
			this.t = t;
		}

		@Override
		public String toString() {
			return "ToHolder{" +
					"status=" + status +
					", t=" + t +
					'}';
		}
	}
}

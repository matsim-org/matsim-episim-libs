package org.matsim.episim.model.input;

import com.google.common.collect.Iterables;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.policy.AdjustedPolicy;
import org.matsim.episim.policy.FixedPolicy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

public final class CreateAdjustedRestrictionsFromCSV implements RestrictionInput {
	// This class does not need a builder, because all functionality is in the create method.  One can re-configure the class and re-run the
	// create method without damage.

	private Path input;
	private double alpha = 1.;
	private EpisimUtils.Extrapolation extrapolation = EpisimUtils.Extrapolation.none;
	private FixedPolicy.ConfigBuilder policy;

	private Map<String, LocalDate[]> periods = new HashMap<>();

	@Override
	public CreateAdjustedRestrictionsFromCSV setInput(Path input) {
		this.input = input;
		return this;
	}

	@Override
	public CreateAdjustedRestrictionsFromCSV setAlpha(double alpha) {
		this.alpha = alpha;
		return this;
	}

	/**
	 * Set the administrative policy.
	 */
	public CreateAdjustedRestrictionsFromCSV setPolicy(FixedPolicy.ConfigBuilder policy) {
		this.policy = policy;
		return this;
	}

	/**
	 * Set the periods of the administrative policies.
	 * @see AdjustedPolicy.ConfigBuilder#administrativePeriod(String, LocalDate...)
	 * @param periods periods to set for all activities
	 */
	public CreateAdjustedRestrictionsFromCSV setAdministrativePeriods(Map<String, LocalDate[]> periods) {
		this.periods = periods;
		return this;
	}

	public double getAlpha() {
		return alpha;
	}

	public CreateAdjustedRestrictionsFromCSV setExtrapolation(EpisimUtils.Extrapolation extrapolation) {
		this.extrapolation = extrapolation;
		return this;
	}

	public EpisimUtils.Extrapolation getExtrapolation() {
		return extrapolation;
	}

	@Override
	public AdjustedPolicy.ConfigBuilder createPolicy() throws IOException {

		Objects.requireNonNull(policy, "Administrative policy must be set beforehand");

		Map<LocalDate, Double> days = CreateRestrictionsFromCSV.readInput(input.toString(), "notAtHome", alpha, 1. );

		LocalDate start = Objects.requireNonNull(Iterables.getFirst(days.keySet(), null), "CSV is empty");

		AdjustedPolicy.ConfigBuilder builder = AdjustedPolicy.config();

		Map<LocalDate, Double> fractions = new HashMap<>();

		// trend used for extrapolation
		List<Double> trend = new ArrayList<>();

		start = RestrictionInput.resampleAvgWeekday(days, start, (date, avg) -> {
			trend.add(avg);
			fractions.put(date, avg);
		});


		// Use last weeks for the trend
		List<Double> recentTrend = trend.subList(Math.max(0, trend.size() - 8), trend.size());
		start = start.plusDays(7);

		for (Double predict : RestrictionInput.extrapolate(recentTrend, 25, extrapolation)) {
			fractions.put(start, Math.min(predict, 1));
			start = start.plusDays(7);
		}

		builder.outOfHomeFractions(fractions);
		builder.administrativePolicy(policy);

		for (Map.Entry<String, LocalDate[]> e : periods.entrySet()) {
			builder.administrativePeriod(e.getKey(), e.getValue());
		}

		return builder;
	}

	@Override
	public String toString() {
		return "fromCSVAdjusted-" +
				"alpha_" + alpha +
				", extrapolation_" + extrapolation +
				'}';
	}

}

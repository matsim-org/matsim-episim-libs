package org.matsim.episim.model.input;

import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.policy.AdjustedPolicy;
import org.matsim.episim.policy.FixedPolicy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public final class CreateAdjustedRestrictionsFromCSV implements ActivityParticipation {
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

		Map<LocalDate, Double> days = CreateRestrictionsFromCSV.readInput(input, "notAtHome", alpha);

		Set<LocalDate> ignored = Resources.readLines(Resources.getResource("bankHolidays.txt"), StandardCharsets.UTF_8)
				.stream().map(LocalDate::parse).collect(Collectors.toSet());

		LocalDate start = Objects.requireNonNull(Iterables.getFirst(days.keySet(), null), "CSV is empty");
		LocalDate end = Iterables.getLast(days.keySet());

		AdjustedPolicy.ConfigBuilder builder = AdjustedPolicy.config();

		Map<LocalDate, Double> fractions = new HashMap<>();

		// trend used for extrapolation
		List<Double> trend = new ArrayList<>();

		while (start.isBefore(end)) {

			List<Double> values = new ArrayList<>();
			for (int i = 0; i < 7; i++) {
				LocalDate day = start.plusDays(i);
				if (!ignored.contains(day) && day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY
						&& day.getDayOfWeek() != DayOfWeek.FRIDAY) {
					values.add(days.get(day));
				}
			}
			double avg = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
			// (the above results in a weekly average. Not necessarily all days for the same week, but this is corrected below)

			trend.add(avg);

			// calc next sunday:
			int n = 7 - start.getDayOfWeek().getValue() % 7;
			fractions.put(start, avg);
			start = start.plusDays(n);
		}


		// Use last weeks for the trend
		trend = trend.subList(Math.max(0, trend.size() - 8), trend.size());
		start = start.plusDays(7);

		if (extrapolation == EpisimUtils.Extrapolation.linear) {
			int n = trend.size();

			SimpleRegression reg = new SimpleRegression();
			for (int i = 0; i < n; i++) {
				reg.addData(i, trend.get(i));
			}

			// continue the trend
			for (int i = 0; i < 8; i++) {
				fractions.put(start, Math.min(reg.predict(n + i), 1));
				// System.out.println(start + " " + reg.predict(n + i));
				start = start.plusDays(7);
			}

		} else if (extrapolation == EpisimUtils.Extrapolation.exponential) {
			int n = trend.size();

			List<WeightedObservedPoint> points = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				points.add(new WeightedObservedPoint(1.0, i, trend.get(i)));
			}

			CreateRestrictionsFromCSV.Exponential expFunction = new CreateRestrictionsFromCSV.Exponential();
			EpisimUtils.FuncFitter fitter = new EpisimUtils.FuncFitter(expFunction);
			double[] coeff = fitter.fit(points);

			// continue the trend
			for (int i = 0; i < 25; i++) {

				double predict = expFunction.value(i + n, coeff);
				// System.out.println(start + " " + predict);
				fractions.put(start, Math.min(predict, 1));
				start = start.plusDays(7);
			}
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

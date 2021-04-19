package org.matsim.episim.model.input;

import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.policy.FixedPolicy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Interface for providing activity participation data.
 */
public interface ActivityParticipation {

	/**
	 * Sets the input for this file.
	 */
	ActivityParticipation setInput(Path input);

	/**
	 * Provide policy with activity reduction.
	 */
	FixedPolicy.ConfigBuilder createPolicy() throws IOException;



	/**
	 * Interpolate series of {@code trend} by {@code n} elements using method given by {@code type}.
	 *
	 * @return list of size n, containing the extrapolated elements.
	 */
	static List<Double> extrapolate(List<Double> trend, int n, EpisimUtils.Extrapolation type) {

		int size = trend.size();
		List<Double> result = new ArrayList<>();

		if (type == EpisimUtils.Extrapolation.linear) {

			SimpleRegression reg = new SimpleRegression();
			for (int i = 0; i < size; i++) {
				reg.addData(i, trend.get(i));
			}

			// continue the trend
			for (int i = 0; i < n; i++) {
				result.add(reg.predict(n + i));
			}
		} else if (type == EpisimUtils.Extrapolation.exponential) {

			List<WeightedObservedPoint> points = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				points.add(new WeightedObservedPoint(1.0, i, trend.get(i)));
			}

			CreateRestrictionsFromCSV.Exponential expFunction = new CreateRestrictionsFromCSV.Exponential();
			EpisimUtils.FuncFitter fitter = new EpisimUtils.FuncFitter(expFunction);
			double[] coeff = fitter.fit(points);

			// continue the trend
			for (int i = 0; i < n; i++) {
				double predict = expFunction.value(i + n, coeff);
				result.add(predict);
			}

		}


		return result;
	}

	/**
	 * Resamples given values to weekly averages, ignoring weekend and holidays.
	 * The resampling result will be feed into consumer function {@code f} and not returned directly.
	 * @param start start of sampling interval
	 * @param f bi consumer that receives the date and average weekly value
	 * @return date for next week after last sampling
	 */
	static LocalDate resampleAvgWeekday(Map<LocalDate, Double> values, LocalDate start, BiConsumer<LocalDate, Double> f) {

		Set<LocalDate> ignored;
		try {
			ignored = Resources.readLines(Resources.getResource("bankHolidays.txt"), StandardCharsets.UTF_8)
					.stream().map(LocalDate::parse).collect(Collectors.toSet());
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read bank holidays.", e);
		}

		LocalDate end = Iterables.getLast(values.keySet());

		while (start.isBefore(end)) {

			DoubleList week = new DoubleArrayList();
			for (int i = 0; i < 7; i++) {
				LocalDate day = start.plusDays(i);
				if (!ignored.contains(day) && day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY
						&& day.getDayOfWeek() != DayOfWeek.FRIDAY && values.containsKey(day)) {
					week.add((double) values.get(day));
				}
			}

			// TODO: use .doubleStream#()
			double avg = week.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
			// (the above results in a weekly average. Not necessarily all days for the same week, but this is corrected below)

			f.accept(start, avg);

			// calc next sunday:
			int n = 7 - start.getDayOfWeek().getValue() % 7;
			start = start.plusDays(n);
		}

		return start;
	}

}

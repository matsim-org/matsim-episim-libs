package org.matsim.episim.model.input;

import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.logging.log4j.util.TriConsumer;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.policy.ShutdownPolicy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Interface for providing activity participation data.
 */
public interface RestrictionInput {

	/**
	 * Sets the input for this file.
	 */
	RestrictionInput setInput(Path input);

	/**
	 * Parameter to modulates the activity participation.
	 */
	default RestrictionInput setAlpha(double alpha) {
		return this;
	}

	/**
	 * Provide policy with activity reduction.
	 */
	ShutdownPolicy.ConfigBuilder<?> createPolicy() throws IOException;


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
			for (int i = 0; i < Math.min(size, n); i++) {
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

		} else if (type == EpisimUtils.Extrapolation.regHospital) {
			for (int i = 0; i < n; i++) {
				result.add(ShutdownPolicy.REG_HOSPITAL);
			}
		}


		return result;
	}

	/**
	 * Resamples given values to weekly averages, ignoring weekend and holidays.
	 * The resampling result will be feed into consumer function {@code f} and not returned directly.
	 *
	 * @param start start of sampling interval
	 * @param f     bi consumer that receives the date and average weekly value
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

			double avg = week.doubleStream().average().orElseThrow();
			// (the above results in a weekly average. Not necessarily all days for the same week, but this is corrected below)

			f.accept(start, avg);

			// calc next sunday:
			int n = 7 - start.getDayOfWeek().getValue() % 7;
			start = start.plusDays(n);
		}

		return start;
	}

	static LocalDate resampleAvgWeekdayBySubdistrict(Map<LocalDate, Double> daysGlobal,
	                                                 Map<String, Map<LocalDate, Double>> daysPerDistrict,
	                                                 LocalDate start,
	                                                 TriConsumer<LocalDate, Double, Map<String, Double>> f) {

		Set<LocalDate> ignored;
		try {
			ignored = Resources.readLines(Resources.getResource("bankHolidays.txt"), StandardCharsets.UTF_8)
					.stream().map(LocalDate::parse).collect(Collectors.toSet());
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read bank holidays.", e);
		}

		LocalDate end = Iterables.getLast(daysGlobal.keySet());

		while (start.isBefore(end)) {

			DoubleList weekGlobal = new DoubleArrayList();
			Map<String, DoubleArrayList> weekPerDistrict = new HashMap<>();

			for (int i = 0; i < 7; i++) {
				LocalDate day = start.plusDays(i);
				if (!ignored.contains(day) && day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY
						&& day.getDayOfWeek() != DayOfWeek.FRIDAY) {
					if (daysGlobal.containsKey(day)) {
						weekGlobal.add((double) daysGlobal.get(day));
					}
					// now per district
					for (Map.Entry<String, Map<LocalDate, Double>> entry : daysPerDistrict.entrySet()) {
						String districtName = entry.getKey();
						Map<LocalDate, Double> daysForDistrict = entry.getValue();
						if (daysForDistrict.containsKey(day)) {
							DoubleArrayList weekForDistrict = weekPerDistrict.getOrDefault(districtName, new DoubleArrayList());
							weekForDistrict.add((double) daysForDistrict.get(day));
							weekPerDistrict.put(districtName, weekForDistrict);
						}
					}
				}
			}

			double avg = weekGlobal.doubleStream().average().orElseThrow();

			Map<String, Double> avgPerDistrict = new HashMap<>();
			for (String districtName : weekPerDistrict.keySet()) {
				double avgForDistrict = weekPerDistrict.get(districtName).doubleStream().average().orElseThrow();
				avgPerDistrict.put(districtName, avgForDistrict);
			}
			// (the above results in a weekly average. Not necessarily all days for the same week, but this is corrected below)

			f.accept(start, avg, avgPerDistrict);

			// calc next sunday:
			int n = 7 - start.getDayOfWeek().getValue() % 7;
			start = start.plusDays(n);
		}

		return start;
	}

}

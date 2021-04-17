package org.matsim.episim.model.input;

import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.analysis.ParametricUnivariateFunction;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.policy.FixedPolicy;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public final class CreateRestrictionsFromCSV implements ActivityParticipation {
	// This class does not need a builder, because all functionality is in the create method.  One can re-configure the class and re-run the
	// create method without damage.

	private final EpisimConfigGroup episimConfig;
	private File input;
	private double alpha = 1.;
	private EpisimUtils.Extrapolation extrapolation = EpisimUtils.Extrapolation.none;

	public CreateRestrictionsFromCSV(EpisimConfigGroup episimConfig) {
		this.episimConfig = episimConfig;
	}


	@Override
	public CreateRestrictionsFromCSV setInput(Path input) {
		// Not in constructor: could be taken from episim config; (2) no damage in changing it and rerunning.  kai, dec'20
		this.input = input.toFile();
		return this;
	}

	public CreateRestrictionsFromCSV setAlpha(double alpha) {
		this.alpha = alpha;
		return this;
	}

	public double getAlpha() {
		return alpha;
	}

	public CreateRestrictionsFromCSV setExtrapolation(EpisimUtils.Extrapolation extrapolation) {
		this.extrapolation = extrapolation;
		return this;
	}

	public EpisimUtils.Extrapolation getExtrapolation() {
		return extrapolation;
	}

	@Override
	public FixedPolicy.ConfigBuilder createPolicy() throws IOException {
		Reader in = new FileReader(input);
		CSVParser parser = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter('\t').parse(in);
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");

		// activity reduction for notAtHome each day
		Map<LocalDate, Double> days = new LinkedHashMap<>();

		for (CSVRecord record : parser) {
			LocalDate date = LocalDate.parse(record.get(0), fmt);

			int value = Integer.parseInt(record.get("notAtHomeExceptLeisureAndEdu"));
			// ("except edu" since we set it separately.  yyyy but why "except leisure"??  kai, dec'20)

			double remainingFraction = 1. + (value / 100.);

			// modulate reduction with alpha:
			double reduction = Math.min(1., alpha * (1. - remainingFraction));
			days.put(date, Math.min(1, 1 - reduction));
		}

		Set<LocalDate> ignored = Resources.readLines(Resources.getResource("bankHolidays.txt"), StandardCharsets.UTF_8)
				.stream().map(LocalDate::parse).collect(Collectors.toSet());

		// activities to set:
		String[] act = episimConfig.getInfectionParams().stream()
				.map(EpisimConfigGroup.InfectionParams::getContainerName)
				.filter(name -> !name.startsWith("edu") && !name.startsWith("pt") && !name.startsWith("tr") && !name.contains("home"))
				.toArray(String[]::new);

		LocalDate start = Objects.requireNonNull(Iterables.getFirst(days.keySet(), null), "CSV is empty");
		LocalDate end = Iterables.getLast(days.keySet());

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();

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
			builder.restrict(start, avg, act);
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
				builder.restrict(start, Math.min(reg.predict(n + i), 1), act);
				// System.out.println(start + " " + reg.predict(n + i));
				start = start.plusDays(7);
			}

		} else if (extrapolation == EpisimUtils.Extrapolation.exponential) {
			int n = trend.size();

			List<WeightedObservedPoint> points = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				points.add(new WeightedObservedPoint(1.0, i, trend.get(i)));
			}

			Exponential expFunction = new Exponential();
			EpisimUtils.FuncFitter fitter = new EpisimUtils.FuncFitter(expFunction);
			double[] coeff = fitter.fit(points);

			// continue the trend
			for (int i = 0; i < 25; i++) {

				double predict = expFunction.value(i + n, coeff);
				// System.out.println(start + " " + predict);
				builder.restrict(start, Math.min(predict, 1), act);
				start = start.plusDays(7);
			}
		}


		return builder;
	}

	@Override
	public String toString() {
		return "fromCSV-" +
				"alpha_" + alpha +
				", extrapolation_" + extrapolation +
				'}';
	}

	/**
	 * Exponential function in the form of 1 - a * exp(-x / b).
	 */
	static final class Exponential implements ParametricUnivariateFunction {

		@Override
		public double value(double x, double... parameters) {
			return 1 - parameters[0] * Math.exp(-x / parameters[1]);
		}

		@Override
		public double[] gradient(double x, double... parameters) {
			double exb = Math.exp(-x / parameters[1]);
			return new double[]{-exb, -parameters[0] * x * exb / (parameters[1] * parameters[1])};
		}
	}

}

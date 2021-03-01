package org.matsim.episim.model.input;

import com.google.common.collect.Iterables;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.analysis.ParametricUnivariateFunction;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.policy.FixedPolicy;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class CreateRestrictionsFromCSV implements ActivityParticipation {
	// This class does not need a builder, because all functionality is in the create method.  One can re-configure the class and re-run the
	// create method without damage.

	private final EpisimConfigGroup episimConfig;
	private Path input;
	private double alpha = 1.;
	private EpisimUtils.Extrapolation extrapolation = EpisimUtils.Extrapolation.none;

	public CreateRestrictionsFromCSV(EpisimConfigGroup episimConfig) {
		this.episimConfig = episimConfig;
	}


	@Override
	public CreateRestrictionsFromCSV setInput(Path input) {
		// Not in constructor: could be taken from episim config; (2) no damage in changing it and rerunning.  kai, dec'20
		this.input = input;
		return this;
	}

	@Override
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

	static Map<LocalDate, Double> readInput(Path input, String column, double alpha) throws IOException {

		try (BufferedReader in = Files.newBufferedReader(input)) {

			CSVParser parser = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter('\t').parse(in);
			DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");

			// activity reduction for notAtHome each day
			Map<LocalDate, Double> days = new LinkedHashMap<>();

			for (CSVRecord record : parser) {
				LocalDate date = LocalDate.parse(record.get(0), fmt);

				int value = Integer.parseInt(record.get(column));

				double remainingFraction = 1. + (value / 100.);

				// modulate reduction with alpha:
				double reduction = Math.min(1., alpha * (1. - remainingFraction));
				days.put(date, Math.min(1, 1 - reduction));
			}

			return days;

		}
	}

	@Override
	public FixedPolicy.ConfigBuilder createPolicy() throws IOException {

		// ("except edu" since we set it separately.  yyyy but why "except leisure"??  kai, dec'20)
		Map<LocalDate, Double> days = readInput(input, "notAtHomeExceptLeisureAndEdu", alpha);


		// activities to set:
		String[] act = episimConfig.getInfectionParams().stream()
				.map(EpisimConfigGroup.InfectionParams::getContainerName)
				.filter(name -> !name.startsWith("edu") && !name.startsWith("pt") && !name.startsWith("tr") && !name.contains("home"))
				.toArray(String[]::new);

		LocalDate start = Objects.requireNonNull(Iterables.getFirst(days.keySet(), null), "CSV is empty");

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();

		// trend used for extrapolation
		List<Double> trend = new ArrayList<>();

		ActivityParticipation.resampleAvgWeekday(days, start, (date, avg) -> {
			trend.add(avg);
			builder.restrict(date, avg, act);
		});

		// Use last weeks for the trend
		List<Double> recentTrend = trend.subList(Math.max(0, trend.size() - 8), trend.size());
		start = start.plusDays(7);

		for (Double predict : ActivityParticipation.extrapolate(recentTrend, 25, extrapolation)) {
			builder.restrict(start, Math.min(predict, 1), act);
			start = start.plusDays(7);
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

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class CreateRestrictionsFromCSV implements RestrictionInput {
	// This class does not need a builder, because all functionality is in the create method.  One can re-configure the class and re-run the
	// create method without damage.

	private final EpisimConfigGroup episimConfig;
	private Path input;
	private double alpha = 1.;
	private double scale = 1.;
	private boolean leisureAsNightly = false;
	private double nightlyScale = 1.;
	private EpisimUtils.Extrapolation extrapolation = EpisimUtils.Extrapolation.none;
	private Map<String, Path> subdistrictInput;

	public CreateRestrictionsFromCSV(EpisimConfigGroup episimConfig) {
		this.episimConfig = episimConfig;
	}


	@Override
	public CreateRestrictionsFromCSV setInput(Path input) {
		// Not in constructor: could be taken from episim config; (2) no damage in changing it and rerunning.  kai, dec'20
		this.input = input;
		return this;
	}

	/**
	 * Sets the paths for each subdistrict CSV
	 */
	public CreateRestrictionsFromCSV setDistrictInputs(Map<String, Path> subdistrictInput) {
		this.subdistrictInput = subdistrictInput;
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

	public CreateRestrictionsFromCSV setScale(double scale) {
		this.scale = scale;
		return this;
	}

	public CreateRestrictionsFromCSV setLeisureAsNightly(boolean leisureAsNightly) {
		this.leisureAsNightly = leisureAsNightly;
		return this;
	}

	public CreateRestrictionsFromCSV setNightlyScale(double nightlyScale) {
		this.nightlyScale = nightlyScale;
		return this;
	}

	public CreateRestrictionsFromCSV setExtrapolation(EpisimUtils.Extrapolation extrapolation) {
		this.extrapolation = extrapolation;
		return this;
	}

	public EpisimUtils.Extrapolation getExtrapolation() {
		return extrapolation;
	}

	static Map<LocalDate, Double> readInput(Path input, String column, double alpha, double scale) throws IOException {

		try (BufferedReader in = Files.newBufferedReader(input)) {

			CSVParser parser = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter('\t').parse(in);
			DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");

			// activity reduction for notAtHome each day
			Map<LocalDate, Double> days = new LinkedHashMap<>();

			for (CSVRecord record : parser) {
				LocalDate date = LocalDate.parse(record.get(0), fmt);

				int value = Integer.parseInt(record.get(column));

				double remainingFraction = (1. + (value / 100.)) / scale; // e.g. "1.2"

				// modulate reduction with alpha:
				double reduction = Math.min(1., alpha * (1. - remainingFraction)); // e.g. min( 1., alpha * (1-1.2) ) = min( 1., alpha * -0.2 ) ... i.e. the "alpha" does not help with values > 100.
				days.put(date, Math.min(1, 1 - reduction));
			}

			return days;

		}
	}

	@Override
	public FixedPolicy.ConfigBuilder createPolicy() throws IOException {

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();

		// activities to set:
		List<String> act = episimConfig.getInfectionParams().stream()
				.map(EpisimConfigGroup.InfectionParams::getContainerName)
				.filter(name -> !name.startsWith("edu") && !name.startsWith("pt") && !name.startsWith("tr") && !name.contains("home"))
				.collect(Collectors.toList());

		if (leisureAsNightly) {

			act.remove("leisure");

			createPolicy(builder, act.toArray(new String[0]), "notAtHome", scale);
			createPolicy(builder, new String[]{"leisure"}, "notAtHome_22", nightlyScale);

		} else {

			createPolicy(builder, act.toArray(new String[0]), "notAtHome", scale);

		}

		return builder;
	}

	private void createPolicy(FixedPolicy.ConfigBuilder builder, String[] act, String column, double scale) throws IOException {

		// If active, the remaining fraction is calculated and saved for each subdistrict
		boolean locationBasedRfActive = episimConfig.getDistrictLevelRestrictions().equals(EpisimConfigGroup.DistrictLevelRestrictions.yes)
				&& subdistrictInput != null && !subdistrictInput.isEmpty();

		// ("except edu" since we set it separately.  yyyy but why "except leisure"??  kai, dec'20)
		Map<LocalDate, Double> days = readInput(input, column, alpha, scale);

		// days per subdistrict
		Map<String, Map<LocalDate, Double>> daysPerDistrict = new HashMap<>();
		if (locationBasedRfActive) {
			for (Map.Entry<String, Path> entry : subdistrictInput.entrySet()) {
				daysPerDistrict.put(entry.getKey(), readInput(entry.getValue(), column, alpha, scale));
			}
		}

		LocalDate start = Objects.requireNonNull(Iterables.getFirst(days.keySet(), null), "CSV is empty");
		AtomicReference<LocalDate> until = new AtomicReference<>(start);

		// trend used for extrapolation
		List<Double> trend = new ArrayList<>();
		Map<String, List<Double>> trendPerDistrict = new HashMap<>();

		if (locationBasedRfActive) {
			RestrictionInput.resampleAvgWeekdayBySubdistrict(days, daysPerDistrict, start, (date, avg, avgPerDistrict) -> {
				for (String districtName : avgPerDistrict.keySet()) {
					trendPerDistrict.getOrDefault(districtName, new ArrayList<>()).add(avgPerDistrict.get(districtName));
				}
				trend.add(avg);
				builder.restrictWithDistrict(date, avgPerDistrict, avg, act);
				until.set(date);
			});
		} else {
			RestrictionInput.resampleAvgWeekday(days, start, (date, avg) -> {
				trend.add(avg);
				builder.restrict(date, avg, act);
				until.set(date);
			});
		}

		// Use last weeks for the trend
		List<Double> recentTrend = trend.subList(Math.max(0, trend.size() - 8), trend.size());
		start = until.get().plusDays(7);

		List<Double> extrapolateGlobal = RestrictionInput.extrapolate(recentTrend, 25, extrapolation);

		if (locationBasedRfActive) {
			Map<String, List<Double>> extrapolateByDistrict = new HashMap<>();
			for (String district : trendPerDistrict.keySet()) {
				List<Double> recentTrendForDistrict = trendPerDistrict.get(district).subList(Math.max(0, trendPerDistrict.size() - 8), trendPerDistrict.size());
				List<Double> extrapolateForDistrict = RestrictionInput.extrapolate(recentTrendForDistrict, 25, extrapolation);
				extrapolateByDistrict.put(district, extrapolateForDistrict);
			}

			for (int i = 0; i < extrapolateGlobal.size(); i++) {
				double predict = Math.min(extrapolateGlobal.get(i), 1);
				Map<String, Double> predictByDistrict = new HashMap<>();
				for (String district : extrapolateByDistrict.keySet()) {
					predictByDistrict.put(district, Math.min(extrapolateByDistrict.get(district).get(i), 1));
				}
				builder.restrictWithDistrict(start, predictByDistrict, predict, act);
				start = start.plusDays(7);
			}
		} else {
			for (Double predict : extrapolateGlobal) {
				builder.restrict(start, Math.min(predict, 1), act);
				start = start.plusDays(7);
			}
		}

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

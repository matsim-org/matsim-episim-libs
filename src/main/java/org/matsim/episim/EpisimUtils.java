/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.episim;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.analysis.ParametricUnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.fitting.AbstractCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.random.BitsStreamGenerator;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.commons.math3.util.FastMath;
import org.eclipse.collections.api.tuple.primitive.IntDoublePair;
import org.eclipse.collections.impl.tuple.primitive.PrimitiveTuples;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Common utility class for episim.
 */
public final class EpisimUtils {

	private EpisimUtils() {
	}

	/**
	 * Calculates the relative time based on iteration. Only used internally because start offset
	 * has to be added too.
	 */
	private static double getCorrectedTime(double time, long iteration) {
		return Math.min(time, 3600. * 24) + iteration * 24. * 3600;
	}

	/**
	 * Calculates the time based on the current iteration and start day.
	 *
	 * @param startDate offset of the start date
	 * @param time      time relative to start of day
	 * @see #getStartOffset(LocalDate)
	 */
	public static double getCorrectedTime(long startDate, double time, long iteration) {
		// start date refers to iteration 1, therefore 1 has to be subtracted
		// TODO: not yet working return startDate + getCorrectedTime(time, iteration - 1);

		return getCorrectedTime(time, iteration);
	}

	/**
	 * Calculates the start offset in seconds of simulation start.
	 */
	public static long getStartOffset(LocalDate startDate) {
		return startDate.atTime(LocalTime.MIDNIGHT).atZone(ZoneOffset.UTC).toEpochSecond();
	}


	/**
	 * Creates an output directory, with a name based on current config and contact intensity..
	 */
	public static void setOutputDirectory(Config config) {
		StringBuilder outdir = new StringBuilder("output");
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		for (EpisimConfigGroup.InfectionParams infectionParams : episimConfig.getInfectionParams()) {
			outdir.append("-");
			outdir.append(infectionParams.getContainerName());
			if (infectionParams.getContactIntensity() != 1.) {
				outdir.append("ci").append(infectionParams.getContactIntensity());
			}
		}
		config.controler().setOutputDirectory(outdir.toString());
	}

	/**
	 * Extracts the current state of a {@link SplittableRandom} instance.
	 */
	public static long getSeed(SplittableRandom rnd) {
		try {
			Field field = rnd.getClass().getDeclaredField("seed");
			field.setAccessible(true);
			return (long) field.get(rnd);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not extract seed", e);
		}
	}

	/**
	 * Sets current seed of {@link SplittableRandom} instance.
	 */
	public static void setSeed(SplittableRandom rnd, long seed) {
		try {
			Field field = rnd.getClass().getDeclaredField("seed");
			field.setAccessible(true);
			field.set(rnd, seed);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not extract seed", e);
		}
	}


	/**
	 * Compress directory recursively.
	 */
	public static void compressDirectory(String rootDir, String sourceDir, ArchiveOutputStream out) throws IOException {
		File[] fileList = new File(sourceDir).listFiles();
		if (fileList == null) return;
		for (File file : fileList) {
			// Zip files (i.e. other snapshots) are not added
			if (file.getName().endsWith(".zip"))
				continue;

			if (file.isDirectory()) {
				compressDirectory(rootDir, sourceDir + "/" + file.getName(), out);
			} else {
				ArchiveEntry entry = out.createArchiveEntry(file, "output" + sourceDir.replace(rootDir, "") + "/" + file.getName());
				out.putArchiveEntry(entry);
				FileUtils.copyFile(file, out);
				out.closeArchiveEntry();
			}
		}
	}

	/**
	 * Writes characters to output in null-terminated format.
	 */
	public static void writeChars(DataOutput out, String value) throws IOException {
		if (value == null) throw new IllegalArgumentException("Can not write null values!");

		byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
		out.writeInt(bytes.length);
		out.write(bytes);
	}

	/**
	 * Read null-terminated strings from input stream.
	 */
	public static String readChars(DataInput in) throws IOException {
		int length = in.readInt();
		byte[] content = new byte[length];
		in.readFully(content, 0, length);
		return new String(content, 0, length, StandardCharsets.ISO_8859_1);
	}

	/**
	 * Draw a gaussian distributed random number (mean=0, var=1).
	 *
	 * @param rnd splittable random instance
	 * @see BitsStreamGenerator#nextGaussian()
	 */
	public static double nextGaussian(SplittableRandom rnd) {
		// Normally this allows to generate two numbers, but one is thrown away because this function is stateless
		// generate a new pair of gaussian numbers
		final double x = rnd.nextDouble();
		final double y = rnd.nextDouble();
		final double alpha = 2 * FastMath.PI * x;
		final double r = FastMath.sqrt(-2 * FastMath.log(y));
		return r * FastMath.cos(alpha);
		// nextGaussian = r * FastMath.sin(alpha);
	}

	/**
	 * Draws a log normal distributed random number according to X=e^{\mu+\sigma Z}, where Z is a standard normal distribution.
	 *
	 * @param rnd   splittable random instance
	 * @param mu    mu ( median exp mu)
	 * @param sigma sigma
	 */
	public static double nextLogNormal(SplittableRandom rnd, double mu, double sigma) {
		if (sigma == 0)
			return Math.exp(mu);

		return Math.exp(sigma * nextGaussian(rnd) + mu);
	}
	public static double nextLogNormalFromMeanAndSigma( SplittableRandom rnd, double mean, double sigma ) {
		double mu = Math.log( mean ) - sigma*sigma/2;
		return nextLogNormal( rnd, mu, sigma );
	}

	/**
	 * Creates restrictions from csv from Senozon data.
	 * Restrictions at educational facilites are created manually.
	 * Weekends and bank holidays in 2020 are interpolated.
	 */
	public static FixedPolicy.ConfigBuilder createRestrictionsFromCSV(EpisimConfigGroup episimConfig, double alpha, LocalDate changeDate,
																	  double changedExposure) throws IOException {

		HashSet<String> activities = new HashSet<String>();

		HashSet<String> bankHolidays = new HashSet<String>();
		bankHolidays.add("2020-04-09"); // one before Karfreitag
		bankHolidays.add("2020-04-10"); //Karfreitag
		bankHolidays.add("2020-04-13"); //Ostermontag
		bankHolidays.add("2020-04-30"); // one before Tag der Arbeit
		bankHolidays.add("2020-05-01"); //Tag der Arbeit
		bankHolidays.add("2020-05-07"); // one before Tag der Befreiung
		bankHolidays.add("2020-05-08"); //Tag der Befreiung
		bankHolidays.add("2020-05-20"); // one before Himmelfahrt
		bankHolidays.add("2020-05-21"); //Himmelfahrt
		bankHolidays.add("2020-05-22"); //Brueckentag
		bankHolidays.add("2020-06-01"); //Pfingsten

		bankHolidays.add("2020-05-01");
		bankHolidays.add("2020-05-02");
		bankHolidays.add("2020-05-03");
		bankHolidays.add("2020-05-04");
		bankHolidays.add("2020-05-05");
		bankHolidays.add("2020-05-06");
		bankHolidays.add("2020-05-07");
		bankHolidays.add("2020-05-08");
		bankHolidays.add("2020-05-09");
		bankHolidays.add("2020-05-10");
		bankHolidays.add("2020-05-11");
		bankHolidays.add("2020-05-12");
		bankHolidays.add("2020-05-13");
		bankHolidays.add("2020-05-14");
		bankHolidays.add("2020-05-15");

		for (ConfigGroup a : episimConfig.getParameterSets().get("infectionParams")) {
			activities.add(a.getParams().get("activityType"));
		}

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();

		Reader in = new FileReader("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/BerlinSnzData_daily_until20200517.csv");
		Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter('\t').parse(in);

		HashMap<String, Double> lastRestrictions = new HashMap<String, Double>();
		boolean didRestriction = true;
		boolean doInterpolation = false; // == "interpolationOverWeekendsAndHolidays"?  yyyy  kai, may'20
		String lastDate = null;
		double exposure = 1.;
		for (CSVRecord record : records) {

			String date = record.get("date");
			String y = date.substring(0, 4);
			String m = date.substring(4, 6);
			String d = date.substring(6, 8);
			String corrDate = y + "-" + m + "-" + d;

			if (LocalDate.parse(corrDate).isEqual(changeDate) || LocalDate.parse(corrDate).isAfter(changeDate)) {
				exposure = changedExposure;
			}

			doInterpolation = false;

			if (!didRestriction && LocalDate.parse(corrDate).getDayOfWeek().getValue() <= 4 && !bankHolidays.contains(corrDate)) {
				// also exclude fridays.  also see below
				doInterpolation = true;
			}

			didRestriction = false;

			for (String activity : activities) {
				if (record.toMap().keySet().contains(activity)) {

					double remainingFraction = 1. + (Integer.parseInt(record.get(activity)) / 100.);

					if (remainingFraction > 1) remainingFraction = 1;

					if (!activity.contains("home")) {
						if (LocalDate.parse(corrDate).getDayOfWeek().getValue() <= 4 && !bankHolidays.contains(corrDate)) {
							// also exclude fridays.  also see above
							double reduction = Math.min(1., alpha * (1. - remainingFraction));
							builder.restrict(corrDate, Restriction.of(1. - reduction, exposure), activity);

							if (doInterpolation) {
								// yy why can't you use the interpolation facility provided by the framework?  kai, may'20
								int ii = LocalDate.parse(lastDate).until(LocalDate.parse(corrDate)).getDays();
								for (int jj = 1; jj < ii; jj++) {
									double interpolatedRemainingFraction = lastRestrictions.get(activity) + (remainingFraction - lastRestrictions.get(activity)) * (1.0 * jj / (1.0 * ii));
									double interpolatedReduction = Math.min(1., alpha * (1. - interpolatedRemainingFraction));
									builder.restrict(LocalDate.parse(lastDate).plusDays(jj), Restriction.of(1. - interpolatedReduction, exposure), activity);
									LocalDate.parse(lastDate).plusDays(jj);
								}
							}

							lastRestrictions.putIfAbsent(activity, remainingFraction);
							lastRestrictions.replace(activity, remainingFraction);
							didRestriction = true;

						}

					}

				}

			}

			if (didRestriction) lastDate = corrDate;

		}

		builder.restrict("2020-03-14", 0.1, "educ_primary", "educ_kiga")
				.restrict("2020-03-14", 0., "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		return builder;
	}

	/**
	 * Read in restrictions from csv file. A support point will be calculated and intermediate values calculated for each week.
	 *
	 * @param alpha modulate the amount reduction
	 */
	public static FixedPolicy.ConfigBuilder createRestrictionsFromCSV(EpisimConfigGroup episimConfig, File input, double alpha) throws IOException {

		Reader in = new FileReader(input);
		CSVParser parser = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter('\t').parse(in);
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");

		// Activity types to supporting points (x, y)
		Map<String, List<IntDoublePair>> points = new HashMap<>();

		// Init activity types
		for (EpisimConfigGroup.InfectionParams param : episimConfig.getInfectionParams()) {
			if (parser.getHeaderNames().contains(param.getContainerName()) && !param.getContainerName().equals("home"))
				points.put(param.getContainerName(), Lists.newArrayList(PrimitiveTuples.pair(0, 1d)));
		}

		// day index
		int day = 0;

		LocalDate start = null;
		int last = 0;

		for (CSVRecord record : parser) {

			LocalDate date = LocalDate.parse(record.get(0), fmt);
			if (start == null)
				start = date;

			// add supporting point
			if (date.getDayOfWeek() == DayOfWeek.THURSDAY) {

				// thursdays value will be the support value at saturday
				for (Map.Entry<String, List<IntDoublePair>> e : points.entrySet()) {

					double remainingFraction = 1. + (Integer.parseInt(record.get(e.getKey())) / 100.);
					// modulate reduction with alpha
					double reduction = Math.min(1., alpha * (1. - remainingFraction));

					e.getValue().add(PrimitiveTuples.pair(day + 2, 1 - reduction));

					last = day + 2;
				}
			}

			day++;
		}

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();

		LinearInterpolator p = new LinearInterpolator();

		for (Map.Entry<String, List<IntDoublePair>> e : points.entrySet()) {

			PolynomialSplineFunction f = p.interpolate(
					e.getValue().stream().mapToDouble(IntDoublePair::getOne).toArray(),
					e.getValue().stream().mapToDouble(IntDoublePair::getTwo).toArray()
			);

			// Interpolate for each day
			for (int i = 0; i < last; i++) {
				// interpolation could be greater 1
				double r = Math.min(1, f.value(i));
				builder.restrict(start.plusDays(i), r, e.getKey());
			}
		}

		return builder;
	}

	/**
	 * Same as {@link #createRestrictionsFromCSV2(EpisimConfigGroup, File, double, Extrapolation)} with no extrapolation.
	 */
	public static FixedPolicy.ConfigBuilder createRestrictionsFromCSV2(EpisimConfigGroup episimConfig, File input, double alpha) throws IOException {
		return createRestrictionsFromCSV2(episimConfig, input, alpha, Extrapolation.none);
	}

	/**
	 * Read in restriction from csv by taking the average reduction of all not at home activities and apply them to all other activities.
	 *
	 * @param alpha modulate the amount reduction
	 */
	public static FixedPolicy.ConfigBuilder createRestrictionsFromCSV2(EpisimConfigGroup episimConfig, File input, double alpha,
																	   Extrapolation extrapolate) throws IOException {

		Reader in = new FileReader(input);
		CSVParser parser = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter('\t').parse(in);
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");

		// activity reduction for notAtHome each day
		Map<LocalDate, Double> days = new LinkedHashMap<>();

		for (CSVRecord record : parser) {
			LocalDate date = LocalDate.parse(record.get(0), fmt);

			int value = Integer.parseInt(record.get("notAtHomeExceptLeisureAndEdu"));

			double remainingFraction = 1. + (value / 100.);
			// modulate reduction with alpha
			double reduction = Math.min(1., alpha * (1. - remainingFraction));
			days.put(date, Math.min(1, 1 - reduction));
		}

		Set<LocalDate> ignored = Resources.readLines(Resources.getResource("bankHolidays.txt"), StandardCharsets.UTF_8)
				.stream().map(LocalDate::parse).collect(Collectors.toSet());

		// activities to set
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
			trend.add(avg);

			// Calc next sunday
			int n = 7 - start.getDayOfWeek().getValue() % 7;
			builder.restrict(start, avg, act);
			start = start.plusDays(n);
		}


		// Use last weeks for the trend
		trend = trend.subList(Math.max(0, trend.size() - 8), trend.size());
		start = start.plusDays(7);

		if (extrapolate == Extrapolation.linear) {

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

		} else if (extrapolate == Extrapolation.exponential) {

			List<WeightedObservedPoint> points = new ArrayList<>();

			int n = trend.size();
			for (int i = 0; i < n; i++) {
				points.add(new WeightedObservedPoint(1.0, i, trend.get(i)));
			}

			Exponential f = new Exponential();
			FuncFitter fitter = new FuncFitter(f);
			double[] coeff = fitter.fit(points);

			// continue the trend
			for (int i = 0; i < 25; i++) {

				double predict = f.value(i + n, coeff);
				// System.out.println(start + " " + predict);
				builder.restrict(start, Math.min(predict, 1), act);
				start = start.plusDays(7);
			}
		}


		return builder;
	}


	/**
	 * Type of interpolation of activity pattern.
	 */
	public enum Extrapolation {none, linear, exponential}

	/**
	 * Function fitter using least squares.
	 * https://stackoverflow.com/questions/11335127/how-to-use-java-math-commons-curvefitter
	 */
	public static final class FuncFitter extends AbstractCurveFitter {

		private final ParametricUnivariateFunction f;

		public FuncFitter(ParametricUnivariateFunction f) {
			this.f = f;
		}

		protected LeastSquaresProblem getProblem(Collection<WeightedObservedPoint> points) {
			final int len = points.size();
			final double[] target = new double[len];
			final double[] weights = new double[len];
			final double[] initialGuess = {1.0, 1.0};

			int i = 0;
			for (WeightedObservedPoint point : points) {
				target[i] = point.getY();
				weights[i] = point.getWeight();
				i += 1;
			}

			final AbstractCurveFitter.TheoreticalValuesFunction model = new
					AbstractCurveFitter.TheoreticalValuesFunction(f, points);

			return new LeastSquaresBuilder().
					maxEvaluations(Integer.MAX_VALUE).
					maxIterations(Integer.MAX_VALUE).
					start(initialGuess).
					target(target).
					weight(new DiagonalMatrix(weights)).
					model(model.getModelFunction(), model.getModelFunctionJacobian()).
					build();
		}

	}

	/**
	 * Exponential function in the form of 1 - a * exp(-x / b).
	 */
	private static final class Exponential implements ParametricUnivariateFunction {

		@Override
		public double value(double x, double... parameters) {
			return 1 - parameters[0] * Math.exp(-x / parameters[1]);
		}

		@Override
		public double[] gradient(double x, double... parameters) {
			double exb = Math.exp(-x / parameters[1]);
			return new double[]{-exb, - parameters[0] * x * exb / (parameters[1] * parameters[1])};
		}
	}

}

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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.analysis.ParametricUnivariateFunction;
import org.apache.commons.math3.fitting.AbstractCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.random.BitsStreamGenerator;
import org.apache.commons.math3.util.FastMath;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.input.ActivityParticipation;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.policy.FixedPolicy;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Common utility class for episim.
 */
public final class EpisimUtils {

	private EpisimUtils() {
	}

	/**
	 * Gets the day of a week for a certain start day and current iteration.
	 * The {@link DayOfWeek} can be overwritten by the input config and does not necessarily match the reality.
	 */
	public static DayOfWeek getDayOfWeek(EpisimConfigGroup config, long iteration) {
		LocalDate date = config.getStartDate().plusDays(iteration - 1);

		// check if date was re-mapped
		if (config.getInputDays().containsKey(date))
			return config.getInputDays().get(date);

		return date.getDayOfWeek();
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
	 * String representation of arbitrary parameter.
	 */
	public static String asString(Object obj) {

		if (obj == null)
			return "null";

		if (obj instanceof Class)
			return ((Class) obj).getCanonicalName();

		//if (obj instanceof Double || obj instanceof Float) {
		//	return FMT.format(obj);
		//}

		return obj.toString();
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
	 * Find the current valid entry from a map of dates and values.
	 *
	 * @param map          map of values, assumed to be sorted by date
	 * @param defaultValue default value
	 * @param date         date to search for
	 * @return value from the map larger or equal to {@code date}
	 */
	public static <K extends Comparable, T> T findValidEntry(Map<K, T> map, T defaultValue, K date) {
		T result = defaultValue;
		for (Map.Entry<K, T> kv : map.entrySet()) {
			K key = kv.getKey();
			// key <= date
			if (key.compareTo(date) <= 0) {
				result = kv.getValue();
			}
		}
		return result;
	}

	/**
	 * Interpolate a value for the current day from a map of target dates and values.
	 * E.g. if there are the entries 01.01=1 and 10.01=10 then interpolation for
	 * 05.01 will be 5
	 *
	 * @param map  map of target values.
	 * @param date date to interpolate.
	 * @return interpolated values (or exact if entry is in map)
	 */
	public static double interpolateEntry(NavigableMap<LocalDate, ? extends Number> map, LocalDate date) {

		Map.Entry<LocalDate, ? extends Number> floor = map.floorEntry(date);

		if (floor == null)
			return map.firstEntry().getValue().doubleValue();

		if (floor.getKey().equals(date))
			return floor.getValue().doubleValue();

		Map.Entry<LocalDate, ? extends Number> ceil = map.ceilingEntry(date);

		// there is no higher entry to interpolate
		if (ceil == null)
			return floor.getValue().doubleValue();

		double between = ChronoUnit.DAYS.between(floor.getKey(), ceil.getKey());
		double diff = ChronoUnit.DAYS.between(floor.getKey(), date);
		return floor.getValue().doubleValue() + diff * (ceil.getValue().doubleValue() - floor.getValue().doubleValue()) / between;
	}

	/**
	 * Interpolate a value for specific key from a map of target keys and values.
	 *
	 * @param map map of target values.
	 * @param key key to interpolate.
	 * @return interpolated values (or exact if entry is in map)
	 * @see #interpolateEntry(NavigableMap, LocalDate)
	 */
	public static <T extends Number> double interpolateEntry(NavigableMap<T, ? extends Number> map, T key) {

		Map.Entry<T, ? extends Number> floor = map.floorEntry(key);

		if (floor == null)
			return map.firstEntry().getValue().doubleValue();

		if (floor.getKey().equals(key))
			return floor.getValue().doubleValue();

		Map.Entry<T, ? extends Number> ceil = map.ceilingEntry(key);

		// there is no higher entry to interpolate
		if (ceil == null)
			return floor.getValue().doubleValue();

		double between = floor.getKey().doubleValue() - ceil.getKey().doubleValue();
		double diff = floor.getKey().doubleValue() - key.doubleValue();
		return floor.getValue().doubleValue() + diff * (ceil.getValue().doubleValue() - floor.getValue().doubleValue()) / between;
	}

	/**
	 * Compress directory recursively.
	 */
	public static void compressDirectory(String rootDir, String sourceDir, String runId, ArchiveOutputStream out) throws IOException {
		File[] fileList = new File(sourceDir).listFiles();
		if (fileList == null) return;
		for (File file : fileList) {
			// Zip files (i.e. other snapshots or large files) are not added
			if (file.getName().endsWith(".zip") || file.getName().endsWith(".txt.gz"))
				continue;

			if (file.isDirectory()) {
				compressDirectory(rootDir, sourceDir + "/" + file.getName(), runId, out);
			} else {
				// Remove runId from the output name
				String name = file.getName().replace(runId + ".", "");
				ArchiveEntry entry = out.createArchiveEntry(file, "output" + sourceDir.replace(rootDir, "") + "/" + name);
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

	public static double nextLogNormalFromMeanAndSigma(SplittableRandom rnd, double mean, double sigma) {
		double mu = Math.log(mean) - sigma * sigma / 2;
		return nextLogNormal(rnd, mu, sigma);
	}

	/**
	 * Resolves an input path that can be configured with the environment variable EPISIM_INPUT.
	 *
	 * @param defaultPath default path if nothing else is set
	 * @return path to input directory
	 */
	public static Path resolveInputPath(String defaultPath) {
		String input = System.getenv("EPISIM_INPUT");
		return Path.of(input != null ? input : defaultPath).toAbsolutePath().normalize();
	}

	/**
	 * Read in restriction from csv by taking the average reduction of all not at home activities and apply them to all other activities.
	 *
	 * @param alpha modulate the amount reduction
	 * @deprecated use {@link CreateRestrictionsFromCSV}
	 */
	@Deprecated
	public static FixedPolicy.ConfigBuilder createRestrictionsFromCSV2(EpisimConfigGroup episimConfig, File input, double alpha,
																	   Extrapolation extrapolate) throws IOException {
		return new CreateRestrictionsFromCSV(episimConfig).setInput(input.toPath()).setAlpha(alpha).setExtrapolation(extrapolate).createPolicy();
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
	 * Read a csv with date column into a map.
	 */
	public static NavigableMap<LocalDate, Double> readCSV(Path csv, CSVFormat format, String dateColumn, String valueColumn) {

		TreeMap<LocalDate, Double> map = new TreeMap<>();

		try (BufferedReader in = Files.newBufferedReader(csv)) {

			Iterable<CSVRecord> records = format.withFirstRecordAsHeader().withCommentMarker('#').parse(in);

			for (CSVRecord r : records) {

				LocalDate date = LocalDate.parse(r.get(dateColumn));
				map.put(date, Double.parseDouble(r.get(valueColumn)));
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}


		return map;
	}

	public static void setRestrictions(FixedPolicy.ConfigBuilder builder, String act, NavigableMap<LocalDate, Double> data, Extrapolation extrapolation) {

		builder.clearAfter(LocalDate.MIN.toString(), act);

		List<Double> trend = new ArrayList<>();

		AtomicReference<LocalDate> start = new AtomicReference<>(LocalDate.parse("2020-02-28"));

		ActivityParticipation.resampleAvgWeekday(data, start.get(), (date, avg) -> {
			trend.add(avg);
			start.set(date);
			builder.restrict(date, avg, act);
		});

		List<Double> recentTrend = trend.subList(Math.max(0, trend.size() - 8), trend.size());
		start.set(start.get().plusDays(7));

		for (Double predict : ActivityParticipation.extrapolate(recentTrend, 25, extrapolation)) {
			builder.restrict(start.get(), Math.min(predict, 1), act);
			start.set(start.get().plusDays(7));
		}

	}

	public static Map<LocalDate, Double> getOutdoorFractionsFromWeatherData(File weatherCSV, double rainThreshold,
																			Double temperatureIn, Double temperatureOut) throws IOException {
		if ((temperatureIn == null && temperatureOut != null) || (temperatureIn != null && temperatureOut == null)) {
			throw new RuntimeException("one temperature is null, the other one is given; don't know how to interpret that; aborting ...");
		}
		if (temperatureIn == null) {
			temperatureIn = 1.5;
			temperatureOut = 30.5;
			// should correspond to 0.0344 * tMax - 0.0518, which is what I found.  kai, nov'20
		}


		Reader in = new FileReader(weatherCSV);
		Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().withCommentMarker('#').parse(in);
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		final Map<LocalDate, Double> outdoorFractions = new TreeMap<>();
		LocalDate date = null;
		for (CSVRecord record : records) {
			date = LocalDate.parse(record.get("date"), fmt);
			if (record.get("tmax").isEmpty() || record.get("prcp").isEmpty()) {
//				System.out.println("Skipping day because tmax or prcp data is not available. Date: " + date.toString());
				continue;
			}

			double tMax = Double.parseDouble(record.get("tmax"));
			double prcp = Double.parseDouble(record.get("prcp"));

			double outDoorFraction = (tMax - temperatureIn) / (temperatureOut - temperatureIn);

			if (prcp > rainThreshold) {
				outDoorFraction = outDoorFraction * 0.5;
			}

			if (outDoorFraction > 1.) {
				outDoorFraction = 1.;
//				System.out.println("outDoorFraction is > 1. Setting to 1. Date: " + date.toString());
			}
			if (outDoorFraction < 0.) {
				outDoorFraction = 0.;
//				System.out.println("outDoorFraction is < 1. Setting to 0. Date: " + date.toString());
			}
			outdoorFractions.put(date, outDoorFraction);
		}
		return outdoorFractions;
	}

	public static Map<LocalDate, Double> getOutdoorFractions2(File weatherCSV, File avgWeatherCSV, double rainThreshold, Double TmidSpring, Double TmidFall, Double Trange) throws IOException {

		Reader in = new FileReader(weatherCSV);
		Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().withCommentMarker('#').parse(in);
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		LocalDate lastDate = null;
		final Map<LocalDate, Double> outdoorFractions = new TreeMap<>();
		for (CSVRecord record : records) {
//			System.out.println( record );
			LocalDate date = LocalDate.parse(record.get("date"), fmt);
			if (record.get("tmax").isEmpty() || record.get("prcp").isEmpty()) {
//				System.out.println("Skipping day because tmax or prcp data is not available. Date: " + date.toString());
				continue;
			}

			double tMax = Double.parseDouble(record.get("tmax"));
			double prcp = Double.parseDouble(record.get("prcp"));

			outdoorFractions.put(date, getOutDoorFraction(date, TmidSpring, TmidFall, Trange, tMax, prcp, rainThreshold));
			lastDate = date;
		}

		in = new FileReader(avgWeatherCSV);
		records = CSVFormat.DEFAULT.withFirstRecordAsHeader().withCommentMarker('#').parse(in);
		HashMap<String, Double> tmaxPerDay = new HashMap<String, Double>();
		HashMap<String, Double> prcpPerDay = new HashMap<String, Double>();

		for (CSVRecord record : records) {
			String monthDay = record.get("monthDay");
			double tMax = Double.parseDouble(record.get("tmax"));
			double prcp = Double.parseDouble(record.get("prcp"));
			tmaxPerDay.put(monthDay, tMax);
			prcpPerDay.put(monthDay, prcp);
		}

		for (int i = 1; i < 365*3; i++) {
			LocalDate date = lastDate.plusDays(i);
			int month = date.getMonth().getValue();
			int day = date.getDayOfMonth();
			String monthDay = month + "-" + day;
			double tMax = tmaxPerDay.get(monthDay);
			double prcp = prcpPerDay.get(monthDay);
			outdoorFractions.put(date, getOutDoorFraction(date, TmidSpring, TmidFall, Trange, tMax, prcp, rainThreshold));
		}

//		System.exit(-1);
		return outdoorFractions;
	}

	private static double getOutDoorFraction(LocalDate date, Double TmidSpring, Double TmidFall, Double Trange, double tMax, double prcp, double rainThreshold) {

		double tMid;
		int date1 = 152; //01.06.
		int date2 = 213; //01.08.
		if (date.isLeapYear()) {
			date1++;
			date2++;
		}
//		final LocalDate date3 = LocalDate.of( 2020, 12, 31 );
		if (date.getDayOfYear() < date1) {
			tMid = TmidSpring;
		} else if (date.getDayOfYear() > date2) {
			tMid = TmidFall;
		} else {
			double fraction = 1. * (date.getDayOfYear() - date1) / (date2 - date1);
			tMid = (1. - fraction) * TmidSpring + fraction * TmidFall;
		}
		double tAllIn = tMid - Trange;
		double tAllOut = tMid + Trange;

		double outDoorFraction = (tMax - tAllIn) / (tAllOut - tAllIn);

		if (prcp > rainThreshold) outDoorFraction = outDoorFraction * 0.5;
		if (outDoorFraction > 1.) outDoorFraction = 1.;
		if (outDoorFraction < 0.) outDoorFraction = 0.;

//		System.out.println( date + "; tMid=" + tMid + "; tMax=" + tMax + "; outDoorFraction=" + outDoorFraction ) ;


		return outDoorFraction;
	}
}

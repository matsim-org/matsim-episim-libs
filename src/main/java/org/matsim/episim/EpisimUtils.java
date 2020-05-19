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
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.random.BitsStreamGenerator;
import org.apache.commons.math3.util.FastMath;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.SplittableRandom;

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
	 * Draw a gaussian distributed random number (mean=0, var=1)
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
	 * @param rnd splittable random instance
	 * @param mu mu ( median exp mu)
	 * @param sigma sigma
	 */
	public static double nextLogNormal(SplittableRandom rnd, double mu, double sigma) {
		if (sigma == 0)
			return Math.exp(mu);

		return Math.exp(sigma * nextGaussian(rnd) + mu);
	}

}

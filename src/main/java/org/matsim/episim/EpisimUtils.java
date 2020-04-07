package org.matsim.episim;

public class EpisimUtils {

	/**
	 * Calculates the time based on the current iteration.
	 *
	 * @param time time relative to start of day
	 */
	public static double getCorrectedTime(double time, long iteration) {
		return Math.min(time, 3600. * 24) + iteration * 24. * 3600;
	}

}

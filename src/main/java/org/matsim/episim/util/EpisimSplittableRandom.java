/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.matsim.episim.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A copy of the JDK {@link java.util.SplittableRandom} algorithm that exposes
 * its current seed for deterministic snapshot persistence.
 *
 * <p>Like {@code SplittableRandom}, this class is not thread-safe.</p>
 */
public final class EpisimSplittableRandom {

	private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;
	private static final double DOUBLE_UNIT = 0x1.0p-53;
	private static final String BAD_BOUND = "bound must be positive";
	private static final String BAD_RANGE = "bound must be greater than origin";
	private static final AtomicLong DEFAULT_GEN = new AtomicLong(initialSeed());

	/**
	 * The seed. Updated only via method nextSeed.
	 */
	private long seed;

	/**
	 * The step value.
	 */
	private final long gamma;

	/**
	 * Creates a generator with the same sequence as a JDK
	 * {@link java.util.SplittableRandom} created with the same seed.
	 */
	public EpisimSplittableRandom(long seed) {
		this(seed, GOLDEN_GAMMA);
	}

	/**
	 * Creates a generator with a process-local automatically generated seed.
	 */
	public EpisimSplittableRandom() {
		long s = DEFAULT_GEN.getAndAdd(2 * GOLDEN_GAMMA);
		this.seed = mix64(s);
		this.gamma = mixGamma(s + GOLDEN_GAMMA);
	}

	/**
	 * Internal constructor used by all others except default constructor.
	 */
	private EpisimSplittableRandom(long seed, long gamma) {
		this.seed = seed;
		this.gamma = gamma;
	}

	/**
	 * Computes Stafford variant 13 of 64bit mix function.
	 * http://zimbry.blogspot.com/2011/09/better-bit-mixing-improving-on.html
	 */
	private static long mix64(long z) {
		z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
		z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
		return z ^ (z >>> 31);
	}

	/**
	 * Returns the 32 high bits of Stafford variant 4 mix64 function as int.
	 * http://zimbry.blogspot.com/2011/09/better-bit-mixing-improving-on.html
	 */
	private static int mix32(long z) {
		z = (z ^ (z >>> 33)) * 0x62a9d9ed799705f5L;
		return (int)(((z ^ (z >>> 28)) * 0xcb24d0a5c88c35b3L) >>> 32);
	}

	/**
	 * Returns the gamma value to use for a new split instance.
	 * Uses the 64bit mix function from MurmurHash3.
	 * https://github.com/aappleby/smhasher/wiki/MurmurHash3
	 */
	private static long mixGamma(long z) {
		z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL; // MurmurHash3 mix constants
		z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
		z = (z ^ (z >>> 33)) | 1L;                  // force to be odd
		int n = Long.bitCount(z ^ (z >>> 1));       // ensure enough transitions
		return (n < 24) ? z ^ 0xaaaaaaaaaaaaaaaaL : z;
	}

	private static long initialSeed() {
		return mix64(System.currentTimeMillis()) ^ mix64(System.nanoTime());
	}

	private long nextSeed() {
		return seed += gamma;
	}

	private long internalNextLong(long origin, long bound) {
		long r = mix64(nextSeed());
		if (origin < bound) {
			long n = bound - origin;
			long m = n - 1;
			if ((n & m) == 0L) {
				r = (r & m) + origin;
			} else if (n > 0L) {
				for (long u = r >>> 1; u + m - (r = u % n) < 0L; u = mix64(nextSeed()) >>> 1) {
					// Reject over-represented candidates.
				}
				r += origin;
			} else {
				while (r < origin || r >= bound) {
					r = mix64(nextSeed());
				}
			}
		}
		return r;
	}

	private int internalNextInt(int origin, int bound) {
		int r = mix32(nextSeed());
		if (origin < bound) {
			int n = bound - origin;
			int m = n - 1;
			if ((n & m) == 0) {
				r = (r & m) + origin;
			} else if (n > 0) {
				for (int u = r >>> 1; u + m - (r = u % n) < 0; u = mix32(nextSeed()) >>> 1) {
					// Reject over-represented candidates.
				}
				r += origin;
			} else {
				while (r < origin || r >= bound) {
					r = mix32(nextSeed());
				}
			}
		}
		return r;
	}

	private double internalNextDouble(double origin, double bound) {
		double r = (nextLong() >>> 11) * DOUBLE_UNIT;
		if (origin < bound) {
			r = r * (bound - origin) + origin;
			if (r >= bound) {
				r = Double.longBitsToDouble(Double.doubleToLongBits(bound) - 1);
			}
		}
		return r;
	}

	/**
	 * Returns the current internal seed. It can be persisted and later supplied
	 * to {@link #setSeed(long)} to continue the exact same sequence.
	 */
	public long getSeed() {
		return seed;
	}

	/**
	 * Restores the current internal seed without changing this generator's
	 * gamma value.
	 */
	public void setSeed(long seed) {
		this.seed = seed;
	}

	/**
	 * Creates an independent generator using the same splitting algorithm as
	 * {@link java.util.SplittableRandom#split()}.
	 */
	public EpisimSplittableRandom split() {
		return new EpisimSplittableRandom(nextLong(), mixGamma(nextSeed()));
	}

	/**
	 * Returns a pseudorandom {@code int} value.
	 */
	public int nextInt() {
		return mix32(nextSeed());
	}

	/**
	 * Returns a pseudorandom {@code int} between zero (inclusive) and the bound
	 * (exclusive).
	 */
	public int nextInt(int bound) {
		if (bound <= 0) {
			throw new IllegalArgumentException(BAD_BOUND);
		}

		int r = mix32(nextSeed());
		int m = bound - 1;
		if ((bound & m) == 0) {
			r &= m;
		} else {
			for (int u = r >>> 1; u + m - (r = u % bound) < 0; u = mix32(nextSeed()) >>> 1) {
				// Reject over-represented candidates.
			}
		}
		return r;
	}

	/**
	 * Returns a pseudorandom {@code int} between the origin (inclusive) and the
	 * bound (exclusive).
	 */
	public int nextInt(int origin, int bound) {
		if (origin >= bound) {
			throw new IllegalArgumentException(BAD_RANGE);
		}
		return internalNextInt(origin, bound);
	}

	/**
	 * Returns a pseudorandom {@code long} value.
	 */
	public long nextLong() {
		return mix64(nextSeed());
	}

	/**
	 * Returns a pseudorandom {@code long} between zero (inclusive) and the bound
	 * (exclusive).
	 */
	public long nextLong(long bound) {
		if (bound <= 0) {
			throw new IllegalArgumentException(BAD_BOUND);
		}

		long r = mix64(nextSeed());
		long m = bound - 1;
		if ((bound & m) == 0L) {
			r &= m;
		} else {
			for (long u = r >>> 1; u + m - (r = u % bound) < 0L; u = mix64(nextSeed()) >>> 1) {
				// Reject over-represented candidates.
			}
		}
		return r;
	}

	/**
	 * Returns a pseudorandom {@code long} between the origin (inclusive) and the
	 * bound (exclusive).
	 */
	public long nextLong(long origin, long bound) {
		if (origin >= bound) {
			throw new IllegalArgumentException(BAD_RANGE);
		}
		return internalNextLong(origin, bound);
	}

	/**
	 * Returns a pseudorandom {@code double} between zero (inclusive) and one
	 * (exclusive).
	 */
	public double nextDouble() {
		return (mix64(nextSeed()) >>> 11) * DOUBLE_UNIT;
	}

	/**
	 * Returns a pseudorandom {@code double} between zero (inclusive) and the
	 * bound (exclusive).
	 */
	public double nextDouble(double bound) {
		if (!(bound > 0.0)) {
			throw new IllegalArgumentException(BAD_BOUND);
		}

		double result = (mix64(nextSeed()) >>> 11) * DOUBLE_UNIT * bound;
		return result < bound
				? result
				: Double.longBitsToDouble(Double.doubleToLongBits(bound) - 1);
	}

	/**
	 * Returns a pseudorandom {@code double} between the origin (inclusive) and
	 * the bound (exclusive).
	 */
	public double nextDouble(double origin, double bound) {
		if (!(origin < bound)) {
			throw new IllegalArgumentException(BAD_RANGE);
		}
		return internalNextDouble(origin, bound);
	}

	/**
	 * Returns a pseudorandom boolean value.
	 */
	public boolean nextBoolean() {
		return mix32(nextSeed()) < 0;
	}

}

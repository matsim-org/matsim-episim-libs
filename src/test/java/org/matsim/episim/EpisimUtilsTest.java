package org.matsim.episim;

import org.junit.Test;

import java.io.*;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class EpisimUtilsTest {

	@Test
	public void seed() {

		SplittableRandom rnd = new SplittableRandom(1000);

		assertThat(EpisimUtils.getSeed(rnd))
				.isEqualTo(1000);

		long n = rnd.nextLong();

		assertThat(EpisimUtils.getSeed(rnd))
				.isNotEqualTo(1000);

		EpisimUtils.setSeed(rnd, 1000);
		assertThat(rnd.nextLong())
				.isEqualTo(n);

	}

	@Test
	public void chars() throws IOException {

		ByteArrayOutputStream out = new ByteArrayOutputStream(10000);

		DataOutputStream dout = new DataOutputStream(out);

		EpisimUtils.writeChars(dout, "home");
		EpisimUtils.writeChars(dout, "act");
		EpisimUtils.writeChars(dout, "");

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

		DataInputStream din = new DataInputStream(in);

		assertThat(EpisimUtils.readChars(din))
				.isEqualTo("home");

		assertThat(EpisimUtils.readChars(din))
				.isEqualTo("act");

		assertThat(EpisimUtils.readChars(din))
				.isEqualTo("");
	}
}

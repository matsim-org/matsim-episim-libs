package org.matsim.episim;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.util.FastMath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.data.Percentage;
import org.junit.Assume;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SplittableRandom;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

public class EpisimUtilsTest {
	private static final Logger log = LogManager.getLogger(EpisimUtilsTest.class);

	private static final Percentage OFFSET = Percentage.withPercentage(1.5);

	@Test
	public void dayOfWeek() {

		Config testConfig = EpisimTestUtils.createTestConfig();
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(testConfig, EpisimConfigGroup.class);

		episimConfig.setStartDate(LocalDate.of(2020, 7, 1));

		assertThat(EpisimUtils.getDayOfWeek(episimConfig, 1)).isEqualTo(DayOfWeek.WEDNESDAY);
		assertThat(EpisimUtils.getDayOfWeek(episimConfig, 5)).isEqualTo(DayOfWeek.SUNDAY);
		assertThat(EpisimUtils.getDayOfWeek(episimConfig, 6)).isEqualTo(DayOfWeek.MONDAY);

		// set 6th and 7th july as sunday
		episimConfig.setInputDays(Map.of(
				LocalDate.of(2020, 7, 6), DayOfWeek.SUNDAY,
				LocalDate.of(2020, 7, 7), DayOfWeek.SUNDAY
		));

		assertThat(EpisimUtils.getDayOfWeek(episimConfig, 6)).isEqualTo(DayOfWeek.SUNDAY);
		assertThat(EpisimUtils.getDayOfWeek(episimConfig, 7)).isEqualTo(DayOfWeek.SUNDAY);
	}

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

	@Test
	public void nextLogNormal() {

		SplittableRandom rnd = new SplittableRandom(1);

		double mu = 5;
		double sigma = 1.5;
		double ssigma = 1.5 * 1.5;

		double[] values = new double[2_000_000];
		for (int i = 0; i < values.length; i++) {
			values[i] = EpisimUtils.nextLogNormal(rnd, mu, sigma);
		}

		double median = new Median().evaluate(values);
		double mean = new Mean().evaluate(values);
		double std = new StandardDeviation().evaluate(values);

		// https://en.wikipedia.org/wiki/Log-normal_distribution

		assertThat(median).isCloseTo(FastMath.exp(mu), OFFSET);
		assertThat(mean).isCloseTo(FastMath.exp(mu + ssigma / 2.0), OFFSET);
		assertThat(std * std).isCloseTo((FastMath.exp(ssigma) - 1) * FastMath.exp(2 * mu + ssigma), OFFSET);

	}

	@Test
	public void testCreateRestrictionsFromCSV() throws IOException {

		EpisimConfigGroup episimConfig = new EpisimConfigGroup();

		SnzBerlinScenario25pct2020.addParams(episimConfig);

		File f = new File("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/BerlinSnzData_daily_until20200531.csv");

		Assume.assumeTrue("Input must exist", f.exists());

		CreateRestrictionsFromCSV act = new CreateRestrictionsFromCSV(episimConfig);
		act.setInput(f.toPath());
		act.setAlpha(1.0);

		FixedPolicy.ConfigBuilder config = act.createPolicy();

		FixedPolicy.ConfigBuilder config2 = EpisimUtils.createRestrictionsFromCSV2(episimConfig, f, 1.4, EpisimUtils.Extrapolation.linear);
		FixedPolicy.ConfigBuilder config3 = EpisimUtils.createRestrictionsFromCSV2(episimConfig, f, 1.4, EpisimUtils.Extrapolation.exponential);

		//FileUtils.write(new File("out.json"), config.build().root().render(ConfigRenderOptions.defaults().setJson(true).setComments(false).setOriginComments(false)));
	}


	@Test
	public void setRestrictions() {

		Path f = Path.of("berlinGoogleMobility.csv");

		Assume.assumeTrue("Input must exists", Files.exists(f));

		NavigableMap<LocalDate, Double> data = EpisimUtils.readCSV(f, CSVFormat.DEFAULT, "date", "workplaces_percent_change_from_baseline");

		// rescale remaining fraction
		data.replaceAll((k, v) -> Math.min(1, 1 + (v / 100)));

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();

		EpisimUtils.setRestrictions(builder, "work", data, EpisimUtils.Extrapolation.linear);

		builder.build();

	}

	@Test
	public void interpolateEntry() {

		TreeMap<LocalDate, Double> map = new TreeMap<>(Map.of(
				LocalDate.of(2020, 1, 1), 1d,
				LocalDate.of(2020, 1, 10), 10d,
				LocalDate.of(2020, 1, 30), 10d
		));

		assertThat(EpisimUtils.interpolateEntry(map, LocalDate.of(2020, 1, 2)))
				.isEqualTo(2d);

		assertThat(EpisimUtils.interpolateEntry(map, LocalDate.of(2020, 1, 5)))
				.isEqualTo(5d);

		assertThat(EpisimUtils.interpolateEntry(map, LocalDate.of(2020, 1, 10)))
				.isEqualTo(10d);

		assertThat(EpisimUtils.interpolateEntry(map, LocalDate.of(2020, 2, 23)))
				.isEqualTo(10d);

	}

	@Test
	public void interpolateNumber() {

		TreeMap<Integer, Double> map = new TreeMap<>(Map.of(
				5, 0.5d,
				20, 20d,
				30, 30d
		));

		assertThat(EpisimUtils.interpolateEntry(map, 1))
				.isEqualTo(0.5);

		assertThat(EpisimUtils.interpolateEntry(map, 10))
				.isEqualTo(7);

		assertThat(EpisimUtils.interpolateEntry(map, 25))
				.isEqualTo(25);

		assertThat(EpisimUtils.interpolateEntry(map, 35))
				.isEqualTo(30);

	}
}

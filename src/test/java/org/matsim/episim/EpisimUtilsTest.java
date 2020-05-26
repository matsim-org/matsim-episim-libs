package org.matsim.episim;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.util.FastMath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.data.Percentage;
import org.junit.Assume;
import org.junit.Test;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import java.io.File;
import java.io.IOException;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class EpisimUtilsTest {
	private static final Logger log = LogManager.getLogger(EpisimUtilsTest.class);

	private static final Percentage OFFSET = Percentage.withPercentage(1.5);

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

		File f = new File("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/BerlinSnzData_daily_until20200517.csv");

		Assume.assumeTrue("Input must exist", f.exists());

		FixedPolicy.ConfigBuilder config = EpisimUtils.createRestrictionsFromCSV(
				episimConfig,
				f,
				1.0
		);


		FixedPolicy.ConfigBuilder config2 = EpisimUtils.createRestrictionsFromCSV2(episimConfig, f, 1.0);

		//FileUtils.write(new File("out.json"), config.build().root().render(ConfigRenderOptions.defaults().setJson(true).setComments(false).setOriginComments(false)));
	}


}

package org.matsim.episim;

import com.typesafe.config.Config;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.util.FastMath;
import org.apache.log4j.Logger;
import org.assertj.core.data.Percentage;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import java.io.IOException;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class EpisimUtilsTest {
	private static final Logger log = Logger.getLogger( EpisimUtilsTest.class );

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
	public void testCreateRestrictionsFromCSV() throws IOException{

		EpisimConfigGroup episimConfig = new EpisimConfigGroup();

		SnzBerlinScenario25pct2020.addParams(episimConfig );

//		FixedPolicy.ConfigBuilder result = EpisimUtils.createRestrictionsFromCSV( episimConfig, alpha, LocalDate.of( 2020, 3, 1 ), 1. );
//		Config result2 = result.build();

//		log.info( result2.getConfig( "work" ) );
//		log.info( result2.getConfig( "work" ).getConfig( "2020-03-22" ) );
//		log.info( result2.getConfig( "work" ).getConfig( "2020-03-22" ).getDouble( "fraction" ) );
//		Assert.assertEquals( 0.6575, result2.getConfig( "work" ).getConfig( "2020-03-22" ).getDouble( "fraction" ), Double.MIN_VALUE );
//
//		log.info( result2.getConfig( "leisure" ).getConfig( "2020-04-03" ).getDouble( "fraction" ) );
//		Assert.assertEquals( 0.11538461538461539, result2.getConfig( "leisure" ).getConfig( "2020-04-03" ).getDouble( "fraction" ), Double.MIN_VALUE );
		throw new RuntimeException("not implemented");

	}
}

package org.matsim.episim.policy;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.assertj.core.data.Offset;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class FixedPolicyTest {

	private static final Offset<Double> OFFSET = Offset.offset(0.0001);

	private ImmutableMap<String, Restriction> r;

	@Before
	public void setUp() {
		r = ImmutableMap.of(
				"home", Restriction.none(),
				"work", Restriction.none(),
				"pt", Restriction.none()
		);
	}

	@Test
	public void fixedDays() {

		FixedPolicy.ConfigBuilder config = FixedPolicy.config()
				.restrict(2, 0.3, "home");

		FixedPolicy policy = new FixedPolicy(config.build());

		policy.updateRestrictions(EpisimTestUtils.createReport("--", 2), r);

		assertThat(r.get("home").getRemainingFraction()).isEqualTo(0.3);

	}

	@Test
	public void merge() {

		FixedPolicy.ConfigBuilder config = FixedPolicy.config()
				.restrict(1, Restriction.of(0.9), "home", "work")
				.restrict(1, Restriction.ofMask(FaceMask.CLOTH, 1.0), "work")
				.restrict(2, Restriction.ofCiCorrection(0.5), "home", "work")
				.restrict(2, Restriction.of(0.4), "work");

		FixedPolicy policy = new FixedPolicy(config.build());

		policy.updateRestrictions(EpisimTestUtils.createReport("--", 1), r);

		assertThat(r.get("home").getRemainingFraction()).isEqualTo(0.9);
		assertThat(r.get("home").getCiCorrection()).isEqualTo(1);

		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.9);
		assertThat(r.get("work").getCiCorrection()).isEqualTo(1);
		assertThat(r.get("work").determineMask(new SplittableRandom(1))).isEqualTo(FaceMask.CLOTH);

		policy.updateRestrictions(EpisimTestUtils.createReport("--", 2), r);

		assertThat(r.get("home").getRemainingFraction()).isEqualTo(0.9);
		assertThat(r.get("home").getCiCorrection()).isEqualTo(0.5);

		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.4);
		assertThat(r.get("work").getCiCorrection()).isEqualTo(0.5);

	}

	@Test
	public void interpolate() {

		FixedPolicy.ConfigBuilder config = FixedPolicy.config()
				.interpolate(
						LocalDate.of(2020, 3, 20),
						LocalDate.of(2020, 3, 30),
						Restriction.of(0.95), Restriction.of(0.45),
						"work"
				)
				.interpolate(
						LocalDate.of(2020, 3, 31),
						LocalDate.of(2020, 4, 10),
						Restriction.of(0.5, 1.0, Map.of(FaceMask.CLOTH, 1.0)),
						Restriction.of(1d, 0.5, Map.of(FaceMask.CLOTH, 1.0)),
						"work"
				);

		FixedPolicy policy = new FixedPolicy(config.build());

		policy.updateRestrictions(EpisimTestUtils.createReport("2020-03-20", -1), r);
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.95);

		// mid of first interval
		policy.updateRestrictions(EpisimTestUtils.createReport("2020-03-25", -1), r);
		assertThat(r.get("work").getRemainingFraction()).isCloseTo(0.95 - (0.95 - 0.45) / 2, OFFSET);

		policy.updateRestrictions(EpisimTestUtils.createReport("2020-03-30", -1), r);
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.45);

		policy.updateRestrictions(EpisimTestUtils.createReport("2020-03-31", -1), r);
		assertThat(r.get("work").getCiCorrection()).isEqualTo(1);
		assertThat(r.get("work").determineMask(new SplittableRandom(1))).isEqualTo(FaceMask.CLOTH);

		policy.updateRestrictions(EpisimTestUtils.createReport("2020-04-10", -1), r);
		assertThat(r.get("work").getRemainingFraction()).isCloseTo(1, OFFSET);
		assertThat(r.get("work").getCiCorrection()).isCloseTo(0.5, OFFSET);


	}

	@Test
	public void builder() {

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config()
				.restrict(1, Restriction.of(0.9), "home", "work")
				.restrict(1, Restriction.ofMask(FaceMask.CLOTH, 1.0), "work")
				.restrict(2, Restriction.ofCiCorrection(0.5), "home", "work")
				.restrict(2, Restriction.of(0.4), "work");

		assertThat(
				FixedPolicy.parse(builder.build()).restrict(2, Restriction.of(0.7), "work").build()
		)
				.isEqualTo(
						builder.restrict(2, Restriction.of(0.7), "work").build()
				);
	}


	@Test
	public void config() throws IOException {

		File f = new File("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/BerlinSnzData_daily_until20200524.csv");

		Assume.assumeTrue("Input must exist", f.exists());

//		String content = SnzBerlinScenario25pct2020.basePolicy(ConfigUtils.addOrGetModule(EpisimTestUtils.createTestConfig(), EpisimConfigGroup.class),
//				f, 1.0, 1.0, "2020-03-10", EpisimUtils.Extrapolation.linear


		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(EpisimTestUtils.createTestConfig(), EpisimConfigGroup.class);
		SnzBerlinScenario25pct2020.BasePolicyBuilder builder = new SnzBerlinScenario25pct2020.BasePolicyBuilder(episimConfig);

		CreateRestrictionsFromCSV act = new CreateRestrictionsFromCSV(episimConfig);
		act.setAlpha(1.);
		act.setInput(f.toPath());
		builder.setActivityParticipation(act);

		builder.setCiCorrections(Map.of("2020-03-10", 1.));

		String content = builder.build().build().root().render();

		Config config = ConfigFactory.parseString(content);
		LocalDate start = LocalDate.parse("2020-03-05");

		FixedPolicy p = new FixedPolicy(config);
		for (int i = 0; i < 200; i++) {
			EpisimReporting.InfectionReport report = EpisimTestUtils.createReport(start.plusDays(i).toString(), i);
			p.updateRestrictions(report, r);
		}
	}

	@Test
	public void locationBasedRestrictions() {
		Map<String, Double> nycBoroughs = new HashMap<>();
		nycBoroughs.put("Bronx", 0.7);
		nycBoroughs.put("Queens", 0.8);

		FixedPolicy.ConfigBuilder config1 = FixedPolicy.config()
				.restrict(2, Restriction.of(0.9), "work")
				.restrict(2, Restriction.ofLocationBasedRf(nycBoroughs), "work");

		config1.build();

		FixedPolicy.ConfigBuilder config2 = FixedPolicy.config()
				.restrictWithDistrict(2, nycBoroughs, 0.9, "work");


		assertThat(config1.build()).isEqualTo(config2.build());

		config1.restrict(1, 0.4, "work");
		FixedPolicy policy = new FixedPolicy(config1.build());
		policy.updateRestrictions(EpisimTestUtils.createReport("--", 1), r);
		assertThat(r.get("work").getLocationBasedRf().size()).isEqualTo(0);
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.4);
		policy.updateRestrictions(EpisimTestUtils.createReport("--", 2), r);
		assertThat(r.get("work").getLocationBasedRf().size()).isEqualTo(2);
		assertThat(r.get("work").getLocationBasedRf().get("Bronx")).isEqualTo(0.7);
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.9);
		assertThat(r.get("home").getLocationBasedRf().size()).isEqualTo(0);

	}


	@Test
	public void hospitalReg() {

		FixedPolicy.ConfigBuilder config = FixedPolicy.config()
				.restrict(1, Restriction.of(0.95), "work")
				.restrict(2, Restriction.of(ShutdownPolicy.REG_HOSPITAL), "work")
				.restrict(3, Restriction.of(ShutdownPolicy.REG_HOSPITAL), "work");


		FixedPolicy policy = new FixedPolicy(config.build());
		LocalDate now = LocalDate.now();
		policy.init(now, r);


		double frac = 1;
		for (int i = 1; i < 10; i++) {

			EpisimReporting.InfectionReport report = EpisimTestUtils.createReportHospital(now.plusDays(i), i, (i-1) * 300);
			policy.updateRestrictions(report, r);

			assertThat(r.get("work").getRemainingFraction())
					.isLessThan(frac);

			frac = r.get("work").getRemainingFraction();
		}

		assertThat(frac).isLessThan(0.6);

	}
}

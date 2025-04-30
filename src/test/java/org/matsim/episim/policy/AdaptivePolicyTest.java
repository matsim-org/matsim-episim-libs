package org.matsim.episim.policy;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import org.junit.Before;
import org.junit.Test;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.EpisimTestUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AdaptivePolicyTest {

	private ImmutableMap<String, Restriction> r;

	@Before
	public void setUp() {
		r = ImmutableMap.of(
				"home", Restriction.none(),
				"work", Restriction.none(),
				"edu", Restriction.none(),
				"pt", Restriction.none()
		);
	}

	// PART 1 : GLOBAL RESTRICTIONS

	@Test
	public void changeRestrictionWithDate() {

		Config config = AdaptivePolicy.config()
				.startDate("2030-12-31")
				.incidenceTrigger(35, 50, "work")
				.initialPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.of(0.111), "work")
						.restrict(LocalDate.parse("2020-01-29"), Restriction.of(0.666), "work")
						.restrict(LocalDate.parse("2020-01-09"), Restriction.of(0.222), "work")
						.restrict(LocalDate.parse("2020-01-19"), Restriction.of(0.444), "work")

				)
				.restrictedPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.of(0.3), "work")
				)
				.openPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.of(0.8), "work")
				)
				.build();

		AdaptivePolicy policy = new AdaptivePolicy(config);
		LocalDate startingDate = LocalDate.parse("2020-01-01");
		LocalDate localDate = null;
		int day = 0;
		int showingSymptoms = 0;

		policy.init(startingDate, r);

		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.111);

		showingSymptoms = 0;
		for (; day < 9; day++) {
			localDate = startingDate.plusDays(day);
			policy.updateRestrictions(EpisimTestUtils.createReport(localDate, day, showingSymptoms), r);
		}

		//01-09
		assertThat(localDate).isEqualTo("2020-01-09");
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.111);

		//01-10
		localDate = startingDate.plusDays(day);
		policy.updateRestrictions(EpisimTestUtils.createReport(localDate, day, showingSymptoms), r);

		assertThat(localDate).isEqualTo("2020-01-10");
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.222);

		for (; day < 19; day++) {
			localDate = startingDate.plusDays(day);
			policy.updateRestrictions(EpisimTestUtils.createReport(localDate, day, showingSymptoms), r);
		}
		//01-19
		assertThat(localDate).isEqualTo("2020-01-19");
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.222);

		//01-20
		localDate = startingDate.plusDays(day);
		policy.updateRestrictions(EpisimTestUtils.createReport(localDate, day, showingSymptoms), r);

		assertThat(localDate).isEqualTo("2020-01-20");
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.444);

		for (; day < 29; day++) {
			localDate = startingDate.plusDays(day);
			policy.updateRestrictions(EpisimTestUtils.createReport(localDate, day, showingSymptoms), r);
		}
		//01-29
		assertThat(localDate).isEqualTo("2020-01-29");
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.444);

		//01-30
		localDate = startingDate.plusDays(day);
		policy.updateRestrictions(EpisimTestUtils.createReport(localDate, day, showingSymptoms), r);

		assertThat(localDate).isEqualTo("2020-01-30");
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.666);
	}
	@Test
	public void policy() {

		Config config = AdaptivePolicy.config()
				.incidenceTrigger(35, 50, "work")
				.incidenceTrigger(50, 50, "edu")
				.initialPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.of(0.9), "work")
				)
				.restrictedPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.of(0.2), "work")
						.restrict(LocalDate.MIN, Restriction.of(0.0), "edu")
				)
				.openPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.of(0.8), "work")
						.restrict(LocalDate.MIN, Restriction.of(1.0), "edu")
				)
				.build();

		AdaptivePolicy policy = new AdaptivePolicy(config);
		LocalDate date = LocalDate.now();
		int day = 0;
		int showingSymptoms = 0;

		policy.init(date, r);

		assertThat(r.get("home").getRemainingFraction()).isEqualTo(1.0);
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.9);

		for (; day < 8; day++) {
			showingSymptoms += 60 / 7;
			policy.updateRestrictions(EpisimTestUtils.createReport(date.plusDays(day), day, showingSymptoms), r);
		}

		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.2);
		assertThat(r.get("edu").getRemainingFraction()).isEqualTo(0.0);

		for (; day < 30; day++) {
			showingSymptoms += 23 / 7;
			policy.updateRestrictions(EpisimTestUtils.createReport(date.plusDays(day), day, showingSymptoms), r);
		}

		// open after 14 days of lockdown
		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.8);
		assertThat(r.get("edu").getRemainingFraction()).isEqualTo(1.0);

	}
	// PART 2 : LOCAL RESTRICTIONS

	/**
	 * Tests
	 * A) No restriction in place
	 * B) Only Global Rf present
	 * C) Only local Rf present
	 * D) Both global & local Rf present
	 * <p>
	 * i) add new local Rf
	 * ii) change local Rf
	 * 1) localRf is added
	 */


	@Test
	public void changeRestrictionWithDateLocal() {
		List<String> nycBoroughs = List.of("Manhattan", "Brooklyn", "Queens", "Bronx", "StatenIsland");

		Config config = AdaptivePolicy.config()
				.startDate("2030-12-31")
				.restrictionScope(AdaptivePolicy.RestrictionScope.local.toString())
				.incidenceTrigger(35, 50, "work")
				.incidenceTrigger(50, 50, "edu")
				.initialPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.111)), "work")
						.restrict(LocalDate.parse("2020-01-09"), Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.222)), "work")
						.restrict(LocalDate.parse("2020-01-19"), Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.444)), "work")
						.restrict(LocalDate.parse("2020-01-29"), Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.666)), "work")
				)
				.restrictedPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.2)), "work")
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.0)), "edu")
				)
				.openPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.8)), "work")
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 1.0)), "edu")
				)
				.build();

		AdaptivePolicy policy = new AdaptivePolicy(config, nycBoroughs);
		LocalDate startingDate = LocalDate.parse("2020-01-01");
		LocalDate localDate = null;
		int day = 0;

		policy.init(startingDate, r); // TODO: test init


		assertThat(r.get("work").getLocationBasedRf().isEmpty()).isFalse();
		assertThat(r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.111);

		Map<String, Integer> symptomsPerBorough = new HashMap<>();
		symptomsPerBorough.put("Queens", 5000);
		symptomsPerBorough.put("Brooklyn", 0);

		for (; day < 9; day++) {

			localDate = startingDate.plusDays(day);
			Map<String, EpisimReporting.InfectionReport> reportSubdistrict = EpisimTestUtils.createReportSubdistrict(localDate, day, symptomsPerBorough);
			policy.updateRestrictions(reportSubdistrict, r);
		}

		//01-09
		assertThat(localDate).isEqualTo("2020-01-09");
		assertThat(r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.111);

		//01-10
		localDate = startingDate.plusDays(day);
		policy.updateRestrictions(EpisimTestUtils.createReportSubdistrict(localDate, day, symptomsPerBorough), r);

		assertThat(localDate).isEqualTo("2020-01-10");
		assertThat(r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.222);

		for (; day < 19; day++) {
			localDate = startingDate.plusDays(day);
			Map<String, EpisimReporting.InfectionReport> reportSubdistrict = EpisimTestUtils.createReportSubdistrict(localDate, day, symptomsPerBorough);
			policy.updateRestrictions(reportSubdistrict, r);
		}
		//01-19
		assertThat(localDate).isEqualTo("2020-01-19");
		assertThat(r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.222);

		//01-20
		localDate = startingDate.plusDays(day);
		policy.updateRestrictions(EpisimTestUtils.createReportSubdistrict(localDate, day, symptomsPerBorough), r);

		assertThat(localDate).isEqualTo("2020-01-20");
		assertThat(r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.444);

		for (; day < 29; day++) {
			localDate = startingDate.plusDays(day);
			Map<String, EpisimReporting.InfectionReport> reportSubdistrict = EpisimTestUtils.createReportSubdistrict(localDate, day, symptomsPerBorough);
			policy.updateRestrictions(reportSubdistrict, r);
		}
		//01-29
		assertThat(localDate).isEqualTo("2020-01-29");
		assertThat(r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.444);

		//01-30
		localDate = startingDate.plusDays(day);
		policy.updateRestrictions(EpisimTestUtils.createReportSubdistrict(localDate, day, symptomsPerBorough), r);

		assertThat(localDate).isEqualTo("2020-01-30");
		assertThat(r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.666);

	}

	/**
	 * scope : local
	 * initial : none
	 * restricted : local Rf only
	 * open : local Rf only
	 */
	@Test
	public void noInitial() {

		List<String> nycBoroughs = List.of("Manhattan", "Brooklyn", "Queens", "Bronx", "StatenIsland");

		Config config = AdaptivePolicy.config()
				.restrictionScope(AdaptivePolicy.RestrictionScope.local.toString())
				.incidenceTrigger(35, 50, "work")
				.incidenceTrigger(50, 50, "edu")
				.restrictedPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.2)), "work")
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.0)), "edu")
				)
				.openPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.8)), "work")
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 1.0)), "edu")
				)
				.build();

		AdaptivePolicy policy = new AdaptivePolicy(config, nycBoroughs);
		LocalDate date = LocalDate.now();
		int day = 0;
		int showingSymptoms = 0;

		policy.init(date, r);

		assertThat(r.get("home").getRemainingFraction()).isEqualTo(1.0);
		assertThat(r.get("home").getLocationBasedRf().isEmpty()).isTrue();

		//		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.9);
		//		assertThat(r.get("work").getLocationBasedRf().isEmpty()).isTrue();

		for (; day < 8; day++) {
			showingSymptoms += 60 / 7;
			//			EpisimTestUtils.createReport(date.plusDays(day), day, showingSymptoms)
			Map<String, Integer> stringIntegerMap = new HashMap<>();
			stringIntegerMap.put("Queens", showingSymptoms);
			stringIntegerMap.put("Brooklyn", showingSymptoms);
			stringIntegerMap.put("Manhattan", 0);

			Map<String, EpisimReporting.InfectionReport> reportSubdistrict = EpisimTestUtils.createReportSubdistrict(date.plusDays(day), day, stringIntegerMap);
			policy.updateRestrictions(reportSubdistrict, r);
		}


		//		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.9);
		assertThat(r.get("work").getLocationBasedRf().isEmpty()).isFalse();
		assertThat(r.get("work").getLocationBasedRf().containsKey("Queens")).isTrue();
		assertThat(r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.2);
		assertThat(r.get("work").getLocationBasedRf().containsKey("Brooklyn")).isTrue();
		assertThat(r.get("work").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.2);
		assertThat(r.get("work").getLocationBasedRf().containsKey("Manhattan")).isFalse();
		//		assertThat(r.get("edu").getRemainingFraction()).isEqualTo(0.0);

		for (; day < 30; day++) {
			showingSymptoms += 23 / 7;
			Map<String, Integer> stringIntegerMap = new HashMap<>();
			stringIntegerMap.put("Queens", showingSymptoms);
			stringIntegerMap.put("Brooklyn", showingSymptoms);
			stringIntegerMap.put("Manhattan", 0);
			Map<String, EpisimReporting.InfectionReport> reportSubdistrict = EpisimTestUtils.createReportSubdistrict(date.plusDays(day), day, stringIntegerMap);
			policy.updateRestrictions(reportSubdistrict, r);
		}

		// open after 14 days of lockdown


		assertThat(r.get("work").getLocationBasedRf().isEmpty()).isFalse();
		assertThat(r.get("work").getLocationBasedRf().containsKey("Queens")).isTrue();
		assertThat(r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.8);
		assertThat(r.get("work").getLocationBasedRf().containsKey("Brooklyn")).isTrue();
		assertThat(r.get("work").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.8);
		assertThat(r.get("work").getLocationBasedRf().containsKey("Manhattan")).isFalse();
		//		assertThat(r.get("work").getRemainingFraction()).isEqualTo(0.8);
		//		assertThat(r.get("edu").getRemainingFraction()).isEqualTo(1.0);

	}

	/**
	 * There restricted & open policies are always only contain local Rfs. However, there are four different types of
	 * initial polices, which are examined side-by-side here:
	 * 1) no initial policy (home)
	 * 2) initial policy with global Rf restriction (work)
	 * 3) initial policy with local Rf restriction (pt)
	 * 4) initial policy with both global and local Rf (edu)
	 *
	 * For all four cases, the expected result is as follows: global Rf will not be changed (if none is given, default is 1.0),
	 * while local Rf will be replaced/added for district where trigger was activated.
	 */
	@Test
	public void initialPolicyTypesLocal() {

		List<String> nycBoroughs = List.of("Manhattan", "Brooklyn", "Queens", "Bronx", "StatenIsland");

		Restriction global = Restriction.of(0.91);
		Restriction local = Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.92));
		Restriction globalLocal = Restriction.clone(local);
		globalLocal.merge(global.asMap());


		Config config = AdaptivePolicy.config()
				.restrictionScope(AdaptivePolicy.RestrictionScope.local.toString())
				.incidenceTrigger(50, 50, "home","work", "pt", "edu")
				.initialPolicy(FixedPolicy.config()
						// no initial policy for home
						.restrict(LocalDate.MIN, global, "work")
						.restrict(LocalDate.MIN, local, "pt")
						.restrict(LocalDate.MIN, globalLocal, "edu")

				)
				.restrictedPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.20)), "home")
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.21)), "work")
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.22)), "pt")
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.23)), "edu")
				)
				.openPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.80)), "home")
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.81)), "work")
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.82)), "pt")
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.83)), "edu")
				)
				.build();

		AdaptivePolicy policy = new AdaptivePolicy(config, nycBoroughs);
		LocalDate date = LocalDate.parse("2020-01-01");
		int day = 0;
		int showingSymptoms = 0;

		policy.init(date, this.r);

		// Test after initialization
		// 1) home
		assertThat(this.r.get("home").getRemainingFraction()).isEqualTo(1.0);
		assertThat(this.r.get("home").getLocationBasedRf().isEmpty()).isTrue();

		// 2) work
		assertThat(this.r.get("work").getRemainingFraction()).isEqualTo(0.91);
		assertThat(this.r.get("work").getLocationBasedRf().isEmpty()).isTrue();

		// 3) pt
		assertThat(this.r.get("pt").getRemainingFraction()).isEqualTo(1.0);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.92);

		// 4) edu
		assertThat(this.r.get("edu").getRemainingFraction()).isEqualTo(0.91);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.92);


		// Test restricted policy:
		for (; day < 8; day++) {
			showingSymptoms += 60 / 7;
			Map<String, Integer> stringIntegerMap = new HashMap<>();
			stringIntegerMap.put("Brooklyn", showingSymptoms);

			Map<String, EpisimReporting.InfectionReport> reportSubdistrict = EpisimTestUtils.createReportSubdistrict(date.plusDays(day), day, stringIntegerMap);
			policy.updateRestrictions(reportSubdistrict, this.r);
		}

		// 1) home
		assertThat(this.r.get("home").getRemainingFraction()).isEqualTo(1.0);
		assertThat(this.r.get("home").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.20);
		assertThat(this.r.get("home").getLocationBasedRf().containsKey("Queens")).isFalse();


		// 2) work
		assertThat(this.r.get("work").getRemainingFraction()).isEqualTo(0.91);
		assertThat(this.r.get("work").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.21);
		assertThat(this.r.get("work").getLocationBasedRf().containsKey("Queens")).isFalse();

		// 3) pt
		assertThat(this.r.get("pt").getRemainingFraction()).isEqualTo(1.0);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.22);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Queens")).isEqualTo(0.92);

		// 4) edu
		assertThat(this.r.get("edu").getRemainingFraction()).isEqualTo(0.91);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.23);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Queens")).isEqualTo(0.92);


		// Test open policy
		for (; day < 30; day++) {
			showingSymptoms = 0;
			Map<String, Integer> stringIntegerMap = new HashMap<>();
			stringIntegerMap.put("Brooklyn", showingSymptoms);
			Map<String, EpisimReporting.InfectionReport> reportSubdistrict = EpisimTestUtils.createReportSubdistrict(date.plusDays(day), day, stringIntegerMap);
			policy.updateRestrictions(reportSubdistrict, this.r);
		}

		// 1) home
		assertThat(this.r.get("home").getRemainingFraction()).isEqualTo(1.0);
		assertThat(this.r.get("home").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.80);
		assertThat(this.r.get("home").getLocationBasedRf().containsKey("Queens")).isFalse();

		// 2) work
		assertThat(this.r.get("work").getRemainingFraction()).isEqualTo(0.91);
		assertThat(this.r.get("work").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.81);
		assertThat(this.r.get("work").getLocationBasedRf().containsKey("Queens")).isFalse();

		// 3) pt
		assertThat(this.r.get("pt").getRemainingFraction()).isEqualTo(1.0);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.82);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Queens")).isEqualTo(0.92);

		// 4) edu
		assertThat(this.r.get("edu").getRemainingFraction()).isEqualTo(0.91);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.83);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Queens")).isEqualTo(0.92);

	}

	/**
	 * Initial & open policies are kept constant, while restricted policy is varied
	 * Tests different restricted policies.
	 *
	 * 1) no restricted policy (home)
	 * 2) restricted policy with global Rf restriction (work)
	 * 3) restricted policy with local Rf restriction (pt)
	 * 4) restricted policy with both global and local Rf (edu)
	 */

	@Test
	public void restrictedPolicyTypesLocal() {

		List<String> nycBoroughs = List.of("Manhattan", "Brooklyn", "Queens", "Bronx", "StatenIsland");

		// initial policy
		Restriction initial = Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.9));
		initial.merge(Restriction.of(0.9).asMap());

		// restricted policy
		Restriction global = Restriction.of(0.21);
		Restriction local = Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.22));
		Restriction globalLocal = Restriction.clone(local);
		globalLocal.merge(global.asMap());


		Config config = AdaptivePolicy.config()
				.restrictionScope(AdaptivePolicy.RestrictionScope.local.toString())
				.incidenceTrigger(50, 50, "home", "work", "pt", "edu")
				.initialPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, initial, "home", "work", "pt", "edu")
				)
				.restrictedPolicy(FixedPolicy.config()
						// no restricted policy for home
						.restrict(LocalDate.MIN, global, "work")
						.restrict(LocalDate.MIN, local, "pt")
						.restrict(LocalDate.MIN, globalLocal, "edu")
				)
				.openPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.80)), "home", "work", "pt", "edu")
				)
				.build();

		AdaptivePolicy policy = new AdaptivePolicy(config, nycBoroughs);
		LocalDate date = LocalDate.parse("2020-01-01");
		int day = 0;
		int showingSymptoms = 0;

		policy.init(date, this.r);

		// Test after initialization
		// 1) home
		assertThat(this.r.get("home").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("home").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.9);

		// 2) work
		assertThat(this.r.get("work").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("work").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.9);

		// 3) pt
		assertThat(this.r.get("pt").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.9);

		// 4) edu
		assertThat(this.r.get("edu").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.9);


		// Test restricted policy:
		for (; day < 8; day++) {
			showingSymptoms += 60 / 7;
			Map<String, Integer> stringIntegerMap = new HashMap<>();
			stringIntegerMap.put("Brooklyn", showingSymptoms);

			Map<String, EpisimReporting.InfectionReport> reportSubdistrict = EpisimTestUtils.createReportSubdistrict(date.plusDays(day), day, stringIntegerMap);
			policy.updateRestrictions(reportSubdistrict, this.r);
		}

		// 1) home
		assertThat(this.r.get("home").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("home").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.90);
		assertThat(this.r.get("home").getLocationBasedRf().get("Queens")).isEqualTo(0.90);


		// 2) work
		assertThat(this.r.get("work").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("work").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.90);
		assertThat(this.r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.90);

		// 3) pt
		assertThat(this.r.get("pt").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.22);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Queens")).isEqualTo(0.90);

		// 4) edu
		assertThat(this.r.get("edu").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.22);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Queens")).isEqualTo(0.90);



		// Test open policy
		for (; day < 30; day++) {
			showingSymptoms = 0;
			Map<String, Integer> stringIntegerMap = new HashMap<>();
			stringIntegerMap.put("Brooklyn", showingSymptoms);
			Map<String, EpisimReporting.InfectionReport> reportSubdistrict = EpisimTestUtils.createReportSubdistrict(date.plusDays(day), day, stringIntegerMap);
			policy.updateRestrictions(reportSubdistrict, this.r);
		}

		// 1) home
		assertThat(this.r.get("home").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("home").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.80);
		assertThat(this.r.get("home").getLocationBasedRf().get("Queens")).isEqualTo(0.90);


		// 2) work
		assertThat(this.r.get("work").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("work").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.80);
		assertThat(this.r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.90);

		// 3) pt
		assertThat(this.r.get("pt").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.8);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Queens")).isEqualTo(0.90);

		// 4) edu
		assertThat(this.r.get("edu").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.8);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Queens")).isEqualTo(0.90);

	}

	/**
	 * Initial & restricted policies are kept constant, while restricted policy is varied
	 * Tests different open policies.
	 *
	 * 1) no open policy (home)
	 * 2) open policy with global Rf restriction (work)
	 * 3) open policy with local Rf restriction (pt)
	 * 4) open policy with both global and local Rf (edu)
	 */

	@Test
	public void openPolicyTypesLocal() {

		List<String> nycBoroughs = List.of("Manhattan", "Brooklyn", "Queens", "Bronx", "StatenIsland");

		// initial policy
		Restriction initial = Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.9));
		initial.merge(Restriction.of(0.9).asMap());

		// open policy
		Restriction global = Restriction.of(0.81);
		Restriction local = Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.82));
		Restriction globalLocal = Restriction.clone(local);
		globalLocal.merge(global.asMap());


		Config config = AdaptivePolicy.config()
				.restrictionScope(AdaptivePolicy.RestrictionScope.local.toString())
				.incidenceTrigger(50, 50, "home", "work", "pt", "edu")
				.initialPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, initial, "home", "work", "pt", "edu")
				)
				.restrictedPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.20)), "home", "work", "pt", "edu")
				)
				.openPolicy(FixedPolicy.config()
						// no restricted policy for home
						.restrict(LocalDate.MIN, global, "work")
						.restrict(LocalDate.MIN, local, "pt")
						.restrict(LocalDate.MIN, globalLocal, "edu")

				)
				.build();

		AdaptivePolicy policy = new AdaptivePolicy(config, nycBoroughs);
		LocalDate date = LocalDate.parse("2020-01-01");
		int day = 0;
		int showingSymptoms = 0;

		policy.init(date, this.r);

		// Test after initialization
		// 1) home
		assertThat(this.r.get("home").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("home").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.9);

		// 2) work
		assertThat(this.r.get("work").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("work").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.9);

		// 3) pt
		assertThat(this.r.get("pt").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.9);

		// 4) edu
		assertThat(this.r.get("edu").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.9);


		// Test restricted policy:
		for (; day < 8; day++) {
			showingSymptoms += 60 / 7;
			Map<String, Integer> stringIntegerMap = new HashMap<>();
			stringIntegerMap.put("Brooklyn", showingSymptoms);

			Map<String, EpisimReporting.InfectionReport> reportSubdistrict = EpisimTestUtils.createReportSubdistrict(date.plusDays(day), day, stringIntegerMap);
			policy.updateRestrictions(reportSubdistrict, this.r);
		}

		// 1) home
		assertThat(this.r.get("home").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("home").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.20);
		assertThat(this.r.get("home").getLocationBasedRf().get("Queens")).isEqualTo(0.90);


		// 2) work
		assertThat(this.r.get("work").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("work").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.20);
		assertThat(this.r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.90);

		// 3) pt
		assertThat(this.r.get("pt").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.20);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Queens")).isEqualTo(0.90);

		// 4) edu
		assertThat(this.r.get("edu").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.2);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Queens")).isEqualTo(0.90);



		// Test open policy
		for (; day < 30; day++) {
			showingSymptoms = 0;
			Map<String, Integer> stringIntegerMap = new HashMap<>();
			stringIntegerMap.put("Brooklyn", showingSymptoms);
			Map<String, EpisimReporting.InfectionReport> reportSubdistrict = EpisimTestUtils.createReportSubdistrict(date.plusDays(day), day, stringIntegerMap);
			policy.updateRestrictions(reportSubdistrict, this.r);
		}

		// 1) home
		assertThat(this.r.get("home").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("home").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.20);
		assertThat(this.r.get("home").getLocationBasedRf().get("Queens")).isEqualTo(0.90);


		// 2) work
		assertThat(this.r.get("work").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("work").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.20);
		assertThat(this.r.get("work").getLocationBasedRf().get("Queens")).isEqualTo(0.90);

		// 3) pt
		assertThat(this.r.get("pt").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.82);
		assertThat(this.r.get("pt").getLocationBasedRf().get("Queens")).isEqualTo(0.90);

		// 4) edu
		assertThat(this.r.get("edu").getRemainingFraction()).isEqualTo(0.90);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Brooklyn")).isEqualTo(0.82);
		assertThat(this.r.get("edu").getLocationBasedRf().get("Queens")).isEqualTo(0.90);

	}



	private Map<String, Double> makeUniformLocalRf(List<String> nycBoroughs, Double rf) {
		Map<String, Double> localRf = new HashMap<>();
		for (String borough : nycBoroughs) {
			localRf.put(borough, rf);
		}
		return localRf;
	}
}

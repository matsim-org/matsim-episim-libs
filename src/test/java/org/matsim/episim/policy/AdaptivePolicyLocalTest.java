package org.matsim.episim.policy;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import org.junit.Before;
import org.junit.Test;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.EpisimTestUtils;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class AdaptivePolicyLocalTest {

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

	@Test
	public void createTestReports(){

		LocalDate localDate = LocalDate.parse("2020-02-25");

		long iteration = 1;
		Map<String, Integer> stringIntegerMap = new HashMap<>();
		stringIntegerMap.put("queens", 100);
		stringIntegerMap.put("brooklyn", 10);
		stringIntegerMap.put("manhattan", 60);

		Map<String, EpisimReporting.InfectionReport> reportSubdistrict = EpisimTestUtils.createReportSubdistrict(localDate, iteration, stringIntegerMap);

		assertThat(reportSubdistrict.get("queens").nShowingSymptomsCumulative).isEqualTo(100);
		assertThat(reportSubdistrict.get("queens").nSusceptible).isEqualTo(100_000);

	}

	@Test
	public void updateDistrictValue() {

		// make global restriction:
		Restriction global = Restriction.of(0.555);

		// just of location based restriction
		Map<String, Double> nycBoroughs = new HashMap<>();
		nycBoroughs.put("Bronx", 0.7);
		nycBoroughs.put("Queens", 0.8);
		Restriction rNYC = Restriction.ofLocationBasedRf(nycBoroughs);

		assertThat(rNYC.getRemainingFraction()).isNull();
		assertThat(rNYC.getLocationBasedRf().get("Bronx")).isEqualTo(0.7);
		assertThat(rNYC.getLocationBasedRf().get("Queens")).isEqualTo(0.8);


		global.update(rNYC);
		assertThat(global.getRemainingFraction()).isEqualTo(0.555);
		assertThat(global.getLocationBasedRf().get("Bronx")).isEqualTo(0.7);
		assertThat(global.getLocationBasedRf().get("Queens")).isEqualTo(0.8);



		Map<String, Double> nycNew = new HashMap<>();
		nycNew.put("Bronx", 0.2);
		Restriction rNYC2 = Restriction.ofLocationBasedRf(nycNew);
		global.updateLocationBasedRf(rNYC2);

		assertThat(global.getRemainingFraction()).isEqualTo(0.555);
		assertThat(global.getLocationBasedRf().get("Bronx")).isEqualTo(0.2);
		assertThat(global.getLocationBasedRf().get("Queens")).isEqualTo(0.8);
	}

	@Test
	public void policy() {

		List<String> nycBoroughs = List.of("Manhattan", "Brooklyn", "Queens", "Bronx", "StatenIsland");
		Double rf = 0.0;


		Config config = AdaptivePolicyLocal.config()
				.incidenceTrigger(35, 50, "work")
				.incidenceTrigger(50, 50, "edu")
//				.initialPolicy(FixedPolicy.config()
//						.restrict(LocalDate.MIN, Restriction.of(0.9), "work")
//				)
				.restrictedPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.2)), "work")
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.0)), "edu")
				)
				.openPolicy(FixedPolicy.config()
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 0.8)), "work")
						.restrict(LocalDate.MIN, Restriction.ofLocationBasedRf(makeUniformLocalRf(nycBoroughs, 1.0)), "edu")
				)
				.build();

		AdaptivePolicyLocal policy = new AdaptivePolicyLocal(config);
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
			policy.updateRestrictionsLocal(reportSubdistrict, r);
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
			policy.updateRestrictionsLocal(reportSubdistrict, r);
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

	private Map<String, Double> makeUniformLocalRf(List<String> nycBoroughs, Double rf) {
		Map<String, Double> localRf = new HashMap<>();
		for (String borough : nycBoroughs) {
			localRf.put(borough, rf);
		}
		return localRf;
	}
}

package org.matsim.episim.policy;

import org.junit.Test;
import org.matsim.episim.model.FaceMask;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RestrictionTest {

	/**
	 * Helper function to allow updating restrictions for tests.
	 */
	public static Restriction update(Restriction r, Restriction other) {
		r.update(other);
		return r;
	}

	/**
	 * Helper function to allow merging restrictions for tests.
	 */
	public static Restriction merge(Restriction r, Restriction other) {
		r.merge(other.asMap());
		return r;
	}

	@Test
	public void merge() {

		Restriction r = Restriction.of(0.8);

		r.merge(Restriction.ofMask(FaceMask.CLOTH, 0.5).asMap());
		r.merge(Restriction.ofCiCorrection(0.5).asMap());
		r.merge(Restriction.ofGroupSize(20).asMap());
		r.merge(Restriction.ofReducedGroupSize(5).asMap());
		r.merge(Restriction.ofClosingHours(0, 7).asMap());



		assertThat(r.getRemainingFraction()).isEqualTo(0.8);
		assertThat(r.getCiCorrection()).isEqualTo(0.5);

		assertThat(r.getMaskUsage().get(FaceMask.NONE)).isEqualTo(0.5);
		assertThat(r.getMaskUsage().get(FaceMask.CLOTH)).isEqualTo(1);

		assertThat(r.getMaxGroupSize()).isEqualTo(20);
		assertThat(r.getReducedGroupSize()).isEqualTo(5);

		assertThat(r.hasClosingHours()).isTrue();
		assertThat(r.getClosingHours()).isEqualTo(new Restriction.ClosingHours(hours(0), hours(7)));

		assertThat(Restriction.ofClosingHours(0, 0).hasClosingHours()).isFalse();

		Map<String, Double> nycBoroughs = new HashMap<>();
		nycBoroughs.put("Bronx", 0.7);
		nycBoroughs.put("Queens", 0.8);
		Restriction localRestriction = Restriction.ofLocationBasedRf(nycBoroughs);
		r.merge(localRestriction.asMap());

		// if merged in this direction, locationBasedRf is NOT kept
		assertThat(r.getLocationBasedRf().size()).isEqualTo(0);

		// if merged in this direction, locationBasedRf IS kept
		localRestriction.merge(r.asMap());
		assertThat(localRestriction.getLocationBasedRf().size()).isEqualTo(2);
		assertThat(localRestriction.getLocationBasedRf().get("Queens")).isEqualTo(0.8);

	}

	@Test
	public void adjustClosing() {

		Restriction r = Restriction.ofClosingHours(5, 9);

		assertThat(r.getClosingHours()).isEqualTo(new Restriction.ClosingHours(hours(5), hours(9)));

		// not overwritten
		r.merge(Restriction.ofClosingHours(17, 20).asMap());
		assertThat(r.getClosingHours()).isEqualTo(new Restriction.ClosingHours(hours(5), hours(9)));

		r = Restriction.ofClosingHours(1, 6);

		assertThat(r.calculateOverlap(hours(2), true))
				.isEqualTo(hours(4));

		assertThat(r.calculateOverlap(hours(2), false))
				.isEqualTo(hours(1));

		r = Restriction.ofClosingHours(22, 6);

		assertThat(r.calculateOverlap(hours(23), true))
				.isEqualTo(hours(7));
		assertThat(r.calculateOverlap(hours(23), false))
				.isEqualTo(hours(1));

		assertThat(r.calculateOverlap(hours(3), true))
				.isEqualTo(hours(3));
		assertThat(r.calculateOverlap(hours(3), false))
				.isEqualTo(hours(5));
	}

	@Test
	public void closingHours() {

		int d = days(2);

		Restriction r = Restriction.ofClosingHours(0, 24);
		assertThat(r.overlapWithClosingHour(d + hours(1), d + hours(5)))
				.isEqualTo(Integer.MAX_VALUE);

		r = Restriction.ofClosingHours(1, 6);

		assertThat(r.overlapWithClosingHour(d + hours(1), d + hours(5)))
				.isEqualTo(hours(4));
		assertThat(r.overlapWithClosingHour(d + hours(0), d + hours(7)))
				.isEqualTo(hours(5));
		assertThat(r.overlapWithClosingHour(d + hours(4), d + hours(7)))
				.isEqualTo(hours(2));
		assertThat(r.overlapWithClosingHour(d + hours(23), d + days(1) + hours(7)))
				.isEqualTo(hours(5));

		// not affected
		assertThat(r.overlapWithClosingHour(d + hours(6), d + hours(8)))
				.isEqualTo(0);
		assertThat(r.overlapWithClosingHour(d + hours(23), d + hours(24)))
				.isEqualTo(0);
		assertThat(r.overlapWithClosingHour(d + hours(23), d + hours(24) + 30))
				.isEqualTo(0);

		r = Restriction.ofClosingHours(22, 6);
		assertThat(r.overlapWithClosingHour(d + hours(22), d + hours(23)))
				.isEqualTo(hours(1));
		assertThat(r.overlapWithClosingHour(d + hours(22), d + days(1) + hours(5)))
				.isEqualTo(hours(7));
		assertThat(r.overlapWithClosingHour(d + hours(21), d + days(1) + hours(10)))
				.isEqualTo(hours(8));
		assertThat(r.overlapWithClosingHour(d + hours(0), d + hours(7)))
				.isEqualTo(hours(6));
		assertThat(r.overlapWithClosingHour(d + hours(4), d + hours(7)))
				.isEqualTo(hours(2));
		// not affected
		assertThat(r.overlapWithClosingHour(d + hours(6), d + hours(8)))
				.isEqualTo(0);
		assertThat(r.overlapWithClosingHour(d + hours(8), d + hours(22)))
				.isEqualTo(0);
		assertThat(r.overlapWithClosingHour(d + hours(8), d + hours(10)))
				.isEqualTo(0);


	}

	@Test
	public void locationBasedRestrictions() {

		// Set up restrictions
		Map<String, Double> nycBoroughs = new HashMap<>();
		nycBoroughs.put("Bronx", 0.7);
		nycBoroughs.put("Queens", 0.8);
		Restriction rNYC = Restriction.ofLocationBasedRf(nycBoroughs);

		Map<String, Double> berlinDistricts = new HashMap<>();
		berlinDistricts.put("Marzahn", 0.6);
		berlinDistricts.put("Wilmersdorf", 0.4);
		Restriction rBerlin = Restriction.ofLocationBasedRf(berlinDistricts);

		Restriction rEmpty = Restriction.ofLocationBasedRf(new HashMap<>());

		Restriction rNull = Restriction.ofLocationBasedRf(null);

		// Test clone functionality
		Restriction clone = Restriction.clone(rNYC);
		assertThat(clone.getLocationBasedRf().size()).isEqualTo(2);
		assertThat(clone.getLocationBasedRf().get("Queens")).isEqualTo(0.8);

		// Test update/merge functionality
		// Update/Merge nyc into berlin should always keep berlins values
		Restriction updateResult = update(rNYC, rBerlin);
		assertThat(updateResult.getLocationBasedRf().containsKey("Queens")).isEqualTo(false);
		assertThat(updateResult.getLocationBasedRf().containsKey("Wilmersdorf")).isEqualTo(true);
		assertThat(updateResult.getLocationBasedRf().get("Wilmersdorf")).isEqualTo(0.4);

		Restriction mergeResult = merge(rNYC, rBerlin);
		assertThat(mergeResult.getLocationBasedRf().containsKey("Queens")).isEqualTo(false);
		assertThat(mergeResult.getLocationBasedRf().containsKey("Wilmersdorf")).isEqualTo(true);
		assertThat(mergeResult.getLocationBasedRf().get("Wilmersdorf")).isEqualTo(0.4);

		// Update/Merge berlin from empty & vice versa should always keep berlins values;
		updateResult = update(rEmpty, rBerlin);
		assertThat(updateResult.getLocationBasedRf().containsKey("Wilmersdorf")).isEqualTo(true);
		assertThat(updateResult.getLocationBasedRf().get("Wilmersdorf")).isEqualTo(0.4);

		updateResult = update(rBerlin, rEmpty);
		assertThat(updateResult.getLocationBasedRf().containsKey("Wilmersdorf")).isEqualTo(true);
		assertThat(updateResult.getLocationBasedRf().get("Wilmersdorf")).isEqualTo(0.4);

		mergeResult = merge(rEmpty, rBerlin);
		assertThat(mergeResult.getLocationBasedRf().containsKey("Wilmersdorf")).isEqualTo(true);
		assertThat(mergeResult.getLocationBasedRf().get("Wilmersdorf")).isEqualTo(0.4);

		mergeResult = merge(rBerlin, rEmpty);
		assertThat(mergeResult.getLocationBasedRf().containsKey("Wilmersdorf")).isEqualTo(true);
		assertThat(mergeResult.getLocationBasedRf().get("Wilmersdorf")).isEqualTo(0.4);

		// Update/Merge berlin from null & vice versa should always keep berlins values;
		updateResult = update(rNull, rBerlin);
		assertThat(updateResult.getLocationBasedRf().containsKey("Wilmersdorf")).isEqualTo(true);
		assertThat(updateResult.getLocationBasedRf().get("Wilmersdorf")).isEqualTo(0.4);

		updateResult = update(rBerlin, rNull);
		assertThat(updateResult.getLocationBasedRf().containsKey("Wilmersdorf")).isEqualTo(true);
		assertThat(updateResult.getLocationBasedRf().get("Wilmersdorf")).isEqualTo(0.4);

		mergeResult = merge(rNull, rBerlin);
		assertThat(mergeResult.getLocationBasedRf().containsKey("Wilmersdorf")).isEqualTo(true);
		assertThat(mergeResult.getLocationBasedRf().get("Wilmersdorf")).isEqualTo(0.4);

		mergeResult = merge(rBerlin, rNull);
		assertThat(mergeResult.getLocationBasedRf().containsKey("Wilmersdorf")).isEqualTo(true);
		assertThat(mergeResult.getLocationBasedRf().get("Wilmersdorf")).isEqualTo(0.4);

		// Tests merge behaviour between Restrictions with rf and Restrictions with localRfs
		// As a rule, if new Restriction has rf, the old restriction's localRf should not be used, as it could be out of date

		Restriction rf05 = Restriction.of(0.5);
		Restriction rf08 = Restriction.of(0.8);

		// old Restriction: rBerlin
		// new Restriction: rf05
		// merged Restriction should only contain rf05; rBerlin should be deleted
		mergeResult = merge(rf05, rBerlin);
		assertThat(mergeResult.getRemainingFraction()).isEqualTo(0.5);
		assertThat(mergeResult.getLocationBasedRf().size()).isEqualTo(0);

		// old Restriction: rf05
		// new Restriction: rBerlin
		// merged should contain both rf05 and localRf
		Restriction rBerlin_rf05 = merge(rBerlin, rf05);
		assertThat(rBerlin_rf05.getRemainingFraction()).isEqualTo(0.5);
		assertThat(rBerlin_rf05.getLocationBasedRf().size()).isEqualTo(2);

		// old Restriction: rBerlin_rf05
		// new Restriction: rf08
		// merged should only contain rf of 0.8
		mergeResult = merge(rf08, rBerlin_rf05);
		assertThat(mergeResult.getRemainingFraction()).isEqualTo(0.8);
		assertThat(mergeResult.getLocationBasedRf().size()).isEqualTo(0);

		// old Restriction: rf08
		// new Restriction: rBerlin_rf05
		// merged should equal rBerlin_rf05
		mergeResult = merge(rBerlin_rf05, rf08);
		assertThat(mergeResult.getRemainingFraction()).isEqualTo(0.5);
		assertThat(mergeResult.getLocationBasedRf().size()).isEqualTo(2);


	}

	private int days(int d) {
		return d * 86400;
	}

	private int hours(int h) {
		return h * 3600;
	}

}

package org.matsim.episim.policy;

import org.junit.Test;
import org.matsim.episim.model.FaceMask;

import static org.assertj.core.api.Assertions.assertThat;

public class RestrictionTest {

	/**
	 * Helper function to allow updating restrictions for tests.
	 */
	public static Restriction update(Restriction r, Restriction other) {
		r.update(other);
		return r;
	}

	@Test
	public void merge() {

		Restriction r = Restriction.of(0.8);

		r.merge(Restriction.ofMask(FaceMask.CLOTH, 0.5).asMap());
		r.merge(Restriction.ofCiCorrection(0.5).asMap());
		r.merge(Restriction.ofGroupSize(20).asMap());
		r.merge(Restriction.ofClosingHours(0, 7).asMap());

		assertThat(r.getRemainingFraction()).isEqualTo(0.8);
		assertThat(r.getCiCorrection()).isEqualTo(0.5);

		assertThat(r.getMaskUsage().get(FaceMask.NONE)).isEqualTo(0.5);
		assertThat(r.getMaskUsage().get(FaceMask.CLOTH)).isEqualTo(1);

		assertThat(r.getMaxGroupSize()).isEqualTo(20);

		assertThat(r.getClosingHours()).isEqualTo(new Restriction.ClosingHours(hours(0), hours(7)));

		//assertThatExceptionOfType(IllegalArgumentException.class)
		//		.isThrownBy(() -> r.merge(Restriction.ofExposure(0.4).asMap()));

	}

	@Test
	public void closingHours() {

		Restriction r = Restriction.ofClosingHours(5, 9);

		assertThat(r.getClosingHours()).isEqualTo(new Restriction.ClosingHours(hours(5), hours(9)));

		// not overwritten
		r.merge(Restriction.ofClosingHours(17, 20).asMap());
		assertThat(r.getClosingHours()).isEqualTo(new Restriction.ClosingHours(hours(5), hours(9)));

		r = Restriction.ofClosingHours(1, 6);

		// moved to be later
		assertThat(r.adjustByClosingHour(hours(2), true))
				.isEqualTo(hours(6));

		assertThat(r.adjustByClosingHour(hours(2), false))
				.isEqualTo(hours(1));

		assertThat(r.adjustByClosingHour(days(2) + hours(3), true))
				.isEqualTo(days(2) + hours(6));

		assertThat(r.adjustByClosingHour(days(2) + hours(3), false))
				.isEqualTo(days(2) + hours(1));

		assertThat(r.adjustByClosingHour(days(2) + hours(10), true))
				.isEqualTo(days(2) + hours(10));

		r = Restriction.ofClosingHours(0, 24);
		assertThat(r.adjustByClosingHour(days(2) + hours(10), true))
				.isEqualTo(Integer.MAX_VALUE);

	}

	@Test
	public void closingOverlap() {

		Restriction r = Restriction.ofClosingHours(22, 6);

		// day
		int d = days(2);

		assertThat(r.adjustByClosingHour(d + hours(8), false))
				.isEqualTo(d + hours(8));

		assertThat(r.adjustByClosingHour(d + hours(23), true))
				.isEqualTo(d + days(1) + hours(6));
		assertThat(r.adjustByClosingHour(d + hours(23), false))
				.isEqualTo(d + hours(22));

		assertThat(r.adjustByClosingHour(d + days(1) + hours(3), true))
				.isEqualTo(d + days(1) + hours(6));
		assertThat(r.adjustByClosingHour(d + days(1) + hours(3), false))
				.isEqualTo(d + hours(22));

	}

	private int days(int d) {
		return d * 86400;
	}

	private int hours(int h) {
		return h * 3600;
	}

}

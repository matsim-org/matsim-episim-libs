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

	private int days(int d) {
		return d * 86400;
	}

	private int hours(int h) {
		return h * 3600;
	}

}

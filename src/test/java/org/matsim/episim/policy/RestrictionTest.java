package org.matsim.episim.policy;

import org.junit.Test;
import org.matsim.episim.model.FaceMask;

import static org.assertj.core.api.Assertions.assertThat;

public class RestrictionTest {


	@Test
	public void merge() {

		Restriction r = Restriction.of(0.8);

		r.merge(Restriction.ofMask(FaceMask.CLOTH, 0.5).asMap());
		r.merge(Restriction.ofCiCorrection(0.5).asMap());

		assertThat(r.getRemainingFraction()).isEqualTo(0.8);
		assertThat(r.getCiCorrection()).isEqualTo(0.5);

		assertThat(r.getMaskUsage().get(FaceMask.NONE)).isEqualTo(0.5);
		assertThat(r.getMaskUsage().get(FaceMask.CLOTH)).isEqualTo(1);

		//assertThatExceptionOfType(IllegalArgumentException.class)
		//		.isThrownBy(() -> r.merge(Restriction.ofExposure(0.4).asMap()));

	}
}

package org.matsim.episim.policy;

import org.junit.Test;
import org.matsim.episim.model.FaceMask;

import static org.assertj.core.api.Assertions.assertThat;

public class RestrictionTest {


	@Test
	public void merge() {

		Restriction r = Restriction.of(0.8);

		r.merge(Restriction.ofMask(FaceMask.CLOTH).asMap());
		r.merge(Restriction.ofExposure(0.5).asMap());

		assertThat(r.getRemainingFraction()).isEqualTo(0.8);
		assertThat(r.getExposure()).isEqualTo(0.5);
		assertThat(r.getRequireMask()).isEqualTo(FaceMask.CLOTH);

		//assertThatExceptionOfType(IllegalArgumentException.class)
		//		.isThrownBy(() -> r.merge(Restriction.ofExposure(0.4).asMap()));

	}
}

package org.matsim.episim.model;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VaccinationTypeTest {

	@Test
	public void allOptionsKeepStandardOrderAndAppendCustomTypes() {
		VaccinationType zeta = VaccinationType.of("zetaCustomVaccine");
		VaccinationType alpha = VaccinationType.of("alphaCustomVaccine");

		assertThat(VaccinationType.getAllOptions())
			.startsWith(VaccinationType.generic, VaccinationType.mRNA, VaccinationType.vector, VaccinationType.ba1Update,
				VaccinationType.ba5Update, VaccinationType.xbbUpdate, VaccinationType.natural)
			.endsWith(alpha, zeta);
	}
}

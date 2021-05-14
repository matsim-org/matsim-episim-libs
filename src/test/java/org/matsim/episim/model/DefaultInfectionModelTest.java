package org.matsim.episim.model;

import org.assertj.core.data.Offset;
import org.junit.Test;
import org.matsim.episim.*;

import static org.assertj.core.api.Assertions.assertThat;


public class DefaultInfectionModelTest {

	@Test
	public void getVaccinationEffectiveness() {

		VaccinationConfigGroup vacConfig = new VaccinationConfigGroup();
		vacConfig.setEffectiveness(0.9);

		VirusStrainConfigGroup strainConfig = new VirusStrainConfigGroup();
		VirusStrainConfigGroup.StrainParams cov2 = strainConfig.getOrAddParams(VirusStrain.SARS_CoV_2);
		cov2.setVaccineEffectiveness(1);
		cov2.setReVaccineEffectiveness(1);

		VirusStrainConfigGroup.StrainParams escape = strainConfig.getOrAddParams(VirusStrain.B1351);
		escape.setVaccineEffectiveness(0.2);
		escape.setReVaccineEffectiveness(1);

		EpisimPerson p = EpisimTestUtils.createPerson(true);

		p.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, 0);

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 1)
		).isEqualTo(1.0);

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 3)
		).isEqualTo(1.0 - 0.94 * 0.9);

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 30)
		).isCloseTo(0.1, Offset.offset(0.001));

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(escape, p, vacConfig, 30)
		).isCloseTo(0.82, Offset.offset(0.001));


		// test with re vaccination

		p.setReVaccinationStatus(EpisimPerson.VaccinationStatus.yes, 0);

		// person has full effect from first vaccine
		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 1)
		).isCloseTo(0.1, Offset.offset(0.001));

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(escape, p, vacConfig, 1)
		).isEqualTo(0.82);

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 3)
		).isCloseTo(0.1, Offset.offset(0.001));

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(escape, p, vacConfig, 3)
		).isEqualTo(1.0 - 0.94 * 0.9);

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 30)
		).isCloseTo(0.1, Offset.offset(0.001));

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(escape, p, vacConfig, 30)
		).isCloseTo(0.1, Offset.offset(0.001));

	}
}

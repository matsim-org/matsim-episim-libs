package org.matsim.episim.model;

import org.assertj.core.data.Offset;
import org.junit.Test;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.events.EpisimInfectionEvent;

import static org.assertj.core.api.Assertions.assertThat;


public class DefaultInfectionModelTest {

	@Test
	public void getVaccinationEffectiveness() {

		VaccinationConfigGroup vacConfig = new VaccinationConfigGroup();
		vacConfig.getParams(VaccinationType.generic)
				.setDaysBeforeFullEffect(42)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(4, 0)
						.atDay(5, 0.45)
						.atFullEffect(0.9))
				.setBoostEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(5, 0)
						.atFullEffect(0.9))
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B1351)
						.atDay(4, 0)
						.atDay(5, 0.1)
						.atFullEffect(0.2))
				.setBoostEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.B1351)
						.atDay(5, 0.2)
						.atFullEffect(0.9));

		VirusStrainConfigGroup strainConfig = new VirusStrainConfigGroup();
		VirusStrainConfigGroup.StrainParams cov2 = strainConfig.getOrAddParams(VirusStrain.SARS_CoV_2);
		VirusStrainConfigGroup.StrainParams escape = strainConfig.getOrAddParams(VirusStrain.B1351);


		EpisimPerson p = EpisimTestUtils.createPerson(true, -1);

		p.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.generic, 0);

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 0)
		).isEqualTo(1.0);

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 3)
		).isEqualTo(1.0);

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 5)
		).isEqualTo(1 - 0.45);

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(escape, p, vacConfig, 5)
		).isEqualTo(1.0 - 0.2 / 2);

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 30)
		).isCloseTo(0.246, Offset.offset(0.001));

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 42)
		).isCloseTo(0.1, Offset.offset(0.001));

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(escape, p, vacConfig, 42)
		).isCloseTo(0.8, Offset.offset(0.001));


		// test with re vaccination

		p.setReVaccinationStatus(EpisimPerson.VaccinationStatus.yes, 0);

		// person does not has full effect from first vaccine
		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 1)
		).isCloseTo(1, Offset.offset(0.001));

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(escape, p, vacConfig, 1)
		).isEqualTo(0.8);

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 5)
		).isCloseTo(1, Offset.offset(0.001));

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(escape, p, vacConfig, 5)
		).isEqualTo(0.8);

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(escape, p, vacConfig, 10)
		).isCloseTo(0.705, Offset.offset(0.01));

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vacConfig, 42)
		).isCloseTo(0.1, Offset.offset(0.001));

		assertThat(
				DefaultInfectionModel.getVaccinationEffectiveness(escape, p, vacConfig, 42)
		).isCloseTo(0.1, Offset.offset(0.001));

	}

	@Test
	public void immunityEffectiveness() {

		VaccinationConfigGroup vacConfig = new VaccinationConfigGroup();
		vacConfig.getOrAddParams(VaccinationType.natural)
				.setDaysBeforeFullEffect(0)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(180, 0.1)
						.atFullEffect(0.9));

		VirusStrainConfigGroup strainConfig = new VirusStrainConfigGroup();
		VirusStrainConfigGroup.StrainParams cov2 = strainConfig.getOrAddParams(VirusStrain.SARS_CoV_2);


		EpisimPerson p = EpisimTestUtils.createPerson(true, -1);

		p.possibleInfection(new EpisimInfectionEvent(0, p.getPersonId(), p.getPersonId(), null, "somewhere", 1, VirusStrain.SARS_CoV_2, 1d));
		p.checkInfection();
		p.setDiseaseStatus(1000, EpisimPerson.DiseaseStatus.recovered);
		p.setDiseaseStatus(2000, EpisimPerson.DiseaseStatus.susceptible);


		assertThat(DefaultInfectionModel.getImmunityEffectiveness(cov2, p, vacConfig, 0))
				.isCloseTo(0.1, Offset.offset(0.0001));

		assertThat(DefaultInfectionModel.getImmunityEffectiveness(cov2, p, vacConfig, 280))
				.isCloseTo(0.9, Offset.offset(0.0001));

	}

	@Test
	public void infectivity() {

		VaccinationConfigGroup vacConfig = new VaccinationConfigGroup();
		vacConfig.getOrAddParams(VaccinationType.generic)
				.setDaysBeforeFullEffect(0)
				.setInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(5, 0.5)
						.atFullEffect(0.9))
				.setBoostInfectivity(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 0.2)
						.atFullEffect(0.5)
				);

		VirusStrainConfigGroup strainConfig = new VirusStrainConfigGroup();
		VirusStrainConfigGroup.StrainParams cov2 = strainConfig.getOrAddParams(VirusStrain.SARS_CoV_2);

		EpisimPerson p = EpisimTestUtils.createPerson(true, -1);

		p.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.generic, 0);

		assertThat(DefaultInfectionModel.getVaccinationInfectivity(p, cov2, vacConfig, 5))
				.isCloseTo(0.5, Offset.offset(0.001));

		p.setReVaccinationStatus(EpisimPerson.VaccinationStatus.yes, 5);

		assertThat(DefaultInfectionModel.getVaccinationInfectivity(p, cov2, vacConfig, 10))
				.isCloseTo(0.2, Offset.offset(0.001));

	}

	@Test
	public void config() {

		VaccinationConfigGroup vaccinationConfig = new VaccinationConfigGroup();

		VirusStrainConfigGroup strainConfig = new VirusStrainConfigGroup();
		VirusStrainConfigGroup.StrainParams cov2 = strainConfig.getOrAddParams(VirusStrain.SARS_CoV_2);


		double effectivnessMRNA = 0.7;

		int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot
		vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
				.setDaysBeforeFullEffect(fullEffectMRNA)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(1, 0.0)
						.atFullEffect(effectivnessMRNA)
						.atDay(fullEffectMRNA + 5 * 365, 0.0) //10% reduction every 6 months (source: TC)
				);

		EpisimPerson p = EpisimTestUtils.createPerson(true, -1);
		p.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 0);

		Offset<Double> offset = Offset.offset(0.001);

		assertThat(DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vaccinationConfig, 1))
				.isCloseTo(1, offset);

		assertThat(DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vaccinationConfig, 5))
				.isCloseTo(0.941, offset);

		assertThat(DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vaccinationConfig, 49))
				.isCloseTo(0.3, offset);

		assertThat(DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vaccinationConfig, 180))
				.isCloseTo(0.35, offset);

		assertThat(DefaultInfectionModel.getVaccinationEffectiveness(cov2, p, vaccinationConfig, 360))
				.isCloseTo(0.419, offset);

	}
}

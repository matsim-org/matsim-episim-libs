package org.matsim.episim;

import org.assertj.core.data.Offset;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class VaccinationConfigGroupTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void compliance() {

		VaccinationConfigGroup config = new VaccinationConfigGroup();

		Map<Integer, Double> vaccinationCompliance = new HashMap<>();

		for (int i = 0; i < 18; i++) vaccinationCompliance.put(i, 0.);
		for (int i = 18; i < 120; i++) vaccinationCompliance.put(i, 0.5);

		config.setCompliancePerAge(vaccinationCompliance);


		assertThat(EpisimUtils.findValidEntry(config.getCompliancePerAge(), 1.0, 5))
				.isEqualTo(0);

		assertThat(EpisimUtils.findValidEntry(config.getCompliancePerAge(), 1.0, 18))
				.isEqualTo(0.5);

		assertThat(EpisimUtils.findValidEntry(config.getCompliancePerAge(), 1.0, 25))
				.isEqualTo(0.5);

	}

	@Test
	public void share() throws IOException {

		Config config = ConfigUtils.createConfig();

		VaccinationConfigGroup group = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

		group.setVaccinationShare(Map.of(
				LocalDate.parse("2021-02-02"), Map.of(VaccinationType.mRNA, 0.8d, VaccinationType.vector, 0.2d)
		));

		File file = tmp.newFile("tmp.xml");
		ConfigUtils.writeConfig(config, file.toString());

		Config loaded = ConfigUtils.loadConfig(file.toString());
		assertThat(ConfigUtils.addOrGetModule(loaded, VaccinationConfigGroup.class).getVaccinationShare())
				.containsKey(LocalDate.of(2021, 2, 2));

	}


	@Test
	public void prob() {

		VaccinationConfigGroup config = new VaccinationConfigGroup();

		assertThat(config.getVaccinationTypeProb(LocalDate.now()))
				.containsEntry(VaccinationType.generic, 1d)
				.containsEntry(VaccinationType.mRNA, 1d);

		config.setVaccinationShare(Map.of(
				LocalDate.parse("2021-02-02"), Map.of(VaccinationType.mRNA, 0.8d, VaccinationType.vector, 0.2d)
		));

		assertThat(config.getVaccinationTypeProb(LocalDate.parse("2021-02-02")))
				.containsEntry(VaccinationType.generic, 0d)
				.containsEntry(VaccinationType.mRNA, 0.8d)
				.containsEntry(VaccinationType.vector, 1d);

	}


	@Test
	public void parameter() throws IOException {

		Config config = ConfigUtils.createConfig();
		VaccinationConfigGroup group = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

		group.getOrAddParams(VaccinationType.generic)
				.setDaysBeforeFullEffect(30)
				.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atDay(10, 0.5)
						.atDay(20, 0.8)
						.atFullEffect(0.99)
						.atDay(100, 0.8)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.B1351)
						.atDay(0, 0.5)
						.atDay(10, 0.2)
				);

		File file = tmp.newFile("tmp.xml");
		ConfigUtils.writeConfig(config, file.toString());

		Config loaded = ConfigUtils.loadConfig(file.toString());
		VaccinationConfigGroup.VaccinationParams cmp = ConfigUtils.addOrGetModule(loaded, VaccinationConfigGroup.class).getParams(VaccinationType.generic);

		assertThat(cmp.getEffectiveness(VirusStrain.SARS_CoV_2, 10))
				.isEqualTo(0.5);

		assertThat(cmp.getEffectiveness(VirusStrain.SARS_CoV_2, 15))
				.isEqualTo(0.65);

		assertThat(cmp.getEffectiveness(VirusStrain.SARS_CoV_2, 20))
				.isEqualTo(0.8);

		assertThat(cmp.getEffectiveness(VirusStrain.SARS_CoV_2, 25))
				.isEqualTo(0.895);

		assertThat(cmp.getEffectiveness(VirusStrain.SARS_CoV_2, 30))
				.isEqualTo(0.99);

		assertThat(cmp.getEffectiveness(VirusStrain.SARS_CoV_2, 35))
				.isCloseTo(0.976, Offset.offset(0.01));

		assertThat(cmp.getEffectiveness(VirusStrain.SARS_CoV_2, 65))
				.isEqualTo(0.895);

		assertThat(cmp.getFactorShowingSymptoms(VirusStrain.B1351, 5))
				.isEqualTo(0.35);

	}
}

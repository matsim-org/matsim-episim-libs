package org.matsim.episim;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.VaccinationType;

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
}

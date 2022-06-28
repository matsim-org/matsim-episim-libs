package org.matsim.episim;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.VirusStrain;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class VirusStrainConfigGroupTest {

	@Test
	public void config() throws IOException {

		VirusStrainConfigGroup group = new VirusStrainConfigGroup();

		Config config = ConfigUtils.createConfig(group);

		File tmp = File.createTempFile("matsim", "config");
		tmp.deleteOnExit();


		group.getOrAddParams(VirusStrain.ALPHA)
				.setInfectiousness(0.5);

		ConfigUtils.writeConfig(config, tmp.toString());

		VirusStrainConfigGroup copyGroup = new VirusStrainConfigGroup();
		Config copyConfig = ConfigUtils.loadConfig(tmp.toString(), copyGroup);

		assertThat(group.getParams(VirusStrain.ALPHA).getInfectiousness())
				.isEqualTo(0.5);

	}
}

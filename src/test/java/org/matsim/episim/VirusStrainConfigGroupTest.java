package org.matsim.episim;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.Pathogen;
import org.matsim.episim.model.VirusStrain;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class VirusStrainConfigGroupTest {
	private static final String STRAIN_PARAMS = "strainParams";

	@Test
	public void config() throws IOException {

		VirusStrainConfigGroup group = new VirusStrainConfigGroup();
		assertThat(group.getVirusStrains())
			.containsExactlyElementsOf(VirusStrain.getAllStandardOptions());

		Config config = ConfigUtils.createConfig(group);

		File tmp = File.createTempFile("matsim", "config");
		tmp.deleteOnExit();


		group.getOrAddParams(VirusStrain.ALPHA)
				.setInfectiousness(0.5);
		VirusStrain custom = VirusStrain.of("CUSTOM_CONFIG_STRAIN");
		group.getOrAddParams(custom).setInfectiousness(1.25);
		Pathogen influenza = new Pathogen("Influenza");
		VirusStrain h1n1 = VirusStrain.of(influenza, "H1N1_CONFIG_STRAIN");
		group.getOrAddParams(h1n1).setInfectiousness(0.75);

		ConfigUtils.writeConfig(config, tmp.toString());

		VirusStrainConfigGroup copyGroup = new VirusStrainConfigGroup();
		ConfigUtils.loadConfig(tmp.toString(), copyGroup);

		assertThat(copyGroup.getParams(VirusStrain.ALPHA).getInfectiousness())
				.isEqualTo(0.5);
		assertThat(copyGroup.getParams(custom).getInfectiousness())
			.isEqualTo(1.25);
		assertThat(copyGroup.getParams(h1n1).getInfectiousness())
			.isEqualTo(0.75);
		assertThat(copyGroup.getParams(h1n1).getPathogen())
			.isEqualTo(influenza);
		assertThat(h1n1.getPathogen()).isEqualTo(influenza);
		assertThat(copyGroup.getVirusStrains())
			.containsAll(VirusStrain.getAllStandardOptions())
			.contains(custom, h1n1)
			.hasSize(VirusStrain.getAllStandardOptions().size() + 2);

	}

	@Test
	public void defaultsToSarsCov2WhenPathogenIsOmitted() {
		VirusStrainConfigGroup group = new VirusStrainConfigGroup();
		VirusStrainConfigGroup.StrainParams params =
			(VirusStrainConfigGroup.StrainParams) group.createParameterSet(STRAIN_PARAMS);

		params.setStrain("LEGACY_CONFIG_STRAIN");
		group.addParameterSet(params);

		assertThat(params.getStrain().getPathogen()).isSameAs(Pathogen.SARS_COV_2);
	}
}

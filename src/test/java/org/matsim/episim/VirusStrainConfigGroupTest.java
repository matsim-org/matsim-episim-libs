package org.matsim.episim;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.Pathogen;
import org.matsim.episim.model.VirusStrain;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	/**
	 * MATSim writes config modules in alphabetical order, so {@code antibodies} (and {@code episim}) reference a
	 * custom strain by name before {@code virusStrains} declares its pathogen. Loading must not depend on that order.
	 */
	@Test
	public void loadsCustomPathogenStrainReferencedBeforeVirusStrains() throws IOException {

		File tmp = File.createTempFile("matsim", "config.xml");
		tmp.deleteOnExit();

		Files.writeString(tmp.toPath(), String.join("\n",
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
			"<!DOCTYPE config SYSTEM \"http://www.matsim.org/files/dtd/config_v2.dtd\">",
			"<config>",
			"	<module name=\"antibodies\" >",
			"		<parameterset type=\"antibodyParams\" >",
			"			<param name=\"immunityEvent\" value=\"FLU_ORDER_TEST\" />",
			"			<param name=\"immunityEventKind\" value=\"strain\" />",
			"			<param name=\"initialAntibodies\" value=\"FLU_ORDER_TEST=1.0\" />",
			"		</parameterset>",
			"	</module>",
			"	<module name=\"virusStrains\" >",
			"		<parameterset type=\"strainParams\" >",
			"			<param name=\"pathogen\" value=\"influenza\" />",
			"			<param name=\"strain\" value=\"FLU_ORDER_TEST\" />",
			"		</parameterset>",
			"	</module>",
			"</config>"));

		AntibodyConfigGroup antibodies = new AntibodyConfigGroup();
		VirusStrainConfigGroup strains = new VirusStrainConfigGroup();
		ConfigUtils.loadConfig(tmp.toString(), antibodies, strains);

		VirusStrain flu = VirusStrain.of("FLU_ORDER_TEST");
		assertThat(flu.getPathogen()).isEqualTo(new Pathogen("influenza"));
		assertThat(strains.getParams(flu).getPathogen()).isEqualTo(new Pathogen("influenza"));
		assertThat(antibodies.getParams(flu).getInitialAntibodies()).containsEntry(flu, 1.0);
	}

	@Test
	public void paramsFollowPathogenDeclaredAfterCreation() {
		VirusStrainConfigGroup group = new VirusStrainConfigGroup();
		VirusStrainConfigGroup.StrainParams params = group.getOrAddParams(VirusStrain.of("LATE_DECLARED_STRAIN"));
		assertThat(params.getPathogen()).isEqualTo(Pathogen.SARS_COV_2);

		VirusStrain.of(new Pathogen("influenza"), "LATE_DECLARED_STRAIN");

		assertThat(params.getPathogen()).isEqualTo(new Pathogen("influenza"));
		assertThat(params.getPathogenName()).isEqualTo("influenza");
	}

	@Test
	public void importedStrainWithoutParamsIsRejected() {
		VirusStrainConfigGroup strains = new VirusStrainConfigGroup();
		EpisimConfigGroup episim = new EpisimConfigGroup();
		Config config = ConfigUtils.createConfig(episim, strains);

		// a misspelled name is registered leniently while reading, but has no parameter set
		episim.setInfections_pers_per_day(VirusStrain.of("DELTAA_TYPO"), Map.of(LocalDate.parse("2021-01-01"), 5));

		assertThatThrownBy(() -> strains.checkConsistency(config))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("DELTAA_TYPO");

		// placeholders without infections are fine
		episim.setInfections_pers_per_day(VirusStrain.of("DELTAA_TYPO"), Map.of(LocalDate.parse("2021-01-01"), 0));
		strains.checkConsistency(config);

		episim.setInfections_pers_per_day(VirusStrain.of("DELTAA_TYPO"), Map.of(LocalDate.parse("2021-01-01"), 5));
		strains.getOrAddParams(VirusStrain.of("DELTAA_TYPO"));
		strains.checkConsistency(config);
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

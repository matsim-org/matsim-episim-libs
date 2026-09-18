package org.matsim.episim;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.Pathogen;
import org.matsim.episim.model.VirusStrain;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.matsim.episim.EpisimTestUtils.loadConfigXml;
import static org.matsim.episim.EpisimTestUtils.xmlModule;
import static org.matsim.episim.EpisimTestUtils.xmlParam;
import static org.matsim.episim.EpisimTestUtils.xmlSet;

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
	 * MATSim writes config modules in alphabetical order, so {@code episim} references a custom strain by name before
	 * {@code virusStrains} declares its pathogen. Loading must not depend on that order.
	 */
	@Test
	public void loadsCustomPathogenStrainReferencedBeforeVirusStrains() throws IOException {

		File tmp = File.createTempFile("matsim", "config.xml");
		tmp.deleteOnExit();

		Files.writeString(tmp.toPath(), String.join("\n",
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
			"<!DOCTYPE config SYSTEM \"http://www.matsim.org/files/dtd/config_v2.dtd\">",
			"<config>",
			"	<module name=\"episim\" >",
			"		<param name=\"infectionsPerDay\" value=\"FLU_ORDER_TEST>2021-01-01=5\" />",
			"	</module>",
			"	<module name=\"virusStrains\" >",
			"		<parameterset type=\"strainParams\" >",
			"			<param name=\"pathogen\" value=\"influenza\" />",
			"			<param name=\"strain\" value=\"FLU_ORDER_TEST\" />",
			"		</parameterset>",
			"	</module>",
			"</config>"));

		EpisimConfigGroup episim = new EpisimConfigGroup();
		VirusStrainConfigGroup strains = new VirusStrainConfigGroup();
		ConfigUtils.loadConfig(tmp.toString(), episim, strains);

		VirusStrain flu = VirusStrain.of("FLU_ORDER_TEST");
		assertThat(flu.getPathogen()).isEqualTo(new Pathogen("influenza"));
		assertThat(strains.getParams(flu).getPathogen()).isEqualTo(new Pathogen("influenza"));
		assertThat(episim.getInfections_pers_per_day()).containsKey(flu);
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
	public void importedStrainWithoutParamsIsRejected() throws IOException {

		// a misspelled name in a config file is registered leniently while reading, but has no parameter set
		VirusStrainConfigGroup strains = new VirusStrainConfigGroup();
		Config config = loadConfigXml(xmlModule("episim", xmlParam("infectionsPerDay", "DELTAA_TYPO>2021-01-01=5")),
			new EpisimConfigGroup(), strains);

		assertThatThrownBy(() -> strains.checkConsistency(config))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("DELTAA_TYPO");

		// placeholders without infections are fine
		VirusStrainConfigGroup placeholder = new VirusStrainConfigGroup();
		Config placeholderConfig = loadConfigXml(xmlModule("episim", xmlParam("infectionsPerDay", "DELTAA_TYPO>2021-01-01=0")),
			new EpisimConfigGroup(), placeholder);
		placeholder.checkConsistency(placeholderConfig);

		VirusStrainConfigGroup declared = new VirusStrainConfigGroup();
		Config declaredConfig = loadConfigXml(
			xmlModule("episim", xmlParam("infectionsPerDay", "DELTAA_TYPO>2021-01-01=5")) + "\n" +
				xmlModule("virusStrains", xmlSet(STRAIN_PARAMS, xmlParam("strain", "DELTAA_TYPO"))),
			new EpisimConfigGroup(), declared);
		declared.checkConsistency(declaredConfig);
	}

	/**
	 * A strain of a pathogen without {@code pathogenParams} is rejected before the simulation starts, instead of failing
	 * at its first infection.
	 */
	@Test
	public void strainOfUnconfiguredPathogenIsRejected() throws IOException {

		String strain = xmlModule("virusStrains", xmlSet(STRAIN_PARAMS,
			xmlParam("strain", "CHECK_PATHOGEN_STRAIN"),
			xmlParam("pathogen", "checkPathogen")));

		VirusStrainConfigGroup strains = new VirusStrainConfigGroup();
		Config config = loadConfigXml(strain, strains, new PathogenConfigGroup());

		assertThatThrownBy(() -> strains.checkConsistency(config))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("CHECK_PATHOGEN_STRAIN")
			.hasMessageContaining("pathogen 'checkPathogen'")
			.hasMessageContaining("pathogenParams");

		// the pathogen group is not part of the config: its defaults only contain SARS-CoV-2
		VirusStrainConfigGroup withoutGroup = new VirusStrainConfigGroup();
		Config configWithoutGroup = loadConfigXml(strain, withoutGroup);

		assertThatThrownBy(() -> withoutGroup.checkConsistency(configWithoutGroup))
			.hasMessageContaining("pathogen 'checkPathogen'");

		VirusStrainConfigGroup configured = new VirusStrainConfigGroup();
		Config configuredConfig = loadConfigXml(strain + "\n" +
				xmlModule("pathogen", xmlSet("pathogenParams", xmlParam("pathogen", "checkPathogen"))),
			configured, new PathogenConfigGroup());
		configured.checkConsistency(configuredConfig);
	}

	/**
	 * All problems are reported at once rather than one per run: here a misspelled import together with a strain of
	 * a pathogen that has no parameters.
	 */
	@Test
	public void allProblemsAreReportedAtOnce() throws IOException {

		VirusStrainConfigGroup strains = new VirusStrainConfigGroup();
		Config config = loadConfigXml(
			xmlModule("episim", xmlParam("infectionsPerDay", "CHECK_TYPO_STRAIN>2021-01-01=5")) + "\n" +
				xmlModule("virusStrains", xmlSet(STRAIN_PARAMS,
					xmlParam("strain", "CHECK_ALL_PROBLEMS_STRAIN"),
					xmlParam("pathogen", "checkAllProblemsPathogen"))),
			new EpisimConfigGroup(), strains, new PathogenConfigGroup());

		assertThatThrownBy(() -> strains.checkConsistency(config))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("[CHECK_TYPO_STRAIN] are imported")
			.hasMessageContaining("pathogen 'checkAllProblemsPathogen'");

		// a standard strain passes without any further configuration
		VirusStrainConfigGroup standard = new VirusStrainConfigGroup();
		Config standardConfig = loadConfigXml(
			xmlModule("virusStrains", xmlSet(STRAIN_PARAMS, xmlParam("strain", "DELTA"))), standard);
		standard.checkConsistency(standardConfig);
	}

	@Test
	public void defaultsToSarsCov2WhenPathogenIsOmitted() throws IOException {

		// config files written before pathogens existed have no pathogen param
		VirusStrainConfigGroup group = new VirusStrainConfigGroup();
		loadConfigXml(xmlModule("virusStrains",
			xmlSet(STRAIN_PARAMS,
				xmlParam("strain", "LEGACY_CONFIG_STRAIN"),
				xmlParam("infectiousness", "1.5"))), group);

		VirusStrain strain = VirusStrain.of("LEGACY_CONFIG_STRAIN");
		assertThat(strain.getPathogen()).isSameAs(Pathogen.SARS_COV_2);
		assertThat(group.getParams(strain).getPathogen()).isSameAs(Pathogen.SARS_COV_2);
		assertThat(group.getParams(strain).getInfectiousness()).isEqualTo(1.5);
	}
}

package org.matsim.episim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.model.Pathogen;
import org.matsim.episim.model.Transition;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.matsim.episim.EpisimTestUtils.loadConfigXml;
import static org.matsim.episim.EpisimTestUtils.xmlModule;
import static org.matsim.episim.EpisimTestUtils.xmlParam;
import static org.matsim.episim.EpisimTestUtils.xmlSet;

public class ImmunityConfigGroupTest {

	private static final Pathogen RSV = new Pathogen("RSV_IMMUNITY_CONFIG");
	private static final VirusStrain RSV_A = VirusStrain.of(RSV, "RSV_A_IMMUNITY_CONFIG");
	private static final VaccinationType NIRSEVIMAB = VaccinationType.of("nirsevimab_IMMUNITY_CONFIG");

	@TempDir
	Path tmp;

	/**
	 * The written file is checked as text as well: the registries of strains and vaccination types are static, so a
	 * read-back in the same JVM would pass even if nothing had been written.
	 */
	@Test
	public void isWrittenAndReadBack() throws IOException {

		Config config = ConfigUtils.createConfig();
		ImmunityConfigGroup group = ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class);
		group.setOtherPathogensProtection(0.0);
		group.setCombinator(ImmunityConfigGroup.Combinator.product);
		group.getOrAddSource(RSV_A)
			.setProtection(DiseaseStatus.infectedButNotContagious, RSV_A, ProtectionCurve.parse("0>0.47|365>0.0"));
		group.getOrAddSource(NIRSEVIMAB)
			.setProtection(DiseaseStatus.seriouslySick, RSV_A, ProtectionCurve.of(0, 0.83, 180, 0.83, 210, 0));

		Path file = tmp.resolve("immunity.xml");
		ConfigUtils.writeConfig(config, file.toString());

		String written = Files.readString(file);
		assertThat(written)
			.contains("<param name=\"otherPathogensProtection\" value=\"0.0\" />")
			.contains("<param name=\"combinator\" value=\"product\" />")
			.contains("<param name=\"sourceKind\" value=\"infection\" />")
			.contains("<param name=\"sourceKind\" value=\"product\" />")
			.contains("<param name=\"curve\" value=\"0>0.47|365>0.0\" />")
			.contains("<param name=\"curve\" value=\"0>0.83|180>0.83|210>0.0\" />");

		ImmunityConfigGroup read = new ImmunityConfigGroup();
		ConfigUtils.loadConfig(file.toString(), read);

		assertThat(read.getOtherPathogensProtection()).hasValue(0.0);
		assertThat(read.getCombinator()).isEqualTo(ImmunityConfigGroup.Combinator.product);

		ImmunityConfigGroup.SourceParams infection = read.getSource(RSV_A);
		assertThat(infection.getSourceKind()).isEqualTo(ImmunityConfigGroup.SourceKind.infection);
		assertThat(infection.getProtection(DiseaseStatus.infectedButNotContagious, RSV_A))
			.isEqualTo(ProtectionCurve.parse("0>0.47|365>0.0"));
		assertThat(infection.getProtection(DiseaseStatus.seriouslySick, RSV_A)).as("not listed").isNull();

		ImmunityConfigGroup.SourceParams product = read.getSource(NIRSEVIMAB);
		assertThat(product.getSourceKind()).isEqualTo(ImmunityConfigGroup.SourceKind.product);
		assertThat(product.getProtection(DiseaseStatus.seriouslySick, RSV_A).protectionAt(200))
			.isCloseTo(0.83 * 10 / 30, org.assertj.core.api.Assertions.within(1e-12));
	}

	@Test
	public void otherPathogensProtectionIsUnsetUntilWritten() throws IOException {
		Config config = loadConfigXml(xmlModule(ImmunityConfigGroup.GROUPNAME,
			xmlParam("combinator", "min")), new ImmunityConfigGroup());

		assertThat(ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class).getOtherPathogensProtection()).isEmpty();
	}

	@Test
	public void modelDefaultsToLegacyAndIsWrittenAndRead() throws IOException {
		assertThat(new ImmunityConfigGroup().getModel()).isEqualTo(ImmunityConfigGroup.Model.legacyCovid);

		Config config = ConfigUtils.createConfig();
		ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class).setModel(ImmunityConfigGroup.Model.explicit);

		Path file = tmp.resolve("model.xml");
		ConfigUtils.writeConfig(config, file.toString());
		assertThat(Files.readString(file)).contains("<param name=\"model\" value=\"explicit\" />");

		ImmunityConfigGroup read = new ImmunityConfigGroup();
		ConfigUtils.loadConfig(file.toString(), read);
		assertThat(read.getModel()).isEqualTo(ImmunityConfigGroup.Model.explicit);
	}

	/**
	 * With the legacy model the group is not read, so it is not checked either; with the explicit model it is.
	 */
	@Test
	public void checkDependsOnTheModel() throws IOException {
		String incomplete = xmlSet("immunitySource",
			xmlParam("source", "RSV_CHECK_MODEL"), xmlParam("sourceKind", "infection"),
			xmlSet("protection", xmlParam("against", "critical"), xmlParam("strain", "RSV_CHECK_MODEL")));

		ImmunityConfigGroup legacy = new ImmunityConfigGroup();
		Config legacyConfig = loadConfigXml(xmlModule(ImmunityConfigGroup.GROUPNAME, incomplete), legacy);
		legacy.checkConsistency(legacyConfig);

		ImmunityConfigGroup explicit = new ImmunityConfigGroup();
		Config explicitConfig = loadConfigXml(xmlModule(ImmunityConfigGroup.GROUPNAME,
			xmlParam("model", "explicit"), incomplete), explicit);
		assertThatThrownBy(() -> explicit.checkConsistency(explicitConfig))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("has no 'curve'");
	}

	/**
	 * An unset value is written as the literal "null" by MATSim and has to come back unset, not as an error or 0.
	 */
	@Test
	public void unsetOtherPathogensProtectionSurvivesWriteAndRead() throws IOException {
		Config config = ConfigUtils.createConfig();
		ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class);

		Path file = tmp.resolve("unset.xml");
		ConfigUtils.writeConfig(config, file.toString());

		ImmunityConfigGroup read = new ImmunityConfigGroup();
		ConfigUtils.loadConfig(file.toString(), read);

		assertThat(read.getOtherPathogensProtection()).isEmpty();
		assertThat(read.getCombinator()).isEqualTo(ImmunityConfigGroup.Combinator.min);
	}

	/**
	 * MATSim writes modules in alphabetical order, so {@code immunity} names a custom strain before
	 * {@code virusStrains} declares its pathogen. The strain is resolved on access and picks up the pathogen.
	 */
	@Test
	public void strainReferencedBeforeVirusStrainsGetsItsPathogen() throws IOException {
		Config config = loadConfigXml(String.join("\n",
				xmlModule(ImmunityConfigGroup.GROUPNAME,
					xmlSet("immunitySource",
						xmlParam("source", "FLU_IMMUNITY_ORDER"),
						xmlParam("sourceKind", "infection"),
						xmlSet("protection",
							xmlParam("against", "seriouslySick"),
							xmlParam("strain", "FLU_IMMUNITY_ORDER"),
							xmlParam("curve", "0>0.5")))),
				xmlModule("virusStrains",
					xmlSet("strainParams",
						xmlParam("strain", "FLU_IMMUNITY_ORDER"),
						xmlParam("pathogen", "INFLUENZA_IMMUNITY_ORDER")))),
			new ImmunityConfigGroup(), new VirusStrainConfigGroup());

		VirusStrain flu = VirusStrain.of("FLU_IMMUNITY_ORDER");
		ImmunityConfigGroup.SourceParams source = ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class).getSource(flu);

		assertThat(source.getSource().toString()).isEqualTo("FLU_IMMUNITY_ORDER");
		assertThat(source.getProtections().get(0).getStrain().getPathogen()).isEqualTo(new Pathogen("INFLUENZA_IMMUNITY_ORDER"));
	}

	@Test
	public void rejectsWhatCannotBeRight() {
		ImmunityConfigGroup group = new ImmunityConfigGroup();
		ImmunityConfigGroup.SourceParams source = group.getOrAddSource(RSV_A);

		assertThatThrownBy(() -> source.setProtection(DiseaseStatus.deceased, RSV_A, ProtectionCurve.NONE))
			.as("deceased is not supported yet").hasMessageContaining("not supported");
		assertThatThrownBy(() -> group.setOtherPathogensProtection(1.5))
			.hasMessageContaining("between 0 and 1");
		assertThatThrownBy(() -> group.addParameterSet(sourceNamed(RSV_A.toString(), "infection")))
			.as("one source, one set").hasMessageContaining("configured twice");
	}

	@Test
	public void rejectsDuplicatesAndBadValuesWhileReading() {
		String duplicateProtection = xmlModule(ImmunityConfigGroup.GROUPNAME,
			xmlSet("immunitySource",
				xmlParam("source", "RSV_DUPLICATE"), xmlParam("sourceKind", "infection"),
				xmlSet("protection", xmlParam("against", "critical"), xmlParam("strain", "RSV_DUPLICATE"), xmlParam("curve", "0>0.1")),
				xmlSet("protection", xmlParam("against", "critical"), xmlParam("strain", "RSV_DUPLICATE"), xmlParam("curve", "0>0.2"))));
		assertThatThrownBy(() -> loadConfigXml(duplicateProtection, new ImmunityConfigGroup()))
			.hasStackTraceContaining("twice");

		String badCurve = xmlModule(ImmunityConfigGroup.GROUPNAME,
			xmlSet("immunitySource",
				xmlParam("source", "RSV_BAD_CURVE"), xmlParam("sourceKind", "infection"),
				xmlSet("protection", xmlParam("against", "critical"), xmlParam("strain", "RSV_BAD_CURVE"), xmlParam("curve", "3>0.1"))));
		assertThatThrownBy(() -> loadConfigXml(badCurve, new ImmunityConfigGroup()))
			.as("the message names the protection, not just the curve")
			.hasStackTraceContaining("Protection against critical for strain 'RSV_BAD_CURVE'")
			.hasStackTraceContaining("has to start at day 0");

		String unknownKind = xmlModule(ImmunityConfigGroup.GROUPNAME,
			xmlSet("immunitySource", xmlParam("source", "X"), xmlParam("sourceKind", "vaccine")));
		assertThatThrownBy(() -> loadConfigXml(unknownKind, new ImmunityConfigGroup()))
			.hasStackTraceContaining("'sourceKind' has no value 'vaccine'; allowed are [infection, product]");
	}

	private static ImmunityConfigGroup.SourceParams sourceNamed(String name, String kind) {
		ImmunityConfigGroup.SourceParams p = (ImmunityConfigGroup.SourceParams)
			new ImmunityConfigGroup().createParameterSet("immunitySource");
		p.setSourceName(name);
		p.setSourceKindString(kind);
		return p;
	}

	/**
	 * Config with the given strains configured; the ones in {@code imported} circulate. SARS-CoV-2 is always configured
	 * by {@link VirusStrainConfigGroup} but not imported here.
	 */
	private static Config coverageConfig(VirusStrain[] configured, VirusStrain... imported) {
		Config config = ConfigUtils.createConfig(new EpisimConfigGroup(), new VirusStrainConfigGroup(), new ImmunityConfigGroup());
		VirusStrainConfigGroup strains = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		for (VirusStrain strain : configured)
			strains.getOrAddParams(strain);
		EpisimConfigGroup episim = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		for (VirusStrain strain : imported)
			episim.setInfections_pers_per_day(strain, Map.of(LocalDate.parse("2022-09-26"), 3));
		ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class).setModel(ImmunityConfigGroup.Model.explicit);
		refractoryPeriod(config);
		return config;
	}

	/** Without a way back to susceptible the curves of an infection could never be applied. */
	private static void refractoryPeriod(Config config) {
		ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class).setProgressionConfig(Transition.config()
			.from(DiseaseStatus.recovered, Transition.to(DiseaseStatus.susceptible, Transition.fixed(7)))
			.build());
	}

	/** An infection source with a curve against every supported target for the given strains. */
	private static void fullSource(ImmunityConfigGroup group, VirusStrain source, VirusStrain... targets) {
		ImmunityConfigGroup.SourceParams p = group.getOrAddSource(source);
		for (VirusStrain target : targets)
			for (DiseaseStatus against : ImmunityConfigGroup.SUPPORTED_TARGETS)
				p.setProtection(against, target, ProtectionCurve.parse("0>0.3"));
	}

	private static void check(Config config) {
		ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class).checkConsistency(config);
	}

	@Test
	public void completeSinglePathogenConfigPassesWithoutPlaceholderForSarsCov2() {
		VirusStrain rsv = VirusStrain.of(new Pathogen("RSV_COVER_OK"), "RSV_COVER_OK");
		Config config = coverageConfig(new VirusStrain[]{rsv}, rsv);
		fullSource(ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class), rsv, rsv);

		// SARS-CoV-2 is configured by default but not imported, so no source is required for it
		check(config);
	}

	@Test
	public void everyTargetOfTheOwnPathogenHasToBeListed() {
		VirusStrain rsv = VirusStrain.of(new Pathogen("RSV_COVER_MISSING"), "RSV_COVER_MISSING");
		Config config = coverageConfig(new VirusStrain[]{rsv}, rsv);
		ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class).getOrAddSource(rsv)
			.setProtection(DiseaseStatus.infectedButNotContagious, rsv, ProtectionCurve.parse("0>0.47"));

		assertThatThrownBy(() -> check(config))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("no protection against showingSymptoms for its strain 'RSV_COVER_MISSING'")
			.hasMessageContaining("no protection against seriouslySick")
			.hasMessageContaining("no protection against critical")
			.hasMessageContaining("write curve '0>0.0'");
	}

	@Test
	public void circulatingStrainNeedsAnInfectionSource() {
		VirusStrain rsv = VirusStrain.of(new Pathogen("RSV_COVER_NOSOURCE"), "RSV_COVER_NOSOURCE");
		Config config = coverageConfig(new VirusStrain[]{rsv}, rsv);

		assertThatThrownBy(() -> check(config))
			.hasMessageContaining("Strain 'RSV_COVER_NOSOURCE' circulates, but there is no 'immunitySource'");
	}

	/**
	 * With two circulating pathogens the pairs across them are covered by one explicit value, not pair by pair.
	 */
	@Test
	public void otherPathogensNeedTheExplicitDefault() {
		VirusStrain rsv = VirusStrain.of(new Pathogen("RSV_COVER_TWO"), "RSV_COVER_TWO");
		VirusStrain flu = VirusStrain.of(new Pathogen("FLU_COVER_TWO"), "FLU_COVER_TWO");
		Config config = coverageConfig(new VirusStrain[]{rsv, flu}, rsv, flu);
		ImmunityConfigGroup group = ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class);
		fullSource(group, rsv, rsv);
		fullSource(group, flu, flu);

		assertThatThrownBy(() -> check(config))
			.hasMessageContaining("RSV_COVER_TWO -> FLU_COVER_TWO")
			.hasMessageContaining("FLU_COVER_TWO -> RSV_COVER_TWO")
			.hasMessageContaining("'otherPathogensProtection' is not set");

		group.setOtherPathogensProtection(0.0);
		check(config);
	}

	/**
	 * A product covers the pathogens it mentions, and then all of their targets.
	 */
	@Test
	public void productCoversThePathogensItMentions() {
		VirusStrain rsv = VirusStrain.of(new Pathogen("RSV_COVER_PRODUCT"), "RSV_COVER_PRODUCT");
		Config config = coverageConfig(new VirusStrain[]{rsv}, rsv);
		ImmunityConfigGroup group = ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class);
		fullSource(group, rsv, rsv);
		group.getOrAddSource(VaccinationType.of("nirsevimab_COVER_PRODUCT"))
			.setProtection(DiseaseStatus.seriouslySick, rsv, ProtectionCurve.of(0, 0.83, 180, 0.83, 210, 0));

		assertThatThrownBy(() -> check(config))
			.hasMessageContaining("'nirsevimab_COVER_PRODUCT' covers pathogen 'RSV_COVER_PRODUCT' but has no protection against infectedButNotContagious");
	}

	@Test
	public void unknownStrainNamesAreReported() {
		VirusStrain rsv = VirusStrain.of(new Pathogen("RSV_COVER_TYPO"), "RSV_COVER_TYPO");
		Config config = coverageConfig(new VirusStrain[]{rsv}, rsv);
		ImmunityConfigGroup group = ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class);
		fullSource(group, rsv, rsv);
		group.getOrAddSource(rsv).setProtection(DiseaseStatus.critical, VirusStrain.of("RSV_COVER_TYPPO"), ProtectionCurve.NONE);

		assertThatThrownBy(() -> check(config))
			.hasMessageContaining("strain 'RSV_COVER_TYPPO', which has no 'strainParams'");
	}

	@Test
	public void protectionLeftByAnInfectionNeedsAWayBackToSusceptible() {
		VirusStrain flu = VirusStrain.of(new Pathogen("FLU_REFRACTORY"), "FLU_REFRACTORY");
		Config config = coverageConfig(new VirusStrain[]{flu}, flu);
		fullSource(ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class), flu, flu);

		// the scenario describes immunity with curves but keeps agents in recovered forever
		ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class).setProgressionConfig(Transition.config()
			.from(DiseaseStatus.contagious, Transition.to(DiseaseStatus.recovered, Transition.fixed(4)))
			.build());

		assertThatThrownBy(() -> check(config))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("FLU_REFRACTORY")
			.hasMessageContaining("never become susceptible again");
	}

	@Test
	public void infectionsWithoutProtectionDoNotNeedTheTransition() {
		VirusStrain flu = VirusStrain.of(new Pathogen("FLU_REFRACTORY_ZERO"), "FLU_REFRACTORY_ZERO");
		Config config = coverageConfig(new VirusStrain[]{flu}, flu);
		ImmunityConfigGroup immunity = ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class);
		for (DiseaseStatus against : ImmunityConfigGroup.SUPPORTED_TARGETS)
			immunity.getOrAddSource(flu).setProtection(against, flu, ProtectionCurve.parse("0>0.0|400>0.0"));

		ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class).setProgressionConfig(Transition.config()
			.from(DiseaseStatus.contagious, Transition.to(DiseaseStatus.recovered, Transition.fixed(4)))
			.build());

		// immunity is the recovered state itself here, which is the legacy way and stays allowed
		check(config);
	}

	@Test
	public void productCurvesDoNotNeedTheTransition() {
		VirusStrain flu = VirusStrain.of(new Pathogen("FLU_REFRACTORY_PRODUCT"), "FLU_REFRACTORY_PRODUCT");
		Config config = coverageConfig(new VirusStrain[]{flu}, flu);
		ImmunityConfigGroup immunity = ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class);
		for (DiseaseStatus against : ImmunityConfigGroup.SUPPORTED_TARGETS) {
			immunity.getOrAddSource(flu).setProtection(against, flu, ProtectionCurve.NONE);
			immunity.getOrAddSource(VaccinationType.of("FLU_SHOT_REFRACTORY"))
				.setProtection(against, flu, ProtectionCurve.parse("0>0.6|180>0.2"));
		}

		ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class).setProgressionConfig(Transition.config()
			.from(DiseaseStatus.contagious, Transition.to(DiseaseStatus.recovered, Transition.fixed(4)))
			.build());

		// a vaccine protects agents who have not been infected yet, so it works without re-infection
		check(config);
	}

}

package org.matsim.episim;

import org.assertj.core.data.Offset;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.Pathogen;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.matsim.episim.EpisimTestUtils.loadConfigXml;
import static org.matsim.episim.EpisimTestUtils.xmlModule;
import static org.matsim.episim.EpisimTestUtils.xmlParam;
import static org.matsim.episim.EpisimTestUtils.xmlSet;

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

	@Test
	public void validVaccination() {

		Config config = ConfigUtils.createConfig();
		VaccinationConfigGroup group = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

		group.getOrAddParams(VaccinationType.mRNA)
						.setDaysBeforeFullEffect(40);

		group.setDaysValid(90);
		group.setValidDeadline(LocalDate.parse("2022-09-01"));

		LocalDate date = LocalDate.parse("2022-01-01");

		EpisimPerson p = EpisimTestUtils.createPerson(true, 20);

		assertThat(group.hasValidVaccination(p, 0, date))
				.isEqualTo(false);

		p.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 0);

		assertThat(group.hasValidVaccination(p, 10, date.plusDays(10)))
				.isEqualTo(false);

		assertThat(group.hasValidVaccination(p, 41, date.plusDays(41)))
				.isEqualTo(true);


		p.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 50);

		assertThat(group.hasValidVaccination(p, 55, date.plusDays(55)))
				.isEqualTo(true);

		assertThat(group.hasValidVaccination(p, 140, date.plusDays(140)))
				.isEqualTo(true);

		assertThat(group.hasValidVaccination(p, 300, date.plusDays(300)))
				.isEqualTo(false);

	}

	/**
	 * A natural-immunity type carries the strain whose infection induced it, so the source has to survive a write and
	 * a read. The write is asserted on the file itself: the static {@link VaccinationType} registry already knows the
	 * type in this JVM, so a read-back alone would pass even if nothing had been written.
	 */
	@Test
	public void naturalVaccinationSourceIsWrittenAndRead() throws IOException {

		Pathogen rsv = new Pathogen("RSV_VAC_CONFIG");
		VirusStrain rsvStrain = VirusStrain.of(rsv, "RSV_VAC_CONFIG_STRAIN");
		VaccinationType naturalRsv = VaccinationType.naturalFor(rsvStrain);

		Config config = ConfigUtils.createConfig();
		VaccinationConfigGroup group = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

		group.getOrAddParams(naturalRsv).setDaysBeforeFullEffect(7);
		group.getOrAddParams(VaccinationType.mRNA).setDaysBeforeFullEffect(28);

		File file = tmp.newFile("natural-source.xml");
		ConfigUtils.writeConfig(config, file.toString());

		String written = Files.readString(file.toPath());
		assertThat(written)
			.as("source strain is written for a natural type")
			.contains("<param name=\"sourceOfNaturalVaccination\" value=\"RSV_VAC_CONFIG_STRAIN\" />");

		VaccinationConfigGroup copy = new VaccinationConfigGroup();
		ConfigUtils.loadConfig(file.toString(), copy);

		VaccinationConfigGroup.VaccinationParams read = copy.getParams(naturalRsv);
		assertThat(read.getDaysBeforeFullEffect()).isEqualTo(7);
		assertThat(read.getType().isNaturalVaccination()).isTrue();
		assertThat(read.getType().getSourceOfNaturalVaccination()).isEqualTo(rsvStrain);

		// MATSim writes the absent source as the literal "null"; reading it back must not invent a strain of that name
		VaccinationConfigGroup.VaccinationParams mRNA = copy.getParams(VaccinationType.mRNA);
		assertThat(mRNA.getType().isNaturalVaccination()).isFalse();
		assertThat(mRNA.getType().getSourceOfNaturalVaccination()).isNull();
	}

	/**
	 * Reading has to declare the source on a type that was never mentioned in code, otherwise a config file is not a
	 * self-contained description of the run.
	 */
	@Test
	public void readingDeclaresSourceOfUnknownNaturalType() throws IOException {

		Config config = loadConfigXml(xmlModule("episimVaccination",
				xmlSet("vaccinationParams",
						xmlParam("type", "natural_UNSEEN_PATHOGEN_UNSEEN_STRAIN"),
						xmlParam("sourceOfNaturalVaccination", "UNSEEN_STRAIN"),
						xmlParam("daysBeforeFullEffect", "3"))),
			new VaccinationConfigGroup());

		VaccinationConfigGroup group = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		VaccinationType type = VaccinationType.of("natural_UNSEEN_PATHOGEN_UNSEEN_STRAIN");

		assertThat(type.isNaturalVaccination())
			.as("source declared while reading the config")
			.isTrue();
		assertThat(type.getSourceOfNaturalVaccination()).isEqualTo(VirusStrain.of("UNSEEN_STRAIN"));
		assertThat(VaccinationType.naturalFor(VirusStrain.of("UNSEEN_STRAIN"))).isEqualTo(type);
		assertThat(group.getParams(type).getDaysBeforeFullEffect()).isEqualTo(3);
	}

	/**
	 * MATSim writes config modules in alphabetical order, so {@code vaccination} is read before {@code virusStrains}
	 * declares the pathogen of a custom strain. Loading must not depend on that order.
	 */
	@Test
	public void loadsNaturalSourceReferencedBeforeVirusStrains() throws IOException {

		Pathogen influenza = new Pathogen("INFLUENZA_VAC_ORDER");

		Config config = loadConfigXml(String.join("\n",
				xmlModule("episimVaccination",
						xmlSet("vaccinationParams",
								xmlParam("type", "natural_INFLUENZA_VAC_ORDER_H3N2_VAC_ORDER"),
								xmlParam("sourceOfNaturalVaccination", "H3N2_VAC_ORDER"))),
				xmlModule("virusStrains",
						xmlSet("strainParams",
								xmlParam("strain", "H3N2_VAC_ORDER"),
								xmlParam("pathogen", "INFLUENZA_VAC_ORDER")))),
			new VaccinationConfigGroup(), new VirusStrainConfigGroup());

		VirusStrain strain = VirusStrain.of("H3N2_VAC_ORDER");
		assertThat(strain.getPathogen()).isEqualTo(influenza);

		VaccinationType type = VaccinationType.of("natural_INFLUENZA_VAC_ORDER_H3N2_VAC_ORDER");
		assertThat(type.getSourceOfNaturalVaccination()).isEqualTo(strain);
		assertThat(VaccinationType.naturalFor(strain)).isEqualTo(type);

		VaccinationConfigGroup group = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		assertThat(group.getParams(type).getType().getSourceOfNaturalVaccination().getPathogen())
			.isEqualTo(influenza);
	}

	/**
	 * An empty value must not become a strain with an empty name, which would silently turn a real vaccination into a
	 * natural one.
	 */
	@Test
	public void emptySourceIsNotAStrain() throws IOException {

		Config config = loadConfigXml(xmlModule("episimVaccination",
				xmlSet("vaccinationParams",
						xmlParam("type", "EMPTY_SOURCE_VAC"),
						xmlParam("sourceOfNaturalVaccination", ""))),
			new VaccinationConfigGroup());

		ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

		VaccinationType type = VaccinationType.of("EMPTY_SOURCE_VAC");
		assertThat(type.isNaturalVaccination()).isFalse();
		assertThat(type.getSourceOfNaturalVaccination()).isNull();
	}
}

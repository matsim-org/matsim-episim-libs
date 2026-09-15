package org.matsim.episim;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.AntibodyConfigGroup.AntibodyParams;
import org.matsim.episim.model.ImmunityEvent;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.MapEntry.entry;
import static org.matsim.episim.EpisimTestUtils.loadConfigXml;
import static org.matsim.episim.EpisimTestUtils.xmlModule;
import static org.matsim.episim.EpisimTestUtils.xmlParam;
import static org.matsim.episim.EpisimTestUtils.xmlSet;

/**
 * Tests for {@link AntibodyConfigGroup}. The config group is expected to reproduce the antibody default that
 * used to live in {@code AntibodyModel.Config} before it was replaced by this config group, and to survive an
 * XML round-trip.
 */
public class AntibodyConfigGroupTest {

	private static final double MUT_ESC_DELTA = 29.2 / 10.9;
	private static final double MUT_ESC_BA1 = 10.9 / 1.9;
	private static final double MUT_ESC_BA5 = 2.9;

	/**
	 * (1) The default constructor registers exactly the initial-antibody and refresh-factor values that the
	 * pre-refactor {@code AntibodyModel.Config} produced. {@link #legacyInitialAntibodies()} /
	 * {@link #legacyAntibodyRefreshFactors()} are a frozen copy of that constructor and act as an independent
	 * oracle.
	 */
	@Test
	public void defaultMatchesLegacyAntibodyConfig() {

		AntibodyConfigGroup group = new AntibodyConfigGroup();

		assertThat(group.getImmuneResponseSigma()).isEqualTo(0.0);
		assertThat(group.getHlMultiplierForInfected()).isEqualTo(1.0);

		assertThat(group.getInitialAntibodies()).isEqualTo(legacyInitialAntibodies());
		assertThat(group.getAntibodyRefreshFactors()).isEqualTo(legacyAntibodyRefreshFactors());
	}

	/**
	 * (2) The default is keyed by every {@link VaccinationType} plus every standard {@link VirusStrain}, with
	 * both maps sharing the same key set.
	 */
	@Test
	public void defaultCoversEveryImmunityEvent() {

		AntibodyConfigGroup group = new AntibodyConfigGroup();

		Set<ImmunityEvent> expected = new LinkedHashSet<>();
		expected.addAll(VaccinationType.getAllStandardOptions());
		expected.addAll(VirusStrain.getAllStandardOptions());

		assertThat(group.getInitialAntibodies().keySet()).containsExactlyInAnyOrderElementsOf(expected);
		assertThat(group.getAntibodyRefreshFactors().keySet()).containsExactlyInAnyOrderElementsOf(expected);

		for (ImmunityEvent e : expected) {
			assertThat(group.getParams(e).getInitialAntibodies().keySet())
					.containsExactlyInAnyOrderElementsOf(VirusStrain.getAllStandardOptions());
			assertThat(group.getParams(e).getAntibodyRefreshFactors().keySet())
					.containsExactlyInAnyOrderElementsOf(VirusStrain.getAllStandardOptions());
		}
	}

	/**
	 * (3) Spot-check of representative default values: the type-wide loop values, the strain-specific
	 * overrides and the {@code NaN} refresh factors.
	 */
	@Test
	public void defaultSpotChecks() {

		AntibodyConfigGroup group = new AntibodyConfigGroup();

		// type-wide loop values (a strain without a specific override)
		assertThat(group.getParams(VaccinationType.mRNA).getInitialAntibodies().get(VirusStrain.B1351)).isEqualTo(29.2);
		assertThat(group.getParams(VaccinationType.vector).getInitialAntibodies().get(VirusStrain.B1351)).isEqualTo(6.8);
		assertThat(group.getParams(VaccinationType.generic).getInitialAntibodies().get(VirusStrain.B1351)).isEqualTo(5.0);
		assertThat(group.getParams(VirusStrain.SARS_CoV_2).getInitialAntibodies().get(VirusStrain.B1351)).isEqualTo(5.0);

		// wildtype overrides
		double mRNAAlpha = 29.2;
		assertThat(group.getParams(VaccinationType.mRNA).getInitialAntibodies().get(VirusStrain.SARS_CoV_2)).isEqualTo(mRNAAlpha);
		assertThat(group.getParams(VaccinationType.vector).getInitialAntibodies().get(VirusStrain.SARS_CoV_2)).isEqualTo(mRNAAlpha * 210. / 700.);
		assertThat(group.getParams(VirusStrain.SARS_CoV_2).getInitialAntibodies().get(VirusStrain.SARS_CoV_2)).isEqualTo(mRNAAlpha * 300. / 700.);
		assertThat(group.getParams(VirusStrain.OMICRON_BA1).getInitialAntibodies().get(VirusStrain.SARS_CoV_2)).isEqualTo(0.01);
		assertThat(group.getParams(VaccinationType.ba5Update).getInitialAntibodies().get(VirusStrain.SARS_CoV_2))
				.isEqualTo(mRNAAlpha / MUT_ESC_DELTA / MUT_ESC_BA1 / MUT_ESC_BA5);

		// delta / ba.1 / ba.5 override blocks
		double mRNADelta = mRNAAlpha / MUT_ESC_DELTA;
		assertThat(group.getParams(VaccinationType.mRNA).getInitialAntibodies().get(VirusStrain.DELTA)).isEqualTo(mRNADelta);
		assertThat(group.getParams(VirusStrain.DELTA).getInitialAntibodies().get(VirusStrain.DELTA)).isEqualTo(mRNADelta * 450. / 300.);
		assertThat(group.getParams(VaccinationType.ba1Update).getInitialAntibodies().get(VirusStrain.OMICRON_BA1)).isEqualTo(mRNAAlpha);
		assertThat(group.getParams(VaccinationType.ba5Update).getInitialAntibodies().get(VirusStrain.OMICRON_BA5)).isEqualTo(mRNAAlpha);
		assertThat(group.getParams(VirusStrain.OMICRON_BA1).getInitialAntibodies().get(VirusStrain.OMICRON_BA1)).isEqualTo(64.0 / 300.);
		assertThat(group.getParams(VirusStrain.OMICRON_BA5).getInitialAntibodies().get(VirusStrain.OMICRON_BA5)).isEqualTo(64.0 / 300.);

		// refresh factors: one per branch of the type switch, plus the strain-keyed constant
		assertThat(group.getParams(VaccinationType.mRNA).getAntibodyRefreshFactors().get(VirusStrain.SARS_CoV_2)).isEqualTo(15.0);
		assertThat(group.getParams(VaccinationType.vector).getAntibodyRefreshFactors().get(VirusStrain.SARS_CoV_2)).isEqualTo(5.0);
		assertThat(group.getParams(VaccinationType.ba1Update).getAntibodyRefreshFactors().get(VirusStrain.SARS_CoV_2)).isEqualTo(15.0);
		assertThat(group.getParams(VaccinationType.ba5Update).getAntibodyRefreshFactors().get(VirusStrain.SARS_CoV_2)).isEqualTo(15.0);
		assertThat(group.getParams(VaccinationType.generic).getAntibodyRefreshFactors().get(VirusStrain.SARS_CoV_2)).isNaN();
		assertThat(group.getParams(VaccinationType.natural).getAntibodyRefreshFactors().get(VirusStrain.SARS_CoV_2)).isNaN();
		assertThat(group.getParams(VaccinationType.xbbUpdate).getAntibodyRefreshFactors().get(VirusStrain.SARS_CoV_2)).isNaN();
		assertThat(group.getParams(VirusStrain.DELTA).getAntibodyRefreshFactors().get(VirusStrain.ALPHA)).isEqualTo(15.0);
	}

	/**
	 * (4) The default configuration survives an XML round-trip without loss, including the {@code NaN} refresh
	 * factors and the top-level scalars.
	 */
	@Test
	public void defaultSurvivesXmlRoundTrip() throws IOException {

		AntibodyConfigGroup group = new AntibodyConfigGroup();
		group.setImmuneResponseSigma(0.5);
		group.setHlMultiplierForInfected(1.5);

		Config config = ConfigUtils.createConfig(group);

		File tmp = File.createTempFile("matsim", "config");
		tmp.deleteOnExit();
		ConfigUtils.writeConfig(config, tmp.toString());

		AntibodyConfigGroup copy = new AntibodyConfigGroup();
		ConfigUtils.loadConfig(tmp.toString(), copy);

		assertThat(copy.getImmuneResponseSigma()).isEqualTo(0.5);
		assertThat(copy.getHlMultiplierForInfected()).isEqualTo(1.5);

		assertThat(copy.getInitialAntibodies()).isEqualTo(legacyInitialAntibodies());
		assertThat(copy.getAntibodyRefreshFactors()).isEqualTo(legacyAntibodyRefreshFactors());

		assertThat(copy.getParams(VaccinationType.mRNA).getInitialAntibodies().get(VirusStrain.SARS_CoV_2)).isEqualTo(29.2);
		assertThat(copy.getParams(VaccinationType.generic).getAntibodyRefreshFactors().get(VirusStrain.SARS_CoV_2)).isNaN();

		// loaded sets replace the auto-added defaults instead of being appended as duplicates
		assertThat(copy.getParameterSets(AntibodyParams.SET_TYPE)).hasSameSizeAs(group.getParameterSets(AntibodyParams.SET_TYPE));
	}

	/**
	 * (5) A custom immunity event and custom values can be added through configuration alone and survive an
	 * XML round-trip, without disturbing the defaults.
	 */
	@Test
	public void customImmunityEventCanBeAddedAndRoundTripped() throws IOException {

		AntibodyConfigGroup group = new AntibodyConfigGroup();
		Config config = ConfigUtils.createConfig(group);

		// override an existing default entry
		group.getParams(VaccinationType.mRNA).getInitialAntibodies().put(VirusStrain.SARS_CoV_2, 42.0);

		// brand-new immunity event
		VirusStrain custom = VirusStrain.of("CUSTOM_ANTIBODY_STRAIN");
		assertThat(group.hasParams(custom)).isFalse();
		group.getOrAddParams(custom)
				.setInitialAntibodies(Map.of(VirusStrain.SARS_CoV_2, 3.3, VirusStrain.DELTA, 1.1))
				.setAntibodyRefreshFactors(Map.of(VirusStrain.SARS_CoV_2, 7.0));
		assertThat(group.hasParams(custom)).isTrue();

		File tmp = File.createTempFile("matsim", "config");
		tmp.deleteOnExit();
		ConfigUtils.writeConfig(config, tmp.toString());

		AntibodyConfigGroup copy = new AntibodyConfigGroup();
		ConfigUtils.loadConfig(tmp.toString(), copy);

		assertThat(copy.getParams(VaccinationType.mRNA).getInitialAntibodies().get(VirusStrain.SARS_CoV_2)).isEqualTo(42.0);

		AntibodyParams reloaded = copy.getParams(custom);
		assertThat(reloaded.getImmunityEvent()).isEqualTo(custom);
		assertThat(reloaded.getInitialAntibodies()).containsOnly(
				entry(VirusStrain.SARS_CoV_2, 3.3),
				entry(VirusStrain.DELTA, 1.1));
		assertThat(reloaded.getAntibodyRefreshFactors()).containsOnly(
				entry(VirusStrain.SARS_CoV_2, 7.0));

		// untouched default is still there
		assertThat(copy.getParams(VaccinationType.vector).getInitialAntibodies().get(VirusStrain.B1351)).isEqualTo(6.8);
	}

	/**
	 * (6) A vaccination type used in the vaccination config without antibody params is rejected before the simulation
	 * starts, instead of failing when the first person is vaccinated with it.
	 */
	@Test
	public void vaccinationTypeWithoutParamsIsRejected() throws IOException {

		String vaccinations = xmlModule("episimVaccination",
				xmlParam("vaccinationShare", "2021-01-01>CHECK_SHARE_VACCINE=0.5;mRNA=0.5"),
				xmlSet("vaccinationParams", xmlParam("type", "CHECK_PARAMS_VACCINE")));

		AntibodyConfigGroup antibodies = new AntibodyConfigGroup();
		Config config = loadConfigXml(vaccinations, antibodies, new VaccinationConfigGroup());

		assertThatThrownBy(() -> antibodies.checkConsistency(config))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("[CHECK_PARAMS_VACCINE, CHECK_SHARE_VACCINE]")
				.hasMessageContaining("antibodyParams");

		AntibodyConfigGroup configured = new AntibodyConfigGroup();
		Config configuredConfig = loadConfigXml(vaccinations + "\n" + xmlModule("antibodies",
						vaccinationAntibodyParams("CHECK_SHARE_VACCINE"),
						vaccinationAntibodyParams("CHECK_PARAMS_VACCINE")),
				configured, new VaccinationConfigGroup());
		configured.checkConsistency(configuredConfig);

		// standard vaccination types are covered by the defaults
		AntibodyConfigGroup defaults = new AntibodyConfigGroup();
		defaults.checkConsistency(ConfigUtils.createConfig(defaults, new VaccinationConfigGroup()));
	}

	private static String vaccinationAntibodyParams(String type) {
		return xmlSet(AntibodyParams.SET_TYPE,
				xmlParam("immunityEvent", type),
				xmlParam("immunityEventKind", AntibodyParams.KIND_VACCINATION),
				xmlParam("initialAntibodies", "SARS_CoV_2=1.0"));
	}

	// ---------------------------------------------------------------------------------------------------------
	// Frozen copy of the pre-refactor AntibodyModel.Config constructor. Do NOT "simplify" against
	// AntibodyConfigGroup - the point is that this stays an independent reference of the historical default.
	// ---------------------------------------------------------------------------------------------------------

	@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
	private static Map<ImmunityEvent, Map<VirusStrain, Double>> legacyInitialAntibodies() {

		Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();

		for (VaccinationType immunityType : VaccinationType.getAllStandardOptions()) {
			initialAntibodies.put(immunityType, new HashMap<>());
			for (VirusStrain virusStrain : VirusStrain.getAllStandardOptions()) {
				if (immunityType == VaccinationType.mRNA) {
					initialAntibodies.get(immunityType).put(virusStrain, 29.2);
				} else if (immunityType == VaccinationType.vector) {
					initialAntibodies.get(immunityType).put(virusStrain, 6.8);
				} else {
					initialAntibodies.get(immunityType).put(virusStrain, 5.0);
				}
			}
		}

		for (VirusStrain immunityType : VirusStrain.getAllStandardOptions()) {
			initialAntibodies.put(immunityType, new HashMap<>());
			for (VirusStrain virusStrain : VirusStrain.getAllStandardOptions()) {
				initialAntibodies.get(immunityType).put(virusStrain, 5.0);
			}
		}

		double mutEscDelta = 29.2 / 10.9;
		double mutEscBa1 = 10.9 / 1.9;
		double mutEscBa5 = 2.9;

		//Wildtype
		double mRNAAlpha = 29.2;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.SARS_CoV_2, mRNAAlpha);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.SARS_CoV_2, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.SARS_CoV_2, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.SARS_CoV_2, 0.01);
		initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.SARS_CoV_2, mRNAAlpha / mutEscDelta / mutEscBa1);
		initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.SARS_CoV_2, mRNAAlpha / mutEscDelta / mutEscBa1 / mutEscBa5);

		//Alpha
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.ALPHA, mRNAAlpha);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.ALPHA, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.ALPHA, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.ALPHA, 0.01);
		initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.ALPHA, mRNAAlpha / mutEscDelta / mutEscBa1);
		initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.ALPHA, mRNAAlpha / mutEscDelta / mutEscBa1 / mutEscBa5);

		//DELTA
		double mRNADelta = mRNAAlpha / mutEscDelta;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.DELTA, mRNADelta);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.DELTA, mRNADelta * 150. / 300.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.DELTA, mRNADelta * 64. / 300.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.DELTA, mRNADelta * 64. / 300.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.DELTA, mRNADelta * 450. / 300.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.DELTA, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.DELTA, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.DELTA, 0.01);
		initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.DELTA, mRNADelta / mutEscBa1);
		initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.DELTA, mRNADelta / mutEscBa1 / mutEscBa5);

		//BA.1
		double mRNABA1 = mRNADelta / mutEscBa1;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA1, mRNABA1);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA1, mRNABA1 * 4. / 20.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6. / 20.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6. / 20.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 8. / 20.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA1, 64.0 / 300.);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / mutEscBa5);
		initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA1, mRNAAlpha);
		initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.OMICRON_BA1, mRNAAlpha / mutEscBa5);

		//BA.2
		double mRNABA2 = mRNABA1;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA2, mRNABA2);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA2, mRNABA2 * 4. / 20.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6. / 20.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6. / 20.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 8. / 20.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / 1.4);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA2, 64.0 / 300.);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / mutEscBa5);
		initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA2, mRNAAlpha);
		initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.OMICRON_BA2, mRNAAlpha / mutEscBa5);

		//BA.5
		double mRNABa5 = mRNABA2 / mutEscBa5;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA5, mRNABa5);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA5, mRNABa5 * 4. / 20.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6. / 20.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6. / 20.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA5, mRNABa5 * 8. / 20.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA5, 64.0 / 300. / mutEscBa5);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA5, 64.0 / 300. / mutEscBa5);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA5, 64.0 / 300.);
		initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA5, mRNAAlpha / mutEscBa5);
		initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.OMICRON_BA5, mRNAAlpha);

		return initialAntibodies;
	}

	private static Map<ImmunityEvent, Map<VirusStrain, Double>> legacyAntibodyRefreshFactors() {

		Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();

		for (VaccinationType immunityType : VaccinationType.getAllStandardOptions()) {
			antibodyRefreshFactors.put(immunityType, new HashMap<>());
			for (VirusStrain virusStrain : VirusStrain.getAllStandardOptions()) {
				if (immunityType == VaccinationType.mRNA) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				} else if (immunityType == VaccinationType.vector) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 5.0);
				} else if (immunityType == VaccinationType.ba1Update) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				} else if (immunityType == VaccinationType.ba5Update) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				} else {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, Double.NaN);
				}
			}
		}

		for (VirusStrain immunityType : VirusStrain.getAllStandardOptions()) {
			antibodyRefreshFactors.put(immunityType, new HashMap<>());
			for (VirusStrain virusStrain : VirusStrain.getAllStandardOptions()) {
				antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
			}
		}

		return antibodyRefreshFactors;
	}
}

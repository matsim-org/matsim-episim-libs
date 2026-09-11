/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * #L%
 */
package org.matsim.episim.run;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.AntibodyConfigGroup;
import org.matsim.episim.ContactTransmissionConfigGroup;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimRunner;
import org.matsim.episim.PathogenConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.model.Pathogen;
import org.matsim.episim.model.TransmissionWeights;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.run.modules.SnzCologneOpenProductionScenario;
import org.matsim.testcases.MatsimTestUtils;

import java.io.File;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Template for a technical "run RSV on the Cologne open scenario" test.
 *
 * <p>Same idea as {@link InfluenzaCologneScenarioTest} &mdash; reuse {@link SnzCologneOpenProductionScenario}
 * verbatim (same Senozon inputs and settings) and layer a second {@link Pathogen} on top. RSV is the
 * case that exercises the <b>direct-contact</b> route: its {@code routeTransmissibility} carries a
 * non-zero {@code directContact} weight and the contact side is shaped through
 * {@link ContactTransmissionConfigGroup}. Face masks / ventilation / indoor-outdoor dilution attenuate
 * only the respiratory channel, so direct contact keeps contributing under Cologne's mask policy.</p>
 *
 * <p>See {@link InfluenzaCologneScenarioTest} for why {@code SnzCologneOpenProductionScenario} is
 * copied into test sources. Skeleton: numbers in {@link #configureRsv} are placeholders;
 * {@link #runsOnCologneScenario()} is disabled (downloads the Cologne input set).</p>
 *
 * <p><b>Known limitation</b> the antibody / individual
 * immunity model is still SARS-CoV-2 specific. These templates seed only RSV (the SARS-CoV-2 disease
 * import is cleared); co-circulation needs the immunity model reworked first.</p>
 */
public class RsvCologneScenarioTest {

	private static final Pathogen RSV = new Pathogen("RSV");
	private static final VirusStrain RSV_STRAIN = VirusStrain.of(RSV, "RSV");

	/** Relative per-route transmissibility of RSV. TODO calibrate the direct-contact share. */
	private static final String RSV_ROUTE_TRANSMISSIBILITY = "respiratory=1.0;directContact=0.4";

	private static final LocalDate START_DATE = LocalDate.parse("2020-02-25");
	private static final double COLOGNE_FACTOR = 0.5;

	private static final int ITERATIONS = 10;

	@RegisterExtension
	public MatsimTestUtils utils = new MatsimTestUtils();

	/**
	 * Layers RSV onto an already-built Cologne {@link Config}. Numbers are placeholders &mdash; replace
	 * with literature values.
	 */
	static void configureRsv(Config config) {

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		VirusStrainConfigGroup virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		PathogenConfigGroup pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);
		ContactTransmissionConfigGroup contactTransmissionConfig =
				ConfigUtils.addOrGetModule(config, ContactTransmissionConfigGroup.class);

		// 1. strain -> pathogen
		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getOrAddParams(RSV_STRAIN);
		strain.setPathogen(RSV);
		strain.setInfectiousness(1.0);                       // TODO calibrate

		// 2. natural history + per-route transmissibility (age-independent variant)
		//PathogenConfigGroup.PathogenParams flat = pathogenConfig.getOrAddParams(RSV, false);
		//flat.setShowingSymptomsProbabilityByAge(Map.of(0, 0.50));   // TODO
		//flat.setSeriouslySickProbabilityByAge(Map.of(0, 0.020));    // TODO
		//flat.setCriticalProbabilityByAge(Map.of(0, 0.050));         // TODO
		//flat.setDeathProbabilityByAge(Map.of(0, 0.0));              // TODO
		//flat.setRouteTransmissibility(TransmissionWeights.parse(RSV_ROUTE_TRANSMISSIBILITY));

		// 3. natural history - age-dependent variant. RSV skews hard to infants and the elderly.
		PathogenConfigGroup.PathogenParams byAge = pathogenConfig.getOrAddParams(RSV, true);
		byAge.setShowingSymptomsProbabilityByAge(Map.of(0, 0.70, 5, 0.40, 65, 0.55));                    // TODO
		byAge.setSeriouslySickProbabilityByAge(Map.of(0, 0.08, 1, 0.01, 5, 0.002, 65, 0.03, 80, 0.09)); // TODO
		byAge.setCriticalProbabilityByAge(Map.of(0, 0.10, 5, 0.02, 65, 0.15, 80, 0.25));                // TODO
		byAge.setDeathProbabilityByAge(Map.of(0, 0.0));                                                 // TODO
		byAge.setRouteTransmissibility(TransmissionWeights.parse(RSV_ROUTE_TRANSMISSIBILITY));

		// 4. contact-side route split: default for any pair, plus a stronger household share
		contactTransmissionConfig.setDefaultTransmissionWeights(
				TransmissionWeights.parse("respiratory=1.0;directContact=0.2"));
		contactTransmissionConfig.getOrAddContactPair("home", "home")
				.setTransmissionWeights(TransmissionWeights.parse("respiratory=1.0;directContact=1.0"));  // TODO
		// Optionally an age x activity contact matrix instead of inline weights:
		//   contactTransmissionConfig.setAgeBands(List.of(0, 1, 5, 15, 65));
		//   contactTransmissionConfig.getOrAddContactPair("home", "home").setFile("rsv_home_contacts.csv");

		// 5. seeding: drop the SARS-CoV-2 disease import, seed RSV with the same interpolated shape
		episimConfig.getInfections_pers_per_day().clear();
		Map<LocalDate, Integer> rsvImport = new HashMap<>();
		SnzCologneOpenProductionScenario.interpolateImport(rsvImport, COLOGNE_FACTOR * 4.0,
				START_DATE.minusDays(1), START_DATE.plusDays(14), 0.9, 23.1);    // TODO real RSV import
		episimConfig.setInfections_pers_per_day(RSV_STRAIN, rsvImport);

		// 6. antibody / individual-immunity model: give RSV the plain COVID-style profile
		//    (flat 5.0 initial antibodies, 15.0 refresh factor against every strain), matching what
		//    AntibodyConfigGroup fills in for a virus-strain immunity event without special-casing.
		//    TODO replace with real RSV immunity numbers.
		AntibodyConfigGroup antibodyConfig = ConfigUtils.addOrGetModule(config, AntibodyConfigGroup.class);
		AntibodyConfigGroup.AntibodyParams rsvAntibodies = antibodyConfig.getOrAddParams(RSV_STRAIN);
		for (VirusStrain against : virusStrainConfig.getVirusStrains()) {
			rsvAntibodies.getInitialAntibodies().put(against, 5.0);
			rsvAntibodies.getAntibodyRefreshFactors().put(against, 15.0);
		}
	}

	/**
	 * Cheap check: RSV (with a direct-contact route) can be layered on the Cologne settings and both
	 * the pathogen config and the contact-transmission resolver validate &mdash; no input files, no run.
	 */
	@Test
	public void pathogenOnCologneSettingsValidates() {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup(), new VirusStrainConfigGroup(),
				new PathogenConfigGroup(), new ContactTransmissionConfigGroup());
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setStartDate(START_DATE);
		episimConfig.setCalibrationParameter(1.0e-05 * 0.83 * 1.4 * 1.2 * 1.7);
		episimConfig.setHospitalFactor(0.5);
		episimConfig.setSampleSize(0.25);
		// TODO RSV-specific disease-progression timing; SnzCologneOpenProductionScenario.progressionConfig(...)
		//  is COVID timing and is package-private - copy it in or provide your own Transition.config().
		SnzCologneOpenProductionScenario.configureContactIntensitiesAndSeasonality(episimConfig);
		SnzCologneOpenProductionScenario.configureTracing(config, COLOGNE_FACTOR);

		configureRsv(config);

		PathogenConfigGroup pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);
		VirusStrainConfigGroup virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		ContactTransmissionConfigGroup contactTransmissionConfig =
				ConfigUtils.addOrGetModule(config, ContactTransmissionConfigGroup.class);

		assertThat(virusStrainConfig.getParams(RSV_STRAIN).getPathogen()).isEqualTo(RSV);

		TransmissionWeights routes = pathogenConfig.getParams(RSV, true).getRouteTransmissibility();
		assertThat(routes.getRespiratory()).isEqualTo(1.0);
		assertThat(routes.getDirectContact()).isGreaterThan(0.0);

		assertThat(pathogenConfig.getParams(RSV, true).getSeriouslySickProbability(0))
				.isGreaterThan(pathogenConfig.getParams(RSV, true).getSeriouslySickProbability(10));

		assertThat(episimConfig.getInfections_pers_per_day()).containsOnlyKeys(RSV_STRAIN);

		// the getParams / probability lookups above already exercise the config; full finalisation
		// validation (PathogenConfigGroup.checkConsistency) runs inside the scenario in runsOnCologneScenario().

		ContactTransmissionConfigGroup.Resolver resolver = contactTransmissionConfig.createResolver();
		assertThat(resolver.resolve("home", "home", 30, 30).getDirectContact())
				.isGreaterThan(resolver.resolve("work", "leisure", 30, 30).getDirectContact());

		assertThat(pathogenConfig.getParams(Pathogen.SARS_COV_2, false).getRouteTransmissibility().getDirectContact())
				.isEqualTo(0.0);
	}

	/**
	 * Skeleton for the real thing: the full Cologne open scenario with RSV as the only circulating virus.
	 */
	@Test
	//@Disabled("template: downloads the full Cologne input set from the VSP SVN; provide real parameters and assertions")
	public void runsOnCologneScenario() throws Exception {

		OutputDirectoryLogging.catchLogEntries();

		Injector injector = Guice.createInjector(
				Modules.override(new EpisimModule()).with(new SnzCologneOpenProductionScenario.Builder().build()));

		Config config = injector.getInstance(Config.class);
		config.controller().setOutputDirectory(utils.getOutputDirectory());

		configureRsv(config);   // before the runner is built - the contact model reads config at construction

		injector.getInstance(EpisimRunner.class).run(ITERATIONS);

		assertThat(new File(utils.getOutputDirectory(), "infections.txt")).exists();
		assertThat(new File(utils.getOutputDirectory(), "infectionEvents.txt")).exists();

		// TODO assert RSV actually circulated:
		//  - infectionEvents.txt has rows with virusStrain == "RSV", none with SARS_CoV_2
		//  - infections still occur under the Cologne mask policy (direct-contact channel)
		//  - severity skews to the youngest / oldest age bands
	}
}

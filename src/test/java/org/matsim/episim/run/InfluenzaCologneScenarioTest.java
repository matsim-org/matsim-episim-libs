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
 * Template for a technical "run influenza on the Cologne open scenario" test.
 *
 * <p>Both tests reuse {@link SnzCologneOpenProductionScenario} verbatim &mdash; same Senozon input
 * files (population, weekday/Saturday/Sunday event files, vehicles), same calibration parameter,
 * contact intensities, seasonality, mobility-based restrictions, masks and tracing. The only thing
 * layered on top is a second {@link Pathogen} (influenza) described purely through
 * {@link VirusStrainConfigGroup} and {@link PathogenConfigGroup}; influenza is respiratory-only so
 * {@link ContactTransmissionConfigGroup} is left at its defaults (see {@link RsvCologneScenarioTest}
 * for the direct-contact route).</p>
 *
 * <p>{@code SnzCologneOpenProductionScenario} is copied into test sources here because the
 * pathogen / contact-transmission config groups live in this module and are not yet published in a
 * {@code matsim-episim-libs} artifact that the {@code matsim-episim} scenario repo depends on. Once
 * the pathogen refactor is released and {@code matsim-episim} bumps {@code episim.version}, move these
 * tests there and delete the copy.</p>
 *
 * <p>Skeleton: the epidemiological numbers in {@link #configureInfluenza} are placeholders, and
 * {@link #runsOnCologneScenario()} is disabled because it downloads the full Cologne input set from
 * the VSP SVN. Fill in real values and enable it once ready.</p>
 *
 * <p><b>Known limitation</b> (see {@code docs/multi-pathogen-refactor.md}): the antibody / individual
 * immunity model is still SARS-CoV-2 specific. These templates seed <i>only</i> the new pathogen
 * (the SARS-CoV-2 disease import is cleared); co-circulating both needs the immunity model reworked.</p>
 */
public class InfluenzaCologneScenarioTest {

	private static final Pathogen INFLUENZA = new Pathogen("influenza");
	private static final VirusStrain INFLUENZA_STRAIN = VirusStrain.of(INFLUENZA, "influenza");

	/** Cologne model constants, mirrored from {@link SnzCologneOpenProductionScenario}. */
	private static final LocalDate START_DATE = LocalDate.parse("2020-02-25");
	private static final double COLOGNE_FACTOR = 0.5;

	private static final int ITERATIONS = 10;

	@RegisterExtension
	public MatsimTestUtils utils = new MatsimTestUtils();

	/**
	 * Layers influenza onto an already-built Cologne {@link Config}: registers the strain and its
	 * pathogen params, and replaces the SARS-CoV-2 disease import with an influenza import of the same
	 * shape. Numbers are placeholders &mdash; replace with literature values.
	 */
	static void configureInfluenza(Config config) {

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		VirusStrainConfigGroup virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		PathogenConfigGroup pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);

		// 1. strain -> pathogen, relative infectiousness (SARS-CoV-2 = 1.0 anchor)
		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getOrAddParams(INFLUENZA_STRAIN);
		strain.setPathogen(INFLUENZA);
		strain.setInfectiousness(1.0);                       // TODO calibrate

		// 2. natural history - age-independent variant
		PathogenConfigGroup.PathogenParams flat = pathogenConfig.getOrAddParams(INFLUENZA, false);
		flat.setShowingSymptomsProbabilityByAge(Map.of(0, 0.60));   // TODO
		flat.setSeriouslySickProbabilityByAge(Map.of(0, 0.010));    // TODO
		flat.setCriticalProbabilityByAge(Map.of(0, 0.050));         // TODO
		flat.setDeathProbabilityByAge(Map.of(0, 0.0));              // TODO
		flat.setRouteTransmissibility(TransmissionWeights.parse("respiratory=1.0"));  // influenza: respiratory only

		// 3. natural history - age-dependent variant (Cologne uses AgeDependentDiseaseStatusTransitionModel)
		PathogenConfigGroup.PathogenParams byAge = pathogenConfig.getOrAddParams(INFLUENZA, true);
		byAge.setShowingSymptomsProbabilityByAge(Map.of(0, 0.60));                                // TODO real age table
		byAge.setSeriouslySickProbabilityByAge(Map.of(0, 0.005, 5, 0.002, 60, 0.03, 80, 0.08));  // TODO
		byAge.setCriticalProbabilityByAge(Map.of(0, 0.05, 60, 0.15, 80, 0.25));                  // TODO
		byAge.setDeathProbabilityByAge(Map.of(0, 0.0));                                          // TODO
		byAge.setRouteTransmissibility(TransmissionWeights.parse("respiratory=1.0"));

		// 4. seeding: drop the SARS-CoV-2 disease import, seed influenza with the same interpolated shape
		episimConfig.getInfections_pers_per_day().clear();
		Map<LocalDate, Integer> influenzaImport = new HashMap<>();
		SnzCologneOpenProductionScenario.interpolateImport(influenzaImport, COLOGNE_FACTOR * 4.0,
				START_DATE.minusDays(1), START_DATE.plusDays(14), 0.9, 23.1);    // TODO real influenza import
		episimConfig.setInfections_pers_per_day(INFLUENZA_STRAIN, influenzaImport);
	}

	/**
	 * Cheap check: influenza can be layered on the Cologne <em>settings</em> (contact intensities,
	 * seasonality, progression) and the config validates &mdash; without loading the Cologne input
	 * files or running a simulation.
	 */
	@Test
	public void pathogenOnCologneSettingsValidates() {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup(), new VirusStrainConfigGroup(),
				new PathogenConfigGroup());
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		// same settings as SnzCologneOpenProductionScenario, minus the remote input files
		episimConfig.setStartDate(START_DATE);
		episimConfig.setCalibrationParameter(1.0e-05 * 0.83 * 1.4 * 1.2 * 1.7);
		episimConfig.setHospitalFactor(0.5);
		episimConfig.setSampleSize(0.25);
		// TODO influenza-specific disease-progression timing; SnzCologneOpenProductionScenario.progressionConfig(...)
		//  is COVID timing and is package-private - copy it in or provide your own Transition.config().
		SnzCologneOpenProductionScenario.configureContactIntensitiesAndSeasonality(episimConfig);
		SnzCologneOpenProductionScenario.configureTracing(config, COLOGNE_FACTOR);

		configureInfluenza(config);

		PathogenConfigGroup pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);
		VirusStrainConfigGroup virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		assertThat(virusStrainConfig.getParams(INFLUENZA_STRAIN).getPathogen()).isEqualTo(INFLUENZA);
		assertThat(pathogenConfig.hasParams(INFLUENZA, true)).isTrue();
		assertThat(pathogenConfig.getParams(INFLUENZA, false).getRouteTransmissibility().getRespiratory()).isEqualTo(1.0);
		assertThat(pathogenConfig.getParams(INFLUENZA, false).getRouteTransmissibility().getDirectContact()).isEqualTo(0.0);
		assertThat(pathogenConfig.getParams(INFLUENZA, true).getSeriouslySickProbability(85)).isGreaterThan(0.0);

		assertThat(episimConfig.getInfections_pers_per_day()).containsOnlyKeys(INFLUENZA_STRAIN);

		// the getParams / probability lookups above already exercise the config; full finalisation
		// validation (PathogenConfigGroup.checkConsistency) runs inside the scenario in runsOnCologneScenario().

		// SARS-CoV-2 defaults untouched
		assertThat(pathogenConfig.getParams(Pathogen.SARS_COV_2, false).getRouteTransmissibility().getDirectContact())
				.isEqualTo(0.0);
	}

	/**
	 * Skeleton for the real thing: the full Cologne open scenario with influenza as the only
	 * circulating virus.
	 */
	@Test
	//@Disabled("template: downloads the full Cologne input set from the VSP SVN; provide real parameters and assertions")
	public void runsOnCologneScenario() throws Exception {

		OutputDirectoryLogging.catchLogEntries();

		Injector injector = Guice.createInjector(
				Modules.override(new EpisimModule()).with(new SnzCologneOpenProductionScenario.Builder().build()));

		Config config = injector.getInstance(Config.class);
		config.controller().setOutputDirectory(utils.getOutputDirectory());

		configureInfluenza(config);   // before the runner is built - the contact model reads config at construction

		injector.getInstance(EpisimRunner.class).run(ITERATIONS);

		assertThat(new File(utils.getOutputDirectory(), "infections.txt")).exists();
		assertThat(new File(utils.getOutputDirectory(), "infectionEvents.txt")).exists();

		// TODO assert influenza actually circulated:
		//  - infectionEvents.txt has rows with virusStrain == "influenza", none with SARS_CoV_2
		//  - infections grow over the run
	}
}

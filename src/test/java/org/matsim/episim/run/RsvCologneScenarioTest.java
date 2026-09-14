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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.AntibodyConfigGroup;
import org.matsim.episim.ContactTransmissionConfigGroup;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimRunner;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.PathogenConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.model.Pathogen;
import org.matsim.episim.model.Transition;
import org.matsim.episim.model.TransmissionWeights;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.run.modules.SnzCologneOpenProductionScenario;
import org.matsim.testcases.MatsimTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.matsim.episim.model.Transition.to;
import static org.matsim.episim.EpisimPerson.DiseaseStatus;

/**
 * Technical "run RSV on the Cologne open scenario" test.
 *
 * <p>Reuses {@link SnzCologneOpenProductionScenario} verbatim, exactly like
 * {@link InfluenzaCologneScenarioTest} &mdash; same Senozon input files, calibration parameter, contact
 * intensities, seasonality and mobility-based restrictions. RSV is the case that exercises the
 * <b>direct-contact</b> route: its {@code routeTransmissibility} carries a non-zero {@code directContact}
 * weight, and {@link ContactTransmissionConfigGroup} shapes the contact side, including an age-banded
 * CSV for the {@code educ_kiga}/{@code educ_kiga} pair (see below). Face masks, ventilation and
 * indoor-outdoor dilution attenuate only the respiratory channel, so direct contact keeps contributing
 * under the Cologne mask policy and is <b>not damped by the seasonality mechanism at all</b>
 * ({@code docs/rsv-parameterisation.md} section 3.5).</p>
 *
 * <p>The numbers in {@link #configureRsv} and {@link #rsvProgressionConfig} are sourced in
 * {@code docs/rsv-parameterisation.md} (2022/23 season, shared start date with
 * {@link InfluenzaCologneScenarioTest}, RSV-B dominated through the peak). The strain infectiousness and
 * the direct-contact route weight are both uncalibrated placeholders, to be fit jointly against the
 * ICOSARI RSV-SARI growth rate and the household secondary attack rate (doc section 4.6).
 * {@link #runsOnCologneScenario()} downloads the full Cologne input set from the VSP SVN and is meant to
 * be run by hand, not in CI.</p>
 *
 * <p><b>Biggest blocker (doc section 1): the Cologne synthetic population does not represent infant
 * childcare or infant households.</b> A direct check of the downloaded 25&nbsp;% population found ~50&nbsp;%
 * of age-0 agents attending {@code educ_kiga} on a weekday (real NRW under-1 Kita attendance is ~1.1&nbsp;%,
 * IT.NRW), and 25.3&nbsp;% of age-0 agents with no adult in their household at all. The age-banded CSV
 * configured below (respiratory=0.0;directContact=0.0 for any pair involving age band 0 on
 * {@code educ_kiga}/{@code educ_kiga}) suppresses the daycare symptom of this defect; it does
 * <b>not</b> fix the missing-adult-household problem, which stays open (doc section 6).</p>
 *
 * <p><b>Known limitations</b> (shared with {@link InfluenzaCologneScenarioTest}): the antibody /
 * individual immunity model is still SARS-CoV-2 specific, so only RSV is seeded (the SARS-CoV-2 disease
 * import is cleared); co-circulation needs the immunity model reworked first. Isolation at symptom onset
 * uses {@code atHome} (not {@code full}) so a sick person still infects their own household &mdash; the
 * dominant real RSV transmission situation &mdash; with a low-confidence, age-graded probability; see the
 * parameterisation document sections 3.1 and 6. Protection after a single RSV infection is short and
 * partial (median ~90&nbsp;days, not influenza's {@code fixed(365)}), so within-season reinfection is
 * plausible and exercised here for the first time in this test suite; re-validate once run.</p>
 */
public class RsvCologneScenarioTest {

	private static final Pathogen RSV = new Pathogen("RSV");
	private static final VirusStrain RSV_STRAIN = VirusStrain.of(RSV, "RSV");

	/** Cologne model constants, mirrored from {@link SnzCologneOpenProductionScenario}. */
	private static final LocalDate START_DATE = LocalDate.parse("2020-02-25");
	private static final double COLOGNE_FACTOR = 0.5;

	/**
	 * Start of the simulated 2022/23 RSV season, shared with {@link InfluenzaCologneScenarioTest}: Monday
	 * of KW 39/2022, two weeks before the RKI-defined RSV wave onset in KW 41/2022 (Cai et al. 2024,
	 * doi:10.2807/1560-7917.es.2024.29.13.2300465) and inside the Cologne mobility input, which ends
	 * 2022-12-31.
	 */
	static final LocalDate SEASON_START = LocalDate.parse("2022-09-26");

	/** School activities closed during NRW school holidays (kindergartens and universities stay open). */
	private static final String[] SCHOOLS = {"educ_primary", "educ_secondary", "educ_tertiary", "educ_other"};

	/** Log-normal sigma of the PICU stay: median 5 d, IQR 3-8 d (BRICK, doi:10.1097/inf.0000000000004712); ln(8/3) / (2 * 0.6745). */
	private static final double RSV_ICU_LOS_SIGMA = Math.log(8.0 / 3.0) / (2 * 0.6745);

	/** Columns of {@code infectionEvents.txt}: time, infector, infected, infectionType, date, groupSize, facility, virusStrain, probability. */
	private static final int VIRUS_STRAIN_COLUMN = 7;

	private static final int ITERATIONS = 100;

	@RegisterExtension
	public MatsimTestUtils utils = new MatsimTestUtils();

	/**
	 * Layers RSV onto an already-built Cologne {@link Config}: moves the start date to the 2022/23
	 * season, registers the strain, its pathogen params, its disease progression and the direct-contact
	 * route, and replaces the SARS-CoV-2 disease import with an RSV import. Every number is sourced in
	 * {@code docs/rsv-parameterisation.md}.
	 */
	static void configureRsv(Config config) {

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		VirusStrainConfigGroup virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		PathogenConfigGroup pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);
		ContactTransmissionConfigGroup contactTransmissionConfig =
				ConfigUtils.addOrGetModule(config, ContactTransmissionConfigGroup.class);

		// 0. season window: 2022/23 on the real Cologne mobility trace, shared with influenza (doc section 2)
		episimConfig.setStartDate(SEASON_START);

		// 1. one pooled strain (RSV-A dominated 2021, RSV-B dominated 2022/23; one-pathogen immunity limitation
		//    means two strains would infect the same people independently -> pool, same decision as influenza)
		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getOrAddParams(RSV_STRAIN);
		strain.setPathogen(RSV);
		strain.setInfectiousness(1.0);        // NOT an estimate: calibrate to r = 0.06-0.07/d (ICOSARI RSV-SARI 00+ W40-45/2022)
		strain.setFactorSeriouslySick(1.0);   // pooled strain = pathogen baseline
		strain.setFactorCritical(1.0);        // pooled strain = pathogen baseline
		// household-exposure-controlled relative odds of acquiring infection by contact age, ref. >=65y (PHIRST, doi:10.1038/s41467-023-44275-y)
		strain.setAgeSusceptibility(Map.of(0, 5.0, 1, 7.1, 5, 3.5, 13, 2.9, 19, 2.6, 45, 2.7, 65, 1.0));
		// children shed more, longer, and are 55% of household index cases (doi:10.1101/2024.11.14.24317347, doi:10.1093/infdis/jit828)
		strain.setAgeInfectivity(Map.of(0, 1.3, 1, 1.8, 5, 1.5, 19, 1.0, 65, 0.7));

		// 2. natural history (age-dependent); seriouslySick = effective target / hospitalFactor
		double hospitalFactor = episimConfig.getHospitalFactor();
		PathogenConfigGroup.PathogenParams rsv = pathogenConfig.getOrAddParams(RSV);
		PathogenConfigGroup.ProgressionParams byAge = rsv.getOrAddProgressionParams(true);
		// P(symptomatic|infected): PHIRST South Africa by age, band 5 pools 5-12/13-18/19-44/45-64 (doi:10.1038/s41467-023-44275-y)
		byAge.setShowingSymptomsProbabilityByAge(Map.of(0, 0.67, 1, 0.48, 5, 0.26, 60, 0.35, 80, 0.45));
		byAge.setSeriouslySickProbabilityByAge(Map.of(       // P(hosp|symptomatic); chained derivation, doc section 4.2
				0, 0.2051,    // RESCEU hosp/infection-incidence / PHIRST symptomatic fraction (doi:10.1016/s2213-2600(22)00414-3)
				1, 0.0216,    // Wick 2023 hosp / PHIRST infection incidence & symptomatic fraction (doi:10.1111/irv.13211)
				5, 0.001,     // no evidence found: coarse low placeholder for the broad 5-59 band
				60, 0.30,     // Scholz 2024 underdetection-corrected 60+ estimate, split as an assumption (doi:10.1007/s40121-024-01006-0)
				80, 0.56));   // same split, upper half
		byAge.setCriticalProbabilityByAge(Map.of(              // P(ICU|hospitalised)
				0, 0.08,      // Wick 2023, <1y, Germany 2022 (doi:10.1111/irv.13211)
				1, 0.051,     // Wick 2023, 1-2y, Germany 2022
				5, 0.30,      // US adult ICU-among-hospitalised proxy (doi:10.1007/s40121-025-01255-7)
				60, 0.161,    // Scholz 2024, Germany 60+, excl. pandemic seasons
				80, 0.15));   // assumption: ICU-among-hospitalised plateaus/declines at oldest ages (Liang 2025 pattern)
		byAge.setDeathProbabilityByAge(Map.of(                  // P(deceased|critical) = P(death|ICU); death-only-via-ICU gap, doc section 4.4
				0, 0.01,      // BRICK: 3/423 PICU deaths, all comorbid (doi:10.1097/inf.0000000000004712)
				1, 0.01,      // same order, pediatric ICU death rare beyond infancy
				5, 0.05,      // no evidence found: coarse assumption
				60, 0.15,     // assumption bridging Falsey 2005 in-hospital 8% and a ~19% ICU-cohort comparator
				80, 0.22));   // assumption, rising with age
		// RSV spreads by droplets AND direct/self-inoculation contact, aerosols "less important" than for influenza (doi:10.1111/irv.70270)
		rsv.setRouteTransmissibility(TransmissionWeights.parse("respiratory=1.0;directContact=0.6"));
		// atHome (not full): a sick child still infects their own household, the dominant real RSV transmission situation.
		// Low-confidence, age-graded assumption (doc section 3.1): informal Kita-exclusion norm vs. adult presenteeism.
		rsv.setSymptomaticIsolationProbabilityByAge(Map.of(0, 0.85, 1, 0.70, 5, 0.30, 60, 0.40, 80, 0.50));
		rsv.setSymptomaticIsolationStatus(EpisimPerson.QuarantineStatus.atHome);

		// 3. disease progression (replaces the COVID progressionConfig of SnzCologneOpenProductionScenario)
		episimConfig.setProgressionConfig(rsvProgressionConfig(Transition.config()).build());

		// 4. season inputs the Cologne builder does not cover for 2022/23 (same fixes as influenza, doc section 3.5)
		FixedPolicy.ConfigBuilder policy = FixedPolicy.parse(episimConfig.getPolicy());
		// schools fully open in autumn 2022; the Cologne builder keeps all educ_* at 0.5 from 2020-04-27 onwards
		policy.restrict(SEASON_START, 1.0, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_higher", "educ_other");
		// NRW Herbstferien 04.10.-15.10.2022 and Weihnachtsferien 23.12.2022-06.01.2023 (Ferienordnung, schulministerium.nrw);
		// 0.2 = residual fraction the Cologne builder uses for closed schools (2020-03-16), an assumption for holidays
		policy.restrict(LocalDate.parse("2022-10-04"), 0.2, SCHOOLS);
		policy.restrict(LocalDate.parse("2022-10-17"), 1.0, SCHOOLS);
		policy.restrict(LocalDate.parse("2022-12-23"), 0.2, SCHOOLS);
		policy.restrict(LocalDate.parse("2023-01-09"), 1.0, SCHOOLS);
		episimConfig.setPolicy(policy.build());
		// the default leisureOutdoorFraction ends 2021-09-15 (0.8 thereafter); repeat its 2020/21 pattern for 2022/23
		// (EpisimConfigGroup defaults); weather-based fractions would be better (EpisimUtils.getOutDoorFractionFromDateAndTemp2)
		episimConfig.setLeisureOutdoorFraction(Map.of(
				LocalDate.parse("2022-04-15"), 0.8,
				LocalDate.parse("2022-09-15"), 0.8,
				LocalDate.parse("2022-11-15"), 0.1,
				LocalDate.parse("2023-02-15"), 0.1,
				LocalDate.parse("2023-04-15"), 0.8));
		// no contact tracing for RSV in 2022/23
		ConfigUtils.addOrGetModule(config, TracingConfigGroup.class).setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);

		// 5. contact-side route split: home is the most physical setting in Germany (POLYMOD DE re-analysis, doc section 4.7);
		//    educ_kiga gets an age-banded CSV to suppress the model's erroneous ~50% age-0 kiga attendance (doc section 2.1) -
		//    real under-1 Kita attendance is ~1.1% (IT.NRW); this patches the symptom, not the population defect itself.
		contactTransmissionConfig.setDefaultTransmissionWeights(TransmissionWeights.parse("respiratory=1.0;directContact=0.2"));
		contactTransmissionConfig.getOrAddContactPair("home", "home")
				.setTransmissionWeights(TransmissionWeights.parse("respiratory=1.0;directContact=1.0"));
		contactTransmissionConfig.setAgeBands(List.of(0, 1, 3, 18));
		contactTransmissionConfig.getOrAddContactPair("educ_kiga", "educ_kiga")
				.setFile("src/test/resources/rsv/rsv_kiga_direct_contact.csv");

		// 6. seeding: drop the SARS-CoV-2 import; small continuous trickle, ramped, far below the previous ~120/day
		//    placeholder (which was almost certainly too high). Level is a calibration prior, no evidence found.
		//    interpolateImport works on day-of-year, so each segment must stay within one calendar year.
		episimConfig.getInfections_pers_per_day().clear();
		Map<LocalDate, Integer> rsvImport = new HashMap<>();
		SnzCologneOpenProductionScenario.interpolateImport(rsvImport, COLOGNE_FACTOR,
				SEASON_START.minusDays(1), LocalDate.parse("2022-10-24"), 2.0, 6.0);
		SnzCologneOpenProductionScenario.interpolateImport(rsvImport, COLOGNE_FACTOR,
				LocalDate.parse("2022-10-24"), LocalDate.parse("2022-12-31"), 6.0, 6.0);
		episimConfig.setInfections_pers_per_day(RSV_STRAIN, rsvImport);

		// 7. antibodies 0.0 keep getSeriouslySickFactor = 1/(1+ab^beta) at 1 (same neutralisation as influenza,
		//    doc section 1 / influenza doc section 6.2)
		AntibodyConfigGroup antibodyConfig = ConfigUtils.addOrGetModule(config, AntibodyConfigGroup.class);
		AntibodyConfigGroup.AntibodyParams rsvAntibodies = antibodyConfig.getOrAddParams(RSV_STRAIN);
		for (VirusStrain against : virusStrainConfig.getVirusStrains()) {
			rsvAntibodies.getInitialAntibodies().put(against, 0.0);
			rsvAntibodies.getAntibodyRefreshFactors().put(against, 1.0);
		}
	}

	/**
	 * RSV disease-progression timing. Transitions fire once {@code daysSince >= transitionDay}, evaluated once per
	 * day, so everything is discretised to whole days.
	 */
	static Transition.Builder rsvProgressionConfig(Transition.Builder builder) {

		return builder
				// latent: half of the 4.4 d median incubation (Lessler 2009, doi:10.1016/S1473-3099(09)70069-6);
				// no latent-only RSV estimate found, explicit compromise
				.from(DiseaseStatus.infectedButNotContagious,
						to(DiseaseStatus.contagious, Transition.fixed(2)))

				// remainder of incubation after the 2 d latent step, dispersion 1.24 (1.13-1.35) (Lessler 2009)
				// asymptomatic branch: mean shedding 7.8 d (Munywoki 2015, doi:10.1017/S0950268814001393); std widened, assumption
				.from(DiseaseStatus.contagious,
						to(DiseaseStatus.showingSymptoms, Transition.logNormalWithMedianAndSigma(2.4, Math.log(1.24))),
						to(DiseaseStatus.recovered, Transition.logNormalWithMeanAndStd(7.8, 4.0)))

				// onset->admission ~3-5 d; adult median 3 d, IQR 2-4 (Malosh 2017, doi:10.1016/j.jcv.2017.09.001), blended
				// with the infant bronchiolitis pattern described in the task brief; sigma from the adult IQR
				// symptomatic illness: total symptomatic shedding mean 13.5 d (Munywoki 2015) minus ~2.4 d presymptomatic
				.from(DiseaseStatus.showingSymptoms,
						to(DiseaseStatus.seriouslySick, Transition.logNormalWithMedianAndSigma(3.5, 0.514)),
						to(DiseaseStatus.recovered, Transition.logNormalWithMeanAndStd(11.0, 5.0)))

				// ward -> ICU: no RSV-specific evidence found; COVID placeholder kept (explicit assumption)
				// hospital stay: cross-age compromise, infant LOS 4.5 d (doi:10.1111/irv.13211) vs. elderly pneumonia LOS
				// 10.4 d mean (Scholz 2024, doi:10.1007/s40121-024-01006-0), weighted toward infants (dominant case count)
				.from(DiseaseStatus.seriouslySick,
						to(DiseaseStatus.critical, Transition.logNormalWithMedianAndStd(1.0, 1.0)),
						to(DiseaseStatus.recovered, Transition.logNormalWithMeanAndStd(5.0, 5.0)))

				// ICU (PICU) stay: median 5 d, IQR 3-8 (BRICK NL, n=423, doi:10.1097/inf.0000000000004712)
				// time to death in ICU: no evidence found -> same distribution (explicit assumption)
				.from(DiseaseStatus.critical,
						to(DiseaseStatus.seriouslySickAfterCritical, Transition.logNormalWithMedianAndSigma(5.0, RSV_ICU_LOS_SIGMA)),
						to(DiseaseStatus.deceased, Transition.logNormalWithMedianAndSigma(5.0, RSV_ICU_LOS_SIGMA)))

				// post-ICU ward stay: no RSV-specific evidence found; COVID placeholder kept (explicit assumption)
				.from(DiseaseStatus.seriouslySickAfterCritical,
						to(DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(7.0, 7.0)))

				// protection is short and partial: ~half of adults reinfected within 2 months, two-thirds within 8 months
				// (Hall 1991, doi:10.1093/infdis/163.4.693); within-epidemic reinfection risk reduced only ~47%
				// (Kombe 2019, doi:10.1016/j.epidem.2018.12.001) - unlike influenza's fixed(365), within-season
				// reinfection is plausible and deliberately allowed here
				.from(DiseaseStatus.recovered,
						to(DiseaseStatus.susceptible, Transition.logNormalWithMedianAndStd(90, 60)));
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

		// same settings as SnzCologneOpenProductionScenario, minus the remote input files
		episimConfig.setStartDate(START_DATE);
		episimConfig.setCalibrationParameter(1.0e-05 * 0.83 * 1.4 * 1.2 * 1.7);
		episimConfig.setHospitalFactor(0.5);
		episimConfig.setSampleSize(0.25);
		SnzCologneOpenProductionScenario.configureContactIntensitiesAndSeasonality(episimConfig);
		SnzCologneOpenProductionScenario.configureTracing(config, COLOGNE_FACTOR);

		// also sets the RSV progression config, season start, season policy and contact-transmission routes
		configureRsv(config);

		PathogenConfigGroup pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);
		VirusStrainConfigGroup virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		ContactTransmissionConfigGroup contactTransmissionConfig =
				ConfigUtils.addOrGetModule(config, ContactTransmissionConfigGroup.class);

		assertThat(virusStrainConfig.getParams(RSV_STRAIN).getPathogen()).isEqualTo(RSV);

		TransmissionWeights routes = pathogenConfig.getParams(RSV).getRouteTransmissibility();
		assertThat(routes.getRespiratory()).isEqualTo(1.0);
		assertThat(routes.getDirectContact()).isGreaterThan(0.0);

		// infants (age 0) are the most severe age band, and the most susceptible age band is toddlers (age 1)
		assertThat(pathogenConfig.getProgressionParams(RSV, true).getSeriouslySickProbability(0))
				.isGreaterThan(pathogenConfig.getProgressionParams(RSV, true).getSeriouslySickProbability(10));
		assertThat(pathogenConfig.getParams(RSV).getSymptomaticIsolationStatus())
				.isEqualTo(EpisimPerson.QuarantineStatus.atHome);

		assertThat(episimConfig.getInfections_pers_per_day()).containsOnlyKeys(RSV_STRAIN);

		// the getParams / probability lookups above already exercise the config; full finalisation
		// validation (PathogenConfigGroup.checkConsistency) runs inside the scenario in runsOnCologneScenario().

		ContactTransmissionConfigGroup.Resolver resolver = contactTransmissionConfig.createResolver();
		assertThat(resolver.resolve("home", "home", 30, 30).getDirectContact())
				.isGreaterThan(resolver.resolve("work", "leisure", 30, 30).getDirectContact());
		// the age-banded CSV suppresses transmission for an age-0 agent placed in an educ_kiga container
		// (doc section 2.1 / section 3.4): real under-1 Kita attendance is ~1.1%, the Cologne population puts it at ~50%
		assertThat(resolver.resolve("educ_kiga", "educ_kiga", 0, 3).getDirectContact()).isEqualTo(0.0);
		assertThat(resolver.resolve("educ_kiga", "educ_kiga", 4, 3).getDirectContact()).isGreaterThan(0.0);

		// SARS-CoV-2 defaults untouched
		assertThat(pathogenConfig.getParams(Pathogen.SARS_COV_2).getRouteTransmissibility().getDirectContact())
				.isEqualTo(0.0);
	}

	/**
	 * The real thing: the full Cologne open scenario with RSV as the only circulating virus.
	 *
	 * <p>Heavy on purpose: downloads the full Cologne input set (25% sample) from the VSP SVN and simulates
	 * {@value #ITERATIONS} days. It is run locally on a laptop, which is fine; it is intentionally not disabled,
	 * so expect a long runtime when running the whole test suite.</p>
	 */
	@Test
	public void runsOnCologneScenario() throws Exception {

		OutputDirectoryLogging.catchLogEntries();

		Injector injector = Guice.createInjector(
				Modules.override(new EpisimModule()).with(new SnzCologneOpenProductionScenario.Builder().build()));

		Config config = injector.getInstance(Config.class);
		configureRsv(config);   // before the runner is built - the contact model reads config at construction

		// <EPISIM_OUTPUT or test output>/<date>/RSV-<NNNNN>/output, packed for the Episim viewer after the run
		ViewerOutput output = ViewerOutput.create("RSV", Path.of(utils.getOutputDirectory()));
		output.configure(config);

		injector.getInstance(EpisimRunner.class).run(ITERATIONS);

		Path infectionEvents = output.runOutput().resolve(output.runId() + ".infectionEvents.txt");
		assertThat(output.runOutput().resolve(output.runId() + ".infections.txt")).exists();
		assertThat(infectionEvents).exists();

		output.pack(config, "cologne", "rsv", ITERATIONS, "Köln");
		assertThat(output.visualizationWithSeeds().resolve("summaries/" + output.runId() + ".zip")).exists();
		assertThat(output.visualizationWithoutSeeds().resolve("summaries/0.zip")).exists();

		// RSV circulated, and it was the only strain that did
		List<String> strains;
		try (Stream<String> lines = Files.lines(infectionEvents)) {
			strains = lines.skip(1)
					.map(line -> line.split("\t"))
					.filter(row -> row.length > VIRUS_STRAIN_COLUMN)
					.map(row -> row[VIRUS_STRAIN_COLUMN])
					.distinct()
					.toList();
		}
		assertThat(strains).containsExactly(RSV_STRAIN.toString());

		// infectionEvents.txt carries no age, so severity by age is read off the agents themselves.
		// Shares, not counts: ages 5-59 are most of the population and take most of the infections.
		Collection<EpisimPerson> persons = injector.getInstance(InfectionEventHandler.class).getPersons();
		long infectedEdge = 0;
		long severeEdge = 0;
		long infectedMiddle = 0;
		long severeMiddle = 0;

		for (EpisimPerson person : persons) {
			int age = person.getAge();
			if (age < 0 || !person.hadStrain(RSV_STRAIN))
				continue;

			boolean severe = person.hadDiseaseStatus(DiseaseStatus.seriouslySick)
					|| person.hadDiseaseStatus(DiseaseStatus.critical);

			if (age <= 4 || age >= 60) {
				infectedEdge++;
				if (severe)
					severeEdge++;
			} else {
				infectedMiddle++;
				if (severe)
					severeMiddle++;
			}
		}

		assertThat(severeEdge + severeMiddle).as("RSV produced severe cases").isPositive();
		assertThat(infectedEdge).as("infections among ages 0-4 and 60+").isPositive();
		assertThat(infectedMiddle).as("infections among ages 5-59").isPositive();
		assertThat((double) severeEdge / infectedEdge)
				.as("severe share of infected, ages 0-4 and 60+ vs ages 5-59")
				.isGreaterThan((double) severeMiddle / infectedMiddle);
	}
}

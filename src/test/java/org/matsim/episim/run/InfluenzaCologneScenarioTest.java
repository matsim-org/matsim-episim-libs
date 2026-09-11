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
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.EpisimRunner;
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

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.matsim.episim.model.Transition.to;

/**
 * Technical "run influenza on the Cologne open scenario" test.
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
 * <p>The numbers in {@link #configureInfluenza} and {@link #influenzaProgressionConfig} are sourced in
 * {@code docs/influenza-parameterisation.md} (2022/23 season, A(H3N2)-dominated). The strain
 * infectiousness is still an uncalibrated placeholder. {@link #runsOnCologneScenario()} downloads the
 * full Cologne input set from the VSP SVN and is meant to be run by hand, not in CI.</p>
 *
 * <p><b>Known limitations</b>: the antibody / individual immunity model is still SARS-CoV-2 specific,
 * so only influenza is seeded (the SARS-CoV-2 disease import is cleared). Isolation at symptom onset
 * is set to 75 % home isolation, a low-confidence value derived from contact data during illness; see the
 * parameterisation document, sections 4.9 and "Gaps and risks".</p>
 */
public class InfluenzaCologneScenarioTest {

	private static final Pathogen INFLUENZA = new Pathogen("influenza");
	private static final VirusStrain INFLUENZA_STRAIN = VirusStrain.of(INFLUENZA, "influenza");

	/** Cologne model constants, mirrored from {@link SnzCologneOpenProductionScenario}. */
	private static final LocalDate START_DATE = LocalDate.parse("2020-02-25");
	private static final double COLOGNE_FACTOR = 0.5;

	/**
	 * Start of the simulated 2022/23 influenza season: Monday of KW 39/2022, four weeks before the RKI
	 * wave onset in KW 43/2022 (ARE-Wochenbericht KW 44/2022, doi:10.25646/10757) and inside the
	 * Cologne mobility input, which ends 2022-12-31.
	 */
	static final LocalDate SEASON_START = LocalDate.parse("2022-09-26");

	/** School activities closed during NRW school holidays (kindergartens and universities stay open). */
	private static final String[] SCHOOLS = {"educ_primary", "educ_secondary", "educ_tertiary", "educ_other"};

	/** Log-normal sigma of the ICU stay: median 4 d, IQR 1-8 d (doi:10.3390/v17111467); ln(8/1) / (2 * 0.6745). */
	private static final double ICU_LOS_SIGMA = Math.log(8.0 / 1.0) / (2 * 0.6745);

	private static final int ITERATIONS = 10;

	@RegisterExtension
	public MatsimTestUtils utils = new MatsimTestUtils();

	/**
	 * Layers influenza onto an already-built Cologne {@link Config}: moves the start date to the
	 * 2022/23 season, registers the strain, its pathogen params and its disease progression, and
	 * replaces the SARS-CoV-2 disease import with an influenza import. Every number is sourced in
	 * {@code docs/influenza-parameterisation.md}.
	 */
	static void configureInfluenza(Config config) {

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		VirusStrainConfigGroup virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		PathogenConfigGroup pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);

		// 0. season window: 2022/23 on the real Cologne mobility trace (doc section 2)
		episimConfig.setStartDate(SEASON_START);

		// 1. one pooled strain -> pathogen (2022/23: sentinel subtyping 95 A(H3N2), 1 A(H1N1)pdm09, 2 B/Victoria up to KW 44,
		//    ARE-Wochenbericht KW 44/2022, doi:10.25646/10757)
		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getOrAddParams(INFLUENZA_STRAIN);
		strain.setPathogen(INFLUENZA);
		// NOT an estimate: calibration placeholder. Fit together with calibrationParameter to the 2022/23 growth rate
		// r = 0.085/day (doubling 8.1 d, ICOSARI flu-SARI KW 45-49/2022, doi:10.5281/zenodo.22686153), i.e. R ~ 1.2-1.35
		strain.setInfectiousness(1.0);
		// pooled strain = pathogen baseline, no strain-relative severity (doc section 1.2)
		strain.setFactorSeriouslySick(1.0);
		strain.setFactorCritical(1.0);
		// relative susceptibility vs adults >= 40 y for A(H3N2): 12-18 y HR 2.04 (1.19-3.49); < 12 y not significantly
		// elevated -> 1.0; 19-39 y and elderly not reported -> 1.0 (Sauter 2026, doi:10.1038/s41467-026-76037-x)
		strain.setAgeSusceptibility(Map.of(0, 1.0, 11, 1.0, 12, 2.04, 18, 2.04, 19, 1.0));
		// infectivity did not vary with age (Cauchemez 2009, doi:10.1056/NEJMoa0905498)
		strain.setAgeInfectivity(Map.of(0, 1.0));

		// 2. natural history, age-dependent variant (Cologne uses AgeDependentDiseaseStatusTransitionModel).
		//    The seriouslySick values are the effective targets divided by hospitalFactor, which the model multiplies back in.
		double hospitalFactor = episimConfig.getHospitalFactor();
		PathogenConfigGroup.PathogenParams byAge = pathogenConfig.getOrAddParams(INFLUENZA, true);
		// P(showingSymptoms | contagious): 268 of 478 PCR-confirmed infections symptomatic (PHIRST, Cohen 2021,
		// doi:10.1016/S2214-109X(21)00141-8); no age-stratified estimate found
		byAge.setShowingSymptomsProbabilityByAge(Map.of(0, 0.56));
		// P(seriouslySick | showingSymptoms): flu-associated hospitalisations per symptomatic illness, CDC 2018-19 burden
		// Table 1 (https://archive.cdc.gov/www_cdc_gov/flu/about/burden/2018-2019.html), US substitute for Germany
		byAge.setSeriouslySickProbabilityByAge(Map.of(
				0, 0.00697 / hospitalFactor,     // 0-4 y:   21,046 / 3,018,815
				5, 0.00274 / hospitalFactor,     // 5-17 y:  18,159 / 6,622,851
				18, 0.00561 / hospitalFactor,    // 18-49 y: 54,978 / 9,794,700
				50, 0.01060 / hospitalFactor,    // 50-64 y: 76,617 / 7,224,769
				65, 0.09091 / hospitalFactor));  // 65+ y:   204,326 / 2,247,586
		// P(critical | seriouslySick): ICU share of J09-J11 hospitalisations in Germany 2022/23, 0-17 y 4.4 %, 18-59 y 9.9 %,
		// >= 60 y 11.3 % (InEK data, Meyer 2026, doi:10.1007/s40121-026-01384-7)
		byAge.setCriticalProbabilityByAge(Map.of(0, 0.044, 18, 0.099, 60, 0.113));
		// P(deceased | critical): pooled European ICU mortality 0.24 (0.20-0.27), no age strata
		// (Suarez-Sanchez 2025, doi:10.1111/irv.70073); requires the critical -> deceased transition below
		byAge.setDeathProbabilityByAge(Map.of(0, 0.24));
		// respiratory only: aerosols ~ half of household transmission (Cowling 2013, doi:10.1038/ncomms2922); hand hygiene
		// alone showed no significant effect (Wong 2014, doi:10.1017/S095026881400003X)
		byAge.setRouteTransmissibility(TransmissionWeights.parse("respiratory=1.0"));
		// isolation at symptom onset: illness cut contacts, mostly outside the home, so that R fell to about 1/4
		// (Van Kerckhove 2013, doi:10.1093/aje/kwt196). atHome removes all non-home contacts, so 1 - p = 0.25 -> p = 0.75.
		// Low confidence: ILI cases in England 2009, sicker than all symptomatic infections; no age-specific estimate.
		byAge.setSymptomaticIsolationProbabilityByAge(Map.of(0, 0.75));
		byAge.setSymptomaticIsolationStatus(EpisimPerson.QuarantineStatus.atHome);

		// 3. disease progression timing (replaces the COVID progressionConfig of SnzCologneOpenProductionScenario)
		episimConfig.setProgressionConfig(influenzaProgressionConfig(Transition.config()).build());

		// 4. season inputs the Cologne builder does not cover for 2022/23 (doc section "Gaps and risks")
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
		// no contact tracing for influenza in 2022/23
		ConfigUtils.addOrGetModule(config, TracingConfigGroup.class).setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);

		// 5. seeding: drop the SARS-CoV-2 import; continuous low-level influenza import. Ramp 2 -> 4 persons/day (x cologneFactor
		//    = 1 -> 2 agents/day in the 25 % sample) mirrors sentinel influenza positivity 12 % (KW 40) -> 22 % (KW 43/2022)
		//    (doi:10.25646/10757). The level is a calibration prior: no evidence found for a Cologne importation rate.
		//    interpolateImport works on day-of-year, so each segment must stay within one calendar year.
		episimConfig.getInfections_pers_per_day().clear();
		Map<LocalDate, Integer> influenzaImport = new HashMap<>();
		SnzCologneOpenProductionScenario.interpolateImport(influenzaImport, COLOGNE_FACTOR,
				SEASON_START.minusDays(1), LocalDate.parse("2022-10-24"), 2.0, 4.0);
		SnzCologneOpenProductionScenario.interpolateImport(influenzaImport, COLOGNE_FACTOR,
				LocalDate.parse("2022-10-24"), LocalDate.parse("2022-12-31"), 4.0, 4.0);
		episimConfig.setInfections_pers_per_day(INFLUENZA_STRAIN, influenzaImport);

		// 6. antibody model: initial antibodies 0.0. AntibodyDependentTransitionModel.getSeriouslySickFactor applies
		//    1 / (1 + ab^beta) even on a first infection, so the former placeholder 5.0 cut hospitalisation ~6-fold; 0.0 keeps the
		//    factor at 1. The Cologne infection model does not read antibodies for susceptibility (doc section 1.5).
		AntibodyConfigGroup antibodyConfig = ConfigUtils.addOrGetModule(config, AntibodyConfigGroup.class);
		AntibodyConfigGroup.AntibodyParams influenzaAntibodies = antibodyConfig.getOrAddParams(INFLUENZA_STRAIN);
		for (VirusStrain against : virusStrainConfig.getVirusStrains()) {
			influenzaAntibodies.getInitialAntibodies().put(against, 0.0);
			influenzaAntibodies.getAntibodyRefreshFactors().put(against, 1.0);
		}
	}

	/**
	 * Influenza disease-progression timing. Transitions fire once {@code daysSince >= transitionDay}, evaluated once per
	 * day, so everything is discretised to whole days.
	 */
	static Transition.Builder influenzaProgressionConfig(Transition.Builder builder) {

		return builder
				// latent period: shedding rises 0.5-1 d after challenge (Carrat 2008, doi:10.1093/aje/kwm375) -> contagious
				// from the next daily update
				.from(DiseaseStatus.infectedButNotContagious,
						to(DiseaseStatus.contagious, Transition.fixed(1)))

				// incubation: influenza A median 1.4 d, dispersion 1.51; 1.9 d / 1.22 without an outlier study
				// (Lessler 2009, doi:10.1016/S1473-3099(09)70069-6); minus the 1 d latent step -> median 1.0 d
				// asymptomatic branch: shedding 3-5 (up to 7) d (RKI AGI Saisonbericht 2018/19); median below Carrat's 4.8 d
				// mean because asymptomatic infections shed shorter (Ip 2017, doi:10.1093/cid/ciw841)
				.from(DiseaseStatus.contagious,
						to(DiseaseStatus.showingSymptoms, Transition.logNormalWithMedianAndSigma(1.0, Math.log(1.51))),
						to(DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(4.0, 2.0)))

				// onset -> admission median 3 d, IQR 1-4 (FluSurv-NET 2011-2019, doi:10.1093/ofid/ofad599); sigma 0.6 matches
				// median and Q3, a log-normal cannot match all three
				// symptomatic illness: shedding and illness end by day 6-7 after onset (Ip 2016, doi:10.1093/cid/civ909);
				// std 2.0 is an assumption
				.from(DiseaseStatus.showingSymptoms,
						to(DiseaseStatus.seriouslySick, Transition.logNormalWithMedianAndSigma(3.0, 0.6)),
						to(DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(6.0, 2.0)))

				// ward -> ICU: no influenza-specific evidence found; COVID placeholder kept (explicit assumption)
				// hospital stay: mean 5.6 d (SD 6.0), Germany 2022/23 InEK data (Meyer 2026, doi:10.1007/s40121-026-01384-7)
				.from(DiseaseStatus.seriouslySick,
						to(DiseaseStatus.critical, Transition.logNormalWithMedianAndStd(1.0, 1.0)),
						to(DiseaseStatus.recovered, Transition.logNormalWithMeanAndStd(5.6, 6.0)))

				// ICU stay: median 4 d, IQR 1-8 (van der Bie 2025, doi:10.3390/v17111467)
				// time to death in ICU: no evidence found -> same distribution (explicit assumption)
				.from(DiseaseStatus.critical,
						to(DiseaseStatus.seriouslySickAfterCritical, Transition.logNormalWithMedianAndSigma(4.0, ICU_LOS_SIGMA)),
						to(DiseaseStatus.deceased, Transition.logNormalWithMedianAndSigma(4.0, ICU_LOS_SIGMA)))

				// post-ICU ward stay: no influenza-specific evidence found; COVID placeholder kept (explicit assumption)
				.from(DiseaseStatus.seriouslySickAfterCritical,
						to(DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(7.0, 7.0)))

				// protection halves only 3.5-7 y after infection (Ranjeva 2019, doi:10.1038/s41467-019-09652-6):
				// no reinfection with the pooled strain within one simulated season
				.from(DiseaseStatus.recovered,
						to(DiseaseStatus.susceptible, Transition.fixed(365)));
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
		SnzCologneOpenProductionScenario.configureContactIntensitiesAndSeasonality(episimConfig);
		SnzCologneOpenProductionScenario.configureTracing(config, COLOGNE_FACTOR);

		// also sets the influenza progression config, season start and season policy
		configureInfluenza(config);

		PathogenConfigGroup pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);
		VirusStrainConfigGroup virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		assertThat(virusStrainConfig.getParams(INFLUENZA_STRAIN).getPathogen()).isEqualTo(INFLUENZA);
		assertThat(pathogenConfig.hasParams(INFLUENZA, true)).isTrue();
		assertThat(pathogenConfig.getParams(INFLUENZA, true).getRouteTransmissibility().getRespiratory()).isEqualTo(1.0);
		assertThat(pathogenConfig.getParams(INFLUENZA, true).getRouteTransmissibility().getDirectContact()).isEqualTo(0.0);
		assertThat(pathogenConfig.getParams(INFLUENZA, true).getSeriouslySickProbability(85)).isGreaterThan(0.0);
		assertThat(pathogenConfig.getParams(INFLUENZA, true).getSymptomaticIsolationProbability(30)).isEqualTo(0.75);
		assertThat(pathogenConfig.getParams(INFLUENZA, true).getSymptomaticIsolationStatus())
				.isEqualTo(EpisimPerson.QuarantineStatus.atHome);

		// effective (after hospitalFactor) hospitalisation risk of a symptomatic 70-year-old equals the CDC ratio
		assertThat(pathogenConfig.getParams(INFLUENZA, true).getSeriouslySickProbability(70) * episimConfig.getHospitalFactor())
				.isCloseTo(0.09091, within(1e-9));

		assertThat(episimConfig.getStartDate()).isEqualTo(SEASON_START);
		assertThat(episimConfig.getInfections_pers_per_day()).containsOnlyKeys(INFLUENZA_STRAIN);

		// the getParams / probability lookups above already exercise the config; full finalisation
		// validation (PathogenConfigGroup.checkConsistency) runs inside the scenario in runsOnCologneScenario().

		// SARS-CoV-2 defaults untouched
		assertThat(pathogenConfig.getParams(Pathogen.SARS_COV_2, false).getRouteTransmissibility().getDirectContact())
				.isEqualTo(0.0);
	}

	/**
	 * The real thing: the full Cologne open scenario with influenza as the only circulating virus.
	 */
	@Test
	public void runsOnCologneScenario() throws Exception {

		OutputDirectoryLogging.catchLogEntries();

		Injector injector = Guice.createInjector(
				Modules.override(new EpisimModule()).with(new SnzCologneOpenProductionScenario.Builder().build()));

		Config config = injector.getInstance(Config.class);
		configureInfluenza(config);   // before the runner is built - the contact model reads config at construction

		// <EPISIM_OUTPUT or test output>/<date>/INF-<NNNNN>/output, packed for the Episim viewer after the run
		ViewerOutput output = ViewerOutput.create("INF", Path.of(utils.getOutputDirectory()));
		output.configure(config);

		injector.getInstance(EpisimRunner.class).run(ITERATIONS);

		assertThat(output.runOutput().resolve(output.runId() + ".infections.txt")).exists();

		output.pack(config, "cologne", "influenza", ITERATIONS, "Köln");
		assertThat(output.visualizationWithSeeds().resolve("summaries/" + output.runId() + ".zip")).exists();
		assertThat(output.visualizationWithoutSeeds().resolve("summaries/0.zip")).exists();

		// TODO assert influenza actually circulated:
		//  - infectionEvents.txt has rows with virusStrain == "influenza", none with SARS_CoV_2
		//  - infections grow over the run
	}
}

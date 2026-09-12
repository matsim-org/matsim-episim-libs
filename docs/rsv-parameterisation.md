# RSV parameterisation and data sources for MATSim-EpiSim (Cologne scenario)
## JS: This document was created for source code validation purposes. It should not be considered the final source of truth without additional validation.

## 1. Executive summary

**Can the model be run for RSV today? Technically yes**, the configuration in section 7 validates and slots into the Cologne scenario without code changes (plus one optional CSV, section 2.5). **Epidemiologically, the run would not reproduce infant RSV burden**, and the reason is not primarily the parameters below — it is the **synthetic population itself**.

**Biggest blocker: the Cologne 25 % population does not represent infant childcare or infant households.** This review downloaded the population file (`cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt_split_grid.xml.gz`, 229,986 Köln agents in the 25 % sample) and the weekday events file and checked both directly:

| Check | Model (Köln, 25 % sample) | Reality (NRW / Köln, 2022) |
|---|---|---|
| Age-0 agents | 2,463 (×4 ≈ 9,852) | 9,811 births in Köln 2022 (Stadt Köln) — **count is right** |
| Age-0 agents attending `educ_kiga` on a weekday | **50.1 %** | **1.1 %** under-1s in Kindertagesbetreuung, NRW 1.3.2022 (IT.NRW) |
| Age-1 agents attending `educ_kiga` | 48.2 % | 27.7 % (IT.NRW) — still too high, less severely |
| Age-0 agents with **no adult in their household** | 25.3 % | not directly measurable, but implausible for infants |
| Age-0 agents **living alone** (household size 1) | 20.1 % | implausible for infants |

So a quarter of the model's infants cannot acquire RSV through the dominant real-world route — a parent or older sibling at home — because they have no co-resident adult in the synthetic population, and half of them are (implausibly) exposed to a `educ_kiga` contact pool that real under-1s essentially never enter. **This is a population/synthetic-plans defect, not a parameter problem**, and no choice of `PathogenParams` or `ContactTransmissionConfigGroup` can fully compensate for it. Section 2.1 has the full check and derivation; section 7 ships a **partial, config-only mitigation** (an age-banded contact CSV that suppresses `educ_kiga` transmission for age-0 agents specifically, `src/test/resources/rsv/rsv_kiga_direct_contact.csv`) — it removes the daycare artefact but does **not** fix the missing-adult-household defect, which stays as the primary open risk (section 6).

Four more issues, in decreasing order of how much they bias the result:

1. **The death-only-via-ICU gap is worse for RSV than for influenza.** Reproducing the observed German 60+ in-hospital mortality (9.4 %, Scholz 2024) through the ICU route alone would need `P(deceased|critical) ≈ 0.58`, an implausibly high ICU mortality. Quantified in section 4.4.
2. **Age bucket 0 hides a 3.5× internal gradient.** German claims data (Lade 2025) put RSV-specific hospital incidence at 35.6/1,000 person-years in months 1–3 of life versus 10.2/1,000 in months 7–12 — a single flat `age=0` value cannot separate the group that actually drives PICU admissions (76 % of Dutch RSV-PICU admissions are under 3 months, BRICK study) from the rest of the first year. Section 2.1 gives the population weighting.
3. **The route split (respiratory vs. direct contact) is only very weakly constrained by evidence**, and the model has no output field to validate it against beyond household secondary attack rate (SAR). Section 3.1/4.6.
4. **Reinfection within one season is now plausible and switches on a code path influenza never exercised**: with `recovered → susceptible` set to weeks rather than a year, agents can cycle back to `susceptible` mid-season. This is the single largest deliberate divergence from the influenza configuration (section 3.3, 4.3) and should be re-validated once implemented, since the influenza review did not exercise this regime.

**Recommended season and calibration target:** 2022/23 on the real Cologne mobility trace, same window as influenza (start 2022‑09‑26), for the same practical reasons influenza chose it (shared scenario setup, real inputs and real surveillance overlap). The RSV wave ran KW41/2022–KW3/2023 and peaked in KW49/2022, entirely inside the trace. Primary target: ICOSARI RSV-SARI hospitalisation incidence, all ages (the open RKI repository does **not** carry RSV by age group, unlike influenza). Secondary/derived: a 0–4y RSV-SARI series constructed from the open all-cause SARI 0–4 series × the RSV share of 0–4 SARI patients reported in the weekly ARE reports (section 2, section 4.1). Infants under 1 have **no open German incidence series for 2022/23** at all — this is itself part of the blocker above.

---

## 2. Which season to simulate

| Option | For | Against |
|---|---|---|
| **A. 2022/23 on the real trace (recommended)** | Same mobility/NPI inputs and shared scenario setup as [influenza's 2022/23 choice](influenza-parameterisation.md#2-which-season-to-simulate), so both pathogens can reuse the season-window helper (section 6, item 7). Wave onset KW41/2022, peak KW49/2022 (ICOSARI/AGI), fully inside the trace, which ends 2022‑12‑31. RSV-B dominated (Cai 2024, doi:10.2807/1560-7917.es.2024.29.13.2300465), so one pooled strain is defensible (section 3.2). | Atypical post-pandemic "immunity-debt" season: the 2021/22–2022/23 age distribution was unusually flat across 1–4y (normally protected toddlers behaved like naive infants), and severity rose relative to pre-COVID (ICU share 8.5 % vs. 6.8 %, ventilation 6.1 % vs. 3.8 %; Cai 2024). No nationwide RSV notification data exist for this season (mandatory reporting started July 2023) — only Saxony has covered RSV since 2002 and can serve as a secondary, single-Land comparison series only. |
| B. The out-of-season 2021 wave | Also inside the trace (weeks 35–50/2021); a natural experiment for the immunity-debt hypothesis, with an even more atypical, earlier and more concentrated onset. | Smaller, less representative of a "normal" future RSV season; the same lack of nationwide notification data applies; NPI effects (masks, reduced mobility) were still substantial in autumn 2021 and confound the mobility-vs-immunity story more than 2022/23 does. |
| C. A pre-COVID season | Epidemiologically "typical" (peak weeks 51–12, Cai 2024). | No Senozon Cologne mobility input exists before 2020; the same counterfactual-baseline problem the influenza doc noted for option B applies, only worse since ICOSARI RSV-SARI itself only starts in 2020/21. |

**Recommendation: option A**, `SEASON_START = 2022-09-26` (shared with influenza), run to about 2023‑01‑15.

**Calibration targets (option A):**

1. **Primary:** ICOSARI weekly RSV-SARI hospitalisation incidence per 100,000, **all ages only** (`SARI-Hospitalisierungsinzidenz.tsv`, `SARI == "RSV"`, `Altersgruppe == "00+"` — RSV, unlike influenza, is **not** broken down by age in the open repository), 2022‑W38 to 2023‑W04.
   - National values (00+): W40 0.4, W41 0.5, W42 0.9, W43 1.5, W44 2.6, W45 3.4, W46 5.0, W47 6.7, W48 7.6, W49 **8.1 (peak)**, W50 6.7, W51 5.4, W52 4.6, W01 4.8, W02 2.1. Season total 69.5/100k (2021/22: 51.8; 2023/24: 63.5).
   - Fitted on log-weekly incidence W40–W45: slope 0.462/week → **r = 0.066/d, doubling time 10.5 d**; W42–W47: r = 0.057/d (12.2 d). Both fits use the growth phase before the peak-week's known reporting noise.
   - RKI's own wave definition (virological sentinel) puts onset at **KW41/2022** and calls the season over by KW3/2023 (Cai 2024).
2. **Secondary / derived (shape and level, medium confidence):** a 0–4y RSV-SARI series is not directly published, but can be reconstructed: the open all-cause SARI 0–4 incidence (`SARI == "Gesamt"`, `Altersgruppe == "0-4"`) × the RSV share of 0–4y SARI patients reported as a percentage in the weekly ARE-Wochenbericht text/figures.
   - All-cause SARI 0–4 (open, per 100k/week): W44 89.6, W48 204.1, **W49 217.4 (peak)** — already above every pre-COVID 0–4 peak in the same series (2017/18–2019/20: 132–165/100k), consistent with the immunity-debt severity increase.
   - RSV share of 0–4 SARI (ARE-Wochenbericht, doi:10.25646/10757 KW44, doi:10.25646/10844 KW48): **46 % (KW44), 61 % (KW48)**.
   - Implied RSV-SARI 0–4 ≈ 41/100k (W44), ≈ 125/100k (W48). **Low-to-medium confidence**: this multiplies two different reporting streams and assumes the reported share is representative of the whole week; request the disaggregated series from RKI (FG36/NRZ Influenza) for a direct figure.
   - AGI sentinel RSV positivity (all ages): KW44 19 %, KW45 19 %, KW46 24 %, KW47 20 %, KW48 15 % — RSV predominantly detected in children up to 4y (ARE-Wochenbericht KW44/2022), and predominantly **RSV-B** in that age group this season.
   - **Infants under 1 year: no open German incidence series exists for 2022/23 at all.** This is the calibration-side face of the population defect in section 1/2.1: even if the model reproduced infant transmission correctly, there is no open national number to check it against for this season. Request age-disaggregated ICOSARI RSV-SARI (under-1, 1–4) and the NRZ virological sentinel line list from RKI.
3. **Implied reproduction number** during growth: R = (1 + r·θ)^k for a gamma generation time with shape k = mean²/sd² and scale θ = sd²/mean, using the W40–W45 fit (r = 0.066/d):

   | generation time used | R |
   |---|---|
   | 7.5 d, sd 3.5 (Vink 2014 pooled RSV serial interval) | **1.60** |
   | 8.4 d, sd 4.0 (PHIRST South Africa RSV serial interval, Cohen 2024) | **1.69** |
   | 6.0 d, sd 3.0 (lower bound, sensitivity) | 1.46 |

   Using the more conservative W42–W47 fit (r = 0.057/d) these drop to 1.50/1.57/1.39. **Target: R_eff ≈ 1.5–1.7 through November 2022.** This already includes pre-season immunity/immunity-debt, and is comparable in magnitude to influenza's 2022/23 target (1.2–1.35) despite the qualitatively different (contact-heavy) transmission route — see section 3.1 on why the two pathogens can still share the Cologne `calibrationParameter`.

As with influenza, `strain.setInfectiousness(...)` and `episimConfig.setCalibrationParameter(...)` enter the infection probability as a product with the pathogen's `routeTransmissibility`, so only the combination is identifiable; keep `calibrationParameter` fixed (it is shared with influenza and COVID in this scenario) and fit `infectiousness` together with the import level and the direct-contact weight (section 4.6).

---

## 3. Parameter table

Confidence: **H** = high, **M** = medium, **L** = low, **NE** = no evidence found (explicit assumption). "Eff." means the value after `hospitalFactor` (0.5 in Cologne); the code value = eff. / `hospitalFactor`, same convention as the influenza doc.

### 3.1 `PathogenConfigGroup.PathogenParams` (age-dependent)

Age bands: **0** (infants), **1** (ages 1–4, toddlers), **5** (ages 5–59, a broad low-risk band — see the note below on why this band is coarser than influenza's), **60** (60–79), **80** (80+).

| Java API | Proposed (`age: value`) | Denominator / notes | Source | Conf. |
|---|---|---|---|---|
| `setShowingSymptomsProbabilityByAge` | `0: 0.67, 1: 0.48, 5: 0.26, 60: 0.35, 80: 0.45` | P(symptomatic \| PCR-confirmed infection), by age. `5` pools PHIRST's 5–12, 13–18, 19–44 and 45–64 bands (75/285 symptomatic); `60`/`80` are explicit assumptions (PHIRST ≥65 is 4/6, too small to use directly, and RKI notes reinfections in adults are "often mild or subclinical", pulling the other way from age-related severity in the very old). | Cohen 2024 (PHIRST South Africa), doi:10.1038/s41467-023-44275-y | M (0/1/5), L (60/80) |
| `setSeriouslySickProbabilityByAge` (eff.) | `0: 0.1025, 1: 0.0108, 5: 0.0005, 60: 0.15, 80: 0.28` | P(hospitalised \| symptomatic), by age. Chained derivation in section 4.2 (age 0 from a European birth-cohort's own hospitalisation and active-surveillance-infection incidence; age 1 from German claims hospitalisation ÷ South African infection incidence; 5 is a coarse NE placeholder; 60/80 split an underdetection-corrected German 60+ estimate). Code value = eff. / `hospitalFactor`. | Wildenbeest 2023 (RESCEU) doi:10.1016/s2213-2600(22)00414-3; Lade 2025 doi:10.1007/s15010-024-02391-x; Cohen 2024; Scholz 2024 doi:10.1007/s40121-024-01006-0; Falsey 2005 doi:10.1056/nejmoa043951 | M (0), L (1, 60, 80), NE (5) |
| `setCriticalProbabilityByAge` | `0: 0.08, 1: 0.051, 5: 0.30, 60: 0.161, 80: 0.15` | P(ICU \| hospitalised). `0`/`1` are direct German InEK data (nationwide, unselected). `5` is a US adult ICU-among-hospitalised proxy (band 5–59's hospitalised sub-population is adult-dominated since paediatric hospitalisation is rare past infancy). `60` is direct German data; `80` an explicit assumption (US data show ICU-among-hospitalised *declining* at the oldest ages, plausibly reflecting triage/care-goal decisions, not falling severity). | Wick 2023 doi:10.1111/irv.13211; Liang 2025 doi:10.1007/s40121-025-01255-7; Scholz 2024 | M (0/1/60), L (5/80) |
| `setDeathProbabilityByAge` | `0: 0.01, 1: 0.01, 5: 0.05, 60: 0.15, 80: 0.22` | P(deceased \| critical), i.e. **P(death \| ICU admission)**. `0`/`1` from a Dutch national PICU cohort (3/423 deaths, all with severe comorbidities — the true rate is very low and 0.01 already rounds it up from 0.007 to avoid false precision). `5` is a coarse assumption. `60`/`80` bridge Falsey 2005's all-ward in-hospital mortality (8 %, includes non-ICU deaths this model cannot represent) and a single-centre ICU-cohort comparator (~19.4 %); **the death-only-via-ICU structural gap is large for RSV, see section 4.4.** Non-zero, so the `critical → deceased` transition (section 3.3) is required. | Phijffer 2025 (BRICK) doi:10.1097/inf.0000000000004712; Falsey 2005; Grangier 2024 doi:10.1038/s41598-024-55378-x | L–M |
| `setRouteTransmissibility` | `respiratory=1.0;directContact=0.6` | RSV spreads by droplets **and** by direct/self-inoculation contact; aerosol transmission is considered comparatively less important for RSV than for influenza (Saravanos 2026 review). This is a **calibration prior**, not a measured split — the natural target is household SAR (section 2.5/4.6). | Saravanos 2026 doi:10.1111/irv.70270; Hall 1980/1981 (survival/inoculation studies, section 4.5) | L |
| `setSymptomaticIsolationProbabilityByAge`, `setSymptomaticIsolationStatus` | `0: 0.85, 1: 0.70, 5: 0.30, 60: 0.40, 80: 0.50`, `atHome` | P(isolate at home \| symptom onset). `atHome` (not `full`) is deliberate: it keeps home activities, i.e. a sick child still infects their own household — the dominant real RSV transmission situation (section 4.7) — while removing `educ_kiga`/school/work/leisure. Infants are already mostly home-based at baseline, so their isolation probability mainly matters for the small non-home share (`errands`, `visit`). Toddler/school-age values reflect the informal Kita-exclusion norm (RKI Ratgeber, no legal ban) tempered by known adult presenteeism (≥68.6 % of German employees went to work sick at least once in 2016, LPP/IAB) for the parents making the decision. **All values are explicit, low-confidence assumptions**: no direct German measurement of RSV-specific isolation compliance by age was found. | RKI-Ratgeber RSV; IAB-Forum (Dietrich & Hiesinger 2020, LPP 2016/17); PAPI study (parental work loss 64.3 % hospitalised / 37.5 % outpatient RSV cases, cited in economic-burden literature) | L |

**Why band 5 is coarser than the age structure elsewhere (5–59, not split further).** Every source in this review that reports RSV hospitalisation risk with real statistical power is either paediatric (infants/toddlers) or elderly (60+/75+); none of the German or European sources found give a usable hospitalisation-per-population or hospitalisation-per-infection estimate for school-age children, working-age adults, or the 45–59 group. Rather than presenting five-way-split numbers with no evidence behind four of the five cells, this review pools them into one explicitly low-confidence "background" band — narrower splits (`5`, `19`, `45`) can be added once the RKI-partnership NRZ sentinel line list (age × week × subtype) is available.

### 3.2 `VirusStrainConfigGroup.StrainParams`

| Java API | Proposed | Notes | Source | Conf. |
|---|---|---|---|---|
| `setInfectiousness` | `1.0` **placeholder** | Not an estimate. Fit jointly with `directContact` weight and import level to r ≈ 0.06/d (R ≈ 1.5–1.7), section 2. | ICOSARI RSV-SARI (00+) growth fit | — (calibrate) |
| `setFactorSeriouslySick`, `setFactorCritical` | `1.0` | One pooled strain = pathogen baseline (see subtype discussion below). | — | — |
| `setAgeSusceptibility` | `0: 5.0, 1: 7.1, 5: 3.5, 13: 2.9, 19: 2.6, 45: 2.7, 65: 1.0` (linear between keys) | **Household-exposure-controlled** relative odds of acquiring infection, by contact age, ≥65y = reference. This is the RSV analogue of the influenza doc's Sauter 2026 household hazard ratios: PHIRST's household-cumulative-infection-risk model reports the odds of a household contact acquiring RSV by their own age, adjusted for the index case's characteristics — i.e. it already separates "gets exposed more" from "is more susceptible per exposure" as far as the data allow. Toddlers (1–4y) are the single most susceptible group; the ≥65y group is least susceptible **per household exposure**, even though raw community infection incidence is higher in children mainly because of higher contact rates, which the contact model already represents separately. | Cohen 2024 (PHIRST), Table 3 (household contact age, adjusted OR) | M |
| `setAgeInfectivity` | `0: 1.3, 1: 1.8, 5: 1.5, 19: 1.0, 65: 0.7` (linear between keys) | Children shed higher viral loads for longer than adults or the elderly (in-vivo kinetics modelling: higher peak viral load and longer shedding in children, Li 2024 preprint) and are disproportionately the household index case (PHIRST: individuals 1–12y = 55 % of index cases; Kenya household cohort: symptomatic individuals 2.5–6.7× more infectious than asymptomatic, school-going siblings introduce RSV into 73 % of within-household infant infections). No single quantitative infectivity-fold-change study exists across this whole age range, so **these numbers translate several qualitative/partial findings into one curve and carry lower confidence than `ageSusceptibility`.** Infants (0) are set below toddlers (1) despite their very long shedding duration because much of an infant's real-world contact is already 1:1 caregiver contact captured through the home container and `HouseholdSusceptibility`, not through broad social mixing. | Li 2024 (preprint) doi:10.1101/2024.11.14.24317347; Cohen 2024; Munywoki 2014 doi:10.1093/infdis/jit828; Kombe 2019 doi:10.1016/j.epidem.2018.12.001 | L |

**Immunity debt (2020–22 NPI period), not applied by default.** After roughly two seasons with minimal RSV circulation, several birth cohorts reached 2021 and 2022/23 without prior exposure. Cai 2024 documents this directly for 2021: RSV positivity rates were "similar in 1–<2, 2–<3, 3–<4 and 4–<5-year-olds" that year — an unusually flat age distribution, since normally-protected 2–4-year-olds behaved as susceptibly as RSV-naive 1-year-olds. The model has no pre-season immune state besides `ageSusceptibility`, so the only way to encode this is a temporary, season-specific uplift of `ageSusceptibility` for ages roughly 1–3 on top of the baseline PHIRST-derived curve above (e.g. ×1.3–1.5, chosen to flatten the 1→3y gradient toward the 2021 pattern). **This review does not bake that uplift into the numbers in section 7**, because the size of the multiplier is this reviewer's own translation of a qualitative finding (a flattened age curve) into a number, not a published estimate — flagged as an explicit recommendation for anyone running the immunity-debt-affected seasons (2021 or 2022/23) rather than as a default.

**Subtypes: RSV-A and RSV-B — recommend one pooled strain.** RSV-A dominated the 2021 wave (lineages GA2.3.5/GA2.3.6b), RSV-B the 2022/23 wave (GB5.0.5a) (Cai 2024). Kombe 2019's household model found RSV-B is more often the one introduced into a household while RSV-A spreads faster once inside — a real transmission-dynamics difference — but, exactly as for influenza's B/Victoria, this model's one-pathogen immunity limitation (section "Hard constraint" in the task brief) means two independent `VirusStrain`s under one `Pathogen` would infect the same people independently with no cross-protection, which is worse than pooling. **One pooled strain**, matching the influenza decision and for the same reason.

### 3.3 Disease progression (`rsvProgressionConfig`)

All transitions are whole days (`daysSince >= transitionDay`, evaluated once per day), same discretisation caveat as influenza.

| Transition | Proposed | Evidence (with dispersion) | Conf. |
|---|---|---|---|
| infectedButNotContagious → contagious | `fixed(2)` | Total incubation median 4.4 d (Lessler 2009, RSV pooled estimate). No RSV-specific *latent-only* estimate exists; this splits the total roughly in half between a latent stage and a presymptomatic-contagious stage, the same structural move influenza made with Carrat's 0.5–1 d. **Explicit compromise.** | NE (split), M (total incubation) |
| contagious → showingSymptoms | `logNormalWithMedianAndSigma(2.4, ln 1.24 = 0.215)` | Remainder of the 4.4 d median incubation after the 2 d latent step; dispersion 1.24 (1.13–1.35) carried over from the total-incubation estimate, same convention as influenza's Lessler-derived transition. | M |
| contagious → recovered (asymptomatic branch) | `logNormalWithMeanAndStd(7.8, 4.0)` | Asymptomatic shedding mean 7.8 d (Munywoki 2015, doi:10.1017/S0950268814001393); std widened beyond the source's own narrower spread as an explicit assumption, reflecting the very wide range PHIRST separately reports for shedding overall (<1–50 d). | M (mean), NE (std) |
| showingSymptoms → seriouslySick | `logNormalWithMedianAndSigma(3.5, 0.514)` | Onset-to-admission median 3 d, IQR 2–4 d in hospitalised **adults** (Malosh 2017, doi:10.1016/j.jcv.2017.09.001); infant bronchiolitis typically progresses to admission over a similar 3–5 d window as lower-respiratory disease develops. Median 3.5 d splits the difference; σ from the adult IQR. **US adult substitute blended with a qualitative infant statement — no single-population estimate spans both.** | L–M |
| showingSymptoms → recovered | `logNormalWithMeanAndStd(11.0, 5.0)` | Total symptomatic shedding mean 13.5 d (Munywoki 2015) minus the ≈2.4 d already spent in the presymptomatic `contagious` stage ≈ 11.1 d; std widened as an explicit assumption. Under current code this doubles as the non-hospitalised illness/isolation duration, exactly as for influenza. | M (mean), NE (std) |
| seriouslySick → critical | `logNormalWithMedianAndStd(1.0, 1.0)` | **No RSV-specific evidence found.** COVID placeholder kept (same as influenza). | NE |
| seriouslySick → recovered | `logNormalWithMeanAndStd(5.0, 5.0)` | Cross-age compromise: German infant hospital LOS 4.5 d (SD 3.3), German elderly (60+) pneumonia LOS mean 10.4 d (median 8, IQR 6–10). Weighted toward the infant value because infants dominate RSV hospitalisation **counts** even though elderly stays are individually longer — the same durations-cannot-depend-on-age problem flagged in section 4.3. | M (components), NE (blend) |
| critical → seriouslySickAfterCritical | `logNormalWithMedianAndSigma(5.0, 0.727)` | PICU stay median 5 d, IQR 3–8 d (BRICK, Netherlands, n = 423, Phijffer 2025). σ = ln(8/3)/1.349. Infant PICU data used as primary because RSV-critical admissions in this model are overwhelmingly infant (per the severity table above); no comparably sized adult RSV-ICU-LOS distribution (only a mean, Liang 2025) was found to blend in. | M |
| critical → deceased | same distribution as the ICU stay | **No RSV time-to-death evidence found.** Assumption, same convention as influenza. | NE |
| seriouslySickAfterCritical → recovered | `logNormalWithMedianAndStd(7.0, 7.0)` | **No RSV-specific evidence found.** COVID placeholder kept (same as influenza). | NE |
| recovered → susceptible | `logNormalWithMedianAndStd(90, 60)` | **The RSV-distinctive transition.** Adult re-challenge data: about half of previously-infected adults were reinfected by 2 months and two-thirds by 8 months after natural infection, and "even in subjects with the highest antibody levels, the risk of reinfection was 25 %" (Hall 1991, doi:10.1093/infdis/163.4.693). A within-epidemic reinfection model estimates prior infection reduces susceptibility by only ≈47 % (homologous) / 39 % (heterologous) (Kombe 2019). Median 90 d with a wide std places substantial probability mass in the 60–240 d range (matching Hall's half/two-thirds points) while keeping a real tail beyond one season. **This is the single largest deliberate divergence from influenza's `fixed(365)`** (no reinfection within a season): RSV protection is short and partial enough that within-season reinfection, especially of toddlers with high exposure, is plausible and arguably necessary to represent. | M |
| `episimConfig.setDaysInfectious` | keep `Integer.MAX_VALUE` | Same reasoning as influenza: the infectious window is bounded by the `contagious`/`showingSymptoms` states and by isolation. | M |

**Infectivity curve `NormalDistribution(0.5, 2.6)` around symptom onset (COVID, arXiv:2007.06602).** For RSV this curve is likely too narrow in the wrong direction from influenza's problem: influenza's real infectious period is *shorter* than the curve assumes, RSV's is plausibly *longer*, especially for infants. Viral-load kinetics modelling (Li 2024 preprint) puts peak viral load in children about 3.1 days after infection versus symptom onset around 3.5 days post-infection — i.e. **peaking slightly *before* symptom onset** in children, whereas in elderly patients peak viral load comes roughly 1.5 days *after* onset (adults/elderly time-to-peak ≈5 d vs. children ≈3 d). The *width* of the true infectivity window is not directly reported by that model, but Munywoki's shedding-duration data (infant mean total shedding 18 d, symptomatic episodes 13.5 d) implies a window several times longer than the ≈5 d of concentrated density the fixed COVID curve effectively represents (±1 SD ≈ 2.6 d each side). **Net effect: the fixed curve likely understates infant infectious duration substantially, and slightly mistimes the peak in the opposite direction from influenza (too late for children, plausibly too early for the oldest adults).** No RSV-specific infectivity-over-time curve fit was found in the literature reviewed; this is flagged as a gap (section 6), not fixed here, exactly as for influenza.

### 3.4 Transmission routes

See section 3.1 (`routeTransmissibility`) for the pathogen side. Contact side (`ContactTransmissionConfigGroup`):

| Item | Proposed | Notes | Conf. |
|---|---|---|---|
| `defaultTransmissionWeights` | `respiratory=1.0;directContact=0.2` | Applies to any contact pair without a specific override; low direct-contact weight for non-close settings (pt, shops, errands, work), mirroring the general finding that RSV close-contact transmission concentrates in household- and childcare-like settings. | L |
| `home`/`home` `contactPair` | `respiratory=1.0;directContact=1.0` | Home contacts are the most physical contact type measured in Germany: 82 % of German POLYMOD home-diary contacts are physical, versus 56 % at work, 41 % in transport (this review's own re-analysis of the German subsample of POLYMOD, `2008_Mossong_POLYMOD_*`, doi:10.5281/zenodo.3874557 — see section 4.7). This pair also applies to `quarantine_home` (mapped to `"home"` in `SymmetricContactModel` before `resolve()` is called), so an `atHome`-isolated sick child still transmits to co-resident family at this weight — the intended, key RSV transmission situation. | M (physical-contact share), L (translation to a transmission weight) |
| `educ_kiga`/`educ_kiga` `contactPair` | age-band CSV, `src/test/resources/rsv/rsv_kiga_direct_contact.csv` (`ageBands = 0, 1, 3, 18`) | `respiratory=1.0;directContact=0.6` for all age-band pairs **except any pair involving band 0**, which is `respiratory=0.0;directContact=0.0`. This is the mitigation for the population defect in section 1/2.1: it does not represent real infant-Kita transmission (there essentially isn't any), it suppresses the model's erroneous placement of half the age-0 population into `educ_kiga` containers. The residual over-attendance at age 1–2 (48–51 % modelled vs. 27.5–29.5 % real, IT.NRW) is **not** corrected here (see "gaps"). `0.6` for the non-suppressed cells is the same order as the default `educ_*` intensity choice made for other pairs, since no age-structured evidence for daycare-specific direct contact was found beyond the general closed-setting attack-rate literature (section 3.5). | L (weight value), H (that the age-0 suppression is needed — verified directly against the downloaded population) |
| Other pairs (`visit`, `educ_primary`, …) | left at `defaultTransmissionWeights` | No pair-specific evidence found; an explicit gap, not a decision. | NE |

### 3.5 Seasonality

| Item | Finding | Source | Conf. |
|---|---|---|---|
| Empirical forcing | Lower temperature and higher relative humidity are associated with higher RSV activity across 13 European countries, with a **14-day lag** (roughly twice the RSV serial interval); season duration ranges 8–18 weeks; onset is 3.8 weeks later in eastern than western Europe. No Germany-specific amplitude (R0max/R0min-style) estimate, unlike influenza's Shaman 2010 US figure, was found for Central Europe. | Li 2022 doi:10.2807/1560-7917.es.2022.27.16.2100619 | M (association), NE (amplitude) |
| Generic model range | Fitting SIRS-type models to historical monthly RSV hospital case series gives R0 in the 1.2–2.1 range depending on model structure — the same order as this review's own growth-rate-implied R (1.5–1.7, section 2), which is reassuring but not independent confirmation. | Weber 2001 doi:10.1016/s0025-5564(01)00066-9 | L |
| Model mechanism, applied to RSV specifically | `InfectionModelWithSeasonality`/`AgeAndProgressionDependentInfectionModelWithSeasonality` damp the **respiratory route only** (`ciCorrection · mask.shedding · mask.intake · indoorOutdoorFactor`, verified in `calcInfectionProbability`). With `routeTransmissibility.directContact = 0.6` (section 3.1), a substantial share of RSV's simulated transmission bypasses seasonal damping entirely — **the total seasonal forcing the model applies to RSV is therefore weaker than the nominal container `seasonality`/outdoor-fraction settings would suggest**, and weaker than it is for influenza (`directContact = 0` there), even with identical container settings. This is a direct, code-verified consequence of the route split, not a guess. | code: `AgeAndProgressionDependentInfectionModelWithSeasonality.calcInfectionProbability` | H (mechanism), NE (how much it matters quantitatively — would need a per-route infection counter, section 4.8) |
| 2022/23-specific caveat | The 2021 and 2022/23 waves both started unusually early (KW35, KW41) relative to the pre-COVID median onset (KW51), and the age-distribution flattening (immunity debt) plausibly dominates over normal seasonal timing in these two seasons specifically. Calibrating a seasonality *amplitude* against 2022/23 risks conflating immunity-debt effects with weather-driven forcing. | Cai 2024 | M |

**Recommendation:** reuse influenza's fix for the Cologne test copy (schools/holidays/tracing, weather-driven `leisureOutdoorFraction` via `EpisimUtils.getOutDoorFractionFromDateAndTemp2` if available) without a separate RSV-specific seasonality term for a single-season 2022/23 run, but flag the direct-contact-route damping gap above for anyone calibrating amplitude across multiple seasons.

### 3.6 Prevention products (for scenario work, not the 2022/23 calibration season — nothing below was in use before the season starts)

| Product | Efficacy / effect | STIKO status (Germany) | How it maps onto the model | Conf. |
|---|---|---|---|---|
| **Nirsevimab** (long-acting monoclonal antibody, infants) | HARMONIE (pragmatic RCT, France/Germany/UK): 83.2 % (67.8–92.0) efficacy against RSV-hospitalisation to 180 days (Drysdale 2023, doi:10.1056/nejmoa2309189). MELODY: 74.5 % (49.6–87.1) against medically-attended LRTI to 150 days (Hammitt 2022, doi:10.1056/nejmoa2110275). Real-world pooled effectiveness across 43 observational studies: 80.9 % against hospitalisation (Oliva 2026, doi:10.1080/21645515.2026.2690750). Germany's own 2023/24→2024/25 comparison: infant (<1y) notification incidence fell 54 % (2,291→1,045/100k) and hospitalised infant notifications fell from 6,482 to 2,899 after nirsevimab's first full season (Schönfeld 2025, doi:10.3238/arztebl.m2025.0111). | STIKO recommendation since 27.6.2024 for all newborns/infants in their first RSV season (EB 26/2024, doi:10.25646/12198) — **not yet in effect for 2022/23**. | The antibody model only scales severity (`AntibodyDependentTransitionModel.getSeriouslySickFactor`, inherited fact from the influenza review, section 1). Nirsevimab's own trial endpoints are hospitalisation/severe-LRTI, i.e. dominantly a severity effect, so a severity-only representation (raised `initialAntibodies` for age-0 recipients, time-limited to roughly a 5-month protection window matching the trial follow-up) is a reasonable first approximation. **What this misses:** nirsevimab plausibly also reduces onward transmission from protected infants (a `driving household transmission` mechanism this review documents in section 3.2), which a severity-only antibody effect cannot represent — the population-level impact would likely be understated. | M (efficacy figures), L (model mapping) |
| **Maternal vaccination** (RSVpreF, Abrysvo) | MATISSE trial: 81.8 % (40.6–96.3) efficacy against severe MA-LRTI to 90 days, 69.4 % to 180 days (Kampmann 2023, doi:10.1056/nejmoa2216480). | **No STIKO recommendation as of this review** (RKI FAQ, Stand 19.2.2026): data judged insufficient; re-evaluation planned after the second nirsevimab season completes, expected mid-2026. | Not usable without code changes today (`VaccinationConfigGroup`/`VaccinationType` would need a maternal-to-infant pathway, which does not exist; a pregnant-person vaccination cannot currently confer protection on a different, younger `EpisimPerson`). Document only. | M (efficacy), H (not implementable without code changes) |
| **Older-adult vaccines** (Arexvy/RSVPreF3 OA, Abrysvo) | Papi 2023 (AS01E-adjuvanted, GSK): 82.6 % (57.9–94.1) efficacy against RSV-LRTD, one season (doi:10.1056/nejmoa2209604); AReSVi-006 extension to three seasons (Ison 2025, doi:10.1016/s2213-2600(25)00048-7). Walsh 2023 (Pfizer bivalent): 66.7 %/85.7 % against RSV-LRTD with ≥2/≥3 symptoms (doi:10.1056/nejmoa2213836). | STIKO standard recommendation for ≥75y and risk-based for 60–74y with serious comorbidities, since 8.8.2024 (EB 32/2024, doi:10.25646/12470) — **not yet in effect for 2022/23**. | Same antibody-model limitation as nirsevimab: severity-only representation via `initialAntibodies` for the 60+/80+ bands is possible; the vaccines' own primary endpoint (RSV-LRTD, a severity/clinical-threshold outcome) fits this mechanism better than nirsevimab's broader population effect does, so the mismatch here is smaller. | M (efficacy), L (model mapping) |

**What the antibody-severity-only mechanism implies for all three products together:** none of them can reduce simulated *transmission* in this model without a code change (raising `ageSusceptibility`/`ageInfectivity` for vaccinated sub-populations, which the current `VaccinationConfigGroup`/antibody wiring does not connect to). Any scenario using these products for policy comparison should report this limitation alongside the results, exactly as the influenza doc flags for its own vaccination section (3.5).

### 3.7 Seeding / disease import

| Item | Proposed | Source | Conf. |
|---|---|---|---|
| Shape | Ramp 1 → 3 in-sample agents/day from `SEASON_START` (2022‑09‑26) to 2022‑10‑24 (≈4 weeks, matching influenza's own segment boundary), then hold at 3/day through 2022‑12‑31. Far below the smoke-run placeholder's ~120/day (`diseaseImport.tsv` of the previous `RsvCologneScenarioTest` run), which the task brief already flagged as implausible. | Consistency with the observed r ≈ 0.06–0.07/d growth (section 2) — the import should be a small, roughly-constant trickle that local transmission then amplifies, not itself drive the visible growth. | L |
| Scaling | × `cologneFactor` 0.5 → the values above are already the in-25 %-sample per-day count (`interpolateImport(..., COLOGNE_FACTOR, ..., a=2.0, b=6.0)` gives 1→3/day in-sample); real-population equivalent ≈ 4–12 infections/day. | code convention of `SnzCologneOpenProductionScenario`, same as influenza | L |
| Level | Calibration prior, fit jointly with `infectiousness` and `routeTransmissibility.directContact`. **No evidence found** for a Cologne-specific RSV importation rate. | — | NE |
| After 2022‑12‑31 | Last value (3/day) persists (`EpisimUtils.findValidEntry`), same as influenza. | code | H |

---

## 4. Derivation appendix

### 4.1 The 0–4y RSV-SARI proxy series
ICOSARI's open repository carries RSV-SARI only as `Altersgruppe == "00+"`; it does carry the age-split `"Gesamt"` (all-cause SARI) series by age, including `"0-4"`. The RSV share of 0–4y SARI patients is reported as a rounded percentage in the RKI weekly ARE-Wochenbericht text (not as a machine-readable series): 46 % in KW44/2022, 61 % in KW48/2022. Multiplying the open 0–4 all-cause SARI incidence (89.6/100k in W44, 204.1/100k in W48) by these shares gives 41.2/100k and 124.5/100k respectively. This is a **derived, not a published, series** — treat it as shape-only, medium-to-low confidence, and prefer requesting the true disaggregated ICOSARI RSV-SARI-by-age series from RKI over using this proxy for a primary calibration target.

### 4.2 Hospitalisation given symptomatic infection, by age
**Age 0.** The RESCEU European birth-cohort study (Wildenbeest 2023) followed 9,154 healthy term infants for their first year and separately ran a nested active-surveillance cohort (n = 993) with fortnightly virological sampling regardless of symptoms:

- P(hospitalised | in cohort) = 1.8 % (whole cohort, n = 9,154)
- P(any confirmed RSV infection | in nested surveillance cohort) = 26.2 % (n = 993)

Treating these as estimates of the same underlying population (reasonable, since RESCEU's own stated purpose for the nested cohort was to estimate exactly this multiplier):

P(hospitalised | infected) = 0.018 / 0.262 = **0.0687**

Combined with PHIRST's P(symptomatic | infected, age <1) = 8/12 = 0.67 (South African substitute, flagged):

P(seriouslySick | showingSymptoms, age 0) = 0.0687 / 0.67 = **0.1025** (eff.), code value 0.1025/0.5 = **0.2051**.

**Age 1 (1–4y band).** German claims data (Wick 2023, InEK, nationwide, unselected, 2022): P(hospitalised | in population, age 1–2) = 4.1/1,000 = 0.0041. No matching German infection-incidence figure exists; using PHIRST's South African infection incidence for age 1–4 (79.1 per 100 person-years = 0.791/yr) and symptomatic fraction (45/93 = 0.484):

P(hospitalised | infected) = 0.0041 / 0.791 = 0.00518
P(seriouslySick | showingSymptoms, age 1) = 0.00518 / 0.484 = **0.0108** (eff.), code value **0.0216**.

**Ages 60–79 / 80+.** Scholz 2024's own comparison of coded vs. sentinel-triangulated RSV burden estimates a roughly sevenfold underdetection for 60+ hospitalisations between the pre- and post-pandemic seasons; applying this to the coded 2022/23 figure (51.5/100,000):

true P(hospitalised | in population, 60+) ≈ 51.5/100,000 × 7 = 0.0036

Using Falsey 2005's US healthy-elderly infection incidence (3–7 %/year, midpoint 5 %) as an infection-incidence proxy and this review's own assumed symptomatic fraction for the 60-band (0.35, section 3.1):

P(hospitalised | infected) = 0.0036 / 0.05 = 0.072
P(seriouslySick | showingSymptoms, 60+ combined) = 0.072 / 0.35 = **0.206** (eff.)

This combined 60+ figure is then split, as an explicit assumption with no age-disaggregated source, into 60–79: eff. 0.15 (code 0.30) and 80+: eff. 0.28 (code 0.56), centred around the blended value and consistent with Scholz's qualitative statement that hospitalisation share and mortality are both highest in the 80+ group. **This whole chain (US infection incidence × assumed symptomatic fraction × a sevenfold underdetection factor) compounds three separate low-confidence steps and should be treated as an order-of-magnitude placeholder, not a calibrated value.**

### 4.3 Population weighting for age bucket 0
German claims data give RSV-specific hospitalisation incidence per 1,000 person-years by month of life, 2014–2019 (Lade 2025):

| Months of life | Incidence /1,000 PY |
|---|---|
| 1–3 | 35.55 |
| 4–6 | 24.62 |
| 7–12 | 10.21 |
| **1–12 (annual average)** | **20.15** |

Since births are approximately uniform across the year and the model's `age == 0` bucket covers the entire first year of life, the population-weighted average that bucket 0 must carry is simply the uniform average across months of life:

P_bucket0 = (1/12) · Σ_{m=1}^{12} P(month m)
        ≈ (3 × 35.55 + 3 × 24.62 + 6 × 10.21) / 12 = 241.77 / 12 = **20.15/1,000 PY**

This exactly reproduces Lade's own directly-reported annual figure (20.15/1,000), which is a useful internal-consistency check on the weighting method. **What this hides:** months 1–3 alone (35.55/1,000) carry 3.5× the risk of months 7–12 (10.21/1,000) — a single flat `age=0` parameter necessarily under-represents the 0–2-month group (which BRICK separately shows accounts for 75.7 % of Dutch RSV-PICU admissions among all PICU-admitted infants) and over-represents the 7–11-month group by a similar factor. The same asymmetry likely applies to severity (PICU risk), not just hospitalisation risk, and the model cannot separate them without a code change to make `Transition`/`decideTransitionDay` age-aware (see the "progression durations cannot depend on age" point below and in section 6).

### 4.4 The death-only-via-ICU gap, quantified
`AbstractProgressionModel` only allows death via the `critical → deceased` transition, i.e. `P(deceased) = P(critical | seriouslySick) × P(deceased | critical)`. For the German 60+ population in 2022/23 (Scholz 2024): ICU share among hospitalised = 16.1 %, **observed in-hospital mortality = 9.4 %**. Solving for the implied ICU mortality needed to reproduce the observed figure through the ICU route alone:

P(deceased | critical) = 0.094 / 0.161 = **0.584**

This is implausibly high for ICU mortality (typical RSV/influenza ICU mortality figures in the literature reviewed here sit around 15–25 %). Using a realistic value instead:

| Assumed P(deceased\|critical) | Model-implied in-hospital mortality (60+) | Observed |
|---|---|---|
| 0.15 | 2.4 % | 9.4 % |
| 0.20 | 3.2 % | 9.4 % |
| 0.25 | 4.0 % | 9.4 % |

At any realistic ICU mortality, the ICU-only route **under-counts 60+ in-hospital deaths by roughly a factor of 2.5–4×**, i.e. most elderly RSV deaths in reality do not pass through this model's `critical` state at all, the same structural conclusion the influenza doc reached (its own factor was closer to 3×) but somewhat larger here because RSV's German hospital mortality (9.4 %) is higher relative to its own ICU share than influenza's was. **Recommendation, same as influenza's:** keep the derived `deathProbabilityByAge` values with the documented ICU-mortality semantics, and compare model deaths only against ICU/critical-care deaths, never against total in-hospital or all-cause mortality.

### 4.5 Route-of-transmission evidence quality
The route split (`respiratory` vs. `directContact`) rests on qualitative and indirect evidence only:
- A 2026 systematic review states aerosol transmission is "considered less important for RSV" than for influenza or SARS-CoV-2, with spread "via contact and close-range droplets" (Saravanos 2026).
- Classic inoculation experiments (Hall 1981, doi:10.1128/iai.33.3.779-783.1981) show adult volunteers can be infected via the nose or eye with equal sensitivity, but the mouth is a poor route — relevant to self-inoculation via hands but not a transmission-route split per se.
- Environmental-survival experiments (Hall 1980, doi:10.1093/infdis/141.1.98) found RSV in infant secretions survives on countertops up to 6 h, rubber gloves 1.5 h, gowns/tissue 30–45 min, skin 20 min, and remains transferable from contaminated surfaces to hands for up to 25 min — evidence for the *plausibility* of fomite/contact transmission, not a quantified share of overall transmission attributable to it.

**No study found gives a direct quantitative respiratory-vs-contact transmission split for RSV.** The `directContact = 0.6` value in section 3.1 is therefore explicitly a calibration starting point, to be fit against household SAR (section 4.6), not a literature estimate.

### 4.6 Route-weight calibration via household secondary attack rate
**Target.** The pooled household RSV SAR from a 2026 systematic review and meta-analysis (38 studies, 30 populations, 1956–2023, 12 countries): **23.8 % (95 % CI 16.5–30.0 %) across all ages, 26.8 % (15.5–38.2 %) for children under 5** (Saravanos 2026). Stratified by index-case age specifically, the South African PHIRST cohort (Cohen 2024) gives household cumulative infection risk (HCIR) by the *index case's* age: <1y 23 % (n=22, wide CI), 1–4y 13 %, 5–12y 15 %, 13–18y 7 %, 19–44y 8 %, 45–64y 5 %, ≥65y 0/4 — i.e. **younger index cases transmit more within the household**, consistent with the higher `ageInfectivity` for children in section 3.2. Early childhood education/residential-care outbreak attack rates pool much higher, at 59.2 % (31.7–86.8 %).

**Fitting procedure (documented, not executed in this review — running the full Cologne scenario is explicitly out of scope, see task brief).**
1. Run `runsOnCologneScenario` (or a cheaper standalone household-only variant) for a small grid of `directContact` values (e.g. 0.2, 0.4, 0.6, 0.8, 1.0) with `infectiousness` re-fit at each point to keep the *overall* growth rate/R at the section 2 target (since `infectiousness` and `calibrationParameter` are jointly identifiable with the route weights only through the growth rate, not through SAR).
2. Post-process each run's `infectionEvents.txt`/`homeId` data with `SecondaryAttackRateFromEvents` (already index-based, backdated-to-contagious, weekly-averaged; no code change needed) to get the model's own household SAR.
3. Select the `directContact` value whose model SAR falls inside 16.5–30.0 % (the pooled CI), preferring the centre of that range if several values qualify, and re-check the growth rate/R fit is not lost.
4. **A second target:** the early-childhood-education attack rate (59.2 %, 31.7–86.8 %) could in principle validate the `educ_kiga` contact-pair weight independently of the household weight, but given the population defect in section 2.1 (real Kita-attending infants are essentially absent, and toddler attendance is itself overstated), fitting against this second target with the current population would calibrate the CSV mitigation's residual bias rather than the true daycare transmission rate. **Not recommended as a second target until the population defect is fixed or better understood** — use it only as a sanity check that the model isn't wildly off, not as a formal fitting target.
5. `SecondaryAttackRateFromEvents` has no age stratification of the index case built in; replicating the PHIRST-style by-index-age SAR table above would need a small extension (grouping households by the index person's age at the point they become contagious) — flagged as a possible follow-up, not implemented here.

**Whether a per-route counter would help.** `infectionEvents.txt` currently logs `infectionType` (the activity pair, e.g. `home_home`) but not which route (respiratory/direct-contact) generated each infection. Calibration therefore has to go entirely through aggregate outcomes (SAR, growth rate, age pattern) rather than being able to check, say, "what fraction of infections this run attributes to the direct-contact channel". **A per-route counter in the output would make this calibration substantially easier and more diagnosable** — recommended as a small follow-up (a route field alongside `infectionType` in `EpisimReporting`/the infection event, populated from `viaRespiratory`/`viaDirectContact` in `AgeAndProgressionDependentInfectionModelWithSeasonality.calcInfectionProbability`), but out of scope for this parameterisation-only review.

### 4.7 POLYMOD Germany: physical-contact shares by setting
Own re-analysis of the German subsample of the POLYMOD contact-diary data (`2008_Mossong_POLYMOD_contact_common.csv` + `participant_common.csv`, filtered to `country == "DE"`, n = 1,341 participants, 10,659 logged contacts; doi:10.5281/zenodo.3874557, CC-BY-NC):

| Setting | Physical-contact share |
|---|---|
| Home | **0.82** |
| Leisure | 0.69 |
| School | 0.64 |
| Other place | 0.49 |
| Work | 0.56 |
| Transport | 0.41 |

By participant age, physical contacts per day: age 0 — 4.8 (n = 13, very small sample), 1–4y — 6.7, 5–14y — 6.5, 15–64y — 5.3, 65+ — 3.7. Within home contacts specifically, the physical-contact share rises with contact duration (46 % for contacts under 5 minutes, 92 % for contacts over 4 hours), consistent with household members being both the longest-duration and most-physical contact type — the basis for the `home`/`home` weight of `directContact = 1.0` in section 3.1. **Caveat:** POLYMOD Germany is a pre-COVID (2005/06), non-RSV-specific general contact survey with only 13 German infant participants; it measures *contact* (a necessary but not sufficient condition for RSV transmission), not transmission itself, and CoMix Germany — which would have given a more recent, pandemic-era comparison — was not found on Zenodo (same gap the influenza review noted).

### 4.8 Implied R and generation time
Section 2 already gives R ≈ 1.5–1.7 from the ICOSARI growth-rate fit combined with published RSV serial-interval estimates (7.5 d Vink 2014, 8.4 d PHIRST). No Monte-Carlo model-implied-generation-time calculation (mirroring the influenza doc's section 4.6) was run for the proposed `rsvProgressionConfig`, since it would need the same simulation infrastructure explicitly out of scope here (task brief: "do not run" `runsOnCologneScenario`); flagged as a natural first check once the configuration above is exercised.

---

## 5. Data sources

Legend, same as influenza. **P** = programmatic today (open download or API). **S** = semi-manual (web export or PDF). **R** = request needed; the **RKI partnership in EPISERVE (FG 36 / NRZ Influenza, Unit 17) is the realistic access route** for anything marked R.

| Source | URL | Content | Space | Time | Age | RSV-A/B | Licence | Access | Cadence | Biases |
|---|---|---|---|---|---|---|---|---|---|---|
| ICOSARI SARI hospitalisation incidence | https://github.com/robert-koch-institut/SARI-Hospitalisierungsinzidenz, doi:10.5281/zenodo.22686153 | Weekly SARI incidence per 100k, subsets flu/COVID/RSV; ~70 Helios sentinel hospitals | national only | weekly, RSV series from 2020/21 | **RSV only "00+" — no age split**, unlike flu/COVID/Gesamt in the same file | flu/COVID/RSV/Gesamt only, no A/B | CC-BY 4.0 | **P** (raw TSV) | weekly (Thu) | ICD-coded not lab-confirmed; one hospital network; **age-disaggregated RSV-SARI is not in the open file at all — R for age split** |
| ARE-Wochenbericht (weekly PDF report) | e.g. https://edoc.rki.de/bitstream/handle/176904/10487/ARE_Wochenbericht_KW48_2022.pdf, DOI per week (e.g. doi:10.25646/10844) | RSV share of 0–4y SARI patients (text/figure %), AGI sentinel RSV positivity by age (figure only) | national | weekly | 0–1, 2–4, 5–14, 15–34, 35–59, 60+ (sentinel); 0–4 vs rest (SARI text) | not usually stated | RKI terms, cite | **S** (PDF; numbers only in text/figures, not tabulated) | weekly | Small sentinel sample (~150–330 swabs/week); RSV-share-of-SARI numbers are rounded percentages, not exact counts |
| RSV notifications (IfSG, since July 2023) | RKI Epid Bull 37/2024, doi:10.25646/12728 | Lab-confirmed notified RSV cases, hospitalisation/ICU/death flags by age/sex | national + Länder | weekly (reporting week) | fine (age in years, e.g. <1, 1, 2–4, 5–9, 10–19, 20–59, 60–74, ≥75 in later bulletins) | not typically split | RKI terms | **S** (bulletin PDF); SurvStat@RKI for finer extracts | weekly-updated, season-summarised | **Does not cover 2022/23 at all** (mandatory reporting started 21.7.2023) — first season with data is 2023/24. Only **Saxony** (Land-level Meldeverordnung since 2002) covers 2022/23, and only for one Land — usable only as a secondary, out-of-scope-region comparison. |
| AGI virological sentinel (NRZ) | weekly ARE reports (as above) | Positivity by type/subtype/lineage and age group | national | weekly | 0–1, 2–4, 5–14, 15–34, 35–59, 60+ | **yes** (A vs B, lineage-level in season summaries) | RKI terms, cite | **S** (PDF); line list **R** | weekly | Small samples; positivity is influenza-sentinel-swab-derived, not RSV-targeted testing |
| GrippeWeb | https://github.com/robert-koch-institut/GrippeWeb_Daten_des_Wochenberichts | Weekly self-reported ARE/ILI incidence, population-based | national + 5 regions | weekly, from 2011 | 00+, 0–14, 15+, 0–4, 5–14, 15–34, 35–59, 60+ | none | CC-BY 4.0 | **P** | weekly | **ARE/ILI only — no RSV-specific series** in this repository |
| Paediatric InEK/DRG cost-and-outcome studies | e.g. Wick 2023 doi:10.1111/irv.13211, Lade 2025 doi:10.1007/s15010-024-02391-x | Nationwide RSV-coded hospitalisations, LOS, ICU, ventilation, deaths, by age (incl. month-of-life granularity in Lade) | national | annual | fine (month-of-life in Lade for <2y) | not split | InEK terms; published studies CC-BY where stated | **R** (raw InEK data) / published aggregates **P** via the papers | annual | ICD-coded principal diagnosis only; likely undercounts RSV as a secondary contributor to a respiratory admission |
| DIVI paediatric intensive-care survey | https://www.divi.de/pressemeldungen/... (Dec 2022) | One-off snapshot survey of PICU bed availability during the autumn 2022 crisis (110/130 clinics, 83 free PICU beds nationwide) | national | one survey date (24.11.2022) | not applicable | — | press release | **S** | one-off | Not a recurring series; qualitative "capacity crisis" evidence only, not usable for calibration |
| DIVI Intensivregister | https://github.com/robert-koch-institut/Intensivkapazitaeten_und_COVID-19-Intensivbettenbelegung_in_Deutschland | Daily ICU capacity/COVID occupancy | Kreis | daily | adult/child | no RSV field | CC-BY 4.0 | **P** | daily | Not usable for the RSV ICU branch beyond general capacity context (same conclusion as the influenza doc) |
| BRICK study (NL PICU cohort) | Phijffer 2025, doi:10.1097/inf.0000000000004712 | Nationwide Dutch prospective PICU admission cohort, age, comorbidities, LOS, ventilation, mortality | national (Netherlands, not Germany) | one season pair | fine (age in days at admission) | not split | published, check journal terms | **P** (published aggregates) | one-off study | Netherlands substitute for Germany, flagged throughout |
| Adult RSV hospitalisation studies (Germany) | Scholz 2024 doi:10.1007/s40121-024-01006-0; Liang 2025 doi:10.1007/s40121-025-01255-7 (US) | 60+ hospitalisation, ICU, mortality, underdetection multiplier (Scholz, Germany); ICU/MV by age and risk group (Liang, US, used as an age-gradient proxy only) | national (Scholz: Germany; Liang: US) | annual/seasonal | 18–44, 45–59, 60–74, ≥75 (Liang); 60+ combined mostly (Scholz) | not split | published | **P** (published aggregates) | annual | Scholz's own headline finding is that coded German data underdetect 60+ RSV by up to 7×, i.e. **the raw coded series is known to be badly biased** |
| ECDC ERVISS | https://erviss.org; https://github.com/EU-ECDC/Respiratory_viruses_weekly_data | Weekly detections by type/subtype/lineage, SARI, per country | national | weekly | partial | **yes** | EUPL-1.2 | **P** | weekly | Publication resumed 26.6.2026 after the TESSy→EpiPulse transition, with known inconsistencies (same caveat the influenza doc raised) |
| WHO RSV surveillance | no single stable open endpoint found in this review (FluNet/FluID do not cover RSV) | — | — | — | — | — | — | **NE** (not located) | — | Flag as a genuine gap; WHO does run separate RSV surveillance activities but this review did not locate a programmatic equivalent to FluNet for RSV |
| POLYMOD Germany | doi:10.5281/zenodo.3874557 (data), doi:10.1371/journal.pmed.0050074 (Mossong 2008) | Contact diaries by age/location, physical vs. non-physical, downloaded and re-analysed in this review (section 4.7) | national | 2005/06 | yes | — | CC-BY-NC 4.0 | **P** | static | Pre-COVID; only 13 German infant participants; general contact, not RSV-specific |
| CoMix Germany | — | — | — | — | — | — | — | **NE** (not found on Zenodo, same as the influenza review's own finding) | — | — |
| Infant–carer contact studies | Ozella 2018 (Italy) doi:10.1371/journal.pone.0198733; Kiti 2014 (Kenya) doi:10.1371/journal.pone.0104786 | Wearable-sensor / diary infant contact patterns | Italy / Kenya, not Germany | short (days) | infant-focused | — | published | **P** (published aggregates) | one-off studies | Neither is Germany; used only qualitatively (which household member an infant contacts most) |
| Kölner Statistische Nachrichten (Bevölkerung/Geburten) | https://www.stadt-koeln.de/mediaasset/content/pdf15/statistik-einwohner-und-haushalte/... | Köln population and birth counts by year | Köln | annual | fine | — | Stadt Köln terms | **S** (PDF) | annual | Used only for the population-plausibility check in section 2.1 |
| IT.NRW Kindertagesbetreuung | https://www.it.nrw/... (press releases, e.g. "Anfang März 2022...") | NRW childcare attendance rate by single year of age (<1, 1, 2, ...) | NRW, Kreis-level detail (incl. Köln U3 rate) | annual (1 March reference date) | fine (<1, 1, 2, 3–5) | — | dl-de/by-2-0 (IT.NRW/Destatis) | **S** (press releases; Landesdatenbank NRW for the raw table) | annual | Used directly in section 2.1's population check |

**Programmatic today (P):** ICOSARI, GrippeWeb (no RSV field), DIVI Intensivregister (no RSV field), ERVISS, POLYMOD; published aggregates from InEK/BRICK/Scholz/Liang studies.
**Semi-manual (S):** ARE-Wochenbericht (RSV numbers are in text/figures only), RSV notifications since 2023/24, AGI sentinel positivity, IT.NRW childcare releases, Kölner Statistische Nachrichten.
**Request via the RKI partnership (R):** age-disaggregated ICOSARI RSV-SARI; NRZ virological sentinel line list (age × week × subtype); case-level IfSG RSV notifications with hospitalisation/ICU/death flags by Kreis × age (for seasons after 2023/24 — irrelevant to 2022/23 itself, but useful for validating the model against a *different* season once the population defect is addressed); raw InEK data for a custom age breakdown finer than the published studies.

---

## 6. Gaps and risks

Shared gaps already covered by [influenza doc §6](influenza-parameterisation.md#6-gaps-and-risks) — isolation mechanism limits, antibody model limitations, scenario-input fixes (schools/holidays/tracing/seasonality), death-only-via-ICU as a general code limitation, `interpolateImport`'s 1-January break, `findValidEntry`'s pre-season default — **apply unchanged to RSV** and are not repeated here. RSV-specific items:

1. **The synthetic population does not represent infant childcare or infant households (section 1/2.1).** This is the biggest blocker for infant-burden calibration specifically, and it is a population/plans defect, not something any `PathogenConfigGroup`/`ContactTransmissionConfigGroup` value can fix. The CSV mitigation in section 3.4/7 only suppresses the `educ_kiga` symptom (age-0 attendance), not the underlying cause, and does **not** touch the 25.3 %-of-age-0-households-have-no-adult problem, which remains open. Any infant-focused RSV scenario built on this population should treat infant hospitalisation/ICU output as **not directly comparable** to real infant burden until this is investigated further (e.g. checking whether "no adult in household" reflects a genuine synthetic-household-generation artefact or a district/sampling edge effect in the 25 % extract specifically).
2. **Progression durations cannot depend on age**, and this matters far more for RSV than it did for influenza: `Transition`/`ConfigurableProgressionModel.decideTransitionDay` take no age input, but shedding duration in this review's own sources varies roughly 2× between infants (mean 18 d) and adults (mean 9 d) (Munywoki 2015), and PICU length-of-stay/hospital-LOS both vary similarly by age. The compromise durations in section 3.3 (e.g. `seriouslySick → recovered` at mean 5.0 d, blending a 4.5 d infant figure and a 10.4 d elderly figure) systematically **overstate** infant illness/hospital duration and **understate** elderly duration, in both directions biasing whichever age group is under-represented in a given run's hospitalised population.
3. **Fomites are not implemented** (`FOMITE` scaffolded, commented out). RSV survives on hard surfaces for hours (Hall 1980) and self-inoculation via contaminated hands is documented, but no source found quantifies what share of real-world RSV transmission this route represents versus direct person-to-person contact — so it is not possible to estimate what is lost by leaving it unimplemented, only that the omission is directionally real. Not recommended for implementation within this review's scope; flagged for a future contact-model iteration if a fomite-attributable-fraction estimate becomes available.
4. **Direct contact has no dedicated contact structure** (inherited from `multi-pathogen-refactor.md` §3.2, "known limitation" #2) and, specific to RSV, **is not damped by the model's seasonality mechanism at all** (section 3.5) — a real, code-verified structural gap that will systematically understate RSV's winter concentration relative to a fully-respiratory pathogen with the same nominal container seasonality settings.
5. **The route split is fundamentally uncalibrated against real data within this review** (section 4.5/4.6): it rests on qualitative literature only, and the proposed household-SAR fitting procedure was documented but not executed (out of scope, task brief). Anyone using the shipped `directContact = 0.6` value should treat it as a starting point for the calibration in section 4.6, not a result.
6. **No output field records which route caused an infection** (section 4.6), making the route-weight calibration harder to validate than it should be — recommended as a small follow-up to `EpisimReporting`/the infection-event schema.
7. **The immunity-debt effect for 2021/2022/23 is documented but not applied by default** (section 3.2): the recommended `ageSusceptibility` uplift for ages 1–3 is this reviewer's own translation of Cai 2024's qualitative "flattened age curve" finding into a multiplier, and is deliberately left out of the shipped configuration in section 7 to keep the numbers traceable to a single source per parameter.
8. **Prevention products cannot reduce simulated transmission**, only severity, given the antibody-model limitation inherited from the influenza review — larger a concern for nirsevimab (whose real-world effect plausibly includes a transmission-reduction component from protected infants) than for the two vaccine products (section 3.6).
9. **No infant-under-1 German incidence series exists for the calibration season itself** (section 1/2): even a perfectly-parameterised model has nothing open to validate infant-specific output against for 2022/23, only the derived 0–4y proxy (section 4.1) and the InEK-study-based hospitalisation figures used in the derivations above (which are themselves not weekly time series, only annual averages).
10. **`runsOnCologneScenario` has no `@Disabled` in the current working copy**, same finding as the influenza review — pre-existing, not introduced by this review, and still noted here since it applies to `RsvCologneScenarioTest` too.

---

## 7. Ready-to-paste code

Applied to `src/test/java/org/matsim/episim/run/RsvCologneScenarioTest.java`. Needs the additional imports `EpisimPerson`, `EpisimPerson.DiseaseStatus`, `TracingConfigGroup`, `Transition`, `FixedPolicy`, static `Transition.to`, and the shared constants also used by `InfluenzaCologneScenarioTest` (`SEASON_START`, `SCHOOLS`) — see section 1 of the influenza doc for why these are proposed as a small shared helper rather than duplicated; this review keeps them duplicated for now, consistent with how `RsvCologneScenarioTest` currently stands relative to `InfluenzaCologneScenarioTest` (neither test depends on the other).

```java
static final LocalDate SEASON_START = LocalDate.parse("2022-09-26");   // shared with InfluenzaCologneScenarioTest: KW 39/2022, RSV wave onset KW 41 (Cai 2024, doi:10.2807/1560-7917.es.2024.29.13.2300465)
private static final String[] SCHOOLS = {"educ_primary", "educ_secondary", "educ_tertiary", "educ_other"};
private static final double RSV_ICU_LOS_SIGMA = Math.log(8.0 / 3.0) / (2 * 0.6745);   // PICU stay IQR 3-8 d (doi:10.1097/inf.0000000000004712)

static void configureRsv(Config config) {

	EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
	VirusStrainConfigGroup virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
	PathogenConfigGroup pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);
	ContactTransmissionConfigGroup contactTransmissionConfig = ConfigUtils.addOrGetModule(config, ContactTransmissionConfigGroup.class);

	// 0. season window: 2022/23 on the real Cologne mobility trace, shared with influenza (doc section 2)
	episimConfig.setStartDate(SEASON_START);

	// 1. one pooled strain (RSV-A dominated 2021, RSV-B dominated 2022/23; one-pathogen immunity limitation
	//    means two strains would infect the same people independently -> pool, same decision as influenza)
	VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getOrAddParams(RSV_STRAIN);
	strain.setPathogen(RSV);
	strain.setInfectiousness(1.0);         // NOT an estimate: calibrate to r = 0.06-0.07/d (ICOSARI RSV-SARI 00+ W40-45/2022)
	strain.setFactorSeriouslySick(1.0);    // pooled strain = pathogen baseline
	strain.setFactorCritical(1.0);         // pooled strain = pathogen baseline
	// household-exposure-controlled relative odds of acquiring infection by contact age, ref. >=65y (PHIRST, doi:10.1038/s41467-023-44275-y)
	strain.setAgeSusceptibility(Map.of(0, 5.0, 1, 7.1, 5, 3.5, 13, 2.9, 19, 2.6, 45, 2.7, 65, 1.0));
	// children shed more, longer, and are 55% of household index cases (doi:10.1101/2024.11.14.24317347, doi:10.1093/infdis/jit828)
	strain.setAgeInfectivity(Map.of(0, 1.3, 1, 1.8, 5, 1.5, 19, 1.0, 65, 0.7));

	// 2. natural history (age-dependent); seriouslySick = effective target / hospitalFactor
	double hospitalFactor = episimConfig.getHospitalFactor();
	PathogenConfigGroup.PathogenParams byAge = pathogenConfig.getOrAddParams(RSV, true);
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
	byAge.setRouteTransmissibility(TransmissionWeights.parse("respiratory=1.0;directContact=0.6"));
	// atHome (not full): a sick child still infects their own household, the dominant real RSV transmission situation.
	// Low-confidence, age-graded assumption (doc section 3.1): informal Kita-exclusion norm vs. adult presenteeism.
	byAge.setSymptomaticIsolationProbabilityByAge(Map.of(0, 0.85, 1, 0.70, 5, 0.30, 60, 0.40, 80, 0.50));
	byAge.setSymptomaticIsolationStatus(EpisimPerson.QuarantineStatus.atHome);

	// 3. disease progression
	episimConfig.setProgressionConfig(rsvProgressionConfig(Transition.config()).build());

	// 4. season inputs the Cologne builder does not cover for 2022/23 (same fixes as influenza, doc section 3.5)
	FixedPolicy.ConfigBuilder policy = FixedPolicy.parse(episimConfig.getPolicy());
	policy.restrict(SEASON_START, 1.0, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_higher", "educ_other");
	policy.restrict(LocalDate.parse("2022-10-04"), 0.2, SCHOOLS);   // NRW Herbstferien 04.10.-15.10.2022
	policy.restrict(LocalDate.parse("2022-10-17"), 1.0, SCHOOLS);
	policy.restrict(LocalDate.parse("2022-12-23"), 0.2, SCHOOLS);   // NRW Weihnachtsferien 23.12.2022-06.01.2023
	policy.restrict(LocalDate.parse("2023-01-09"), 1.0, SCHOOLS);
	episimConfig.setPolicy(policy.build());
	episimConfig.setLeisureOutdoorFraction(Map.of(                  // EpisimConfigGroup 2020/21 default pattern, repeated (same as influenza)
			LocalDate.parse("2022-04-15"), 0.8, LocalDate.parse("2022-09-15"), 0.8, LocalDate.parse("2022-11-15"), 0.1,
			LocalDate.parse("2023-02-15"), 0.1, LocalDate.parse("2023-04-15"), 0.8));
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

	// 6. seeding: drop the SARS-CoV-2 import; small continuous trickle, ramped, far below the previous ~120/day placeholder
	//    (which was almost certainly too high, see the task brief). Level is a calibration prior, no evidence found.
	episimConfig.getInfections_pers_per_day().clear();
	Map<LocalDate, Integer> rsvImport = new HashMap<>();
	SnzCologneOpenProductionScenario.interpolateImport(rsvImport, COLOGNE_FACTOR,
			SEASON_START.minusDays(1), LocalDate.parse("2022-10-24"), 2.0, 6.0);
	SnzCologneOpenProductionScenario.interpolateImport(rsvImport, COLOGNE_FACTOR,
			LocalDate.parse("2022-10-24"), LocalDate.parse("2022-12-31"), 6.0, 6.0);
	episimConfig.setInfections_pers_per_day(RSV_STRAIN, rsvImport);

	// 7. antibodies 0.0 keep getSeriouslySickFactor = 1/(1+ab^beta) at 1 (same neutralisation as influenza, doc section 1 / influenza doc 6.2)
	AntibodyConfigGroup antibodyConfig = ConfigUtils.addOrGetModule(config, AntibodyConfigGroup.class);
	AntibodyConfigGroup.AntibodyParams rsvAntibodies = antibodyConfig.getOrAddParams(RSV_STRAIN);
	for (VirusStrain against : virusStrainConfig.getVirusStrains()) {
		rsvAntibodies.getInitialAntibodies().put(against, 0.0);
		rsvAntibodies.getAntibodyRefreshFactors().put(against, 1.0);
	}
}

static Transition.Builder rsvProgressionConfig(Transition.Builder builder) {
	return builder
			.from(DiseaseStatus.infectedButNotContagious,
					to(DiseaseStatus.contagious, Transition.fixed(2)))                                          // latent: half of the 4.4 d median incubation (doi:10.1016/S1473-3099(09)70069-6), no latent-only estimate found
			.from(DiseaseStatus.contagious,
					to(DiseaseStatus.showingSymptoms, Transition.logNormalWithMedianAndSigma(2.4, Math.log(1.24))), // remainder of incubation, dispersion 1.24
					to(DiseaseStatus.recovered, Transition.logNormalWithMeanAndStd(7.8, 4.0)))                  // asympt. shedding mean 7.8 d (doi:10.1017/S0950268814001393)
			.from(DiseaseStatus.showingSymptoms,
					to(DiseaseStatus.seriouslySick, Transition.logNormalWithMedianAndSigma(3.5, 0.514)),        // onset->admission ~3-5 d, adult IQR 2-4 (doi:10.1016/j.jcv.2017.09.001)
					to(DiseaseStatus.recovered, Transition.logNormalWithMeanAndStd(11.0, 5.0)))                 // symptomatic shedding 13.5 d minus ~2.4 d presymptomatic
			.from(DiseaseStatus.seriouslySick,
					to(DiseaseStatus.critical, Transition.logNormalWithMedianAndStd(1.0, 1.0)),                 // no evidence found: COVID placeholder
					to(DiseaseStatus.recovered, Transition.logNormalWithMeanAndStd(5.0, 5.0)))                  // cross-age compromise, infant LOS 4.5 d vs. elderly pneumonia LOS 10.4 d
			.from(DiseaseStatus.critical,
					to(DiseaseStatus.seriouslySickAfterCritical, Transition.logNormalWithMedianAndSigma(5.0, RSV_ICU_LOS_SIGMA)), // PICU stay 5 (3-8) d (doi:10.1097/inf.0000000000004712)
					to(DiseaseStatus.deceased, Transition.logNormalWithMedianAndSigma(5.0, RSV_ICU_LOS_SIGMA)))  // no time-to-death evidence found: same as ICU stay
			.from(DiseaseStatus.seriouslySickAfterCritical,
					to(DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(7.0, 7.0)))                // no evidence found: COVID placeholder
			.from(DiseaseStatus.recovered,
					to(DiseaseStatus.susceptible, Transition.logNormalWithMedianAndStd(90, 60)));               // short, partial protection (doi:10.1093/infdis/163.4.693, doi:10.1016/j.epidem.2018.12.001) - unlike influenza's fixed(365)
}
```

**The age-banded CSV** (`src/test/resources/rsv/rsv_kiga_direct_contact.csv`, `ageBands = List.of(0, 1, 3, 18)`) zeroes both routes for any pair involving age band 0 (infants) on the `educ_kiga`/`educ_kiga` contact pair, and applies `respiratory=1.0;directContact=0.6` to every other age-band combination. See the header comment in the file itself for the full rationale (section 3.4 above).

---

## References (DOIs verified against Europe PMC or the publisher during this review)

- Cai W et al. 2024, Euro Surveill, doi:10.2807/1560-7917.es.2024.29.13.2300465
- Cohen C et al. 2024 (PHIRST South Africa), Nat Commun, doi:10.1038/s41467-023-44275-y
- Drysdale SB et al. 2023 (HARMONIE), NEJM, doi:10.1056/nejmoa2309189
- Falsey AR et al. 2005, NEJM, doi:10.1056/nejmoa043951
- Glezen WP et al. 1986, Am J Dis Child, doi:10.1001/archpedi.1986.02140200053026
- Grangier B et al. 2024, Sci Rep, doi:10.1038/s41598-024-55378-x
- Hall CB, Geiman JM, Biggar R, Kotok DI, Hogan PM, Douglas GR 1976, NEJM, doi:10.1056/nejm197602192940803
- Hall CB, Douglas RG, Geiman JM 1980, J Infect Dis, doi:10.1093/infdis/141.1.98
- Hall CB, Douglas RG 1981, J Pediatr, doi:10.1016/s0022-3476(81)80969-9
- Hall CB, Douglas RG, Schnabel KC, Geiman JM 1981, Infect Immun, doi:10.1128/iai.33.3.779-783.1981
- Hall CB, Walsh EE, Long CE, Schnabel KC 1991, J Infect Dis, doi:10.1093/infdis/163.4.693
- Hammitt LL et al. 2022 (MELODY), NEJM, doi:10.1056/nejmoa2110275
- Ison MG et al. 2025 (AReSVi-006), Lancet Respir Med, doi:10.1016/s2213-2600(25)00048-7
- Kampmann B et al. 2023 (MATISSE), NEJM, doi:10.1056/nejmoa2216480
- Kombe IK, Munywoki PK, Baguelin M, Nokes DJ, Medley GF 2019, Epidemics, doi:10.1016/j.epidem.2018.12.001
- Lade C et al. 2025, Infection, doi:10.1007/s15010-024-02391-x
- Lessler J et al. 2009, Lancet Infect Dis, doi:10.1016/S1473-3099(09)70069-6
- Li K, Bont LJ, Weinberger DM, Pitzer VE 2024 (preprint), medRxiv, doi:10.1101/2024.11.14.24317347
- Li Y et al. 2022, Euro Surveill, doi:10.2807/1560-7917.es.2022.27.16.2100619
- Liang C et al. 2025, Infect Dis Ther, doi:10.1007/s40121-025-01255-7
- Malosh RE et al. 2017, J Clin Virol, doi:10.1016/j.jcv.2017.09.001
- Mossong J et al. 2008 (POLYMOD), PLoS Med, doi:10.1371/journal.pmed.0050074
- Munywoki PK et al. 2014, J Infect Dis, doi:10.1093/infdis/jit828
- Munywoki PK et al. 2015 (shedding duration), Epidemiol Infect, doi:10.1017/S0950268814001393
- Munywoki PK et al. 2015 (asymptomatic infections), J Infect Dis, doi:10.1093/infdis/jiv263
- Ochola R et al. 2009, PLoS One, doi:10.1371/journal.pone.0008088
- Oliva IO, Oliveira CR 2026, Hum Vaccin Immunother, doi:10.1080/21645515.2026.2690750
- Ozella L et al. 2018, PLoS One, doi:10.1371/journal.pone.0198733
- Papi A et al. 2023, NEJM, doi:10.1056/nejmoa2209604
- Phijffer EWEM et al. 2025 (BRICK), Pediatr Infect Dis J, doi:10.1097/inf.0000000000004712
- Saravanos GL et al. 2026, Influenza Other Respir Viruses, doi:10.1111/irv.70270
- Schönfeld V, Rau C, Cai W, Wichmann O, Harder T 2025, Dtsch Arztebl Int, doi:10.3238/arztebl.m2025.0111
- Scholz S et al. 2024, Infect Dis Ther, doi:10.1007/s40121-024-01006-0
- Vink MA, Bootsma MC, Wallinga J 2014, Am J Epidemiol, doi:10.1093/aje/kwu209
- Walsh EE et al. 2023 (RENOIR), NEJM, doi:10.1056/nejmoa2213836
- Weber A, Weber M, Milligan P 2001, Math Biosci, doi:10.1016/s0025-5564(01)00066-9
- Wick M et al. 2023, Influenza Other Respir Viruses, doi:10.1111/irv.13211
- Wildenbeest JG et al. 2023 (RESCEU), Lancet Respir Med, doi:10.1016/s2213-2600(22)00414-3
- RKI ARE-Wochenbericht KW44/2022, doi:10.25646/10757; KW48/2022, doi:10.25646/10844
- RKI Epid Bull 37/2024 (RSV notifications, first season), doi:10.25646/12728
- RKI STIKO EB 26/2024 (Nirsevimab recommendation), doi:10.25646/12198
- RKI STIKO EB 32/2024 (older-adult RSV vaccination recommendation), doi:10.25646/12470
- RKI-Ratgeber RSV-Infektionen, https://www.rki.de/DE/Aktuelles/Publikationen/RKI-Ratgeber/Ratgeber/Ratgeber_RSV.html
- IT.NRW press releases on Kindertagesbetreuung (1.3.2022, 1.3.2023), https://www.it.nrw
- Stadt Köln, Kölner Statistische Nachrichten 4/2023 ("Bevölkerung in Köln 2022")
- DIVI Pressemeldung, 1.12.2022, https://www.divi.de
- IAB-Forum / Dietrich AS, Hiesinger K 2020 (presenteeism, LPP 2016/17), https://iab-forum.de

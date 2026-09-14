# Influenza parameterisation and data sources for MATSim-EpiSim (Cologne scenario)

## JS: This document was created for source code validation purposes. It should not be considered the final source of truth without additional validation.

## 1. Executive summary

**Can the model be run for influenza today?** Technically yes: the configuration in section 7 validates, and it slots into the Cologne scenario without code changes. **Epidemiologically, not yet.** The run will produce epidemic curves, but four structural issues make them unreliable before calibration can mean anything, the first of which, full isolation at symptom onset, is now addressed by configuration.

**Former biggest blocker, now configurable: symptomatic persons were fully isolated at symptom onset.** Until the isolation parameters were added, `ConfigurableProgressionModel.onTransition` set `QuarantineStatus.full` for every person entering `showingSymptoms`, unconditionally. `AbstractContactModel.checkPersonInContainer` then removes them from *every* container, the home included. This was a deliberate COVID-era assumption. For influenza it removes the most infectious period, because shedding peaks on the first 1–2 days of illness (Ip 2016). A simulation using the timings proposed here shows the consequence:

| | share of transmission from asymptomatic infections | mean generation time |
|---|---|---|
| previous default (full isolation at onset for everyone) | **72 %** | 2.8 d |
| same parameters, no isolation | 38 % | 3.4 d |
| evidence | 34 % (66 % from symptomatic, Van Kerckhove 2013) | 2.2–3.6 d (Vink 2014; Cowling 2009) |

Ill people do reduce their contacts, to about a quarter of the healthy reproduction number, but mostly outside the home (Van Kerckhove 2013). They do not drop to zero. Calibrating `infectiousness` on top of full isolation would fit the growth rate, but through the wrong mechanism, and it would mis-predict the effect of any intervention. **Resolved by configuration:** isolation at symptom onset is now set per pathogen through `PathogenParams.setSymptomaticIsolationProbabilityByAge` and `setSymptomaticIsolationStatus` (`full` or `atHome`). The defaults, `0=1.0` and `full`, keep the COVID behaviour unchanged; there is no delay option. **Influenza uses 75 % home isolation (`0=0.75`, `atHome`)**, derived from Van Kerckhove 2013 in section 4.9. Confidence is low, so treat it as a sensitivity parameter (range 0.5–0.9). The biggest remaining gap is the immunity model, the first item below.

The other three:
1. The antibody model can't represent influenza immunity. It has a 60-day half-life, no antigenic distance, and in the Cologne infection model it only scales severity (section 6.2).
2. The test copy of the Cologne scenario has no 2022/23-appropriate seasonality, schools or tracing. These are fixed by config in section 7.
3. Death can only occur via ICU (section 6.5).
---

## 3. Parameter table

Confidence: **H** = high, **M** = medium, **L** = low, **NE** = no evidence found (explicit assumption). "Eff." means the value after `hospitalFactor` (0.5 in Cologne); the code divides by `hospitalFactor` so that the effective value equals the table value.

### 3.1 `PathogenConfigGroup.PathogenParams` with its age-dependent `ProgressionParams`

| Java API | Proposed | Plausible range | Denominator / notes | Source | Conf. |
|---|---|---|---|---|---|
| `setShowingSymptomsProbabilityByAge` | `0: 0.56` | 0.56–0.84 for PCR-defined infections; 0.23 if serology-defined | P(symptomatic \| PCR-confirmed infection). 268/478 in a twice-weekly-swabbed community cohort. No age-stratified estimate found. | Cohen 2021 (PHIRST) doi:10.1016/S2214-109X(21)00141-8; Leung 2015 doi:10.1097/EDE.0000000000000340 (outbreaks: asymptomatic 16 %, 13–19 %); Carrat 2008 doi:10.1093/aje/kwm375 (66.9 %, 58.3–74.5); Hayward 2014 doi:10.1016/S2213-2600(14)70034-7 (23 illnesses per 100 serologic infections) | M |
| `setSeriouslySickProbabilityByAge` (eff.) | `0: 0.00697, 5: 0.00274, 18: 0.00561, 50: 0.0106, 65: 0.0909` | ÷2.1 to ÷5.2 if coded (detected) hospitalisations are meant | P(flu-associated hospitalisation \| symptomatic illness), US, multiplier-corrected. CDC uses a fixed illness : hospitalisation ratio per age (identical in 2017/18, 2018/19 and 2019/20), so this is a model constant, not a season estimate. Code value = eff. / `hospitalFactor`. | CDC burden 2018/19 Table 1 https://archive.cdc.gov/www_cdc_gov/flu/about/burden/2018-2019.html; Reed 2015 doi:10.1371/journal.pone.0118369 | L–M |
| `setCriticalProbabilityByAge` | `0: 0.044, 18: 0.099, 60: 0.113` | 0.074 (Spain) – 0.195 (US 18–49) | P(ICU \| hospitalised), Germany 2022/23, J09–J11 primary diagnosis. | Meyer 2026 doi:10.1007/s40121-026-01384-7; Ramos-Rincón 2024 doi:10.3389/fpubh.2024.1360372; Ludwig 2021 doi:10.1016/j.ijid.2020.11.204 (13 %) | M |
| `setDeathProbabilityByAge` | `0: 0.24` | 0.20–0.27 | P(death in ICU \| ICU), pooled across 37 European studies, no age strata. **Non-zero**, so a `critical → deceased` transition is required (section 3.3). | Suárez-Sánchez 2025 doi:10.1111/irv.70073; van der Bie 2025 doi:10.3390/v17111467 (21.9 %) | M |
| `setRouteTransmissibility` | `respiratory=1.0` | directContact 0 | Aerosols account for about half of household transmission. Hand hygiene alone had no significant effect on lab-confirmed influenza. In this model the direct-contact route is not a physical-contact channel: it only *bypasses* masks, ventilation and outdoor dilution, so a non-zero weight would mean "unmitigable transmission", not "hand contact". | Cowling 2013 doi:10.1038/ncomms2922; Wong 2014 doi:10.1017/S095026881400003X; Killingley 2013 doi:10.1111/irv.12080 | M |
| `setSymptomaticIsolationProbabilityByAge`, `setSymptomaticIsolationStatus` | `0: 0.75`, `atHome` | 0.5–0.9 | P(isolate at home \| symptom onset). ILI reduced R to about a quarter, mainly through fewer contacts outside the home; `atHome` removes all non-home contacts, so 1 − p = 0.25. No direct measurement of the fraction that isolates was found; derivation in section 4.9. | Van Kerckhove 2013 doi:10.1093/aje/kwt196 | L |

### 3.2 `VirusStrainConfigGroup.StrainParams`

| Java API | Proposed | Plausible range | Notes | Source | Conf. |
|---|---|---|---|---|---|
| `setInfectiousness` | `1.0` **placeholder** | — | Not an estimate. Fit to r = 0.085/d (R ≈ 1.2–1.35) and peak week KW 50 (section 2). | Biggerstaff 2014 doi:10.1186/1471-2334-14-480; ICOSARI doi:10.5281/zenodo.22686153 | — (calibrate) |
| `setAgeSusceptibility` | `0:1.0, 11:1.0, 12:2.04, 18:2.04, 19:1.0` (linear between keys) | 12–18 y: 1.19–3.49. Sensitivity: children ≤18 = 1.96 (1.05–3.78) (H1N1pdm09) | A(H3N2), relative to adults ≥40 (hazard ratio per exposure, household model). <12 y not significantly elevated for H3N2 (but <5 y HR 2.69 for H1N1pdm09 and 5.65 for B/Victoria). 19–39 y and elderly: no estimate, so 1.0. | Sauter 2026 doi:10.1038/s41467-026-76037-x; Cauchemez 2009 doi:10.1056/NEJMoa0905498 | L–M |
| `setAgeInfectivity` | `0: 1.0` | — | "Infectivity did not vary with age". Children <5 shed 3–5 d longer (Sauter 2026), which cannot be expressed because `Transition` is not age-dependent. | Cauchemez 2009; Sauter 2026 | L |
| `setFactorSeriouslySick`, `setFactorCritical` | `1.0` | — | One pooled strain = pathogen baseline. | — | — |
| `setFactorSeriouslySickVaccinated` | not set (deprecated) | — | No influenza vaccination model is bound (section 3.5). | — | NE |

The current COVID table (0.45 below age 20, doi:10.1101/2020.06.03.20121145) is **not** reused.

### 3.3 Disease progression (`influenzaProgressionConfig`)

All transitions are whole days (`daysSince >= transitionDay`, evaluated once per day).

| Transition | Proposed | Evidence (with dispersion) | Conf. |
|---|---|---|---|
| infectedButNotContagious → contagious | `fixed(1)` | Shedding rises 0.5–1 d after challenge (Carrat 2008). Contagious from the next daily update; `fixed(0)` gives the same result. | M |
| contagious → showingSymptoms | `logNormalWithMedianAndSigma(1.0, ln 1.51)` | Influenza A incubation median 1.4 d (1.3–1.5), dispersion 1.51 (1.43–1.60), 5–95 % 0.7–2.8 d; without an outlier study median 1.9 d, dispersion 1.22. Influenza B median 0.6 d (Lessler 2009, doi:10.1016/S1473-3099(09)70069-6). Model incubation = 1 d latent + this leg, about 2 d median; medians below 1.0 would round to 0 contagious days before isolation. | M |
| contagious → recovered (asymptomatic) | `logNormalWithMedianAndStd(4.0, 2.0)` | Shedding 3–5 (up to 7) d (RKI AGI Saisonbericht 2018/19). All infections: mean 4.8 d (4.31–5.29) (Carrat 2008). Asymptomatic infections shed shorter and 1–2 log10 lower (Ip 2017, doi:10.1093/cid/ciw841). Std chosen so the IQR ≈ 3–5.5 d. | M |
| showingSymptoms → seriouslySick | `logNormalWithMedianAndSigma(3.0, 0.6)` | Onset to admission median 3 d, IQR 1–4 (n = 15,110, FluSurv-NET adults 18–49, doi:10.1093/ofid/ofad599). σ = 0.6 matches the median and Q3; the lower quartile is not matched. US substitute. | M |
| showingSymptoms → recovered | `logNormalWithMedianAndStd(6.0, 2.0)` | Influenza A shedding and illness end by day 6–7 after onset (Ip 2016, doi:10.1093/cid/civ909). Std 2.0 is an assumption. Under current code this is the isolation duration. | M (median), NE (std) |
| seriouslySick → critical | `logNormalWithMedianAndStd(1.0, 1.0)` | **No influenza evidence found.** COVID placeholder kept. | NE |
| seriouslySick → recovered | `logNormalWithMeanAndStd(5.6, 6.0)` | Hospital stay, Germany 2022/23: mean 5.6 d (SD 6.0). By age: 3.1 (0–17), 4.6 (18–59), 8.1 (≥60) (Meyer 2026). Earlier German median 4.7 d (113 h) (von der Beck 2017, doi:10.1371/journal.pone.0180920). | H (value), M (shape) |
| critical → seriouslySickAfterCritical | `logNormalWithMedianAndSigma(4.0, 1.54)` | ICU stay median 4 d, IQR 1–8 (NL 2023/24, n = 498, van der Bie 2025). σ = ln(8)/1.349. | M |
| critical → deceased | same as the ICU stay | **No time-to-death evidence found.** Assumption. | NE |
| seriouslySickAfterCritical → recovered | `logNormalWithMedianAndStd(7.0, 7.0)` | **No influenza evidence found.** COVID placeholder kept. The NL hospital stay of ICU patients (median 10, IQR 6–21) minus the ICU stay (4) suggests about 6 d. | NE |
| recovered → susceptible | `fixed(365)` | Protection wanes to half 3.5–7 y after infection, faster for H3N2 (Ranjeva 2019, doi:10.1038/s41467-019-09652-6). Anything longer than the run means no reinfection with the pooled strain within the season. PHIRST saw 17 % repeat infections within a season, mostly other subtypes, which a single strain cannot represent. | M |
| `episimConfig.setDaysInfectious` | keep `Integer.MAX_VALUE` | The infectious window is bounded by the contagious state (asymptomatic branch) and by isolation at onset (symptomatic branch). | M |

**Infectivity curve `NormalDistribution(0.5, 2.6)` around onset (COVID, arXiv:2007.06602).** For influenza A, shedding peaks on the first 1–2 days of illness and is undetectable by day 6–7 (Ip 2016). Influenza B rises up to 2 days before onset. Infectivity tracks viral load only weakly, as V^0.14–0.16 (Tsang 2015, doi:10.1093/infdis/jiv225), which argues for a flatter curve than shedding itself. Most household transmission happens "soon before or after onset" (Cauchemez 2009, mean serial interval 2.6 d).

The peak offset (+0.5 d) is defensible. The width of 2.6 d has no influenza evidence. Under current code the post-onset half is never used, because of isolation. The pre-onset half is truncated at 1–2 days by the short incubation, so the curve's width mostly affects the asymptomatic branch.

### 3.4 Seasonality

| Item | Finding | Source | Conf. |
|---|---|---|---|
| Empirical forcing | Absolute-humidity-forced model for the US: R0max 3.52, R0min 1.12, a winter/summer **ratio of about 3.1**. For the Netherlands, closest to Cologne: absolute humidity explains only 3 % of the variation in weekly R, depletion of susceptibles 30 %, between-season effects 27 %. | Shaman 2010 doi:10.1371/journal.pbio.1000316; te Beest 2013 doi:10.1093/aje/kwt132 | M |
| School holidays | Holidays reduce transmission to children by 20–29 % (France). No significant Christmas-holiday effect in NL. Germany 2022/23 wave "ended abruptly with the national school holidays". | Cauchemez 2008 doi:10.1038/nature06732; te Beest 2013; doi:10.1002/jmv.70530 | M |
| Model mechanism | E[indoorOutdoorFactor] = 1 − 0.9·f·s with outdoor fraction f and container seasonality s. For leisure (s = 1): 0.91 in winter (f = 0.1) vs 0.28 in summer (f = 0.8), ×3.25. For home, work, school, visit and errands (s = 0.5): 0.955 vs 0.64, ×1.49. For pt and shops (s = 0): no forcing. | code: `InfectionModelWithSeasonality.getIndoorOutdoorFactor` | H |
| Defect | The default `leisureOutdoorFraction` ends at 2021-09-15 = 0.8, so **there is no winter in 2022/23**. Fixed in config by repeating the 2020/21 pattern. | `EpisimConfigGroup` l. 100 | H |

Assessment: the mechanism models behaviour (outdoor contacts), not virus survival. Its total amplitude (×1.5–×3.3 depending on container mix) is in the range of Shaman's ×3, so it is adequate for a first calibration if the outdoor fraction is driven by Cologne weather (`EpisimUtils.getOutDoorFractionFromDateAndTemp2`). No explicit seasonal transmissibility term is required for a single season. For multi-season runs, a humidity- or date-driven multiplier on `infectiousness` is preferable, and would need a code change.

### 3.5 Immunity, waning and vaccination

| Item | Finding | Source | Conf. |
|---|---|---|---|
| Duration of protection | Half of peak protection after 3.5–7 y; wanes faster against H3N2 than H1N1pdm09 | Ranjeva 2019 | M |
| Antigenic drift | H3N2 drifts faster and more punctuated than H1N1, B/Vic and B/Yam; drift drives year-to-year incidence | Bedford 2014 doi:10.7554/eLife.01914; Smith 2004 doi:10.1126/science.1097211 | H |
| Antibody dynamics | Short-term boost, "antigenic seniority", cross-reactivity decays quickly with antigenic distance | Kucharski 2015 doi:10.1371/journal.pbio.1002082 | M |
| Correlate | HI titre 1:40 ≈ 50 % protection, similar for A and B; HI ≥1:40 protective for H1N1pdm09, H3N2 and B/Vic | Coudeville 2010 doi:10.1186/1471-2288-10-18; Sauter 2026 | M |
| Heterosubtypic | Pre-existing NP-specific T cells protect against *symptomatic* PCR-confirmed disease (aOR 0.27, 0.11–0.68), not against infection | Hayward 2015 doi:10.1164/rccm.201411-1988OC | M |
| Proposed `AntibodyConfigGroup` | initial antibodies **0.0**, refresh 1.0 | see section 6.2 | — |
| `DefaultAntibodyModel.HALF_LIFE_DAYS = 60` | Not appropriate for influenza (years, not 60 d). Irrelevant for susceptibility in the Cologne infection model; with initial antibodies = 0 also irrelevant for severity. | Ranjeva 2019 | — |

**Vaccination (Germany).** The Cologne scenario binds `NoVaccination`, so none of this enters the run. It is documented for a future influenza `VaccinationType`.

| Group | Coverage | Source |
|---|---|---|
| ≥60 y, 2020/21 | 47.3 % | RKI Epid Bull 49/2022, https://edoc.rki.de/bitstream/handle/176904/10490/EB-49-2022-Impfquoten-Erwachsene.pdf |
| ≥60 y, 2021/22 | 43.3 % (60–69: 34.8 %, 70–79: 48.9 %, 80+: 51.8 %); western Länder 40.6 %, range 26.8–53.0 % | same |
| ≥60 y, 2022/23 | 37 % (district range 10–61 %; denominator: SHI-insured with a physician contact) | Bundesgesundheitsblatt 2025, doi:10.1007/s00103-025-04103-8 |
| Adults with an indication, 2021/22 | 35.4 % overall; 8.2 % (18–29) to 24.1 % (50–59) | Epid Bull 49/2022 |
| Hospital staff, 2022/23 | 58.7 % (57.9–59.4); physicians 80.7 %, nurses 51.1 % | OKaPII 2023, https://www.rki.de/DE/Themen/Infektionskrankheiten/Impfen/Forschungsprojekte/OKaPII/Ergebnisbericht_2023.html |
| Healthy children | STIKO recommends vaccination only for risk groups; **no coverage data found** | — |
| Rollout timing within a season | **No evidence found** in the sources reviewed. Weekly claims data exist in the KV-Impfsurveillance (RKI) and would need to be requested. | — |

**VE 2022/23 (interim, Europe, Oct 2022–Jan 2023, 16 countries incl. Germany; Kissling 2023, doi:10.2807/1560-7917.ES.2023.28.21.2300116):**
- A(H1N1)pdm09: 28–46 %, children 49–77 %
- A(H3N2): 2–44 %, children 62–70 %
- B/Victoria: ≥50 %, children 87–95 %

No German-only season estimate was found.

### 3.6 Seeding / disease import

| Item | Proposed | Source | Conf. |
|---|---|---|---|
| Shape | Continuous low-level import: linear ramp 2 → 4 persons/day from 2022-09-26 to 2022-10-24, then 4/day to 2022-12-31. This follows the doubling of sentinel influenza positivity (12 % KW40 → 22 % KW43) and national IfSG notifications (897 → 2,208/week). | ARE-Wochenbericht KW 44/2022, doi:10.25646/10757 | L |
| Scaling | × `cologneFactor` 0.5 → **1 → 2 agents/day in the 25 % sample**, i.e. about 4–8 infections/day in the real population | code convention of `SnzCologneOpenProductionScenario` | L |
| Level | Calibration prior, fitted together with `infectiousness`. **No evidence found** for a Cologne importation rate. | — | NE |
| After 2022-12-31 | The last value (2/day) persists (`EpisimUtils.findValidEntry`); negligible next to local incidence | code | H |

---

## 4. Derivation appendix

### 4.1 Symptomatic fraction
The model's "infection" is a shedding episode that can transmit, which is closest to a PCR-confirmed infection. The candidate sources use different denominators:

| Source | Denominator | Symptomatic fraction |
|---|---|---|
| PHIRST (Cohen 2021) | PCR episodes with twice-weekly swabs regardless of symptoms | 268/478 = **0.561** |
| Outbreak investigations (Leung 2015) | outbreak infections, mostly PCR | 1 − 0.16 = 0.84 (0.81–0.87) |
| Challenge studies (Carrat 2008) | inoculated, infected volunteers | 0.669 |
| Household secondary cases (Ip 2017) | 235 PCR-confirmed secondary cases | 1 − 0.11 − 0.13 = 0.76 with ≥2 symptoms; 0.89 with any symptom |
| Flu Watch (Hayward 2014) | serologic infections, including non-shedding ones; not the model's denominator | 23 per 100 |

Chosen: 0.56, the only unselected community cohort on the PCR denominator. Range: 0.56–0.84.

### 4.2 Hospitalisation | symptomatic
The CDC burden model's denominator is symptomatic illness. Hospitalisations are corrected for under-detection, and illnesses are derived from them with a fixed illness : hospitalisation ratio (Reed 2015). The ratio H/I per age is therefore identical across seasons (checked for 2017/18, 2018/19 and 2019/20).

| age | H (2018/19) | I (2018/19) | P_eff = H/I | code value = P_eff / 0.5 |
|---|---|---|---|---|
| 0–4 | 21,046 | 3,018,815 | 0.00697 | 0.01394 |
| 5–17 | 18,159 | 6,622,851 | 0.00274 | 0.00548 |
| 18–49 | 54,978 | 9,794,700 | 0.00561 | 0.01122 |
| 50–64 | 76,617 | 7,224,769 | 0.01060 | 0.02121 |
| 65+ | 204,326 | 2,247,586 | 0.09091 | 0.18182 |

The effective model probability is P(seriouslySick) = code value × `hospitalFactor` × `factorSeriouslySick` (1.0) × `getSeriouslySickFactor` (1/(1+ab^β) = 1 with initial antibodies 0) = P_eff.

**Observation model for comparison with ICOSARI and InEK (coded influenza hospitalisations).** Divide model hospitalisations by the under-detection multipliers: 2.1 (<18 y), 3.1 (18–64 y), 5.2 (65+ y) (Reed 2015, 2010/11). This is a US substitute. A German multiplier can be derived from ICOSARI by comparing the basic and sensitive case definitions (sensitive = 2.2 × basic; Buda 2017, doi:10.1186/s12889-017-4515-1). Request the influenza-specific ratio from RKI.

**Plausibility check for Germany 2022/23:**
1. Coded J09–J11 hospitalisations were 53,830, or 64.8/100k (InEK; Meyer 2026). ICOSARI flu-SARI totals 78.1/100k.
2. × about 3 for under-detection gives ≈ 200–240/100k true flu-associated hospitalisations.
3. ÷ the CDC all-age ratio of 1.30 % gives ≈ 15–18 % symptomatic attack.
4. ÷ 0.56 gives ≈ 27–33 % infection attack.

That is high but plausible for a post-COVID H3N2 season. Flu Watch averaged 18 % per winter, and a 2022 modelling study predicted a larger-than-usual 2022/23 epidemic for Germany, especially in children (doi:10.1111/irv.13091). This is a consistency check only: the US and German age mixes differ.

### 4.3 ICU | hospitalised
Meyer 2026 (InEK, all German hospitals, 2022/23, J09–J11 primary diagnosis):

| age | ICU / hospitalised |
|---|---|
| 0–17 | 4.4 % |
| 18–59 | 9.9 % |
| ≥60 | 11.3 % |
| all | 8.4 % |

These are used directly as P(critical | seriouslySick). Denominator caveat: the model's seriouslySick is the larger, multiplier-corrected set, and the extra, uncoded hospitalisations are probably less often ICU cases. So these shares are an upper bound for the model's denominator. Alternatives: 7.4 % (Spain, primary or secondary diagnosis), 13 % (German claims, Ludwig 2021), 19.5 % (US adults 18–49).

### 4.4 Death | ICU
- Pooled European ICU mortality 0.24 (0.20–0.27), 37 studies, 13,616 patients (Suárez-Sánchez 2025).
- NL 2023/24: 21.9 % in ICU, plus 5.2 % after ICU discharge in hospital.

**Consequence of using 0.24.** Implied in-hospital deaths per hospitalisation = ICU share × 0.24, compared with observed in-hospital mortality in Germany 2022/23 (Meyer 2026):

| age | model | observed | model / observed |
|---|---|---|---|
| 0–17 | 1.06 % | 0.1 % | ×10 |
| 18–59 | 2.4 % | 1.1 % | ×2.2 |
| ≥60 | 2.7 % | 8.6 % | ×0.31 |

Most elderly influenza deaths happen without ICU admission, so the ICU-only death route under-counts them. Alternatively, calibrate P(death | critical) = in-hospital mortality / ICU share: 0.023 (0–17), 0.11 (18–59), 0.76 (≥60). That reproduces in-hospital deaths but no longer means "ICU mortality". All-cause excess mortality during the wave (KW 47/2022–KW 1/2023: 5,043–6,812 excess deaths per week; doi:10.1371/journal.pone.0335982) cannot be represented either way. **Recommendation:** keep 0.24 with the documented semantics, and compare model deaths only to ICU deaths.

### 4.5 Growth rate and R
Least-squares fit of ln(flu-SARI incidence) for 2022-W45..W49 (1.1, 2.0, 3.8, 7.3, 11.4): slope 0.597/week, so r = 0.0853/d. With a gamma generation time (mean m, sd s), R = (1 + r s²/m)^(m²/s²). Results are in section 2.

### 4.6 Model-implied generation time
Monte Carlo over the proposed transitions and the hard-coded curve (40,000 draws, P(sympt) = 0.55): mean generation time 2.77 d with isolation at onset, 3.40 d without. Share of infectiousness from the asymptomatic branch: 72 % vs 38 %. The COVID configuration gives 6.96 d.

### 4.7 Transition dispersion
- `logNormalWithMedianAndSigma(m, σ)`: Lessler's dispersion factor is e^σ, so σ = ln 1.51 = 0.412.
- ICU stay from the IQR: σ = ln(Q3/Q1)/(2·0.6745) = ln 8/1.349 = 1.54.

### 4.8 Import
In-sample agents/day = round(0.5 × (2 + 2·t)) with t ∈ [0, 1] over 2022-09-26..10-24, i.e. 1 → 2/day. The full population receives 4 × that. `interpolateImport` uses day-of-year differences, so segments must not cross 1 January.

### 4.9 Isolation at symptom onset
Van Kerckhove 2013 (England, 2009/10, A(H1N1)pdm09) recorded contacts of people while they had influenza-like illness, and again two weeks after recovery. Illness reduced the number of contacts, "particularly in settings outside the home", enough to bring R to about a quarter of its value; 66 % of transmission came from symptomatic persons.

How the model can express this:
- `atHome` keeps home activities and removes all others ([ActivityParticipationModel.java:44](../src/main/java/org/matsim/episim/model/activity/ActivityParticipationModel.java)); a person who does not isolate keeps everything.
- The expected out-of-home contact volume of a symptomatic person is therefore 1 − p.
- Setting 1 − p = 0.25 gives **p = 0.75**.

Why the value is uncertain, in both directions:
- The quarter refers to R overall. Home contacts also fell somewhat, so the out-of-home reduction alone would need a slightly higher p.
- The study population had ILI (fever and cough), which is sicker than the model's `showingSymptoms`, defined as any symptoms (PHIRST, section 4.1). Milder cases isolate less, which argues for a lower p.
- 2009 was a pandemic with public-health messaging; 2022/23 followed two years of COVID-era norms on staying home when ill.
- No age-specific estimate was found, so the value is flat across ages. Children may be kept home more often.

Sensitivity range: 0.5–0.9. The partial-reduction pattern ("still goes out, but less") cannot be expressed: each person either isolates at home or behaves normally.

**Hospitalised states** (`seriouslySick`, `critical`, `seriouslySickAfterCritical`) are excluded from every contact, the home included, whatever their quarantine status. This is implemented through the disease status, not through quarantine ([AbstractContactModel.java:137](../src/main/java/org/matsim/episim/model/AbstractContactModel.java)). There is no hospital container, so nosocomial transmission is not modelled.

---

## 5. Data sources

Legend. **P** = programmatic today (open download or API). **S** = semi-manual (web export or PDF). **R** = request needed; for these, the **RKI partnership in EPISERVE (FG 36 / NRZ Influenza, Unit 17) is the realistic access route**.

| Source | URL | Content | Space | Time | Age | Subtype | Licence | Access | Cadence | Biases |
|---|---|---|---|---|---|---|---|---|---|---|
| ICOSARI SARI hospitalisation incidence | https://github.com/robert-koch-institut/SARI-Hospitalisierungsinzidenz, doi:10.5281/zenodo.22686153 | Weekly SARI (J09–J22 primary diagnosis) incidence per 100k, subsets flu, COVID, RSV; about 70 Helios sentinel hospitals | national only | weekly (flu series from 2020/21 in the file) | 0–4, 5–14, 15–34, 35–59, 60–79, 80+ | flu vs non-flu only | CC-BY 4.0 | **P** (raw TSV) | weekly (Thu) | ICD-coded, not lab-confirmed; one hospital network; under-coding of influenza. **ICU/ventilation subset only in weekly reports: R** |
| ARE consultation incidence (AGI practice sentinel) | https://github.com/robert-koch-institut/ARE-Konsultationsinzidenz, doi:10.5281/zenodo.22686075 | Weekly ARE GP consultations per 100k | national since 2012/13; **Bundesland (NRW) since 2022/23** | weekly | 0–4, 5–14, 15–34, 35–59, 60+ | none (syndromic) | CC-BY 4.0 | **P** | weekly | Post-COVID consultation behaviour more sensitive (RKI KW44/2022); needs virological positivity to become flu-specific |
| AGI virological sentinel (NRZ) | Weekly ARE reports, e.g. https://edoc.rki.de/bitstream/handle/176904/10411/ARE_Wochenbericht_KW44_2022.pdf (doi per week) | Positivity by type/subtype/lineage and age group | national | weekly | 0–1, 2–4, 5–14, 15–34, 35–59, 60+ | **yes** (A(H3N2), A(H1N1)pdm09, B/Vic, B/Yam) | RKI terms, cite | **S** (PDF); line list **R** | weekly | Small samples (≈100–150 swabs/week) |
| GrippeWeb (+ GrippeWeb-Plus) | https://github.com/robert-koch-institut/GrippeWeb_Daten_des_Wochenberichts; https://www.rki.de/grippeweb | Weekly self-reported ARE/ILI incidence in the population, independent of consultation | national (strata per README, not checked here) | weekly | per README (not checked here) | none; GrippeWeb-Plus self-swabs have virology: **R** | CC-BY 4.0 | **P** / **R** | weekly | Self-selected participants |
| IfSG notified influenza | https://github.com/robert-koch-institut/Influenzafaelle_in_Deutschland, doi:10.5281/zenodo.22686219 | Lab-confirmed notified cases and incidence | national + 16 Länder (NRW) | weekly (reporting week) | 00–14, 15–59, 60+, unknown | not in this file | CC-BY 4.0 | **P** | weekly (Thu), overwritten | **Heavily under-ascertained and testing-driven** (RKI noted increased testing in autumn 2022). Never use as a denominator. |
| SurvStat@RKI 2.0 | https://survstat.rki.de | Same IfSG data, finer: Landkreis (SK Köln), 1-year/5-year age groups, type/subtype where reported, hospitalised/deceased flags | Kreis | weekly | fine | partial | RKI terms of use | **S** (web export, no documented API) | daily-updated | same as IfSG; case-level extracts **R** |
| LfGA NRW weekly ARE / influenza report | https://www.lzg.nrw.de/inf_schutz/meldewesen/infektionsberichte/Influenza-Saisonbericht/index.html | IfSG influenza for NRW by Kreis / Regierungsbezirk Köln | Kreis | weekly | limited | limited | Land terms | **S** (web/PDF) | weekly | same data basis as SurvStat, not finer. **No separate Cologne city series found.** |
| InEK hospital data (DRG) | InEK DatenBrowser (URL not verified here) | All German hospital cases by ICD, ICU, LOS, mortality | national / Land | annual | yes | J09 vs J10/J11 only | InEK terms | **R** / registration | annual | Primary diagnosis only (Meyer 2026 used J09–J11) |
| DIVI Intensivregister (via RKI) | https://github.com/robert-koch-institut/Intensivkapazitaeten_und_COVID-19-Intensivbettenbelegung_in_Deutschland | Daily ICU capacity and COVID-19 occupancy | Kreis | daily | adult/child | **no influenza field** | CC-BY 4.0 | **P** | daily | Not usable for the influenza ICU branch beyond capacity context |
| RKI excess mortality | https://github.com/robert-koch-institut/Daten_des_Uebersterblichkeitsberichts | Weekly observed vs expected deaths by age, sex, region | national / Land | weekly | yes | none | CC-BY 4.0 (per RKI open-data practice; check repo) | **P** | weekly | All-cause, not attributable |
| Destatis causes of death & weekly deaths | https://www-genesis.destatis.de (causes-of-death statistics, table 23211-*); https://www.destatis.de (Sonderauswertung Sterbefallzahlen) | Deaths by ICD-10 (J09–J11), age, Land (annual); all-cause deaths daily/weekly | Land | annual / weekly | yes | none | dl-de/by-2-0 | **P** (GENESIS web service, free registration) | annual / weekly | Influenza strongly under-coded on death certificates |
| IT.NRW Landesdatenbank | https://www.landesdatenbank.nrw.de | NRW deaths by cause, Kreis population | Kreis | annual | yes | none | dl-de/by-2-0 | **P** / **S** | annual | as above |
| ECDC ERVISS | https://erviss.org; https://github.com/EU-ECDC/Respiratory_viruses_weekly_data | Weekly detections by type/subtype/lineage, ILI/ARI, SARI, per country | national | weekly | partial | **yes** | ECDC reuse with attribution | **P** | weekly | Publication resumed 26 June 2026 after the TESSy → EpiPulse transition, with known inconsistencies |
| WHO FluNet / FluID | https://xmart-api-public.who.int/FLUMART/VIW_FNT?$format=csv | Specimens and positives by subtype/lineage per country and week | national | weekly | no | **yes** | WHO terms | **P** (API) | weekly | Testing-volume dependent |
| POLYMOD Germany | doi:10.1371/journal.pmed.0050074; data doi:10.5281/zenodo.1043437 (R: `socialmixr`) | Contact diaries by age and location (home, school, work, leisure, transport), physical vs non-physical | national | 2005/06 | yes | — | CC-BY | **P** | static | Pre-COVID; for validating EpiSim's implicit age mixing |
| COVIMOD (German CoMix-type survey) | doi:10.1186/s12916-021-02139-6 | Contacts during 2020–22 in Germany | national | waves | yes | — | per authors | **R** (authors) | — | **CoMix Germany not found on Zenodo** |
| RKI KV-Impfsurveillance / VacMap | https://www.rki.de/DE/Themen/Infektionskrankheiten/Impfen/Impfquoten/KV-Impfsurveillance/kvis_inhalt.html | Influenza vaccination coverage from SHI claims | Kreis | season (weekly on request) | 60+ subgroups, risk groups | — | RKI terms | **S** / **R** (weekly timing) | annual | SHI-insured only |
| AMELAG wastewater | https://github.com/robert-koch-institut/Abwassersurveillance_AMELAG | Viral load in wastewater (SARS-CoV-2, extended to further viruses) | plant | weekly | — | per README (not checked here) | CC-BY 4.0 | **P** | weekly | Not checked whether influenza covers Cologne in 2022/23; likely not |

**Programmatic today (P):** ICOSARI, ARE, IfSG, GrippeWeb, excess mortality, DIVI, AMELAG (all RKI GitHub); ERVISS; FluNet; POLYMOD; Destatis GENESIS.
**Request via the RKI partnership (R):** ICOSARI influenza ICU and ventilation by age; NRZ virological sentinel line list (subtype × age × week); GrippeWeb-Plus; case-level IfSG with hospitalisation and death flags by Kreis × age × subtype; RKI excess estimates for 2022/23; a German hospital-ascertainment multiplier.

---

## 6. Gaps and risks

1. **Isolation at symptom onset.** See sections 1 and 4.9. The mechanism exists (`symptomaticIsolationProbabilityByAge`, `symptomaticIsolationStatus`), without a delay option. Influenza is set to 75 % home isolation, which is low confidence. What changes as a result:
   - post-onset transmission is possible again, so the infectivity curve (`NormalDistribution(0.5, 2.6)`) and the `showingSymptoms → recovered` duration now affect spread;
   - the incubation leg (median 1.0 d, chosen to keep a presymptomatic day under full isolation) can be reconsidered;
   - `infectiousness` has to be calibrated with the isolation setting in place.

   Partial contact reduction ("goes out, but less") still can't be expressed.
2. **Antibody model.**
   - It's a scalar per strain with a 60-day half-life, with no antigenic distance and no waning over years.
   - In the Cologne infection model (`AgeAndProgressionDependentInfectionModelWithSeasonality`) antibodies do **not** affect susceptibility at all. They only enter severity via `getSeriouslySickFactor = 1/(1+ab^β)`, and that is applied **even on a first infection**, using the antibodies generated by that same infection.
   - The former placeholder (5.0, β = 1, median immune-response multiplier 1) therefore reduced influenza hospitalisation about 6-fold. Setting it to 0.0 neutralises this.
   - Within-season protection is carried by `recovered → susceptible = fixed(365)`. Pre-season immunity (including 2020–22 immunity debt) is only implicit in `ageSusceptibility` and `infectiousness`.
   - What the model would need: a per-individual, per-subtype/lineage immune state (e.g. titre per antigenic cluster) with cross-protection decaying with antigenic distance, waning over years, and pre-season initialisation from age-specific serology.
3. **Several strains.** Recommendation: **one pooled strain** for 2022/23 (almost pure H3N2 through the peak). Separate `VirusStrain`s for A(H3N2), A(H1N1)pdm09 and B/Victoria under `Pathogen("influenza")` only make sense once item 2 is solved. Without cross-immunity handling they would infect the same people independently. Severity and age patterns do differ: B/Victoria hits children hardest (Sauter 2026; VE in children 87–95 %).
4. **Age structure.** The susceptibility table comes from one household cohort (South Africa) and is subtype-specific. Progression durations cannot depend on age, so longer shedding in young children is lost. `HouseholdSusceptibility` (35 % of households ×5, bound in the scenario) is a COVID calibration and is still active.
5. **Death only via ICU** (section 4.4).
6. **Daily discretisation** of a 1–2-day incubation: the model incubation is about 2 d median, versus 1.4 d (all studies) or 1.9 d (outlier excluded).
7. **Scenario inputs in the test copy of the Cologne scenario, as found:**
   - All `educ_*` stay at 0.5 from 2020-04-27 onward. The mobility CSV explicitly excludes `edu`. Fixed in config: schools back at 1.0, plus the NRW autumn and Christmas holidays.
   - Masks: 45 % cloth + 45 % surgical in pt, shops and errands persist from 2021 onward. That fits pt, where masks were still mandated in autumn 2022; the end dates of the NRW mandates were not verified in this review. For shops it is probably too high. **Not changed.**
   - Tracing: disabled in config.
   - Mobility: the input ends 2022-12-31 and the last value persists through the decline phase.
   - The `calibrationParameter`, contact intensities and `spacesPerFacility` are COVID-calibrated.
8. **Seasonality:** fixed in config for 2022/23 (section 3.4). Weather-driven outdoor fractions are recommended.
9. **Code details found along the way:**
   - `EpisimUtils.findValidEntry` returns the default of 1 import/day before the first map entry.
   - `interpolateImport` breaks across 1 January.
   - `runsOnCologneScenario` has no `@Disabled` in the current working copy. That is an uncommitted local change that predates this review.

---

## 7. Ready-to-paste code

Applied to `src/test/java/org/matsim/episim/run/InfluenzaCologneScenarioTest.java`. It needs these extra imports: `EpisimPerson`, `EpisimPerson.DiseaseStatus`, `TracingConfigGroup`, `Transition`, `FixedPolicy`, static `Transition.to`, and a class constant `SEASON_START`.

```java
static final LocalDate SEASON_START = LocalDate.parse("2022-09-26");   // KW 39/2022, 4 weeks before RKI wave onset KW 43 (doi:10.25646/10757)
private static final String[] SCHOOLS = {"educ_primary", "educ_secondary", "educ_tertiary", "educ_other"};
private static final double ICU_LOS_SIGMA = Math.log(8.0 / 1.0) / (2 * 0.6745);  // ICU stay IQR 1-8 d (doi:10.3390/v17111467)

static void configureInfluenza(Config config) {

	EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
	VirusStrainConfigGroup virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
	PathogenConfigGroup pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);

	// 0. season window: 2022/23 on the real Cologne mobility trace
	episimConfig.setStartDate(SEASON_START);

	// 1. one pooled strain (2022/23 sentinel: 95 A(H3N2), 1 A(H1N1)pdm09, 2 B/Victoria to KW 44, doi:10.25646/10757)
	VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getOrAddParams(INFLUENZA_STRAIN);
	strain.setPathogen(INFLUENZA);
	strain.setInfectiousness(1.0);        // NOT an estimate: calibrate to r = 0.085/d (ICOSARI KW45-49/2022, doi:10.5281/zenodo.22686153)
	strain.setFactorSeriouslySick(1.0);   // pooled strain = pathogen baseline
	strain.setFactorCritical(1.0);        // pooled strain = pathogen baseline
	// A(H3N2) vs adults >= 40 y: 12-18 y HR 2.04 (1.19-3.49); <12 y n.s. -> 1.0 (doi:10.1038/s41467-026-76037-x)
	strain.setAgeSusceptibility(Map.of(0, 1.0, 11, 1.0, 12, 2.04, 18, 2.04, 19, 1.0));
	strain.setAgeInfectivity(Map.of(0, 1.0));   // infectivity did not vary with age (doi:10.1056/NEJMoa0905498)

	// 2. natural history (age-dependent); seriouslySick = effective target / hospitalFactor
	double hospitalFactor = episimConfig.getHospitalFactor();
	PathogenConfigGroup.PathogenParams influenza = pathogenConfig.getOrAddParams(INFLUENZA);
	PathogenConfigGroup.ProgressionParams byAge = influenza.getOrAddProgressionParams(true);
	byAge.setShowingSymptomsProbabilityByAge(Map.of(0, 0.56));   // 268/478 PCR infections symptomatic (doi:10.1016/S2214-109X(21)00141-8)
	byAge.setSeriouslySickProbabilityByAge(Map.of(                // CDC 2018-19 hospitalisations / symptomatic illnesses
			0, 0.00697 / hospitalFactor,     // 21,046 / 3,018,815
			5, 0.00274 / hospitalFactor,     // 18,159 / 6,622,851
			18, 0.00561 / hospitalFactor,    // 54,978 / 9,794,700
			50, 0.01060 / hospitalFactor,    // 76,617 / 7,224,769
			65, 0.09091 / hospitalFactor));  // 204,326 / 2,247,586
	byAge.setCriticalProbabilityByAge(Map.of(0, 0.044, 18, 0.099, 60, 0.113));   // ICU share DE 2022/23 (doi:10.1007/s40121-026-01384-7)
	byAge.setDeathProbabilityByAge(Map.of(0, 0.24));                              // European ICU mortality 0.24 (doi:10.1111/irv.70073)
	influenza.setRouteTransmissibility(TransmissionWeights.parse("respiratory=1.0")); // doi:10.1038/ncomms2922, doi:10.1017/S095026881400003X
	// isolation at symptom onset: illness cut R to ~1/4, mostly outside the home (doi:10.1093/aje/kwt196) -> 1 - p = 0.25
	influenza.setSymptomaticIsolationProbabilityByAge(Map.of(0, 0.75));
	influenza.setSymptomaticIsolationStatus(EpisimPerson.QuarantineStatus.atHome);

	// 3. disease progression
	episimConfig.setProgressionConfig(influenzaProgressionConfig(Transition.config()).build());

	// 4. season inputs the Cologne builder does not cover for 2022/23
	FixedPolicy.ConfigBuilder policy = FixedPolicy.parse(episimConfig.getPolicy());
	policy.restrict(SEASON_START, 1.0, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_higher", "educ_other");
	policy.restrict(LocalDate.parse("2022-10-04"), 0.2, SCHOOLS);   // NRW Herbstferien 04.10.-15.10.2022 (0.2 = Cologne closed-school residual)
	policy.restrict(LocalDate.parse("2022-10-17"), 1.0, SCHOOLS);
	policy.restrict(LocalDate.parse("2022-12-23"), 0.2, SCHOOLS);   // NRW Weihnachtsferien 23.12.2022-06.01.2023
	policy.restrict(LocalDate.parse("2023-01-09"), 1.0, SCHOOLS);
	episimConfig.setPolicy(policy.build());
	episimConfig.setLeisureOutdoorFraction(Map.of(                  // EpisimConfigGroup 2020/21 default pattern, repeated
			LocalDate.parse("2022-04-15"), 0.8, LocalDate.parse("2022-09-15"), 0.8, LocalDate.parse("2022-11-15"), 0.1,
			LocalDate.parse("2023-02-15"), 0.1, LocalDate.parse("2023-04-15"), 0.8));
	ConfigUtils.addOrGetModule(config, TracingConfigGroup.class).setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);

	// 5. seeding: 2 -> 4 persons/day x cologneFactor (sentinel positivity 12 % -> 22 %, KW40-43/2022); level = calibration prior
	episimConfig.getInfections_pers_per_day().clear();
	Map<LocalDate, Integer> influenzaImport = new HashMap<>();
	SnzCologneOpenProductionScenario.interpolateImport(influenzaImport, COLOGNE_FACTOR,
			SEASON_START.minusDays(1), LocalDate.parse("2022-10-24"), 2.0, 4.0);
	SnzCologneOpenProductionScenario.interpolateImport(influenzaImport, COLOGNE_FACTOR,
			LocalDate.parse("2022-10-24"), LocalDate.parse("2022-12-31"), 4.0, 4.0);
	episimConfig.setInfections_pers_per_day(INFLUENZA_STRAIN, influenzaImport);

	// 6. antibodies 0.0 keep getSeriouslySickFactor = 1/(1+ab^beta) at 1 (5.0 would cut hospitalisation ~6x)
	AntibodyConfigGroup antibodyConfig = ConfigUtils.addOrGetModule(config, AntibodyConfigGroup.class);
	AntibodyConfigGroup.AntibodyParams influenzaAntibodies = antibodyConfig.getOrAddParams(INFLUENZA_STRAIN);
	for (VirusStrain against : virusStrainConfig.getVirusStrains()) {
		influenzaAntibodies.getInitialAntibodies().put(against, 0.0);
		influenzaAntibodies.getAntibodyRefreshFactors().put(against, 1.0);
	}
}

static Transition.Builder influenzaProgressionConfig(Transition.Builder builder) {
	return builder
			.from(DiseaseStatus.infectedButNotContagious,
					to(DiseaseStatus.contagious, Transition.fixed(1)))                                    // latent 0.5-1 d (doi:10.1093/aje/kwm375)
			.from(DiseaseStatus.contagious,
					to(DiseaseStatus.showingSymptoms, Transition.logNormalWithMedianAndSigma(1.0, Math.log(1.51))), // incubation 1.4 d, disp. 1.51 (doi:10.1016/S1473-3099(09)70069-6) minus latent
					to(DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(4.0, 2.0)))           // asympt. shedding 3-5 d (RKI 2018/19; doi:10.1093/cid/ciw841)
			.from(DiseaseStatus.showingSymptoms,
					to(DiseaseStatus.seriouslySick, Transition.logNormalWithMedianAndSigma(3.0, 0.6)),      // onset->admission 3 (1-4) d (doi:10.1093/ofid/ofad599)
					to(DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(6.0, 2.0)))           // illness ends day 6-7 (doi:10.1093/cid/civ909)
			.from(DiseaseStatus.seriouslySick,
					to(DiseaseStatus.critical, Transition.logNormalWithMedianAndStd(1.0, 1.0)),            // no evidence found: COVID placeholder
					to(DiseaseStatus.recovered, Transition.logNormalWithMeanAndStd(5.6, 6.0)))             // LOS 5.6 (SD 6.0) d (doi:10.1007/s40121-026-01384-7)
			.from(DiseaseStatus.critical,
					to(DiseaseStatus.seriouslySickAfterCritical, Transition.logNormalWithMedianAndSigma(4.0, ICU_LOS_SIGMA)), // ICU 4 (1-8) d (doi:10.3390/v17111467)
					to(DiseaseStatus.deceased, Transition.logNormalWithMedianAndSigma(4.0, ICU_LOS_SIGMA)))  // no evidence found: same as ICU stay
			.from(DiseaseStatus.seriouslySickAfterCritical,
					to(DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(7.0, 7.0)))           // no evidence found: COVID placeholder
			.from(DiseaseStatus.recovered,
					to(DiseaseStatus.susceptible, Transition.fixed(365)));                                // protection halves after 3.5-7 y (doi:10.1038/s41467-019-09652-6)
}
```

---

### Viewing a run in the Episim viewer

`runsOnCologneScenario` writes its output in the same layout as `StarterBatchOpenCologne` in `matsim-episim`, via the test helper `ViewerOutput`:

```
<EPISIM_OUTPUT>/<date>/INF-<NNNNN>/output                  simulation output of run INF1, _info.txt, metadata.yaml, notes.md
<EPISIM_OUTPUT>/<date>/INF-<NNNNN>/output-vis-keep-seeds   viewer package (BatchOutputPacker, seeds kept)
<EPISIM_OUTPUT>/<date>/INF-<NNNNN>/output-vis-no-seeds     viewer package (BatchOutputPacker, seeds averaged)
```

RSV uses the prefix `RSV`. Set `EPISIM_OUTPUT` in the IDE run configuration, e.g. to a local SVN working copy, and commit from there by hand; the tests never run `svn`. Without `EPISIM_OUTPUT` the layout is created inside the test's output directory. The viewer metadata follows `PreparedRun.getMetadata` (city `cologne`, `runName` = pathogen, start date from the config, one option group with `seed` and `pathogen`).

## References (DOIs verified against Europe PMC or the publisher during this review)

- Bedford T et al. 2014, eLife, doi:10.7554/eLife.01914
- Biggerstaff M et al. 2014, BMC Infect Dis, doi:10.1186/1471-2334-14-480
- Buda S et al. 2017, BMC Public Health, doi:10.1186/s12889-017-4515-1
- Carrat F et al. 2008, Am J Epidemiol, doi:10.1093/aje/kwm375
- Cauchemez S et al. 2008, Nature, doi:10.1038/nature06732
- Cauchemez S et al. 2009, NEJM, doi:10.1056/NEJMoa0905498
- CDC burden estimates 2017/18, 2018/19, 2019/20, archive.cdc.gov (pages cited above)
- Cohen C et al. 2021 (PHIRST), Lancet Glob Health, doi:10.1016/S2214-109X(21)00141-8
- Coudeville L et al. 2010, BMC Med Res Methodol, doi:10.1186/1471-2288-10-18
- Cowling BJ et al. 2009, Epidemiology, doi:10.1097/EDE.0b013e31819d1092
- Cowling BJ et al. 2013, Nat Commun, doi:10.1038/ncomms2922
- Dürrwald R et al. 2025, J Med Virol, doi:10.1002/jmv.70530
- FluSurv-NET adults 18–49, 2023, Open Forum Infect Dis, doi:10.1093/ofid/ofad599
- Hayward AC et al. 2014, Lancet Respir Med, doi:10.1016/S2213-2600(14)70034-7
- Hayward AC et al. 2015, AJRCCM, doi:10.1164/rccm.201411-1988OC
- Ip DKM et al. 2016, Clin Infect Dis, doi:10.1093/cid/civ909
- Ip DKM et al. 2017, Clin Infect Dis, doi:10.1093/cid/ciw841
- Killingley B, Nguyen-Van-Tam J 2013, Influenza Other Respir Viruses, doi:10.1111/irv.12080
- Kissling E et al. 2023, Euro Surveill, doi:10.2807/1560-7917.ES.2023.28.21.2300116
- Kucharski AJ et al. 2015, PLoS Biol, doi:10.1371/journal.pbio.1002082
- Leung NHL et al. 2015, Epidemiology, doi:10.1097/EDE.0000000000000340
- Lessler J et al. 2009, Lancet Infect Dis, doi:10.1016/S1473-3099(09)70069-6
- Ludwig M et al. 2021, Int J Infect Dis, doi:10.1016/j.ijid.2020.11.204
- Meyer AC et al. 2026, Infect Dis Ther, doi:10.1007/s40121-026-01384-7
- Mossong J et al. 2008 (POLYMOD), PLoS Med, doi:10.1371/journal.pmed.0050074
- Ramos-Rincón JM et al. 2024, Front Public Health, doi:10.3389/fpubh.2024.1360372
- Ranjeva S et al. 2019, Nat Commun, doi:10.1038/s41467-019-09652-6
- Reed C et al. 2015, PLoS One, doi:10.1371/journal.pone.0118369
- RKI AGI Saisonbericht 2018/19, https://influenza.rki.de/Saisonberichte/2018.pdf
- RKI ARE-Wochenbericht KW 44/2022, doi:10.25646/10757
- RKI Epid Bull 49/2022 (Impfquoten Erwachsene), edoc link above
- Sauter MK et al. 2026, Nat Commun, doi:10.1038/s41467-026-76037-x
- Shaman J et al. 2010, PLoS Biol, doi:10.1371/journal.pbio.1000316
- Smith DJ et al. 2004, Science, doi:10.1126/science.1097211
- Suárez-Sánchez P et al. 2025, Influenza Other Respir Viruses, doi:10.1111/irv.70073
- te Beest DE et al. 2013, Am J Epidemiol, doi:10.1093/aje/kwt132
- Tomori DV et al. 2021 (COVIMOD), BMC Med, doi:10.1186/s12916-021-02139-6
- Tsang TK et al. 2015, J Infect Dis, doi:10.1093/infdis/jiv225
- van der Bie S et al. 2025, Viruses, doi:10.3390/v17111467
- Van Kerckhove K et al. 2013, Am J Epidemiol, doi:10.1093/aje/kwt196
- Vink MA et al. 2014, Am J Epidemiol, doi:10.1093/aje/kwu209
- von der Beck D et al. 2017, PLoS One, doi:10.1371/journal.pone.0180920
- Wong VW et al. 2014, Epidemiol Infect, doi:10.1017/S095026881400003X
- Excess mortality Germany 2020–2023, 2026, PLoS One, doi:10.1371/journal.pone.0335982
- Influenza vaccination 60+ 2022/23, 2025, Bundesgesundheitsblatt, doi:10.1007/s00103-025-04103-8
- Susceptible population ahead of 2022/23 (Germany predicted larger epidemic), 2023, Influenza Other Respir Viruses, doi:10.1111/irv.13091

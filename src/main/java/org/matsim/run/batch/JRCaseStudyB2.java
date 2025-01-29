package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.model.Transition;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.AdaptivePolicy;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.matsim.episim.model.Transition.to;
import static org.matsim.run.modules.SnzBerlinProductionScenario.*;


public class JRCaseStudyB2 implements BatchRun<JRCaseStudyB2.Params> {


	boolean DEBUG = true;
	int runCounter = 0;

	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder()
				.setLocationBasedRestrictions(EpisimConfigGroup.DistrictLevelRestrictions.yesForHomeLocation)
				.setAdaptiveRestrictions(params != null ? params.adaptivePolicy : AdaptiveRestrictions.no)
				.setSnapshot(Snapshot.no)
				.setChristmasModel(ChristmasModel.no)
				.setEasterModel(EasterModel.no)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
				.setSample(25)
				.build();

	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "b2");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		if (DEBUG & runCounter == 1) {
			return null;
		} else if(DEBUG)
			runCounter++;

		SnzBerlinProductionScenario module = getBindings(id, params);

		// global config
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		// episim config
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);

		List<String> subdistricts = Arrays.asList("Spandau", "Neukoelln", "Reinickendorf",
				"Charlottenburg_Wilmersdorf", "Marzahn_Hellersdorf", "Mitte", "Pankow", "Friedrichshain_Kreuzberg",
				"Tempelhof_Schoeneberg", "Treptow_Koepenick", "Lichtenberg", "Steglitz_Zehlendorf");
		episimConfig.setDistricts(subdistricts);
		episimConfig.setDistrictLevelRestrictionsAttribute("subdistrict");

		// produces timeUse.txt output
		episimConfig.setReportTimeUse(EpisimConfigGroup.ReportTimeUse.yes);

		// Set up initial policy - remove all location based restrictions from snz data
		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		builder.apply("2020-01-01", "2022-01-01", (d, e) -> ((HashMap<String, Double>) e.get("locationBasedRf")).clear(), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);


		// General setup of adaptive restrictions
		LocalDate minDate = LocalDate.MIN;

		double workTrigger = params.trigger;
		double leisureTrigger = params.trigger;
		double eduTrigger = params.trigger;
		double shopErrandsTrigger = params.trigger;

		double openFraction = 0.9;
		double restrictedFraction = params.restrictedFraction;

		String startDate = "2020-07-18";
		// GLOBAL ADAPTIVE POLICY
		if (params.adaptivePolicy.equals(AdaptiveRestrictions.yesGlobal)) {
			com.typesafe.config.Config policy = AdaptivePolicy.config()
					.startDate(startDate)
					.restrictionScope(AdaptivePolicy.RestrictionScope.global.toString())
					.incidenceTrigger(workTrigger, workTrigger, "work", "business")
					.incidenceTrigger(leisureTrigger, leisureTrigger, "leisure", "visit")
					.incidenceTrigger(eduTrigger, eduTrigger, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					.incidenceTrigger(shopErrandsTrigger, shopErrandsTrigger, "shop_other", "shop_daily", "errands")
					.initialPolicy(builder)
					.restrictedPolicy(FixedPolicy.config()
							.restrict(minDate, Restriction.of(restrictedFraction), "work")
							.restrict(minDate, Restriction.of(restrictedFraction), "shop_daily")
							.restrict(minDate, Restriction.of(restrictedFraction), "shop_other")
							.restrict(minDate, Restriction.of(restrictedFraction), "errands")
							.restrict(minDate, Restriction.of(restrictedFraction), "business")
							.restrict(minDate, Restriction.of(restrictedFraction), "visit")
							.restrict(minDate, Restriction.of(restrictedFraction), "leisure")
							.restrict(minDate, Restriction.of(restrictedFraction), "educ_higher")
							.restrict(minDate, Restriction.of(restrictedFraction), "educ_kiga")
							.restrict(minDate, Restriction.of(restrictedFraction), "educ_primary")
							.restrict(minDate, Restriction.of(restrictedFraction), "educ_secondary")
							.restrict(minDate, Restriction.of(restrictedFraction), "educ_tertiary")
							.restrict(minDate, Restriction.of(restrictedFraction), "educ_other")
							.restrict(minDate, Restriction.ofLocationBasedRf(new HashMap<>()), "work", "shop_daily", "shop_other", "errands", "business", "visit", "leisure", "educ_higher", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					)
					.openPolicy(FixedPolicy.config()
							.restrict(minDate, Restriction.of(openFraction), "work")
							.restrict(minDate, Restriction.of(openFraction), "shop_daily")
							.restrict(minDate, Restriction.of(openFraction), "shop_other")
							.restrict(minDate, Restriction.of(openFraction), "errands")
							.restrict(minDate, Restriction.of(openFraction), "business")
							.restrict(minDate, Restriction.of(openFraction), "visit")
							.restrict(minDate, Restriction.of(openFraction), "leisure")
							.restrict(minDate, Restriction.of(openFraction), "educ_higher")
							.restrict(minDate, Restriction.of(openFraction), "educ_kiga")
							.restrict(minDate, Restriction.of(openFraction), "educ_primary")
							.restrict(minDate, Restriction.of(openFraction), "educ_secondary")
							.restrict(minDate, Restriction.of(openFraction), "educ_tertiary")
							.restrict(minDate, Restriction.of(openFraction), "educ_other")
							.restrict(minDate, Restriction.ofLocationBasedRf(new HashMap<>()), "work", "shop_daily", "shop_other", "errands", "business", "visit", "leisure", "educ_higher", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					)
					.build();

			episimConfig.setPolicy(AdaptivePolicy.class, policy);
		}

		// LOCAL ADAPTIVE POLICY
		else if (params.adaptivePolicy.equals(AdaptiveRestrictions.yesLocal)) {
			com.typesafe.config.Config policy = AdaptivePolicy.config()
					.startDate(startDate)
					.restrictionScope(AdaptivePolicy.RestrictionScope.local.toString())
					.incidenceTrigger(workTrigger, workTrigger, "work", "business")
					.incidenceTrigger(leisureTrigger, leisureTrigger, "leisure", "visit")
					.incidenceTrigger(eduTrigger, eduTrigger, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					.incidenceTrigger(shopErrandsTrigger, shopErrandsTrigger, "shop_other", "shop_daily", "errands")
					.initialPolicy(builder)
					.restrictedPolicy(FixedPolicy.config()
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "work")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "shop_daily")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "shop_other")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "errands")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "business")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "visit")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "leisure")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_higher")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_kiga")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_primary")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_secondary")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_tertiary")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_other")
					)
					.openPolicy(FixedPolicy.config()
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "work")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "shop_daily")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "shop_other")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "errands")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "business")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "visit")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "leisure")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_higher")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_kiga")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_primary")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_secondary")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_tertiary")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_other")
					)
					.build();

			episimConfig.setPolicy(AdaptivePolicy.class, policy);
		}


		// the following code block are modifications which follow episim batch from BMBF210903. Changes include: weather model, progression model, vaccination compliance, and B117 VOC
		// modifications were NOT included that are in effect AFTER the relevant study period (March 25, 2020 to March 19, 2021)
		{
			// CALIBRATION PARAM
			episimConfig.setCalibrationParameter(1.0e-05);
			episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 0.83);

			// PROGRESSION MODEL
			episimConfig.setProgressionConfig(progressionConfig(params, Transition.config()).build());
			episimConfig.setDaysInfectious(Integer.MAX_VALUE);


			// WEATHER MODEL
			// (TmidFall = 25.0)
			try {
				Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutDoorFractionFromDateAndTemp2(SnzBerlinProductionScenario.INPUT.resolve("tempelhofWeatherUntil20210905.csv").toFile(),
						SnzBerlinProductionScenario.INPUT.resolve("temeplhofWeatherDataAvg2000-2020.csv").toFile(), 0.5, 18.5, 25.0, 18.5, 25.0, 5., 1.0, 1.0);
				episimConfig.setLeisureOutdoorFraction(outdoorFractions);
			} catch (IOException e) {
				e.printStackTrace();
			}


			// MUTATIONS
			// mutation: B117
			VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
			Map<LocalDate, Integer> infPerDayB117 = new HashMap<>();
			infPerDayB117.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayB117.put(LocalDate.parse("2020-12-05"), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayB117);

			virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(1.7);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySick(1.0);


			// VACCINATIONS (change vaccination compliance)
			VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

			// Vaccination compliance
			Map<Integer, Double> vaccinationCompliance = new HashMap<>();
			for (int i = 0; i < 12; i++) vaccinationCompliance.put(i, 0.0);
			for (int i = 12; i < 18; i++) vaccinationCompliance.put(i, 0.7);
			for (int i = 18; i < 25; i++) vaccinationCompliance.put(i, 0.7);
			for (int i = 25; i < 40; i++) vaccinationCompliance.put(i, 0.75);
			for (int i = 40; i < 65; i++) vaccinationCompliance.put(i, 0.8);
			for (int i = 65; i <= 120; i++) vaccinationCompliance.put(i, 0.9);


			vaccinationConfig.setCompliancePerAge(vaccinationCompliance);
		}


		return config;
	}

	private Restriction constructRestrictionWithLocalAndGlobalRf(List<String> subdistricts, double rf) {
		Restriction r = Restriction.ofLocationBasedRf(makeUniformLocalRf(subdistricts, rf));
		r.merge(Restriction.of(rf).asMap());

		return r;
	}

	public static final class Params {

		@EnumParameter(AdaptiveRestrictions.class)
		AdaptiveRestrictions adaptivePolicy;

		@Parameter({0.0, 0.2, 0.4, 0.6})
		double restrictedFraction;

		@Parameter({10, 25, 50, 75, 100, 125, 150})
		double trigger;

		@GenerateSeeds(10)
		public long seed;
	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRCaseStudyB2.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}

	private Map<String, Double> makeUniformLocalRf(List<String> districts, Double rf) {
		Map<String, Double> localRf = new HashMap<>();
		for (String district : districts) {
			localRf.put(district, rf);
		}
		return localRf;
	}

	/**
	 * Adds progression config to the given builder.
	 *
	 * @param params
	 */
	private static Transition.Builder progressionConfig(Params params, Transition.Builder builder) {

		Transition transitionRecSus;

		transitionRecSus = Transition.logNormalWithMedianAndStd(180., 10.);

		return builder
				// Inkubationszeit: Die Inkubationszeit [ ... ] liegt im Mittel (Median) bei 5–6 Tagen (Spannweite 1 bis 14 Tage)
				.from(EpisimPerson.DiseaseStatus.infectedButNotContagious,
						to(EpisimPerson.DiseaseStatus.contagious, Transition.fixed(0)))

				// Dauer Infektiosität:: Es wurde geschätzt, dass eine relevante Infektiosität bereits zwei Tage vor Symptombeginn vorhanden ist und die höchste Infektiosität am Tag vor dem Symptombeginn liegt
				// Dauer Infektiosität: Abstrichproben vom Rachen enthielten vermehrungsfähige Viren bis zum vierten, aus dem Sputum bis zum achten Tag nach Symptombeginn
				.from(EpisimPerson.DiseaseStatus.contagious,
						to(EpisimPerson.DiseaseStatus.showingSymptoms, Transition.logNormalWithMedianAndStd(6., 6.)),    //80%
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(8., 8.)))            //20%

				// Erkankungsbeginn -> Hospitalisierung: Eine Studie aus Deutschland zu 50 Patienten mit eher schwereren Verläufen berichtete für alle Patienten eine mittlere (Median) Dauer von vier Tagen (IQR: 1–8 Tage)
				.from(EpisimPerson.DiseaseStatus.showingSymptoms,
						to(EpisimPerson.DiseaseStatus.seriouslySick, Transition.logNormalWithMedianAndStd(5., 5.)),
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(8., 8.)))

				// Hospitalisierung -> ITS: In einer chinesischen Fallserie betrug diese Zeitspanne im Mittel (Median) einen Tag (IQR: 0–3 Tage)
				.from(EpisimPerson.DiseaseStatus.seriouslySick,
						to(EpisimPerson.DiseaseStatus.critical, Transition.logNormalWithMedianAndStd(1., 1.)),
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(14., 14.)))

				// Dauer des Krankenhausaufenthalts: „WHO-China Joint Mission on Coronavirus Disease 2019“ wird berichtet, dass milde Fälle im Mittel (Median) einen Krankheitsverlauf von zwei Wochen haben und schwere von 3–6 Wochen
				.from(EpisimPerson.DiseaseStatus.critical,
						to(EpisimPerson.DiseaseStatus.seriouslySickAfterCritical, Transition.logNormalWithMedianAndStd(21., 21.)))

				.from(EpisimPerson.DiseaseStatus.seriouslySickAfterCritical,
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(7., 7.)))

				.from(EpisimPerson.DiseaseStatus.recovered,
						to(EpisimPerson.DiseaseStatus.susceptible, transitionRecSus))
				;
	}



}


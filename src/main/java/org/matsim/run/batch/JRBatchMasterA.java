package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.model.Transition;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.matsim.episim.EpisimConfigGroup.ActivityHandling;
import static org.matsim.episim.model.Transition.to;

public class JRBatchMasterA implements BatchRun<JRBatchMasterA.Params> {

	boolean DEBUG = true;

	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder()
				.setSample(DEBUG ? 1 : 25)
				.setLocationBasedRestrictions(params != null ? params.locationBasedRestrictions : EpisimConfigGroup.DistrictLevelRestrictions.yesForHomeLocation)
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setEasterModel(SnzBerlinProductionScenario.EasterModel.no)
				.setChristmasModel(SnzBerlinProductionScenario.ChristmasModel.no)
				.setActivityHandling(ActivityHandling.startOfDay)
				.createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "masterA");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		if (DEBUG) {
			if (params.seed != 4711
					|| params.locationBasedRestrictions != EpisimConfigGroup.DistrictLevelRestrictions.yesForHomeLocation
					|| !params.restrictMitte.equals("yes")) {
				return null;
			}
		}

		SnzBerlinProductionScenario module = getBindings(id, params);

		assert module != null;
		Config config = module.config();

		config.global().setRandomSeed(params.seed);


		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setDistrictLevelRestrictionsAttribute("subdistrict");

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		// clears all location based restrictions from snz data. We only want to look at a synthetic restriction
		builder.apply("2020-01-01", "2022-01-01", (d, e) -> ((HashMap<String, Double>) e.get("locationBasedRf")).clear(), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);

		// restrict mitte in october
		if (params.restrictMitte.equals("yes")) {


			// Change localRf for specific activtiy type and district
			String fromDateLocalRestriction = DEBUG ? "2020-04-01" : "2020-10-01";
			String toDateLocalRestriction = DEBUG ? "2020-05-01" : "2020-10-31";
			double newLocalRf = 0.0;

			// the locationBasedRf must first be cloned to avoid side effects; without clone, changing work will also change leisure, business, and visit activite
			builder.apply(fromDateLocalRestriction, toDateLocalRestriction, (d, e) -> e.put("locationBasedRf", ((HashMap<String, Double>) e.get("locationBasedRf")).clone()), "leisure");
			builder.apply(fromDateLocalRestriction, toDateLocalRestriction, (d, e) -> ((HashMap<String, Double>) e.get("locationBasedRf")).put("Mitte", newLocalRf), "leisure"); //AbstractSnzScenario2020.DEFAULT_ACTIVITIES

			episimConfig.setPolicy(builder.build()); //FixedPolicy.class,

		}

		// the following code block are modifications which follow episim batch from BMBF210903. Changes include: weather model, progression model, vector and mrna vaccines, and B117 VOC
		// modifications were NOT included that are in effect AFTER the relevant study period (March 25, 2020 to March 25, 2021)
		{
			double deltaVacEffect = 0.7;

			// CALIBRATION PARAM
			episimConfig.setCalibrationParameter(1.0e-05);
			episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 0.83);

			// PROGRESSION MODEL
			episimConfig.setProgressionConfig(progressionConfig(params, Transition.config()).build());
			episimConfig.setDaysInfectious(Integer.MAX_VALUE);


			// WEATHER MODEL
			// (TmidFall = 25.0)!!
			try {
				Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutDoorFractionFromDateAndTemp2(SnzBerlinProductionScenario.INPUT.resolve("tempelhofWeatherUntil20210905.csv").toFile(),
						SnzBerlinProductionScenario.INPUT.resolve("temeplhofWeatherDataAvg2000-2020.csv").toFile(), 0.5, 18.5, 25.0, 5., 1.0);
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
			episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayB117);

			virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(1.7);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySick(1.0);

			// VACCINATIONS
			// Vaccination: mrna
			VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
			double effectivnessMRNA = deltaVacEffect;
			double factorShowingSymptomsMRNA = 0.12 / (1 - effectivnessMRNA);
			double factorSeriouslySickMRNA = 0.02 / ((1 - effectivnessMRNA) * factorShowingSymptomsMRNA);
			int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot
			vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
					.setDaysBeforeFullEffect(fullEffectMRNA)
					.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 0.0)
							.atDay(fullEffectMRNA - 7, effectivnessMRNA / 2.)
							.atFullEffect(effectivnessMRNA)
							.atDay(fullEffectMRNA + 5 * 365, 0.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 1.0)
							.atDay(fullEffectMRNA - 7, 1.0 - ((1.0 - factorShowingSymptomsMRNA) / 2.))
							.atFullEffect(factorShowingSymptomsMRNA)
							.atDay(fullEffectMRNA + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 1.0)
							.atDay(fullEffectMRNA - 7, 1.0 - ((1.0 - factorSeriouslySickMRNA) / 2.))
							.atFullEffect(factorSeriouslySickMRNA)
							.atDay(fullEffectMRNA + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
			;

			double effectivnessVector = deltaVacEffect * 0.5 / 0.7;
			double factorShowingSymptomsVector = 0.32 / (1 - effectivnessVector);
			double factorSeriouslySickVector = 0.15 / ((1 - effectivnessVector) * factorShowingSymptomsVector);
			int fullEffectVector = 10 * 7; //second shot after 9 weeks, full effect one week after second shot


			// Vaccination: vector
			vaccinationConfig.getOrAddParams(VaccinationType.vector)
					.setDaysBeforeFullEffect(fullEffectVector)
					.setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 0.0)
							.atDay(fullEffectVector - 7, effectivnessVector / 2.)
							.atFullEffect(effectivnessVector)
							.atDay(fullEffectVector + 5 * 365, 0.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 1.0)
							.atDay(fullEffectVector - 7, 1.0 - ((1.0 - factorShowingSymptomsVector) / 2.))
							.atFullEffect(factorShowingSymptomsVector)
							.atDay(fullEffectVector + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
					.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.MUTB)
							.atDay(1, 1.0)
							.atDay(fullEffectVector - 7, 1.0 - ((1.0 - factorSeriouslySickVector) / 2.))
							.atFullEffect(factorSeriouslySickVector)
							.atDay(fullEffectVector + 5 * 365, 1.0) //10% reduction every 6 months (source: TC)
					)
			;

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

	public static final class Params {

		@EnumParameter(EpisimConfigGroup.DistrictLevelRestrictions.class)
		EpisimConfigGroup.DistrictLevelRestrictions locationBasedRestrictions;

		@GenerateSeeds(10)
		public long seed;


		@StringParameter({"no", "yes"})
		String restrictMitte;
	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRBatchMasterA.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_ITERATIONS, Integer.toString(360),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
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


package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.model.Transition;
import org.matsim.episim.model.VirusStrain;
import org.matsim.run.RunParallel;
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


public class JRCaseStudyA2 implements BatchRun<JRCaseStudyA2.Params> {


	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder()
				.setLocationBasedRestrictions(params != null ? params.locationBasedRestrictions : EpisimConfigGroup.DistrictLevelRestrictions.yesForHomeLocation)
				.setLocationBasedContactIntensity(SnzBerlinProductionScenario.LocationBasedContactIntensity.yes)
				.setSnapshot(Snapshot.no)
				.setChristmasModel(ChristmasModel.no)
				.setEasterModel(EasterModel.no)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
				.setSample(25)
				.build();

	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "a2");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

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

		// add modified contact intensities
		episimConfig.getOrAddContainerParams("home_15").setContactIntensity(1.0 + 3 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_25").setContactIntensity(1.0 + 2 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_35").setContactIntensity(1.0 + 1 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_45").setContactIntensity(1.0).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_55").setContactIntensity(1.0 - 1 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_65").setContactIntensity(1.0 - 2 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_75").setContactIntensity(1.0 - 3 * params.ciModifier).setSpacesPerFacility(1);


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

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.thetaFactor);

		return config;

	}


	public static final class Params {

		@EnumParameter(value = EpisimConfigGroup.DistrictLevelRestrictions.class, ignore = "yesForActivityLocation")
		EpisimConfigGroup.DistrictLevelRestrictions locationBasedRestrictions;

		@Parameter({0.0, 0.1, 0.2, 0.3})
		Double ciModifier;

		@Parameter({0.95, 0.955, 0.96, 0.965, 0.97, 0.975, 0.98, 0.985, 0.99, 0.995, 1.0})
		double thetaFactor;

		@GenerateSeeds(10)
		public long seed;


	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRCaseStudyA2.class.getName(),
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


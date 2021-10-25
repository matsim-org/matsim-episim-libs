package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TestingConfigGroup;
import org.matsim.episim.TestingConfigGroup.TestingParams;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Mutations
 */
public class BMBF210716 implements BatchRun<BMBF210716.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder().setSnapshot(SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "mutations");
	}

//	@Override
//	public int getOffset() {
//		return 20000;
//	}

	@Override
	public Config prepareConfig(int id, Params params) {


		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder()
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setEasterModel(SnzBerlinProductionScenario.EasterModel.no)
				.setChristmasModel(SnzBerlinProductionScenario.ChristmasModel.restrictive)
				.createSnzBerlinProductionScenario();
		Config config = module.config();

//		config.global().setRandomSeed(params.seed);
		config.global().setRandomSeed(3831662765844904176L);


		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		episimConfig.setStartFromSnapshot("../episim-snapshot-270-2020-11-20.zip");
//		episimConfig.setSnapshotInterval(30);


		//restrictions
		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		builder.restrict("2021-04-06", Restriction.ofClosingHours(21, 5), "leisure", "visit");
		Map<LocalDate, Double> curfewCompliance = new HashMap<LocalDate, Double>();
		curfewCompliance.put(LocalDate.parse("2021-04-06"), 0.5);
		curfewCompliance.put(LocalDate.parse("2021-05-16"), 0.0);
		episimConfig.setCurfewCompliance(curfewCompliance);

		LocalDate date1 = LocalDate.parse("2021-08-06");
		//kein zusätzliches Lüften mehr nach den Sommerferien
		builder.restrict("2021-08-07", Restriction.ofCiCorrection(params.ciCorrectionEdu), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		builder.restrict(date1, 1.0, "shop_daily", "shop_other", "errands");

		builder.restrict(date1, 1.0, "work", "business", "leisure", "visit");

		builder.restrict("2021-10-18", 1.0, "educ_higher");

		if(params.masks.equals("no")) {
			builder.restrict(date1, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.0, FaceMask.SURGICAL, 0.0, FaceMask.N95, 0.0)), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
			builder.restrict(date1, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.0, FaceMask.SURGICAL, 0.0, FaceMask.N95, 0.0)), "pt");
		}

		builder.apply("2021-03-26", "2021-04-09", (d, e) -> e.put("fraction", 0.92 * (double) e.get("fraction")), "work", "business");
		builder.apply("2021-06-24", "2021-08-05", (d, e) -> e.put("fraction", 0.92 * (double) e.get("fraction")), "work", "business");

		builder.restrict("2021-10-11", 0.83, "work", "business");
		builder.restrict("2021-10-23", 1.0, "work", "business");

		builder.restrict("2021-12-20", 0.83, "work", "business");
		builder.restrict("2022-01-02", 1.0, "work", "business");

		builder.restrict("2021-12-20", 0.2, "educ_higher");
		builder.restrict("2022-01-02", 1.0, "educ_higher");

		episimConfig.setPolicy(FixedPolicy.class, builder.build());


		//mutations and vaccinations
		Map<LocalDate, Integer> infPerDayB117 = new HashMap<>();
		infPerDayB117.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayB117.put(LocalDate.parse("2020-11-30"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayB117);

		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		double vaccineEffectiveness = vaccinationConfig.getParams(VaccinationType.generic).getEffectiveness();

		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(1.8);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setVaccineEffectiveness(1.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySick(1.5);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySickVaccinated(0.05 / (1-vaccineEffectiveness));

		virusStrainConfigGroup.getOrAddParams(VirusStrain.SARS_CoV_2).setFactorSeriouslySickVaccinated(0.05 / (1-vaccineEffectiveness));

		Map<LocalDate, Integer> infPerDayMUTB = new HashMap<>();
		infPerDayMUTB.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayMUTB.put(LocalDate.parse("2021-04-07"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.MUTB, infPerDayMUTB);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setInfectiousness(2.5);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setVaccineEffectiveness(params.mutBVaccinationEffectiveness / vaccineEffectiveness);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setReVaccineEffectiveness(1.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.MUTB).setFactorSeriouslySickVaccinated(0.05 / (1- params.mutBVaccinationEffectiveness));

		Map<Integer, Double> vaccinationCompliance = new HashMap<>();

		if (params.vaccinationAgeGroup.equals("6m")) {
			vaccinationCompliance.put(0, params.vaccinationCompliance * 0.5);
			for(int i = 1; i<120; i++) vaccinationCompliance.put(i, params.vaccinationCompliance);
		}
		if (params.vaccinationAgeGroup.equals("12y")) {
			for(int i = 0; i<11; i++) vaccinationCompliance.put(i, 0.0);
			for(int i = 12; i<120; i++) vaccinationCompliance.put(i, params.vaccinationCompliance);
		}
		if (params.vaccinationAgeGroup.equals("16y")) {
			for(int i = 0; i<15; i++) vaccinationCompliance.put(i, 0.0);
			for(int i = 16; i<120; i++) vaccinationCompliance.put(i, params.vaccinationCompliance);
		}

		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);

		Map<LocalDate, Integer> reVaccinations = new HashMap<>();

		if (!params.revaccinationDate.equals("no")) {
			reVaccinations.put(LocalDate.parse("2020-01-01"), 0);
			reVaccinations.put(LocalDate.parse(params.revaccinationDate), (int) (params.revaccinationSpeed * 4_831_120));
			vaccinationConfig.setReVaccinationCapacity_pers_per_day(reVaccinations);
		}

		//testing
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);

//		TestType testType = TestType.valueOf(params.testType);

		TestingParams rapidTest = testingConfigGroup.getOrAddParams(TestType.RAPID_TEST);
		TestingParams pcrTest = testingConfigGroup.getOrAddParams(TestType.PCR);

		testingConfigGroup.setStrategy(TestingConfigGroup.Strategy.ACTIVITIES);

		List<String> actsList = new ArrayList<String>();
		actsList.add("leisure");
		actsList.add("work");
		actsList.add("business");
		actsList.add("educ_kiga");
		actsList.add("educ_primary");
		actsList.add("educ_secondary");
		actsList.add("educ_tertiary");
		actsList.add("educ_other");
		actsList.add("educ_higher");
		testingConfigGroup.setActivities(actsList);

		rapidTest.setFalseNegativeRate(0.3);
		rapidTest.setFalsePositiveRate(0.03);

		pcrTest.setFalseNegativeRate(0.1);
		pcrTest.setFalsePositiveRate(0.01);

		testingConfigGroup.setHouseholdCompliance(1.0);

		LocalDate testingStartDate = LocalDate.parse("2021-03-19");

		Map<LocalDate, Double> leisureTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTests = new HashMap<LocalDate, Double>();
		leisureTests.put(LocalDate.parse("2020-01-01"), 0.);
		workTests.put(LocalDate.parse("2020-01-01"), 0.);
		eduTests.put(LocalDate.parse("2020-01-01"), 0.);

		for (int i = 1; i<=31; i++) {
			leisureTests.put(testingStartDate.plusDays(i), 0.05 * i / 31.);
			workTests.put(testingStartDate.plusDays(i), 0.05 * i / 31.);
			eduTests.put(testingStartDate.plusDays(i), 0.2 * i / 31.);
		}

		eduTests.put(LocalDate.parse("2021-06-24"), 0.0);
		workTests.put(LocalDate.parse("2021-06-24"), 0.0);
		leisureTests.put(LocalDate.parse("2021-06-24"), 0.0);

		eduTests.put(LocalDate.parse("2021-08-06"), params.testingRateRapidTest);

		rapidTest.setTestingRatePerActivityAndDate((Map.of(
				"leisure", leisureTests,
				"work", workTests,
				"business", workTests,
				"educ_kiga", eduTests,
				"educ_primary", eduTests,
				"educ_secondary", eduTests,
				"educ_tertiary", eduTests,
				"educ_higher", eduTests,
				"educ_other", eduTests
				)));

		Map<LocalDate, Double> leisureTestsPCR = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTestsPCR = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTestsPCR = new HashMap<LocalDate, Double>();
		leisureTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		workTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		eduTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);

		eduTestsPCR.put(LocalDate.parse("2021-08-06"), params.testingRatePCRTest);

		pcrTest.setTestingRatePerActivityAndDate((Map.of(
				"leisure", leisureTestsPCR,
				"work", workTestsPCR,
				"business", workTestsPCR,
				"educ_kiga", eduTestsPCR,
				"educ_primary", eduTestsPCR,
				"educ_secondary", eduTestsPCR,
				"educ_tertiary", eduTestsPCR,
				"educ_higher", eduTestsPCR,
				"educ_other", eduTestsPCR
				)));

		rapidTest.setTestingCapacity_pers_per_day(Map.of(
				LocalDate.of(1970, 1, 1), 0,
				testingStartDate, Integer.MAX_VALUE));

		pcrTest.setTestingCapacity_pers_per_day(Map.of(
				LocalDate.of(1970, 1, 1), 0,
				testingStartDate, Integer.MAX_VALUE));

		return config;
	}

	public static final class Params {

//		@GenerateSeeds(1)
//		public long seed;

		@StringParameter({"no", "yes"})
		String masks;

//		@Parameter({0.9, 0.8, 0.7})
		@Parameter({0.9, 0.7})
		double mutBVaccinationEffectiveness;

		@Parameter({0.8, 0.95})
		double vaccinationCompliance;

		@StringParameter({"6m", "12y", "16y"})
		String vaccinationAgeGroup;

//		@StringParameter({"no", "2021-10-01"})
		@StringParameter({"no"})
		String revaccinationDate;

		@Parameter({0.02})
		double revaccinationSpeed;

		@Parameter({0.125, 0.25, 0.5, 1.0})
		double ciCorrectionEdu;

//		@StringParameter({"RAPID_TEST", "PCR"})
//		String testType;

		@Parameter({0.0, 0.2, 0.4, 0.6, 0.8, 1.0})
		double testingRateRapidTest;

		@Parameter({0.0, 0.2, 0.4, 0.6, 0.8, 1.0})
		double testingRatePCRTest;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, BMBF210716.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(280),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.EpisimPerson.QuarantineStatus;
import org.matsim.episim.analysis.OutputAnalysis;
import org.matsim.episim.analysis.RValuesFromEvents;
import org.matsim.episim.analysis.VaccinationEffectiveness;
import org.matsim.episim.analysis.VaccinationEffectivenessFromPotentialInfections;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.InfectionModelWithAntibodies;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.model.vaccination.VaccinationStrategy;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;


/**
 * Batch for Bmbf runs
 */
public class Berlin220215 implements BatchRun<Berlin220215.Params> {


	boolean DEBUG = false;

	@Nullable
	@Override
	public Module getBindings(int id, @Nullable Params params) {
		return Modules.override(getBindings(0.0)).with(new AbstractModule() {
			@Override
			protected void configure() {

				Multibinder<VaccinationModel> set = Multibinder.newSetBinder(binder(), VaccinationModel.class);

				set.addBinding().to(VaccinationStrategy.class).in(Singleton.class);
				LocalDate oVacStartDate = null;
				int campaignDuration = 0;
				if (params != null) {
					oVacStartDate = LocalDate.parse(params.oVac);
					campaignDuration = params.dur;
				}

				bind(VaccinationStrategy.Config.class).toInstance(new VaccinationStrategy.Config(oVacStartDate, campaignDuration));
			}
		});
	}

	private SnzBerlinProductionScenario getBindings(double pHousehold) {
		return new SnzBerlinProductionScenario.Builder()
//				.setScale(1.3)
//				.setHouseholdSusc(pHousehold)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
				.setInfectionModel(InfectionModelWithAntibodies.class)
				.setEasterModel(SnzBerlinProductionScenario.EasterModel.no)
				.setChristmasModel(SnzBerlinProductionScenario.ChristmasModel.no)
				.setSample(DEBUG ? 1 : 25)
				.build();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
	}

	@Override
	public Collection<OutputAnalysis> postProcessing() {
		return List.of(
				new VaccinationEffectiveness().withArgs("--district", "Berlin"),//"--input","../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input",
//				"--population-file", "/be_2020_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz",
//				"--district", "Berlin"),//TODO: clean up & check this functions properly
				new RValuesFromEvents().withArgs(),
				new VaccinationEffectivenessFromPotentialInfections().withArgs("--remove-infected")
		);
	}

	boolean oneRun = true;
	@Override
	public Config prepareConfig(int id, Params params) {

		if(!oneRun && DEBUG) return null;
		else oneRun = false;


		SnzBerlinProductionScenario module = getBindings(0.0);

		// GENERAL CONFIGURATION
		//config
		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		//episim-config
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(1.0e-05 * 0.83 * params.thetaFactor); // 0.83 important b/c days infectious was turned up

		episimConfig.setDaysInfectious(Integer.MAX_VALUE);

//		episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-cologne-20210917/" + params.seed + "-270-2020-11-20.zip");
//		episimConfig.setSnapshotSeed(SnapshotSeed.restore);

		// age susceptibility increases by 28% every 10 years
//		if (params.ageDep.equals("yes")) {
//			episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() / 3.5);
//			Map<Integer, Double> map = new HashMap<>();
//			for (int i = 0; i<120; i++) map.put(i, Math.pow(1.02499323, i));
//			episimConfig.setAgeSusceptibility(map);
//		}

		//WEATHER
//		Path INPUT = EpisimUtils.resolveInputPath("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input");
//
//		File weather = INPUT.resolve("tempelhofWeatherUntil20220208.csv").toFile();
//		File avgWeather = INPUT.resolve("temeplhofWeatherDataAvg2000-2020.csv").toFile();
//		Map<LocalDate, Double> outdoorFractions = null;
//		try {
//			outdoorFractions = EpisimUtils.getOutDoorFractionFromDateAndTemp2(weather, avgWeather, 0.5, 18.5, 25., 18.5, params.TmidFall, 5., 1.0);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		episimConfig.setLeisureOutdoorFraction(outdoorFractions);


		// RESTRICTIONS
		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		//2G Policy:
		builder.restrict(LocalDate.parse("2021-11-27"), Restriction.ofSusceptibleRf(0.75), "leisure"); //https://www.berlin.de/en/news/coronavirus/7115029-6098215-2g-rule-for-retail-from-saturday.en.html
//		builder.restrict(LocalDate.parse("2022-03-01"), Restriction.ofSusceptibleRf(params.leis), "leisure");

		builder.restrict(LocalDate.parse("2021-12-20"), Restriction.ofVaccinatedRf(0.75), "leisure"); //TODO: does this make sense?
		//		builder.restrict(restrictionDate, Restriction.ofVaccinatedRf(params.leis), "leisure");


		//Schools  TODO: check end of vacation Berlin, & mask restrictions
		double schoolFac = 0.5;

		// school vacations up to 2023 are already included in SnzBerlinScenario25pct2020

		// contact intensity correction
		builder.restrict(LocalDate.parse("2021-08-17"), Restriction.ofCiCorrection(1 - (0.5 * schoolFac)), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other"); //TODO: is this accurate?

		// masks
		//Maskenpflicht für Schule im Schuljahr 2021/2022
		builder.restrict(LocalDate.parse("2021-08-17"), Restriction.ofMask(FaceMask.N95, 0.9 * schoolFac), "educ_primary", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		//Maskenpflicht für Grundschule entfaellt (bis einschliesslich 6. Klasse): https://www.berlin.de/sen/bjf/service/presse/pressearchiv-2021/pressemitteilung.1130780.php
		builder.restrict(LocalDate.parse("2021-09-28"), Restriction.ofMask(FaceMask.N95, 0.), "educ_primary");
		//Maskenpflicht für Grundschule wieder eingefuert: https://www.berlin.de/sen/bjf/service/presse/pressearchiv-2021/pressemitteilung.1143667.php
		builder.restrict(LocalDate.parse("2021-11-08"), Restriction.ofMask(FaceMask.N95, 0.9 * schoolFac), "educ_primary");

		//university (only included up to May 2020 in SnzBerlinScenario25pct2020)
		builder.restrict("2021-10-18", 1.0, "educ_higher");
		builder.restrict("2021-12-20", 0.2, "educ_higher");
		builder.restrict("2022-01-02", 1.0, "educ_higher");

//		if (params.school.equals("protected")) {
//			builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.9), "educ_primary", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
//			builder.restrict(restrictionDate, Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
//		}

		// TODO: weird warning: local remaining fraction removed (Restriction, line 539)
//		builder.restrict(restrictionDate, 0.78, "work", "leisure", "shop_daily", "shop_other", "visit", "errands", "business");

		episimConfig.setPolicy(builder.build());


		// WEEKDAY DESIGNATION FOR HOLIDAYS
		Map<LocalDate, DayOfWeek> inputDays = new HashMap<>();
//		inputDays.put(LocalDate.parse("2021-11-01"), DayOfWeek.SUNDAY); Allerheiligentag is not an official holiday in Berlin
		//christmas 2021
		inputDays.put(LocalDate.parse("2021-12-24"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2021-12-25"), DayOfWeek.SUNDAY);
		inputDays.put(LocalDate.parse("2021-12-26"), DayOfWeek.SUNDAY);
		inputDays.put(LocalDate.parse("2021-12-27"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2021-12-28"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2021-12-29"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2021-12-30"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2021-12-31"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2022-01-01"), DayOfWeek.SUNDAY);
		//christmas 2022
		inputDays.put(LocalDate.parse("2022-12-24"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2022-12-25"), DayOfWeek.SUNDAY);
		inputDays.put(LocalDate.parse("2022-12-26"), DayOfWeek.SUNDAY);
		inputDays.put(LocalDate.parse("2022-12-27"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2022-12-28"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2022-12-29"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2022-12-30"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2022-12-31"), DayOfWeek.SATURDAY);
		inputDays.put(LocalDate.parse("2023-01-01"), DayOfWeek.SUNDAY);
		episimConfig.setInputDays(inputDays);

		// VARIANTS OF CONCERN  // TODO: leave for now, but update in future
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		//alpha (b117)
		Map<LocalDate, Integer> infPerDayB117 = new HashMap<>();
		infPerDayB117.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayB117.put(LocalDate.parse("2020-12-05"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayB117);

		episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayB117);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(1.55);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySick(1.0);

		//delta
		Map<LocalDate, Integer> infPerDayMUTB = new HashMap<>();
		infPerDayMUTB.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayMUTB.put(LocalDate.parse("2021-05-01"), 1);


		//disease import of MUTB (Delta) in 2021
		double impFacSum = params.impFacSum;//5.0;
		int imp = 16;

		//Sommerferien
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, impFacSum, LocalDate.parse("2021-06-24").plusDays(0),
				LocalDate.parse("2021-07-17").plusDays(0), 1, 48); // summer vacation: beginning -> middle
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, impFacSum, LocalDate.parse("2021-07-18").plusDays(0),
				LocalDate.parse("2021-08-09").plusDays(0), 48, imp); // summer vacation: middle -> end TODO:check

		infPerDayMUTB.put(LocalDate.parse("2021-08-10"), imp);

		//Herbstferien
		double impFacOct = params.impFacOct;// 2.0;
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, impFacOct, LocalDate.parse("2021-10-09").plusDays(0),
				LocalDate.parse("2021-10-16").plusDays(0), imp, imp);
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, impFacOct, LocalDate.parse("2021-10-17").plusDays(0),
				LocalDate.parse("2021-10-24").plusDays(0), imp, 1);
		infPerDayMUTB.put(LocalDate.parse("2021-10-25"), 1);


		episimConfig.setInfections_pers_per_day(VirusStrain.DELTA, infPerDayMUTB);
		double deltaInf = 2.0;
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setInfectiousness(deltaInf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySick(1.25);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySickVaccinated(1.25);


		//omicron
		double oInf = 2.0;
		Map<LocalDate, Integer> infPerDayOmicron = new HashMap<>();
		infPerDayOmicron.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayOmicron.put(LocalDate.parse("2021-11-28"), 4); //TODO: change potentially
		infPerDayOmicron.put(LocalDate.parse("2021-12-03").plusDays(1), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA1, infPerDayOmicron);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setInfectiousness(deltaInf * oInf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySick(0.5 * 1.25);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySickVaccinated(0.5 * 1.25);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorCritical(0.5);

		//BA.2
		Map<LocalDate, Integer> infPerDayBA2 = new HashMap<>();
		infPerDayBA2.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayBA2.put(LocalDate.parse("2022-01-24"), 4); //TODO: change potentially
		infPerDayBA2.put(LocalDate.parse("2022-01-24").plusDays(6), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA2, infPerDayBA2);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setInfectiousness(deltaInf * oInf * 1.5);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorSeriouslySick(0.5 * 1.25);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorSeriouslySickVaccinated(0.5 * 1.25);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorCritical(0.5);

		// VACCINATIONS
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

		//vaccination compliance
		Map<Integer, Double> vaccinationCompliance = new HashMap<>();
		for (int i = 0; i < 5; i++) vaccinationCompliance.put(i, 0.0);
		for (int i = 5; i <= 11; i++) vaccinationCompliance.put(i, 0.4);
		for (int i = 12; i <= 120; i++) vaccinationCompliance.put(i, 1.0);
		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);

		//vaccination capacity (partially set in production scenario but updated here)
		Map<LocalDate, Integer> vaccinations = new HashMap<>(vaccinationConfig.getVaccinationCapacity());
		double population = 4_800_000; // pop of Berlin
		vaccinations.put(LocalDate.parse("2022-01-17"), (int) (0.0035 * population / 7)); // TODO: maybe change? // 0.0035 = 0.3% of pop vaxxed per week
		vaccinations.put(LocalDate.parse("2022-06-30"), 0);
		vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);

		//length of time that vaccination is valid (along w/ start date for this policy)
		vaccinationConfig.setDaysValid(270);
		vaccinationConfig.setValidDeadline(LocalDate.parse("2022-01-01")); //TODO: deadline seems to be a misleading term here

		//vaccination effectiveness against different VOCs
		adaptVacinationEffectiveness(vaccinationConfig);

		//antibodies per strain
		Map<VirusStrain, Double> ak50PerStrain = new HashMap<>();
		ak50PerStrain.put(VirusStrain.SARS_CoV_2, 0.3); // TODO: move from param to here
		ak50PerStrain.put(VirusStrain.ALPHA, 0.3);
		ak50PerStrain.put(VirusStrain.DELTA, 0.4);
		ak50PerStrain.put(VirusStrain.OMICRON_BA1, 2.4);
		ak50PerStrain.put(VirusStrain.OMICRON_BA2, 3.0);

		vaccinationConfig.setAk50PerStrain(ak50PerStrain);

		//booster configuration
		vaccinationConfig.setBeta(1.0);
		configureBooster(vaccinationConfig, 1.0, 3); // TODO go into here


		// TESTING
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);

 		testingConfigGroup.setTestAllPersonsAfter(LocalDate.parse("2021-10-01"));

		TestingConfigGroup.TestingParams rapidTest = testingConfigGroup.getOrAddParams(TestType.RAPID_TEST);
		TestingConfigGroup.TestingParams pcrTest = testingConfigGroup.getOrAddParams(TestType.PCR);

		testingConfigGroup.setStrategy(TestingConfigGroup.Strategy.ACTIVITIES);

		List<String> actsList = new ArrayList<>();
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

		//rapid tests
		Map<LocalDate, Double> leisureTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> kigaPrimaryTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> uniTests = new HashMap<LocalDate, Double>();
		leisureTests.put(LocalDate.parse("2020-01-01"), 0.);
		workTests.put(LocalDate.parse("2020-01-01"), 0.);
		eduTests.put(LocalDate.parse("2020-01-01"), 0.);
		kigaPrimaryTests.put(LocalDate.parse("2020-01-01"), 0.);
		uniTests.put(LocalDate.parse("2020-01-01"), 0.);

		for (int i = 1; i <= 31; i++) {
			leisureTests.put(testingStartDate.plusDays(i),  0.1 * i / 31.);
			workTests.put(testingStartDate.plusDays(i), 0.1 * i / 31.);
			eduTests.put(testingStartDate.plusDays(i), 0.4 * i / 31.); // 2x per week //TODO: maybe diff for berlin
			kigaPrimaryTests.put(testingStartDate.plusDays(i), 0.4 * i / 31.);
			uniTests.put(testingStartDate.plusDays(i), 0.8 * i / 31.);

		}

		kigaPrimaryTests.put(LocalDate.parse("2021-05-10"), 0.0);

		workTests.put(LocalDate.parse("2021-06-04"), 0.05);

		workTests.put(LocalDate.parse("2021-11-24"), 0.5);

		leisureTests.put(LocalDate.parse("2021-06-04"), 0.05);
		leisureTests.put(LocalDate.parse("2021-08-23"), 0.2 * params.leisureTestCorrection); //TODO: revert

		eduTests.put(LocalDate.parse("2021-09-20"), 0.6 * params.eduTestCorrection); // 3x per week TODO: same for berlin?

		rapidTest.setTestingRatePerActivityAndDate((Map.of(
				"leisure", leisureTests,
				"work", workTests,
				"business", workTests,
				"educ_kiga", eduTests,
				"educ_primary", eduTests,
				"educ_secondary", eduTests,
				"educ_tertiary", eduTests,
				"educ_higher", uniTests,
				"educ_other", eduTests
		)));

		// tests for vaccinated agents
		Map<LocalDate, Double> leisureTestsVaccinated = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTestsVaccinated = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTestsVaccinated = new HashMap<LocalDate, Double>();

		leisureTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
		workTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
		eduTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);

		leisureTestsVaccinated.put(LocalDate.parse("2021-08-23"), 0.2);

		rapidTest.setTestingRatePerActivityAndDateVaccinated((Map.of(
				"leisure", leisureTestsVaccinated,
				"work", workTestsVaccinated,
				"business", workTestsVaccinated,
				"educ_kiga", eduTestsVaccinated,
				"educ_primary", eduTestsVaccinated,
				"educ_secondary", eduTestsVaccinated,
				"educ_tertiary", eduTestsVaccinated,
				"educ_higher", eduTestsVaccinated,
				"educ_other", eduTestsVaccinated
		)));


		Map<LocalDate, Double> leisureTestsPCR = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTestsPCR = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> kigaPramaryTestsPCR = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTestsPCR = new HashMap<LocalDate, Double>();

		leisureTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		workTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		kigaPramaryTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);
		eduTestsPCR.put(LocalDate.parse("2020-01-01"), 0.);

		kigaPramaryTestsPCR.put(LocalDate.parse("2021-05-10"), 0.4); // TODO: PCR lolli tests in berlin? ask sydney? remove or replace w/ antigen?

		pcrTest.setTestingRatePerActivityAndDate((Map.of(
				"leisure", leisureTestsPCR,
				"work", workTestsPCR,
				"business", workTestsPCR,
				"educ_kiga", kigaPramaryTestsPCR,
				"educ_primary", kigaPramaryTestsPCR,
				"educ_secondary", eduTestsPCR,
				"educ_tertiary", eduTestsPCR,
				"educ_higher", eduTestsPCR,
				"educ_other", eduTestsPCR
		)));

		// useless for now
		Map<LocalDate, Double> leisureTestsPCRVaccinated = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTestsPCRVaccinated = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTestsPCRVaccinated = new HashMap<LocalDate, Double>();
		leisureTestsPCRVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
		workTestsPCRVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
		eduTestsPCRVaccinated.put(LocalDate.parse("2020-01-01"), 0.);

		pcrTest.setTestingRatePerActivityAndDateVaccinated((Map.of(
				"leisure", leisureTestsPCRVaccinated,
				"work", workTestsPCRVaccinated,
				"business", workTestsPCRVaccinated,
				"educ_kiga", eduTestsPCRVaccinated,
				"educ_primary", eduTestsPCRVaccinated,
				"educ_secondary", eduTestsPCRVaccinated,
				"educ_tertiary", eduTestsPCRVaccinated,
				"educ_higher", eduTestsPCRVaccinated,
				"educ_other", eduTestsPCRVaccinated
		)));

		rapidTest.setTestingCapacity_pers_per_day(Map.of(
				LocalDate.of(1970, 1, 1), 0,
				testingStartDate, Integer.MAX_VALUE));

		pcrTest.setTestingCapacity_pers_per_day(Map.of(
				LocalDate.of(1970, 1, 1), 0,
				testingStartDate, Integer.MAX_VALUE));

		// TRACING
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		tracingConfig.setQuarantineVaccinated((Map.of(
				episimConfig.getStartDate(), false
//				restrictionDate, qv
		)));

		tracingConfig.setQuarantineDuration(Map.of(
				episimConfig.getStartDate(), 14,
				LocalDate.parse("2022-01-01"), 10
			));

		int greenPassValid = 90; // green pass holders do not have to go into quarantine b/c of contact w/ covid
		int greenPassValidBoostered = Integer.MAX_VALUE;

		tracingConfig.setGreenPassValidDays(greenPassValid);
		tracingConfig.setGreenPassBoosterValidDays(greenPassValidBoostered);

		QuarantineStatus qs = QuarantineStatus.atHome;

		tracingConfig.setQuarantineStatus(Map.of(
					episimConfig.getStartDate(), QuarantineStatus.atHome
//					restrictionDate, qs
			));





		return config;
	}

	private void adaptVacinationEffectiveness(VaccinationConfigGroup vaccinationConfig) {

		double factorSymptomsMRNA = 0.4;
		double factorSymptomsVector = 0.76;

		double factorSeriouslySickMRNA = 0.63;
		double factorSeriouslySickVector = 0.19;

		int fullEffectMRNA = 7 * 7; //second shot after 6 weeks, full effect one week after second shot

		vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
				.setDaysBeforeFullEffect(fullEffectMRNA)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.STRAIN_A)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.STRAIN_B)
						.atFullEffect(factorSeriouslySickMRNA)
				)
		;

		int fullEffectVector = 10 * 7; //second shot after 9 weeks, full effect one week after second shot

		vaccinationConfig.getOrAddParams(VaccinationType.vector)
				.setDaysBeforeFullEffect(fullEffectVector)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSymptomsVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
						.atFullEffect(factorSymptomsVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
						.atFullEffect(factorSymptomsVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
						.atFullEffect(factorSymptomsVector)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
						.atFullEffect(factorSymptomsVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSeriouslySickVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
						.atFullEffect(factorSeriouslySickVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
						.atFullEffect(factorSeriouslySickVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
						.atFullEffect(factorSeriouslySickVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
						.atFullEffect(factorSeriouslySickVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.STRAIN_A)
						.atFullEffect(factorSeriouslySickVector)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.STRAIN_B)
						.atFullEffect(factorSeriouslySickVector)
				)
		;

		vaccinationConfig.getOrAddParams(VaccinationType.omicronUpdate)
		.setDaysBeforeFullEffect(fullEffectMRNA)
		.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
				.atFullEffect(factorSymptomsMRNA)
		)
		.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
				.atFullEffect(factorSymptomsMRNA)
		)
		.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
				.atFullEffect(factorSymptomsMRNA)
		)
		.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
				.atFullEffect(factorSymptomsMRNA)
		)
		.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
				.atFullEffect(factorSymptomsMRNA)
		)
		.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
				.atFullEffect(factorSeriouslySickMRNA)
		)
		.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
				.atFullEffect(factorSeriouslySickMRNA)
		)
		.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
				.atFullEffect(factorSeriouslySickMRNA)
		)
		.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
				.atFullEffect(factorSeriouslySickMRNA)
		)
		.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
				.atFullEffect(factorSeriouslySickMRNA)
		)
		.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.STRAIN_A)
				.atFullEffect(factorSeriouslySickMRNA)
		)
		.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.STRAIN_B)
				.atFullEffect(factorSeriouslySickMRNA)
		)
;

		int fullEffectNatural = 2;
		vaccinationConfig.getOrAddParams(VaccinationType.natural)
				.setDaysBeforeFullEffect(fullEffectNatural)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorShowingSymptoms(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
						.atFullEffect(factorSymptomsMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.ALPHA)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.DELTA)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA1)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.OMICRON_BA2)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.STRAIN_A)
						.atFullEffect(factorSeriouslySickMRNA)
				)
				.setFactorSeriouslySick(VaccinationConfigGroup.forStrain(VirusStrain.STRAIN_B)
						.atFullEffect(factorSeriouslySickMRNA)
				)
		;


	}

	private void configureBooster(VaccinationConfigGroup vaccinationConfig, double boosterSpeed, int boostAfter) {

		Map<LocalDate, Integer> boosterVaccinations = new HashMap<>();

		boosterVaccinations.put(LocalDate.parse("2020-01-01"), 0);

		boosterVaccinations.put(LocalDate.parse("2022-01-17"), (int) (4_800_000 * 0.04 * boosterSpeed / 7)); // TODO maybe use new booster data: change date to today
		boosterVaccinations.put(LocalDate.parse("2022-06-30"), 0);

		vaccinationConfig.setReVaccinationCapacity_pers_per_day(boosterVaccinations);


		vaccinationConfig.getOrAddParams(VaccinationType.mRNA)
				.setBoostWaitPeriod(boostAfter * 30 + 6 * 7);
		;

		vaccinationConfig.getOrAddParams(VaccinationType.omicronUpdate)
		.setBoostWaitPeriod(boostAfter * 30 + 6 * 7);
;

		vaccinationConfig.getOrAddParams(VaccinationType.vector)
				.setBoostWaitPeriod(boostAfter * 30 + 9 * 7);
		;
	}

	public static final class Params {
		// ~1000 runs is max


		@GenerateSeeds(5)
		public long seed;

		@Parameter({0.5,1.0})
		public double leisureTestCorrection;

		@Parameter({0.5,1.0})
		public double eduTestCorrection;

		@Parameter({2.0,3.0,4.0,5.0})
		public double impFacSum;

		@Parameter({2.0,3.,4.,5.})
		public double impFacOct;

//		@Parameter({20.0,22.5,25.0,27.5,30})
//		double TmidFall;

		@Parameter({1.})//{0.96, 0.98, 1.0, 1.2, 1.4})
		double thetaFactor;

		@StringParameter({"2099-01-01"})//"2022-03-01", "2099-01-01"})
		String oVac;

		@IntParameter({50})//, 80})
		int dur;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, Berlin220215.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(30),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


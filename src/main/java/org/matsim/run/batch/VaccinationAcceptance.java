package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TestingConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinProductionScenario.ChristmasModel;
import org.matsim.run.modules.SnzBerlinProductionScenario.EasterModel;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Vaccination acceptance
 */
public class VaccinationAcceptance implements BatchRun<VaccinationAcceptance.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder().setSnapshot(SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "vaccination acceptance");
	}
	
//	@Override
//	public int getOffset() {
//		return 5000;
//	}

	@Override
	public Config prepareConfig(int id, Params params) {
		
		ChristmasModel christmasModel = SnzBerlinProductionScenario.ChristmasModel.valueOf(params.christmasModel);
		EasterModel easterModel = SnzBerlinProductionScenario.EasterModel.valueOf(params.easterModel);

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder()
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setEasterModel(easterModel)
				.setChristmasModel(christmasModel)
				.createSnzBerlinProductionScenario();
		Config config = module.config();
		config.global().setRandomSeed(params.seed);


		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		episimConfig.setStartFromSnapshot("../episim-snapshot-270-2020-11-20.zip");
//		episimConfig.setSnapshotInterval(30);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		Map<LocalDate, Integer> infPerDayVariant = new HashMap<>();
		infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayVariant.put(LocalDate.parse(params.b117Date), 1);

		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayVariant);

		builder.restrict("2021-04-06", Restriction.ofClosingHours(21, 5), "leisure", "visit");
		Map<LocalDate, Double> curfewCompliance = new HashMap<LocalDate, Double>();
		curfewCompliance.put(LocalDate.parse("2021-04-06"), 0.5);

		episimConfig.setCurfewCompliance(curfewCompliance);
		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());
		
		
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(1.8);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setVaccineEffectiveness(1.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySick(1.5);
		
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setFactorSeriouslySick(0.5);
		Map<Integer, Double> vaccinationCompliance = new HashMap<>();
		
		for(int i = 0; i<18; i++) vaccinationCompliance.put(i, 0.);
		for(int i = 18; i<120; i++) vaccinationCompliance.put(i, params.vaccinationAcceptance);

		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);
		
		if (!params.vaccination.equals("current")) {
			Map<LocalDate, Integer> vaccinations = new HashMap<>();
			for(Entry<LocalDate, Integer> e : vaccinationConfig.getVaccinationCapacity().entrySet()) vaccinations.put(e.getKey(), e.getValue());
			int population = 4_800_000;
			double dailyPercentageMay = (2./3.) * (250_000. / 7. / 3_645_000.);
			double dailyPercentageJune = (2./3.) * (340_000. / 7. / 3_645_000.);
			vaccinations.put(LocalDate.parse("2021-05-01"), (int) (dailyPercentageMay * population));
			vaccinations.put(LocalDate.parse("2021-06-01"), (int) (dailyPercentageJune * population));
			vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);
		}
		
		
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);
		
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
		testingConfigGroup.setActivities(actsList);
		
		testingConfigGroup.setFalseNegativeRate(0.3);
		testingConfigGroup.setFalsePositiveRate(0.03);
		testingConfigGroup.setHouseholdCompliance(1.0);
						
		LocalDate testingDate = LocalDate.parse("2021-04-19");
		
		double leisureRate = Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[2]) / 100.;
		double workRate = Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[1]) / 100.;
		double eduRate = Integer.parseInt(params.testingRateEduWorkLeisure.split("-")[0]) / 100.;
		
		testingConfigGroup.setTestingRatePerActivityAndDate((Map.of(
				"leisure", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						testingDate, leisureRate
						),
				"work", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						testingDate, workRate
						),
				"business", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						testingDate, workRate
						),
				"educ_kiga", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						testingDate, eduRate
						),
				"educ_primary", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						testingDate, eduRate
						),
				"educ_secondary", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						testingDate, eduRate
						),
				"educ_tertiary", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						testingDate, eduRate
						),
				"educ_other", Map.of(
						LocalDate.parse("2020-01-01"), 0.,
						testingDate, eduRate
						)
				)));

		testingConfigGroup.setTestingCapacity_pers_per_day(Map.of(
				LocalDate.of(1970, 1, 1), 0, 
				testingDate, Integer.MAX_VALUE));
	
		return config;
	}

	public static final class Params {

		@GenerateSeeds(5)
		public long seed;
		
		@StringParameter({"permissive", "restrictive"})
		String christmasModel;
		
		@StringParameter({"yes", "no"})
		String easterModel;
		
		@StringParameter({"2020-12-10", "2020-12-05", "2020-11-30", "2020-11-25"})
		String b117Date;
		
		@Parameter({0.7, 0.8, 0.9, 1.0})
		double vaccinationAcceptance;
		
//		@Parameter({1.0, 1.5})
//		double factorSeriouslySickB117;
//		
//		@Parameter({1.0, 0.5})
//		double factorSeriouslySickVaccine;
		
//		@StringParameter({"0-0-0", "20-5-5"})
		@StringParameter({"20-5-5"})
		String testingRateEduWorkLeisure;
		
		@StringParameter({"current", "prediction"})
		String vaccination;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, VaccinationAcceptance.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


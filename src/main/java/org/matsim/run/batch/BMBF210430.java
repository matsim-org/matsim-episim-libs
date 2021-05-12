package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TestingConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
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
 * Opening strategies
 */
public class BMBF210430 implements BatchRun<BMBF210430.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder().setSnapshot(SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "opening");
	}
	
//	@Override
//	public int getOffset() {
//		return 10000;
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
		config.global().setRandomSeed(4711L);


		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		episimConfig.setStartFromSnapshot("../episim-snapshot-270-2020-11-20.zip");
//		episimConfig.setSnapshotInterval(30);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());


		Map<LocalDate, Integer> infPerDayVariant = new HashMap<>();
		infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayVariant.put(LocalDate.parse("2020-11-30"), 1);

		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayVariant);

		
		builder.restrict("2021-04-06", Restriction.ofClosingHours(21, 5), "leisure", "visit");
		Map<LocalDate, Double> curfewCompliance = new HashMap<LocalDate, Double>();
		curfewCompliance.put(LocalDate.parse("2021-04-06"), 0.5);
		
		LocalDate date1 = LocalDate.parse("2021-05-15");
		LocalDate date2 = date1.plusWeeks(3);
		LocalDate date3 = date2.plusWeeks(3);
		
		builder.restrict(date1, params.sc_1, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
		builder.restrict(date2, params.sc_2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
		//Sommerferien
		builder.restrict("2021-08-07", params.sc_2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		
		builder.restrict(date1, params.sh_e_1, "shop_daily", "shop_other", "errands");
		builder.restrict(date2, params.sh_e_2, "shop_daily", "shop_other", "errands");
		builder.restrict(date3, params.sh_e_3, "shop_daily", "shop_other", "errands");
		
		builder.restrict(date1, params.l_w_1, "work", "business");
		builder.restrict(date2, params.l_w_2, "work", "business");
		builder.restrict(date3, params.l_w_3, "work", "business");
		
		builder.restrict(date1, params.l_w_1, "leisure", "visit");
		builder.restrict(date2, params.l_w_2, "leisure", "visit");
		builder.restrict(date3, params.l_w_3, "leisure", "visit");
		
		if (params.l_w_1 > 0.78) curfewCompliance.put(date1, 0.0);
		if (params.l_w_2> 0.78) curfewCompliance.put(date2, 0.0);
		if (params.l_w_3 > 0.78) curfewCompliance.put(date3, 0.0);
		
		builder.restrict(date2, params.u_2, "educ_higher");
		builder.restrict(date3, params.u_3, "educ_higher");
		
		episimConfig.setCurfewCompliance(curfewCompliance);
		
		if(params.m_3.equals("no")) {
			builder.restrict(date3, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.0, FaceMask.SURGICAL, 0.0, FaceMask.N95, 0.0)), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
			builder.restrict(date3, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.0, FaceMask.SURGICAL, 0.0, FaceMask.N95, 0.0)), "pt");
		}

		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());
		
				
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(1.8);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setVaccineEffectiveness(1.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySick(1.5);
		
		
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setFactorSeriouslySick(0.5);
//		Map<Integer, Double> vaccinationCompliance = new HashMap<>();
//		
//		for(int i = 0; i<120; i++) vaccinationCompliance.put(i, params.vaccinationCompliance);
//			
//		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);
		
		if (!params.vaccination.equals("current")) {
			Map<LocalDate, Integer> vaccinations = vaccinationConfig.getVaccinationCapacity();
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
		actsList.add("educ_higher");
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
				"educ_higher", Map.of(
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

//		@GenerateSeeds(5)
//		public long seed;
		
//		@StringParameter({"2020-12-15"})
//		String newVariantDate;
		
//		@StringParameter({"permissive", "restrictive"})
		@StringParameter({"restrictive"})
		String christmasModel;
		
//		@StringParameter({"yes", "no"})
		@StringParameter({"no"})
		String easterModel;
		
//		@StringParameter({"2020-12-10", "2020-12-05", "2020-11-30", "2020-11-25"})
//		String b117Date;
		
//		@Parameter({1.0, 1.5})
//		double factorSeriouslySickB117;
//		
//		@Parameter({1.0, 0.5})
//		double factorSeriouslySickVaccine;
		
//		@StringParameter({"0-0-0", "20-5-5"})
		@StringParameter({"0-0-0", "20-5-5"})
		String testingRateEduWorkLeisure;
		
		@Parameter({1.0, 0.75, 0.5})
		double sc_1;
		
		@Parameter({1.0, 0.75})
		double sc_2;
		
		@Parameter({1.0, 0.78})
		double sh_e_1;
		
		@Parameter({1.0})
		double sh_e_2;
		
		@Parameter({1.0})
		double sh_e_3;
		
		@Parameter({0.78, 0.9, 1.0})
		double l_w_1;
		
		@Parameter({0.78, 0.9, 1.0})
		double l_w_2;
		
		@Parameter({0.9, 1.0})
		double l_w_3;
		
		@Parameter({0.2, 0.5, 0.75, 1.0})
		double u_2;
		
		@Parameter({0.2, 0.5, 0.75, 1.0})
		double u_3;
		
		@StringParameter({"current"})
		String vaccination;
		
		@StringParameter({"yes", "no"})
		String m_3;
		
//		@StringParameter({"0-0-0", "20-5-5", "20-10-5"})
//		String testingRateEduWorkLeisure2;
//		
//		@StringParameter({"0-0-0", "20-5-5", "20-10-5"})
//		String testingRateEduWorkLeisure3;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, BMBF210430.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


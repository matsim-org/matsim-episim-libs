package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.BatchRun.Parameter;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.AdaptivePolicy;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;


/**
 * Strain paper
 */
public class StrainPaperAdaptivePolicy implements BatchRun<StrainPaperAdaptivePolicy.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder()
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setChristmasModel(SnzBerlinProductionScenario.ChristmasModel.no)
				.setEasterModel(SnzBerlinProductionScenario.EasterModel.no)
				.setVaccinations(SnzBerlinProductionScenario.Vaccinations.no)
				.createSnzBerlinProductionScenario();
		
				}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "strainPaper");
	}
	
//	@Override
//	public int getOffset() {
//		return 1500;
//	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder()
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setChristmasModel(SnzBerlinProductionScenario.ChristmasModel.no)
				.setEasterModel(SnzBerlinProductionScenario.EasterModel.no)
				.setVaccinations(SnzBerlinProductionScenario.Vaccinations.no)
				.createSnzBerlinProductionScenario();
		
		Config config = module.config();
//		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		episimConfig.setStartFromSnapshot("../episim-snapshot-270-2020-11-20.zip");

//		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
//		builder.clearAfter("2020-12-14");
//		episimConfig.setPolicy(FixedPolicy.class, builder.build());
		
		if (params.outdoorModel.equals("no")) episimConfig.setLeisureOutdoorFraction(0.);
		
		LocalDate date = LocalDate.parse("2020-11-21");
		
		double workTrigger = params.trigger;
		double leisureTrigger = params.trigger;
		double eduTrigger = params.trigger;
		double shopErrandsTrigger = params.trigger;
		
		double openFraction = 0.9;
		double restrictedFraction = 0.6;
		
		com.typesafe.config.Config policy = AdaptivePolicy.config()
				.incidenceTrigger(workTrigger, workTrigger, "work", "business")
				.incidenceTrigger(leisureTrigger, leisureTrigger, "leisure", "visit")
				.incidenceTrigger(eduTrigger, eduTrigger, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
				.incidenceTrigger(shopErrandsTrigger, shopErrandsTrigger, "shop_other", "shop_daily", "errands")
				.initialPolicy(FixedPolicy.config()
						.restrict("2020-08-01", Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.9 * 0.9, FaceMask.SURGICAL, 0.9 * 0.1)), "pt", "shop_daily", "shop_other", "errands")
						.restrict("2020-08-08", Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
						.restrict("2020-10-25", Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.8 * 0.9, FaceMask.SURGICAL, 0.8 * 0.1)), "educ_higher", "educ_tertiary", "educ_other")
						)
				.restrictedPolicy(FixedPolicy.config()
						.restrict(date, Restriction.of(restrictedFraction), "work")
						.restrict(date, Restriction.of(restrictedFraction), "shop_daily")
						.restrict(date, Restriction.of(restrictedFraction), "shop_other")
						.restrict(date, Restriction.of(restrictedFraction), "errands")
						.restrict(date, Restriction.of(restrictedFraction), "business")
						.restrict(date, Restriction.of(restrictedFraction), "visit")
						.restrict(date, Restriction.of(restrictedFraction), "leisure")
						.restrict(date, Restriction.of(restrictedFraction), "educ_higher")
						.restrict(date, Restriction.of(.2), "educ_kiga")
						.restrict(date, Restriction.of(.2), "educ_primary")
						.restrict(date, Restriction.of(.2), "educ_secondary")
						.restrict(date, Restriction.of(.2), "educ_tertiary")
						.restrict(date, Restriction.of(.2), "educ_other")
				)
				.openPolicy(FixedPolicy.config()
						.restrict(date, Restriction.of(openFraction), "work")
						.restrict(date, Restriction.of(openFraction), "shop_daily")
						.restrict(date, Restriction.of(openFraction), "shop_other")
						.restrict(date, Restriction.of(openFraction), "errands")
						.restrict(date, Restriction.of(openFraction), "business")
						.restrict(date, Restriction.of(openFraction), "visit")
						.restrict(date, Restriction.of(openFraction), "leisure")
						.restrict(date, Restriction.of(openFraction), "educ_higher")
						.restrict(date, Restriction.of(1.), "educ_kiga")
						.restrict(date, Restriction.of(1.), "educ_primary")
						.restrict(date, Restriction.of(1.), "educ_secondary")
						.restrict(date, Restriction.of(1.), "educ_tertiary")
						.restrict(date, Restriction.of(1.), "educ_other")
						)
				.build();		
		
		episimConfig.setPolicy(AdaptivePolicy.class, policy);
		
		Map<LocalDate, Integer> infPerDayB117 = new HashMap<>();
		infPerDayB117.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayB117.put(LocalDate.parse(params.b117date), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayB117);
		
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		double vaccineEffectiveness = vaccinationConfig.getEffectiveness();
		
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		
		virusStrainConfigGroup.getOrAddParams(VirusStrain.SARS_CoV_2).setVaccineEffectiveness(1.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.SARS_CoV_2).setReVaccineEffectiveness(1.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.SARS_CoV_2).setFactorSeriouslySickVaccinated(0.05 / (1-vaccineEffectiveness));

		
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(params.b117inf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setVaccineEffectiveness(1.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setReVaccineEffectiveness(1.0);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySick(1.5);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySickVaccinated(0.05 / (1-vaccineEffectiveness));
		
		


		if (!params.b1351inf.equals("no")) {
			Map<LocalDate, Integer> infPerDayB1351 = new HashMap<>();
			infPerDayB1351.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayB1351.put(LocalDate.parse(params.b1351date), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.B1351, infPerDayB1351);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.B1351).setInfectiousness(Double.parseDouble(params.b1351inf));
			virusStrainConfigGroup.getOrAddParams(VirusStrain.B1351).setVaccineEffectiveness(params.b1351VaccinationEffectiveness / vaccineEffectiveness);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.B1351).setReVaccineEffectiveness(1.0);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.B1351).setFactorSeriouslySickVaccinated(0.05 / (1- params.b1351VaccinationEffectiveness));

		}
		
		if (!params.vaccinations.equals("no")) {
			vaccinationConfig.setEffectiveness(0.9);
			vaccinationConfig.setDaysBeforeFullEffect(28);
			
			Map<LocalDate, Integer> vaccinations = new HashMap<>();
			
			vaccinations.put(LocalDate.parse("2020-01-01"), 0);
			vaccinations.put(LocalDate.parse("2021-01-01"), (int) (0.05 * 4_831_120 / 59));
			vaccinations.put(LocalDate.parse("2021-03-01"), (int) ((0.119 - 0.05) * 4_831_120 / 31));
			vaccinations.put(LocalDate.parse("2021-04-01"), (int) ((0.248 - 0.119) * 4_831_120 / 29));
			
			vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);
			
			Map<LocalDate, Integer> reVaccinations = new HashMap<>();
			
			vaccinations.put(LocalDate.parse("2020-01-01"), 0);
			
			if(params.vaccinations.equals("revaccination")) reVaccinations.put(LocalDate.parse("2021-08-01"), (int) ((0.248 - 0.119) * 2 * 4_831_120 / 29));
			if(params.vaccinations.equals("quickRevaccination")) reVaccinations.put(LocalDate.parse("2021-08-01"), (int) ((0.248 - 0.119) * 4 * 4_831_120 / 29));
			
			vaccinationConfig.setReVaccinationCapacity_pers_per_day(reVaccinations);
			
			vaccinationConfig.setFactorSeriouslySick(0.5);
			
			Map<Integer, Double> vaccinationCompliance = new HashMap<>();
			
			for(int i = 0; i<18; i++) vaccinationCompliance.put(i, 0.);
			for(int i = 18; i<120; i++) vaccinationCompliance.put(i, params.vaccinationCompliance);

			vaccinationConfig.setCompliancePerAge(vaccinationCompliance);
		}
		

		return config;
	}

	public static final class Params {

//		@GenerateSeeds(3)
//		public long seed;
		
		@StringParameter({"2020-12-15"})
		String b117date;
		
//		@Parameter({1.2, 1.5, 1.8, 2.1, 2.4})
		@Parameter({1.5, 1.8, 2.1})
		double b117inf;
		
		@StringParameter({"2021-04-01"})
		String b1351date;
		
		@StringParameter({"no", "1.0", "1.5", "1.8"})
		String b1351inf;
		
		@StringParameter({"no", "noRevaccination", "revaccination", "quickRevaccination"})
		String vaccinations;
		
		@Parameter({1.0, 0.8, 0.6})
		double vaccinationCompliance;
		
		@Parameter({0.9, 0.7, 0.5, 0.3, 0.1, 0.0})
		double b1351VaccinationEffectiveness;
		
//		@Parameter({0.65})
//		double restrictedFraction;
//		
//		@Parameter({0.9})
//		double openFraction;
		
		@Parameter({Integer.MAX_VALUE, 100.})
//		@Parameter({100.})
		double trigger;
		
		@StringParameter({"no", "yes"})
		String outdoorModel;
		

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, StrainPaperAdaptivePolicy.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


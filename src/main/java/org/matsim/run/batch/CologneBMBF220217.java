package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.EpisimConfigGroup.SnapshotSeed;
import org.matsim.episim.EpisimPerson.QuarantineStatus;
import org.matsim.episim.analysis.OutputAnalysis;
import org.matsim.episim.analysis.RValuesFromEvents;
import org.matsim.episim.analysis.VaccinationEffectiveness;
import org.matsim.episim.analysis.VaccinationEffectivenessFromPotentialInfections;
import org.matsim.episim.model.AntibodyModel;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.ImmunityEvent;
import org.matsim.episim.model.InfectionModelWithAntibodies;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.model.vaccination.VaccinationStrategy2;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.annotation.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;


/**
 * Batch for Bmbf runs
 */
public class CologneBMBF220217 implements BatchRun<CologneBMBF220217.Params> {


	@Nullable
	@Override
	public Module getBindings(int id, @Nullable Params params) {
		return Modules.override(getBindings(0.0)).with(new AbstractModule() {
			@Override
			protected void configure() {

				Multibinder<VaccinationModel> set = Multibinder.newSetBinder(binder(), VaccinationModel.class);

				set.addBinding().to(VaccinationStrategy2.class).in(Singleton.class);
				boolean vaccinateRecovered = false;
				boolean vaccinateYoung = false;
				boolean vaccinateOld = false;
				double mutEsc = 0;
				String vacStart = "2020-01-01";

				if (params != null) {
					if (params.vac.contains("over18")) {
						vaccinateYoung = true;
						vaccinateOld = true;
					}

					if (params.vac.contains("over50"))
						vaccinateOld = true;

					if (params.vac.contains("Plus"))
						vaccinateRecovered = true;
					mutEsc = params.mutEsc;
					vacStart = params.vacStart;
				}

				bind(VaccinationStrategy2.Config.class).toInstance(new VaccinationStrategy2.Config(LocalDate.parse(vacStart), vaccinateRecovered, vaccinateYoung, vaccinateOld));
				
				//initial antibodies
				Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();
				Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();

				configureAntibodies(initialAntibodies, antibodyRefreshFactors, mutEsc);
				
				bind(AntibodyModel.Config.class).toInstance(new AntibodyModel.Config(initialAntibodies, antibodyRefreshFactors));
				
			}

			private void configureAntibodies(Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies,
					Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors, double mutEsc) {
				for (VaccinationType immunityType : VaccinationType.values()) {
					initialAntibodies.put(immunityType, new EnumMap<>( VirusStrain.class ) );
					for (VirusStrain virusStrain : VirusStrain.values()) {

						if (immunityType == VaccinationType.mRNA) {
							initialAntibodies.get(immunityType).put(virusStrain, 29.2); //10.0
						}
						else if (immunityType == VaccinationType.vector) {
							initialAntibodies.get(immunityType).put(virusStrain, 6.8);  //2.5
						}
						else {
							initialAntibodies.get(immunityType).put(virusStrain, 5.0);
						}
					}
				}

				for (VirusStrain immunityType : VirusStrain.values()) {
					initialAntibodies.put(immunityType, new EnumMap<>( VirusStrain.class ) );
					for (VirusStrain virusStrain : VirusStrain.values()) {
						initialAntibodies.get(immunityType).put(virusStrain, 5.0);
					}
				}
				
				//mRNAAlpha, mRNADelta, mRNABA1 comes from Sydney's calibration. 
				//The other values come from Rössler et al. 
				
				//Wildtype
				double mRNAAlpha = 29.2;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.SARS_CoV_2, mRNAAlpha);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.SARS_CoV_2, 0.01);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.SARS_CoV_2, 0.01);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.SARS_CoV_2, 0.01);

				//Alpha
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.ALPHA, mRNAAlpha);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.ALPHA, 0.01);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.ALPHA, 0.01);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.ALPHA, 0.01);

				//DELTA
				double mRNADelta = 10.9;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.DELTA, mRNADelta);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.DELTA, mRNADelta * 150./300.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.DELTA, mRNADelta * 64./300.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.DELTA, mRNADelta * 64./300.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.DELTA, mRNADelta * 450./300.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.DELTA, 0.2 / 6.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.DELTA, 0.2 / 6.4);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.DELTA, 0.2 / 6.4);

				//BA.1
				double mRNABA1 = 1.9;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA1, mRNABA1);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA1, mRNABA1 * 4./20.); //???
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6./20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6./20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 8./20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA1, 64.0 / 300.);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4);

				//BA.2
				double mRNABA2 = mRNABA1 / 1.4;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA2, mRNABA2);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA2, mRNABA2 * 4./20.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6./20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6./20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 8./20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / 1.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA2, 64.0 / 300.);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.OMICRON_BA2, 64.0 / 300.);

				//BA.2
				double mRNAStrainA = mRNABA2 * mutEsc;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.STRAIN_A, mRNAStrainA);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.STRAIN_A, mRNAStrainA * 4./20.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.STRAIN_A, mRNAStrainA * 6./20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.STRAIN_A, mRNAStrainA * 6./20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.STRAIN_A, mRNAStrainA * 8./20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.STRAIN_A, mutEsc * 64.0 / 300. / 1.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.STRAIN_A, mutEsc * 64.0 / 300.);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.STRAIN_A, 64.0 / 300.);
				
				for (VaccinationType immunityType : VaccinationType.values()) {
					antibodyRefreshFactors.put(immunityType, new EnumMap<>( VirusStrain.class ) );
					for (VirusStrain virusStrain : VirusStrain.values()) {

						if (immunityType == VaccinationType.mRNA) {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
						}
						else if (immunityType == VaccinationType.vector) {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, 5.0);
						}
						else {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, Double.NaN);
						}

					}
				}

				for (VirusStrain immunityType : VirusStrain.values()) {
					antibodyRefreshFactors.put(immunityType, new EnumMap<>( VirusStrain.class ) );
					for (VirusStrain virusStrain : VirusStrain.values()) {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
					}
				}
			}
		});
	}

	private SnzCologneProductionScenario getBindings(double pHousehold) {
		return new SnzCologneProductionScenario.Builder()
				.setScaleForActivityLevels(1.3 )
				.setSuscHouseholds_pct(pHousehold )
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
				.setInfectionModel(InfectionModelWithAntibodies.class)
				.build();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("cologne", "calibration");
	}

	@Override
	public Collection<OutputAnalysis> postProcessing() {
		return List.of(
				new VaccinationEffectiveness().withArgs(),
				new RValuesFromEvents().withArgs(),
				new VaccinationEffectivenessFromPotentialInfections().withArgs("--remove-infected")
		);
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		LocalDate restrictionDate = LocalDate.parse("2022-03-20");

		SnzCologneProductionScenario module = getBindings(0.0);

		Config config = module.config();
				
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 0.96 * 1.06);

		//local (see svn for more snapshots with different dates)
//		episimConfig.setStartFromSnapshot("../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input/snapshots-cologne-20220218/" + params.seed + "-540-2021-08-17.zip");

		//cluster
//		episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-cologne-20220316/" + params.seed + "-450-2021-05-19.zip");
//		episimConfig.setSnapshotSeed(SnapshotSeed.restore);

		//restrictions
		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());


		builder.restrict(LocalDate.parse("2021-12-01"), Restriction.ofVaccinatedRf(0.75), "leisure");
		builder.restrict(restrictionDate, Restriction.ofVaccinatedRf(1.0), "leisure");
		
		//2G
		builder.restrict(LocalDate.parse("2021-11-22"), Restriction.ofSusceptibleRf(0.75), "leisure");
		builder.restrict(restrictionDate, Restriction.ofSusceptibleRf(1.0), "leisure");

		double schoolFac = 0.5;
		builder.restrict(LocalDate.parse("2021-08-17"), Restriction.ofCiCorrection(1 - (0.5 * schoolFac)), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");

		builder.restrict(LocalDate.parse("2021-08-17"), Restriction.ofMask(FaceMask.N95, 0.9 * schoolFac), "educ_primary", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-11-02"), Restriction.ofMask(FaceMask.N95, 0.0), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-12-02"), Restriction.ofMask(FaceMask.N95, 0.9 * schoolFac), "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		
//		builder.restrict(restrictionDate, 0.78, "work", "leisure", "shop_daily", "shop_other", "visit", "errands", "business");

		episimConfig.setPolicy(builder.build());

		Map<LocalDate, DayOfWeek> inputDays = new HashMap<>();
		inputDays.put(LocalDate.parse("2021-11-01"), DayOfWeek.SUNDAY);

		episimConfig.setInputDays(inputDays);

		//mutations
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		Map<LocalDate, Integer> infPerDayB117 = new HashMap<>();
		infPerDayB117.put(LocalDate.parse("2020-01-01"), 0);

		infPerDayB117.put(LocalDate.parse("2021-01-16"), 20);
		infPerDayB117.put(LocalDate.parse("2021-01-16").plusDays(1), 1);
		infPerDayB117.put(LocalDate.parse("2020-12-31"), 1);

		episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayB117);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(1.7);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySick(1.0);

		Map<LocalDate, Integer> infPerDayMUTB = new HashMap<>();
		infPerDayMUTB.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayMUTB.put(LocalDate.parse("2021-06-21"), 10);
		infPerDayMUTB.put(LocalDate.parse("2021-06-21").plusDays(1), 1);


		//disease import 2021
		double cologneFactor = 0.5;
		int imp1 = 10;
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, cologneFactor * 5, LocalDate.parse("2021-07-03").plusDays(0),
				LocalDate.parse("2021-07-25").plusDays(0), 1, 48);
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, cologneFactor * 5, LocalDate.parse("2021-07-26").plusDays(0),
				LocalDate.parse("2021-08-17").plusDays(0), 48, imp1);

		int imp =(int) (48 * 0.5);
		imp = Math.max(imp, imp1);

		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, cologneFactor * 5, LocalDate.parse("2021-10-09").plusDays(0),
				LocalDate.parse("2021-10-16").plusDays(0), imp1, imp);
		SnzCologneProductionScenario.interpolateImport(infPerDayMUTB, cologneFactor * 5, LocalDate.parse("2021-10-17").plusDays(0),
				LocalDate.parse("2021-10-24").plusDays(0), imp, imp1);
		infPerDayMUTB.put(LocalDate.parse("2021-10-25"), 1);
		;
		episimConfig.setInfections_pers_per_day(VirusStrain.DELTA, infPerDayMUTB);
		double deltaInf = 2.7;
		double deltaHos = 1.5;
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setInfectiousness(deltaInf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySick(deltaHos);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySickVaccinated(deltaHos);


		//omicron
		double ba1Inf = 2.1;
		double oHos = 0.3;
		if (ba1Inf > 0) {
			Map<LocalDate, Integer> infPerDayOmicron = new HashMap<>();
			infPerDayOmicron.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayOmicron.put(LocalDate.parse("2021-11-19"), 4);
			infPerDayOmicron.put(LocalDate.parse("2021-11-19").plusDays(6), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA1, infPerDayOmicron);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setInfectiousness(deltaInf * ba1Inf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySick(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySickVaccinated(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorCritical(oHos);
		}
		

		//BA.2
		double ba2Inf = 1.5;
		if (ba2Inf > 0) {
			Map<LocalDate, Integer> infPerDayBA2 = new HashMap<>();
			infPerDayBA2.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayBA2.put(LocalDate.parse("2021-12-25"), 4);
			infPerDayBA2.put(LocalDate.parse("2021-12-25").plusDays(6), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA2, infPerDayBA2);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setInfectiousness(deltaInf * ba1Inf * ba2Inf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorSeriouslySick(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorSeriouslySickVaccinated(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setFactorCritical(oHos);
		}
		
		//STRAIN_A
		if (params.mutInf > 0) {
			Map<LocalDate, Integer> infPerDayStrainA = new HashMap<>();
			infPerDayStrainA.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayStrainA.put(LocalDate.parse(params.mutDate), 4);
			infPerDayStrainA.put(LocalDate.parse(params.mutDate).plusDays(6), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.STRAIN_A, infPerDayStrainA);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setInfectiousness(deltaInf * ba1Inf * ba2Inf * params.mutInf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(0.3);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySickVaccinated(0.3);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorCritical(0.35);
		}


		//vaccinations
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setUseIgA(Boolean.valueOf(params.ba1ba2x));
		Map<Integer, Double> vaccinationCompliance = new HashMap<>();
		for (int i = 0; i < 5; i++) vaccinationCompliance.put(i, 0.0);
		for (int i = 5; i <= 11; i++) vaccinationCompliance.put(i, 0.4);
		for (int i = 12; i <= 120; i++) vaccinationCompliance.put(i, 1.0);
		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);

		Map<LocalDate, Integer> vaccinations = new HashMap<>();
		double population = 2_352_480;
		vaccinations.put(LocalDate.parse("2022-02-15"), (int) (0.0035 * population / 7));
		vaccinations.put(LocalDate.parse("2022-06-30"), 0);

		vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);
		vaccinationConfig.setDaysValid(270);
		vaccinationConfig.setValidDeadline(LocalDate.parse("2022-01-01"));

		vaccinationConfig.setBeta(1.2);

		configureBooster(vaccinationConfig, 1.0, 3);

		//testing
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);

 		testingConfigGroup.setTestAllPersonsAfter(LocalDate.parse("2021-10-01"));

		TestingConfigGroup.TestingParams rapidTest = testingConfigGroup.getOrAddParams(TestType.RAPID_TEST);
		TestingConfigGroup.TestingParams pcrTest = testingConfigGroup.getOrAddParams(TestType.PCR);

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
			eduTests.put(testingStartDate.plusDays(i), 0.4 * i / 31.);
			kigaPrimaryTests.put(testingStartDate.plusDays(i), 0.4 * i / 31.);
			uniTests.put(testingStartDate.plusDays(i), 0.8 * i / 31.);

		}

		kigaPrimaryTests.put(LocalDate.parse("2021-05-10"), 0.0);

		workTests.put(LocalDate.parse("2021-06-04"), 0.05);

		workTests.put(LocalDate.parse("2021-11-24"), 0.5);

		leisureTests.put(LocalDate.parse("2021-06-04"), 0.05);
		leisureTests.put(LocalDate.parse("2021-08-23"), 0.2);

		eduTests.put(LocalDate.parse("2021-09-20"), 0.6);
		
		if (params.testing.equals("no")) {
			kigaPrimaryTests.put(restrictionDate, 0.0);
			workTests.put(restrictionDate, 0.0);
			leisureTests.put(restrictionDate, 0.0);
			eduTests.put(restrictionDate, 0.0);
			uniTests.put(restrictionDate, 0.0);
		}

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

		Map<LocalDate, Double> leisureTestsVaccinated = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTestsVaccinated = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTestsVaccinated = new HashMap<LocalDate, Double>();

		leisureTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
		workTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);
		eduTestsVaccinated.put(LocalDate.parse("2020-01-01"), 0.);

		leisureTestsVaccinated.put(LocalDate.parse("2021-08-23"), 0.2);
		
		if (params.testing.equals("no")) {
			leisureTestsVaccinated.put(restrictionDate, 0.0);
			workTestsVaccinated.put(restrictionDate, 0.0);
			eduTestsVaccinated.put(restrictionDate, 0.0);
		}


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

		kigaPramaryTestsPCR.put(LocalDate.parse("2021-05-10"), 0.4);
		
		if (params.testing.equals("no")) {
			leisureTestsPCR.put(restrictionDate, 0.0);
			workTestsPCR.put(restrictionDate, 0.0);
			kigaPramaryTestsPCR.put(restrictionDate, 0.0);
			eduTestsPCR.put(restrictionDate, 0.0);
		}


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

		//tracing
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		boolean qv = false;
//		if (params.qV.equals("yes")) {
//			qv = true;
//		}
		tracingConfig.setQuarantineVaccinated((Map.of(
				episimConfig.getStartDate(), false
//				restrictionDate, qv
		)));

		tracingConfig.setQuarantineDuration(Map.of(
				episimConfig.getStartDate(), 14,
				LocalDate.parse("2022-01-01"), 10
			));

		int greenPassValid = 90;
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



	private void configureBooster(VaccinationConfigGroup vaccinationConfig, double boosterSpeed, int boostAfter_months) {

		Map<LocalDate, Integer> boosterVaccinations = new HashMap<>();

		boosterVaccinations.put(LocalDate.parse("2020-01-01"), 0);

		boosterVaccinations.put(LocalDate.parse("2022-02-15"), (int) (2_352_480 * 0.04 * boosterSpeed / 7));
		// (I think that this means 4pct of population per week.)

		boosterVaccinations.put(LocalDate.parse("2022-06-30"), 0);

		vaccinationConfig.setReVaccinationCapacity_pers_per_day(boosterVaccinations);

		vaccinationConfig.getOrAddParams(VaccinationType.mRNA).setBoostWaitPeriod(boostAfter_months * 30 + 6 * 7);
		vaccinationConfig.getOrAddParams(VaccinationType.omicronUpdate).setBoostWaitPeriod(boostAfter_months * 30 + 6 * 7);
		vaccinationConfig.getOrAddParams(VaccinationType.vector).setBoostWaitPeriod(boostAfter_months * 30 + 9 * 7);
		;
	}

	public static final class Params {

		@GenerateSeeds(5)
		public long seed;

		@StringParameter({"current"})
		String testing;

		@Parameter({0.0, 0.5, 1.0, 1.5, 2.0})
		double mutInf;
		
		@Parameter({0.2, 0.4, 0.7, 1.0})
		double mutEsc;
		
		@StringParameter({"2022-07-01", "2022-10-01"})
		String mutDate;
		
		@StringParameter({"false", "true"})
		String ba1ba2x;

		@StringParameter({"no", "over18Plus", "over18", "over50Plus", "over50"})
		String vac;
		
		@StringParameter({"2022-05-01"})
		String vacStart;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneBMBF220217.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import it.unimi.dsi.fastutil.ints.Int2DoubleAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.analysis.OutputAnalysis;
import org.matsim.episim.analysis.RValuesFromEvents;
import org.matsim.episim.analysis.VaccinationEffectiveness;
import org.matsim.episim.analysis.VaccinationEffectivenessFromPotentialInfections;
import org.matsim.episim.model.*;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.model.vaccination.VaccinationStrategyBMBF0617;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiFunction;


/**
 * Batch for Bmbf runs
 */
public class CologneJR implements BatchRun<CologneJR.Params> {

	boolean DEBUG_MODE = false;
	int runCount = 0;


	@Nullable
	@Override
	public Module getBindings(int id, @Nullable Params params) {
		return Modules.override(getBindings(0.0, params)).with(new AbstractModule() {
			@Override
			protected void configure() {

				Multibinder<VaccinationModel> set = Multibinder.newSetBinder(binder(), VaccinationModel.class);

				set.addBinding().to(VaccinationStrategyBMBF0617.class).in(Singleton.class);


				double mutEscBa5 = 1.;
				double mutEscStrainA = 1.;

				LocalDate start = null;
				VaccinationType vaccinationType = null;

				Int2DoubleMap compliance = new Int2DoubleAVLTreeMap();
				compliance.put(60, 0.0);
				compliance.put(18, 0.0);
				compliance.put(12, 0.0);
				compliance.put(0, 0.0);



				if (params != null) {
					mutEscBa5 = params.ba5Esc;
					mutEscStrainA = params.strAEsc;

					start = LocalDate.parse(params.vacDate);
					vaccinationType = VaccinationType.valueOf(params.vacType);


					if (params.vacCamp.equals("age")) {
						compliance.put(60, 0.85); // 60+
						compliance.put(18, 0.55); // 18-59
						compliance.put(12, 0.20); // 12-17
						compliance.put(0, 0.0); // 0 - 11
					}
					else if (params.vacCamp.equals("eu")) {
						compliance.put(60, 0.40); // half of 80% (which reflects the current percentage of people in Dland who are boostered)
						compliance.put(18, 0.);
						compliance.put(12, 0.);
						compliance.put(0, 0.);
					}
					else if (params.vacCamp.equals("off")) {

					} else {
						throw new RuntimeException("Not a valid option for vaccinationCampaignType");
					}
				}

				bind(VaccinationStrategyBMBF0617.Config.class).toInstance(new VaccinationStrategyBMBF0617.Config(start, 30, vaccinationType, compliance));

				//initial antibodies
				Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();
				Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();
				configureAntibodies(initialAntibodies, antibodyRefreshFactors, mutEscBa5,mutEscStrainA);

				AntibodyModel.Config antibodyConfig = new AntibodyModel.Config(initialAntibodies, antibodyRefreshFactors);

				if (params != null) {
					antibodyConfig.setImmuneReponseSigma(params.immuneSigma);
				}

				bind(AntibodyModel.Config.class).toInstance(antibodyConfig);

			}

			private void configureAntibodies(Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies,
											 Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors,
											 double mutEscBa5, double mutEscStrainA) {
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
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.SARS_CoV_2, 0.01);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.SARS_CoV_2, 0.01);
				initialAntibodies.get(VaccinationType.omicronUpdate).put(VirusStrain.SARS_CoV_2, mRNAAlpha);

				//Alpha
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.ALPHA, mRNAAlpha);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.ALPHA, 0.01);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.ALPHA, 0.01);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.ALPHA, 0.01);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.ALPHA, 0.01);
				initialAntibodies.get(VaccinationType.omicronUpdate).put(VirusStrain.ALPHA, mRNAAlpha);

				//DELTA
				double mRNADelta = 10.9;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.DELTA, mRNADelta);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.DELTA, mRNADelta * 150./300.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.DELTA, mRNADelta * 64./300.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.DELTA, mRNADelta * 64./300.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.DELTA, mRNADelta * 450./300.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.DELTA, 0.2 / 6.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.DELTA, 0.2 / 6.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.DELTA, 0.2 / 6.4);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.DELTA, 0.2 / 6.4);
				initialAntibodies.get(VaccinationType.omicronUpdate).put(VirusStrain.DELTA, mRNADelta);

				//BA.1
				double mRNABA1 = 1.9;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA1, mRNABA1);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA1, mRNABA1 * 4./20.); //???
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6./20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6./20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 8./20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA1, 64.0 / 300.);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4); //todo: is 1.4
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4);
				initialAntibodies.get(VaccinationType.omicronUpdate).put(VirusStrain.OMICRON_BA1, mRNAAlpha);

				//BA.2
				double mRNABA2 = mRNABA1;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA2, mRNABA2);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA2, mRNABA2 * 4./20.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6./20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6./20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 8./20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / 1.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA2, 64.0 / 300.);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / 1.4);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.OMICRON_BA2, 64.0 / 300.);
				initialAntibodies.get(VaccinationType.omicronUpdate).put(VirusStrain.OMICRON_BA2, mRNAAlpha);


				//BA.5
				double mRNABa5 = mRNABA2 / mutEscBa5;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA5, mRNABa5);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA5, mRNABa5 * 4./20.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6./20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6./20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA5,  mRNABa5 * 8./20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA5,  64.0 / 300. / 1.4 / mutEscBa5);// todo: do we need 1.4?
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA5, 64.0 / 300./ mutEscBa5);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA5, 64.0 / 300.);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.OMICRON_BA5, 64.0 / 300./ mutEscBa5); //todo ???
				initialAntibodies.get(VaccinationType.omicronUpdate).put(VirusStrain.OMICRON_BA5, mRNAAlpha / mutEscBa5);

				//StrainA
				double mRNAStrainA = mRNABa5 / mutEscStrainA;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.STRAIN_A, mRNAStrainA);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.STRAIN_A, mRNAStrainA * 4./20.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.STRAIN_A, mRNAStrainA * 6./20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.STRAIN_A, mRNAStrainA * 6./20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.STRAIN_A,  mRNAStrainA * 8./20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.STRAIN_A,  64.0 / 300. / mutEscBa5 /mutEscStrainA);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.STRAIN_A, 64.0 / 300./ mutEscBa5 /mutEscStrainA);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.STRAIN_A, 64.0 / 300. / mutEscBa5 / mutEscStrainA);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.STRAIN_A, 64.0 / 300.);
				initialAntibodies.get(VaccinationType.omicronUpdate).put(VirusStrain.STRAIN_A, mRNAAlpha / mutEscBa5 /mutEscStrainA);

				for (VaccinationType immunityType : VaccinationType.values()) {
					antibodyRefreshFactors.put(immunityType, new EnumMap<>( VirusStrain.class ) );
					for (VirusStrain virusStrain : VirusStrain.values()) {

						if (immunityType == VaccinationType.mRNA) {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
						}
						else if (immunityType == VaccinationType.vector) {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, 5.0);
						}
						else if (immunityType == VaccinationType.omicronUpdate) {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
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



				System.out.print("immunityGiver");
				for (VirusStrain immunityFrom : VirusStrain.values()) {
					if (immunityFrom == VirusStrain.OMICRON_BA1) {
						System.out.print( "," + "BA.1");
					} else 		if (immunityFrom == VirusStrain.OMICRON_BA2) {
						System.out.print( "," + "BA.2");
					} else {
						System.out.print( "," + immunityFrom);
					}
				}


				for (ImmunityEvent immunityGiver : VaccinationType.values()) {
					System.out.print("\n" + immunityGiver);
					for (VirusStrain immunityFrom : VirusStrain.values()) {
						System.out.print("," +  String.format("%.3g", initialAntibodies.get(immunityGiver).get(immunityFrom)));
					}
				}
				for (ImmunityEvent immunityGiver : VirusStrain.values()) {
					System.out.print("\n" + immunityGiver);
					for (VirusStrain immunityFrom : VirusStrain.values()) {
						System.out.print("," + String.format("%.3g", initialAntibodies.get(immunityGiver).get(immunityFrom)));
					}
				}

				System.out.println();

			}
		});

	}

	private SnzCologneProductionScenario getBindings(double pHousehold, Params params) {
		return new SnzCologneProductionScenario.Builder()
				.setCarnivalModel(SnzCologneProductionScenario.CarnivalModel.yes)
				.setSebastianUpdate(params == null ? false : Boolean.parseBoolean(params.sebaUp))
				.setScaleForActivityLevels(1.3)
				.setSuscHouseholds_pct(pHousehold)
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

		if (DEBUG_MODE) {
			if (runCount == 0 && params.sebaUp.equals("false") && Objects.equals(params.vacCamp, "age")) {
				runCount++;
			} else {
				return null;
			}
		}



//		LocalDate restrictionDate = LocalDate.parse("2022-03-01");

		SnzCologneProductionScenario module = getBindings(0.0, params);

		Config config = module.config();


		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		if (DEBUG_MODE) {
			//local (see svn for more snapshots with different dates)
//			episimConfig.setStartFromSnapshot("../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input/snapshots-cologne-20220316/" + params.seed + "-450-2021-05-19.zip");
		}
		else {
//			episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-cologne-20220316/" + params.seed + "-450-2021-05-19.zip");
//			episimConfig.setSnapshotSeed(SnapshotSeed.restore);

		}

		//restrictions

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		// Restrictions starting on December 1, 2022
		LocalDate restrictionDate = LocalDate.of(2022, 12, 1);
		//school
		BiFunction<LocalDate, Double, Double> schoolFactor = (d, rf) -> Math.max(rf * params.school, 0.2);
		builder.applyToRf(restrictionDate.toString(), restrictionDate.plusDays(1000).toString(), schoolFactor, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//university
		BiFunction<LocalDate, Double, Double> uniFactor = (d, rf) -> Math.max(rf * params.uni, 0.2);
		builder.applyToRf(restrictionDate.toString(), restrictionDate.plusDays(1000).toString(), uniFactor, "educ_higher");
		//shopping: masks
		if (Boolean.parseBoolean(params.maskShop)) {
			builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.9 ), "shop_daily", "shop_other", "errands");
		}
		//public transit: masks
		if (Boolean.parseBoolean(params.maskPt)) {
			builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.9), "pt");
		}
		//work
		BiFunction<LocalDate, Double, Double> workFactor = (d, rf) -> rf * params.work;
		builder.applyToRf(restrictionDate.toString(), restrictionDate.plusDays(1000).toString(), workFactor, "work");

		//leisure
		BiFunction<LocalDate, Double, Double> leisFactor = (d, rf) -> rf * params.leis;
		builder.applyToRf(restrictionDate.toString(), restrictionDate.plusDays(1000).toString(), leisFactor, "leisure");

		episimConfig.setPolicy(builder.build());


		//mutations
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		//BA5
		if (params.ba5Inf > 0) {
			double oHos = virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getFactorSeriouslySick();

			Map<LocalDate, Integer> infPerDayBa5 = new HashMap<>();
			infPerDayBa5.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayBa5.put(LocalDate.parse(params.ba5Date), 4);
			infPerDayBa5.put(LocalDate.parse(params.ba5Date).plusDays(7), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA5, infPerDayBa5);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getInfectiousness() * params.ba5Inf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySick(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySickVaccinated(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorCritical(oHos);
		}


//		STRAIN_A
		if (params.strAInf > 0) {
			double oHos = virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getFactorSeriouslySick();

			Map<LocalDate, Integer> infPerDayStrainA = new HashMap<>();
			infPerDayStrainA.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayStrainA.put(LocalDate.parse(params.strADate), 4);
			infPerDayStrainA.put(LocalDate.parse(params.strADate).plusDays(7), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.STRAIN_A, infPerDayStrainA);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getInfectiousness() * params.ba5Inf * params.strAInf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySickVaccinated(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorCritical(oHos);
		}


		//vaccinations
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setUseIgA(true);
		vaccinationConfig.setTimePeriodIgA(params.igATime);

		return config;
	}


	public static final class Params {

		// general
		@GenerateSeeds(1)
		public long seed;

		@StringParameter({"true"})
		public String sebaUp;


		// Antibody Model
		@Parameter({3.0})
		double immuneSigma;

		@Parameter({730.}) //120,
		public double igATime;


		// BA5
		@StringParameter({"2022-04-10"})
		public String ba5Date;

		@Parameter({0.9})
		double ba5Inf;

		@Parameter({3.})
		public double ba5Esc;

		// StrainA
		@StringParameter({"2022-11-01"})
		public String strADate;

		@Parameter({1., 6.})
		public double strAEsc;

		@Parameter({0.0, 1.0})
		public double strAInf;


		// vaccination campaign
		@StringParameter({"2022-12-01"})
		public String vacDate;

		@StringParameter({"omicronUpdate"})
		public String vacType;

		@StringParameter({"off", "age", "eu"})
		String vacCamp;

		// other restrictions
		// schools
		@Parameter({0.0, 0.5, 1.0})
		double school;

		// university
		@Parameter({0.0, 1.0})
		double uni;

		// shopping: mask
		@StringParameter({"true", "false"})
		String maskShop;

		// pt: mask
		@StringParameter({"true", "false"})
		String maskPt;

		// work:
		@Parameter({ 0.5, 1.0})
		double work;

		// leisure
		@Parameter({0.5, 0.75, 1.0})
		double leis;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneJR.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(70),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


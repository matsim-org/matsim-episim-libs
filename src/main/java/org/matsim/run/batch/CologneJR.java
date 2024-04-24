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
import org.matsim.episim.analysis.*;
import org.matsim.episim.model.*;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.model.vaccination.VaccinationStrategyBMBF0617;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.*;


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

//				Multibinder<VaccinationModel> set = Multibinder.newSetBinder(binder(), VaccinationModel.class);
//
//				set.addBinding().to(VaccinationStrategyBMBF0617.class).in(Singleton.class);


				double mutEscBa5 = 3.0;
				double mutEscStrainA = 0.;

//				LocalDate start = null;
//				VaccinationType vaccinationType = null;

//				Int2DoubleMap compliance = new Int2DoubleAVLTreeMap();
//				compliance.put(60, 0.0);
//				compliance.put(18, 0.0);
//				compliance.put(12, 0.0);
//				compliance.put(0, 0.0);



				if (params != null) {
					mutEscBa5 = params.ba5Esc;

					mutEscStrainA = params.strAEsc;
//
//					start = LocalDate.parse(params.resDate);
//					vaccinationType = VaccinationType.valueOf(params.vacType);
//
//
//					if (params.vacCamp.equals("age")) {
//						compliance.put(60, 0.85); // 60+
//						compliance.put(18, 0.55); // 18-59
//						compliance.put(12, 0.20); // 12-17
//						compliance.put(0, 0.0); // 0 - 11
//					}
//					else if (params.vacCamp.equals("eu")) {
//						compliance.put(60, 0.40); // half of 80% (which reflects the current percentage of people in Dland who are boostered)
//						compliance.put(18, 0.);
//						compliance.put(12, 0.);
//						compliance.put(0, 0.);
//					}
//					else if (params.vacCamp.equals("off")) {
//
//					} else {
//						throw new RuntimeException("Not a valid option for vaccinationCampaignType");
//					}
				}
//
//				bind(VaccinationStrategyBMBF0617.Config.class).toInstance(new VaccinationStrategyBMBF0617.Config(start, 30, vaccinationType, compliance));

				//initial antibodies
				Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();
				Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();
				configureAntibodies(initialAntibodies, antibodyRefreshFactors, mutEscBa5,mutEscStrainA);

				AntibodyModel.Config antibodyConfig = new AntibodyModel.Config(initialAntibodies, antibodyRefreshFactors);

				double immuneSigma = 3.0;
				if (params != null) {
					antibodyConfig.setImmuneReponseSigma(immuneSigma);
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
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.SARS_CoV_2, mRNAAlpha);

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
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.ALPHA, mRNAAlpha);

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
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.DELTA, mRNADelta);

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
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA1, mRNAAlpha);

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
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA2, mRNAAlpha);


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
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA5, mRNAAlpha / mutEscBa5);

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
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.STRAIN_A, mRNAAlpha / mutEscBa5 /mutEscStrainA);

				for (VaccinationType immunityType : VaccinationType.values()) {
					antibodyRefreshFactors.put(immunityType, new EnumMap<>( VirusStrain.class ) );
					for (VirusStrain virusStrain : VirusStrain.values()) {

						if (immunityType == VaccinationType.mRNA) {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
						}
						else if (immunityType == VaccinationType.vector) {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, 5.0);
						}
						else if (immunityType == VaccinationType.ba1Update) {
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



//				System.out.print("immunityGiver");
//				for (VirusStrain immunityFrom : VirusStrain.values()) {
//					if (immunityFrom == VirusStrain.OMICRON_BA1) {
//						System.out.print( "," + "BA.1");
//					} else 		if (immunityFrom == VirusStrain.OMICRON_BA2) {
//						System.out.print( "," + "BA.2");
//					} else {
//						System.out.print( "," + immunityFrom);
//					}
//				}
//
//
//				for (ImmunityEvent immunityGiver : VaccinationType.values()) {
//					System.out.print("\n" + immunityGiver);
//					for (VirusStrain immunityFrom : VirusStrain.values()) {
//						System.out.print("," +  String.format("%.3g", initialAntibodies.get(immunityGiver).get(immunityFrom)));
//					}
//				}
//				for (ImmunityEvent immunityGiver : VirusStrain.values()) {
//					System.out.print("\n" + immunityGiver);
//					for (VirusStrain immunityFrom : VirusStrain.values()) {
//						System.out.print("," + String.format("%.3g", initialAntibodies.get(immunityGiver).get(immunityFrom)));
//					}
//				}
//
//				System.out.println();

			}
		});

	}

	private SnzCologneProductionScenario getBindings(double pHousehold, Params params) {
		return new SnzCologneProductionScenario.Builder()
				.setCarnivalModel(SnzCologneProductionScenario.CarnivalModel.yes)
				.setSebastianUpdate(true)
				.setLeisureCorrection(1.3)//params == null ? 0.0 : params.actCorrection)
				.setScaleForActivityLevels(1.3)
				.setSuscHouseholds_pct(pHousehold)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
//				.setTestingModel(params != null ? FlexibleTestingModel.class : DefaultTestingModel.class)
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
				new VaccinationEffectivenessFromPotentialInfections().withArgs("--remove-infected"),
				new HospitalNumbersFromEvents().withArgs(),
				new SecondaryAttackRateFromEvents().withArgs()
		);
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		if (DEBUG_MODE) {
			if (runCount == 0){ //&& params.strAEsc != 0.0 && params.ba5Inf == 0. && params.eduTest.equals("true")) {
				runCount++;
			} else {
				return null;
			}
		}

		SnzCologneProductionScenario module = getBindings(0.0, params);

		Config config = module.config();


		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);


		double thFactor = 1.2;
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * thFactor);



		//---------------------------------------
		//		S T R A I N S
		//---------------------------------------

		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);


//		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setInfectiousness(virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).getInfectiousness() * params.deltaTheta);

		//BA5
		double ba5Inf = 1.0;
		double oHos = virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getFactorSeriouslySick();
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getInfectiousness() * ba5Inf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySick(oHos);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySickVaccinated(oHos);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorCritical(oHos);


//		STRAIN_A
		double strAInf = 1.0;
		if (params.strAEsc != 0.) {

			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getInfectiousness() * ba5Inf * strAInf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySickVaccinated(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorCritical(oHos);
		}

		// remove age-based susceptibility of strains starting with DELTA

		if (!Boolean.parseBoolean("false")) {
			TreeMap<Integer, Double> nonSteppedAgeSusceptibility = new TreeMap<>(Map.of(
					19, 1d,
					20, 1d
			));

			for (VirusStrain strain : List.of(VirusStrain.DELTA, VirusStrain.OMICRON_BA1, VirusStrain.OMICRON_BA2, VirusStrain.OMICRON_BA5, VirusStrain.STRAIN_A)) {
				virusStrainConfigGroup.getOrAddParams(strain).setAgeSusceptibility(nonSteppedAgeSusceptibility);
			}
		}

		// increase infectivity of alpha
		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).getInfectiousness() * params.alphaTheta);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setInfectiousness(virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).getInfectiousness() * params.deltaTheta);
		double ba1Inf = params.ba1Inf; // 2.0,2.1,2.2
		double ba2Inf = 1.7;
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setInfectiousness(virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).getInfectiousness() * ba1Inf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setInfectiousness(virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).getInfectiousness() * ba1Inf * ba2Inf);






		//---------------------------------------
		//		I M P O R T
		//---------------------------------------

		Map<LocalDate, Integer> infPerDayAlpha = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.ALPHA, new TreeMap<>()));


		// reconfig disease import of alpha
		LocalDate startDateAlpha = LocalDate.parse(params.alphaDate);

		for (int i = 0; i < 7; i++) {
			infPerDayAlpha.put(startDateAlpha.plusDays(i), 4);
		}


		infPerDayAlpha.put(startDateAlpha.plusDays(7), 1);

		episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayAlpha);


		configureFutureDiseaseImport(params, episimConfig);

		//---------------------------------------
		//		R E S T R I C T I O N S
		//---------------------------------------

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		//school
		String schoolUpdate = "yes";
		if(schoolUpdate.equals("yes")) {
			// school closed completely until 21.2.2022
			builder.restrict(LocalDate.parse("2021-01-11"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
			builder.restrict(LocalDate.parse("2021-02-21"), 0.5, "educ_primary");
			builder.restrict(LocalDate.parse("2021-03-15"), 0.5, "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");

		} else if (schoolUpdate.equals("no")) {

		} else {
			throw new RuntimeException("param value doesn't exist");
		}

		String schoolTest = "later";
		if (schoolTest.equals("later")) {
			TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);
			TestingConfigGroup.TestingParams rapidTest = testingConfigGroup.getOrAddParams(TestType.RAPID_TEST);
//			TestingConfigGroup.TestingParams pcrTest = testingConfigGroup.getOrAddParams(TestType.PCR);
			Map<String, NavigableMap<LocalDate, Double>> testingRateForActivitiesRapid = rapidTest.getTestingRateForActivities();
//			Map<String, NavigableMap<LocalDate, Double>> testingRateForActivitiesPCR = pcrTest.getTestingRateForActivities();


			for (LocalDate date = LocalDate.parse("2021-03-19"); date.isBefore(LocalDate.parse("2021-04-25")); date = date.plusDays(1)) {

				testingRateForActivitiesRapid.get("educ_kiga").put(date, 0.);
				testingRateForActivitiesRapid.get("educ_primary").put(date, 0.);
				testingRateForActivitiesRapid.get("educ_secondary").put(date, 0.);
				testingRateForActivitiesRapid.get("educ_tertiary").put(date, 0.);
				testingRateForActivitiesRapid.get("educ_other").put(date, 0.);

			}

			testingRateForActivitiesRapid.get("educ_kiga").put(LocalDate.parse("2021-09-20"), 0.);
			testingRateForActivitiesRapid.get("educ_primary").put(LocalDate.parse("2021-09-20"), 0.);

//			testingRateForActivitiesPCR.get("educ_primary").put(LocalDate.parse("2021-05-10"), 0.4);
			testingRateForActivitiesRapid.get("educ_secondary").put(LocalDate.parse("2021-05-10"), 0.4);
			testingRateForActivitiesRapid.get("educ_tertiary").put(LocalDate.parse("2021-05-10"), 0.4);
			testingRateForActivitiesRapid.get("educ_other").put(LocalDate.parse("2021-05-10"), 0.4);

		} else if (schoolTest.equals("base")) {

		}else {
			throw new RuntimeException("param value doesn't exist");
		}


		// masks
		//pt: masks
		String maskType = "45to45";
		if (maskType.equals("45to45")) {
			for (LocalDate date = LocalDate.parse("2020-04-21"); date.isBefore(LocalDate.parse("2021-05-01")); date = date.plusDays(1)) {
				builder.restrict(date, Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.45, FaceMask.SURGICAL, 0.45)), "pt", "errands", "shop_daily", "shop_other");

			}
		} else if (maskType.equals("base")) {

		} else {
			throw new RuntimeException("param value doesn't exist");
		}

		episimConfig.setPolicy(builder.build());


		//---------------------------------------
		//		M I S C
		//---------------------------------------

		// vaccination
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setUseIgA(true);
		vaccinationConfig.setTimePeriodIgA(730.);


		//modify contact intensity
		double workCi = 0.75;
		episimConfig.getOrAddContainerParams("work").setContactIntensity(episimConfig.getOrAddContainerParams("work").getContactIntensity() * workCi);
		episimConfig.getOrAddContainerParams("business").setContactIntensity(episimConfig.getOrAddContainerParams("business").getContactIntensity() * workCi);

		double leisureCi = 0.4;
		episimConfig.getOrAddContainerParams("leisure").setContactIntensity(episimConfig.getOrAddContainerParams("leisure").getContactIntensity() * leisureCi);
		episimConfig.getOrAddContainerParams("visit").setContactIntensity(episimConfig.getOrAddContainerParams("visit").getContactIntensity() * leisureCi);


		double schoolCi = 0.75;
		episimConfig.getOrAddContainerParams("educ_kiga").setContactIntensity(episimConfig.getOrAddContainerParams("educ_kiga").getContactIntensity() * schoolCi);
		episimConfig.getOrAddContainerParams("educ_primary").setContactIntensity(episimConfig.getOrAddContainerParams("educ_primary").getContactIntensity() * schoolCi);
		episimConfig.getOrAddContainerParams("educ_secondary").setContactIntensity(episimConfig.getOrAddContainerParams("educ_secondary").getContactIntensity() * schoolCi);
		episimConfig.getOrAddContainerParams("educ_tertiary").setContactIntensity(episimConfig.getOrAddContainerParams("educ_tertiary").getContactIntensity() * schoolCi);
		episimConfig.getOrAddContainerParams("educ_higher").setContactIntensity(episimConfig.getOrAddContainerParams("educ_higher").getContactIntensity() * schoolCi);
		episimConfig.getOrAddContainerParams("educ_other").setContactIntensity(episimConfig.getOrAddContainerParams("educ_other").getContactIntensity() * schoolCi);


		if (DEBUG_MODE) {
			UtilsJR.produceDiseaseImportPlot(episimConfig.getInfections_pers_per_day());

		}

		return config;
	}

	private void configureFutureDiseaseImport(Params params, EpisimConfigGroup episimConfig) {
		Map<LocalDate, Integer> infPerDayBa1 = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.OMICRON_BA1, new TreeMap<>()));
		Map<LocalDate, Integer> infPerDayBa2 = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.OMICRON_BA2, new TreeMap<>()));
		Map<LocalDate, Integer> infPerDayBa5 = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.OMICRON_BA5, new TreeMap<>()));
		Map<LocalDate, Integer> infPerDayStrA = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.STRAIN_A, new TreeMap<>()));

		// add initial impulses for strains
		//BA.1
		LocalDate ba1Date = LocalDate.parse(params.ba1Date);
		for (int i = 0; i < 7; i++) {
			infPerDayBa1.put(ba1Date.plusDays(i), 4);
		}
		infPerDayBa1.put(ba1Date.plusDays(7), 1);


		//BA.2
		LocalDate ba2Date = LocalDate.parse("2021-12-18");
		for (int i = 0; i < 7; i++) {
			infPerDayBa2.put(ba2Date.plusDays(i), 4);
		}
		infPerDayBa2.put(ba2Date.plusDays(7), 1);

		//BA.5
		LocalDate ba5Date = LocalDate.parse("2022-04-10");
		for (int i = 0; i < 7; i++) {
			infPerDayBa5.put(ba5Date.plusDays(i), 4);
		}
		infPerDayBa5.put(ba5Date.plusDays(7), 1);

		//StrainA

		if (params.strAEsc != 0.) {
			infPerDayStrA.put(LocalDate.parse("2020-01-01"), 0);
			LocalDate strADate = LocalDate.parse(params.strADate);
			for (int i = 0; i < 7; i++) {
				infPerDayStrA.put(strADate.plusDays(i), 4);
			}
			infPerDayStrA.put(strADate.plusDays(7), 1);
		}


		// add projected disease import for vacation waves after initial disease import
		int facBa2 = 4;
		int facBa5 = 4;
		int facStrA = 4;

		LocalDate dateBa2 = LocalDate.parse("2022-01-27"); // local min of disease import
		LocalDate dateBa5 = LocalDate.parse("2022-05-01"); // after vaca import
		LocalDate dateStrainA = LocalDate.parse("2022-11-18"); // after vaca import

		NavigableMap<LocalDate, Double> data = DataUtils.readDiseaseImport(SnzCologneProductionScenario.INPUT.resolve("cologneDiseaseImport_Projected.csv"));
		LocalDate date = null;
		for (Map.Entry<LocalDate, Double> entry : data.entrySet()) {
			date = entry.getKey();
			double factor = 0.25 * 2352476. / 919936.; //25% sample, data is given for Cologne City so we have to scale it to the whole model
//
			double cases = factor * entry.getValue();

			if (date.isAfter(dateStrainA) && params.strAEsc != 0) {
				infPerDayStrA.put(date, ((int) cases * facStrA) == 0 ? 1 : (int) (cases * facStrA));
				infPerDayBa5.put(date, 1);
				infPerDayBa2.put(date, 1);
			} else if (date.isAfter(dateBa5)) {
				infPerDayBa5.put(date, ((int) cases * facBa5) == 0 ? 1 : (int) (cases * facBa5));
				infPerDayBa2.put(date, 1);
			} else if (date.isAfter(dateBa2)) {
				infPerDayBa2.put(date, ((int) cases * facBa2) == 0 ? 1 : (int) (cases * facBa2));
			}

		}

		if( params.strAEsc!=0.) {
			infPerDayBa5.put(dateStrainA.plusDays(1), 1);
			infPerDayStrA.put(date.plusDays(1), 1);
		} else {
			infPerDayBa5.put(date.plusDays(1), 1);
		}

		// save disease import
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA1, infPerDayBa1);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA2, infPerDayBa2);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA5, infPerDayBa5);

		if (params.strAEsc != 0.) {
			episimConfig.setInfections_pers_per_day(VirusStrain.STRAIN_A, infPerDayStrA);
		}
	}

	public static final class Params {


		// general
		@GenerateSeeds(5)
		public long seed;

		@Parameter({0.9})
		public double deltaTheta;

		@Parameter({1.7,1.9,2.1})
		public double ba1Inf;

		@StringParameter({"2021-11-20", "2021-11-27", "2021-12-04"})
		public String ba1Date;

		@Parameter({2.8, 2.9, 3.0})
		public double ba5Esc;

//		@Parameter({1.3})
//		@Parameter({0.0})
//		public double actCorrection;


//		@Parameter({1.2})
//		@Parameter({1.0})
//		public double thFactor;

		//		@Parameter({1.})
//		@Parameter({0.75})
//		public double schoolCi;

//		@Parameter({0.75})
//		@Parameter({1.})
//		public double workCi;

//		@Parameter({0.4})
//		@Parameter({ 1.})
//		public double leisureCi;

//		@StringParameter({"yes"})
//		public String schoolUpdate;

//		@StringParameter({"45to45"})
//		public String maskType;

//		@StringParameter({"later"})
//		public String schoolTest;

//		@StringParameter({"false"})
//		public String ageSusc;

//		@Parameter({0.90, .95, 1.})
//		public double deltaTheta;

		@StringParameter({"2021-01-15"})
		public String alphaDate;

		@Parameter({1.4})
		public double alphaTheta;

		@StringParameter({"2022-11-01"})
		public String strADate;

		@Parameter({0.0})
		public double strAEsc;


		// General Restriction date
//		@StringParameter({"2022-07-01","2022-12-01"})
//		@StringParameter({"2022-12-01"})
//		public String resDate;
//
//
//		@StringParameter({"off", "age"})
//		@StringParameter({"off"})
//		String vacCamp;
//
		// other restrictions
		// schools & university // close: rf reduced // maskVent: ciCorrection reduced & surgical mask for most // normal: no changes made
//		@StringParameter({"close", "maskVent", "normal"})
//		@StringParameter({ "normal"})
//		String edu;

		// shopping: mask
//		@StringParameter({"true", "false"})
//		@StringParameter({"false"})
//		String maskShop;

		// pt: mask
//		@StringParameter({"true", "false"})
//		@StringParameter({"false"})
//		String maskPt;

		// work:
//		@Parameter({0.5, 1.0})
//		@Parameter({1.0})
//		double work;

		// leisure
//		@Parameter({0.25, 0.5, 0.75, 1.0})
//		@Parameter({1.0})
//		double leis;


		// vaccination campaign
//		@StringParameter({"omicronUpdate"})
//		public String vacType;

//		@StringParameter({"2022-04-25"})
//		public String unResDate;

		// StrainA

//		@StringParameter({"true"})
//		public String sebaUp;

		// Antibody Model
//		@Parameter({3.0})
//		double immuneSigma;

//		@Parameter({730.}) //120,
//		public double igATime;

		// BA5
//		@StringParameter({"2022-04-10"})
//		public String ba5Date;
//
//		@Parameter({0.9}) //,1.0,1.1,1.2,1.3})
//		double ba5Inf;
//
//		@Parameter({3.})
//		public double ba5Esc;

		//		@Parameter({0.0, 1.0})
//		public double strAInf;

	}


	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneJR.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(1000),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


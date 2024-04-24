package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import it.unimi.dsi.fastutil.ints.Int2DoubleAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import jdk.jshell.execution.Util;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.analysis.*;
import org.matsim.episim.model.*;
import org.matsim.episim.model.listener.HouseholdSusceptibility;
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
public class CologneVaryHhSusceptibility implements BatchRun<CologneVaryHhSusceptibility.Params> {

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

				double mutEscDelta = 29.2 / 10.9;
				double mutEscBa1 = 10.9 / 1.9;
				double mutEscBa5 = 2.9;

				double mutEscStrainA = 0.;
				double mutEscStrainB = 0.;

				LocalDate start = null;
				VaccinationType vaccinationType = VaccinationType.mRNA;

				Int2DoubleMap compliance = new Int2DoubleAVLTreeMap();
				compliance.put(60, 0.0);
				compliance.put(18, 0.0);
				compliance.put(12, 0.0);
				compliance.put(0, 0.0);

				String vacCamp = "off";

				if (params != null) {
//					mutEscBa5 = params.ba5Esc;

					if (!params.vacType.equals("off")) {
						vacCamp = "age";
						vaccinationType = VaccinationType.valueOf(params.vacType);
					}
					if (!params.StrainA.equals("off")) {
						mutEscStrainA = Double.parseDouble(params.StrainA);
					}
					if (!params.StrainB.equals("off")) {
						mutEscStrainB = Double.parseDouble(params.StrainB);
					}

					start = LocalDate.parse(params.resDate);



					if (vacCamp.equals("age")) {
						compliance.put(60, 0.85); // 60+
						compliance.put(18, 0.55); // 18-59
						compliance.put(12, 0.20); // 12-17
						compliance.put(0, 0.0); // 0 - 11
					}
					else if (vacCamp.equals("eu")) {
						compliance.put(60, 0.40); // half of 80% (which reflects the current percentage of people in Dland who are boostered)
						compliance.put(18, 0.);
						compliance.put(12, 0.);
						compliance.put(0, 0.);
					}
					else if (vacCamp.equals("off")) {

					} else {
						throw new RuntimeException("Not a valid option for vaccinationCampaignType");
					}
				}
//
				bind(VaccinationStrategyBMBF0617.Config.class).toInstance(new VaccinationStrategyBMBF0617.Config(start, 30, vaccinationType, compliance));

				//initial antibodies
				Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();
				Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();
				configureAntibodies(initialAntibodies, antibodyRefreshFactors, mutEscDelta, mutEscBa1, mutEscBa5, mutEscStrainA, mutEscStrainB);

				AntibodyModel.Config antibodyConfig = new AntibodyModel.Config(initialAntibodies, antibodyRefreshFactors);

				double immuneSigma = 3.0;
				if (params != null) {
					antibodyConfig.setImmuneReponseSigma(immuneSigma);
				}

				bind(AntibodyModel.Config.class).toInstance(antibodyConfig);

				if (params == null) return;

//				double pHousehold = 1.0;
				// designates a 35% of households  as super safe; the susceptibility of that subpopulation is reduced to 1% wrt to general population.
				bind(HouseholdSusceptibility.Config.class).toInstance(
						HouseholdSusceptibility.newConfig()
								.withSusceptibleHouseholds(0.35, 0.01)
//								.withNonVaccinableHouseholds(params.nonVaccinableHh)
//								.withShape(SnzCologneProductionScenario.INPUT.resolve("CologneDistricts.zip"))
//								.withFeature("STT_NAME", vingst, altstadtNord, bickendorf, weiden)
				);

			}

			private void configureAntibodies(Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies,
											 Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors,
											 double mutEscDelta, double mutEscBa1, double mutEscBa5, double mutEscStrainA, double mutEscStrainB) {
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
				initialAntibodies.get(VirusStrain.STRAIN_B).put(VirusStrain.SARS_CoV_2, 0.01);
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.SARS_CoV_2, mRNAAlpha / mutEscDelta / mutEscBa1);
				initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.SARS_CoV_2, mRNAAlpha / mutEscDelta / mutEscBa1 / mutEscBa5);

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
				initialAntibodies.get(VirusStrain.STRAIN_B).put(VirusStrain.ALPHA, 0.01);
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.ALPHA, mRNAAlpha / mutEscDelta / mutEscBa1);
				initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.ALPHA, mRNAAlpha / mutEscDelta / mutEscBa1 / mutEscBa5);

				//DELTA
				double mRNADelta = mRNAAlpha / mutEscDelta;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.DELTA, mRNADelta);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.DELTA, mRNADelta * 150./300.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.DELTA, mRNADelta * 64./300.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.DELTA, mRNADelta * 64./300.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.DELTA, mRNADelta * 450./300.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.DELTA, 0.01);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.DELTA, 0.01);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.DELTA, 0.01);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.DELTA, 0.01);
				initialAntibodies.get(VirusStrain.STRAIN_B).put(VirusStrain.DELTA, 0.01);
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.DELTA, mRNADelta / mutEscBa1);
				initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.DELTA, mRNADelta / mutEscBa1 / mutEscBa5);

				//BA.1
				double mRNABA1 = mRNADelta / mutEscBa1;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA1, mRNABA1);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA1, mRNABA1 * 4./20.); //???
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6./20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6./20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 8./20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA1, 64.0 / 300.);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / mutEscBa5); //todo: is 1.4
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / mutEscBa5 / mutEscStrainA);
				initialAntibodies.get(VirusStrain.STRAIN_B).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / mutEscStrainB);
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA1, mRNAAlpha);
				initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.OMICRON_BA1, mRNAAlpha / mutEscBa5);

				//BA.2
				double mRNABA2 = mRNABA1;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA2, mRNABA2);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA2, mRNABA2 * 4./20.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6./20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6./20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 8./20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / 1.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA2, 64.0 / 300.);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / mutEscBa5);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / mutEscBa5 / mutEscStrainA);
				initialAntibodies.get(VirusStrain.STRAIN_B).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / mutEscStrainB);
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA2, mRNAAlpha);
				initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.OMICRON_BA2, mRNAAlpha / mutEscBa5);


				//BA.5
				double mRNABa5 = mRNABA2 / mutEscBa5;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA5, mRNABa5);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA5, mRNABa5 * 4./20.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6./20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6./20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA5,  mRNABa5 * 8./20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA5,  64.0 / 300. / mutEscBa5);// todo: do we need 1.4?
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA5, 64.0 / 300./ mutEscBa5);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA5, 64.0 / 300.);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.OMICRON_BA5, 64.0 / 300./ mutEscStrainA); //todo ???
				initialAntibodies.get(VirusStrain.STRAIN_B).put(VirusStrain.OMICRON_BA5,  64.0 / 300. / mutEscBa5 / mutEscStrainB);
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA5, mRNAAlpha / mutEscBa5);
				initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.OMICRON_BA5, mRNAAlpha);

				//StrainA
				double mRNAStrainA = mRNABa5 / mutEscStrainA;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.STRAIN_A, mRNAStrainA);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.STRAIN_A, mRNAStrainA * 4./20.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.STRAIN_A, mRNAStrainA * 6./20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.STRAIN_A, mRNAStrainA * 6./20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.STRAIN_A,  mRNAStrainA * 8./20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.STRAIN_A,  64.0 / 300. / mutEscBa5 /mutEscStrainA);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.STRAIN_A, 64.0 / 300./ mutEscBa5 /mutEscStrainA);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.STRAIN_A, 64.0 / 300. / mutEscStrainA);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.STRAIN_A, 64.0 / 300.);
				initialAntibodies.get(VirusStrain.STRAIN_B).put(VirusStrain.STRAIN_A, 64.0 / 300. / mutEscStrainA / mutEscStrainB / mutEscBa5);
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.STRAIN_A, mRNAAlpha / mutEscBa5 / mutEscStrainA);
				initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.STRAIN_A, mRNAAlpha / mutEscStrainA);

				//StrainB
				double mRNAStrainB = mRNABA2 / mutEscStrainB;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.STRAIN_B, mRNAStrainB);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.STRAIN_B, mRNAStrainB * 4./20.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.STRAIN_B, mRNAStrainB * 6./20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.STRAIN_B, mRNAStrainB * 6./20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.STRAIN_B,  mRNAStrainB * 8./20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.STRAIN_B,  64.0 / 300. / mutEscStrainB);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.STRAIN_B, 64.0 / 300./ mutEscStrainB);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.STRAIN_B, 64.0 / 300. / mutEscStrainB / mutEscBa5);
				initialAntibodies.get(VirusStrain.STRAIN_A).put(VirusStrain.STRAIN_B, 64.0 / 300./ mutEscStrainA / mutEscStrainB / mutEscBa5);
				initialAntibodies.get(VirusStrain.STRAIN_B).put(VirusStrain.STRAIN_B,  64.0 / 300.);
				initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.STRAIN_B, mRNAAlpha / mutEscStrainB);
				initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.STRAIN_B, mRNAAlpha / mutEscStrainB / mutEscBa5);


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
						else if (immunityType == VaccinationType.ba5Update) {
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


//				UtilsJR.printInitialAntibodiesToConsole(initialAntibodies);

			}
		});

	}

	private SnzCologneProductionScenario getBindings(double pHousehold, Params params) {
		return new SnzCologneProductionScenario.Builder()
				.setCarnivalModel(SnzCologneProductionScenario.CarnivalModel.yes)
				.setSebastianUpdate(true)
				.setLeisureCorrection(1.3) //params == null ? 0.0 : params.actCorrection)
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
				new FilterEvents().withArgs("--output","./output/"),
				new HospitalNumbersFromEvents().withArgs("--output","./output/","--input","/scratch/projects/bzz0020/episim-input")
//				new SecondaryAttackRateFromEvents().withArgs()
		);
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		if (DEBUG_MODE) {
			if (runCount == 0 && params.importSummer2022.equals("on")) { //&& params.strAEsc != 0.0 && params.ba5Inf == 0. && params.eduTest.equals("true")) {
				runCount++;
			} else {
				return null;
			}
		}

		SnzCologneProductionScenario module = getBindings(0.0, params);

		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 1.2 * 1.7);


		//snapshot
//		episimConfig.setSnapshotInterval(766);
		episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-cologne-2022-10-07/" + params.seed + "-766-2022-03-31.zip");
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.restore);


		//---------------------------------------
		//		S T R A I N S
		//---------------------------------------

		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		double ba5Inf = virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).getInfectiousness();
		double ba5Hos = virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).getFactorSeriouslySick();

//		STRAIN_A
		if (!params.StrainA.equals("off")) {

			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA5).getInfectiousness() * ba5Inf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(ba5Hos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySickVaccinated(ba5Hos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorCritical(ba5Hos);
		}

//		STRAIN_B
		if (!params.StrainB.equals("off")) {

			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_B).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA5).getInfectiousness() * ba5Inf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_B).setFactorSeriouslySick(ba5Hos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_B).setFactorSeriouslySickVaccinated(ba5Hos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_B).setFactorCritical(ba5Hos);
		}

		//---------------------------------------
		//		I M P O R T
		//---------------------------------------

		configureFutureDiseaseImport(params, episimConfig);

		//---------------------------------------
		//		R E S T R I C T I O N S
		//---------------------------------------

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		// Ci Correction after summer vacation 2022 (more air flow?)
		builder.restrict(LocalDate.parse("2022-08-09"), Restriction.ofCiCorrection(params.ciCorr), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");


		// vary amount of "school" activity that takes place during vacation
		builder.restrict(LocalDate.parse("2022-06-27"), params.eduRfVacation, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");

		episimConfig.setPolicy(builder.build());


		//---------------------------------------
		//		M I S C
		//---------------------------------------


		// modify seasonality
		episimConfig.getOrAddContainerParams("educ_kiga").setSeasonality(params.eduSeasonality);
		episimConfig.getOrAddContainerParams("educ_primary").setSeasonality(params.eduSeasonality);
		episimConfig.getOrAddContainerParams("educ_secondary").setSeasonality(params.eduSeasonality);
		episimConfig.getOrAddContainerParams("educ_tertiary").setSeasonality(params.eduSeasonality);
		episimConfig.getOrAddContainerParams("educ_higher").setSeasonality(params.eduSeasonality);
		episimConfig.getOrAddContainerParams("educ_other").setSeasonality(params.eduSeasonality);


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
		Map<LocalDate, Integer> infPerDayStrB = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.STRAIN_B, new TreeMap<>()));

		//StrainA
		if (!params.StrainA.equals("off")) {
			infPerDayStrA.put(LocalDate.parse("2020-01-01"), 0);
			LocalDate strADate = LocalDate.parse("2022-11-01");
			for (int i = 0; i < 7; i++) {
				infPerDayStrA.put(strADate.plusDays(i), 4);
			}
			infPerDayStrA.put(strADate.plusDays(7), 1);
		}

		//StrainB
		if (!params.StrainB.equals("off")) {
			infPerDayStrB.put(LocalDate.parse("2020-01-01"), 0);
			LocalDate strBDate = LocalDate.parse("2022-11-01");
			for (int i = 0; i < 7; i++) {
				infPerDayStrB.put(strBDate.plusDays(i), 4);
			}
			infPerDayStrB.put(strBDate.plusDays(7), 1);
		}


		// add projected disease import for vacation waves after initial disease import
		int facBa2 = 4;
		int facBa5 = 4;
		int facStrAB = 4;

		LocalDate dateBa2 = LocalDate.parse("2022-01-27"); // local min of disease import
		LocalDate dateBa5 = LocalDate.parse("2022-05-01"); // after vaca import
		LocalDate dateStrainAB = LocalDate.parse("2022-11-18"); // after vaca import


		if (params.importSummer2022.equals("on")) {
			NavigableMap<LocalDate, Double> data = DataUtils.readDiseaseImport(SnzCologneProductionScenario.INPUT.resolve("cologneDiseaseImport_Projected.csv"));
			LocalDate date = null;
			for (Map.Entry<LocalDate, Double> entry : data.entrySet()) {
				date = entry.getKey();
				double factor = 0.25 * 2352476. / 919936.; //25% sample, data is given for Cologne City so we have to scale it to the whole model
//
				double cases = factor * entry.getValue();

				if (date.isAfter(dateStrainAB) && (!params.StrainA.equals("off") || !params.StrainB.equals("off"))) {
					if (!params.StrainA.equals("off") && !params.StrainB.equals("off")) {
						infPerDayStrA.put(date, ((int) cases * facStrAB) == 0 ? 1 : (int) (0.5 * cases * facStrAB));
						infPerDayStrB.put(date, ((int) cases * facStrAB) == 0 ? 1 : (int) (0.5 * cases * facStrAB));
					}
					else if (!params.StrainA.equals("off")) {
						infPerDayStrA.put(date, ((int) cases * facStrAB) == 0 ? 1 : (int) (cases * facStrAB));
					}
					else if (!params.StrainB.equals("off")) {
						infPerDayStrB.put(date, ((int) cases * facStrAB) == 0 ? 1 : (int) (cases * facStrAB));
					}
					else {
						throw new RuntimeException();
					}
					infPerDayBa5.put(date, 1);
					infPerDayBa2.put(date, 1);
				} else if (date.isAfter(dateBa5)) {
					infPerDayBa5.put(date, ((int) cases * facBa5) == 0 ? 1 : (int) (cases * facBa5));
					infPerDayBa2.put(date, 1);
				} else if (date.isAfter(dateBa2)) {
					infPerDayBa2.put(date, ((int) cases * facBa2) == 0 ? 1 : (int) (cases * facBa2));
				}

			}
		} else if (params.importSummer2022.equals("off")) {
		} else {
			throw new RuntimeException();
		}


		// save disease import
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA1, infPerDayBa1);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA2, infPerDayBa2);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA5, infPerDayBa5);

		if (!params.StrainA.equals("off")) {
			episimConfig.setInfections_pers_per_day(VirusStrain.STRAIN_A, infPerDayStrA);
		}
		if (!params.StrainB.equals("off")) {
			episimConfig.setInfections_pers_per_day(VirusStrain.STRAIN_B, infPerDayStrB);
		}
	}

	public static final class Params {
		// general
		@GenerateSeeds(5)
		public long seed;

		@StringParameter({"on", "off"})
		public String importSummer2022;

		@Parameter({0.5, 0.75, 1.0})
		public double eduSeasonality;

		// ci correction of schools starting on Aug 9 (when school begins again), relates to air flow
		@Parameter({0.25, 0.5, 0.75})
		public double ciCorr;

		//how much "school" activity takes places during vacation summmer 2022
		@Parameter({0.2, 0.4, 0.6, 0.8, 1.0})
		public double eduRfVacation;



//		@StringParameter({"off", "3.0", "6.0"})
		@StringParameter({"3.0"})
		public String StrainA;

//		@StringParameter({"off", "3.0", "6.0"})
		@StringParameter({"off"})
		public String StrainB;

		@StringParameter({"2022-11-15","2022-12-01","2022-12-15"})
		public String resDate;


		// vaccination campaign
//		@StringParameter({"ba1Update", "ba5Update", "mRNA", "off"})
		@StringParameter({"off"})
		public String vacType;
	}


	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneVaryHhSusceptibility.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(1000),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


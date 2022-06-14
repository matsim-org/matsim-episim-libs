package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import it.unimi.dsi.fastutil.ints.Int2DoubleAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.analysis.OutputAnalysis;
import org.matsim.episim.analysis.RValuesFromEvents;
import org.matsim.episim.analysis.VaccinationEffectiveness;
import org.matsim.episim.analysis.VaccinationEffectivenessFromPotentialInfections;
import org.matsim.episim.model.*;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.model.vaccination.VaccinationStrategyBMBF0617;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;
import org.matsim.run.modules.SnzProductionScenario;

import javax.annotation.Nullable;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

//				System.out.println();

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
			if (runCount == 0 && params.strAEsc != 0.0 && params.ba5Inf == 0. && params.eduTest.equals("true")) {
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


//		episimConfig.setSnapshotInterval(1150);
		if (DEBUG_MODE) {
			//local (see svn for more snapshots with different dates)
//			episimConfig.setStartFromSnapshot("../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input/snapshots-cologne-20220316/" + params.seed + "-450-2021-05-19.zip");
		}
		else {
//			episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-cologne-20211110/" + params.seed + "-625-2021-11-10.zip");
//			episimConfig.setSnapshotSeed(SnapshotSeed.restore);

		}

		//restrictions

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		// Restrictions starting on December 1, 2022
		LocalDate restrictionDate = LocalDate.of(2022, 12, 1);
		//school
		builder.restrict(restrictionDate, params.edu, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.applyToRf(restrictionDate.plusDays(1).toString(), restrictionDate.plusDays(300).toString(), (d, rf) -> params.edu, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//university
		builder.restrict(restrictionDate, params.edu, "educ_higher");
		builder.applyToRf(restrictionDate.plusDays(1).toString(), restrictionDate.plusDays(300).toString(), (d, rf) -> params.edu, "educ_higher");

//		BiFunction<LocalDate, Double, Double> uniFactor = (d, rf) -> Math.max(rf * params.uni, 0.2);
		//shopping & pt: masks
		if (Boolean.parseBoolean(params.maskShopAndPt)) {
			builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.9 ), "shop_daily", "shop_other", "errands", "pt");
		}
		//work
		builder.restrict(restrictionDate, 0.78 * params.work, "work");
		builder.applyToRf(restrictionDate.plusDays(1).toString(), restrictionDate.plusDays(1000).toString(), (d, rf) -> rf * params.work, "work");

		//leisure
		builder.restrict(restrictionDate, 0.88 * params.leis, "leisure");
		builder.applyToRf(restrictionDate.plusDays(1).toString(), restrictionDate.plusDays(1000).toString(), (d, rf) -> rf * params.leis, "leisure");

		episimConfig.setPolicy(builder.build());


		//mutations
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		//clear disease import
		episimConfig.getInfections_pers_per_day().clear();

		//BA.2
		String ba2Date = "2021-12-18";
		Map<LocalDate, Integer> infPerDayBA2 = new HashMap<>();
		infPerDayBA2.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayBA2.put(LocalDate.parse(ba2Date), 4);
		infPerDayBA2.put(LocalDate.parse(ba2Date).plusDays(7), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA2, infPerDayBA2);

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

		double strAInf = 1.0;
		if (params.strAEsc != 0.) {

			double oHos = virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getFactorSeriouslySick();

			Map<LocalDate, Integer> infPerDayStrainA = new HashMap<>();
			infPerDayStrainA.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayStrainA.put(LocalDate.parse(params.strADate), 4);
			infPerDayStrainA.put(LocalDate.parse(params.strADate).plusDays(7), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.STRAIN_A, infPerDayStrainA);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getInfectiousness() * (params.ba5Inf == 0. ? 1 : params.ba5Inf) * strAInf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySickVaccinated(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorCritical(oHos);
		}


		//vaccinations
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setUseIgA(true);
		vaccinationConfig.setTimePeriodIgA(params.igATime);

		diseaseImp(episimConfig, Boolean.parseBoolean(params.sebaUp),params.ba5Inf!=0., params.strAEsc != 0.);

		// testing

		if (Boolean.parseBoolean(params.eduTest)) {

			TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);

			//add pcr tests for kindergarden and primary school
			TestingConfigGroup.TestingParams pcrTest = testingConfigGroup.getOrAddParams(TestType.PCR);
			Map<String, NavigableMap<LocalDate, Double>> testingRateForActivitiesPcr = pcrTest.getTestingRateForActivities();
			testingRateForActivitiesPcr.get("educ_kiga").put(restrictionDate, 0.4);
			testingRateForActivitiesPcr.get("educ_primary").put(restrictionDate, 0.4);

			//add rapid tests for older kids
			TestingConfigGroup.TestingParams rapidTest = testingConfigGroup.getOrAddParams(TestType.RAPID_TEST);
			Map<String, NavigableMap<LocalDate, Double>> testingRateForActivitiesRapid = rapidTest.getTestingRateForActivities();
			testingRateForActivitiesRapid.get("educ_secondary").put(restrictionDate, 0.6);
			testingRateForActivitiesRapid.get("educ_tertiary").put(restrictionDate, 0.6);
			testingRateForActivitiesRapid.get("educ_other").put(restrictionDate, 0.6);
		}

		if(DEBUG_MODE)
			UtilsJR.produceDiseaseImportPlot(episimConfig.getInfections_pers_per_day());



		return config;
	}


	public void diseaseImp(EpisimConfigGroup episimConfig, boolean sebastianUpdate,boolean ba5,boolean strainA){
		Map<LocalDate, Integer> importMap = new HashMap<>();
		double importFactorBeforeJune = 4.0;
		double imprtFctMult = 1.0;
		long importOffset = 0;
		double cologneFactor = 0.5;

		SnzProductionScenario.interpolateImport(importMap, cologneFactor * imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-02-24").plusDays(importOffset),
				LocalDate.parse("2020-03-09").plusDays(importOffset), 0.9, 23.1);
		SnzProductionScenario.interpolateImport(importMap, cologneFactor * imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-03-09").plusDays(importOffset),
				LocalDate.parse("2020-03-23").plusDays(importOffset), 23.1, 3.9);
		SnzProductionScenario.interpolateImport(importMap, cologneFactor * imprtFctMult * importFactorBeforeJune, LocalDate.parse("2020-03-23").plusDays(importOffset),
				LocalDate.parse("2020-04-13").plusDays(importOffset), 3.9, 0.1);

		importMap.put(LocalDate.parse("2020-07-19"), (int) (0.5 * 32));
		importMap.put(LocalDate.parse("2020-08-09"), 1);

		episimConfig.setInfections_pers_per_day(importMap);

		if (sebastianUpdate) {
			configureImport(episimConfig, ba5, strainA); //todo: integrate this with code above
		} else {

			//ALPHA
			Map<LocalDate, Integer> infPerDayB117 = new HashMap<>();
			infPerDayB117.put(LocalDate.parse("2020-01-01"), 0);

			infPerDayB117.put(LocalDate.parse("2021-01-16"), 20);
			infPerDayB117.put(LocalDate.parse("2021-01-16").plusDays(1), 1);
			infPerDayB117.put(LocalDate.parse("2020-12-31"), 1);

			episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayB117);

			// DELTA
			Map<LocalDate, Integer> infPerDayDelta = new HashMap<>();
			infPerDayDelta.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayDelta.put(LocalDate.parse("2021-06-21"), 4);
			infPerDayDelta.put(LocalDate.parse("2021-06-21").plusDays(7), 1);

			LocalDate summerHolidaysEnd = LocalDate.parse("2021-08-17").minusDays(14);
			int imp1 = 120;
			int imp2 = 10;
			int imp3 = 40;

			SnzCologneProductionScenario.interpolateImport(infPerDayDelta, 1.0, summerHolidaysEnd.minusDays(5 * 7), summerHolidaysEnd, 1, imp1);
			SnzCologneProductionScenario.interpolateImport(infPerDayDelta, 1.0, summerHolidaysEnd, summerHolidaysEnd.plusDays(3 * 7), imp1, imp2);


			LocalDate autumnHolidaysEnd = LocalDate.parse("2021-10-17");

			SnzCologneProductionScenario.interpolateImport(infPerDayDelta, 1.0, autumnHolidaysEnd.minusDays(2 * 7), autumnHolidaysEnd, imp2, imp3);
			SnzCologneProductionScenario.interpolateImport(infPerDayDelta, 1.0, autumnHolidaysEnd, autumnHolidaysEnd.plusDays(2 * 7), imp3, 1);


			episimConfig.setInfections_pers_per_day(VirusStrain.DELTA, infPerDayDelta);

			//BA.1
			String ba1Date = "2021-11-21";
			Map<LocalDate, Integer> infPerDayOmicron = new HashMap<>();
			infPerDayOmicron.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayOmicron.put(LocalDate.parse(ba1Date), 4);
			infPerDayOmicron.put(LocalDate.parse(ba1Date).plusDays(7), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA1, infPerDayOmicron);


			//BA.2
			String ba2Date = "2021-12-18";
			Map<LocalDate, Integer> infPerDayBA2 = new HashMap<>();
			infPerDayBA2.put(LocalDate.parse("2020-01-01"), 0);
			infPerDayBA2.put(LocalDate.parse(ba2Date), 4);
			infPerDayBA2.put(LocalDate.parse(ba2Date).plusDays(7), 1);
			episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA2, infPerDayBA2);
		}
	}

	private void configureImport(EpisimConfigGroup episimConfig, boolean ba5, boolean strainA) {

		Map<LocalDate, Integer> infPerDayWild = new HashMap<>();

		for (Map.Entry<LocalDate, Integer> entry : episimConfig.getInfections_pers_per_day().get(VirusStrain.SARS_CoV_2).entrySet() ) {
			if (entry.getKey().isBefore(LocalDate.parse("2020-08-12"))) {
				int value = entry.getValue();
				value = Math.max(1, value);
				infPerDayWild.put(entry.getKey(), value);
			}
		}

		Map<LocalDate, Integer> infPerDayAlpha = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.ALPHA, new TreeMap<>()));
		Map<LocalDate, Integer> infPerDayDelta = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.DELTA, new TreeMap<>()));
		Map<LocalDate, Integer> infPerDayBa1 = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.OMICRON_BA1, new TreeMap<>()));
		Map<LocalDate, Integer> infPerDayBa2 = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.OMICRON_BA2, new TreeMap<>()));
		Map<LocalDate, Integer> infPerDayBa5 = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.OMICRON_BA5, new TreeMap<>()));
		Map<LocalDate, Integer> infPerDayStrA = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.STRAIN_A, new TreeMap<>()));

		int facWild = 4;
		int facAlpha = 4;
		int facDelta = 4;
		int facBa1 = 4;
		int facBa2 = 4;
		int facBa5 = 4;
		int facStrA = 4;

		// dates for disease import to switch strains
		LocalDate dateAlpha = LocalDate.parse("2021-01-23");
		LocalDate dateDelta = LocalDate.parse("2021-06-28");
		LocalDate dateBa1 = LocalDate.parse("2021-12-12");
		LocalDate dateBa2 = LocalDate.parse("2022-01-27"); // local min of disease import
		LocalDate dateBa5 = LocalDate.parse("2022-05-01"); // after vaca import
		LocalDate dateStrainA = LocalDate.parse("2022-11-18"); // after vaca import


		infPerDayAlpha.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayDelta.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayBa1.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayBa2.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayBa5.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayStrA.put(LocalDate.parse("2020-01-01"), 0);





		LocalDate date = null;
		try (Reader in = new FileReader(SnzCologneProductionScenario.INPUT.resolve("cologneDiseaseImport_Projected.csv").toFile())) {
			Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().withCommentMarker('#').parse(in);
			DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yy");
			for (CSVRecord record : records) {

				double factor = 0.25 * 2352476. / 919936.; //25% sample, data is given for Cologne City so we have to scale it to the whole model

				double cases = factor * Integer.parseInt(record.get("cases"));

				date = LocalDate.parse(record.get(0), fmt);

				if (date.isAfter(dateStrainA) && strainA) {
					infPerDayStrA.put(date, ((int) cases * facStrA) == 0 ? 1 : (int) (cases * facStrA));
				} else if (date.isAfter(dateBa5) && ba5) {
					infPerDayBa5.put(date, ((int) cases * facBa5) == 0 ? 1 : (int) (cases * facBa5));
				} else if (date.isAfter(dateBa2)) {
					infPerDayBa2.put(date, ((int) cases * facBa2) == 0 ? 1 : (int) (cases * facBa2));
				} else if (date.isAfter(dateBa1)) {
					infPerDayBa1.put(date, ((int) cases * facBa1) == 0 ? 1 : (int) (cases * facBa1));
				} else if (date.isAfter(dateDelta)) {
					infPerDayDelta.put(date, ((int) cases * facDelta) == 0 ? 1 : (int) (cases * facDelta));
				} else if (date.isAfter(dateAlpha)) {
					infPerDayAlpha.put(date, ((int) cases * facAlpha) == 0 ? 1 : (int) (cases * facAlpha));
				} else {
					infPerDayWild.put(date, ((int) cases * facWild) == 0 ? 1 : (int) (cases * facWild));
				}
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		if (date == null) {
			throw new RuntimeException("something went wrong while reading csv");
		}

		// Disease Import after new strain becomes prominent; todo: does it make sense to have import of 1 for rest of simulation?
		infPerDayWild.put(dateAlpha.plusDays(1), 1);
		infPerDayAlpha.put(dateDelta.plusDays(1), 1);
		infPerDayDelta.put(dateBa1.plusDays(1), 1);
		infPerDayBa1.put(dateBa2.plusDays(1), 1);
		if(ba5 && strainA) {
			infPerDayBa2.put(dateBa5.plusDays(1), 1);
			infPerDayBa5.put(dateStrainA.plusDays(1), 1);
			infPerDayStrA.put(date.plusDays(1), 1);
		} else if(ba5) {
			infPerDayBa2.put(dateBa5.plusDays(1), 1);
			infPerDayBa5.put(date.plusDays(1), 1);
		}
		else if(strainA) {
			infPerDayBa2.put(dateStrainA.plusDays(1), 1);
			infPerDayStrA.put(date.plusDays(1), 1);
		} else{
			infPerDayBa2.put(date.plusDays(1), 1);
		}



		episimConfig.setInfections_pers_per_day(VirusStrain.SARS_CoV_2, infPerDayWild);
		episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayAlpha);
		episimConfig.setInfections_pers_per_day(VirusStrain.DELTA, infPerDayDelta);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA1, infPerDayBa1);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA2, infPerDayBa2);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA5, infPerDayBa5);
		episimConfig.setInfections_pers_per_day(VirusStrain.STRAIN_A, infPerDayStrA);

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

		@Parameter({0.0, 0.9})
		double ba5Inf;

		@Parameter({3.})
		public double ba5Esc;

		// StrainA
		@StringParameter({"2022-11-01"})
		public String strADate;

		@Parameter({0.0, 1., 6.}) // 0.0 = strainA is off
		public double strAEsc;

//		@Parameter({0.0, 1.0})
//		public double strAInf;

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
		double edu;

		// testing in schools
		@StringParameter({"true", "false"})
		String eduTest;

		// university
//		@Parameter({0.0, 1.0})
//		double uni;

		// shopping: mask
		@StringParameter({"true", "false"})
		String maskShopAndPt;

		// work:
		@Parameter({ 0.5, 1.0})
		double work;

		// leisure
		@Parameter({0.25, 0.5, 0.75, 1.0})
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


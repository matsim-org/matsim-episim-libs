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
import org.matsim.episim.model.testing.DefaultTestingModel;
import org.matsim.episim.model.testing.FlexibleTestingModel;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.model.vaccination.VaccinationStrategyBMBF0617;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.annotation.Nullable;
import java.time.DayOfWeek;
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

					start = LocalDate.parse(params.resDate);
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

				if (params != null) {

					// date of no restrictions and new restrictions
					LocalDate unresDate = LocalDate.parse(params.unResDate);
					LocalDate resDate = LocalDate.parse(params.resDate);

					String[] s = params.testScheme.split("-");

					// Green pass validity for vaccinated and infected
					int vacDays = Integer.parseInt(s[0]) * 30;
					int infDays = Integer.parseInt(s[1]) * 30;

					bind(FlexibleTestingModel.TestRate.class).toInstance(new FlexibleTestingModel.TestRate() {
						@Override
						public boolean useFullyVaccinatedTestRate(EpisimPerson person, int day, DayOfWeek dow, LocalDate date, VaccinationConfigGroup vac) {

							if (date.isBefore(unresDate))
								return vac.hasValidVaccination(person, day, date);

							// When restrictions have been lifted, days valid is set to a high number
							// many tests are voluntary
							if (date.isBefore(resDate))
								return vac.hasValidVaccination(person, day, date, 360);

							// this returns true if for any activities, the person requires increased testing regime
							BiFunction<String, Boolean, Boolean> anyUnVac = (act, red) -> {

								boolean res = false;
								TestScheme scheme = null;

								if (act.equals("leisure"))
									scheme = params.leisTest;
								else if (act.equals("work"))
									scheme = params.workTest;
								else if (act.startsWith("edu"))
									scheme = params.eduTest;

								// null and none, will be false
								if (scheme == TestScheme.all)
									res = true;
								else if (scheme == TestScheme.part) {
									res = person.daysSince(EpisimPerson.VaccinationStatus.yes, day) > vacDays && !person.isRecentlyRecovered(day, infDays);

								}

								return red || res;
							};

							return !person.matchActivities(dow, anyUnVac, false);
						}
					});

					bind(FlexibleTestingModel.TestPolicy.class).toInstance(new FlexibleTestingModel.TestPolicy() {
						@Override
						public boolean shouldTest(EpisimPerson person, int day, DayOfWeek dow, LocalDate date, VaccinationConfigGroup vac) {

							if (date.isBefore(unresDate))
								return vac.hasGreenPass(person, day, date);

							// after restrictions everybody is tested according to the rates
							return true;
						}
					});

				}
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
				.setTestingModel(FlexibleTestingModel.class)
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

		//restrictions

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		// Restrictions starting on December 1, 2022
		LocalDate restrictionDate = LocalDate.parse(params.resDate);

		//school
		if (params.resDate.equals("2022-12-01")) {
			builder.restrict(restrictionDate, params.edu, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		}
		builder.applyToRf(restrictionDate.plusDays(1).toString(), restrictionDate.plusDays(1000).toString(), (d, rf) -> Math.min(params.edu, rf), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");


		//university
		builder.restrict(restrictionDate, params.edu, "educ_higher");
		builder.applyToRf(restrictionDate.plusDays(1).toString(), restrictionDate.plusDays(1000).toString(), (d, rf) -> Math.min(params.edu, rf), "educ_higher");

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

		//configure new strains
		//BA5
		double oHos = virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getFactorSeriouslySick();
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getInfectiousness() * params.ba5Inf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySick(oHos);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySickVaccinated(oHos);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorCritical(oHos);


//		STRAIN_A
		double strAInf = 1.0;
		if (params.strAEsc != 0.) {

			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getInfectiousness() * (params.ba5Inf == 0. ? 1 : params.ba5Inf) * strAInf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySickVaccinated(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorCritical(oHos);
		}

		//Configure Disease Import

		configureFutureDiseaseImport(params, episimConfig);


		//vaccinations
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setUseIgA(true);
		vaccinationConfig.setTimePeriodIgA(params.igATime);

//		diseaseImp(episimConfig, Boolean.parseBoolean(params.sebaUp),params.ba5Inf!=0., params.strAEsc != 0.);

		// testing
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);
		TestingConfigGroup.TestingParams pcrTest = testingConfigGroup.getOrAddParams(TestType.PCR);
		Map<String, NavigableMap<LocalDate, Double>> testingRateForActivitiesPcr = pcrTest.getTestingRateForActivities();
		Map<String, NavigableMap<LocalDate, Double>> testingRateForActivitiesPcrVac = pcrTest.getTestingRateForActivitiesVaccinated();

		TestingConfigGroup.TestingParams rapidTest = testingConfigGroup.getOrAddParams(TestType.RAPID_TEST);
		Map<String, NavigableMap<LocalDate, Double>> testingRateForActivitiesRapid = rapidTest.getTestingRateForActivities();
		Map<String, NavigableMap<LocalDate, Double>> testingRateForActivitiesRapidVac = rapidTest.getTestingRateForActivitiesVaccinated();

		if (params.eduTest.equals("all") || params.eduTest.equals("unvac")  ) {

			//add pcr tests for kindergarden and primary school

			testingRateForActivitiesPcr.get("educ_kiga").put(restrictionDate, 0.4);
			testingRateForActivitiesPcr.get("educ_primary").put(restrictionDate, 0.4);


			if (params.eduTest.equals("all")) {
				testingRateForActivitiesPcrVac.get("educ_kiga").put(restrictionDate, 0.4);
				testingRateForActivitiesPcrVac.get("educ_primary").put(restrictionDate, 0.4);
			}

			//add rapid tests for older kids
			testingRateForActivitiesRapid.get("educ_secondary").put(restrictionDate, 0.6);
			testingRateForActivitiesRapid.get("educ_tertiary").put(restrictionDate, 0.6);
			testingRateForActivitiesRapid.get("educ_other").put(restrictionDate, 0.6);

			if (params.eduTest.equals("all")) {
				testingRateForActivitiesRapidVac.get("educ_secondary").put(restrictionDate, 0.6);
				testingRateForActivitiesRapidVac.get("educ_tertiary").put(restrictionDate, 0.6);
				testingRateForActivitiesRapidVac.get("educ_other").put(restrictionDate, 0.6);
			}
		} else if (params.eduTest.equals("no")) {
		} else {
			throw new RuntimeException("incorrect param for edu test");
		}

		if (params.workTest.equals("all") || params.workTest.equals("unvac")  ) {

			//add rapid tests for older kids
			testingRateForActivitiesRapid.get("work").put(restrictionDate, 0.6);

			if (params.workTest.equals("all")) {
				testingRateForActivitiesRapidVac.get("work").put(restrictionDate, 0.6);
			}
		} else if (params.workTest.equals("no")) {
		} else {
			throw new RuntimeException("incorrect param for edu test");
		}

		// leisure: 2g+
		if (params.testScheme.equals("all")) {
			testingRateForActivitiesRapid.get("leisure").put(restrictionDate, params.testRate);
			testingRateForActivitiesRapidVac.get("leisure").put(restrictionDate, params.testRate);
		} else if (params.testScheme.equals("none")) {

		} else{
			int vacTimePeriod = Integer.parseInt(params.testScheme.split("-")[0]);
			int unvacTimePeriod = Integer.parseInt(params.testScheme.split("-")[1]);

			// TODO: how do we set vac & unvac time period.


		}

		if(DEBUG_MODE) {
			UtilsJR.produceDiseaseImportPlot(episimConfig.getInfections_pers_per_day());
//			UtilsJR.produceMaskPlot(episimConfig.getPolicy());
		}





			return config;
	}

	private void configureFutureDiseaseImport(Params params, EpisimConfigGroup episimConfig) {
		Map<LocalDate, Integer> infPerDayBa2 = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.OMICRON_BA2, new TreeMap<>()));
		Map<LocalDate, Integer> infPerDayBa5 = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.OMICRON_BA5, new TreeMap<>()));
		Map<LocalDate, Integer> infPerDayStrA = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.STRAIN_A, new TreeMap<>()));

		// add initial impulses for strains
		//BA.2
		LocalDate ba2Date = LocalDate.parse("2021-12-18");
		for (int i = 0; i < 7; i++) {
			infPerDayBa2.put(ba2Date.plusDays(i), 4);
		}
		infPerDayBa2.put(ba2Date.plusDays(7), 1);

		//BA.5
		LocalDate ba5Date = LocalDate.parse(params.ba5Date);
		for (int i = 0; i < 7; i++) {
			infPerDayBa5.put(ba5Date.plusDays(i), 4);
		}
		infPerDayBa5.put(ba5Date.plusDays(7), 1);

		//StrainA
		if (params.strAEsc != 0.) {
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
			} else if (date.isAfter(dateBa5) && params.ba5Inf != 0.) {
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


		//


		// save disease import
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

		@Parameter({0.9}) //,1.0,1.1,1.2,1.3})
		double ba5Inf;

		@Parameter({3.})
		public double ba5Esc;

		// StrainA
		@StringParameter({"2022-11-01"})
		public String strADate;

		@Parameter({6.}) // 0.0 = strainA is off
//		@Parameter({6.}) // 0.0 = strainA is off
		public double strAEsc;

//		@Parameter({0.0, 1.0})
//		public double strAInf;

		@StringParameter({"2022-04-25"})
		public String unResDate;

		// General Restriction date
		@StringParameter({"2022-12-01"})
		public String resDate;

		// vaccination campaign
		@StringParameter({"omicronUpdate"})
		public String vacType;

		@StringParameter({"off", "age"})
//		@StringParameter({"age"})
		String vacCamp;

		// other restrictions
		// schools & university
		@Parameter({0.2, 0.5, 1.0})
//		@Parameter({0.2})
		double edu;

		// university
//		@Parameter({0.0, 1.0})
//		double uni;

		// shopping: mask
		@StringParameter({"true", "false"})
//		@StringParameter({"true"})
		String maskShopAndPt;

		// work:
		@Parameter({0.5, 1.0})
//		@Parameter({0.5})
		double work;

		// leisure
		@Parameter({0.25, 0.5, 0.75, 1.0})
//		@Parameter({ 0.5})
		double leis;

		// testing in schools
//		@StringParameter({"no", "unvac", "all"})
////		@StringParameter({"all"})
//				String eduTest;

		// work tests
		@EnumParameter(TestScheme.class)
		TestScheme workTest;

		@EnumParameter(TestScheme.class)
		TestScheme eduTest;

		@EnumParameter(TestScheme.class)
		TestScheme leisTest;

		//2g+
		@StringParameter({"3-0","3-3", "3-6","3-9", "3-12", "6-0","6-3", "6-6","6-9", "6-12","9-0","9-3", "9-6","9-9", "9-12", "12-0","12-3", "12-6","12-9", "12-12"})
		String testScheme;

		@Parameter({0.05, 0.5})
		double testRate;

	}

	public enum TestScheme {
		none,
		all,
		part
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


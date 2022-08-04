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
public class CologneBMBF20220805_IfSG implements BatchRun<CologneBMBF20220805_IfSG.Params> {

	boolean DEBUG_MODE = false;
	int runCount = 0;

	@Nullable
	@Override
	public Module getBindings(int id, @Nullable Params params) {
		return Modules.override(getBindings(0.0, params)).with(new AbstractModule() {
			@Override
			protected void configure() {

				// BIND ANTIBODY MODEL
				double mutEscDelta = 29.2 / 10.9;
				double mutEscBa1 = 10.9 / 1.9;
				double mutEscBa5 = 2.9;

				double mutEscStrainA = 0.;
				double mutEscStrainB = 0.;

				if (params != null && !params.StrainA.equals("off")) {
					mutEscStrainA = Double.parseDouble(params.StrainA);
				}
				if (params != null && !params.StrainB.equals("off")) {
					mutEscStrainB = Double.parseDouble(params.StrainB);
				}

				Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();
				Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();
				configureAntibodies(initialAntibodies, antibodyRefreshFactors, mutEscDelta, mutEscBa1, mutEscBa5, mutEscStrainA, mutEscStrainB);

				AntibodyModel.Config antibodyConfig = new AntibodyModel.Config(initialAntibodies, antibodyRefreshFactors);

				double immuneSigma = 3.0;
				if (params != null) {
					antibodyConfig.setImmuneReponseSigma(immuneSigma);
				}

				bind(AntibodyModel.Config.class).toInstance(antibodyConfig);


				// BIND VACCINATION MODEL

				Multibinder<VaccinationModel> set = Multibinder.newSetBinder(binder(), VaccinationModel.class);

				set.addBinding().to(VaccinationStrategyBMBF0617.class).in(Singleton.class);

				LocalDate start = LocalDate.parse("2022-12-01");

				VaccinationType vaccinationType = VaccinationType.mRNA;

				Int2DoubleMap compliance = new Int2DoubleAVLTreeMap();
				compliance.put(60, 0.0);
				compliance.put(18, 0.0);
				compliance.put(12, 0.0);
				compliance.put(0, 0.0);

				String vacCamp = "off";

				if (params != null) {

					if (!params.vacType.equals("off")) {
						vacCamp = "age";
						vaccinationType = VaccinationType.valueOf(params.vacType);
					}

//					start = LocalDate.parse(params.resDate);


					switch (vacCamp) {
						case "age":
							compliance.put(60, 0.85); // 60+
							compliance.put(18, 0.55); // 18-59
							compliance.put(12, 0.20); // 12-17
							compliance.put(0, 0.0); // 0 - 11
							break;
						case "eu":
							compliance.put(60, 0.40); // half of 80% (which reflects the current percentage of people in Dland who are boostered)
							compliance.put(18, 0.);
							compliance.put(12, 0.);
							compliance.put(0, 0.);
							break;
						case "off":
							break;
						default:
							throw new RuntimeException("Not a valid option for vaccinationCampaignType");
					}
				}

				bind(VaccinationStrategyBMBF0617.Config.class).toInstance(new VaccinationStrategyBMBF0617.Config(start, 30, vaccinationType, compliance));



				// BIND TESTING MODEL
				if (params != null){

					// date of no restrictions and new restrictions
					String unResDate = "2022-04-25";
					LocalDate unresDate = LocalDate.parse(unResDate);
					LocalDate resDate = LocalDate.parse(params.resDate);

//					String[] s = params.gpMonths.split("-");

					// Green pass validity for vaccinated and infected
//					int vacDays = Integer.parseInt(s[0]) * 30;
//					int infDays = Integer.parseInt(s[1]) * 30;

					// set days gp is valid
					int vacDays = 3 * 30;
					int infDays = 3 * 30;

					// set testing scheme for each activity type
					final CologneBMBF220628_3G.TestScheme leisTest;
					final CologneBMBF220628_3G.TestScheme workTest;
					final CologneBMBF220628_3G.TestScheme eduTest;
					if (params.leis.equals("test") || params.leis.equals("all")) {
						leisTest= CologneBMBF220628_3G.TestScheme.gp;
					} else if (params.leis.equals("none") || params.leis.equals("mask")) {
						leisTest = CologneBMBF220628_3G.TestScheme.none;
					} else {
						throw new RuntimeException();
					}


					if (params.work.equals("test") || params.work.equals("all")) {
						workTest= CologneBMBF220628_3G.TestScheme.all;
					} else if (params.work.equals("none") || params.work.equals("mask")|| params.work.equals("homeOff")) {
						workTest = CologneBMBF220628_3G.TestScheme.none;
					} else {
						throw new RuntimeException();
					}

					if (params.edu.equals("maskVentTest")) {
						eduTest = CologneBMBF220628_3G.TestScheme.all;
					} else if (params.edu.equals("none")) {
						eduTest = CologneBMBF220628_3G.TestScheme.none;
					} else {throw new RuntimeException();}

					bind(FlexibleTestingModel.TestRate.class).toInstance((person, day, dow, date, test, vac) -> {

						if (date.isBefore(unresDate))
							return vac.hasValidVaccination(person, day, date);

						// When restrictions have been lifted, days valid is set to a high number
						// many tests are voluntary
						if (date.isBefore(resDate))
							return vac.hasValidVaccination(person, day, date, 360);

						// this returns true if for any activities, the person requires increased testing regime
						BiFunction<String, Boolean, Boolean> anyUnVac = (act, red) -> {

							boolean res = false;
							CologneBMBF220628_3G.TestScheme scheme = null;


							if (act.equals("leisure"))
								scheme = leisTest;
							else if (act.equals("work"))
								scheme = workTest;
							else if (act.startsWith("edu"))
								scheme = eduTest;

							// null and none, will be false
							if (scheme == CologneBMBF220628_3G.TestScheme.all)
								res = true;
							else if (scheme == CologneBMBF220628_3G.TestScheme.gp) {
								res = (person.getNumVaccinations() == 0 || person.daysSince(EpisimPerson.VaccinationStatus.yes, day) > vacDays)
										&& !person.isRecentlyRecovered(day, infDays);

							}

							return red || res;
						};

						return !person.matchActivities(dow, anyUnVac, false);
					});

					bind(FlexibleTestingModel.TestPolicy.class).toInstance(new FlexibleTestingModel.TestPolicy() {
						@Override
						public boolean shouldTest(EpisimPerson person, int day, DayOfWeek dow, LocalDate date,
												  TestingConfigGroup test, VaccinationConfigGroup vac) {

							if (date.isBefore(unresDate)) {

								boolean testAllPersons = test.getTestAllPersonsAfter() != null && date.isAfter(test.getTestAllPersonsAfter());
								return testAllPersons || !vac.hasGreenPass(person, day, date);
							}

							// after restrictions everybody is tested according to the rates
							return true;
						}
					});

				}

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


		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 1.2);



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
		if (!params.StrainA.equals("off")) {

			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA5).getInfectiousness() * ba5Inf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySickVaccinated(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorCritical(oHos);
		}

//		STRAIN_B
		if (!params.StrainB.equals("off")) {

			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_B).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA5).getInfectiousness() * ba5Inf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_B).setFactorSeriouslySick(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_B).setFactorSeriouslySickVaccinated(oHos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_B).setFactorCritical(oHos);
		}

		// remove age-based susceptibility of strains starting with DELTA

		String ageSusc = "false";
		if (!Boolean.parseBoolean(ageSusc)) {
			TreeMap<Integer, Double> nonSteppedAgeSusceptibility = new TreeMap<>(Map.of(
					19, 1d,
					20, 1d
			));

			for (VirusStrain strain : List.of(VirusStrain.DELTA, VirusStrain.OMICRON_BA1, VirusStrain.OMICRON_BA2, VirusStrain.OMICRON_BA5, VirusStrain.STRAIN_A)) {
				virusStrainConfigGroup.getOrAddParams(strain).setAgeSusceptibility(nonSteppedAgeSusceptibility);
			}
		}

		// increase infectivity of alpha
		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).getInfectiousness() * 1.4);

		double deltaTheta = 0.9;
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setInfectiousness(virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).getInfectiousness() * deltaTheta);
		double ba1Inf = 1.9; // 2.0,2.1,2.2
		double ba2Inf = 1.7;
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA1).setInfectiousness(virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).getInfectiousness() * ba1Inf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA2).setInfectiousness(virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).getInfectiousness() * ba1Inf * ba2Inf);


		//---------------------------------------
		//		I M P O R T
		//---------------------------------------

		Map<LocalDate, Integer> infPerDayAlpha = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.ALPHA, new TreeMap<>()));


		// reconfig disease import of alpha
		LocalDate startDateAlpha = LocalDate.parse("2021-01-15");

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

		// -----------------------------------------

		// new restrictions from IfSG
		LocalDate restrictionDate = LocalDate.parse(params.resDate);

		// testing rates

		double gpTestRate = 0.;

		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);
		TestingConfigGroup.TestingParams pcrTest = testingConfigGroup.getOrAddParams(TestType.PCR);
		Map<String, NavigableMap<LocalDate, Double>> testingRateForActivitiesPcr = pcrTest.getTestingRateForActivities();
		Map<String, NavigableMap<LocalDate, Double>> testingRateForActivitiesPcrVac = pcrTest.getTestingRateForActivitiesVaccinated();

		TestingConfigGroup.TestingParams rapidTest = testingConfigGroup.getOrAddParams(TestType.RAPID_TEST);
		Map<String, NavigableMap<LocalDate, Double>> testingRateForActivitiesRapid = rapidTest.getTestingRateForActivities();
		Map<String, NavigableMap<LocalDate, Double>> testingRateForActivitiesRapidVac = rapidTest.getTestingRateForActivitiesVaccinated();


		// school
		{
			//pcr tests for younger kids
			testingRateForActivitiesPcr.get("educ_kiga").put(restrictionDate, 0.4);
			testingRateForActivitiesPcr.get("educ_primary").put(restrictionDate, 0.4);
			testingRateForActivitiesPcrVac.get("educ_kiga").put(restrictionDate, gpTestRate);
			testingRateForActivitiesPcrVac.get("educ_primary").put(restrictionDate, gpTestRate);

			//add rapid tests for older kids
			testingRateForActivitiesRapid.get("educ_secondary").put(restrictionDate, 0.6);
			testingRateForActivitiesRapid.get("educ_tertiary").put(restrictionDate, 0.6);
			testingRateForActivitiesRapid.get("educ_higher").put(restrictionDate, 0.6);
			testingRateForActivitiesRapid.get("educ_other").put(restrictionDate, 0.6);
			testingRateForActivitiesRapidVac.get("educ_secondary").put(restrictionDate, gpTestRate);
			testingRateForActivitiesRapidVac.get("educ_tertiary").put(restrictionDate, gpTestRate);
			testingRateForActivitiesRapidVac.get("educ_higher").put(restrictionDate, gpTestRate);
			testingRateForActivitiesRapidVac.get("educ_other").put(restrictionDate, gpTestRate);
		}

		// work
		{
			testingRateForActivitiesRapid.get("work").put(restrictionDate, 0.6);
			testingRateForActivitiesRapidVac.get("work").put(restrictionDate, gpTestRate);
		}

		// leisure:
		{
			testingRateForActivitiesRapid.get("leisure").put(restrictionDate, 0.45);
			testingRateForActivitiesRapidVac.get("leisure").put(restrictionDate, gpTestRate);
		}

		//WORK
		double homeOfficeFactor = 0.5;
		switch (params.work) {
			case "none":
				break;
			case "homeOff":
				builder.restrict(restrictionDate, 0.78 * homeOfficeFactor, "work"); // dont include business bc harder to do from home office
				builder.applyToRf(restrictionDate.plusDays(1).toString(), restrictionDate.plusDays(1000).toString(), (d, rf) -> rf * homeOfficeFactor, "work");
				break;
			case "test":
				// handled in getBindings
				break;
			case "mask":
				builder.restrict(restrictionDate, Restriction.ofMask(Map.of(FaceMask.SURGICAL, 0.9)), "work", "business");
				break;
			case "all":
				builder.restrict(restrictionDate, 0.78 * homeOfficeFactor, "work");
				builder.applyToRf(restrictionDate.plusDays(1).toString(), restrictionDate.plusDays(1000).toString(), (d, rf) -> rf * homeOfficeFactor, "work");
				builder.restrict(restrictionDate, Restriction.ofMask(Map.of(FaceMask.SURGICAL, 0.9)), "work", "business");
				break;
			default:
				throw new RuntimeException("invalid parameter");
		}

		//LEISURE
		switch (params.leis) {
			case "none":
				break;
			case "test":
				// handled in getBindings
				break;
			case "mask":
				builder.restrict(restrictionDate, Restriction.ofMask(Map.of(FaceMask.N95, 0.45)), "leisure");
			case "all":
				builder.restrict(restrictionDate, Restriction.ofMask(Map.of(FaceMask.N95, 0.45)), "leisure");
				break;
			default:
				throw new RuntimeException("invalid parameter");
		}

		// shop, errands
		switch (params.errands) {
			case "none":
				break;
			case "mask":
				builder.restrict(restrictionDate, Restriction.ofMask(Map.of(FaceMask.N95, 0.9)), "shop_daily", "shop_other", "errands");

				break;
			default:
				throw new RuntimeException("invalid parameter");
		}

		//SCHOOL
		// todo check mask rate
		if (params.edu.equals("maskVentTest")) {
			builder.restrict(LocalDate.parse(params.resDate), Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other", "educ_higher");
			builder.restrict(LocalDate.parse(params.resDate), Restriction.ofMask(Map.of(
							FaceMask.CLOTH, 0.0,
							FaceMask.N95, 0.25,
							FaceMask.SURGICAL, 0.25)),
					 "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		} else if (params.edu.equals("none")) {

		} else {
			throw new RuntimeException("invalid parameter");
		}


		// pt
		switch (params.pt) {
			case "none":
				break;
			case "mask":
				builder.restrict(restrictionDate, Restriction.ofMask(Map.of(FaceMask.N95, 0.9)), "pt");
				break;
			default:
				throw new RuntimeException("invalid parameter");
		}


		episimConfig.setPolicy(builder.build());


		//---------------------------------------
		//		M I S C
		//---------------------------------------

		// vaccination
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setUseIgA(Boolean.parseBoolean("true"));
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
		Map<LocalDate, Integer> infPerDayStrB = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.STRAIN_B, new TreeMap<>()));


		// add initial impulses for strains
		//BA.1
//		LocalDate ba1Date = LocalDate.parse(params.ba1Date);
//		for (int i = 0; i < 7; i++) {
//			infPerDayBa1.put(ba1Date.plusDays(i), 4);
//		}
//		infPerDayBa1.put(ba1Date.plusDays(7), 1);


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

		@StringParameter({"6.0"})
		public String StrainA;

		@StringParameter({"off"})
		public String StrainB;

		@StringParameter({"2022-10-01"})
		public String resDate;

		//		@StringParameter({"false", "true"})
//		public String igA;
		// vaccination campaign
		@StringParameter({"ba5Update", "mRNA", "off"})
		public String vacType;


		//measures in the work context:
		// homeOff = 50% home office = work Rf cut in half
		//
		@StringParameter({"none", "homeOff", "test", "mask", "all"})
		public String work;

		@StringParameter({"none", "mask", "test", "all"})
		public String leis;

		// mask restrictions for "shop_daily", "shop_other", "errands"
		@StringParameter({"none", "mask"})
		public String errands;

		@StringParameter({"none", "maskVentTest"})
		public String edu;

		@StringParameter({"mask"})
		public String pt;
	}


	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneBMBF20220805_IfSG.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(1000),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


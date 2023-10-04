package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.analysis.*;
import org.matsim.episim.model.*;
import org.matsim.episim.model.listener.HouseholdSusceptibility;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.model.vaccination.VaccinationStrategyReoccurringCampaigns;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;


/**
 * Batch for Bmbf runs
 */
public class CologneBMBF202310XX_soup implements BatchRun<CologneBMBF202310XX_soup.Params> {

	boolean DEBUG_MODE = false;
	int runCount = 0;

	LocalDate restrictionDatePhase1 = LocalDate.parse("2022-12-01");
	LocalDate restrictionDatePhase2 = restrictionDatePhase1.plusDays(10);


	@Nullable
	@Override
	public Module getBindings(int id, @Nullable Params params) {
		return Modules.override(getBindings(0.0, params)).with(new AbstractModule() {
			@Override
			protected void configure() {

				// VACCINATION MODEL
				Multibinder<VaccinationModel> set = Multibinder.newSetBinder(binder(), VaccinationModel.class);
				set.addBinding().to(VaccinationStrategyReoccurringCampaigns.class).in(Singleton.class);
				// fixed values
				LocalDate start = LocalDate.parse("2022-10-15");
				VaccinationType vaccinationType = VaccinationType.ba5Update;
				int campaignDuration = 300000;

				// default values, to be changed if params != null
				int minDaysAfterInfection = 180;
				int minDaysAfterVaccination = 180;
				VaccinationStrategyReoccurringCampaigns.Config.VaccinationPool vaccinationPool = VaccinationStrategyReoccurringCampaigns.Config.VaccinationPool.vaccinated;
				LocalDate emergencyDate = LocalDate.MAX;
				LocalDate dateToTurnDownMinDaysAfterInfection = LocalDate.MAX;
				Map<LocalDate,VaccinationType> startDateToVaccination = new HashMap<>();
				startDateToVaccination.put(start, vaccinationType);

				if (params != null) {
					if (params.vacCamp.equals("base")) { // +

					} else if(params.vacCamp.equals("ph1_90")){
						minDaysAfterInfection = 90;
						minDaysAfterVaccination = 90;

						emergencyDate = restrictionDatePhase1;
					} else if(params.vacCamp.equals("ph1_90vax180")){
						minDaysAfterInfection = 90;
						emergencyDate = restrictionDatePhase1;
					} else if(params.vacCamp.equals("ph1_180")){ // +
						emergencyDate = restrictionDatePhase1;
					} else if (params.vacCamp.equals("ph1_180_ph2_inf90vax180")) {
						emergencyDate = restrictionDatePhase1;
						dateToTurnDownMinDaysAfterInfection = restrictionDatePhase2;
						// same as ifsg180 but after phase 2 date, minDaysAfterInfection = 90;
					}else if(params.vacCamp.equals("ph2_90")){
						minDaysAfterInfection = 90;
						minDaysAfterVaccination = 90;
						emergencyDate = restrictionDatePhase2;
					} else if(params.vacCamp.equals("ph2_inf90vax180")){
						minDaysAfterInfection = 90;
						emergencyDate = restrictionDatePhase2;
					} else if(params.vacCamp.equals("ph2_180")) {
						emergencyDate = restrictionDatePhase2;
					}else {
						throw new RuntimeException();
					}
				}

				bind(VaccinationStrategyReoccurringCampaigns.Config.class).toInstance(new VaccinationStrategyReoccurringCampaigns.Config(startDateToVaccination, campaignDuration, vaccinationPool, minDaysAfterInfection, minDaysAfterVaccination, emergencyDate, dateToTurnDownMinDaysAfterInfection));


				// ANTIBODY MODEL
				// default values
				double mutEscDelta = 29.2 / 10.9;
				double mutEscBa1 = 10.9 / 1.9;
				double mutEscBa5 = 5.0;

				double mutEscStrainA = 1.0;
				double mutEscStrainB = 1.0;

				double escape = 12.;
				int days = 30;
				String strainSeed = "no";
				LocalDate strainADate = LocalDate.parse("2020-01-01");
				boolean lineB = true;
				double escapeBetweenLines = 1.0;

				if (params != null) {
//					mutEscBa1 = params.ba1Esc;
//					mutEscBa5 = params.ba5Esc;

//					String StrainA = "6.0";
					String StrainB = "off";


					if (!params.StrainA.equals("off")) {
						mutEscStrainA = Double.parseDouble(params.StrainA);
					}
					if (!StrainB.equals("off")) {
						mutEscStrainB = Double.parseDouble(StrainB);
					}
					escape = params.esc;
					days = params.days;
					strainSeed = params.strainRnd;
					strainADate = LocalDate.parse(params.strainADate);
					lineB = Boolean.valueOf(params.lineB);
					escapeBetweenLines = params.escL;

				}

				//initial antibodies
				Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();
				Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();
				configureAntibodies(initialAntibodies, antibodyRefreshFactors, mutEscDelta, mutEscBa1, mutEscBa5, mutEscStrainA, mutEscStrainB, escape, days, strainSeed, strainADate, lineB, escapeBetweenLines);

				AntibodyModel.Config antibodyConfig = new AntibodyModel.Config(initialAntibodies, antibodyRefreshFactors);

				double immuneSigma = 3.0;
				if (params != null) {
					antibodyConfig.setImmuneReponseSigma(immuneSigma);
				}

				bind(AntibodyModel.Config.class).toInstance(antibodyConfig);


				UtilsJR.printInitialAntibodiesToConsole(initialAntibodies, true);

				if (params == null) return;

				// HOUSEHOLD SUSCEPTIBILITY
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
											 double mutEscDelta, double mutEscBa1, double mutEscBa5, double mutEscStrainA, double mutEscStrainB,
											 double escapePerYear, int days, String strainSeed, LocalDate strainADate, boolean lineB, double escapeBetweenLines) {
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


				{

					ArrayList<VirusStrain> strains = getNewStrains(Boolean.valueOf(lineB));

					ArrayList<LocalDate> dates = getDatesNewStrains(strains, days, strainSeed, strainADate);

					for (int i = 0; i < strains.size(); i++) {
						long daysSince = ChronoUnit.DAYS.between(strainADate, dates.get(i));
						double escape = 1. + (escapePerYear - 1.0) * daysSince / 365.; //factor 6, if variant appears 6 months later
						VirusStrain strain = strains.get(i);

						initialAntibodies.get(strain).put(VirusStrain.SARS_CoV_2, 0.01);
						initialAntibodies.get(strain).put(VirusStrain.ALPHA, 0.01);
						initialAntibodies.get(strain).put(VirusStrain.DELTA, 0.01);

						initialAntibodies.get(strain).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / mutEscBa5 / mutEscStrainA / escape);
						initialAntibodies.get(strain).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / mutEscBa5 / mutEscStrainA / escape);
						initialAntibodies.get(strain).put(VirusStrain.OMICRON_BA5, 64.0 / 300./ mutEscStrainA / escape);
						initialAntibodies.get(strain).put(VirusStrain.STRAIN_A, 64.0 / 300. / escape);

						double mRNAStrain = mRNAStrainA / escape;
						initialAntibodies.get(VaccinationType.mRNA).put(strain, mRNAStrain);
						initialAntibodies.get(VaccinationType.vector).put(strain, mRNAStrain * 4./20.);
						initialAntibodies.get(VirusStrain.SARS_CoV_2).put(strain, mRNAStrain * 6./20.);
						initialAntibodies.get(VirusStrain.ALPHA).put(strain, mRNAStrain * 6./20.);
						initialAntibodies.get(VirusStrain.DELTA).put(strain,  mRNAStrain * 8./20.);

						initialAntibodies.get(VirusStrain.OMICRON_BA1).put(strain,  64.0 / 300. / mutEscBa5 /mutEscStrainA / escape);
						initialAntibodies.get(VirusStrain.OMICRON_BA2).put(strain, 64.0 / 300./ mutEscBa5 /mutEscStrainA / escape);
						initialAntibodies.get(VirusStrain.OMICRON_BA5).put(strain, 64.0 / 300. / mutEscStrainA / escape);
						initialAntibodies.get(VirusStrain.STRAIN_A).put(strain, 64.0 / 300. / escape);
	//					initialAntibodies.get(VirusStrain.STRAIN_B).put(strain, 64.0 / 300. / mutEscStrainA / mutEscStrainB / mutEscBa5 / escape);
						initialAntibodies.get(VaccinationType.ba1Update).put(strain, mRNAAlpha / mutEscBa5 / mutEscStrainA / escape);
						initialAntibodies.get(VaccinationType.ba5Update).put(strain, mRNAAlpha / mutEscStrainA / escape);


						for (int j = 0; j < strains.size(); j++) {
							LocalDate date1 = dates.get(i);
							LocalDate date2 = dates.get(j);
							long daysBetweenStrains = Math.abs(ChronoUnit.DAYS.between(date1, date2));
							double escapeBetweenStrains = 1. + (escapePerYear - 1.0) * daysBetweenStrains / 365.; //factor 6, if variant appears 6 months later
							VirusStrain strain2 = strains.get(j);

							if (strain.toString().charAt(0) != strain2.toString().charAt(0))
								escapeBetweenStrains = escapeBetweenStrains * escapeBetweenLines;

							initialAntibodies.get(strain).put(strain2, 64.0 / 300. / escapeBetweenStrains);
						}

					}

				}



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
			.setFutureVacations(params != null ? params.futureVacations : SnzCologneProductionScenario.FutureVacations.no)
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
			if (runCount == 0 && Boolean.parseBoolean(params.lineB) && params.esc == 12. && params.escL == 6.0 ) { //&& params.strAEsc != 0.0 && params.ba5Inf == 0. && params.eduTest.equals("true")) {
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

		//---------------------------------------
		//		S N A P S H O T
		//---------------------------------------

		// create snapshot
		episimConfig.setSnapshotInterval(900);
		episimConfig.setSnapshotPrefix(String.valueOf(params.seed));
		// start from snapshot
//		episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-cologne-2022-10-27/" + params.seed + "-900-2022-08-12.zip");
//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.restore);
		//---------------------------------------
		//		S T R A I N S
		//---------------------------------------

		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		double ba5Inf = virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).getInfectiousness();
		double ba5Hos = virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).getFactorSeriouslySick();

//		STRAIN_A
		if (!params.StrainA.equals("off")) {

			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setInfectiousness(ba5Inf);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(ba5Hos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySickVaccinated(ba5Hos);
			virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorCritical(ba5Hos);
		}

		for (VirusStrain strain : getNewStrains(Boolean.valueOf(params.lineB))) {
			virusStrainConfigGroup.getOrAddParams(strain).setInfectiousness(ba5Inf);
			virusStrainConfigGroup.getOrAddParams(strain).setFactorSeriouslySick(ba5Hos);
			virusStrainConfigGroup.getOrAddParams(strain).setFactorSeriouslySickVaccinated(ba5Hos);
			virusStrainConfigGroup.getOrAddParams(strain).setFactorCritical(ba5Hos);
		}

		//---------------------------------------
		//		I M P O R T
		//---------------------------------------

		configureFutureDiseaseImport(params, episimConfig);

		// modify import:
		LocalDate impModDate = LocalDate.parse("2022-01-31");
		double impRedBa1 = 0.0;
		double impRedBa2 = 0.0;
		if (impRedBa1 != 1.0) {
			NavigableMap<LocalDate, Integer> impBa1 = episimConfig.getInfections_pers_per_day().get(VirusStrain.OMICRON_BA1);
			for (Entry<LocalDate, Integer> entry : impBa1.entrySet()) {
				if (entry.getKey().isAfter(impModDate)) {
					impBa1.put(entry.getKey(), (int) (entry.getValue() * impRedBa1));
				}
			}
		}

		if (impRedBa2 != 1.0) {
			NavigableMap<LocalDate, Integer> impBa2 = episimConfig.getInfections_pers_per_day().get(VirusStrain.OMICRON_BA2);
			for (Entry<LocalDate, Integer> entry : impBa2.entrySet()) {
				if (entry.getKey().isAfter(impModDate)) {
					impBa2.put(entry.getKey(), (int) (entry.getValue() * impRedBa2));
				}
			}
		}



		//---------------------------------------
		//		R E S T R I C T I O N S
		//---------------------------------------

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());


		// vary amount of "school" activity that takes place during vacation
		builder.restrict(LocalDate.parse("2022-06-27"), 0.8, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");

		// increase work, leisure Rf after SNZ Data runs out
		if (params.rf2023.equals("base")) {
			builder.restrict(LocalDate.parse("2023-01-01"), 0.73, "work", "leisure", "leisPublic", "leisPrivate");
		} else if (params.rf2023.equals("leis")) {
			builder.restrict(LocalDate.parse("2023-01-01"), 0.73, "work");
			builder.interpolate(LocalDate.parse("2023-01-01"), LocalDate.parse("2023-03-31"), Restriction.of(0.73), Restriction.of(1.0), "leisure", "leisPublic", "leisPrivate");
		} else if (params.rf2023.equals("work_leis")) {
			builder.interpolate(LocalDate.parse("2023-01-01"), LocalDate.parse("2023-03-31"), Restriction.of(0.73), Restriction.of(1.0), "work", "leisure", "leisPublic", "leisPrivate");
		} else {
			throw new RuntimeException();
		}


		episimConfig.setPolicy(builder.build());


		//---------------------------------------
		//		M I S C
		//---------------------------------------

		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setUseIgA(Boolean.valueOf(params.iga));

		if (!Boolean.valueOf(params.seasonal)) {

			Map<LocalDate, Double> fractionsOld = episimConfig.getLeisureOutdoorFraction();
			Map<LocalDate, Double> fractionsNew = new HashMap<LocalDate, Double>();

			for (Entry<LocalDate, Double> e : fractionsOld.entrySet()) {
				if (e.getKey().isBefore(LocalDate.parse("2022-12-01")))
					fractionsNew.put(e.getKey(), e.getValue());

			}
			episimConfig.setLeisureOutdoorFraction(fractionsNew);
		}


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
//		Map<LocalDate, Integer> infPerDayStrB = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.STRAIN_B, new TreeMap<>()));

		//StrainA
		if (!params.StrainA.equals("off")) {
			infPerDayStrA.put(LocalDate.parse("2020-01-01"), 0);
//			LocalDate strADate = LocalDate.parse("2022-11-01");
			LocalDate strADate = LocalDate.parse(params.strainADate);

			for (int i = 0; i < 7; i++) {
				infPerDayStrA.put(strADate.plusDays(i), 4);
			}
			infPerDayStrA.put(strADate.plusDays(7), 1);
		}


		for (int i = 0; i < getNewStrains(Boolean.valueOf(params.lineB)).size(); i++) {
			LocalDate date = getDatesNewStrains(getNewStrains(Boolean.valueOf(params.lineB)), params.days, params.strainRnd, LocalDate.parse(params.strainADate)).get(i);
			VirusStrain strain = getNewStrains(Boolean.valueOf(params.lineB)).get(i);

			Map<LocalDate, Integer> infPerDayStrainX = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(strain, new TreeMap<>()));
			infPerDayStrainX.put(LocalDate.parse("2020-01-01"), 0);
			for (int j = 0; j < 7; j++) {
				infPerDayStrainX.put(date.plusDays(j), 4);
			}
			infPerDayStrainX.put(date.plusDays(7), 1);
			episimConfig.setInfections_pers_per_day(strain, infPerDayStrainX);
		}

		// save disease import
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA1, infPerDayBa1);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA2, infPerDayBa2);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA5, infPerDayBa5);

		if (!params.StrainA.equals("off")) {
			episimConfig.setInfections_pers_per_day(VirusStrain.STRAIN_A, infPerDayStrA);
		}
//		if (!params.StrainB.equals("off")) {
//			episimConfig.setInfections_pers_per_day(VirusStrain.STRAIN_B, infPerDayStrB);
//		}
	}

	public static final class Params {
		// general
		@GenerateSeeds(5)
		public long seed;

		// future vacations
		@EnumParameter(SnzCologneProductionScenario.FutureVacations.class)
		public SnzCologneProductionScenario.FutureVacations futureVacations;

		// Vaccination Campaign
		@StringParameter({"base"})
		String vacCamp;

		@StringParameter({"1.7"})
		public String StrainA;

//		@StringParameter({"2022-08-15", "2022-08-22", "2022-08-29", "2022-09-05", "2022-09-12", "2022-09-19", "2022-09-26"})
		@StringParameter({"2022-09-12"})
		public String strainADate;

		@Parameter({6., 12., 18., 24.})
		public double esc;

		@Parameter({1.})//, 6.})
		public double escL;

		@IntParameter({30, 90})
		public int days;

		@StringParameter({"no"})
		public String strainRnd;

		@StringParameter({"false"})
		public String lineB;

		@StringParameter({"true", "false"})
		public String iga;

		@StringParameter({"true", "false"})
		public String seasonal;


		@StringParameter({"base", "leis", "work_leis"})
		public String rf2023;

	}


	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneBMBF202310XX_soup.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(1000),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}

	private static ArrayList<VirusStrain> getNewStrains(boolean lineB) {
		ArrayList<VirusStrain> strains = new ArrayList<VirusStrain>();
		strains.add(VirusStrain.A_1);
		strains.add(VirusStrain.A_2);
		strains.add(VirusStrain.A_3);
		strains.add(VirusStrain.A_4);
		strains.add(VirusStrain.A_5);
		strains.add(VirusStrain.A_6);
		strains.add(VirusStrain.A_7);
		strains.add(VirusStrain.A_8);
		strains.add(VirusStrain.A_9);
		strains.add(VirusStrain.A_10);
		strains.add(VirusStrain.A_11);
		strains.add(VirusStrain.A_12);
		strains.add(VirusStrain.A_13);
		strains.add(VirusStrain.A_14);
		strains.add(VirusStrain.A_15);
		strains.add(VirusStrain.A_16);
		strains.add(VirusStrain.A_17);
		strains.add(VirusStrain.A_18);
		strains.add(VirusStrain.A_19);
		strains.add(VirusStrain.A_20);


		if (lineB) {
			strains.add(1, VirusStrain.B_1);
			strains.add(3, VirusStrain.B_2);
			strains.add(5, VirusStrain.B_3);
			strains.add(7, VirusStrain.B_4);
			strains.add(9, VirusStrain.B_5);
			strains.add(11, VirusStrain.B_6);
			strains.add(13, VirusStrain.B_7);
			strains.add(15, VirusStrain.B_8);
			strains.add(17, VirusStrain.B_9);
			strains.add(19, VirusStrain.B_10);
			strains.add(21, VirusStrain.B_11);
			strains.add(23, VirusStrain.B_12);
			strains.add(25, VirusStrain.B_13);
			strains.add(27, VirusStrain.B_14);
			strains.add(29, VirusStrain.B_15);
			strains.add(31, VirusStrain.B_16);
			strains.add(33, VirusStrain.B_17);
			strains.add(35, VirusStrain.B_18);
			strains.add(37, VirusStrain.B_19);
			strains.add(39, VirusStrain.B_20);
		}

		return strains;
	}

	private static ArrayList<LocalDate> getDatesNewStrains(ArrayList<VirusStrain> strains, int days, String seed, LocalDate start) {
		ArrayList<LocalDate> dates = new ArrayList<LocalDate>();

		if (seed.equals("no")) {
			for (LocalDate date = start; ; date = date.plusDays(1)) {
				long daysBetween = ChronoUnit.DAYS.between(start, date);
				if (daysBetween % days == 0)
					dates.add(date);
				if (dates.size() == strains.size())
					break;
			}
			return dates;
		}

		else {
			Random rand = new Random(Integer.parseInt(seed));
			for (LocalDate date = LocalDate.parse("2022-11-15"); ; date = date.plusDays(1)) {
				if (rand.nextDouble() < 1. / days)
					dates.add(date);
				if (dates.size() == strains.size())
					break;
			}
			return dates;
		}

	}


}


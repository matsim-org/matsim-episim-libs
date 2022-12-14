package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
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
import java.util.*;


/**
 * Batch for Bmbf runs
 */
public class CologneBMBF202212XX_bq1 implements BatchRun<CologneBMBF202212XX_bq1.Params> {

	boolean DEBUG_MODE = false;

	String START_DATE = "2022-04-01";
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

				double mutEscStrainA = 0.;
				double mutEscStrainB = 0.;


				if (params != null) {

					mutEscStrainA = Double.parseDouble(params.StrainA);

				}

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
				new VaccinationEffectiveness().withArgs("--start-date", START_DATE),
				new RValuesFromEvents().withArgs("--start-date", START_DATE),
//				new VaccinationEffectivenessFromPotentialInfections().withArgs("--remove-infected"),
				new FilterEvents().withArgs("--output", "./output/"),
				new HospitalNumbersFromEvents().withArgs("--output", "./output/", "--input", "/scratch/projects/bzz0020/episim-input", "--start-date", START_DATE)
//				new SecondaryAttackRateFromEvents().withArgs()
		);
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		if (DEBUG_MODE) {
			if (runCount == 0) {
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


		// start from immunization history
		episimConfig.setStartDate(LocalDate.parse(START_DATE));

		if (params.startFromImm.equals("sepSeeds")) {
			episimConfig.setStartFromImmunization("/scratch/projects/bzz0020/episim-input/imm-cologne-2022-11-24/" + params.seed + "-events_reduced.tar");
		} else if (params.startFromImm.equals("4711")) {
			episimConfig.setStartFromImmunization("/scratch/projects/bzz0020/episim-input/imm-cologne-2022-11-24/4711-events_reduced.tar");
		} else {
			throw new RuntimeException("invalid param");
		}

		//---------------------------------------
		//		S T R A I N S
		//---------------------------------------

		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		double ba5Inf = virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).getInfectiousness();
		double ba5Hos = virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).getFactorSeriouslySick();


		virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setInfectiousness(ba5Inf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySick(ba5Hos);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorSeriouslySickVaccinated(ba5Hos);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.STRAIN_A).setFactorCritical(ba5Hos);

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
			for (Map.Entry<LocalDate, Integer> entry : impBa1.entrySet()) {
				if (entry.getKey().isAfter(impModDate)) {
					impBa1.put(entry.getKey(), (int) (entry.getValue() * impRedBa1));
				}
			}
		}

		if (impRedBa2 != 1.0) {
			NavigableMap<LocalDate, Integer> impBa2 = episimConfig.getInfections_pers_per_day().get(VirusStrain.OMICRON_BA2);
			for (Map.Entry<LocalDate, Integer> entry : impBa2.entrySet()) {
				if (entry.getKey().isAfter(impModDate)) {
					impBa2.put(entry.getKey(), (int) (entry.getValue() * impRedBa2));
				}
			}
		}



		//---------------------------------------
		//		R E S T R I C T I O N S
		//---------------------------------------

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());



		//ifsg
		if ("base".equals(params.ifsg)) {

		} else if ("45".equals(params.ifsg) || "90".equals(params.ifsg)) {
			double compliance = Double.parseDouble(params.ifsg) / 100.;
			builder.restrict(restrictionDatePhase1, Restriction.ofMask(Map.of(
							FaceMask.CLOTH, 0.0,
							FaceMask.SURGICAL, compliance)),
					"educ_secondary", "educ_tertiary", "educ_other", "educ_higher");
			builder.restrict(restrictionDatePhase1, Restriction.ofMask(Map.of(FaceMask.N95, compliance)), "leisPublic");
			builder.restrict(restrictionDatePhase1, Restriction.ofMask(Map.of(FaceMask.N95, compliance)), "shop_daily", "shop_other", "errands");
			builder.restrict(restrictionDatePhase1, Restriction.ofMask(Map.of(FaceMask.N95, 0.9)), "pt"); // pt has 90 compliance either way
		} else {
			throw new RuntimeException();
		}

		// EMERGENCY RESTRICTIONS
		//work
		builder.restrict(LocalDate.parse("2022-10-15"), 0.88, "work", "business");
		double homeOfficeFactor = 0.5;
		switch (params.work) {
			case "base":
				break;
			case "half":
				builder.restrict(restrictionDatePhase2, 0.88 * homeOfficeFactor, "work"); // dont include business bc harder to do from home office
				builder.applyToRf(restrictionDatePhase2.plusDays(1).toString(), restrictionDatePhase2.plusDays(1000).toString(), (d, rf) -> rf * homeOfficeFactor, "work");
				break;
			case "half&mask":
				builder.restrict(restrictionDatePhase2, 0.88 * homeOfficeFactor, "work"); // dont include business bc harder to do from home office
				builder.applyToRf(restrictionDatePhase2.plusDays(1).toString(), restrictionDatePhase2.plusDays(1000).toString(), (d, rf) -> rf * homeOfficeFactor, "work");

				builder.restrict(restrictionDatePhase2, Restriction.ofMask(Map.of(FaceMask.SURGICAL, 0.0, FaceMask.N95, 0.9)), "work", "business");
				break;
			default:
				throw new RuntimeException("invalid parameter");
		}

		// leisure public + private
		switch (params.leis) {
			case "base":
				break;
			case "pub50":
				builder.restrict(restrictionDatePhase2, 0.88 * 0.5, "leisPublic");
				break;
			case "pubPriv50":
				builder.restrict(restrictionDatePhase2, 0.88 * 0.5, "leisPublic", "leisPrivate");
				break;
			default:
				throw new RuntimeException("invalid parameter");
		}

		//school
		switch (params.edu) {
			case "base":
				break;
			case "mask":
				builder.restrict(restrictionDatePhase2, Restriction.ofMask(Map.of(
								FaceMask.CLOTH, 0.0,
								FaceMask.SURGICAL, 0.0,
								FaceMask.N95, 0.90)),
						"educ_secondary", "educ_tertiary", "educ_other", "educ_higher");
				break;
			case "half&mask":
				builder.restrict(restrictionDatePhase2, Restriction.ofMask(Map.of(
								FaceMask.CLOTH, 0.0,
								FaceMask.SURGICAL, 0.0,
								FaceMask.N95, 0.90)),
						"educ_secondary", "educ_tertiary", "educ_other", "educ_higher");

				builder.restrict(restrictionDatePhase2, 0.5, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other",  "educ_higher");
				builder.applyToRf(restrictionDatePhase2.plusDays(1).toString(), restrictionDatePhase2.plusDays(1000).toString(), (d, rf) -> Math.min(0.5, rf), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other",  "educ_higher");
				break;
			default:
				throw new RuntimeException("invalid parameter");
		}

		if ("base".equals(params.maskPt)) {
		} else {
			builder.restrict(LocalDate.parse(params.maskPt),
					Restriction.ofMask(Map.of(
							FaceMask.CLOTH, 0.0,
							FaceMask.SURGICAL, 0.0,
							FaceMask.N95, 0.0)),
					"pt");
		}


		// vary amount of "school" activity that takes place during vacation
		builder.restrict(LocalDate.parse("2022-06-27"), 0.8, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");

		episimConfig.setPolicy(builder.build());


		//---------------------------------------
		//		M I S C
		//---------------------------------------


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

		//StrainA
		if (!params.StrainA.equals("off")) {
			infPerDayStrA.put(LocalDate.parse("2020-01-01"), 0);
			LocalDate strADate = LocalDate.parse(params.strainADate);

			for (int i = 0; i < 7; i++) {
				infPerDayStrA.put(strADate.plusDays(i), 4);
			}
			infPerDayStrA.put(strADate.plusDays(7), 1);
		}

		// save disease import
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA1, infPerDayBa1);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA2, infPerDayBa2);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA5, infPerDayBa5);

		if (!params.StrainA.equals("off")) {
			episimConfig.setInfections_pers_per_day(VirusStrain.STRAIN_A, infPerDayStrA);
		}
	}

	public static final class Params {
		// general
		@GenerateSeeds(20)
		public long seed;

		@StringParameter({"sepSeeds", "4711"})
		public String startFromImm;

		// BQ 1
//		@StringParameter({"off", "2.0", "2.25", "2.5", "2.75", "3.0"})
//		@StringParameter({"1.0", "1.2", "1.4", "1.6", "1.8", "2.0"})
		@StringParameter({"2.0"})
		public String StrainA;

//		@StringParameter({"base", "2022-11-15", "2022-12-01", "2022-12-15", "2023-01-01"})
		@StringParameter({"base"})
		public String maskPt;




//		@StringParameter({"2022-08-24", "2022-08-29", "2022-09-04", "2022-09-09", "2022-09-14", "2022-09-19"})
		@StringParameter({"2022-09-19"})
		public String strainADate;


		//IFSG
		@StringParameter({"base"})
		public String ifsg;

		// Vaccination Campaign
		@StringParameter({"base"})
		String vacCamp;

		// NEW RESTRICTIONS
		@StringParameter({"base"})
		public String work;

		// leisure Public
		@StringParameter({"base"})
		public String leis;

		//edu
		@StringParameter({"base"})
		public String edu;



	}


	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneBMBF202212XX_bq1.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(1000),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


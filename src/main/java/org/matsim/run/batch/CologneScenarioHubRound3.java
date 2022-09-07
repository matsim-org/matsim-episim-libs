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
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.model.vaccination.VaccinationStrategyReoccurringCampaigns;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


/**
 * ROUND 3
 */
public class CologneScenarioHubRound3 implements BatchRun<CologneScenarioHubRound3.Params> {

	boolean DEBUG_MODE = false;
	int runCount = 0;




	Map<LocalDate,VirusStrain> newVirusStrains = new HashMap<>();
	Map<LocalDate, VaccinationType> newVaccinations = new HashMap<>();

	public CologneScenarioHubRound3() {

		newVirusStrains.put(LocalDate.of(2022,10,15),VirusStrain.STRAIN_A);
		newVirusStrains.put(LocalDate.of(2023,7,15),VirusStrain.STRAIN_B);
		newVirusStrains.put(LocalDate.of(2024,4,15),VirusStrain.STRAIN_C);
		newVirusStrains.put(LocalDate.of(2025,1,15),VirusStrain.STRAIN_D);
		newVirusStrains.put(LocalDate.of(2025,10,15),VirusStrain.STRAIN_E);
		newVirusStrains.put(LocalDate.of(2026,7,15),VirusStrain.STRAIN_F);
		newVirusStrains.put(LocalDate.of(2027,4,15),VirusStrain.STRAIN_G);
		newVirusStrains.put(LocalDate.of(2028,1,15),VirusStrain.STRAIN_H);
		newVirusStrains.put(LocalDate.of(2028,10,15),VirusStrain.STRAIN_I);
		newVirusStrains.put(LocalDate.of(2029,7,15),VirusStrain.STRAIN_J);
		newVirusStrains.put(LocalDate.of(2030,4,15),VirusStrain.STRAIN_K);
		newVirusStrains.put(LocalDate.of(2031,1,15),VirusStrain.STRAIN_L);
		newVirusStrains.put(LocalDate.of(2031,10,15),VirusStrain.STRAIN_M);
		newVirusStrains.put(LocalDate.of(2032,7,15),VirusStrain.STRAIN_N);

		newVaccinations.put(LocalDate.of(2022,9,1),VaccinationType.fall22);
		newVaccinations.put(LocalDate.of(2023,3,1),VaccinationType.spring23);
		newVaccinations.put(LocalDate.of(2023,9,1),VaccinationType.fall23);
		newVaccinations.put(LocalDate.of(2024,3,1),VaccinationType.spring24);
		newVaccinations.put(LocalDate.of(2024,9,1),VaccinationType.fall24);
		newVaccinations.put(LocalDate.of(2025,3,1),VaccinationType.spring25);
		newVaccinations.put(LocalDate.of(2025,9,1),VaccinationType.fall25);
		newVaccinations.put(LocalDate.of(2026,3,1),VaccinationType.spring26);
		newVaccinations.put(LocalDate.of(2026,9,1),VaccinationType.fall26);
		newVaccinations.put(LocalDate.of(2027,3,1),VaccinationType.spring27);
		newVaccinations.put(LocalDate.of(2027,9,1),VaccinationType.fall27);
		newVaccinations.put(LocalDate.of(2028,3,1),VaccinationType.spring28);
		newVaccinations.put(LocalDate.of(2028,9,1),VaccinationType.fall28);
		newVaccinations.put(LocalDate.of(2029,3,1),VaccinationType.spring29);
		newVaccinations.put(LocalDate.of(2029,9,1),VaccinationType.fall29);
		newVaccinations.put(LocalDate.of(2030,3,1),VaccinationType.spring30);
		newVaccinations.put(LocalDate.of(2030,9,1),VaccinationType.fall30);
		newVaccinations.put(LocalDate.of(2031,3,1),VaccinationType.spring31);
		newVaccinations.put(LocalDate.of(2031,9,1),VaccinationType.fall31);
		newVaccinations.put(LocalDate.of(2032,3,1),	VaccinationType.spring32);
	}

	@Nullable
	@Override
	public Module getBindings(int id, @Nullable Params params) {
		return Modules.override(getBindings(0.0, params)).with(new AbstractModule() {
			@Override
			protected void configure() {

				Multibinder<VaccinationModel> set = Multibinder.newSetBinder(binder(), VaccinationModel.class);

				set.addBinding().to(VaccinationStrategyReoccurringCampaigns.class).in(Singleton.class);

				double mutEscBa5 = 3.0;
				double mutEscStrainX = 3.0;


				Map<LocalDate,VaccinationType> startDateToVaccination = new HashMap<>();

				Int2DoubleMap compliance = new Int2DoubleAVLTreeMap();
				compliance.put(60, 0.0);
				compliance.put(18, 0.0);
				compliance.put(12, 0.0);
				compliance.put(0, 0.0);

				if (params != null) {

					mutEscStrainX = params.mutEsc;

					if (params.vacFreq.equals("none")) {
						startDateToVaccination.put(LocalDate.of(2022, 9, 15), newVaccinations.get(LocalDate.of(2022, 9, 15)));
					} else if (params.vacFreq.equals("annual")) {
						startDateToVaccination.putAll(newVaccinations.entrySet().stream().filter(entry -> entry.getKey().getMonthValue() == 9).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
					} else if (params.vacFreq.equals("biannual")) {
						startDateToVaccination.putAll(newVaccinations);
					} else {
						throw new RuntimeException();
					}

					if (params.vacCamp.equals("60plus")) {
						compliance.put(60, 0.94/2); // 0.94 is boost rate July 16, 2022
						compliance.put(18, 0.);
						compliance.put(12, 0.);
						compliance.put(0, 0.);
					}
					// assumption: older age group 2boosted first, then younger, each age group
					// will have rate of 50% 2boosted by end of campaign.
					// motivation: if we give both age groups same rate, then the older people
					// will not be boosted as much as younger people, which seems implausible...
					else if (params.vacCamp.equals("18plus")) {
						compliance.put(60, 0.94/2); // 0.94 is boost rate July 16, 2022
						compliance.put(18, 0.77/2); // 0.77 is boost rate July 16, 2022
						compliance.put(12, 0.);
						compliance.put(0, 0.);
					}
					else if (params.vacCamp.equals("off")) {

					} else {
						throw new RuntimeException("Not a valid option for vaccinationCampaignType");
					}
				}

				int campaignDuration = 91;

//				if (DEBUG_MODE) {
//					campaignDuration = 5;
//				}

				bind(VaccinationStrategyReoccurringCampaigns.Config.class).toInstance(new VaccinationStrategyReoccurringCampaigns.Config(startDateToVaccination, campaignDuration, compliance));

				//initial antibodies
				Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();
				Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();
				configureAntibodies(initialAntibodies, antibodyRefreshFactors, mutEscBa5, mutEscStrainX);

				AntibodyModel.Config antibodyConfig = new AntibodyModel.Config(initialAntibodies, antibodyRefreshFactors);

				double immuneSigma = 3.0;
				if (params != null) {
					antibodyConfig.setImmuneReponseSigma(immuneSigma);
				}

				bind(AntibodyModel.Config.class).toInstance(antibodyConfig);

				if (DEBUG_MODE && params != null) {
					UtilsJR.produceAntibodiesCsv(initialAntibodies);

				}

			}

			private void configureAntibodies(Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies,
											 Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors,
											 double mutEscBa5, double mutEscStrainX) {

				for (VaccinationType immunityType : VaccinationType.values()) {
					initialAntibodies.put(immunityType, new EnumMap<>(VirusStrain.class));
					for (VirusStrain virusStrain : VirusStrain.values()) {

						if (immunityType == VaccinationType.mRNA) {
							initialAntibodies.get(immunityType).put(virusStrain, 29.2); //10.0
						} else if (immunityType == VaccinationType.vector) {
							initialAntibodies.get(immunityType).put(virusStrain, 6.8);  //2.5
						} else {
							initialAntibodies.get(immunityType).put(virusStrain, 5.0);
						}
					}
				}

				for (VirusStrain immunityType : VirusStrain.values()) {
					initialAntibodies.put(immunityType, new EnumMap<>(VirusStrain.class));
					for (VirusStrain virusStrain : VirusStrain.values()) {
						initialAntibodies.get(immunityType).put(virusStrain, 5.0);
					}
				}


				//mRNAAlpha, mRNADelta, mRNABA1 comes from Sydney's calibration.
				//The other values come from Rössler et al.
				//Wildtype
				double mRNAAlpha = 29.2;

				// initialAntibodies.get(IMMUNITY GIVER).put(IMMUNITY AGAINST, ab level);
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.SARS_CoV_2, mRNAAlpha);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.SARS_CoV_2, 0.01);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.SARS_CoV_2, 0.01);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.SARS_CoV_2, 0.01);


				//Alpha
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.ALPHA, mRNAAlpha);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.ALPHA, 0.01);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.ALPHA, 0.01);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.ALPHA, 0.01);

				//DELTA
				double mRNADelta = 10.9;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.DELTA, mRNADelta);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.DELTA, mRNADelta * 150. / 300.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.DELTA, mRNADelta * 64. / 300.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.DELTA, mRNADelta * 64. / 300.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.DELTA, mRNADelta * 450. / 300.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.DELTA, 0.2 / 6.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.DELTA, 0.2 / 6.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.DELTA, 0.2 / 6.4);


				//BA.1
				double mRNABA1 = 1.9;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA1, mRNABA1);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA1, mRNABA1 * 4. / 20.); //???
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6. / 20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6. / 20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 8. / 20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA1, 64.0 / 300.);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4); //todo: is 1.4

				//BA.2
				double mRNABA2 = mRNABA1;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA2, mRNABA2);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA2, mRNABA2 * 4. / 20.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6. / 20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6. / 20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 8. / 20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / 1.4);
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA2, 64.0 / 300.);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / 1.4);


				//BA.5
				double mRNABa5 = mRNABA2 / mutEscBa5;
				initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA5, mRNABa5);
				initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA5, mRNABa5 * 4. / 20.);
				initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6. / 20.);
				initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6. / 20.);
				initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA5, mRNABa5 * 8. / 20.);
				initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA5, 64.0 / 300. / 1.4 / mutEscBa5);// todo: do we need 1.4?
				initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA5, 64.0 / 300. / mutEscBa5);
				initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA5, 64.0 / 300.);

				// NEW STRAINS

				// A) all "new/updated/novel" vaccinations and infections give only 0.01 protection against "old" strains

				for (VirusStrain protectionAgainst : List.of(VirusStrain.SARS_CoV_2, VirusStrain.ALPHA, VirusStrain.DELTA, VirusStrain.OMICRON_BA1, VirusStrain.OMICRON_BA2, VirusStrain.OMICRON_BA5)) {
					for (ImmunityEvent vax : newVaccinations.values()) {
						initialAntibodies.get(vax).put(protectionAgainst, 0.01);
					}

					for (ImmunityEvent strain : newVirusStrains.values()) {
						initialAntibodies.get(strain).put(protectionAgainst, 0.01);
					}
				}

				// B) "old" vaccinations and infections give the same protection to StrainX as to BA5 PLUS an escape
				double mRNAStrainX = mRNABa5 / mutEscStrainX;

				for (VirusStrain newStrain : newVirusStrains.values()) {
					initialAntibodies.get(VaccinationType.mRNA).put(newStrain, mRNAStrainX);
					initialAntibodies.get(VaccinationType.vector).put(newStrain, mRNAStrainX * 4. / 20.);
					initialAntibodies.get(VirusStrain.SARS_CoV_2).put(newStrain, mRNAStrainX * 6. / 20.);
					initialAntibodies.get(VirusStrain.ALPHA).put(newStrain, mRNAStrainX * 6. / 20.);
					initialAntibodies.get(VirusStrain.DELTA).put(newStrain, mRNAStrainX * 8. / 20.);

					initialAntibodies.get(VirusStrain.OMICRON_BA1).put(newStrain, 64.0 / 300. / mutEscBa5 / mutEscStrainX);
					initialAntibodies.get(VirusStrain.OMICRON_BA2).put(newStrain, 64.0 / 300. / mutEscBa5 / mutEscStrainX);
					initialAntibodies.get(VirusStrain.OMICRON_BA5).put(newStrain, 64.0 / 300. / mutEscStrainX);

				}

				// C) Immunity between the novel StrainsX:
				// Newer Strains give full (non-escaped) protection against themselves AND previous strains
				// New Strains give escaped protection against future strainA

				// initialAntibodies.get(IMMUNITY GIVER).put(IMMUNITY AGAINST, ab level);

				for (LocalDate dateProtectionGiver : newVirusStrains.keySet()) {

					for (LocalDate dateProtectionAgainst : newVirusStrains.keySet()) {

						if (dateProtectionGiver.equals(dateProtectionAgainst) || dateProtectionGiver.isAfter(dateProtectionAgainst)) {
							// Newer Strains give full (non-escaped) protection against themselves AND previous strains
							initialAntibodies.get(newVirusStrains.get(dateProtectionGiver)).put(newVirusStrains.get(dateProtectionAgainst), mRNAAlpha);

						} else {
							// New Strains give escaped protection against future strainA
							initialAntibodies.get(newVirusStrains.get(dateProtectionGiver)).put(newVirusStrains.get(dateProtectionAgainst), mRNAAlpha/ mutEscStrainX);

						}

					}
				}

				// D) Immunity provided by new vaccines against novel StrainsX:
				// Provides baseline immunity if StrainX was spawned more than 6 months before vaccination campaign begins
				// provides reduced immunity otherwise

				for (LocalDate dateProtectionGiver : newVaccinations.keySet()) {

					for (LocalDate dateProtectionAgainst : newVirusStrains.keySet()) {

						if (dateProtectionGiver.isAfter(dateProtectionAgainst.plusMonths(6))) {
							// Provides baseline immunity if StrainX was spawned more than 6 months before vaccination campaign begins

							initialAntibodies.get(newVaccinations.get(dateProtectionGiver)).put(newVirusStrains.get(dateProtectionAgainst), mRNAAlpha);

						}
						else {
							// provides reduced immunity otherwise
							initialAntibodies.get(newVaccinations.get(dateProtectionGiver)).put(newVirusStrains.get(dateProtectionAgainst), mRNAAlpha/ mutEscStrainX);

						}


					}
				}

				// R E F R E S H    F A C T O R S
				for (VaccinationType immunityType : VaccinationType.values()) {
					antibodyRefreshFactors.put(immunityType, new EnumMap<>(VirusStrain.class));
					for (VirusStrain virusStrain : VirusStrain.values()) {

						if (immunityType == VaccinationType.mRNA) {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
						} else if (immunityType == VaccinationType.vector) {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, 5.0);
						} else if (immunityType == VaccinationType.ba1Update) {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
						} else if (newVaccinations.containsValue(immunityType)) {
							if (newVirusStrains.containsValue(virusStrain)) {
								antibodyRefreshFactors.get(immunityType).put(virusStrain, 1.0);
							} else {
								antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
							}

						} else {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, Double.NaN);
						}

					}
				}

				for (VirusStrain immunityType : VirusStrain.values()) {
					antibodyRefreshFactors.put(immunityType, new EnumMap<>(VirusStrain.class));
					for (VirusStrain virusStrain : VirusStrain.values()) {
						if (newVirusStrains.containsValue(immunityType) && newVirusStrains.containsValue(virusStrain)) {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, 1.0);
						} else {
							antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
						}
					}
				}
			}
		});

	}

	private SnzCologneProductionScenario getBindings(double pHousehold, Params params) {
		return new SnzCologneProductionScenario.Builder()
				.setLeisureCorrection(0.0)
				.setCarnivalModel(SnzCologneProductionScenario.CarnivalModel.yes)
				.setSebastianUpdate(true)
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

		if (DEBUG_MODE && params.mutEsc == 3.) {

			if (runCount == 0) { //&& params.strAEsc != 0.0 && params.ba5Inf == 0. && params.eduTest.equals("true")) {
				runCount++;
			} else {
				return null;
			}
		}

		SnzCologneProductionScenario module = getBindings(0.0, params);

		Config config = module.config();


		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		// create snapshot
//		episimConfig.setSnapshotInterval(927);
//		episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-cologne-20220218/" + params.seed + "-600-2021-10-16.zip");
//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.restore);

		//mutations
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		//configure new strains
		//BA5
		double ba5Inf = 0.9;
		double oHos = virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getFactorSeriouslySick();
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getInfectiousness() * ba5Inf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySick(oHos);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySickVaccinated(oHos);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorCritical(oHos);

		//StrainA/B/C

		for (VirusStrain strain : newVirusStrains.values()) {
			virusStrainConfigGroup.getOrAddParams(strain).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA5).getInfectiousness());
			virusStrainConfigGroup.getOrAddParams(strain).setFactorSeriouslySick(oHos);
			virusStrainConfigGroup.getOrAddParams(strain).setFactorSeriouslySickVaccinated(oHos);
			virusStrainConfigGroup.getOrAddParams(strain).setFactorCritical(oHos);
		}


		//Configure Disease Import

		configureFutureDiseaseImport(params, episimConfig);


		//vaccinations
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfig.setUseIgA(true);
		vaccinationConfig.setTimePeriodIgA(730.);

		//new vaccinations
		int boostAfter = 3;
		for (VaccinationType vax : List.of(VaccinationType.fall22, VaccinationType.spring23, VaccinationType.fall23, VaccinationType.spring24)) {
			vaccinationConfig.getOrAddParams(vax)
					.setBoostWaitPeriod(boostAfter * 30 + 6 * 7); // todo: does this make sense?
			;
		}


		if (DEBUG_MODE) {
			UtilsJR.produceDiseaseImportPlot(episimConfig.getInfections_pers_per_day());
		}



		return config;
	}

	private void configureFutureDiseaseImport(Params params, EpisimConfigGroup episimConfig) {
		Map<LocalDate, Integer> infPerDayBa2 = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.OMICRON_BA2, new TreeMap<>()));
		Map<LocalDate, Integer> infPerDayBa5 = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(VirusStrain.OMICRON_BA5, new TreeMap<>()));

		// add initial impulses for strains
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

		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA2, infPerDayBa2);
		episimConfig.setInfections_pers_per_day(VirusStrain.OMICRON_BA5, infPerDayBa5);


		//StrainA/B/C
		for (Map.Entry<LocalDate,VirusStrain> entry : newVirusStrains.entrySet()) {
			LocalDate date = entry.getKey();
			VirusStrain strain = entry.getValue();

			Map<LocalDate, Integer> infPerDayStrainX = new HashMap<>(episimConfig.getInfections_pers_per_day().getOrDefault(strain, new TreeMap<>()));
			infPerDayStrainX.put(LocalDate.parse("2020-01-01"), 0);
			for (int i = 0; i < 7; i++) {
				infPerDayStrainX.put(date.plusDays(i), 4);
			}
			infPerDayStrainX.put(date.plusDays(7), 1);
			episimConfig.setInfections_pers_per_day(strain, infPerDayStrainX);
		}

		// add projected disease import for vacation waves after initial disease import
//		int facBa2 = 4;
//		int facBa5 = 4;
		int facStrainX = 4;

		LocalDate dateBa2 = LocalDate.parse("2022-01-27"); // local min of disease import
		LocalDate dateBa5 = LocalDate.parse("2022-05-01"); // after vaca import

		NavigableMap<LocalDate, Double> data = DataUtils.readDiseaseImport(SnzCologneProductionScenario.INPUT.resolve("cologneDiseaseImport_Projected_2032.csv"));
		LocalDate date = null;
		for (Map.Entry<LocalDate, Double> entry : data.entrySet()) {
			date = entry.getKey();
			double factor = 0.25 * 2352476. / 919936.; //25% sample, data is given for Cologne City so we have to scale it to the whole model
//
			double cases = factor * entry.getValue();

			//from most recent to furthest back.
			Map<LocalDate, VirusStrain> reverseSortedVirusStrainMap = new TreeMap<LocalDate, VirusStrain>(Collections.reverseOrder());
			reverseSortedVirusStrainMap.putAll(newVirusStrains);
			reverseSortedVirusStrainMap.put(dateBa5, VirusStrain.OMICRON_BA5);
			reverseSortedVirusStrainMap.put(dateBa2, VirusStrain.OMICRON_BA2);


			// strain map sorted from newest to oldest
			// if current date is after LATEST virus's spawn date + 2 months  -> give the corresponding virusStrain all the import
			// if currents datae is after virusStrainDate -> give the corresponding virusStrain an import of 1.
			// else: current date is before spwan of virus strain -> don't give any import


			boolean firstTimeAfterDate = true;
			for (LocalDate dateStrainX : reverseSortedVirusStrainMap.keySet()) {

				if (date.isAfter(dateStrainX.plusMonths(2)) && firstTimeAfterDate) {
					episimConfig.getInfections_pers_per_day().get(reverseSortedVirusStrainMap.get(dateStrainX)).put(date, ((int) cases * facStrainX) == 0 ? 1 : (int) (cases * facStrainX));
					firstTimeAfterDate = false;
				} else if (date.isAfter(dateStrainX.plusMonths(2))) {
					episimConfig.getInfections_pers_per_day().get(reverseSortedVirusStrainMap.get(dateStrainX)).put(date, 1);
				} else {
					episimConfig.getInfections_pers_per_day().get(reverseSortedVirusStrainMap.get(dateStrainX)).put(date, 0);
				}

			}

//			if (date.isAfter(dateBa5)) {
//				infPerDayBa5.put(date, ((int) cases * facBa5) == 0 ? 1 : (int) (cases * facBa5));
//				infPerDayBa2.put(date, 1);
//			} else if (date.isAfter(dateBa2)) {
//				infPerDayBa2.put(date, ((int) cases * facBa2) == 0 ? 1 : (int) (cases * facBa2));
//			}

		}

//		infPerDayBa5.put(date.plusDays(1), 1);

		// save disease import


	}


	public static final class Params {

		// general
		@GenerateSeeds(3)
		public long seed;


		// vaccination campaign
		//vaccination frequency
		@StringParameter({"none", "annual", "biannual"})
		String vacFreq;

		@StringParameter({"18plus"})
//		@StringParameter({"off", "60plus", "18plus"})
		String vacCamp;


		//new mutations
		@Parameter({3.7, 44.7})
		public double mutEsc;
	}


	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneScenarioHubRound3.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(70),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2DoubleAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.analysis.*;
import org.matsim.episim.model.*;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.model.vaccination.VaccinationStrategyReoccurringCampaigns;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.components.Page;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.table.TableSliceGroup;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


/**
 * ROUND 3
 */
public class CologneScenarioHubRound3 implements BatchRun<CologneScenarioHubRound3.Params> {

	boolean DEBUG_MODE = false;

	int runCount = 0;

	/**
	 * Novel VOCs
	 * Key: Spawn Date
	 * Value: Virus Type
	 */
	public static Map<LocalDate,VirusStrain> newVirusStrains = renderNewVirusStrains();

	/**
	 * Updated Vaccinations
	 * Shows all possible vaccinations, the selection of vaccinations for a given scenario will be done in getBindings()
	 * Key: First day of vaccination campaign
	 * Value: Vaccination Type
	 */
	public static Map<LocalDate, VaccinationType> newVaccinations = renderNewVaccinations();

	private static Map<LocalDate, VirusStrain> renderNewVirusStrains() {
		Map<LocalDate, VirusStrain> newVirusStrains = new TreeMap<>();
		newVirusStrains.put(LocalDate.of(2022,10,1),VirusStrain.STRAIN_A);
		newVirusStrains.put(LocalDate.of(2023,7,1),VirusStrain.STRAIN_B);
		newVirusStrains.put(LocalDate.of(2024,4,1),VirusStrain.STRAIN_C);
		newVirusStrains.put(LocalDate.of(2025,1,1),VirusStrain.STRAIN_D);
		newVirusStrains.put(LocalDate.of(2025,10,1),VirusStrain.STRAIN_E);
		newVirusStrains.put(LocalDate.of(2026,7,1),VirusStrain.STRAIN_F);
		newVirusStrains.put(LocalDate.of(2027,4,1),VirusStrain.STRAIN_G);
		newVirusStrains.put(LocalDate.of(2028,1,1),VirusStrain.STRAIN_H);
		newVirusStrains.put(LocalDate.of(2028,10,1),VirusStrain.STRAIN_I);
		newVirusStrains.put(LocalDate.of(2029,7,1),VirusStrain.STRAIN_J);
		newVirusStrains.put(LocalDate.of(2030,4,1),VirusStrain.STRAIN_K);
		newVirusStrains.put(LocalDate.of(2031,1,1),VirusStrain.STRAIN_L);
		newVirusStrains.put(LocalDate.of(2031,10,1),VirusStrain.STRAIN_M);
		newVirusStrains.put(LocalDate.of(2032,7,1),VirusStrain.STRAIN_N);

		return newVirusStrains;
	}

	private static Map<LocalDate, VaccinationType> renderNewVaccinations() {

		Map<LocalDate, VaccinationType> newVaccinations = new TreeMap<>();
		newVaccinations.put(LocalDate.of(2022,9,15),VaccinationType.fall22);
		newVaccinations.put(LocalDate.of(2023,3,15),VaccinationType.spring23);
		newVaccinations.put(LocalDate.of(2023,9,15),VaccinationType.fall23);
		newVaccinations.put(LocalDate.of(2024,3,15),VaccinationType.spring24);
		newVaccinations.put(LocalDate.of(2024,9,15),VaccinationType.fall24);
		newVaccinations.put(LocalDate.of(2025,3,15),VaccinationType.spring25);
		newVaccinations.put(LocalDate.of(2025,9,15),VaccinationType.fall25);
		newVaccinations.put(LocalDate.of(2026,3,15),VaccinationType.spring26);
		newVaccinations.put(LocalDate.of(2026,9,15),VaccinationType.fall26);
		newVaccinations.put(LocalDate.of(2027,3,15),VaccinationType.spring27);
		newVaccinations.put(LocalDate.of(2027,9,15),VaccinationType.fall27);
		newVaccinations.put(LocalDate.of(2028,3,15),VaccinationType.spring28);
		newVaccinations.put(LocalDate.of(2028,9,15),VaccinationType.fall28);
		newVaccinations.put(LocalDate.of(2029,3,15),VaccinationType.spring29);
		newVaccinations.put(LocalDate.of(2029,9,15),VaccinationType.fall29);
		newVaccinations.put(LocalDate.of(2030,3,15),VaccinationType.spring30);
		newVaccinations.put(LocalDate.of(2030,9,15),VaccinationType.fall30);
		newVaccinations.put(LocalDate.of(2031,3,15),VaccinationType.spring31);
		newVaccinations.put(LocalDate.of(2031,9,15),VaccinationType.fall31);
		newVaccinations.put(LocalDate.of(2032,3,15),VaccinationType.spring32);

		newVaccinations.put(LocalDate.of(2022,10,1),	VaccinationType.vax_STRAIN_A);
		newVaccinations.put(LocalDate.of(2023,7,1),	VaccinationType.vax_STRAIN_B);
		newVaccinations.put(LocalDate.of(2024,4,1),	VaccinationType.vax_STRAIN_C);
		newVaccinations.put(LocalDate.of(2025,1,1),	VaccinationType.vax_STRAIN_D);
		newVaccinations.put(LocalDate.of(2025,10,1),	VaccinationType.vax_STRAIN_E);
		newVaccinations.put(LocalDate.of(2026,7,1),	VaccinationType.vax_STRAIN_F);
		newVaccinations.put(LocalDate.of(2027,4,1),	VaccinationType.vax_STRAIN_G);
		newVaccinations.put(LocalDate.of(2028,1,1),	VaccinationType.vax_STRAIN_H);
		newVaccinations.put(LocalDate.of(2028,10,1),	VaccinationType.vax_STRAIN_I);
		newVaccinations.put(LocalDate.of(2029,7,1),	VaccinationType.vax_STRAIN_J);
		newVaccinations.put(LocalDate.of(2030,4,1),	VaccinationType.vax_STRAIN_K);
		newVaccinations.put(LocalDate.of(2031,1,1),	VaccinationType.vax_STRAIN_L);
		newVaccinations.put(LocalDate.of(2031,10,1),	VaccinationType.vax_STRAIN_M);
		newVaccinations.put(LocalDate.of(2032,7,1),	VaccinationType.vax_STRAIN_N);

		return newVaccinations;
	}

	@Nullable
	@Override
	public Module getBindings(int id, @Nullable Params params) {
		return Modules.override(getBindings(0.0, params)).with(new AbstractModule() {
			@Override
			protected void configure() {

				// VACCINATION MODEL
				Multibinder<VaccinationModel> set = Multibinder.newSetBinder(binder(), VaccinationModel.class);
				set.addBinding().to(VaccinationStrategyReoccurringCampaigns.class).in(Singleton.class);

				// default values (if params==null)
				Map<LocalDate,VaccinationType> startDateToVaccination = new HashMap<>();

				Object2DoubleMap<EpisimReporting.AgeGroup> compliance = new Object2DoubleAVLTreeMap();
				compliance.put(EpisimReporting.AgeGroup.age_60_plus, 0.0);
				compliance.put(EpisimReporting.AgeGroup.age_18_59, 0.0);
				compliance.put(EpisimReporting.AgeGroup.age_12_17, 0.0);
				compliance.put(EpisimReporting.AgeGroup.age_0_11, 0.0);

				VaccinationStrategyReoccurringCampaigns.Config.VaccinationPool vaccinationPool = VaccinationStrategyReoccurringCampaigns.Config.VaccinationPool.vaccinated;

				int campaignDuration = 90;

				if (params != null) {

					campaignDuration = (int) params.campDuration;

					vaccinationPool = VaccinationStrategyReoccurringCampaigns.Config.VaccinationPool.valueOf(params.vacPool);


					if (DEBUG_MODE) {
						startDateToVaccination.put(LocalDate.of(2020, 2, 27), VaccinationType.fall23);
					}


					if (params.vacFreq.equals("none")) {

					} else if (params.vacFreq.equals("fall22")) {
						startDateToVaccination.put(LocalDate.of(2022, 9, 1), newVaccinations.get(LocalDate.of(2022, 9, 1)));
					} else if (params.vacFreq.equals("annual")) {
						startDateToVaccination.putAll(newVaccinations.entrySet().stream().filter(entry -> entry.getValue().toString().startsWith("fall")).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
					} else if (params.vacFreq.equals("biannual")) {
						startDateToVaccination.putAll(newVaccinations.entrySet().stream().filter(entry -> (entry.getValue().toString().startsWith("fall") || entry.getValue().toString().startsWith("spring"))).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
					} else if (params.vacFreq.equals("withStrain")) {
						startDateToVaccination.putAll(newVaccinations.entrySet().stream().filter(entry -> entry.getValue().toString().startsWith("vax")).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
					} else {
						throw new RuntimeException();
					}

					if (params.vacCamp.equals("60plus")) {
						compliance.put(EpisimReporting.AgeGroup.age_60_plus, 0.94/2); // 0.94 is boost rate July 16, 2022
						compliance.put(EpisimReporting.AgeGroup.age_18_59, 0.);
						compliance.put(EpisimReporting.AgeGroup.age_12_17, 0.);
						compliance.put(EpisimReporting.AgeGroup.age_0_11, 0.);
					}
					// assumption: older age group 2boosted first, then younger, each age group
					// will have rate of 50% 2boosted by end of campaign.
					// motivation: if we give both age groups same rate, then the older people
					// will not be boosted as much as younger people, which seems implausible...
					else if (params.vacCamp.equals("18plus")) {
						compliance.put(EpisimReporting.AgeGroup.age_60_plus, 0.94/2); // 0.94 is boost rate July 16, 2022
						compliance.put(EpisimReporting.AgeGroup.age_18_59, 0.77/2); // 0.77 is boost rate July 16, 2022
						compliance.put(EpisimReporting.AgeGroup.age_12_17, 0.);
						compliance.put(EpisimReporting.AgeGroup.age_0_11, 0.);
					} else if (params.vacCamp.equals("18plus50pct")) {
						compliance.put(EpisimReporting.AgeGroup.age_60_plus, 0.5);
						compliance.put(EpisimReporting.AgeGroup.age_18_59, 0.5);
						compliance.put(EpisimReporting.AgeGroup.age_12_17, 0.);
						compliance.put(EpisimReporting.AgeGroup.age_0_11, 0.);
					} else if (params.vacCamp.equals("off")) {

					} else {
						throw new RuntimeException("Not a valid option for vaccinationCampaignType");
					}
				}


				bind(VaccinationStrategyReoccurringCampaigns.Config.class).toInstance(new VaccinationStrategyReoccurringCampaigns.Config(startDateToVaccination, campaignDuration, compliance, vaccinationPool));

				// ANTIBODY MODEL
				// default values
				double mutEscBa5 = 3.0;
				double mutEscStrainX = 3.0;
				//initial antibodies
				Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();
				Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();

				if (params != null) {
					mutEscStrainX = params.mutEsc;
				}

				configureAntibodies(initialAntibodies, antibodyRefreshFactors, mutEscBa5, mutEscStrainX);
				AntibodyModel.Config antibodyConfig = new AntibodyModel.Config(initialAntibodies, antibodyRefreshFactors);
				double immuneSigma = 3.0;
				antibodyConfig.setImmuneReponseSigma(immuneSigma);

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
				new FilterEvents().withArgs("--output","./output/"),
				new HospitalNumbersFromEvents().withArgs()
//				new SecondaryAttackRateFromEvents().withArgs()
		);
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		if (DEBUG_MODE) {

			if (runCount == 0 && params.vacCamp.equals("18plus")) { //&& params.strAEsc != 0.0 && params.ba5Inf == 0. && params.eduTest.equals("true")) {
				runCount++;
			} else {
				return null;
			}
		}

		SnzCologneProductionScenario module = getBindings(0.0, params);

		Config config = module.config();


		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		// create / read snapshot
//		episimConfig.setSnapshotInterval(927);
		episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-cologne-2022-09-16/" + params.seed + "-927-2022-09-08.zip");
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.restore);

		// S T R A I N S
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		//configure new strains
		//BA5
		double ba5Inf = 0.9;
		double oHos = virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getFactorSeriouslySick();
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA2).getInfectiousness() * ba5Inf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySick(oHos);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorSeriouslySickVaccinated(oHos);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.OMICRON_BA5).setFactorCritical(oHos);

		//StrainX
		for (VirusStrain strain : newVirusStrains.values()) {
			virusStrainConfigGroup.getOrAddParams(strain).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.OMICRON_BA5).getInfectiousness());
			virusStrainConfigGroup.getOrAddParams(strain).setFactorSeriouslySick(oHos);
			virusStrainConfigGroup.getOrAddParams(strain).setFactorSeriouslySickVaccinated(oHos);
			virusStrainConfigGroup.getOrAddParams(strain).setFactorCritical(oHos);
		}


		// D I S E A S E    I M P O R T
		configureFutureDiseaseImport(params, episimConfig);


		// V A C C I N A T I O N S
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


		//StrainX
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
//		int facStrainX = 4;
//
//		LocalDate dateBa2 = LocalDate.parse("2022-01-27"); // local min of disease import
//		LocalDate dateBa5 = LocalDate.parse("2022-05-01"); // after vaca import
//
//		NavigableMap<LocalDate, Double> data = DataUtils.readDiseaseImport(SnzCologneProductionScenario.INPUT.resolve("cologneDiseaseImport_Projected_2032.csv"));
//		LocalDate date = null;
//
//		// TODO this overwrites the original import!!!
//		for (Map.Entry<LocalDate, Double> entry : data.entrySet()) {
//			date = entry.getKey();
//			double factor = 0.25 * 2352476. / 919936.; //25% sample, data is given for Cologne City so we have to scale it to the whole model
////
//			double cases = factor * entry.getValue();
//
//			//from most recent to furthest back.
//			Map<LocalDate, VirusStrain> reverseSortedVirusStrainMap = new TreeMap<LocalDate, VirusStrain>(Collections.reverseOrder());
//			reverseSortedVirusStrainMap.putAll(newVirusStrains);
//			reverseSortedVirusStrainMap.put(dateBa5, VirusStrain.OMICRON_BA5);
//			reverseSortedVirusStrainMap.put(dateBa2, VirusStrain.OMICRON_BA2);
//
//
//			// strain map sorted from newest to oldest
//			// if current date is after LATEST virus's spawn date + 2 months  -> give the corresponding virusStrain all the import
//			// if currents datae is after virusStrainDate -> give the corresponding virusStrain an import of 1.
//			// else: current date is before spwan of virus strain -> don't give any import
//
//
//			boolean firstTimeAfterDate = true;
//			for (LocalDate dateStrainX : reverseSortedVirusStrainMap.keySet()) {
//
//				if (date.isAfter(dateStrainX.plusMonths(2)) && firstTimeAfterDate) {
//					episimConfig.getInfections_pers_per_day().get(reverseSortedVirusStrainMap.get(dateStrainX)).put(date, ((int) cases * facStrainX) == 0 ? 1 : (int) (cases * facStrainX));
//					firstTimeAfterDate = false;
//				} else if (date.isAfter(dateStrainX.plusMonths(2))) {
//					episimConfig.getInfections_pers_per_day().get(reverseSortedVirusStrainMap.get(dateStrainX)).put(date, 1);
//				} else {
//					episimConfig.getInfections_pers_per_day().get(reverseSortedVirusStrainMap.get(dateStrainX)).put(date, 0);
//				}
//
//			}
//
////			if (date.isAfter(dateBa5)) {
////				infPerDayBa5.put(date, ((int) cases * facBa5) == 0 ? 1 : (int) (cases * facBa5));
////				infPerDayBa2.put(date, 1);
////			} else if (date.isAfter(dateBa2)) {
////				infPerDayBa2.put(date, ((int) cases * facBa2) == 0 ? 1 : (int) (cases * facBa2));
////			}
//
//		}

//		infPerDayBa5.put(date.plusDays(1), 1);

		// save disease import


	}


	public static final class Params {

		// general
		@GenerateSeeds(3)
		public long seed;


		// vaccination campaign
		//vaccination frequency
		@StringParameter({"none", "fall22", "annual", "biannual","withStrain"})
		String vacFreq;

		@StringParameter({"18plus","18plus50pct"})
//		@StringParameter({"off", "60plus", "18plus"})
		String vacCamp;

		@StringParameter({"vaccinated","boostered"})
		String vacPool;

		@Parameter({30., 90.})
		double campDuration;


		//new mutations
		@Parameter({8.5, 103})
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


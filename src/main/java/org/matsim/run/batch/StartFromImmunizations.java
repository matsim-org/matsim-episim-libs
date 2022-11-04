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
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.analysis.*;
import org.matsim.episim.model.*;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.model.vaccination.VaccinationStrategyReoccurringCampaigns;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.*;


/**
 * Batch for Bmbf runs
 */
public class StartFromImmunizations implements BatchRun<StartFromImmunizations.Params> {


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
				LocalDate start = LocalDate.parse("2020-03-01");
				VaccinationType vaccinationType = VaccinationType.mRNA;
				int campaignDuration = 300000;

				// default values, to be changed if params != null
				int minDaysAfterInfection = 180;
				int minDaysAfterVaccination = 180;
				VaccinationStrategyReoccurringCampaigns.Config.VaccinationPool vaccinationPool = VaccinationStrategyReoccurringCampaigns.Config.VaccinationPool.unvaccinated;
				LocalDate emergencyDate = LocalDate.MAX;
				LocalDate dateToTurnDownMinDaysAfterInfection = LocalDate.MAX;
				Map<LocalDate,VaccinationType> startDateToVaccination = new HashMap<>();
				startDateToVaccination.put(start, vaccinationType);
				bind(VaccinationStrategyReoccurringCampaigns.Config.class).toInstance(new VaccinationStrategyReoccurringCampaigns.Config(startDateToVaccination, campaignDuration, vaccinationPool, minDaysAfterInfection, minDaysAfterVaccination, emergencyDate, dateToTurnDownMinDaysAfterInfection));

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
//				new VaccinationEffectiveness().withArgs(),
//				new RValuesFromEvents().withArgs(),
//				new VaccinationEffectivenessFromPotentialInfections().withArgs("--remove-infected"),
				new FilterEvents().withArgs("--output","./output/")
//				new HospitalNumbersFromEvents().withArgs("--output","./output/","--input","/scratch/projects/bzz0020/episim-input")
//				new SecondaryAttackRateFromEvents().withArgs()
		);
	}

	@Override
	public Config prepareConfig(int id, Params params) {


		SnzCologneProductionScenario module = getBindings(0.0, params);

		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 1.2 * 1.7);

		//snapshot

		episimConfig.setStartDate(LocalDate.parse("2020-02-29"));
		episimConfig.setStartFromImmunization("/Users/jakob/git/matsim-episim/output/seed_4711/2022-11-03");

//		episimConfig.setImmunizationPrefix("imm-" + String.valueOf(params.seed));

//		episimConfig.setStartFromImmunization("/scratch/projects/bzz0020/episim-input/snapshots-cologne-2022-10-27/imm-" + String.valueOf(params.seed)+"-960-2022-10-11.tsv.gz");
//		episimConfig.setStartFromImmunization("/Users/jakob/git/matsim-episim/output/seed_4711/imm-4711-210-2020-09-21.tsv.gz");
//		episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/episim-input/snapshots-cologne-2022-10-18/" + params.seed + "-960-2022-10-11.zip");
//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.restore);

//		episimConfig.getInfections_pers_per_day().get(VirusStrain.OMICRON_BA5).put(startDate, 144_380 / 4);
//		episimConfig.setSnapshotPrefix(String.valueOf(params.seed));
//		episimConfig.setSnapshotInterval(10);


		//---------------------------------------
		//		S T R A I N S
		//---------------------------------------

//		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
//
//		virusStrainConfigGroup.getOrAddParams(VirusStrain.SARS_CoV_2).setInfectiousness(virusStrainConfigGroup.getParams(VirusStrain.SARS_CoV_2).getInfectiousness() * 10);


		return config;
	}

	public static final class Params {
		// general
		@GenerateSeeds(1)
		public long seed;


	}


	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, StartFromImmunizations.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(30),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


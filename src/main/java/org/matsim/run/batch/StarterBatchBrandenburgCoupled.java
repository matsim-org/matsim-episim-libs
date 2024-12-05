package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.analysis.InfectionHomeLocation;
import org.matsim.episim.analysis.OutputAnalysis;
import org.matsim.episim.model.InfectionModelWithAntibodies;
import org.matsim.episim.model.listener.HouseholdSusceptibility;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBrandenburgProductionScenario;
import org.matsim.run.modules.SnzProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;


/**
 * boilerplate batch for brandenburg
 */
public class StarterBatchBrandenburgCoupled implements BatchRun<StarterBatchBrandenburgCoupled.Params> {

	boolean DEBUG_MODE = false;
	int runCount = 0;


	/*
	 * here you can swap out vaccination model, antibody model, etc.
	 * See CologneBMBF202310XX_soup.java for an example
	 */
	@Nullable
	@Override
	public Module getBindings(int id, @Nullable Params params) {

		return Modules.override(getBindings(params)).with(new AbstractModule() {
			@Override
			protected void configure() {
				bind(HouseholdSusceptibility.Config.class).toInstance(
					HouseholdSusceptibility.newConfig()
						.withSusceptibleHouseholds(params == null ? 0.0 : params.pHouseholds, 0.01));
			}
		});
	}


	/*
	 * here you select & modify models specified in the SnzCologneProductionScenario & SnzProductionScenario.
	 */
	private SnzBrandenburgProductionScenario getBindings(Params params) {
		return new SnzBrandenburgProductionScenario.Builder()
			.setScaleForActivityLevels(params == null ? 1.0 : params.scale)
//			.setLocationBasedRestrictions(SnzProductionScenario.LocationBasedRestrictions.yes)
			.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
			.setInfectionModel(InfectionModelWithAntibodies.class)
			.setSample(25)
			.build();
	}

	/*
	 * Metadata is needed for covid-sim.
	 */
	@Override
	public Metadata getMetadata() {
		return Metadata.of("brandenburg", "calibration");
	}


	/*
	 * Here you can add post-processing classes, that will be executed after the simulation.
	 */
	@Override
	public Collection<OutputAnalysis> postProcessing() {
		return List.of(new InfectionHomeLocation().withArgs("--output", "./output/", "--input", SnzBrandenburgProductionScenario.INPUT.toString(),//"/Users/jakob/git/shared-svn/projects/episim/matsim-files/snz/Brandenburg/episim-input",
			"--population-file", "br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz"));
	}

	/*
	 * Here you can specify configuration options
	 */
	@Override
	public Config prepareConfig(int id, Params params) {

		if (DEBUG_MODE) {
			if (runCount == 0) { //&& params.strAEsc != 0.0 && params.ba5Inf == 0. && params.eduTest.equals("true")) {
				runCount++;
			} else {
				return null;
			}
		}

		// Level 1: General (matsim) config. Here you can specify number of iterations and the seed.
		Config config = getBindings(params).config();

		config.global().setRandomSeed(params.seed);

		// Level 2: Episim specific configs:
		// 		 2a: general episim config
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setStartDate(LocalDate.parse("2020-03-03"));


		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 1.2 * 1.7 * params.thetaFactor);
		episimConfig.setOdeCouplingFactor(params.couplingfactor);
		switch (params.district) {
			case "Frankfurt":
				episimConfig.setOdeCouplingDistrict("Frankfurt (Oder)");
				break;
			case "Brandenburg":
				episimConfig.setOdeCouplingDistrict("Brandenburg an der Havel");
				break;
			default:
				episimConfig.setOdeCouplingDistrict(params.district);
				break;
		}

		// 2b: specific config groups, e.g. virusStrainConfigGroup
		// VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		// turn off all import

		episimConfig.setInitialInfections(0);
//		episimConfig.setThreads(1);

		for (NavigableMap<LocalDate, Integer> map : episimConfig.getInfections_pers_per_day().values()) {
			map.clear();
		}
		// summer import
//		{
//			Map<LocalDate, Integer> infPerDayWild = episimConfig.getInfections_pers_per_day().get(VirusStrain.SARS_CoV_2);
//			LocalDate startDateImport = LocalDate.parse("2020-08-01");
//			LocalDate endDateImport = LocalDate.parse("2020-11-02");
//
//			infPerDayWild.put(startDateImport, (int) params.summerImport);
//
//			for (LocalDate date : infPerDayWild.keySet()) {
//				if (date.isAfter(startDateImport) && date.isBefore(endDateImport)) {
//					infPerDayWild.put(date, (int) params.summerImport);
//				}
//			}
//
//			infPerDayWild.put(endDateImport, 1);
//		}

		if(params.ci.equals("red")){
			//work
			double workCiMod = 0.75;
			episimConfig.getOrAddContainerParams("work").setContactIntensity(episimConfig.getOrAddContainerParams("work").getContactIntensity() * workCiMod);
			episimConfig.getOrAddContainerParams("business").setContactIntensity(episimConfig.getOrAddContainerParams("business").getContactIntensity() * workCiMod);

			//leisure & visit
			double leisureCiMod = 0.4;
			episimConfig.getOrAddContainerParams("leisure").setContactIntensity(episimConfig.getOrAddContainerParams("leisure").getContactIntensity() * leisureCiMod);
			episimConfig.getOrAddContainerParams("leisPublic").setContactIntensity(episimConfig.getOrAddContainerParams("leisPublic").getContactIntensity() * leisureCiMod);
			episimConfig.getOrAddContainerParams("leisPrivate").setContactIntensity(episimConfig.getOrAddContainerParams("leisPrivate").getContactIntensity() * leisureCiMod);
			episimConfig.getOrAddContainerParams("visit").setContactIntensity(episimConfig.getOrAddContainerParams("visit").getContactIntensity() * leisureCiMod);


			//school
			double schoolCiMod = 0.75;
			episimConfig.getOrAddContainerParams("educ_kiga").setContactIntensity(episimConfig.getOrAddContainerParams("educ_kiga").getContactIntensity() * schoolCiMod);
			episimConfig.getOrAddContainerParams("educ_primary").setContactIntensity(episimConfig.getOrAddContainerParams("educ_primary").getContactIntensity() * schoolCiMod);
			episimConfig.getOrAddContainerParams("educ_secondary").setContactIntensity(episimConfig.getOrAddContainerParams("educ_secondary").getContactIntensity() * schoolCiMod);
			episimConfig.getOrAddContainerParams("educ_tertiary").setContactIntensity(episimConfig.getOrAddContainerParams("educ_tertiary").getContactIntensity() * schoolCiMod);
			episimConfig.getOrAddContainerParams("educ_higher").setContactIntensity(episimConfig.getOrAddContainerParams("educ_higher").getContactIntensity() * schoolCiMod);
			episimConfig.getOrAddContainerParams("educ_other").setContactIntensity(episimConfig.getOrAddContainerParams("educ_other").getContactIntensity() * schoolCiMod);

		} else if (params.ci.equals("base")) {

		} else {
			throw new RuntimeException();
		}

		return config;
	}


	/*
	 * Specify parameter combinations that will be run.
	 */
	public static final class Params {
		// general
		@GenerateSeeds(2)
		public long seed;

		@StringParameter({"red"})
		public String ci;

		@Parameter({1.3})
		public double scale;

//		@Parameter({ 0.6, 0.8,  1.0, 1.2, 1.4, 1.6, 2.0})
//		@Parameter({0.8, 1.0,  1.2})
		@Parameter({10.0})
		public double thetaFactor;

//		@Parameter({1.0, 1.25, 1.5, 1.75, 2.0, 3.0, 5.0, 7.5, 10.})
//		@Parameter({0.5, 1.0, 2.0})
		@Parameter({10.0})
		public double couplingfactor;

//		@StringParameter({"Cottbus", "Brandenburg", "Frankfurt", "Potsdam",""})
		@StringParameter({"Potsdam"})
		public String district;


//		@Parameter({1, 2, 4, 8, 16})
//		public double summerImport;

		@Parameter({0.25})
		public double pHouseholds;

	}


	/*
	 * top-level parameters for a run on your local machine.
	 */
	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, StarterBatchBrandenburgCoupled.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(30),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}

}


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
import org.matsim.episim.policy.AdaptivePolicy;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.policy.ShutdownPolicy;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBrandenburgProductionScenario;
import org.matsim.run.modules.SnzProductionScenario;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.time.LocalDate;
import java.util.*;


/**
 * boilerplate batch for brandenburg
 */
public class StarterBatchBerlinCoupled implements BatchRun<StarterBatchBerlinCoupled.Params> {

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
						.withSusceptibleHouseholds(0.25, 0.01));

			}
		});
	}


	/*
	 * here you select & modify models specified in the SnzCologneProductionScenario & SnzProductionScenario.
	 */
	private SnzBerlinProductionScenario getBindings(Params params) {
		return new SnzBerlinProductionScenario.Builder()
//			.setScaleForActivityLevels(1.3)
			.setLocationBasedRestrictions(EpisimConfigGroup.DistrictLevelRestrictions.no)
			.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
			.setInfectionModel(InfectionModelWithAntibodies.class)
			.setOdeCoupling(SnzProductionScenario.OdeCoupling.yes)
			.setSample(25)
			.build();
	}

	/*
	 * Metadata is needed for covid-sim.
	 */
	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
	}


	/*
	 * Here you can add post-processing classes, that will be executed after the simulation.
	 */
	@Override
	public Collection<OutputAnalysis> postProcessing() {
		return List.of(new InfectionHomeLocation().withArgs("--output", "./output/", "--input", SnzBerlinProductionScenario.INPUT.toString(),//"/Users/jakob/git/shared-svn/projects/episim/matsim-files/snz/Brandenburg/episim-input",
			"--population-file", "be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz"));
	}

	/*
	 * Here you can specify configuration options
	 */
	@Override
	public Config prepareConfig(int id, Params params) {

		if (DEBUG_MODE) {
			if (runCount == 0){
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




//		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 1.2 * 1.7 * params.theta);

		//override theta parameter to match Brandenburg
		episimConfig.setCalibrationParameter(1.0e-05 * 0.83 * 1.4);
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 1.2 * 1.7 * params.theta);


		// COUPLING
		// turn off other import
		episimConfig.setInitialInfections(0);

		for (NavigableMap<LocalDate, Integer> map : episimConfig.getInfections_pers_per_day().values()) {
			map.clear();
		}

		episimConfig.setOdeIncidenceFile(SnzBerlinProductionScenario.INPUT.resolve("ode_br_infectious_250212.csv").toString());
//		episimConfig.setOdeIncidenceFile(SnzBerlinProductionScenario.INPUT.resolve("ode_inputs/left_s.csv").toString());

		episimConfig.setOdeDistricts(SnzBrandenburgProductionScenario.BRANDENBURG_LANDKREISE);

		episimConfig.setOdeCouplingFactor(params.couplingfactor);

		// set outdoor fraction
		if (!Objects.equals(params.outdoorFrac, "base")) {
			episimConfig.setLeisureOutdoorFraction(Double.parseDouble(params.outdoorFrac));
		}

		// adjust mobility
		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		if(!Boolean.parseBoolean(params.schoolClosure)){
			builder.applyToRf("2020-01-01", "2030-01-01", (d, e) -> 1.0, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_higher");
		}

		episimConfig.setPolicy(builder.build());



		// 2b: specific config groups, e.g. virusStrainConfigGroup
		// VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		episimConfig.setInitialInfections(0);
//		episimConfig.setThreads(1);

		for (NavigableMap<LocalDate, Integer> map : episimConfig.getInfections_pers_per_day().values()) {
			map.clear();
		}


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


		//2
		// general
		@GenerateSeeds(2)
		public long seed;

		@StringParameter({"red"})
		public String ci;

		@Parameter({0.25, 0.5, 0.75, 1.0})
//		@Parameter({1.0})
		public double theta;



		//5
//		@Parameter({1.0, 2.5, 5.0, 7.5, 10.})
//		@Parameter({0.5, 1.0, 2.0})
		@Parameter({0.1, 0.25, 0.5, 0.75, 1.0})
//		@Parameter({1.0})
		public double couplingfactor;

		@StringParameter({"true", "false"})
		public String schoolClosure;


		//6
//		@StringParameter({"base", "0.0", "0.2", "0.4"})
//		@StringParameter({"0.0",  "1.0"})
		@StringParameter({"base"})
		public String outdoorFrac;

	}


	/*
	 * top-level parameters for a run on your local machine.
	 */
	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, StarterBatchBerlinCoupled.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(300),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}

}


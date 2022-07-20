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
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.model.vaccination.VaccinationStrategySociallyDivided;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.*;


/**
 * To prep:
 *
 */
public class CologneContactIntensity implements BatchRun<CologneContactIntensity.Params> {

	boolean DEBUG_MODE = false;
	int runCount = 0;

	@Nullable
	@Override
	public Module getBindings(int id, @Nullable Params params) {
		return Modules.override(getBindings(0.0, params)).with(new AbstractModule() {
			@Override
			protected void configure() {

				Multibinder<VaccinationModel> set = Multibinder.newSetBinder(binder(), VaccinationModel.class);

				set.addBinding().to(VaccinationStrategySociallyDivided.class).in(Singleton.class);

				LocalDate start = LocalDate.of(2020, 3, 1);
				VaccinationType vaccinationType = VaccinationType.mRNA;
				int doses = 350_000 / 4 ;
				int duration = 1;
				int minAge = 0;

				boolean vaxPoor = false;
				boolean vaxRich = false;
				if (params != null) {
					vaxPoor = Boolean.parseBoolean(params.vaxPoor);
					vaxRich = Boolean.parseBoolean(params.vaxRich);
				}

				// none - none
				// rich vax = 455_036
				// rich poor = 383_956
				// both = 838.992

				bind(VaccinationStrategySociallyDivided.Config.class).toInstance(new VaccinationStrategySociallyDivided.Config(start, duration, vaccinationType, minAge, doses, vaxRich, vaxPoor));
			}

			});
	}


	private SnzCologneProductionScenario getBindings(double pHousehold, Params params) {
		return new SnzCologneProductionScenario.Builder()
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
				new VaccinationEffectivenessFromPotentialInfections().withArgs("--remove-infected")
//				new HospitalNumbersFromEvents().withArgs()
		);
	}

	/**
	 *
	 */

	@Override
	public Config prepareConfig(int id, Params params) {

		if (DEBUG_MODE) {
			if (runCount == 0 && params.ciModifier == 0. && Objects.equals(params.vaxPoor, "false") && Objects.equals(params.vaxRich, "true")){
				runCount++;
			} else {
				return null;
			}
		}



		SnzCologneProductionScenario module = getBindings(0.0, params);

		Config config = module.config();

		// set seed
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		// modify contact intensity
		episimConfig.getOrAddContainerParams("home_15").setContactIntensity(1.0 + 3 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_25").setContactIntensity(1.0 + 2 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_35").setContactIntensity(1.0 + 1 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_45").setContactIntensity(1.0).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_55").setContactIntensity(1.0 - 1 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_65").setContactIntensity(1.0 - 2 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_75").setContactIntensity(1.0 - 3 * params.ciModifier).setSpacesPerFacility(1);


		episimConfig.setDistrictLevelRestrictionsAttribute("subdistrict");




//		System.out.print("immunityGiver");
//		List<VirusStrain> virusValues = List.of(VirusStrain.SARS_CoV_2, VirusStrain.ALPHA, VirusStrain.DELTA, VirusStrain.OMICRON_BA1, VirusStrain.OMICRON_BA2);
//		List<VaccinationType> vaccinationValues = List.of(VaccinationType.mRNA, VaccinationType.vector);
//
//		for (VirusStrain immunityFrom : virusValues) {
//			if (immunityFrom == VirusStrain.OMICRON_BA1) {
//				System.out.print( "," + "BA.1");
//			} else 		if (immunityFrom == VirusStrain.OMICRON_BA2) {
//				System.out.print( "," + "BA.2");
//			} else {
//				System.out.print( "," + immunityFrom);
//			}
//		}
//
//		AntibodyModel.Config antibodyConfig = new AntibodyModel.Config();
//		Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = antibodyConfig.getInitialAntibodies();
//		for (ImmunityEvent immunityGiver : vaccinationValues) {
//			System.out.print("\n" + immunityGiver);
//			for (VirusStrain immunityFrom : virusValues) {
//				System.out.print("," +  String.format("%.3g", initialAntibodies.get(immunityGiver).get(immunityFrom)));
//			}
//		}
//		for (ImmunityEvent immunityGiver : virusValues) {
//			System.out.print("\n" + immunityGiver);
//			for (VirusStrain immunityFrom : virusValues) {
//				System.out.print("," + String.format("%.3g", initialAntibodies.get(immunityGiver).get(immunityFrom)));
//			}
//		}
//
//		System.out.println();


		return config;
	}


	public static final class Params {

		// general
		@GenerateSeeds(3)
		public long seed;

		@Parameter({0.0, 0.3})
		Double ciModifier;

		@StringParameter({"true", "false"})
		String vaxRich;

		@StringParameter({"true", "false"})
		String vaxPoor;


//		@Parameter({0.95, 0.955, 0.96, 0.965, 0.97, 0.975, 0.98, 0.985, 0.99, 0.995, 1.0})
//		@Parameter({1.0})
//		double thetaFactor;

	}


	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneContactIntensity.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(70),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


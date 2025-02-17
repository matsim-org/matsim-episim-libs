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
public class StarterBatchBrandenburgCoupledAdaptive implements BatchRun<StarterBatchBrandenburgCoupledAdaptive.Params> {

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

				if(params != null){
					if (params.adaptivePolicy != SnzBerlinProductionScenario.AdaptiveRestrictions.no) {
						bind(ShutdownPolicy.class).to(AdaptivePolicy.class).in(Singleton.class);
					} else {
						bind(ShutdownPolicy.class).to(FixedPolicy.class).in(Singleton.class);
					}
				}


				bind(HouseholdSusceptibility.Config.class).toInstance(
					HouseholdSusceptibility.newConfig()
						.withSusceptibleHouseholds(0.25, 0.01));

			}
		});
	}


	/*
	 * here you select & modify models specified in the SnzCologneProductionScenario & SnzProductionScenario.
	 */
	private SnzBrandenburgProductionScenario getBindings(Params params) {
		return new SnzBrandenburgProductionScenario.Builder()
			.setScaleForActivityLevels(1.3)
			.setLocationBasedRestrictions(SnzProductionScenario.LocationBasedRestrictions.yes)
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
			if (runCount == 0 && params.districtBasedRestrictions == EpisimConfigGroup.DistrictLevelRestrictions.yesForHomeAndActivityLocation && params.adaptivePolicy == SnzBerlinProductionScenario.AdaptiveRestrictions.yesLocal) { //&& params.strAEsc != 0.0 && params.ba5Inf == 0. && params.eduTest.equals("true")) {

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


		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 1.2 * 1.7 );

		// COUPLING
		// turn off other import
		episimConfig.setInitialInfections(0);

		for (NavigableMap<LocalDate, Integer> map : episimConfig.getInfections_pers_per_day().values()) {
			map.clear();
		}

		episimConfig.setOdeIncidenceFile(SnzBrandenburgProductionScenario.INPUT.resolve("ode_be_infectious_250211.csv").toString());

		episimConfig.setOdeDistricts(List.of("Berlin"));

		episimConfig.setOdeCouplingFactor(params.couplingfactor);

		switch (params.district) {
			case "All":
				break;
			case "Frankfurt":
				episimConfig.setOdeInfTargetDistrict("Frankfurt (Oder)");
				break;
			case "Brandenburg":
				episimConfig.setOdeInfTargetDistrict("Brandenburg an der Havel");
				break;
			default:
				episimConfig.setOdeInfTargetDistrict(params.district);
				break;
		}

		// set outdoor fraction
		if (!Objects.equals(params.outdoorFrac, "base")) {
			episimConfig.setLeisureOutdoorFraction(Double.parseDouble(params.outdoorFrac));
		}

		// adjust mobility
		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

//		if (params.mobilityMult != 1.0) {
//			builder.applyToRf("2020-04-01", "2025-04-01", (d, rf) -> rf * params.mobilityMult, "pt", "work", "leisure", "leisPublic", "leisPrivate", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_higher", "educ_other", "shop_daily", "shop_other", "errands", "business", "visit");
//
//		}

		episimConfig.setPolicy(builder.build());



		// 2b: specific config groups, e.g. virusStrainConfigGroup
		// VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);


		// LOCATION BASED RESTRICTIONS
		episimConfig.setReportTimeUse(EpisimConfigGroup.ReportTimeUse.yes);
		if (params.adaptivePolicy != SnzBerlinProductionScenario.AdaptiveRestrictions.no) {

			List<String> subdistricts = SnzBrandenburgProductionScenario.BRANDENBURG_LANDKREISE;
			episimConfig.setDistricts(subdistricts);
			episimConfig.setDistrictLevelRestrictionsAttribute("district");
			episimConfig.setDistrictLevelRestrictions(params.districtBasedRestrictions);

			// Set up initial policy - remove all location based restrictions from snz data
//			FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
//			builder.apply("2020-01-01", "2022-01-01", (d, e) -> ((HashMap<String, Double>) e.get("locationBasedRf")).clear(), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);


			// General setup of adaptive restrictions
			LocalDate minDate = LocalDate.MIN;

			double workTrigger = params.trigger;
			double leisureTrigger = params.trigger;
			double eduTrigger = params.trigger;
			double shopErrandsTrigger = params.trigger;

			double openFraction = 0.9;
			double restrictedFraction = params.restrictedFraction;

			String startDate = "2020-01-01";
			// GLOBAL ADAPTIVE POLICY
			if (params.adaptivePolicy.equals(SnzBerlinProductionScenario.AdaptiveRestrictions.yesGlobal)) {
				com.typesafe.config.Config policy = AdaptivePolicy.config()
					.startDate(startDate)
					.restrictionScope(AdaptivePolicy.RestrictionScope.global.toString())
					.incidenceTrigger(workTrigger, workTrigger, "work", "business")
					.incidenceTrigger(leisureTrigger, leisureTrigger, "leisure", "leisPublic", "leisPrivate", "visit")
					.incidenceTrigger(eduTrigger, eduTrigger, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_higher")
					.incidenceTrigger(shopErrandsTrigger, shopErrandsTrigger, "shop_other", "shop_daily", "errands")
					.initialPolicy(builder)
					.restrictedPolicy(FixedPolicy.config()
						.restrict(minDate, Restriction.of(restrictedFraction), "work")
						.restrict(minDate, Restriction.of(restrictedFraction), "shop_daily")
						.restrict(minDate, Restriction.of(restrictedFraction), "shop_other")
						.restrict(minDate, Restriction.of(restrictedFraction), "errands")
						.restrict(minDate, Restriction.of(restrictedFraction), "business")
						.restrict(minDate, Restriction.of(restrictedFraction), "visit")
						.restrict(minDate, Restriction.of(restrictedFraction), "leisure")
						.restrict(minDate, Restriction.of(restrictedFraction), "leisPublic")
						.restrict(minDate, Restriction.of(restrictedFraction), "leisPrivate")
						.restrict(minDate, Restriction.of(restrictedFraction), "educ_higher")
						.restrict(minDate, Restriction.of(restrictedFraction), "educ_kiga")
						.restrict(minDate, Restriction.of(restrictedFraction), "educ_primary")
						.restrict(minDate, Restriction.of(restrictedFraction), "educ_secondary")
						.restrict(minDate, Restriction.of(restrictedFraction), "educ_tertiary")
						.restrict(minDate, Restriction.of(restrictedFraction), "educ_other")
						.restrict(minDate, Restriction.ofLocationBasedRf(new HashMap<>()), "work", "shop_daily", "shop_other", "errands", "business", "visit", "leisure", "educ_higher", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					)
					.openPolicy(FixedPolicy.config()
						.restrict(minDate, Restriction.of(openFraction), "work")
						.restrict(minDate, Restriction.of(openFraction), "shop_daily")
						.restrict(minDate, Restriction.of(openFraction), "shop_other")
						.restrict(minDate, Restriction.of(openFraction), "errands")
						.restrict(minDate, Restriction.of(openFraction), "business")
						.restrict(minDate, Restriction.of(openFraction), "visit")
						.restrict(minDate, Restriction.of(openFraction), "leisure")
						.restrict(minDate, Restriction.of(openFraction), "leisPublic")
						.restrict(minDate, Restriction.of(openFraction), "leisPrivate")
						.restrict(minDate, Restriction.of(openFraction), "educ_higher")
						.restrict(minDate, Restriction.of(openFraction), "educ_kiga")
						.restrict(minDate, Restriction.of(openFraction), "educ_primary")
						.restrict(minDate, Restriction.of(openFraction), "educ_secondary")
						.restrict(minDate, Restriction.of(openFraction), "educ_tertiary")
						.restrict(minDate, Restriction.of(openFraction), "educ_other")
						.restrict(minDate, Restriction.ofLocationBasedRf(new HashMap<>()), "work", "shop_daily", "shop_other", "errands", "business", "visit", "leisure", "leisPublic", "leisPrivate", "educ_higher", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					)
					.build();

				episimConfig.setPolicy(AdaptivePolicy.class, policy);
			}

			// LOCAL ADAPTIVE POLICY
			else if (params.adaptivePolicy.equals(SnzBerlinProductionScenario.AdaptiveRestrictions.yesLocal)) {
				com.typesafe.config.Config policy = AdaptivePolicy.config()
					.startDate(startDate)
					.restrictionScope(AdaptivePolicy.RestrictionScope.local.toString())
					.incidenceTrigger(workTrigger, workTrigger, "work", "business")
					.incidenceTrigger(leisureTrigger, leisureTrigger, "leisure", "leisPublic", "leisPrivate", "visit")
					.incidenceTrigger(eduTrigger, eduTrigger, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_higher")
					.incidenceTrigger(shopErrandsTrigger, shopErrandsTrigger, "shop_other", "shop_daily", "errands")
					.initialPolicy(builder)
					.restrictedPolicy(FixedPolicy.config()
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "work")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "shop_daily")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "shop_other")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "errands")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "business")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "visit")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "leisure")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "leisPublic")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "leisPrivate")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_higher")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_kiga")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_primary")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_secondary")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_tertiary")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_other")
					)
					.openPolicy(FixedPolicy.config()
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "work")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "shop_daily")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "shop_other")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "errands")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "business")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "visit")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "leisure")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "leisPublic")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "leisPrivate")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_higher")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_kiga")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_primary")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_secondary")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_tertiary")
						.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_other")
					)
					.build();

				episimConfig.setPolicy(AdaptivePolicy.class, policy);
			}
		}

		return config;
	}

	private Restriction constructRestrictionWithLocalAndGlobalRf(List<String> subdistricts, double rf) {
		Restriction r = Restriction.ofLocationBasedRf(makeUniformLocalRf(subdistricts, rf));
		r.merge(Restriction.of(rf).asMap());

		return r;
	}

	private Map<String, Double> makeUniformLocalRf(List<String> districts, Double rf) {
		Map<String, Double> localRf = new HashMap<>();
		for (String district : districts) {
			localRf.put(district, rf);
		}
		return localRf;
	}



	/*
	 * Specify parameter combinations that will be run.
	 */
	public static final class Params {


		// 3
		@EnumParameter(value = EpisimConfigGroup.DistrictLevelRestrictions.class, ignore = "no")
		EpisimConfigGroup.DistrictLevelRestrictions districtBasedRestrictions;



		//3
		@EnumParameter(value = SnzBerlinProductionScenario.AdaptiveRestrictions.class)
		SnzBerlinProductionScenario.AdaptiveRestrictions adaptivePolicy;


		//		@Parameter({10, 25, 50, 75, 100, 125, 150})
//		@Parameter({0.000000001})
		//3
		@Parameter({1, 10, 100})
		double trigger;

		//		@Parameter({0.0, 0.2, 0.4, 0.6})
		@Parameter({0.0})
		double restrictedFraction;


		//2
		// general
		@GenerateSeeds(2)
		public long seed;

		//5
//		@Parameter({1.0, 2.5, 5.0, 7.5, 10.})
//		@Parameter({0.5, 1.0, 2.0})
		@Parameter({1.0,10.0})
		public double couplingfactor;


		//5
//		@StringParameter({"Cottbus", "Brandenburg", "Frankfurt", "Potsdam","All"})
		@StringParameter({"All","Cottbus", "Frankfurt"})
		public String district;




		//6
		@StringParameter({"base", "0.0", "0.2", "0.4"})
//		@StringParameter({"0.0",  "1.0"})
		public String outdoorFrac;


//		@Parameter({1, 2, 4, 8, 16})
//		public double summerImport;


	}


	/*
	 * top-level parameters for a run on your local machine.
	 */
	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, StarterBatchBrandenburgCoupledAdaptive.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(20),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}

}


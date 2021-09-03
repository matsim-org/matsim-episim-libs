package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.policy.AdaptivePolicy;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.*;

import static org.matsim.run.modules.SnzBerlinProductionScenario.*;


public class JRBatchAdaptiveRestrictionsLocal implements BatchRun<JRBatchAdaptiveRestrictionsLocal.Params> {

	boolean DEBUG = false;

	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {
		return new Builder()
				.setSnapshot(Snapshot.no)
				.setChristmasModel(ChristmasModel.no)
				.setEasterModel(EasterModel.no)
				.setVaccinations(Vaccinations.no)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
				.setLocationBasedRestrictions(EpisimConfigGroup.DistrictLevelRestrictions.yesForHomeLocation)
				.setAdaptiveRestrictions(params != null ? params.adaptivePolicy : AdaptiveRestrictions.no)
				.setSample(DEBUG ? 1 : 25)
				.setTracing(Tracing.yes)
				.createSnzBerlinProductionScenario();

	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "adaptLocal");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		if (DEBUG) {
			if (params.adaptivePolicy != AdaptiveRestrictions.yesLocal ||
					params.restrictedFraction != 0.2 || params.trigger != 50.
				//					|| params.tracingCapacity != 200 || params.tracingProbability == 1.0
				//					|| params.tracingStrategy != NONE
			) {
				return null;

			}
		}

		SnzBerlinProductionScenario module = getBindings(id, params);

		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		//		episimConfig.setStartFromSnapshot("../episim-snapshot-270-2020-11-20.zip");

		List<String> subdistricts = Arrays.asList("Spandau", "Neukoelln", "Reinickendorf",
				"Charlottenburg_Wilmersdorf", "Marzahn_Hellersdorf", "Mitte", "Pankow", "Friedrichshain_Kreuzberg",
				"Tempelhof_Schoeneberg", "Treptow_Koepenick", "Lichtenberg", "Steglitz_Zehlendorf");
		episimConfig.setDistricts(subdistricts);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		//		builder.clearAfter("2020-12-14");
		//		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		//		LocalDate date = LocalDate.parse("2020-04-01");

		// General setup of adaptive restrictions
		LocalDate date = LocalDate.MIN;

		double workTrigger = params.trigger;
		double leisureTrigger = params.trigger;
		double eduTrigger = params.trigger;
		double shopErrandsTrigger = params.trigger;

		double openFraction = 0.9;
		//		double restrictedFraction = 0.6;
		double restrictedFraction = params.restrictedFraction;

		// global
		String startDate = DEBUG ? "2020-04-01" : "2020-08-01";
		if (params.adaptivePolicy.equals(AdaptiveRestrictions.yesGlobal)) {
			com.typesafe.config.Config policy = AdaptivePolicy.config()
					.startDate(startDate)
					.restrictionScope(AdaptivePolicy.RestrictionScope.global.toString())
					.incidenceTrigger(workTrigger, workTrigger, "work", "business")
					.incidenceTrigger(leisureTrigger, leisureTrigger, "leisure", "visit")
					.incidenceTrigger(eduTrigger, eduTrigger, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					.incidenceTrigger(shopErrandsTrigger, shopErrandsTrigger, "shop_other", "shop_daily", "errands")
					.initialPolicy(builder)
					.restrictedPolicy(FixedPolicy.config()
							.restrict(date, Restriction.of(restrictedFraction), "work")
							.restrict(date, Restriction.of(restrictedFraction), "shop_daily")
							.restrict(date, Restriction.of(restrictedFraction), "shop_other")
							.restrict(date, Restriction.of(restrictedFraction), "errands")
							.restrict(date, Restriction.of(restrictedFraction), "business")
							.restrict(date, Restriction.of(restrictedFraction), "visit")
							.restrict(date, Restriction.of(restrictedFraction), "leisure")
							.restrict(date, Restriction.of(restrictedFraction), "educ_higher")
							.restrict(date, Restriction.of(.2), "educ_kiga")
							.restrict(date, Restriction.of(.2), "educ_primary")
							.restrict(date, Restriction.of(.2), "educ_secondary")
							.restrict(date, Restriction.of(.2), "educ_tertiary")
							.restrict(date, Restriction.of(.2), "educ_other")
							.restrict(date, Restriction.ofLocationBasedRf(new HashMap<>()), "work", "shop_daily", "shop_other", "errands", "business", "visit", "leisure", "educ_higher", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					)
					.openPolicy(FixedPolicy.config()
							.restrict(date, Restriction.of(openFraction), "work")
							.restrict(date, Restriction.of(openFraction), "shop_daily")
							.restrict(date, Restriction.of(openFraction), "shop_other")
							.restrict(date, Restriction.of(openFraction), "errands")
							.restrict(date, Restriction.of(openFraction), "business")
							.restrict(date, Restriction.of(openFraction), "visit")
							.restrict(date, Restriction.of(openFraction), "leisure")
							.restrict(date, Restriction.of(openFraction), "educ_higher")
							.restrict(date, Restriction.of(1.), "educ_kiga")
							.restrict(date, Restriction.of(1.), "educ_primary")
							.restrict(date, Restriction.of(1.), "educ_secondary")
							.restrict(date, Restriction.of(1.), "educ_tertiary")
							.restrict(date, Restriction.of(1.), "educ_other")
							.restrict(date, Restriction.ofLocationBasedRf(new HashMap<>()), "work", "shop_daily", "shop_other", "errands", "business", "visit", "leisure", "educ_higher", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					)
					.build();

			episimConfig.setPolicy(AdaptivePolicy.class, policy);
		}


		// local

		else if (params.adaptivePolicy.equals(AdaptiveRestrictions.yesLocal)) {
			com.typesafe.config.Config policy = AdaptivePolicy.config()
					.startDate(startDate)
					.restrictionScope(AdaptivePolicy.RestrictionScope.local.toString())
					.incidenceTrigger(workTrigger, workTrigger, "work", "business")
					.incidenceTrigger(leisureTrigger, leisureTrigger, "leisure", "visit")
					.incidenceTrigger(eduTrigger, eduTrigger, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					.incidenceTrigger(shopErrandsTrigger, shopErrandsTrigger, "shop_other", "shop_daily", "errands")
					.initialPolicy(builder)
					.restrictedPolicy(FixedPolicy.config()
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "work")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "shop_daily")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "shop_other")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "errands")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "business")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "visit")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "leisure")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "educ_higher")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, .2), "educ_kiga")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, .2), "educ_primary")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, .2), "educ_secondary")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, .2), "educ_tertiary")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, .2), "educ_other")
							//							.restrict(date, Restriction.ofLocationBasedRf(new HashMap<>()), "work", "shop_daily", "shop_other", "errands", "business", "visit", "leisure", "educ_higher", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					)
					.openPolicy(FixedPolicy.config()
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "work")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "shop_daily")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "shop_other")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "errands")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "business")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "visit")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "leisure")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, openFraction), "educ_higher")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, 1.), "educ_kiga")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, 1.), "educ_primary")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, 1.), "educ_secondary")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, 1.), "educ_tertiary")
									.restrict(date, constructRestrictionWithLocalAndGlobalRf(subdistricts, 1.), "educ_other")
							//							.restrict(date, Restriction.ofLocationBasedRf(new HashMap<>()), "work", "shop_daily", "shop_other", "errands", "business", "visit", "leisure", "educ_higher", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
					)
					.build();

			episimConfig.setPolicy(AdaptivePolicy.class, policy);
		}

		// TRACING
		// based on BerlinSecondLockdown.java
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 300,
				LocalDate.of(2020, 6, 15), 2000,
				LocalDate.of(2020,8,1), params.tracingCapacity
		));
		tracingConfig.setTracingProbability(params.tracingProbability);


		//		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		//		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(30);
		//		tracingConfig.setStrategy(TracingConfigGroup.Strategy.INDIVIDUAL_ONLY);
		////		tracingConfig.setStrategy(Enum.valueOf(TracingConfigGroup.Strategy.class, params.tracingStrategy));
		//		tracingConfig.setLocationThreshold(3);
		//		tracingConfig.setTracingDelay_days(1);
		//		tracingConfig.setCapacityType(TracingConfigGroup.CapacityType.PER_PERSON);
		//		tracingConfig.setTracingCapacity_pers_per_day(params.tracingCapacity);

		return config;
	}

	private Restriction constructRestrictionWithLocalAndGlobalRf(List<String> subdistricts, double rf) {
		Restriction r = Restriction.ofLocationBasedRf(makeUniformLocalRf(subdistricts, rf));
		r.merge(Restriction.of(rf).asMap());

		return r;
	}

	public static final class Params {

		// Adaptive Policy
		@EnumParameter(AdaptiveRestrictions.class)
		AdaptiveRestrictions adaptivePolicy;

		@GenerateSeeds(1)
		public long seed;

		//		@Parameter({0.2, 0.4, 0.6})
		@Parameter({0.6})
		double restrictedFraction;
		//
		//		@Parameter({0.9})
		//		double openFraction;

		//		@Parameter({Integer.MAX_VALUE, 100.})
		//		@Parameter({5, 10, 50, 100})
		@Parameter({10})
		double trigger;


		// Tracing:

		//		@EnumParameter(value = TracingConfigGroup.Strategy.class, ignore = "RANDOM")
		//		TracingConfigGroup.Strategy tracingStrategy;

//		@StringParameter({"INDIVIDUAL_ONLY", "NONE"})
//		String tracingStrategy;

		@IntParameter({0, 2000, 4000, Integer.MAX_VALUE})
		int tracingCapacity;

		@Parameter({0.6, 1.0})
		double tracingProbability;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRBatchAdaptiveRestrictionsLocal.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}

	private Map<String, Double> makeUniformLocalRf(List<String> districts, Double rf) {
		Map<String, Double> localRf = new HashMap<>();
		for (String district : districts) {
			localRf.put(district, rf);
		}
		return localRf;
	}


}


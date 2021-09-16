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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.matsim.run.modules.SnzBerlinProductionScenario.*;


public class JRBatchMasterC implements BatchRun<JRBatchMasterC.Params> {

	boolean DEBUG = true;

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
		return Metadata.of("berlin", "masterC");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		if (DEBUG) {
			if (params.adaptivePolicy != AdaptiveRestrictions.yesLocal ||
					params.restrictedFraction != 0.6 || params.trigger != 100.
//					|| params.tracingCapacity != 2000 || params.tracingProbability != 1.0
//					|| params.tracingDelay != 1
			) {

				return null;

			}
		}

//		if (params.adaptivePolicy == AdaptiveRestrictions.no) {
//			if(params.seed !=4711 | params.restrictedFraction!=0.0 | params.trigger!=10)
//				return null;
//		}

		SnzBerlinProductionScenario module = getBindings(id, params);

		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);

		List<String> subdistricts = Arrays.asList("Spandau", "Neukoelln", "Reinickendorf",
				"Charlottenburg_Wilmersdorf", "Marzahn_Hellersdorf", "Mitte", "Pankow", "Friedrichshain_Kreuzberg",
				"Tempelhof_Schoeneberg", "Treptow_Koepenick", "Lichtenberg", "Steglitz_Zehlendorf");
		episimConfig.setDistricts(subdistricts);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		// General setup of adaptive restrictions
		LocalDate minDate = LocalDate.MIN;

		double workTrigger = params.trigger;
		double leisureTrigger = params.trigger;
		double eduTrigger = params.trigger;
		double shopErrandsTrigger = params.trigger;

		double openFraction = 0.9;
		//		double restrictedFraction = 0.6;
		double restrictedFraction = params.restrictedFraction;

		// global
		String startDate = DEBUG ? "2020-04-05" : "2020-07-14";
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
							.restrict(minDate, Restriction.of(restrictedFraction), "work")
							.restrict(minDate, Restriction.of(restrictedFraction), "shop_daily")
							.restrict(minDate, Restriction.of(restrictedFraction), "shop_other")
							.restrict(minDate, Restriction.of(restrictedFraction), "errands")
							.restrict(minDate, Restriction.of(restrictedFraction), "business")
							.restrict(minDate, Restriction.of(restrictedFraction), "visit")
							.restrict(minDate, Restriction.of(restrictedFraction), "leisure")
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
							.restrict(minDate, Restriction.of(openFraction), "educ_higher")
							.restrict(minDate, Restriction.of(openFraction), "educ_kiga")
							.restrict(minDate, Restriction.of(openFraction), "educ_primary")
							.restrict(minDate, Restriction.of(openFraction), "educ_secondary")
							.restrict(minDate, Restriction.of(openFraction), "educ_tertiary")
							.restrict(minDate, Restriction.of(openFraction), "educ_other")
							.restrict(minDate, Restriction.ofLocationBasedRf(new HashMap<>()), "work", "shop_daily", "shop_other", "errands", "business", "visit", "leisure", "educ_higher", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
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
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "work")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "shop_daily")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "shop_other")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "errands")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "business")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "visit")
							.restrict(minDate, constructRestrictionWithLocalAndGlobalRf(subdistricts, restrictedFraction), "leisure")
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

		// TRACING
//		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
//
//		if (DEBUG) {
//			tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(5);
//		}
//
//		tracingConfig.setTracingProbability(Map.of(
//				LocalDate.of(2020, 4, 1), 0.5,
//				LocalDate.parse(startDate), params.tracingProbability
//		));
//
//		tracingConfig.setTracingDelay_days(Map.of(
//				LocalDate.of(2020, 4, 1), 5,
//				LocalDate.parse(startDate), params.tracingDelay
//		));
//
//		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
//				LocalDate.of(2020, 4, 1), 0,
//				LocalDate.parse(startDate), params.tracingCapacity
//		));

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

		@GenerateSeeds(10)
		public long seed;

		//		@Parameter({0.6})
		@Parameter({0.0, 0.2, 0.4, 0.6})
		double restrictedFraction;
		//
		//		@Parameter({0.9})
		//		double openFraction;

		//		@Parameter({10,100})
		//		@Parameter({Integer.MAX_VALUE, 100.})
		@Parameter({10, 25, 50, 75, 100})
		double trigger;


		// Tracing:
		//
		//		@IntParameter({0, 2000, Integer.MAX_VALUE})
		//		int tracingCapacity;
		//
		//		@IntParameter({5, 1})
		//		int tracingDelay;
		//
		//		@Parameter({0.5, 1.0})
		//		double tracingProbability;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRBatchMasterC.class.getName(),
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


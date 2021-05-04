package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinProductionScenario.*;

import javax.annotation.Nullable;
import java.util.ArrayList;


/**
 * Runs for symmetric Berlin week model
 */
public class BerlinPaperInterventions implements BatchRun<BerlinPaperInterventions.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {

		if (params == null) return new SnzBerlinProductionScenario.Builder().createSnzBerlinProductionScenario();

		return getModule(params);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "basePaperInterventions");
	}

//	@Override
//	public int getOffset() {
//		return 1000;
//	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = getModule(params);
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(1.7E-5 * params.thetaFactor);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		String restrictionDate = "2020-04-01";
		ArrayList<String> acts = new ArrayList<>();

		switch (params.restrictedAct) {
		case "home":
			acts.add("home");
			break;
		case "work_business":
			acts.add("work");
			acts.add("business");
			break;
		case "schools":
			acts.add("educ_primary");
			acts.add("educ_secondary");
			acts.add("educ_tertiary");
			acts.add("educ_other");
			break;
		case "shop_errands":
			acts.add("errands");
			acts.add("shop_other");
			acts.add("shop_daily");
			break;
		case "leisure":
			acts.add("leisure");
			break;
		case "publicTransport":
			acts.add("pt");
			break;
		case "university":
			acts.add("educ_higher");
			break;
		case "dayCare":
			acts.add("educ_kiga");
			break;
		default:
			throw new IllegalArgumentException("Unknown restrictedAct: " + params.restrictedAct);
		}

		switch (params.restriction) {
		case "75pct":
			for (String act : acts) builder.restrict(restrictionDate, 0.75, act);
			break;
		case "50pct":
			for (String act : acts) builder.restrict(restrictionDate, 0.5, act);
			break;
		case "90pctN95Masks":
			for (String act : acts) builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.N95, 0.9), act);
			break;
		case "90pctClothMasks":
			for (String act : acts) builder.restrict(restrictionDate, Restriction.ofMask(FaceMask.CLOTH, 0.9), act);
			break;
		default:
			throw new IllegalArgumentException("Unknown restriction: " + params.restriction);
		}

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	private SnzBerlinProductionScenario getModule(Params params) {
		DiseaseImport diseaseImport = DiseaseImport.no;
		Restrictions restrictions = Restrictions.no;
		Masks masks = Masks.no;
		Tracing tracing = Tracing.no;
		ChristmasModel christmasModel = ChristmasModel.no;
		WeatherModel weatherModel = WeatherModel.no;

		if (params.run.contains("2_withSpringImport")) {
			diseaseImport = DiseaseImport.onlySpring;
		}
		if (params.run.contains("3_withRestrictions")) {
			diseaseImport = DiseaseImport.onlySpring;
			restrictions = Restrictions.yes;
		}
		if (params.run.contains("4_withWeatherModel_175_175")) {
			diseaseImport = DiseaseImport.onlySpring;
			restrictions = Restrictions.yes;
			weatherModel = WeatherModel.midpoints_175_175;
		}
		if (params.run.contains("5_withWeatherModel_175_250")) {
			diseaseImport = DiseaseImport.onlySpring;
			restrictions = Restrictions.yes;
			weatherModel = WeatherModel.midpoints_175_250;
		}
		if (params.run.contains("6_withSummerImport")) {
			diseaseImport = DiseaseImport.yes;
			restrictions = Restrictions.yes;
			weatherModel = WeatherModel.midpoints_175_250;
		}

		if (params.withMasks.contains("yes")) {
			masks = Masks.yes;
		}
		if (params.withTracing.contains("yes")) {
			tracing = Tracing.yes;
		}

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder().setDiseaseImport( diseaseImport ).setRestrictions( restrictions ).setMasks( masks ).setTracing(
				tracing ).setChristmasModel(christmasModel).setWeatherModel(weatherModel).createSnzBerlinProductionScenario();
		return module;
	}

	public static final class Params {

		@GenerateSeeds(10)
		public long seed;

//		@Parameter({1.8E-5, 1.7E-5, 1.6E-5, 1.5E-5, 1.4E-5, 1.3E-5, 1.27E-5, 1.1E-5, 1.E-5})
//		double theta;

		@StringParameter({"1_base"})
		public String run;

		@StringParameter({"no"})
		public String withMasks;

		@StringParameter({"no"})
		public String withTracing;

		@StringParameter({"no"})
		public String withLeisureFactor;

		@StringParameter({"home", "work_business", "schools", "shop_errands", "leisure", "publicTransport", "university", "dayCare"})
		public String restrictedAct;

		@StringParameter({"75pct", "50pct", "90pctN95Masks", "90pctClothMasks"})
		public String restriction;

		@Parameter({0.6, 0.8, 1.0, 1.2})
		double thetaFactor;

	}


}

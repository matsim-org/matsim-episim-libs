package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimConfigGroup.InfectionParams;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import javax.annotation.Nullable;
import java.util.Map;

import static org.matsim.run.batch.ContactModels.*;


/**
 * Interventions for symmetric Berlin week model with different contact models
 */
public class BerlinContactModelsAndInterventions implements BatchRun<BerlinContactModelsAndInterventions.Params> {



	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		if (params == null)
			return new SnzBerlinWeekScenario2020();

		boolean withModifiedCi = !params.contactModel.equals(OLD);
		return new SnzBerlinWeekScenario2020(25, false, withModifiedCi, ContactModels.MODELS.get(params.contactModel));
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "contactModels&Interventions");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		int nSpaces = 1;
		
		if (params.contactModel.equals(SYMMETRIC_NEW_NSPACES_20)) nSpaces = 20; 
		
		boolean withModifiedCi = !params.contactModel.equals(OLD);

		SnzBerlinWeekScenario2020 module = new SnzBerlinWeekScenario2020(25, false, withModifiedCi, MODELS.get(params.contactModel));
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		for (InfectionParams infParams : episimConfig.getInfectionParams()) {
			if (!infParams.includesActivity("home")) infParams.setSpacesPerFacility(nSpaces);
		}

		episimConfig.setStartDate("2020-02-18");

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);

		ConfigBuilder builder = FixedPolicy.config();

		{
			if (params.restriction.startsWith("none") || params.restriction.startsWith("calibr")) ;

			else if (params.restriction.equals("0.9CLOTH@PT&SHOP"))
				builder.restrict(20, Restriction.ofMask(FaceMask.CLOTH, 0.9), "pt", "shop_daily", "shop_other");
			
			else if (params.restriction.equals("0.9CLOTH@EDU"))
				builder.restrict(20, Restriction.ofMask(FaceMask.CLOTH, 0.9), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

			else if (params.restriction.equals("workBusiness50")) builder.restrict(20, 0.5, "work", "business");
			else if (params.restriction.equals("workBusiness0")) builder.restrict(20, 0., "work", "business");

			else if (params.restriction.equals("leisure50")) builder.restrict(20, 0.5, "leisure");
			else if (params.restriction.equals("leisure0")) builder.restrict(20, 0., "leisure");

			else if (params.restriction.equals("shop50")) builder.restrict(20, 0.5, "shop_daily", "shop_other");
			else if (params.restriction.equals("shop0")) builder.restrict(20, 0., "shop_daily", "shop_other");
			
			else if (params.restriction.equals("educ_kiga50")) builder.restrict(20, 0.5, "educ_kiga");
			else if (params.restriction.equals("educ_kiga0")) builder.restrict(20, 0., "educ_kiga");

			else if (params.restriction.equals("educ_school50"))
				builder.restrict(20, 0.5, "educ_primary", "educ_secondary", "educ_tertiary");
			
			else if (params.restriction.equals("educ_school0"))
				builder.restrict(20, 0., "educ_primary", "educ_secondary", "educ_tertiary");
			
			else if (params.restriction.equals("educ_higher50")) builder.restrict(20, 0.5, "educ_higher");
			else if (params.restriction.equals("educ_higher0")) builder.restrict(20, 0., "educ_higher");

			else if (params.restriction.equals("outOfHome50")) builder.restrict(20, 0.5, AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
			else if (params.restriction.equals("outOfHome0")) builder.restrict(20, 0., AbstractSnzScenario2020.DEFAULT_ACTIVITIES);

			else if (params.restriction.contains("tracing")) {
				tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(1);
				tracingConfig.setTracingCapacity_pers_per_day(Map.of(
						episimConfig.getStartDate(), 0,
						episimConfig.getStartDate().plusDays(20), Integer.MAX_VALUE)
				);
				tracingConfig.setTracingProbability(0.75);
			} 
			else throw new RuntimeException("Measure not implemented: " + params.restriction);
		}

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(20)
		public long seed;

		@StringParameter({"none", "tracing75", "0.9CLOTH@PT&SHOP", "0.9CLOTH@EDU", "workBusiness50", "workBusiness0", "leisure50", "leisure0", "shop50", "shop0", "educ_kiga50", "educ_kiga0",
			"educ_school50", "educ_school0", "educ_higher50", "educ_higher0", "outOfHome50", "outOfHome0"})
//		@StringParameter({"calibr"})
		public String restriction;

		@StringParameter({OLD, SYMMETRIC_OLD, SYMMETRIC_NEW_NSPACES_1, SYMMETRIC_NEW_NSPACES_20})
//		@StringParameter({OLD, SYMMETRIC_OLD, SYMMETRIC_NEW_NSPACES_1, SYMMETRIC_NEW_NSPACES_20, PAIRWISE})
		public String contactModel;

	}


}

package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import javax.annotation.Nullable;
import java.util.Map;


/**
 * Runs comparing different contact models for berlin.
 */
public class ContactModels implements BatchRun<ContactModels.Params> {

	static final String OLD = "OLD";
	static final String SYMMETRIC_OLD = "SYMMETRIC_OLD";
	static final String SYMMETRIC_NEW_NSPACES_1 = "SYMMETRIC_NEW_NSPACES_1";
	static final String SYMMETRIC_NEW_NSPACES_20 = "SYMMETRIC_NEW_NSPACES_20";
	static final String DIRECT = "DIRECT";

	static final Map<String, Class<? extends ContactModel>> MODELS = Map.of(
			OLD, DefaultContactModel.class,
			SYMMETRIC_OLD, OldSymmetricContactModel.class,
			SYMMETRIC_NEW_NSPACES_1, SymmetricContactModel.class,
			SYMMETRIC_NEW_NSPACES_20, SymmetricContactModel.class,
			DIRECT, DirectContactModel.class
	);

	static SnzBerlinWeekScenario2020 getModule(Params params) {
		if (params == null)
			return new SnzBerlinWeekScenario2020();

		boolean withModifiedCi = params.modifiedCI.equals("yes");
		boolean withDiseaseImport = params.diseaseImport.equals("yes");
		return new SnzBerlinWeekScenario2020(25, withDiseaseImport, withModifiedCi, ContactModels.MODELS.get(params.contactModel));
	}

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return getModule(params);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "contactModels");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020 module = getModule(params);
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setStartDate("2020-02-16");

		// assumes that 20 is the default...
		if (params.contactModel.equals(SYMMETRIC_NEW_NSPACES_1)) {
			for (EpisimConfigGroup.InfectionParams infParams : episimConfig.getInfectionParams()) {
				if (!infParams.includesActivity("home")) infParams.setSpacesPerFacility(1);
			}

			// separate calib param for nspaces = 1
			if (params.modifiedCI.equals("yes"))
				episimConfig.setCalibrationParameter(2.44e-5);
			else {
				episimConfig.setCalibrationParameter(7.72065E-5);
			}

			FixedPolicy.ConfigBuilder policy = FixedPolicy.parse(episimConfig.getPolicy());

			// separate ci correction for nspaces 1
			policy.restrict("2020-03-06", Restriction.ofCiCorrection(0.384), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);

			episimConfig.setPolicy(FixedPolicy.class, policy.build());
		}

		if (params.unrestricted.equals("yes")) {
			episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config().build());
		} else {
			// set in base class
		}

		config.global().setRandomSeed(params.seed);

		return config;
	}

	public static final class Params {

		@GenerateSeeds(30)
		public long seed;

		@StringParameter({OLD, SYMMETRIC_OLD, SYMMETRIC_NEW_NSPACES_1, SYMMETRIC_NEW_NSPACES_20, DIRECT})
		public String contactModel;

		@StringParameter({"yes", "no"})
		public String unrestricted;

		@StringParameter({"yes"})
		public String modifiedCI;

		@StringParameter({"yes"})
		public String diseaseImport;

	}

}

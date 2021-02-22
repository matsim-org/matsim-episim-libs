package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;


/**
 * This batch run explores different random seeds via different snapshot times.
 */
public class StabilityRuns implements BatchRun<StabilityRuns.Params> {

	@Override
	public Metadata getMetadata() {
		return Metadata.of("paper", "stability");
	}

	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {
		if (params == null)
			return new SnzBerlinProductionScenario.Builder().createSnzBerlinProductionScenario();

		return new SnzBerlinProductionScenario.Builder()
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setRestrictions(params.unrestricted.equals("yes") ? SnzBerlinProductionScenario.Restrictions.no
						: SnzBerlinProductionScenario.Restrictions.yes)
				.setDiseaseImport(params.diseaseImport)
				.createSnzBerlinProductionScenario();
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		Config config = getBindings(id, params).config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(params.theta);

		config.global().setRandomSeed(params.seed);

		return config;
	}

	public static final class Params {

		@GenerateSeeds(20)
		long seed;

		@StringParameter({"yes", "no"})
		String unrestricted;

		@Parameter({1.8E-5, 1.7E-5, 1.6E-5, 1.5E-5, 1.4E-5, 1.36E-5, 1.27E-5, 1.1E-5, 1.E-5})
		double theta;

		@EnumParameter(SnzBerlinProductionScenario.DiseaseImport.class)
		SnzBerlinProductionScenario.DiseaseImport diseaseImport;


	}

}

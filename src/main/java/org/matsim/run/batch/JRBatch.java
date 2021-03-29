package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.apache.commons.csv.CSVFormat;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.TracingConfigGroup.CapacityType;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Interventions for symmetric Berlin week model with different contact models
 */
public class JRBatch implements BatchRun<JRBatch.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder().setSnapshot(SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
	}

	//	@Override
	//	public int getOffset() {
	//		return 400;
	//	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder().setSnapshot(
				SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
		Config config = module.config();
		//		config.global().setRandomSeed(params.seed);

		config.global().setRandomSeed(7564655870752979346L);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setStartFromSnapshot("");
//		episimConfig.setSnapshotInterval();

		VaccinationConfigGroup vaccinationConfigGroup = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vaccinationConfigGroup.setEffectiveness(params.dailyInitialVaccinations);


		return config;
	}

	public static final class Params {

		//		@GenerateSeeds(2)
		//		public long seed;

		@IntParameter({3000, 10000})
		int dailyInitialVaccinations;

		@StringParameter({"restrictive"})
		public String christmasModel;

		@StringParameter({"closed", "open", "open&masks", "50%&masks", "50%open"})
		public String schools;

		@StringParameter({"no", "ffp"})
		public String work;

		@StringParameter({"no", "20-5", "22-5"})
		public String curfew;

		@StringParameter({"2020-12-15", "2020-11-15", "2020-10-15"})
		String newVariantDate;

		@StringParameter({"no", "yes", "yesUntil80", "no100%"})
		String extrapolateRestrictions;

		@Parameter({1.35})
		double newVariantInfectiousness;
	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRBatch.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(330),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}


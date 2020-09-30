package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SyntheticScenario;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Using the synthetic scenario, different facility are compared.
 */
public class EventSizes implements BatchRun<EventSizes.Params> {

	private static final Logger log = LogManager.getLogger(EventSizes.class);

	private final List<CSVRecord> csv = new ArrayList<>();

	private final Function<EventSizes.Params, SyntheticBatch.Params> synParams = (p) -> new SyntheticBatch.Params(
			20000, 1, (int) (20d * p.divider), 1,  (int) (10d / p.divider), p.contactModel, 3
			);

	public EventSizes() {
		try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("syn.result.csv")), CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
			csv.addAll(parser.getRecords());
		} catch (Exception e) {
			log.warn("Could not read calibration params", e);
		}
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("synthetic", "eventSizes");
	}

	@Nullable
	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {

		SyntheticBatch.Params p = synParams.apply(params);
		p.seed = params.seed;

		return new SyntheticScenario(p);
	}

	@Nullable
	@Override
	public Config prepareConfig(int id, Params params) {

		SyntheticBatch.Params p = synParams.apply(params);
		p.seed = params.seed;

		Config config = new SyntheticScenario(p).config();

		double calib = BatchRun.lookup(p, csv, "initialPerFacility", "numFacilities");
		// adjust calibration because didn't work
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(calib);

		if (params.reduction.equals("uniform_0.5"))
			episimConfig.setPolicy(FixedPolicy.class,
					FixedPolicy.config().restrict(1, 0.5, "outside")
							.build()
			);
		else if (params.reduction.equals("closing_0.5")) {

			FixedPolicy.ConfigBuilder policy = FixedPolicy.config();

			for (int i = 1; i < 200; i++) {

				List<String> closed = new ArrayList<>();
				for (int n = 0; n <= p.numFacilities; n++) {
					if (n % 2 == i % 2)
						closed.add("outside" + n);
				}

				policy.restrict(i, Restriction.ofClosedFacilities(closed), "outside");

			}

			episimConfig.setPolicy(FixedPolicy.class, policy.build());
		}

		log.info("Using param {}", calib);

		return config;
	}

	public static final class Params {

		@GenerateSeeds(30)
		public long seed;

		@Parameter({1, 2, 5, 10})
		public double divider;

		@StringParameter({"none", "uniform_0.5", "closing_0.5"})
		public String reduction;

		@ClassParameter({DefaultContactModel.class, SqrtContactModel.class, OldSymmetricContactModel.class,
				SymmetricContactModel.class, DirectContactModel.class})
		public Class<? extends ContactModel> contactModel;

		{
			try {
				contactModel = (Class<? extends ContactModel>) ClassLoader.getSystemClassLoader().loadClass(System.getProperty("syn.contactModel", "org.matsim.episim.model.DefaultContactModel"));
			} catch (ClassNotFoundException e) {
				System.err.println("Could not load class for syn contact model: " + e.getMessage());
			}
		}
	}

}

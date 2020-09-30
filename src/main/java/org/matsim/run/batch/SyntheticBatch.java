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
import org.matsim.run.modules.SyntheticScenario;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares different contact models and parameters on the synthetic scenario
 */
public class SyntheticBatch implements BatchRun<SyntheticBatch.Params> {

	private static final Logger log = LogManager.getLogger(SyntheticBatch.class);

	private final List<CSVRecord> csv = new ArrayList<>();

	public SyntheticBatch() {
		try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of("syn.result.csv")), CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
			csv.addAll(parser.getRecords());
		} catch (Exception e) {
			log.warn("Could not read calibration params", e);
		}
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("synthetic", "default");
	}

	@Nullable
	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SyntheticScenario(params);
	}

	@Nullable
	@Override
	public Config prepareConfig(int id, Params params) {
		Config config = new SyntheticScenario(params).config();

		// these don't need max contact setting
		if (params.contactModel == DirectContactModel.class || params.contactModel == SymmetricContactModel.class)
			if (params.maxContacts > 3)
				return null;

		double param = BatchRun.lookup(params, csv);

		log.info("Using param {}", param);

		ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class).setCalibrationParameter(param);

		return config;
	}

	/**
	 * Params for the synthetic batch run. This class is also as argument to {@link SyntheticScenario} directly.
	 * Params are also reed from properties to allow for automated calibration.
	 */
	public static final class Params {

		@GenerateSeeds(50)
		public long seed;

		@IntParameter({20000})
		public int persons = (int) Double.parseDouble(System.getProperty("syn.persons", "100"));

		@IntParameter(1)
		public int homeSize = (int) Double.parseDouble(System.getProperty("syn.homeSize", "1"));

		@IntParameter(20)
		public int numFacilities = (int) Double.parseDouble(System.getProperty("syn.numFacilities", "1"));

		// group size will be persons / facilities

		@IntParameter(1)
		public int numActivitiesPerDay = (int) Double.parseDouble(System.getProperty("syn.numActivitiesPerDay", "1"));

		@IntParameter(50)
		public int age = (int) Double.parseDouble(System.getProperty("syn.age", "50"));

		@IntParameter({3, 10})
		public int maxContacts = (int) Double.parseDouble(System.getProperty("syn.maxContacts", "3"));

		@IntParameter({10})
		public int initialPerFacility = (int) Double.parseDouble(System.getProperty("syn.initialPerFacility", "10"));

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

		public Params() {
		}

		public Params(int persons, int homeSize, int numFacilities,
					  int numActivitiesPerDay, Class<? extends ContactModel> contactModel, int maxContacts) {
			this.persons = persons;
			this.homeSize = homeSize;
			this.numFacilities = numFacilities;
			this.numActivitiesPerDay = numActivitiesPerDay;
			this.contactModel = contactModel;
			this.maxContacts = maxContacts;
		}

	}

}

package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.episim.BatchRun;
import org.matsim.episim.model.*;
import org.matsim.run.modules.SyntheticScenario;

import javax.annotation.Nullable;

/**
 * Compares different contact models and parameters on the synthetic scenario
 */
public class SyntheticModel implements BatchRun<SyntheticModel.Params> {

	@Nullable
	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SyntheticScenario(params);
	}

	@Nullable
	@Override
	public Config prepareConfig(int id, Params params) {
		Config config = new SyntheticScenario(params).config();
		return config;
	}

	/**
	 * Params for the synthetic batch run. This class is also as argument to {@link SyntheticScenario} directly.
	 * Params are also reed from properties to allow for automated calibration.
	 */
	public static final class Params {

		@GenerateSeeds(100)
		public long seed;

		@IntParameter(10000)
		public int persons = Integer.parseInt(System.getProperty("syn.persons", "10000"));

		@IntParameter(1)
		public int homeSize = Integer.parseInt(System.getProperty("syn.homeSize", "1"));

		@IntParameter(1)
		public int numFacilities = Integer.parseInt(System.getProperty("syn.numFacilities", "1"));

		@IntParameter(1)
		public int numActivitiesPerDay = Integer.parseInt(System.getProperty("syn.numActivitiesPerDay", "1"));

		@IntParameter(50)
		public int age = Integer.parseInt(System.getProperty("syn.age", "50"));

		@IntParameter({3, 10})
		public int maxContacts = Integer.parseInt(System.getProperty("syn.maxContacts", "3"));

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

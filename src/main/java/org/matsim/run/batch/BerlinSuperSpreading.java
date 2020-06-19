package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import java.util.Map;
import java.util.SplittableRandom;

import static org.matsim.episim.EpisimUtils.nextLogNormalFromMeanAndSigma;

/**
 * Run to analyze different viral load and susceptibility for persons.
 */
public class BerlinSuperSpreading implements BatchRun<BerlinSuperSpreading.Params> {

	public static final String SUSCEPTIBILITY = "susceptibility";
	public static final String VIRAL_LOAD = "viralLoad";

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "superSpreading3");
	}

	@Override
	public AbstractModule getBindings(int id, Object params) {
		return new Bindings((Params) params);
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinScenario25pct2020 module = new SnzBerlinScenario25pct2020();

		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		// increase calib parameter
		episimConfig.setCalibrationParameter(params.calib);

		// start a bit earlier
		episimConfig.setStartDate("2020-02-16");

		// evtl. Ci correction auf 0.35

		return config;
	}

	public static final class Params {

		//@GenerateSeeds(200)
		//private long seed;

		@Parameter({0.000_012_5, 0.000_013_0})
		private double calib;

		@Parameter({0.75})
		private double sigmaInfect;

		@Parameter({0.75})
		private double sigmaSusc;
	}


	private static final class Bindings extends AbstractModule {

		private final Params params;

		public Bindings(Params params) {
			this.params = params;
		}

		@Override
		protected void configure() {
			bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);
			bind(InfectionModel.class).to(CustomInfectionModel.class).in(Singleton.class);
		}

		@Provides
		@Singleton
		public Scenario scenario(Config config) {

			config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.ignore);

			// save some time for not needed inputs
			config.facilities().setInputFile(null);
			config.vehicles().setVehiclesFile(null);

			ControlerUtils.checkConfigConsistencyAndWriteToLog(config, "before loading scenario");

			final Scenario scenario = ScenarioUtils.loadScenario(config);

			SplittableRandom rnd = new SplittableRandom(4715);
			for (Person person : scenario.getPopulation().getPersons().values()) {
				person.getAttributes().putAttribute(VIRAL_LOAD, nextLogNormalFromMeanAndSigma(rnd, 1, params.sigmaInfect));
				person.getAttributes().putAttribute(SUSCEPTIBILITY, nextLogNormalFromMeanAndSigma(rnd, 1, params.sigmaSusc));
			}

			return scenario;
		}
	}

	private static class CustomInfectionModel implements InfectionModel {

		private final FaceMaskModel maskModel;
		private final EpisimConfigGroup episimConfig;

		@Inject
		CustomInfectionModel(Config config, FaceMaskModel maskModel) {
			this.maskModel = maskModel;
			this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		}

		@Override
		public double calcInfectionProbability(EpisimPerson target, EpisimPerson infector, Map<String, Restriction> restrictions,
											   EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2, double jointTimeInContainer) {

			double contactIntensity = Math.min(act1.getContactIntensity(), act2.getContactIntensity());

			Map<String, Restriction> r = restrictions;
			// ci corr can not be null, because sim is initialized with non null value
			double ciCorrection = Math.min(r.get(act1.getContainerName()).getCiCorrection(), r.get(act2.getContainerName()).getCiCorrection());

			// note that for 1pct runs, calibParam is of the order of one, which means that for typical times of 100sec or more, exp( - 1 * 1 * 100 ) \approx 0, and
			// thus the infection proba becomes 1.  Which also means that changes in contactIntensity has no effect.  kai, mar'20

			double susceptibility = (double) target.getAttributes().getAttribute(SUSCEPTIBILITY);
			double infectability = (double) infector.getAttributes().getAttribute(VIRAL_LOAD);

			return 1 - Math.exp(-episimConfig.getCalibrationParameter() * susceptibility * infectability * contactIntensity * jointTimeInContainer * ciCorrection
					* maskModel.getWornMask(infector, act2, r.get(act2.getContainerName())).shedding
					* maskModel.getWornMask(target, act1, r.get(act1.getContainerName())).intake
			);
		}

	}

}

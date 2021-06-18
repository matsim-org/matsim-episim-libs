package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;

import javax.annotation.Nullable;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.SplittableRandom;

import static org.matsim.episim.EpisimUtils.nextLogNormalFromMeanAndSigma;
import static org.matsim.episim.model.InfectionModelWithViralLoad.SUSCEPTIBILITY;
import static org.matsim.episim.model.InfectionModelWithViralLoad.VIRAL_LOAD;

/**
 * Percolation runs for berlin
 */
public class BerlinPercolation implements BatchRun<BerlinPercolation.Params> {

	private static final Logger log = LogManager.getLogger(BerlinPercolation.class);

	@Override
	public LocalDate getDefaultStartDate() {
		return LocalDate.of(2021, 1, 3);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "percolation");
	}


	@Nullable
	@Override
	public Module getBindings(int id, @Nullable Params params) {
		/// TODO hardcoded now and needs to be adjusted before runs
		/// XXX
		return new Binding(Params.CURRENT, true);
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		Config config = new Binding(params.contactModel, params.superSpreading).config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

		episimConfig.setWriteEvents(EpisimConfigGroup.WriteEvents.none);

		episimConfig.setStartDate(getDefaultStartDate());
		episimConfig.setInfections_pers_per_day(Map.of(
				getDefaultStartDate(), 1
		));
		episimConfig.setInitialInfections(1);
		episimConfig.setInitialInfectionDistrict(null);


		if (params.contactModel.equals(Params.OLD)) {

			episimConfig.setCalibrationParameter(1.07e-5 * params.fraction);

		} else {
			// adjust calib param
			episimConfig.setCalibrationParameter(1.7E-5 * 0.8 * params.fraction);
		}

		// no tracing and vaccinations
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);
		vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(getDefaultStartDate(), 0));


		FixedPolicy.ConfigBuilder policy = FixedPolicy.parse(episimConfig.getPolicy());

		// restriction fixed at beginning of january.

		policy.clearAfter(getDefaultStartDate().plusDays(1).toString());
		episimConfig.setPolicy(FixedPolicy.class, policy.build());


		return config;
	}

	public static final class Params {

		private final static String OLD = "oldSymmetric";
		private final static String CURRENT = "symmetric";

		@GenerateSeeds(value = 5000, seed = 6)
		public long seed;

		public String contactModel = CURRENT;
		public boolean superSpreading = true;

		@Parameter({0.8, 0.9, 1.0, 1.1, 1.2})
		//@Parameter({0.5, 0.55, 0.6, 0.65, 0.7})
		public double fraction;

	}

	/**
	 * Binding for this batch. Also needed for correct input files.
	 */
	private static final class Binding extends AbstractModule {

		private final AbstractModule delegate;
		private final boolean superSpreading;

		public Binding(String contactModel, boolean superSpreading) {

			if (contactModel.equals(Params.OLD))
				delegate = new SnzBerlinWeekScenario2020(25, false, false, OldSymmetricContactModel.class);
			else
				delegate = new SnzBerlinProductionScenario.Builder()
						.setDiseaseImport(SnzBerlinProductionScenario.DiseaseImport.no)
						.setRestrictions(SnzBerlinProductionScenario.Restrictions.yes)
						.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
						.setTracing(SnzBerlinProductionScenario.Tracing.no)
						.setWeatherModel(SnzBerlinProductionScenario.WeatherModel.no)
						.setInfectionModel(DefaultInfectionModel.class)
						.createSnzBerlinProductionScenario();

			this.superSpreading = superSpreading;
		}

		@Override
		protected void configure() {
			if (superSpreading)
				bind(InfectionModel.class).to(InfectionModelWithViralLoad.class).in(Singleton.class);
			else
				bind(InfectionModel.class).to(DefaultInfectionModel.class).in(Singleton.class);

			if (delegate instanceof SnzBerlinWeekScenario2020)
				bind(ContactModel.class).to(OldSymmetricContactModel.class).in(Singleton.class);
			else
				bind(ContactModel.class).to(SymmetricContactModel.class).in(Singleton.class);

			bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);
		}

		@Provides
		@Singleton
		public Config config() {

			Config config;

			if (delegate instanceof SnzBerlinWeekScenario2020)
				config = ((SnzBerlinWeekScenario2020) delegate).config();
			else
				config = ((SnzBerlinProductionScenario) delegate).config();

			EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
			episimConfig.clearInputEventsFiles();
			episimConfig.addInputEventsFile(SnzBerlinProductionScenario.INPUT.resolve("be_2020-week_snz_episim_events_wt_25pt_split.xml.gz").toString())
					.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

			return config;
		}

		@Provides
		@Singleton
		public Scenario scenario(Config config) {
			Scenario scenario;
			if (delegate instanceof SnzBerlinWeekScenario2020)
				scenario = ((SnzBerlinWeekScenario2020) delegate).scenario(config);
			else
				scenario = ((SnzBerlinProductionScenario) delegate).scenario(config);

			if (superSpreading) {
				SplittableRandom rnd = new SplittableRandom(4715);
				for (Person person : scenario.getPopulation().getPersons().values()) {
					person.getAttributes().putAttribute(VIRAL_LOAD, nextLogNormalFromMeanAndSigma(rnd, 1, 1));
					person.getAttributes().putAttribute(SUSCEPTIBILITY, nextLogNormalFromMeanAndSigma(rnd, 1, 1));
				}
			}

			return scenario;
		}

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, BerlinPercolation.class.getName(),
				RunParallel.OPTION_PARAMS, BerlinPercolation.Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString(2),
				RunParallel.OPTION_ITERATIONS, Integer.toString(10000),
		};

		RunParallel.main(args2);
	}
}

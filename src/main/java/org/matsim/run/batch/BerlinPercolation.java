package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * Percolation runs for berlin
 */
public class BerlinPercolation implements BatchRun<BerlinPercolation.Params> {

	private static final Logger log = LogManager.getLogger(BerlinPercolation.class);

	@Override
	public LocalDate getDefaultStartDate() {
		return LocalDate.of(2020, 1, 1);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "percolation");
	}


	@Nullable
	@Override
	public Module getBindings(int id, @Nullable Params params) {
		return new Binding();
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		Config config = new Binding().config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);


		episimConfig.setStartDate(getDefaultStartDate());
		episimConfig.setInfections_pers_per_day(Map.of(
				getDefaultStartDate(), 1
		));
		episimConfig.setInitialInfections(1);
		episimConfig.setInitialInfectionDistrict(null);

		// reduced calib param
		episimConfig.setCalibrationParameter(2.54e-5 * params.fraction);

		// no tracing and vaccinations
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);
		vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(getDefaultStartDate(), 0));

		// unrestricted
		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();
		episimConfig.setPolicy(FixedPolicy.class, builder.build());


		return config;
	}

	public static final class Params {

		@GenerateSeeds(value = 1500)
		public long seed;

		@Parameter({0.07, 0.08})
		public double fraction;

	}

	/**
	 * Binding for this batch. Also needed for correct input files.
	 */
	private static final class Binding extends AbstractModule {

		private final SnzBerlinProductionScenario delegate = new SnzBerlinProductionScenario.Builder()
				.setDiseaseImport(SnzBerlinProductionScenario.DiseaseImport.no)
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setTracing(SnzBerlinProductionScenario.Tracing.no)
				.setInfectionModel(DefaultInfectionModel.class)
				.createSnzBerlinProductionScenario();

		@Override
		protected void configure() {
			bind(InfectionModel.class).to(DefaultInfectionModel.class);
			bind(ContactModel.class).to(SymmetricContactModel.class);
			bind(ProgressionModel.class).to(AgeDependentProgressionModel.class);
		}

		@Provides
		@Singleton
		public Config config() {

			Config config = delegate.config();

			EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
			episimConfig.clearInputEventsFiles();
			episimConfig.addInputEventsFile(SnzBerlinProductionScenario.INPUT.resolve("be_2020-week_snz_episim_events_wt_25pt_split.xml.gz").toString())
					.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

			return config;
		}

		@Provides
		@Singleton
		public Scenario scenario(Config config) {
			return delegate.scenario(config);
		}

	}

}

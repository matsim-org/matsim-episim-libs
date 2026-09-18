package org.matsim.run;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimRunner;
import org.matsim.episim.SyntheticBatch;
import org.matsim.episim.SyntheticScenario;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.DefaultContactModel;
import org.matsim.testcases.MatsimTestUtils;

import java.time.LocalDate;
import java.util.Map;

/**
 * Regression test for immunity in the scenarios that take severity from antibodies.
 *
 * <p>{@link RunEpisimIntegrationTest} only runs the default {@code EpisimModule}, where immunity comes from the
 * effectiveness curves. Every Snz and Cologne scenario binds {@code AgeDependentDiseaseStatusTransitionModel}
 * instead and, with it, takes the course of the disease from antibody levels. This test covers that pairing with
 * the synthetic scenario, which binds the same models and is cheap to run.</p>
 *
 * <p>The references in {@code test/input/org/matsim/run/ImmunityRegressionTest} were produced by the code before
 * {@code ImmunityModel} existed (commit 30cd74d7) and matched the refactored code byte for byte. A run that binds
 * {@code LegacyCurveImmunityModel} here instead of {@code LegacySplitImmunityModel} produces 89 severe cases instead of 16
 * in {@link #antibodySeverityWithoutVaccination}, so the comparison does detect a wrong source of immunity.</p>
 *
 * <p>The three cases exercise different paths. Note that the calibration parameter multiplies contact time in
 * seconds, so values like {@code 0.02} still infect everybody within days; only around {@code 1e-5} does the
 * epidemic leave room for vaccinations to take effect before people are infected.</p>
 */
public class ImmunityRegressionTest {

	private static final int ITERATIONS = 60;

	@RegisterExtension
	public MatsimTestUtils utils = new MatsimTestUtils();

	/**
	 * Everybody is infected within days and nobody is vaccinated: severity through antibodies is what varies.
	 */
	@Test
	public void antibodySeverityWithoutVaccination() {
		run(100, false);
	}

	/**
	 * Slower epidemic with vaccinations: vaccinated persons are infected, so the vaccine curves on the infection
	 * side are exercised as well.
	 */
	@Test
	public void vaccinationDuringEpidemic() {
		run(5e-6, true);
	}

	/**
	 * Slower still, fewer infections and more severe cases than {@link #vaccinationDuringEpidemic}.
	 */
	@Test
	public void vaccinationDuringSlowerEpidemic() {
		run(3e-6, true);
	}

	private void run(double calibrationParameter, boolean vaccinate) {

		SyntheticBatch.Params params = new SyntheticBatch.Params(2000, 2, 20, 2, 10, DefaultContactModel.class, 3);

		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(new SyntheticScenario(params)));
		Config config = injector.getInstance(Config.class);
		config.controller().setOutputDirectory(utils.getOutputDirectory());
		ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class).setCalibrationParameter(calibrationParameter);

		if (vaccinate)
			ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class)
				.setVaccinationCapacity_pers_per_day(Map.of(LocalDate.parse("2020-02-18"), 150));

		injector.getInstance(EpisimRunner.class).run(ITERATIONS);

		RunEpisimIntegrationTest.assertSimulationOutput(utils);
	}
}

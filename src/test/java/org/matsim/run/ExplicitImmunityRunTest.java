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
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.EpisimRunner;
import org.matsim.episim.ImmunityConfigGroup;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.ProtectionCurve;
import org.matsim.episim.SyntheticBatch;
import org.matsim.episim.SyntheticScenario;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.DefaultContactModel;
import org.matsim.episim.model.ExplicitImmunityModel;
import org.matsim.episim.model.ImmunityModel;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.Transition;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.testcases.MatsimTestUtils;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The immunity model of {@link ImmunityConfigGroup} in a real run, not in isolation.
 *
 * <p>Unit tests ask the model single questions; here the synthetic scenario asks it thousands of times, through
 * the infection model and the disease-status transition model, for agents with real histories. What this catches
 * and unit tests cannot: a target the run asks for but the configuration does not describe, a source that appears
 * only during the run, and protection that is configured but never reaches the probabilities.</p>
 *
 * <p>The scenario binds {@code LegacySplitImmunityModel} as {@code @Legacy}, so these runs also show that
 * {@code model = explicit} overrules what the scenario bound.</p>
 */
public class ExplicitImmunityRunTest {

	private static final int ITERATIONS = 60;

	/**
	 * Slow enough that protection has time to matter before everybody is infected anyway; the same value is used
	 * in {@link ImmunityRegressionTest#vaccinationDuringEpidemic()}.
	 */
	private static final double CALIBRATION = 5e-6;

	private static final VirusStrain STRAIN = VirusStrain.SARS_CoV_2;

	private static final int AGENTS = 2000;

	@RegisterExtension
	public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public void runsWithoutProtectionAndUsesTheExplicitModel() {
		Result result = run("none", 0.0, false, true);

		assertThat(result.model).isInstanceOf(ExplicitImmunityModel.class);
		assertThat(result.infections).as("the epidemic ran").isPositive();
	}

	@Test
	public void protectionAgainstInfectionReducesInfections() {
		long without = run("without", 0.0, false, true).infections;
		long with = run("with", 0.9, false, true).infections;

		assertThat(without).as("agents were infected more than once, otherwise the curve protects nobody")
			.isGreaterThan(AGENTS);

		// measured 2971 without and 1844 with protection; the margin keeps the test from passing on noise
		assertThat(with).as("curves of 0.9 protection against infection reach the infection probabilities")
			.isLessThan((long) (without * 0.8));
	}

	@Test
	public void vaccinatedAgentsAreProtectedByTheProductCurve() {
		long unprotected = run("productWithoutEffect", 0.0, true, true).infections;
		long protectedByProduct = run("productWithEffect", 0.0, true, true, 0.9).infections;

		// measured 2950 without and 574 with the product curve
		assertThat(protectedByProduct).as("the product curve is the only difference between the two runs")
			.isLessThan((long) (unprotected * 0.5));
	}

	/**
	 * The configuration check cannot see which products the vaccination model hands out, so the model has to fail
	 * on the first vaccination with a product it has no curves for.
	 */
	@Test
	public void productWithoutSourceFailsDuringTheRun() {
		assertThatThrownBy(() -> run("noProduct", 0.0, true, false))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("a vaccination with '" + VaccinationType.generic + "'");
	}

	private Result run(String name, double infectionProtection, boolean vaccinate, boolean configureProduct) {
		return run(name, infectionProtection, vaccinate, configureProduct, 0.0);
	}

	private Result run(String name, double infectionProtection, boolean vaccinate, boolean configureProduct,
	                   double productProtection) {

		SyntheticBatch.Params params = new SyntheticBatch.Params(AGENTS, 2, 20, 2, 10, DefaultContactModel.class, 3);

		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(new SyntheticScenario(params)));
		Config config = injector.getInstance(Config.class);
		config.controller().setOutputDirectory(utils.getOutputDirectory() + name);
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setCalibrationParameter(CALIBRATION);

		// without a way back to susceptible nobody is ever infected twice, and protection left by an infection
		// would have nobody to protect; ten days is short enough for re-infections inside the run
		episimConfig.setProgressionConfig(AbstractSnzScenario2020.baseProgressionConfig(Transition.config())
			.from(DiseaseStatus.recovered,
				Transition.to(DiseaseStatus.susceptible, Transition.logNormalWithMedianAndStd(10., 3.)))
			.build());

		ImmunityConfigGroup immunity = ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class);
		immunity.setModel(ImmunityConfigGroup.Model.explicit);
		immunity.setOtherPathogensProtection(0.0);

		// an infection protects against re-infection, but not against the course of a disease
		describe(immunity.getOrAddSource(STRAIN), infectionProtection);

		if (vaccinate) {
			ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class)
				.setVaccinationCapacity_pers_per_day(Map.of(LocalDate.parse("2020-02-18"), 150));

			if (configureProduct)
				describe(immunity.getOrAddSource(VaccinationType.generic), productProtection);
		}

		injector.getInstance(EpisimRunner.class).run(ITERATIONS);

		long infections = 0;
		for (EpisimPerson person : injector.getInstance(InfectionEventHandler.class).getPersons())
			infections += person.getNumInfections();

		return new Result(injector.getInstance(ImmunityModel.class), infections);
	}

	/**
	 * Every supported target has to be listed, so the three severity targets are written as "no protection".
	 */
	private static void describe(ImmunityConfigGroup.SourceParams source, double infectionProtection) {
		source.setProtection(DiseaseStatus.infectedButNotContagious, STRAIN,
			ProtectionCurve.of(0, infectionProtection));
		source.setProtection(DiseaseStatus.showingSymptoms, STRAIN, ProtectionCurve.NONE);
		source.setProtection(DiseaseStatus.seriouslySick, STRAIN, ProtectionCurve.NONE);
		source.setProtection(DiseaseStatus.critical, STRAIN, ProtectionCurve.NONE);
	}

	private record Result(ImmunityModel model, long infections) {
	}
}

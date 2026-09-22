package org.matsim.episim;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.ImmunityModel;
import org.matsim.episim.model.LegacyCurveImmunityModel;
import org.matsim.episim.model.VirusStrain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The check of {@link ImmunityConfigGroup} is only worth writing if a run actually performs it.
 *
 * <p>Nothing in this repository calls {@code Config.checkConsistency()}: the call sits inside
 * {@code ControllerUtils.checkConfigConsistencyAndWriteToLog}, which {@link EpisimRunner#run(int)} makes just before
 * the first iteration. By then the group has been materialised, because the provider of the immunity model reads it
 * while the infection event handler is being built. This test pins that chain down; if it breaks, a broken immunity
 * configuration would run for days and fail somewhere in the middle instead.</p>
 */
public class ImmunityConfigStartupCheckTest {

	@Test
	public void brokenConfigStopsTheRunBeforeTheFirstIteration() {

		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(new SyntheticScenario()));
		Config config = injector.getInstance(Config.class);

		ImmunityConfigGroup immunity = ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class);
		immunity.setModel(ImmunityConfigGroup.Model.explicit);
		immunity.getOrAddSource(VirusStrain.of("STRAIN_OF_ImmunityConfigStartupCheckTest"))
			.setProtection(EpisimPerson.DiseaseStatus.infectedButNotContagious, VirusStrain.SARS_CoV_2, ProtectionCurve.NONE);

		assertThatThrownBy(() -> injector.getInstance(EpisimRunner.class).run(1))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("STRAIN_OF_ImmunityConfigStartupCheckTest")
			.hasMessageContaining("strainParams");
	}

	/**
	 * A module that binds the immunity model directly wins over the provider that reads the configuration. Scenarios
	 * in this repository bind their legacy implementation as {@code @Legacy}, but modules elsewhere may not.
	 */
	@Test
	public void aDirectBindingThatOverrulesTheSettingStopsTheRun() {

		AbstractModule bindsDirectly = new AbstractModule() {
			@Override
			protected void configure() {
				bind(ImmunityModel.class).to(LegacyCurveImmunityModel.class).in(Singleton.class);
			}
		};

		Injector injector = Guice.createInjector(Modules.override(
			Modules.override(new EpisimModule()).with(new SyntheticScenario())).with(bindsDirectly));

		Config config = injector.getInstance(Config.class);
		ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class).setModel(ImmunityConfigGroup.Model.explicit);

		assertThatThrownBy(() -> injector.getInstance(EpisimRunner.class).run(1))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("LegacyCurveImmunityModel")
			.hasMessageContaining("@Legacy");
	}

}

package org.matsim.episim;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.model.AntibodyModel;
import org.matsim.episim.model.DefaultAntibodyModel;
import org.matsim.episim.model.ExplicitImmunityModel;
import org.matsim.episim.model.InfectionModelWithAntibodies;
import org.matsim.episim.model.NoAntibodyModel;
import org.matsim.episim.model.ImmunityModel;
import org.matsim.episim.model.Legacy;
import org.matsim.episim.model.LegacyCurveImmunityModel;
import org.matsim.episim.model.LegacySplitImmunityModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which immunity implementation a run gets is decided by {@link ImmunityConfigGroup}, not by the scenario module.
 */
public class ImmunityModelBindingTest {

	private static ImmunityModel bind(ImmunityConfigGroup.Model model, boolean scenarioBindsSplit) {
		return injector(model, scenarioBindsSplit).getInstance(ImmunityModel.class);
	}

	private static Injector injector(ImmunityConfigGroup.Model model, boolean scenarioBindsSplit) {

		Config config = ConfigUtils.createConfig();
		ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class).setModel(model);

		AbstractModule scenario = new AbstractModule() {
			@Override
			protected void configure() {
				bind(Config.class).toInstance(config);
				if (scenarioBindsSplit)
					bind(ImmunityModel.class).annotatedWith(Legacy.class).to(LegacySplitImmunityModel.class).in(Singleton.class);
			}
		};

		return Guice.createInjector(Modules.override(new EpisimModule()).with(scenario));
	}

	@Test
	public void legacyCovidIsTheDefaultAndKeepsTheCurveBasedModel() {
		assertThat(bind(ImmunityConfigGroup.Model.legacyCovid, false))
			.isInstanceOf(LegacyCurveImmunityModel.class);
	}

	@Test
	public void legacyCovidUsesTheImplementationTheScenarioBound() {
		assertThat(bind(ImmunityConfigGroup.Model.legacyCovid, true))
			.isInstanceOf(LegacySplitImmunityModel.class);
	}

	@Test
	public void explicitOverridesWhatTheScenarioBoundAsLegacy() {
		assertThat(bind(ImmunityConfigGroup.Model.explicit, true))
			.as("the setting decides, a scenario cannot keep its legacy model silently")
			.isInstanceOf(ExplicitImmunityModel.class);
	}

	@Test
	public void newConfigDefaultsToLegacy() {
		assertThat(new ImmunityConfigGroup().getModel())
			.isEqualTo(ImmunityConfigGroup.Model.legacyCovid);
	}

	/**
	 * Antibodies are evolved for every agent on every day, and only the legacy model reads them.
	 */
	@Test
	public void antibodiesAreOnlyEvolvedForTheLegacyModel() {
		assertThat(injector(ImmunityConfigGroup.Model.legacyCovid, false).getInstance(AntibodyModel.class))
			.isInstanceOf(DefaultAntibodyModel.class);

		assertThat(injector(ImmunityConfigGroup.Model.explicit, false).getInstance(AntibodyModel.class))
			.isInstanceOf(NoAntibodyModel.class);
	}


	/**
	 * Some infection models read antibodies directly or apply no immunity at all. Under {@code explicit} their runs
	 * would look normal and give agents no protection, so they have to refuse to start.
	 */
	@Test
	public void infectionModelsThatBypassTheImmunityModelRefuseExplicit() {
		Config legacy = ConfigUtils.createConfig();
		ConfigUtils.addOrGetModule(legacy, ImmunityConfigGroup.class).setModel(ImmunityConfigGroup.Model.legacyCovid);
		ImmunityConfigGroup.requireLegacyImmunity(legacy, InfectionModelWithAntibodies.class, "reads antibodies");

		Config explicit = ConfigUtils.createConfig();
		ConfigUtils.addOrGetModule(explicit, ImmunityConfigGroup.class).setModel(ImmunityConfigGroup.Model.explicit);

		assertThatThrownBy(() -> ImmunityConfigGroup.requireLegacyImmunity(explicit, InfectionModelWithAntibodies.class,
			"reads antibodies"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("InfectionModelWithAntibodies")
			.hasMessageContaining("no protection against infection");
	}

}

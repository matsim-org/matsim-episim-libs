package org.matsim.episim.model;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VirusStrainTest {

	@Test
	public void returnsCanonicalInstanceByName() {
		assertThat(VirusStrain.of("DELTA")).isSameAs(VirusStrain.DELTA);
		assertThat(VirusStrain.of("DELTA").parent).isSameAs(VirusStrain.ALPHA);
		assertThat(VirusStrain.DELTA.getPathogen()).isSameAs(Pathogen.SARS_COV_2);

		VirusStrain custom = VirusStrain.of("CUSTOM_TEST_STRAIN");
		assertThat(VirusStrain.of("CUSTOM_TEST_STRAIN")).isSameAs(custom);
		assertThat(custom.getPathogen()).isSameAs(Pathogen.SARS_COV_2);
	}

	@Test
	public void childInheritsPathogenFromParent() {
		Pathogen influenza = new Pathogen("influenza");
		VirusStrain parent = VirusStrain.of(influenza, "H1N1_PARENT_FACTORY_TEST");
		VirusStrain child = VirusStrain.of("H1N1_CHILD_FACTORY_TEST", parent);

		assertThat(parent.getPathogen()).isSameAs(influenza);
		assertThat(child.getPathogen()).isSameAs(influenza);
		assertThat(VirusStrain.of("H1N1_CHILD_FACTORY_TEST", parent)).isSameAs(child);

		VirusStrain otherParent = VirusStrain.of(influenza, "H1N1_OTHER_PARENT_FACTORY_TEST");
		assertThatThrownBy(() -> VirusStrain.of("H1N1_CHILD_FACTORY_TEST", otherParent))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("already registered with parent");
	}

	@Test
	public void registersGloballyUniqueStrainNameForPathogen() {
		Pathogen influenza = new Pathogen("influenza");
		VirusStrain h1n1 = VirusStrain.of(influenza, "H1N1_FACTORY_TEST");

		assertThat(VirusStrain.of("H1N1_FACTORY_TEST")).isSameAs(h1n1);
		assertThat(VirusStrain.of(new Pathogen("influenza"), "H1N1_FACTORY_TEST")).isSameAs(h1n1);
		assertThatThrownBy(() -> VirusStrain.of(new Pathogen("other"), "H1N1_FACTORY_TEST"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("already registered for pathogen 'influenza'");
	}
}

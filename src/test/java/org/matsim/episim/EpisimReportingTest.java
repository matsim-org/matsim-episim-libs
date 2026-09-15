package org.matsim.episim;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.model.VirusStrain;
import org.matsim.testcases.MatsimTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class EpisimReportingTest {

	@RegisterExtension
	public MatsimTestUtils utils = new MatsimTestUtils();

	/**
	 * A run continuing from a snapshot may configure other custom strains than the run that wrote it, e.g. to introduce a
	 * new variant. The per-strain counts of the snapshot must still be restored to the right strains.
	 */
	@Test
	public void snapshotRestoresStrainCountsByName() throws IOException {

		OutputDirectoryLogging.catchLogEntries();

		VirusStrain fluA = VirusStrain.of("SNAPSHOT_TEST_FLU_A");
		VirusStrain fluB = VirusStrain.of("SNAPSHOT_TEST_FLU_B");

		EpisimReporting written = createReporting("written/", fluA);
		written.strains.put(VirusStrain.SARS_CoV_2, 3);
		written.strains.put(fluA, 2);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			written.writeExternal(out);
		}
		written.close();

		// the continuing run configures an additional strain, which comes before the strain of the snapshot
		EpisimReporting restored = createReporting("restored/", fluB, fluA);
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			restored.readExternal(in);
		}
		restored.close();

		assertThat(restored.strains).containsOnly(
				entry(VirusStrain.SARS_CoV_2, 3),
				entry(fluA, 2));
	}

	private EpisimReporting createReporting(String dir, VirusStrain... customStrains) {

		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(new SyntheticScenario()));
		injector.getInstance(Config.class).controller().setOutputDirectory(utils.getOutputDirectory() + dir);

		VirusStrainConfigGroup strainConfig = injector.getInstance(VirusStrainConfigGroup.class);
		for (VirusStrain strain : customStrains)
			strainConfig.getOrAddParams(strain);

		return injector.getInstance(EpisimReporting.class);
	}
}

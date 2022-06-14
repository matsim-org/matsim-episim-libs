package org.matsim.episim;


import org.junit.Test;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.episim.model.VirusStrain;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;

import static org.assertj.core.api.Assertions.assertThat;

public class DataUtilsTest {


	@Test
	public void voc() throws IOException {

		File file = File.createTempFile( "voc", "csv");
		file.deleteOnExit();

		Path path = file.toPath();

		org.apache.commons.io.IOUtils.copy(IOUtils.getBufferedReader("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/Cologne/VOC_Cologne_RKI.csv"),
				Files.newOutputStream(path));

		NavigableMap<LocalDate, Map<VirusStrain, Double>> result = DataUtils.readVOC(path);

		assertThat(result.get(LocalDate.of(2022,2,6)))
				.containsEntry(VirusStrain.OMICRON_BA1, 0.824)
				.containsEntry(VirusStrain.OMICRON_BA2, 0.16);


		assertThat(result.get(LocalDate.of(2022,5,29)))
				.containsEntry(VirusStrain.OMICRON_BA1, 0.002)
				.containsEntry(VirusStrain.OMICRON_BA2, 0.875);

	}
}

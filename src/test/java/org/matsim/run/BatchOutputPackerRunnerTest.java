package org.matsim.run;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class BatchOutputPackerRunnerTest {

	@Test
	void exposesCommandLineOptions() {
		CommandLine commandLine = new CommandLine(new BatchOutputPackerRunner());

		assertThat(commandLine.getCommandName()).isEqualTo("pack-batch-output");
		assertThat(commandLine.getCommandSpec().optionsMap()).containsKeys(
			"--input", "--output", "--district", "--keep-seeds");
	}
}

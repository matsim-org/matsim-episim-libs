package org.matsim.episim.analysis;

import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Interface for an analysis that needs the output folder of a completed run.
 */
public interface OutputAnalysis extends Callable<Integer> {

	/**
	 * Apply the given command line arguments to this instance and return it.
	 */
	default OutputAnalysis withArgs(String... args) {
		CommandLine cli = new CommandLine(this);
		CommandLine.ParseResult parseResult = cli.parseArgs(args);

		if (!parseResult.errors().isEmpty())
			throw new IllegalStateException("Error parsing arguments", parseResult.errors().get(0));

		return cli.getCommand();
	}

	/**
	 * Analyze the scenario output and write additional files into the same output directory.
	 * @param output path to output files for one simulation run
	 */
	void analyzeOutput(Path output) throws IOException;

}

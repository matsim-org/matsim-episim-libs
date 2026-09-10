/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * #L%
 */
package org.matsim.run;

import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/** Command-line entry point for {@link BatchOutputPacker}. */
@CommandLine.Command(
	name = "pack-batch-output",
	description = "Pack RunParallel output for the Episim viewer.",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)
public final class BatchOutputPackerRunner implements Callable<Integer> {

	@CommandLine.Option(names = {"-i", "--input"}, required = true,
		description = "RunParallel output directory containing _info.txt and metadata.yaml.")
	private Path input;

	@CommandLine.Option(names = {"-o", "--output"}, required = true,
		description = "New or empty viewer output directory.")
	private Path output;

	@CommandLine.Option(names = {"-d", "--district"}, required = true,
		description = "District value to retain from infections.txt, for example Köln.")
	private String district;

	@CommandLine.Option(names = "--keep-seeds", defaultValue = "false",
		description = "Keep every seed as its own run instead of averaging runs with equal parameters.")
	private boolean keepSeeds;

	public static void main(String[] args) {
		System.exit(new CommandLine(new BatchOutputPackerRunner()).execute(args));
	}

	@Override
	public Integer call() throws Exception {
		new BatchOutputPacker(input, output, district, keepSeeds).pack();
		return 0;
	}
}

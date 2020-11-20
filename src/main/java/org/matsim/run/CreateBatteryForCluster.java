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
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.PreparedRun;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;


/**
 * Creates batch scripts to execute one {@link BatchRun} on a computing cluster.
 * It will write all necessary configs, run scripts and metadata information.
 * <p>
 * For examples look in the <em>org.matsim.run.batch</em> package. The classes there
 * can be used for this run class as <em>--setup</em> and <em>--params</em> option to create a batch run.
 *
 * @param <T> type to match run and params
 */
@CommandLine.Command(
		name = "createBattery",
		description = "Create batch scripts for execution on computing cluster.",
		mixinStandardHelpOptions = true
)
@SuppressWarnings("unchecked, rawtypes")
public class CreateBatteryForCluster<T> implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(CreateBatteryForCluster.class);

	@CommandLine.Option(names = "--output", defaultValue = "battery")
	private Path output;

	@CommandLine.Option(names = "--batch-output", defaultValue = "output")
	private Path batchOutput;

	@CommandLine.Option(names = "--run-version", description = "Run version", defaultValue = "v15")
	private String runVersion;

	@CommandLine.Option(names = "--step-size", description = "Step size of the job array", defaultValue = "44")
	private int stepSize;

	@CommandLine.Option(names = "--jvm-opts", description = "Additional options for JVM", defaultValue = "-Xms84G -Xmx84G -XX:+UseParallelGC")
	private String jvmOpts;

	@CommandLine.Option(names = "--setup", defaultValue = "org.matsim.run.batch.BerlinClusterTracing")
	private Class<? extends BatchRun<T>> setup;

	@CommandLine.Option(names = "--params", defaultValue = "org.matsim.run.batch.BerlinClusterTracing$Params")
	private Class<T> params;

	@SuppressWarnings("rawtypes")
	public static void main(String[] args) {
		System.exit(new CommandLine(new CreateBatteryForCluster()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		// Set property for BatchRun resolve to build the correct path
		System.setProperty("EPISIM_ON_CLUSTER", "true");

		PreparedRun prepare = BatchRun.prepare(setup, params);

		boolean noBindings = true;
		BatchRun.Metadata meta = prepare.setup.getMetadata();
		String runName = meta.name;
		Path dir = output.resolve(runVersion).resolve(meta.name).resolve(meta.region);
		Path input = dir.resolve("input");

		Files.createDirectories(input);

		// Copy all resources
		for (String name : Lists.newArrayList("collect.sh", "run.sh", "runSlurm.sh", "runParallel.sh", "postProcess.sh", "jvm.options")) {
			Files.copy(Resources.getResource(name).openStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}

		BufferedWriter infoWriter = new BufferedWriter(new FileWriter(dir.resolve("_info.txt").toFile()));
		BufferedWriter yamlWriter = new BufferedWriter(new FileWriter(dir.resolve("metadata.yaml").toFile()));


		List<String> header = Lists.newArrayList("RunScript", "Config", "RunId", "Output");
		header.addAll(prepare.parameter);

		infoWriter.write(Joiner.on(";").join(header));
		infoWriter.newLine();

		ObjectMapper mapper = new ObjectMapper(new YAMLFactory()
				.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
				.registerModule(new JavaTimeModule())
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		Map<String, Object> metadata = new LinkedHashMap<>();

		metadata.put("readme", runVersion + "-notes.md");
		metadata.put("zip", runVersion + "-data-" + runName + ".zip");
		metadata.put("info", runVersion + "-info-" + runName + ".txt");
		metadata.put("zipFolder", "summaries");
		metadata.put("timestamp", LocalDate.now());

		metadata.putAll(prepare.getMetadata());
		mapper.writeValue(yamlWriter, metadata);

		for (PreparedRun.Run run : prepare.runs) {

			String runId = runName + run.id;
			String configFileName = "config_" + runName + run.id + ".xml";

			noBindings &= ((BatchRun) prepare.setup).getBindings(run.id, run.args) == null;

			String outputPath = batchOutput + "/" + prepare.getOutputName(run);
			if (noBindings) {
				run.config.controler().setOutputDirectory(outputPath);
				run.config.controler().setRunId(runName + run.id);

				prepare.setup.writeAuxiliaryFiles(dir, run.config);
				ConfigUtils.writeConfig(run.config, input.resolve(configFileName).toString());
			}

			List<String> line = Lists.newArrayList("run.sh", configFileName, runId, outputPath);
			line.addAll(run.params.stream().map(EpisimUtils::asString).collect(Collectors.toList()));

			// base case is not contained in the info file
			if (run.id > 0) {
				infoWriter.write(Joiner.on(";").join(line));
				infoWriter.newLine();
			}
		}

		// Round up array size to be multiple of step size
		int step = (1000 / stepSize) * stepSize;

		String jvmOpts = "export JAVA_OPTS='" + this.jvmOpts + "'\n";

		// Split task into multiple below 1000
		// this is due to a limitation of maximum job array size
		List<String> lines = Lists.newArrayList("#!/bin/bash\n", jvmOpts);
		for (int offset = 0; offset < prepare.runs.size(); offset += step) {

			// round array end down according to run size, but must also be multiple of step size
			int arrayEnd = (int) Math.ceil((double) Math.min(offset + step, prepare.runs.size() - offset) / stepSize) * stepSize;

			lines.add(
					String.format("sbatch --export=JAVA_OPTS,EXTRA_OFFSET=%d --array=0-%d:%d --ntasks-per-node=%d --job-name=%s runSlurm.sh",
							offset, arrayEnd - 1, stepSize, stepSize, runName)
			);
		}

		if (noBindings)
			FileUtils.writeLines(dir.resolve("start_slurm.sh").toFile(), lines, "\n");

		// Target system has 4 numa nodes
		int perSocket = (stepSize / 4);

		FileUtils.writeLines(dir.resolve("start_parallel_slurm.sh").toFile(), Lists.newArrayList(
				"#!/bin/bash\n", jvmOpts,
				// Dollar signs must be escaped
				"export EPISIM_SETUP='" + setup.getName() + "'",
				"export EPISIM_PARAMS='" + params.getName() + "'",
				"export EPISIM_INPUT='/scratch/usr/bebchrak/episim/episim-input'",
				"export EPISIM_OUTPUT='" + batchOutput.toString() + "'",
				"",
				String.format("jid=$(sbatch --parsable --export=ALL --array=1-%d --ntasks-per-socket=%d --job-name=%s runParallel.sh)",
						(int) Math.ceil(prepare.runs.size() / (perSocket * 4d)), perSocket, runName),
				"sbatch --export=ALL --dependency=afterok:$jid postProcess.sh"
		), "\n");

		FileUtils.writeLines(dir.resolve("start_qsub.sh").toFile(), Lists.newArrayList(
				"#!/bin/bash\n", jvmOpts,
				// Dollar signs must be escaped
				"export EPISIM_SETUP='" + setup.getName() + "'",
				"export EPISIM_PARAMS='" + params.getName() + "'",
				"export EPISIM_INPUT='<PUT INPUT DIR HERE>'",
				"export EPISIM_OUTPUT='" + batchOutput.toString() + "'",
				"",
				String.format("jid=$(qsub -V -N %s run.sh)", runName),
				"qsub -V -W depend=afterok:$jid postProcess.sh"
		), "\n");


		FileUtils.writeLines(dir.resolve("test.sh").toFile(), Lists.newArrayList(
				"#!/bin/bash\n", jvmOpts,
				"export JOB_NAME=" + runName + 1,
				"",
				"./run.sh"
		), "\n");

		infoWriter.close();

		if (!noBindings) {
			log.warn("This run defines custom bindings. Run from config will not be available.");
		}

		return 0;
	}
}

package org.matsim.run;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.PreparedRun;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;


@CommandLine.Command(
		name = "createBattery",
		description = "Create batch scripts for execution on computing cluster.",
		mixinStandardHelpOptions = true
)
public class CreateBatteryForCluster<T> implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(CreateBatteryForCluster.class);

	@CommandLine.Option(names = "--output", defaultValue = "battery")
	private Path output;

	@CommandLine.Option(names = "--batch-output", defaultValue = "output")
	private Path batchOutput;

	@CommandLine.Option(names = "--name", description = "Run name", defaultValue = "sz")
	private String runName;

	@CommandLine.Option(names = "--step-size", description = "Step size of the job array", defaultValue = "82")
	private int stepSize;

	@CommandLine.Option(names = "--jvm-opts", description = "Additional options for JVM", defaultValue = "-Xmx4G")
	private String jvmOpts;

	@CommandLine.Option(names = "--setup", defaultValue = "org.matsim.run.batch.SchoolClosure")
	private Class<? extends BatchRun<T>> setup;

	@CommandLine.Option(names = "--params", defaultValue = "org.matsim.run.batch.SchoolClosure$Params")
	private Class<T> params;

	@SuppressWarnings("rawtypes")
	public static void main(String[] args) {
		System.exit(new CommandLine(new CreateBatteryForCluster()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		Path dir = output.resolve(runName);
		Path input = dir.resolve("input");

		Files.createDirectories(input);

		// Copy all resources
		for (String name : Lists.newArrayList("run.sh", "runSlurm.sh", "runParallel.sh", "jvm.options")) {
			Files.copy(Resources.getResource(name).openStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}

		BufferedWriter bashScriptWriter = new BufferedWriter(new FileWriter(dir.resolve("start_qsub.sh").toFile()));
		BufferedWriter infoWriter = new BufferedWriter(new FileWriter(dir.resolve("_info.txt").toFile()));

		PreparedRun prepare = BatchRun.prepare(setup, params);


		List<String> header = Lists.newArrayList("RunScript", "Config", "RunId", "Output");
		header.addAll(prepare.parameter);

		infoWriter.write(Joiner.on(";").join(header));
		infoWriter.newLine();


		for (PreparedRun.Run run : prepare.runs) {

			String runId = runName + run.id;
			String configFileName = "config_" + runName + run.id + ".xml";

			String outputPath = batchOutput + "/" + prepare.setup.getOutputName(run);
			run.config.controler().setOutputDirectory(outputPath);

			prepare.setup.writeAuxiliaryFiles(dir, run.config);
			ConfigUtils.writeConfig(run.config, input.resolve(configFileName).toString());

			bashScriptWriter.write("qsub -N " + runId + " run.sh");
			bashScriptWriter.newLine();

			List<String> line = Lists.newArrayList("run.sh", configFileName, runId, outputPath);
			line.addAll(run.params.stream().map(Object::toString).collect(Collectors.toList()));

			infoWriter.write(Joiner.on(";").join(line));
			infoWriter.newLine();

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
					String.format("sbatch --export=JAVA_OPTS,EXTRA_OFFSET=%d --array=1-%d:%d --ntasks-per-node=%d --job-name=%s runSlurm.sh",
							offset, arrayEnd, stepSize, stepSize, runName)
			);
		}

		FileUtils.writeLines(dir.resolve("start_slurm.sh").toFile(), lines, "\n");

		// Target system has 4 numa nodes
		int perSocket = (stepSize / 4);

		FileUtils.writeLines(dir.resolve("start_parallel_slurm.sh").toFile(), Lists.newArrayList(
				"#!/bin/bash\n", jvmOpts,
				// Dollar signs must be escaped
				"export EPISIM_SETUP='" + setup.getName() + "'",
				"export EPISIM_PARAMS='" + params.getName() + "'",
				"export EPISIM_OUTPUT='" + batchOutput.toString() + "'",
				"",
				String.format("sbatch --export=ALL --array=1-%d --ntasks-per-socket=%d --job-name=%s runParallel.sh",
						(int) Math.ceil(prepare.runs.size() / (perSocket * 4d)), perSocket, runName)
		), "\n");

		FileUtils.writeLines(dir.resolve("test.sh").toFile(), Lists.newArrayList(
				"#!/bin/bash\n", jvmOpts,
				"export JOB_NAME=" + runName + 1,
				"",
				"./run.sh"
		), "\n");

		bashScriptWriter.close();
		infoWriter.close();

		return 0;
	}
}

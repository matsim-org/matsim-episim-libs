package org.matsim.run;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
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
		description = "Create batch script for execution on computing cluster.",
		mixinStandardHelpOptions = true
)
public class CreateBatteryForCluster<T> implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(CreateBatteryForCluster.class);

	@CommandLine.Option(names = "--output", defaultValue = "../epidemic/battery")
	private Path output;

	@CommandLine.Option(names = "--name", defaultValue = "sz")
	private String runName;

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

		Files.createDirectories(output);

		// Copy run script
		Path runScript = output.resolve("run.sh");
		Path runSlurm = output.resolve("runSlurm.sh");


		Files.copy(Resources.getResource("_run.sh").openStream(), runScript, StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Resources.getResource("_runSlurm.sh").openStream(), runSlurm, StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Resources.getResource("jvm.options").openStream(), output.resolve("jvm.options"), StandardCopyOption.REPLACE_EXISTING);

		runScript.toFile().setExecutable(true);
		runSlurm.toFile().setExecutable(true);

		BufferedWriter bashScriptWriter = new BufferedWriter(new FileWriter(output.resolve("_bashScript.sh").toFile()));
		BufferedWriter infoWriter = new BufferedWriter(new FileWriter(output.resolve("_info.txt").toFile()));

		PreparedRun prepare = BatchRun.prepare(setup, params);


		log.info("Preparing {} runs for {} ({})", prepare.runs.size(), runName, setup.getSimpleName());


		List<String> header = Lists.newArrayList("RunScript", "Config", "RunId", "Output");
		header.addAll(prepare.parameter);

		infoWriter.write(Joiner.on(";").join(header));
		infoWriter.newLine();


		for (PreparedRun.Run run : prepare.runs) {

			String runId = runName + run.id;
			String configFileName = "config_" + runName + run.id + ".xml";

			String outputPath = "output/" + Joiner.on("-").join(run.params);
			run.config.controler().setOutputDirectory(outputPath);

			prepare.setup.write(output, run.config);
			ConfigUtils.writeConfig(run.config, output.resolve(configFileName).toString());

			bashScriptWriter.write("qsub -N " + runId + " run.sh");
			bashScriptWriter.newLine();

			List<String> line = Lists.newArrayList("run.sh", configFileName, runId, outputPath);
			line.addAll(run.params.stream().map(Object::toString).collect(Collectors.toList()));

			infoWriter.write(Joiner.on(";").join(line));
			infoWriter.newLine();

		}

		// Current script is configured to run with a stepsize of 96
		Files.write(output.resolve("_slurmScript.sh"), Lists.newArrayList(
				"#!/bin/bash\n",
				// Round up array size to be multiple of step size
				String.format("sbatch --array=1-%d:96 --job-name=sz runSlurm.sh", (int) Math.ceil(prepare.runs.size() / 96d) * 96))
		);

		bashScriptWriter.close();
		infoWriter.close();

		return 0;
	}
}

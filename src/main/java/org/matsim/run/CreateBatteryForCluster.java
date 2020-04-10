package org.matsim.run;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimConfigGroup.FacilitiesHandling;
import org.matsim.episim.policy.FixedPolicy;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;


/**
 * @author smueller
 */
public class CreateBatteryForCluster {
	private static final Path workingDir = Path.of("../epidemic/battery/");

	public static void main(String[] args) throws IOException {

		Files.createDirectories(workingDir);

		// Copy run script
		Path runScript = workingDir.resolve("run.sh");
		Path runSlurm = workingDir.resolve("runSlurm.sh");


		Files.copy(Resources.getResource("_run.sh").openStream(), runScript, StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Resources.getResource("_runSlurm.sh").openStream(), runSlurm, StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Resources.getResource("jvm.options").openStream(), workingDir.resolve("jvm.options"), StandardCopyOption.REPLACE_EXISTING);

		runScript.toFile().setExecutable(true);
		runSlurm.toFile().setExecutable(true);

		BufferedWriter bashScriptWriter = new BufferedWriter(new FileWriter(workingDir.resolve("_bashScript.sh").toFile()));
		BufferedWriter infoWriter = new BufferedWriter(new FileWriter(workingDir.resolve("_info.txt").toFile()));

		infoWriter.write("RunScript;Config;RunId;Output;offset;remainingFractionKiga;remainingFractionPrimary;remainingFractionSecondary;remainingFractionLeisure;remainingFractionWork;remainingFractionShoppingErrandsBusiness");
		infoWriter.newLine();
		
		List<Integer> offset = Arrays.asList(-5, 5);
		List<Double> remainingFractionKiga = Arrays.asList(1.0, 0.5, 0.1);
		List<Double> remainingFractionPrima = Arrays.asList(1.0, 0.5, 0.1);
		List<Double> remainingFractionSecon = Arrays.asList(1.0, 0.5, 0.);
		List<Double> remainingFractionLeisure = Arrays.asList(0.4, 0.2, 0.);
		List<Double> remainingFractionWork = Arrays.asList(0.8, 0.6, 0.4);
		List<Double> remainingFractionShoppingBusinessErrands = Arrays.asList(0.4, 0.2);

		int ii = 1;
		for (int off : offset) {
			for (double kiga : remainingFractionKiga) {
				for (double prima : remainingFractionPrima) {
					for (double secon : remainingFractionSecon) {
						for (double leisure : remainingFractionLeisure) {
							for (double work : remainingFractionWork) {
								for (double shBuEr : remainingFractionShoppingBusinessErrands) {
									String runId = "sz" + ii;
									String configFileName = createConfigFile(off, kiga, prima, secon, leisure, work, shBuEr, ii);

									bashScriptWriter.write("qsub -N " + runId + " run.sh");
									bashScriptWriter.newLine();

									String outputPath = "output/" + off +"-" + kiga + "-" + prima + "-" + secon + "-" + leisure + "-" + work + "-" + shBuEr;
									infoWriter.write("run.sh;" + configFileName + ";" + runId + ";" + outputPath + ";" + off + ";" + kiga + ";" + prima + ";" + secon + ";" + leisure + ";" + work + ";" + shBuEr);
									infoWriter.newLine();
									ii++;
								}
							}
						}
					}
				}
			}
		}

		// Current script is configured to run with a stepsize of 96
		Files.write(workingDir.resolve("_slurmScript.sh"), Lists.newArrayList(
				"#!/bin/bash\n",
				// Round up array size to be multiple of step size
				String.format("sbatch --array=1-%d:96 --job-name=sz runSlurm.sh", (int) Math.ceil(ii / 96d) * 96))
		);

		bashScriptWriter.close();
		infoWriter.close();

	}

	public static String createConfigFile(int offset, double kiga, double prima, double secon, double leisure, double work, double shBuEr, int ii) throws IOException {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../be_snz_episim_events.xml.gz");
		episimConfig.setFacilitiesHandling(FacilitiesHandling.snz);

		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.000002);

		RunEpisimSnz.addParams(episimConfig);

		episimConfig.getOrAddContainerParams("pt")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("tr")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("leisure")
				.setContactIntensity(5.0);
		episimConfig.getOrAddContainerParams("educ_kiga")
				.setContactIntensity(10.0);
		episimConfig.getOrAddContainerParams("educ_primary")
				.setContactIntensity(4.0);
		episimConfig.getOrAddContainerParams("educ_secondary")
				.setContactIntensity(2.0);
		episimConfig.getOrAddContainerParams("home")
				.setContactIntensity(3.0);

		com.typesafe.config.Config policyConf = FixedPolicy.config()
				.restrict(26 + offset, 0.9, "leisure")
				.restrict(26 + offset, 0.1, "educ_primary", "educ_kiga")
				.restrict(26 + offset, 0., "educ_secondary", "educ_higher")
				.restrict(35 + offset, leisure, "leisure")
				.restrict(35 + offset, work, "work")
				.restrict(35 + offset, shBuEr, "shopping", "errands", "business")
				.restrict(63 + offset, kiga, "educ_kiga")
				.restrict(63 + offset, prima, "educ_primary")
				.restrict(63 + offset, secon, "educ_secondary")
				.build();

		String policyFileName = "policy" + ii + ".conf";
		episimConfig.setOverwritePolicyLocation(policyFileName);
		Files.writeString(workingDir.resolve(policyFileName), policyConf.root().render());

		config.controler().setOutputDirectory("output/" + offset + "-" + kiga + "-" + prima + "-" + secon + "-" + leisure + "-" + work + "-" + shBuEr);

		String configFileName = "config_sz" + ii + ".xml";
		ConfigUtils.writeConfig(config, workingDir.resolve(configFileName).toString());

		return configFileName;

	}

}

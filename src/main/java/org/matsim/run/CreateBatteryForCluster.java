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

        infoWriter.write("RunScript;Config;RunId;Output;remainingFractionKiga;remainingFractionPrimary;remainingFractionSecondary;remainingFractionHigher;ShutdownType");
        infoWriter.newLine();

        List<Double> remainingFractionKiga = Arrays.asList(1.0, 0.5, 0.1);
        List<Double> remainingFractionPrima = Arrays.asList(1.0, 0.5, 0.1);
        List<Double> remainingFractionSecon = Arrays.asList(1.0, 0.5, 0.);
        List<Double> remainingFractionHigher = Arrays.asList(1.0, 0.5, 0.);
        List<String> shutdown = Arrays.asList("strong", "weak");

        int ii = 1;
        for (String shutd : shutdown) {
            for (double kiga : remainingFractionKiga) {
                for (double prima : remainingFractionPrima) {
                    for (double secon : remainingFractionSecon) {
                        for (double higher : remainingFractionHigher) {
                            String runId = "sz" + ii;
                            String configFileName = createConfigFile(kiga, prima, secon, higher, shutd, ii);

                            bashScriptWriter.write("qsub -N " + runId + " run.sh");
                            bashScriptWriter.newLine();

                            String outputPath = "output/" + kiga + "-" + prima + "-" + secon + "-" + higher + "-" + shutd;
                            infoWriter.write("run.sh;" + configFileName + ";" + runId + ";" + outputPath + ";" + kiga + ";" + prima + ";" + secon + ";" + higher + ";" + shutd);
                            infoWriter.newLine();
                            ii++;
                        }
                    }
                }
            }
        }

        Files.write(workingDir.resolve("_slurmScript.sh"), Lists.newArrayList(
                "#!/bin/bash\n",
                String.format("sbatch --array=1:%d --job-name=sz runSlurm.sh", ii))
        );

        bashScriptWriter.close();
        infoWriter.close();

    }

    public static String createConfigFile(double kiga, double prima, double secon, double higher, String shutd, int ii) throws IOException {

        Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
        EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

        episimConfig.setInputEventsFile("../snzDrt220a.0.events.reduced.xml.gz");
        episimConfig.setFacilitiesHandling(FacilitiesHandling.snz);

        episimConfig.setSampleSize(0.25);
        episimConfig.setCalibrationParameter(0.0000015);

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

        double factor = 1;
        if (shutd.equals("strong")) {
            factor = 0.5;
        }

        com.typesafe.config.Config policyConf = FixedPolicy.config()
                .restrict(26, 0.9, "leisure")
                .restrict(26, 0.1, "educ_primary", "educ_kiga")
                .restrict(26, 0., "educ_secondary", "educ_higher")
                .restrict(35, factor * 0.2, "business", "errands", "leisure")
                .restrict(35, factor * 0.4, "work", "shopping")
                .restrict(63, kiga, "educ_kiga")
                .restrict(63, prima, "educ_primary")
                .restrict(63, secon, "educ_secondary")
                .restrict(63, higher, "educ_higher")
                .build();

        String policyFileName = "policy" + ii + ".conf";
        episimConfig.setOverwritePolicyLocation(policyFileName);
        Files.writeString(workingDir.resolve(policyFileName), policyConf.root().render());

        config.controler().setOutputDirectory("output/" + kiga + "-" + prima + "-" + secon + "-" + higher + "-" + shutd);

        String configFileName = "config_sz" + ii + ".xml";
        ConfigUtils.writeConfig(config, workingDir.resolve(configFileName).toString());

        return configFileName;

    }

}

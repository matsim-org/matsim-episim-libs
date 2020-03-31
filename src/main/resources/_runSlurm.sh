#!/bin/bash --login
#SBATCH --time=01:00:00
#SBATCH --nodes=1
#SBATCH --cpus-per-task=1
#SBATCH --mem-per-cpu=4096
#SBATCH --output=logfile_%j.log

date
hostname

classpath="matsim-episim-1.0-SNAPSHOT-jar-with-dependencies.jar"

echo "***"
echo "classpath: $classpath"
echo "***"

# 12288 MB
# java command
java_command="java -Djava.awt.headless=true -Xmx12G -cp $classpath"

# main
main="org.matsim.run.RunFromConfig"

# arguments
arguments="config_$SLURM_JOB_NAME.xml --config:controler.runId $SLURM_JOB_NAME"

# command
command="$java_command $main $arguments"

echo ""
echo "command is $command"

echo ""
echo "using alternative java"
module add java/11
java -version

$command
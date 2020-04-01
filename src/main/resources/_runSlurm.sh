#!/bin/bash
#SBATCH --time=01:30:00
#SBATCH --nodes=1
#SBATCH --cpus-per-task=1
#SBATCH --mem-per-cpu=4096
#SBATCH --output=logfile_%A_%a.log
#SBATCH --error=errorfile_%A_%a.log

date
hostname

classpath="matsim-episim-1.0-SNAPSHOT-jar-with-dependencies.jar"

echo "***"
echo "classpath: $classpath"
echo "***"

# main
main="org.matsim.run.RunFromConfig"

# arguments
arguments="config_$SLURM_JOB_NAME$SLURM_ARRAY_TASK_ID.xml --config:controler.runId $SLURM_JOB_NAME$SLURM_ARRAY_TASK_ID"

command="java -cp $classpath @jvm.options $main $arguments"

echo ""
echo "command is $command"

module load java/11.0.6

java -version

$command
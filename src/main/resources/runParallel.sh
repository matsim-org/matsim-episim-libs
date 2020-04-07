#!/bin/bash
#SBATCH --time=01:40:00
#SBATCH --nodes=1
#SBATCH --ntasks-per-socket=1
#SBATCH --mem-bind=local

date
hostname

cd $SLURM_SUBMIT_DIR

classpath="matsim-episim-1.0-SNAPSHOT-jar-with-dependencies.jar"

echo "***"
echo "classpath: $classpath"
echo "***"

# main
main="org.matsim.run.RunParallel"

module load java/11.0.6
java -version


# TODO: copy available environment vars and calculate offsets etc.

let NTpn=--SLURM_NTASKS_PER_NODE	# all physical cores
#let NTpn=--SLURM_CPUS_ON_NODE		# all logical HT cores
let offset=SLURM_ARRAY_TASK_ID


arguments="$input --config:controler.runId ${SLURM_JOB_NAME}${nID} -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector"
command="java -cp $classpath $JAVA_OPTS @jvm.options $main $arguments"
echo ""
echo "command is $command"
test -f $input && taskset -c $cid $command &

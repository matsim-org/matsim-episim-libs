#!/bin/bash --login
#$ -l h_rt=86400
#$ -o ./logfile_$JOB_NAME_$TASK_ID.log
#$ -j y
#$ -m a
#$ -M mueller@vsp.tu-berlin.de
#$ -cwd
#$ -pe mp 4
#$ -l mem_free=28G

date
hostname

classpath="matsim-episim-*-SNAPSHOT.jar"

echo "***"
echo "classpath: $classpath"
echo "***"

# main
main="org.matsim.run.RunParallel"

let workerId=$((${SGE_TASK_ID:-1} - 1))
let numWorker=${SGE_TASK_LAST:-1}

# arguments
arguments="--threads 4 --total-worker $numWorker --worker-index $workerId"

command="java -cp $classpath $JAVA_OPTS @jvm.options -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector $main $arguments"


echo "setup=$EPISIM_SETUP, params=$EPISIM_PARAMS"
echo ""
echo "command is $command"

echo ""
echo "using alternative java"
module add java/11
java -version

$command
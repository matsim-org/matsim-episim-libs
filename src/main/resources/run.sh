#!/bin/bash --login
#$ -l h_rt=86400
#$ -o ./logfile_$JOB_NAME.log
#$ -j y
#$ -m a
#$ -M mueller@vsp.tu-berlin.de
#$ -cwd
#$ -pe mp 4
#$ -l mem_free=28G

date
hostname

classpath="matsim-episim-1.0-SNAPSHOT.jar"

echo "***"
echo "classpath: $classpath"
echo "***"

# main
main="org.matsim.run.RunParallel"

# arguments
arguments="--threads 4 --total-worker 1 --worker-index ${PBS_ARRAY_INDEX:0}"

command="java -cp $classpath $JAVA_OPTS @jvm.options -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector $main $arguments"


echo "setup=$EPISIM_SETUP, params=$EPISIM_PARAMS"
echo ""
echo "command is $command"

echo ""
echo "using alternative java"
module add java/11
java -version

$command
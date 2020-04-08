#!/bin/bash
#SBATCH --time=01:40:00
#SBATCH --nodes=1
#SBATCH --ntasks-per-socket=48

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
#
# Start & pin multiple processes on different physical cores of a node
#
export SLURM_CPU_BIND=none

# Number of numa nodes
let nNuma=$(numactl --hardware | grep -oE 'available: [0-9]+' | grep -oE '[0-9]+')

# Total number of available worker processes
let totalWorker=${$SLURM_ARRAY_TASK_COUNT:-1} * nNuma
# Worker offset starting at 0
let offset=${SLURM_ARRAY_TASK_ID:-1} - 1

echo "totalWorker $totalWorker offset $offset (numa: $nNuma)"

for sId in $(seq 0 $nNuma - 1); do

  # Needs to be unique among all processes
  let workerId=offset+sId
  arguments="$input --config:controler.runId ${SLURM_JOB_NAME}${nID} -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
      --threads $SLURM_NTASKS_PER_SOCKET --total-worker $totalWorker --worker-index $workerId"

  command="java -cp $classpath $JAVA_OPTS @jvm.options $main $arguments"
  echo ""
  echo "command on socket $sId is $command"
  numactl --cpunodebind=$sId --membind=$sId $command &
done
wait
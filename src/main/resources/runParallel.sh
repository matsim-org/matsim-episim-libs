#!/bin/bash
#SBATCH --time=12:00:00
#SBATCH --nodes=1
#SBATCH -A bzz0020

date
hostname

cd $SLURM_SUBMIT_DIR

classpath="matsim-episim-*-SNAPSHOT.jar"

echo "***"
echo "classpath: $classpath"
echo "***"

# main
main="org.matsim.run.RunParallel"

module load java/11
java -version
#
# Start & pin multiple processes on different physical cores of a node
#
export SLURM_CPU_BIND=none

# Number of numa nodes
let nNuma=$(numactl --hardware | grep -oE 'available: [0-9]+' | grep -oE '[0-9]+')

# Total number of available worker processes
let totalWorker=$(( ${SLURM_ARRAY_TASK_COUNT:-1} * nNuma ))
# Worker offset starting at 0
let offset=$(( (${SLURM_ARRAY_TASK_ID:-1} - 1) * nNuma ))

echo "totalWorker $totalWorker offset $offset (numa: $nNuma)"
echo "setup=$EPISIM_SETUP, params=$EPISIM_PARAMS"

let numaIndex=$((nNuma - 1))
for sId in $(seq 0 $numaIndex); do

  # Add these to command if needed
  MONITOR="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=$(( 9010 + (sId * 2) )) -Dcom.sun.management.jmxremote.rmi.port=$(( 9011 + (sId * 2) ))  -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.net.preferIPv4Stack=true -Djava.rmi.server.hostname=0.0.0.0"
  DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$(( 5005 + sId ))"

  # Needs to be unique among all processes
  let workerId=offset+sId
  arguments="--tasks $SLURM_NTASKS_PER_SOCKET --total-worker $totalWorker --worker-index $workerId"
  command="java -cp $classpath $JAVA_OPTS @jvm.options -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector $main $arguments"
  echo ""
  echo "command on socket $sId is $command"
  numactl --cpunodebind=$sId --membind=$sId -- $command &
done
wait
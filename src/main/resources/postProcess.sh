#!/bin/bash
#SBATCH --time=05:00:00
#SBATCH --nodes=1
#SBATCH --ntasks-per-node=1
#SBATCH --cpus-per-task=96
#SBATCH -A bzz0020
#$ -l h_rt=18000
#$ -o ./logfile_$JOB_NAME_$JOB_ID.log
#$ -j y
#$ -m a
#$ -cwd
#$ -pe mp 6
#$ -l mem_free=10G

date
hostname

if [ -n "$SLURM_SUBMIT_DIR" ]; then
  echo "Using slurm"
  cd $SLURM_SUBMIT_DIR
  module load java/11
  cmd="srun"
else
  echo "Using qsub"
  module add java/11
  cmd=""
fi


classpath="matsim-episim-*-SNAPSHOT.jar"
# main
main="analysis calculateRValues extractInfectionsByAge --population=$EPISIM_INPUT/<REPLACE> --district Berlin"

java -version

echo "***"
echo "cmd: $classpath $main"
echo "***"

$cmd java -Xmx60G -jar $classpath $main

$cmd ./collect.sh Berlin

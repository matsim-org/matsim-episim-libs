#!/bin/bash
#SBATCH --time=12:00:00
#SBATCH --nodes=1
#SBATCH --ntasks-per-socket=10
#SBATCH -A bzz0020

date
hostname

cd $SLURM_SUBMIT_DIR

command="python calibrate.py 10 --runs 10 --objective hospital"

echo ""
echo "command is $command"

echo ""
echo "using alternative java"
module load java/11
module load anaconda3/2019.10
java -version

# Activate the virtual environment
source bin/activate

export SLURM_CPU_BIND=none
# Number of numa nodes
let nNuma=$(numactl --hardware | grep -oE 'available: [0-9]+' | grep -oE '[0-9]+')


let numaIndex=$((nNuma - 1))
for sId in $(seq 0 $numaIndex); do

  echo "command on socket $sId is $command"

   # Try another time in case of an error
   (numactl --cpunodebind=$sId --membind=$sId -- $command || (sleep 30 && numactl --cpunodebind=$sId --membind=$sId -- $command)) &
   sleep 60
done

wait

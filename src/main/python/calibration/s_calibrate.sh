#!/bin/bash
#SBATCH --time=12:00:00
#SBATCH --nodes=1
#SBATCH --ntasks-per-node=40
#SBATCH -A bzz0020

date
hostname

cd $SLURM_SUBMIT_DIR

command="python calibrate.py --objective multi"

echo ""
echo "command is $command"

echo ""
echo "using alternative java"
module load java/11.0.6
module load anaconda3/2019.10
java -version

# Activate the virtual environment
source bin/activate

export SLURM_CPU_BIND=none
let NTpn=--SLURM_NTASKS_PER_NODE	# all physical cores

for cid in $(seq 0 $NTpn); do
   let second=cid+96

   # Try another time in case of an error
   (taskset -c $cid,$second $command || taskset -c $cid,$second $command) &
   sleep 5
done

wait

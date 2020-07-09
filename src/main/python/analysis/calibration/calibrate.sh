#!/bin/bash --login
#$ -l h_rt=86400
#$ -o ./logfile_$JOB_NAME.log
#$ -j y
#$ -m a
#$ -M mueller@vsp.tu-berlin.de
#$ -cwd
#$ -pe mp 5
#$ -l mem_free=7G

date
hostname

command="python calibrate.py 15 --runs 3 --objective ci_correction --start 2020-03-07"

echo ""
echo "command is $command"

echo ""
echo "using alternative java"
module add java/11
java -version

# Activate the virtual environment
source bin/activate

# Script starts 5 processes in parallel
for i in $(seq 0 4); do
   $command &
   sleep 20
done

wait
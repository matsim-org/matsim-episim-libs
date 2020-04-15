#!/bin/bash
#SBATCH -p standard96:test
#SBATCH --time=00:05:00
#SBATCH --mem-bind=local


# Just tests some slurm properties

date
hostname

cd $SLURM_SUBMIT_DIR

echo "Tasks per Node/Core/Socket: $SLURM_NTASKS_PER_NODE / $SLURM_NTASKS_PER_SOCKET / $SLURM_NTASKS_PER_CORE"
echo "Node CPU/Job CPU:  $SLURM_CPUS_ON_NODE / $SLURM_JOB_CPUS_PER_NODE"
echo "Nodes: $SLURM_JOB_NUM_NODES"
echo "Tasks: $SLURM_NTASKS"
echo "Task PID: $SLURM_TASK_PID"
echo "CPU Bind: $SLURM_CPU_BIND"

echo "Array"
echo "###########"
echo "$SLURM_ARRAY_TASK_COUNT   Total number of tasks in a job array"
echo "$SLURM_ARRAY_TASK_ID	    Job array ID (index) number"
echo "$SLURM_ARRAY_TASK_MAX	    Job array's maximum ID (index) number"
echo "$SLURM_ARRAY_TASK_MIN	    Job array's minimum ID (index) number"
echo "$SLURM_ARRAY_TASK_STEP		Job array's index step size"
echo "$SLURM_ARRAY_JOB_ID		    Job array's master job ID number"
echo "###########"
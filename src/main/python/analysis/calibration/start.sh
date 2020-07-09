#!/bin/bash

export EPISIM_INPUT='<PUT INPUT DIR HERE>'

# Use Either one
qsub -V -N calibration calibrate.sh
sbatch --export=ALL --nodes=1 --job-name=calibration s_calibrate.sh
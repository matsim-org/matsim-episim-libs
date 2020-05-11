#!/usr/bin/env python
# -*- coding: utf-8 -*-

import argparse
import subprocess

import numpy as np
import pandas as pd
import optuna


def infection_rate(f, district, target_rate=2, target_interval=3):
    """  Calculates the R values between a fixed day interval and returns MSE according to target rate """

    df = pd.read_csv(f, sep="\t")
    df = df[df.district == district]

    rates = []
    for i in range(25, 40):
        prev = float(df[df.day == i - target_interval].nTotalInfected)
        today = float(df[df.day == i].nTotalInfected)

        rates.append(today / prev)

    rates = np.array(rates)

    return rates.mean(), np.square(rates - target_rate).mean()


def objective(trial):
    """ Runs one episim trial """

    n = trial.number
    c = trial.suggest_uniform("calibrationParameter", 1e-07, 5e-06)

    scenario = trial.study.user_attrs["scenario"]
    district = trial.study.user_attrs["district"]

    cmd = "java -jar matsim-episim-1.0-SNAPSHOT.jar scenarioCreation trial %s --number %d --calibParameter %f" % (scenario, n, c)

    print("Running calibration for %s (district: %s) : %s" % (scenario, district, cmd))
    subprocess.run(cmd)

    rate, error = infection_rate("output-calibration/%d/infections.txt" % n, district)
    trial.set_user_attr("mean_infection_rate", rate)

    return error


if __name__ == "__main__":
    # Needs to be run from top-level episim directory!

    parser = argparse.ArgumentParser(description="Run calibrations with optuna.")
    parser.add_argument("n_trials", metavar='N', type=int, nargs="?", help="Number of trials", default=10)
    parser.add_argument("--district", type=str, default="Berlin",
                        help="District to calibrate for. Should be 'unknown' if no district information is available")
    parser.add_argument("--scenario", type=str, help="Scenario module used for calibration", default="SnzBerlinScenario")

    args = parser.parse_args()

    study = optuna.create_study(study_name="calibration", direction="minimize",
                                storage="sqlite:///calibration.db", load_if_exists=True)

    study.set_user_attr("district", args.district)
    study.set_user_attr("scenario", args.scenario)

    study.optimize(objective, n_trials=args.n_trials)

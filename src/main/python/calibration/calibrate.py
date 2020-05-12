#!/usr/bin/env python
# -*- coding: utf-8 -*-

import argparse
import subprocess

import numpy as np
import optuna
import pandas as pd
from sklearn.metrics import mean_squared_log_error


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


def hospitalization_rate(f, district, target="berlin-hospital.csv", start="2020-03-15", end="2020-05-08"):
    """ Compares hospitalization rate """

    df = pd.read_csv(f, sep="\t", parse_dates=[2])
    df = df[df.district == district]
    cmp = pd.read_csv(target, parse_dates=[0], dayfirst=True)

    df = df[(df.date >= start) & (df.date <= end)]
    cmp = cmp[(cmp.Datum >= start) & (cmp.Datum <= end)]

    error_sick = mean_squared_log_error(cmp["Stationäre Behandlung"], df.nSeriouslySick)
    error_critical = mean_squared_log_error(cmp["Intensivmedizin"], df.nCritical)

    return error_sick, error_critical


def objective_unconstrained(trial):
    """ Objective for constrained infection dynamic. """

    n = trial.number
    c = trial.suggest_uniform("calibrationParameter", 1e-07, 5e-06)

    scenario = trial.study.user_attrs["scenario"]
    district = trial.study.user_attrs["district"]

    cmd = "java -jar matsim-episim-1.0-SNAPSHOT.jar scenarioCreation trial %s --number %d --calibParameter %.12f" % (scenario, n, c)

    print("Running calibration for %s (district: %s) : %s" % (scenario, district, cmd))
    subprocess.run(cmd, shell=True)

    rate, error = infection_rate("output-calibration/%d/infections.txt" % n, district)
    trial.set_user_attr("mean_infection_rate", rate)

    return error


def objective_offset(trial):
    """ Objective for offset in number of days """
    n = trial.number
    offset = trial.suggest_int('offset', -8, 8)

    scenario = trial.study.user_attrs["scenario"]
    district = trial.study.user_attrs["district"]

    # TODO: needs to be set manually when performing offset calibration
    c = 0.000006

    cmd = "java -jar matsim-episim-1.0-SNAPSHOT.jar scenarioCreation trial %s --days 90" \
          " --number %d --calibParameter %.12f --with-restrictions --offset %d" % (scenario, n, c, offset)

    print("Running calibration for %s (district: %s) : %s" % (scenario, district, cmd))
    subprocess.run(cmd, shell=True)
    e_sick, e_critical = hospitalization_rate("output-calibration-restrictions/%d/infections.txt" % n, district)

    trial.set_user_attr("error_sick", e_sick)
    trial.set_user_attr("error_critical", e_critical)

    return e_sick + e_critical


if __name__ == "__main__":
    # Needs to be run from top-level episim directory!

    parser = argparse.ArgumentParser(description="Run calibrations with optuna.")
    parser.add_argument("n_trials", metavar='N', type=int, nargs="?", help="Number of trials", default=10)
    parser.add_argument("--district", type=str, default="Berlin",
                        help="District to calibrate for. Should be 'unknown' if no district information is available")
    parser.add_argument("--scenario", type=str, help="Scenario module used for calibration", default="SnzBerlinScenario")
    parser.add_argument("--objective", type=str, choices=["unconstrained", "offset"], default="unconstrained")

    args = parser.parse_args()

    study = optuna.create_study(study_name=args.objective, direction="minimize",
                                storage="sqlite:///calibration.db", load_if_exists=True)

    study.set_user_attr("district", args.district)
    study.set_user_attr("scenario", args.scenario)

    objective = objective_unconstrained if args.objective == "unconstrained" else objective_offset

    study.optimize(objective, n_trials=args.n_trials)

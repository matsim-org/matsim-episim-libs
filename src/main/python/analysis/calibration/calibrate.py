#!/usr/bin/env python
# -*- coding: utf-8 -*-

import argparse
import subprocess
from datetime import datetime, date, timedelta

import numpy as np
import optuna
import pandas as pd
from sklearn.metrics import mean_squared_log_error

if not hasattr(date, 'fromisoformat'):
    # python 3.6 backwards compatibility
    def parse(date_string):
        return datetime.strptime(date_string, "%Y-%m-%d").date()


    fromisoformat = parse

else:
    fromisoformat = date.fromisoformat


def read_data(f, district, hospital, rki, window=5):
    """Reads in three csv files"""
    # Simulation output
    df = pd.read_csv(f, sep="\t", parse_dates=[2])
    df = df[df.district == district]
    df.set_index('date', drop=False, inplace=True)
    df['cases'] = df.nShowingSymptomsCumulative.diff(1)
    df['casesSmoothed'] = df.cases.rolling(window).mean()
    df['casesNorm'] = df.casesSmoothed / df.casesSmoothed.mean()

    hospital = pd.read_csv(hospital, parse_dates=[0], dayfirst=True)
    rki = pd.read_csv(rki, parse_dates={'date': ['month', 'day', 'year']})
    rki.set_index('date', drop=False, inplace=True)
    rki['casesCumulative'] = rki.cases.cumsum()
    rki['casesSmoothed'] = rki.cases.rolling(window).mean()
    rki['casesNorm'] = rki.casesSmoothed / rki.casesSmoothed.mean()

    return df, hospital, rki


def percentage_error(actual, predicted):
    """ https://stackoverflow.com/questions/47648133/mape-calculation-in-python """
    res = np.empty(actual.shape)
    for j in range(actual.shape[0]):
        # Small values are measured with mean error
        if abs(actual[j]) >= 15:
            res[j] = (actual[j] - predicted[j]) / actual[j]
        else:
            res[j] = (actual[j] - predicted[j]) / np.mean(actual)
    return res


def mean_absolute_percentage_error(y_true, y_pred):
    return np.mean(np.abs(percentage_error(np.asarray(y_true), np.asarray(y_pred)))) * 100


def reinfection_number(f, target=2.5, days=20):
    """ Calculates reinfection number of a run """

    ev = pd.read_csv(f, sep="\t")
    df = ev[ev.time <= days * 86400]

    # persons at end of interval are not considered
    relevant = set(ev.infected[ev.time <= (days - 4) * 86400])
    no_inf = set(df.infected).difference(set(ev.infector))

    if not relevant:
        return 0, target ** 2

    counts = df['infector'].value_counts()

    res = np.concatenate((np.zeros(len(no_inf)), counts[counts.index.isin(relevant)].array))

    return res.mean(), np.square(res.mean() - target)


def infection_rate(f, district, target_rate=2, target_interval=3, days=15):
    """  Calculates the R values between a fixed day interval and returns MSE according to target rate """

    df = pd.read_csv(f, sep="\t")
    df = df[df.district == district]

    total = float(df[df.day == 1].nSusceptible)

    # comparison interval starts when 2% of susceptible infected
    start = df[df.nTotalInfected >= total * 0.002].day
    if not start.empty:
        start = start.iloc[0]
    else:
        start = df.day.iloc[-1]

    diff = df.iloc[-1].day - start - days

    if diff < 0:
        print("Simulation interval may be too short, had to adjust by", diff)
        start += diff

    # first day is 1 and not 0
    if start - target_interval <= 0:
        print("Adjust start to target interval")
        start = target_interval + 1
        days = min(days, len(df.day) - target_interval)

    rates = []
    for i in range(start, start + days):
        prev = float(df[df.day == i - target_interval].nTotalInfected)
        today = float(df[df.day == i].nTotalInfected)

        rates.append(today / prev)

    rates = np.array(rates)

    return rates.mean(), np.square(rates - target_rate).mean()


def calc_multi_error(f, district, start, end, assumed_dz=2, hospital="berlin-hospital.csv", rki="berlin-cases.csv"):
    """ Compares hospitalization rate """

    df, hospital, rki = read_data(f, district, hospital, rki)

    df = df[(df.date >= start) & (df.date <= end)]
    hospital = hospital[(hospital.Datum >= start) & (hospital.Datum <= end)]
    rki = rki[(rki.date >= start) & (rki.date <= end)]

    peak = str(df.loc[df.cases.idxmax()].date)

    error_sick = mean_squared_log_error(hospital["Stationäre Behandlung"], df.nSeriouslySick)
    error_critical = mean_squared_log_error(hospital["Intensivmedizin"], df.nCritical)

    # Assume fixed Dunkelziffer
    error_cases = mean_squared_log_error(rki.casesSmoothed * assumed_dz, df.casesSmoothed)
    # error_cases = mean_squared_log_error(rki.casesNorm, df.casesNorm)

    # Dunkelziffer
    dz = float(df.nContagiousCumulative.tail(1) / rki.casesCumulative.tail(1))

    return error_cases, error_sick, error_critical, peak, dz


def objective_reinfection(trial):
    """ Objective for reinfection number R """

    n = trial.number
    c = trial.suggest_loguniform("calibrationParameter", 0.5e-7, 1e-4)

    scenario = trial.study.user_attrs["scenario"]
    jvm = trial.study.user_attrs["jvm_opts"]

    results = []
    for i in range(trial.study.user_attrs["runs"]):
        cmd = "java -jar %s matsim-episim-1.0-SNAPSHOT.jar scenarioCreation trial %s --number %d --run %d --unconstrained --calibParameter %.12f" \
              % (jvm, scenario, n, i, c)

        print("Running calibration for %s : %s" % (scenario, cmd))
        subprocess.run(cmd, shell=True)

        res = reinfection_number("output-calibration-unconstrained/%d/run%d/infectionEvents.txt" % (n, i), target=2.5)

        # Ignore results with no infections at all
        if res[0] == 0:
            continue

        results.append(res)

    if not results:
        results.append((0, 2.5 ** 2))

    df = pd.DataFrame(results, columns=["target", "error"])
    print(df)

    for k, v in df.mean().iteritems():
        trial.set_user_attr(k, v)

    trial.set_user_attr("df", df.to_json())
    return df.error.mean()


def objective_unconstrained(trial):
    """ Objective for constrained infection dynamic. """

    n = trial.number
    c = trial.suggest_uniform("calibrationParameter", 0.7e-5, 1.7e-5)

    scenario = trial.study.user_attrs["scenario"]
    district = trial.study.user_attrs.get("district", "unknown")
    jvm = trial.study.user_attrs["jvm_opts"]

    results = []
    for i in range(trial.study.user_attrs["runs"]):
        cmd = "java -jar %s matsim-episim-1.0-SNAPSHOT.jar scenarioCreation trial %s --number %d --run %d --unconstrained --calibParameter %.12f" \
              % (jvm, scenario, n, i, c)

        print("Running calibration for %s (district: %s) : %s" % (scenario, district, cmd))
        subprocess.run(cmd, shell=True)

        res = infection_rate("output-calibration-unconstrained/%d/run%d/infections.txt" % (n, i), district)
        results.append(res)

    df = pd.DataFrame(results, columns=["target", "error"])
    print(df)

    for k, v in df.mean().iteritems():
        trial.set_user_attr(k, v)

    trial.set_user_attr("df", df.to_json())
    return df.error.mean()


def objective_ci_correction(trial):
    """ Objective for ci correction """

    n = trial.number

    start = fromisoformat(trial.study.user_attrs["start"]) + timedelta(days=trial.suggest_int('ciOffset', -2, 2))
    params = dict(
        number=n,
        scenario=trial.study.user_attrs["scenario"],
        district=trial.study.user_attrs["district"],
        days=trial.study.user_attrs["days"],
        dz=trial.study.user_attrs["dz"],
        jvm=trial.study.user_attrs["jvm_opts"],
        alpha=1,
        offset=0,
        # Parameter to calibrate
        correction=trial.suggest_uniform("ciCorrection", 0.2, 0.9),
        start=str(start)
    )

    end = fromisoformat(params["start"]) + timedelta(days=params["days"])

    results = []

    for i in range(trial.study.user_attrs["runs"]):
        cmd = "java %(jvm)s -jar matsim-episim-1.0-SNAPSHOT.jar scenarioCreation trial %(scenario)s --days %(days)s" \
              f" --number %(number)d --run {i} --alpha %(alpha).3f --offset %(offset)d" \
              " --correction %(correction).3f --start \"%(start)s\"" % params

        print("Running ci correction with params: %s" % params)
        print("Running calibration command: %s" % cmd)
        subprocess.run(cmd, shell=True)
        res = calc_multi_error("output-calibration-%s/%d/run%d/infections.txt" % (params["start"], n, i), params["district"],
                               start=params["start"], end=str(end), assumed_dz=params["dz"])

        results.append(res)

    df = pd.DataFrame(results, columns=["error_cases", "error_sick", "error_critical", "peak", "dz"])
    print(df)

    for k, v in df.mean().iteritems():
        trial.set_user_attr(k, v)

    trial.set_user_attr("df", df.to_json())

    return df.error_cases.mean()


def objective_multi(trial):
    """ Objective for multiple parameter """
    global district, scenario

    n = trial.number

    params = dict(
        scenario=scenario,
        district=district,
        number=n,
        # Parameter to calibrate
        # c=trial.suggest_uniform("calibrationParameter", 0.5e-06, 3e-06),
        offset=trial.suggest_int('offset', -3, 3),
        alpha=1,
        correction=trial.suggest_uniform("ciCorrection", 0.2, 1),
        hospital=trial.suggest_uniform('hospital', 1, 2)
    )

    cmd = "java -Xmx7G -jar matsim-episim-1.0-SNAPSHOT.jar scenarioCreation trial %(scenario)s --days 90" \
          " --number %(number)d --alpha %(alpha).3f --offset %(offset)d --hospitalFactor %(hospital).3f" \
          " --correction %(correction).3f" % params

    print("Running multi objective with params: %s" % params)
    print("Running calibration command: %s" % cmd)
    subprocess.run(cmd, shell=True)
    e_cases, e_sick, e_critical, peak, dz = calc_multi_error("output-calibration/%d/run0/infections.txt" % n, params["district"],
                                                             start="2020-03-01", end="2020-06-01")

    trial.set_user_attr("error_cases", e_cases)
    trial.set_user_attr("error_sick", e_sick)
    trial.set_user_attr("error_critical", e_critical)
    trial.set_user_attr("peak", peak)
    trial.set_user_attr("dz", dz)

    return e_cases, e_sick + e_critical


if __name__ == "__main__":
    # Needs to be run from top-level episim directory!

    parser = argparse.ArgumentParser(description="Run calibrations with optuna.")
    parser.add_argument("n_trials", metavar='N', type=int, nargs="?", help="Number of trials", default=10)
    parser.add_argument("--district", type=str, default="Berlin",
                        help="District to calibrate for. Should be 'unknown' if no district information is available")
    parser.add_argument("--scenario", type=str, help="Scenario module used for calibration", default="SnzBerlinWeekScenario2020")
    parser.add_argument("--runs", type=int, default=1, help="Number of runs per objective")
    parser.add_argument("--start", type=str, default="2020-03-06", help="Start date for ci correction")
    parser.add_argument("--days", type=int, default="70", help="Number of days to simulate after ci correction")
    parser.add_argument("--dz", type=float, default="1.5", help="Assumed Dunkelziffer for error metric")
    parser.add_argument("--objective", type=str, choices=["unconstrained", "ci_correction", "multi"], default="unconstrained")
    parser.add_argument("--jvm-opts", type=str, default="-Xmx8G")

    args = parser.parse_args()

    if args.objective == "multi":
        study = optuna.multi_objective.create_study(
            study_name=args.objective, storage="sqlite:///calibration.db", load_if_exists=True,
            directions=["minimize"] * 2
        )

        district = args.district
        scenario = args.scenario

    else:
        study = optuna.create_study(
            study_name=args.objective + ("_" if args.start else "") + args.start, storage="sqlite:///calibration.db", load_if_exists=True,
            direction="minimize"
        )

    # Copy all args to study
    for k, v in args.__dict__.items():
        study.set_user_attr(k, v)

    objectives = {
        "multi": objective_multi,
        "unconstrained": objective_unconstrained,
        "ci_correction": objective_ci_correction
    }

    objective = objectives[args.objective]

    study.optimize(objective, n_trials=args.n_trials)

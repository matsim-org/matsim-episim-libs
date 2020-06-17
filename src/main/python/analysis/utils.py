#!/usr/bin/env python
# -*- coding: utf-8 -*-

import zipfile
from os import path

import numpy as np
import pandas as pd


def read_batch_run(run):
    """ Reads one batch run from a directory with the _info and .zip file, or directly from the zip file. """

    info = None
    if path.isdir(run):
        info = pd.read_csv(path.join(run, "_info.txt"), sep=";")
        run = path.join(run, "summaries.zip")

    frames = []

    with zipfile.ZipFile(run) as z:

        if info is None:
            with z.open("_info.txt") as f:
                info = pd.read_csv(f, sep=";")

        i = 0
        for f in z.namelist():
            if "infections.txt" not in f:
                continue

            idx = f.replace(".infections.txt.csv", "")
            with z.open(f) as csv:
                df = read_run(csv)
                df['run'] = i

                for name, v in info[info.RunId == idx].iteritems():
                    if v.values.shape[0] > 0:
                        df[name] = v.values[0]

                frames.append(df)

            i += 1

    return pd.concat(frames)


def read_run(f, district=None, window=5):
    """ Reads in one simulation run.

    :param f: input infection.txt
    :param district: district to filter for
    :param window: Number of days for smoothing case numbers
    """
    # Simulation output
    df = pd.read_csv(f, sep="\t", parse_dates=[2])

    df.set_index('date', drop=False, inplace=True)

    if district is not None:
        df = df[df.district == district]
        df['cases'] = df.nShowingSymptomsCumulative.diff(1)
        df['casesSmoothed'] = df.cases.rolling(window).mean()
        df['casesNorm'] = df.casesSmoothed / df.casesSmoothed.mean()

    return df


def read_case_data(rki, hospital, window=5):
    """ Reads in RKI and hospital case numbers from csv """
    hospital = pd.read_csv(hospital, parse_dates=[0], dayfirst=True)
    rki = pd.read_csv(rki, parse_dates={'date': ['month', 'day', 'year']})
    rki.set_index('date', drop=False, inplace=True)
    rki['casesCumulative'] = rki.cases.cumsum()
    rki['casesSmoothed'] = rki.cases.rolling(window).mean()
    rki['casesNorm'] = rki.casesSmoothed / rki.casesSmoothed.mean()

    return rki, hospital


def infection_rate(f, district, target_rate=2, target_interval=3):
    """  Calculates the Growth between a fixed day interval and returns MSE according to target rate """

    df = pd.read_csv(f, sep="\t")
    df = df[df.district == district]

    rates = []
    for i in range(25, 40):
        prev = float(df[df.day == i - target_interval].nTotalInfected)
        today = float(df[df.day == i].nTotalInfected)

        rates.append(today / prev)

    rates = np.array(rates)

    return rates.mean(), np.square(rates - target_rate).mean()

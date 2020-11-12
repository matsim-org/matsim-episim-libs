#!/usr/bin/env python
# -*- coding: utf-8 -*-

import io
import zipfile
from collections import defaultdict
from os import path

import numpy as np
import pandas as pd


def read_batch_run(run, r_values=False, age_groups=None):
    """ Reads one batch run from a directory with the _info and .zip file, or directly from the zip file.

    :param run path to folder or zip file of the run
    :param r_values whether to read r values from the file
    :param age_groups If given aggregates infections by age. Param needs to be array of monotonically increasing bin edges,
                    including the rightmost edge.
    """

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

                if r_values:
                    with z.open(idx + ".rValues.txt.csv") as rCSV:
                        rv = pd.read_csv(rCSV, sep="\t", parse_dates=True, index_col="date")
                        df['rValue'] = rv.rValue
                        df['newContagious'] = rv.newContagious

                if age_groups:
                    with z.open(idx + ".post.infectionsByAge.txt") as rCSV:
                        rv = pd.read_csv(rCSV, sep="\t", parse_dates=True, index_col="date")
                        df = df.join(group_by_age(rv, age_groups))

                    with z.open(idx + ".post.seriouslySickByAge.txt") as rCSV:
                        rv = pd.read_csv(rCSV, sep="\t", parse_dates=True, index_col="day")
                        df = df.merge(group_by_age(rv, age_groups, "sick"), on="day")

                    with z.open(idx + ".post.criticalByAge.txt") as rCSV:
                        rv = pd.read_csv(rCSV, sep="\t", parse_dates=True, index_col="day")
                        df = df.merge(group_by_age(rv, age_groups, "crit"), on="day")

                frames.append(df)

            i += 1

    return pd.concat(frames)


def group_by_age(rv, age_groups, prefix="age", scale=None):
    """ Groups data frame by age """
    d = rv.to_numpy()
    data = {}

    off = age_groups[0]
    for i in range(len(age_groups) - 1):
        idx = np.arange(age_groups[i] - off, age_groups[i + 1] - off, step=1)
        column = d[:, idx].sum(axis=1)

        name = prefix + "%d-%d" % (idx[0] + off, idx[-1] + off)

        if scale is not None:
            column = np.multiply(column, scale[i])

        data[name] = column

    return pd.DataFrame(index=rv.index.copy(), data=data)


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


def read_case_data(rki, meldedatum, hospital, window=5):
    """ Reads in RKI and hospital case numbers from csv """
    hospital = pd.read_csv(hospital, parse_dates=[0], dayfirst=True)
    rki = pd.read_csv(rki, parse_dates={'date': ['month', 'day', 'year']})
    rki.set_index('date', drop=False, inplace=True)
    rki['casesCumulative'] = rki.cases.cumsum()
    rki['casesSmoothed'] = rki.cases.rolling(window).mean()
    rki['casesNorm'] = rki.casesSmoothed / rki.casesSmoothed.mean()
    
    meldedatum = pd.read_csv(meldedatum, parse_dates={'date': ['month', 'day', 'year']})
    meldedatum.set_index('date', drop=False, inplace=True)
    meldedatum['casesCumulative'] = meldedatum.cases.cumsum()
    meldedatum['casesSmoothed'] = meldedatum.cases.rolling(window).mean()
    meldedatum['casesNorm'] = meldedatum.casesSmoothed / meldedatum.casesSmoothed.mean()

    return rki, meldedatum, hospital


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

def aggregate_batch_run(run):
    """ Reads a batch run with all files and aggregates over all seeds by using the mean.

     :param run path to the zip file
     :return nothing, aggregated run will be written based on the filename
     """
    # run id to file id to list of files
    runs = defaultdict(lambda: defaultdict(lambda: []))
    # run id to aggregated id
    idMap = {}
    info = None

    with zipfile.ZipFile(run) as z:

        with z.open("_info.txt") as f:
            info = pd.read_csv(f, sep=";")

        params = set(info.columns).difference({'RunScript', 'Config', 'RunId', 'Output'})
        woSeed = params.difference({'seed'})

        byId = info.groupby(list(woSeed), as_index=False).agg(ids=('RunId', set))

        for row in byId.itertuples():
            for idx in row.ids:
                idMap[idx] = row.Index

        for f in z.namelist():
            idx, _, filename = f.partition(".")
            if idx not in idMap:
                continue

            with z.open(f) as zf:
                df = pd.read_csv(zf, sep="\t")
                runs[idMap[idx]][filename].append(df)


    with zipfile.ZipFile(run.replace(".zip", "-aggr.zip"),
                         mode="w", compresslevel=9,
                         compression=zipfile.ZIP_DEFLATED) as z:

        info = byId.reset_index().rename(columns={"index": "RunId"}).drop(columns=["ids"])
        info["RunScript"] = "na"
        info["Config"] = "na"
        info["Output"] = "na"

        with z.open("_info.txt", "w") as zf:
            buf = io.TextIOWrapper(zf, encoding="utf8", newline="\n")
            info.to_csv(buf, columns = ["RunScript", "Config", "RunId", "Output"] + list(woSeed),
                        sep=";", mode="w", line_terminator="\n", index=False)
            buf.flush()

        for runId, files in runs.items():
            for filename, dfs in files.items():
                concat = pd.concat(dfs)
                by_row_index = concat.groupby(concat.index)
                means = by_row_index.mean()

                # attach non numeric columns without aggregating
                nonNumeric = dfs[0].columns.difference(means.columns)
                for column in nonNumeric:
                    means[column] = dfs[0][column]

                with z.open(str(runId) + "." + filename, "w") as zf:
                    buf = io.TextIOWrapper(zf, encoding="utf8", newline="\n")
                    means.to_csv(buf, sep="\t", columns=list(dfs[0].columns), mode="w", line_terminator="\n", index=False)
                    buf.flush()



def calc_r_reduction(base_case, base_variables, df, group_by=None):
    """ Calculates the reduction of r
    
    :param base_case: data set with the base case
    :param base_variables: columns to group by in the base case
    :param df: data set for which to calculate the reductions
    :param group_by: columns to group by in the result
    :return: aggregated data frame
    """

    if group_by is None:
        group_by = base_variables
    else:
        group_by.insert(0, "seed")

    base_variables.insert(0, "seed")

    base_r = base_case.groupby(base_variables).agg(rValue=("rValue", "mean"))

    aggr = df.groupby(group_by).agg(rValue=("rValue", "mean"))

    aggr['baseR'] = 0

    for index, value in base_r.itertuples():
        aggr.loc[index, "baseR"] = value

    aggr['reduction'] = 1 - aggr.rValue / aggr.baseR

    group_by.remove("seed")

    result = aggr.groupby(group_by).agg(rValue=("rValue", "mean"), rReduction=("reduction", "mean"),
                                        std=("reduction", "std"), sem=("reduction", "sem"))

    return result

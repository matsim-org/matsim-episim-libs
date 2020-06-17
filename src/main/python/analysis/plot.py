#!/usr/bin/env python
# -*- coding: utf-8 -*-

import matplotlib.pyplot as plt


def comparison_plots(dfs: dict, rki, hospital, logy=True, figsize=(14, 8)):
    """ Creates plot to compare simulations runs with actual case numbers. """

    last = rki.casesCumulative.tail(1)

    fig, ax = plt.subplots(figsize=figsize, dpi=150)
    rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], color="orange", ax=ax)
    plt.title("Cases")

    for name, df in dfs.items():
        dz = float(df.loc[last.index].nContagiousCumulative / last)
        df.plot.scatter(x="date", y=["cases"], label=["Simulated Cases | %s | DZ: %.2f" % (name, dz)], logy=logy, ax=ax)

    fig, ax = plt.subplots(figsize=figsize, dpi=150)
    rki.plot(x="date", y=["casesNorm"], label=["RKI cases norm"], ax=ax)
    plt.title("Normalized cases")
    for name, df in dfs.items():
        dz = float(df.loc[last.index].nContagiousCumulative / last)
        df.plot(x="date", y=["casesNorm"], label=["Sim. Cases norm. | %s | DZ: %.2f" % (name, dz)], ax=ax)

    fig, ax = plt.subplots(figsize=figsize, dpi=150)
    hospital.plot(x="Datum", y=["Stationäre Behandlung", "Intensivmedizin"], logy=logy, ax=ax)
    for name, df in dfs.items():
        df.plot(x="date", y=["nSeriouslySick", "nCritical"], label=["nSeriouslySick " + name, "nCritical" + name], logy=logy, ax=ax)

    plt.title("Hospitalization")

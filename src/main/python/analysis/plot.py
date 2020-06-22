#!/usr/bin/env python
# -*- coding: utf-8 -*-

import matplotlib.pyplot as plt
import seaborn as sns


def comparison_plots(dfs: dict, rki, hospital, logy=True, figsize=(14, 8)):
    """ Creates plot to compare simulations runs with actual case numbers. """

    # Find lowest common last date
    last = min(rki.casesCumulative.tail(1).index, next(iter(dfs.values())).nContagiousCumulative.tail(1).index)

    colors = sns.color_palette(n_colors=len(dfs) + 1)

    fig, ax = plt.subplots(figsize=figsize, dpi=150)
    rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], color=colors[0], ax=ax)
    plt.title("Cases")
    for i, (name, df) in enumerate(dfs.items()):
        dz = float(df.loc[last].nContagiousCumulative / rki.loc[last].casesCumulative)
        df.plot.scatter(x="date", y=["cases"], label=["Simulated Cases | %s | DZ: %.2f" % (name, dz)],
                        color=colors[i+1], logy=logy, ax=ax)

    fig, ax = plt.subplots(figsize=figsize, dpi=150)
    rki.plot(x="date", y=["casesNorm"], label=["RKI cases norm"], color=colors[0], ax=ax)
    plt.title("Normalized cases")
    for i, (name, df) in enumerate(dfs.items()):
        dz = float(df.loc[last].nContagiousCumulative / rki.loc[last].casesCumulative)
        df.plot(x="date", y=["casesNorm"], label=["Sim. Cases norm. | %s | DZ: %.2f" % (name, dz)], color=colors[i+1], ax=ax)

    fig, ax = plt.subplots(figsize=figsize, dpi=150)
    hospital.plot(x="Datum", y=["Stationäre Behandlung", "Intensivmedizin"], logy=logy, ax=ax)
    for name, df in dfs.items():
        df['sc'] = df.nSeriouslySick + df.nCritical

        df.plot(x="date", y=["sc", "nCritical"], label=["nSer.Sick +nCrit. " + name, "nCritical " + name], logy=logy, ax=ax)

    plt.title("Hospitalization")

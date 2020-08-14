#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os
import pandas as pd
import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt

from matplotlib.ticker import ScalarFormatter, PercentFormatter
from matplotlib.dates import AutoDateLocator, AutoDateFormatter, ConciseDateFormatter
from datetime import datetime

from utils import read_batch_run, read_case_data, read_run, infection_rate
from plot import comparison_plots

#%%

sns.set_style("whitegrid")
sns.set_context("paper")
sns.set_palette("deep")

dateFormater = ConciseDateFormatter(AutoDateLocator())

#%%

rki, hospital = read_case_data("berlin-cases.csv", "berlin-hospital.csv")


#%% 

def infections(f):
    ev = pd.read_csv(f, sep="\t")
    
    no_inf = set(ev.infected).difference(set(ev.infector))
    
    res = np.sort(np.concatenate((ev['infector'].value_counts().array, np.zeros(len(no_inf)))))
    
    return pd.DataFrame(res)



#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3))
hue = sns.color_palette(n_colors=3)


for i, s in enumerate((0.0, 1.0, 1.5)):
    counts = np.load("data/dispersion/%s_aggr.npy" % s)
        
    # only select persons that infected others
    #counts = counts[counts!=0]
    
    hist, bin_edges = np.histogram(counts, bins=np.arange(0, 30), density=True)
    
    plt.bar(bin_edges[:-1] + (i-1) * 0.3, hist, width=0.3, color=hue[i], label=f"$\sigma = {s} \Rightarrow k={0.4}$" )
    
    
plt.xlim(-0.6, 10.6)
plt.ylim( 0.001, 1)
plt.legend()
plt.yscale("log")

ax = plt.gca()
ax.yaxis.set_major_formatter(PercentFormatter(xmax=1, decimals=1))


#%%

# max tracing capacity
m = 2147483647

susp = read_batch_run("data/suspContainment.zip")


#%%

df = susp[(susp.tracingCapacity==m) & (susp.unrestricted=="yes")]
#df = suspT

hue = sns.color_palette(n_colors=3)

g = sns.relplot(x="date", y="cases", estimator="mean", ci="q95", palette=hue,
                hue="sigma", col="containment", row="tracingCapacity",
                kind="line", data=df)

g.fig.set_dpi(250)
g.fig.set_size_inches((9.5, 3.0))

for ax in g.axes.flat:
    ax.set_yscale('log')
    ax.set_ylim(bottom=1000)
    ax.set_xlim(datetime.fromisoformat("2020-03-01"), datetime.fromisoformat("2020-10-01"))
    
    ax.xaxis.set_major_formatter(dateFormater)
    ax.yaxis.set_major_formatter(ScalarFormatter())
    
    
#ci = datetime.fromisoformat("2020-03-07")
#plt.axvline(ci, color="gray", linewidth=1, linestyle="--", alpha=0.8, ax=ax)
#plt.text(ci, 1.2, ' Date of ci change', color="gray")

#%%


df = susp[(susp.unrestricted=="yes") & (susp.tracingCapacity==0)]
#df = suspT

hue = sns.color_palette(n_colors=3)

g = sns.relplot(x="date", y="cases", estimator="mean", ci="q95", palette=hue,
                hue="sigma", col="containment",
                kind="line", data=df)

g.fig.set_dpi(250)
g.fig.set_size_inches((9.5, 3.0))


for ax in g.axes.flat:
    ax.set_yscale('log')
    ax.set_ylim(bottom=5)
    ax.set_xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2020-12-01"))
    
    ax.xaxis.set_major_formatter(dateFormater)
    ax.yaxis.set_major_formatter(ScalarFormatter())


#%%

df = susp[(susp.unrestricted=="yes") & (susp.tracingCapacity == 90) & (susp.containment !='GROUP_SIZES')]
#df = suspT

hue = sns.color_palette(n_colors=3)

g = sns.relplot(x="date", y="cases", estimator="mean", ci="q95", palette=hue,
                hue="sigma", style="containment", row="tracingCapacity", col="sigma",
                kind="line", data=df,
                height=5, aspect=0.5)

g.fig.set_dpi(250)
g.fig.set_size_inches((9.5, 3.0))


for ax in g.axes.flat:
    ax.set_yscale('log')
    ax.set_ylim(bottom=5000)
    ax.set_xlim(datetime.fromisoformat("2020-03-20"), datetime.fromisoformat("2020-06-01"))
    
    ax.xaxis.set_major_formatter(dateFormater)
    ax.yaxis.set_major_formatter(ScalarFormatter())

##%



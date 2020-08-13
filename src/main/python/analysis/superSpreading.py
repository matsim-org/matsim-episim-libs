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

susp = read_batch_run("data/superSpreading.zip")
#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

df = susp[(susp.ciChange=="no") & (susp.groupSize != 42) & ( (susp.sigma == 0.5) | (susp.sigma == 0.75) )]

hue = sns.color_palette(n_colors=3)


sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax, 
             style="sigma", hue="groupSize", palette=hue,
             data=df)

plt.yscale("log")
plt.ylim(bottom=1)
plt.xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2020-09-31"))


#%%

suspT = read_batch_run("data/suspTracing.zip")

#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3))
hue = sns.color_palette(n_colors=3)


for i, s in enumerate((0.0, 1.0, 2.0)):
    counts = np.load("data/dispersion/%s_aggr.csv.npy" % s)
        
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

m = 0x7fffffff
df = suspT[suspT.tracingCapacity==m]

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

hue = sns.color_palette(n_colors=4)

sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax, 
             hue="sigma", style="strategy", palette=hue, data=df)

plt.yscale("log")
plt.ylim(bottom=1)

#%%

df = suspT[(suspT.tracingCapacity==30) & (suspT.sigma < 2) & (suspT.strategy != "LOCATION_WITH_TESTING")]
#df = suspT

g = sns.relplot(x="date", y="cases", estimator="mean", ci="q95",
                hue="sigma", col="strategy", row="tracingCapacity",
                kind="line", data=df)


for ax in g.axes.flat:
    ax.set_yscale('log')
    ax.set_ylim(bottom=10)
    ax.set_xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2020-09-01"))
    
    ax.xaxis.set_major_formatter(dateFormater)
    ax.yaxis.set_major_formatter(ScalarFormatter())


#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

bins = np.arange(1, 30)
width = 0.25
i = 0

for (s, n) in ( (0.0, 7), (0.5, 9), (0.75, 11)):
    f = "data/groupSizes/seed_4711-remaining_0.5-sigma_%s-bySize_yes/groupSizes%d.infectionEvents.txt" % (s, n)
    d = pd.read_csv(f, sep="\t")    
    d = d[0:32914]
    
    vc = d['infector'].value_counts()
    
    if bins is None:
        heights, bins = np.histogram(vc)
        width = (bins[1] - bins[0])/4
    else:
        heights, _ = np.histogram(vc, bins=bins)
        
    ax.bar(bins[:-1] + width*i, heights, width=width, label="sigma=%s"%s)
    #d['infector'].value_counts().hist(bins=30, label="sigma=%s"%s, alpha=0.5)
    i += 1

plt.ylabel("Secondary infections")
plt.yscale('log')
plt.legend()




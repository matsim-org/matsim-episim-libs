import os
from datetime import datetime

import pandas as pd
import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt
from matplotlib.ticker import ScalarFormatter
from matplotlib.dates import AutoDateLocator, AutoDateFormatter, ConciseDateFormatter


from utils import read_batch_run, read_case_data, read_run, infection_rate
from plot import comparison_plots

#%%

sns.set_style("whitegrid")
sns.set_context("paper")

dateFormater = ConciseDateFormatter(AutoDateLocator())

palette = sns.color_palette()


#%%

rki, hospital = read_case_data("berlin-cases.csv", "berlin-hospital.csv")

#%%  Graphs for outdoor / indoor runs

outdoor = read_batch_run("data/outdoor.zip")

#%% 

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))
hue = sns.color_palette(n_colors=2)

rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], color=palette[4], ax=ax, logy=True)

sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax,
             style="tracingCapacity", hue="furtherMeasuresOnOct1", palette=hue, 
             data=outdoor)


ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())


plt.ylim(bottom=1)
plt.xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2021-07-31"))
plt.legend(loc="upper left")
plt.title("Daily new infections aggregated over all random seeds")

current_handles, current_labels = plt.gca().get_legend_handles_labels()
current_labels[5] = "inf"

plt.legend(current_handles, current_labels)


#%%

holiday = read_batch_run("data/holidayReturnees4.zip")


#%% 

df = holiday[(holiday.furtherMeasuresOnOct1=="no") & (holiday.infectionsPerDay.isin((1, 5, 15, 30)))]

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))
hue = sns.color_palette(n_colors=4)

rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], color=palette[4], ax=ax, logy=True)

sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax,
             hue="infectionsPerDay", style="tracingCapacity", palette=hue, 
             data=df)

ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())

current_handles, current_labels = plt.gca().get_legend_handles_labels()

# Adjust for sample size
for i in range(1, 5):
    current_labels[i] = int(current_labels[i]) * 4
    
current_labels[7] = "inf"
    
plt.legend(current_handles, current_labels, loc="best")


plt.ylim(bottom=10)
plt.xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2021-04-30"))
plt.title("Daily new infections aggregated over all random seeds")
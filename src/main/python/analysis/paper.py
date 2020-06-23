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


#%%

rki, hospital = read_case_data("berlin-cases.csv", "berlin-hospital.csv")

#%%

df = read_batch_run("data/section3-3.zip")

#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

ci = datetime.fromisoformat("2020-03-07")

plt.axvline(ci, color="gray", linewidth=1, linestyle="--", alpha=0.8)
plt.text(ci, 1.2, ' Date of ci change', color="gray")

rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], color="red", ax=ax, logy=True)
sns.lineplot(x="date", y="cases", estimator="mean", ci="sd", ax=ax,
             label="Uncalibrated", data=df[(df.ci==1) & (df.alpha==1)])

sns.lineplot(x="date", y="cases", estimator="mean", ci="sd", ax=ax,
             label=r"Calibrated ci", data=df[(df.ci!=1) & (df.alpha==1)])

sns.lineplot(x="date", y="cases", estimator="mean", ci="sd", ax=ax,
             label=r"Calibrated $\alpha$ & ci", data=df[(df.ci!=1) & (df.alpha==1.2)])

ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())

plt.ylim(bottom=1)
plt.xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2020-06-01"))
plt.legend(loc="upper left")

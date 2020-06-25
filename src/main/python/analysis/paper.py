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

#%% Activity participation

from pandas.tseries.offsets import BDay

isBusinessDay = BDay().onOffset

act = pd.read_csv("../../../../../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/BerlinSnzData_daily_until20200614.csv",
                  sep="\t", parse_dates=[0])

act_week = act[act.date.map(isBusinessDay)]

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))


ax = sns.scatterplot(x="date", y="home", label="home", data=act_week, ax=ax)
sns.scatterplot(x="date", y="notAtHome", label= "notAtHome", data=act_week, ax=ax)

ax.xaxis.set_major_formatter(dateFormater)

plt.ylabel("activity participation in %")
plt.legend(loc="best")

plt.xlim(datetime.fromisoformat("2020-03-01"), datetime.fromisoformat("2020-06-15"))


#%%


df = read_batch_run("data/section3-3-new.zip")
dfOld = read_batch_run("data/section3-3.zip")

#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

ci = datetime.fromisoformat("2020-03-07")

plt.axvline(ci, color="gray", linewidth=1, linestyle="--", alpha=0.8)
plt.text(ci, 1.2, ' Date of ci change', color="gray")

rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], color="purple", ax=ax, logy=True)
sns.lineplot(x="date", y="cases", estimator="mean", ci="sd", ax=ax,
             label=r"Uncalibrated $(\alpha=ci=1)$", data=dfOld[(dfOld.ci==1) & (dfOld.alpha==1)])

sns.lineplot(x="date", y="cases", estimator="mean", ci="sd", ax=ax,
             label=r"$\alpha=1.7, ci=1$", data=df[df.alpha==1.7])

sns.lineplot(x="date", y="cases", estimator="mean", ci="sd", ax=ax,
             label=r"$\alpha=1.0, ci$ calib.", data=df[df.alpha==1.0])

sns.lineplot(x="date", y="cases", estimator="mean", ci="sd", ax=ax,
             label=r"$\alpha=1.2, ci$ calib.", data=df[df.alpha==1.2])

sns.lineplot(x="date", y="cases", estimator="mean", ci="sd", ax=ax,
             label=r"$\alpha=1.4, ci$ calib.", data=df[(df.alpha==1.4)])

ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())

plt.ylim(bottom=1)
plt.xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2020-08-01"))
plt.legend(loc="upper left")

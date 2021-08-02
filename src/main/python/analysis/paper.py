import os
from datetime import datetime

import pandas as pd
import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt
from matplotlib.ticker import ScalarFormatter
from matplotlib.dates import AutoDateLocator, AutoDateFormatter, ConciseDateFormatter


from utils import read_batch_run, aggregate_batch_run, read_case_data, read_run, infection_rate
from plot import comparison_plots

#%%
# Code-snipped needed at seaborn/relational.py, ca. line 410
"""
elif ci == "q95":
    q05 = grouped.quantile(0.05)
    q95 = grouped.quantile(0.95)
    cis = pd.DataFrame(np.c_[q05, q95],
                       index=est.index,
                       columns=["low", "high"]).stack()
"""

#%%

sns.set_style("whitegrid")
sns.set_context("paper")
sns.set_palette("deep")


dateFormater = ConciseDateFormatter(AutoDateLocator())

palette = sns.color_palette()


#%%

rki, meldedatum, hospital = read_case_data("berlin-cases.csv", "berlin-cases-meldedatum.csv", "berlin-hospital.csv")


#%%

import zipfile

z = zipfile.ZipFile("../../../../../public-svn/matsim/scenarios/countries/de/episim/battery/2021-02-09/paperAggr/summaries/79.zip")

with z.open("79.outdoorFraction.tsv") as f:
    df = pd.read_csv(f, delimiter="\t", index_col=0, parse_dates=[1], dayfirst=True)

with z.open("79.rValues.txt.csv") as f:
    rf = pd.read_csv(f, delimiter="\t", index_col=0, parse_dates=[1], dayfirst=True)
    rf = rf.set_index("date", drop=False).resample("W").mean().reset_index()
    rf = pd.melt(rf, id_vars=["date"],  value_name="reinfections",
                 var_name="activity", value_vars=["home", "leisure", "schools", "day care", "university", "work&business", "pt", "other"])
    
with z.open("79.diseaseImport.tsv") as f:
    ff = pd.read_csv(f, delimiter="\t", index_col=0, parse_dates=[1], dayfirst=True)
    
with z.open("79.infectionsPerActivity.txt.tsv") as f:
    af = pd.read_csv(f, delimiter="\t", index_col=0, parse_dates=[1], dayfirst=True)
    
act_order = sorted(set(af.activity))
    
z.close()

#%%  Outdoor fraction

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 2.4))

sns.scatterplot(x="date", y="outdoorFraction", s=40, data=df, ax=ax)

ax.xaxis.set_major_formatter(dateFormater)

plt.xlim(datetime.fromisoformat("2020-03-01"), datetime.fromisoformat("2020-11-01"))

#%% Infections per activity


fig, axes = plt.subplots(2,1, sharex=True, dpi=250, figsize=(7.5, 5.0))

sns.lineplot(data=af, x="date", y="infections", hue="activity", hue_order=act_order, ax=axes[0])
sns.lineplot(data=af, x="date", y="infectionsShare", hue="activity", hue_order=act_order, ax=axes[1])


plt.xlim(datetime.fromisoformat("2020-03-01"), datetime.fromisoformat("2020-11-01"))

axes[0].set_yscale("symlog")

axes[0].xaxis.set_major_formatter(dateFormater)
axes[0].yaxis.set_major_formatter(ScalarFormatter())

axes[0].get_legend().remove()
axes[1].legend(loc='upper center', bbox_to_anchor=(0.5, -0.25), ncol=5)
axes[1].set_ylabel("infection share")



#%% Reinfections

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 2.4))

sns.lineplot(data=rf, x="date", y="reinfections", hue="activity", hue_order=act_order, ax=ax)

ax.xaxis.set_major_formatter(dateFormater)

# Put a legend below current axis
ax.legend(loc='upper center', bbox_to_anchor=(0.5, -0.25), ncol=5)

plt.xlim(datetime.fromisoformat("2020-03-01"), datetime.fromisoformat("2020-11-01"))


#%% Disease import

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 2.4))

sns.lineplot(data=ff, x="date", y="nInfected", ax=ax)

ax.xaxis.set_major_formatter(dateFormater)

plt.xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2020-11-01"))
    
plt.ylabel("Imported cases")


#%% Mask compliance



#%% Activity participation

from pandas.tseries.offsets import BDay

isBusinessDay = BDay().onOffset

act = pd.read_csv("C:/home/Development/matsim-org/matsim-episim/output/BerlinSnzData_daily_until20200705.csv",
                  sep="\t", parse_dates=[0])

act_week = act[act.date.map(isBusinessDay)]
act_wend = act[act.date.map(lambda *args: not isBusinessDay(*args))]

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))


ax = sns.scatterplot(x="date", y="home", label="home", s=40, data=act_week, ax=ax)
sns.scatterplot(x="date", y="notAtHomeExceptLeisureAndEdu", label="notAtHome", s=40, data=act_week, ax=ax)
#sns.scatterplot(x="date", y="notAtHomeExceptLeisureAndEdu", label="notAtHome (Weekend)", s=40, data=act_wend, ax=ax)

ax.xaxis.set_major_formatter(dateFormater)

plt.ylabel("activity participation in %")
plt.legend(loc="best")

plt.xlim(datetime.fromisoformat("2020-03-01"), datetime.fromisoformat("2020-11-01"))

#%% Base calibration

df_base = read_batch_run("data/paper.zip")

#%%

aggregate_batch_run("data/paper.zip")

#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

df = df_base[(df_base.unrestricted=="yes") & (df_base.diseaseImport=="yes") & (df_base.theta==1.36e-5)]

rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], color=palette[4], ax=ax)

sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax, 
             label=r"Calibrated $\theta$", data=df)

ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())

plt.ylim(bottom=1)
plt.xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2020-11-01"))
plt.legend(loc="upper left")
plt.yscale("log")

#######################################################################################################
#%%


df = read_batch_run("data/section3-3-data.zip")
#dfOld = read_batch_run("data/section3-3.zip")

baseCase = df[df.alpha==1.0]

#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

ci = datetime.fromisoformat("2020-03-07")

#plt.axvline(ci, color="gray", linewidth=1, linestyle="--", alpha=0.8)
#plt.text(ci, 1.2, ' Date of ci change', color="gray")

rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], color=palette[4], ax=ax, logy=True)

#sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax,
#             label=r"Unrestricted", data=df31)

# In the data alpha=0 is a special case
sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax,
            label=r"$\alpha=1.0$", data=df[(df.alpha==0)])

sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax,
            label=r"$\alpha=1.7$", data=df[df.alpha==1.7])

sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax,
            label=r"$\alpha=1.0$, ci adaptation 7-mar", data=baseCase)

ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())

plt.ylim(bottom=1)
plt.xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2020-06-01"))
plt.legend(loc="upper left")


#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

hospital.plot.scatter(x="Datum", y=["Stationäre Behandlung"], label=["Hospital treatment"], color=palette[4], logy=True, ax=ax)
hospital.plot.scatter(x="Datum", y=["Intensivmedizin"], label=["Intensive care"], color=palette[5], logy=True, ax=ax)

baseCase["inHospital"] =  baseCase.nSeriouslySick + baseCase.nCritical

sns.lineplot(x="date", y="inHospital", estimator="mean", ci="q95", ax=ax,            
             label=r"In Hospital", data=baseCase)

sns.lineplot(x="date", y="nCritical", estimator="mean", ci="q95", ax=ax,
             label=r"In ICU", data=baseCase)


ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())
plt.ylim(bottom=1, top=10000)
plt.ylabel("Hospitalized persons")
plt.xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2020-06-01"))
plt.legend(loc="upper left")

#%% Fig 8

df = pd.read_csv("data/0.rValues.txt.csv", index_col="date", parse_dates=True, sep="\t")
rf = df.rolling(7, center=True).mean()

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

sns.scatterplot(x="date", y="rValue", s=5, ax=ax, data=df, label="Daily R value")
sns.lineplot(x="date", y="rValue", ax=ax, data=rf, color=palette[1], label="7-day average R-value")

plt.axhline(1, color="gray", linewidth=1, linestyle="--", alpha=0.8)

plt.xlim(datetime.fromisoformat("2020-02-16"), datetime.fromisoformat("2020-10-31"))

ax.xaxis.set_major_formatter(dateFormater)


#%% Activities 

df = pd.read_csv("data/0.infectionsPerActivity.txt.tsv", index_col="date", parse_dates=True, sep="\t")

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

g = sns.lineplot(x="date", y="infections", hue="activity", data=df, ax=ax)

g.set(yscale="symlog")
ax.yaxis.set_major_formatter(ScalarFormatter())
ax.xaxis.set_major_formatter(dateFormater)
plt.xlim(datetime.fromisoformat("2020-02-16"), datetime.fromisoformat("2020-10-31"))

plt.ylim(bottom=0, top=1000)

plt.legend(loc="best")

#%% Calibration

from sklearn.metrics import mean_squared_log_error

df = read_batch_run("data/summaries-aggr.zip")

df = df[(df.diseaseImport=="1") & (df.avgTemperatures=="yes")]


#%%

for run in df.run.value_counts().keys():

    for act in df.activityLevel.value_counts().keys():
    
        for until in df.calibrationUntil.value_counts().keys():
                    
            tf = df[(df.calibrationUntil == until) & (df.activityLevel==act) & (df.run==run)]
            
            print(run, act, until)
            print()
    
            start = "2020-03-01"
        
            rf = tf[(tf.date >= start) & (tf.date <= until)]
            h = hospital[(hospital.Datum >= start) & (hospital.Datum <= until)]
            error_sick = mean_squared_log_error(h["Stationäre Behandlung"], rf.nSeriouslySick + rf.nCritical)
            
            print("Train error", error_sick)
    
            start = "2020-09-01"
            end = "2020-10-31"    
        
            rf = tf[(tf.date >= start) & (tf.date <= end)]
            h = hospital[(hospital.Datum >= start) & (hospital.Datum <= end)]
            error_sick = mean_squared_log_error(h["Stationäre Behandlung"], rf.nSeriouslySick + rf.nCritical)
            
            print("Pred error", error_sick)
    
            print("-------------")

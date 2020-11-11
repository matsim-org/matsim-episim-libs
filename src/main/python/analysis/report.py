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
#rki, meldedatum, hospital = read_case_data("/Users/sebastianmuller/git/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/berlin-cases.csv", "/Users/sebastianmuller/git/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/berlin-cases-meldedatum.csv", "/Users/sebastianmuller/git/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/Berlin/berlin-hospital.csv")

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


#%% Disease import

imp = read_batch_run("data/diseaseImport.zip", age_groups=[1, 5, 15, 35, 60, 80, 100])
m = 2147483647


#%%

for g in imp.ageBoundaries.value_counts().keys():
    df = imp[(imp.calibrationParam==0.000004) & (imp.tracingCapacity==m) & (imp.ageBoundaries==g)]
        
    fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))    
    ci = None
    
    #sns.lineplot(x="date", y="cases", label="cases", estimator="mean", ci=ci, ax=ax, data=df)
    
    for y in df.columns:
        if not y.startswith("sick") or "-" not in y:
            continue
    
        sns.lineplot(x="date", y=y, label=y, estimator="mean", ci=ci, ax=ax, data=df)
        
    ax.xaxis.set_major_formatter(dateFormater)
    ax.yaxis.set_major_formatter(ScalarFormatter())
    
    plt.ylim(bottom=0, top=10)
    plt.ylabel("cases")
    plt.legend(loc="best")
    plt.title("ageBoundaries=%s" % g)
    plt.xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2021-04-30"))
    
#%%

df = imp[(imp.calibrationParam==0.000004) & (imp.tracingCapacity==m)]

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))
hue = sns.color_palette(n_colors=10)

rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], color=palette[4], ax=ax, logy=True)

sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax,
             hue="ageBoundaries", palette=hue, 
             data=df)

plt.ylim(bottom=1)
ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())

#%% Curfew

curfew = read_batch_run("data/curfew5.zip")

#%%

df = curfew[(curfew.holidays=="yes") & (curfew.tracingCapacity==200) & (curfew.curfew !="remainingFraction0") & (curfew.curfew != "1-6")]

order = sorted(df.curfew.value_counts().keys())

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))
hue = sns.color_palette(n_colors=8)

rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], color=palette[4], ax=ax)

sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax,
             hue="curfew", hue_order=order, palette=hue,
             data=df)

plt.ylim(bottom=1)
plt.yscale("log")
plt.title("Daily new infections over all random seeds, tracingCapacity=200")

ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())

#%% sensitivity

sensitivity = read_batch_run("/Users/sebastianmuller/git/public-svn/matsim/scenarios/countries/de/episim/battery/2020-11-03/sensitivity/summaries.zip")

#%%

run = "noDiseaseImport"
run = "noDiseaseImportAdaptedTheta"
run = "noTracing"
run = "noAgeDepInfModel"
run = "noAgeDepInfModelAdaptedTheta"
run = "noSchoolAndDayCareRestrictions"
run = "noUniversitiyRestrictions"
run = "noOutOfHomeRestrictionsExceptEdu"
run = "base"

df = sensitivity[(sensitivity.run==run) & (sensitivity.thetaFactor==1.1)]

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))
hue = sns.color_palette(n_colors=1)

rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases (reference date)"], edgecolors=palette[3], facecolors='none',  ax=ax, s=3)
meldedatum.plot.scatter(x="date", y=["cases"], label=["RKI Cases (reporting date)"], edgecolors=palette[2], facecolors='none',  ax=ax, s=3)

sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax, palette=hue, label="showing symptoms (model)",
             data=df)
plt.legend()
plt.ylim(bottom=1)
plt.ylim(top=50000)

plt.xlim(datetime.fromisoformat("2020-02-16"), datetime.fromisoformat("2020-10-31"))

plt.yscale("log")
#plt.title("Daily new infections over all random seeds - " + run)

ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())

#%% sensitivity hospital cases

sensitivity = read_batch_run("/Users/sebastianmuller/git/public-svn/matsim/scenarios/countries/de/episim/battery/2020-11-03/sensitivity/summaries.zip")

#%%

run = "noDiseaseImport"
run = "noDiseaseImportAdaptedTheta"
run = "noTracing"
run = "noAgeDepInfModel"
run = "noAgeDepInfModelAdaptedTheta"
run = "noSchoolAndDayCareRestrictions"
run = "noUniversitiyRestrictions"
run = "noOutOfHomeRestrictionsExceptEdu"
run = "base"

df = sensitivity[(sensitivity.run==run) & (sensitivity.thetaFactor==1.0)]
df['inHospital'] = df['nCritical'] + df['nSeriouslySick']

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))
hue = sns.color_palette(n_colors=1)

hospital.plot.scatter(x="Datum", y=["Stationaere Behandlung"], label=["hospitalized (LAGeSo)"], edgecolors=palette[3], facecolors='none',  ax=ax, s=3)
hospital.plot.scatter(x="Datum", y=["Intensivmedizin"], label=["intensive care (LAGeSo)"], edgecolors=palette[2], facecolors='none',  ax=ax, s=3)
sns.lineplot(x="date", y="nCritical", estimator="mean", ci="q95", ax=ax, palette=hue, label="critical (model)",
             data=df)
sns.lineplot(x="date", y="inHospital", estimator="mean", ci="q95", ax=ax, palette=hue, label="seriouslySick + critical (model)",
             data=df)
plt.legend()
plt.ylim(bottom=1)
plt.ylim(top=50000)
plt.ylabel('cases')

plt.xlim(datetime.fromisoformat("2020-02-16"), datetime.fromisoformat("2020-10-31"))

plt.yscale("log")
#plt.title("Daily new infections over all random seeds - " + run)

ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())

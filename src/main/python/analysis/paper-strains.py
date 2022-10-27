import os
from datetime import datetime

import pandas as pd
import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt
from matplotlib.ticker import ScalarFormatter
from matplotlib.dates import AutoDateLocator, AutoDateFormatter, ConciseDateFormatter

from calibration.calibrate import msle

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

tmp = pd.read_csv("calibration/ShareTimesDunkelziffer.csv")

tmp = tmp.rename(columns={"Incidence(NRW)": "Inzidenz", "Dunkelziffer(NRW)": "DunkelzifferInzidenz", "Date": "Datum"})
tmp = tmp.drop(columns=["ShareNRW", "ShareTimesDunkelziffer"])

tmp.to_csv("calibration/InzidenzDunkelzifferNRW.csv", index=False)

#%%


df = pd.read_csv("cmp.csv", parse_dates=[0])

cases = pd.read_csv("calibration/InzidenzDunkelzifferNRW.csv", parse_dates=[0])
cases.set_index("Datum", drop=True, inplace=True)

#%%


fig, ax = plt.subplots(dpi=250, figsize=(7.5, 2.4))

sns.lineplot(data=df, x="date", y="cases", hue="tf", ci=None, ax=ax)
sns.scatterplot(data=cases.DunkelzifferInzidenz, color="red", linewidth=2, ax=ax, label="RKI")

plt.ylim(bottom=100)
plt.yscale("log")
plt.legend(loc="upper left", fontsize="small")


ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())

plt.xlim(datetime.fromisoformat("2021-01-01"), datetime.fromisoformat("2021-06-01"))

#%%

scenario = "weekly-incd+7"

df = pd.read_csv(scenario + ".csv", parse_dates=[0])
df = df.groupby(["trial", "date"]).agg(share=("share", "mean"), cases=("cases", "mean"))

df["alpha"] = df.share * df.cases

shares_c = pd.read_csv("../calibration/VOC_Cologne.csv", decimal=',', parse_dates=[0])
shares_c.rename(columns={shares_c.columns[-1]: "share"}, inplace=True)
shares_c = shares_c.groupby(pd.Grouper(key='Date', freq='W-SUN')).agg(Share=("share", "mean"))

#shares_c.to_csv("AlphaAnteileKöln.csv")

shares = pd.read_csv("../calibration/AlphaAnteileNRW.csv", parse_dates=[0])
shares.set_index("Date", drop=True, inplace=True)

shares = shares.merge(cases, left_index=True, right_index=True, how="left")

shares["incidence"] = shares.DunkelzifferInzidenz * shares.Share

#%%

params = pd.read_csv(scenario + "_params.csv", parse_dates=[0])

start = "2021-01-31"
end = "2021-05-30"
delta = pd.Timedelta(value=28, unit="days")

def error_shares(d):
    
    d = d.reset_index()
    a = d[ (d.date >= start ) & (d.date <= end) ]
    b = shares[ (shares.index >= start) & (shares.index <= end) ]
        
    return msle(a.share, b.Share)

def error_incd(d):
    
    d = d.reset_index()
    a = d[ (d.date >= start ) & (d.date <= end) ]
    b = shares[ (shares.index >= start) & (shares.index <= end) ]
        
    return msle(a.alpha, b.incidence)


def week_error_shares(d):
    
    trial_date = pd.to_datetime(d.index[0][0])
    int_start = trial_date - delta
    
    d = d.reset_index()
    b = shares[ (shares.index > int_start) & (shares.index <= trial_date) ]
    a = d[ d.date.isin(b.index) ]    
    
    return msle(a.share, b.Share)

def week_error_incd(d):
    trial_date = pd.to_datetime(d.index[0][0])
    int_start = trial_date - delta
    
    d = d.reset_index()
    b = shares[ (shares.index > int_start) & (shares.index <= trial_date) ]
    a = d[ d.date.isin(b.index) ]
    
    return msle(a.alpha, b.incidence)

params["pred_error_shares"] = df.groupby(["trial"]).apply(error_shares).to_numpy()
params["pred_error_incd"] = df.groupby(["trial"]).apply(error_incd).to_numpy()
params["4week_error_shares"] = df.groupby(["trial"]).apply(week_error_shares).to_numpy()
params["4week_error_incd"] = df.groupby(["trial"]).apply(week_error_incd).to_numpy()

print("Scenario", scenario)
print(params.astype(str).to_markdown(index=False))

#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 2.4))

sns.lineplot(data=df, x="date", y="share", hue="trial", ax=ax)
sns.scatterplot(data=shares.Share, color="red", linewidth=2, ax=ax, label="RKI")
#sns.scatterplot(data=shares_c.Share, color="blue", linewidth=2, ax=ax, label="Köln")
plt.legend(loc="upper left", fontsize="x-small")
plt.title("Alpha shares, " + scenario)

plt.yscale("log")
plt.ylim(bottom=0.01)

plt.xlim(datetime.fromisoformat("2021-01-01"), datetime.fromisoformat("2021-06-01"))

#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 2.4))

sns.lineplot(data=df, x="date", y="cases", hue="trial", ax=ax)
sns.scatterplot(data=cases.DunkelzifferInzidenz, color="red", linewidth=2, ax=ax, label="Ref")


plt.ylim(bottom=100)
plt.yscale("log")
plt.legend(loc="upper left", fontsize="small")
plt.title("Total incidence, " + scenario)

ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())

plt.xlim(datetime.fromisoformat("2021-01-01"), datetime.fromisoformat("2021-06-01"))

#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 2.4))

sns.lineplot(data=df, x="date", y="alpha", hue="trial", ax=ax)
sns.scatterplot(data=shares.incidence, color="red", linewidth=2, ax=ax, label="Ref")


plt.ylim(bottom=1)
plt.yscale("log")
plt.legend(loc="upper left", fontsize="small")
plt.title("Alpha incidence, " + scenario)

ax.xaxis.set_major_formatter(dateFormater)
ax.yaxis.set_major_formatter(ScalarFormatter())

plt.xlim(datetime.fromisoformat("2021-01-01"), datetime.fromisoformat("2021-06-01"))

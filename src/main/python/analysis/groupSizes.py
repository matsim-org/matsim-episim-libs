import os
import re
import pandas as pd
import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt

from matplotlib.ticker import ScalarFormatter
from scipy import stats
from xopen import xopen
from matsim import event_reader

from utils import read_batch_run, read_case_data, read_run, infection_rate
from plot import comparison_plots

#%%

rki, hospital = read_case_data("berlin-cases.csv", "berlin-hospital.csv")

#%%

container = pd.read_csv(xopen("data/groupSizes/containerUsage.txt.gz"), index_col=0, sep="\t").dropna()

pattern = re.compile(r"([a-zA-Z]+)=>([0-9]+)")

extra = {}

for row in container.itertuples():
    m = pattern.findall(row.types)
    if m:
        extra[row.Index] = {act: int(n) for (act, n) in m}
        
container = container.join(pd.DataFrame.from_dict(extra, orient="index")).sort_index()

#%%

def read_events(f, act=None, lookup=True):
    data = []

    for ev in event_reader(f, types=["episimContact"]):
        
        # Lookup maxgroup size of container
        if lookup and ev["container"] in container.index:
            ev["maxGroupSize"] = container.loc[ev["container"]].maxGroupSize
        
        if not act or any(x in ev["actType"] for x in act):
            data.append(ev)
    
    return pd.DataFrame(data).astype(dtype={"duration": "float", "groupSize": "int",
                                     "type": "str", "actType": "str", "container": "str"})

events = read_events("data/groupSizes/day_wt.xml.gz")

#%%

ev_work = events[events.actType.str.contains("work")].dropna()
ev_leis = events[events.actType.str.contains("leisure")].dropna()
ev_visit = events[events.actType.str.contains("visit")].dropna()
ev_errands = events[events.actType.str.contains("errands")].dropna()

#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))
x = np.linspace(0, 1)

ev_leis.maxGroupSize.quantile(x).plot(logy=True, label="leisure", ax=ax)
ev_work.maxGroupSize.quantile(x).plot(logy=True, label="work", ax=ax)
plt.legend()
plt.ylabel("Max group size")
plt.xlabel("Quantile")
plt.title("Amount of contacts")


#%%

def inv_quantile(df, x):        
    inv = np.vectorize(lambda t: stats.percentileofscore(df, t))
    return inv(x) / 100

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

x = np.arange(0, 1200)

plt.plot(x, inv_quantile(ev_work.maxGroupSize, x), label="work")
plt.plot(x, inv_quantile(ev_leis.maxGroupSize, x), label="leisure")
plt.plot(x, inv_quantile(ev_visit.maxGroupSize, x), label="visit")
plt.plot(x, inv_quantile(ev_errands.maxGroupSize, x), label="errands")

plt.legend()


#%%
   
events_05 = read_events("data/groupSizes/day_wt_05.xml.gz", lookup=False)
ev_leis_05 = events_05[events_05.actType.str.contains("leisure")]
ev_work_05 = events_05[events_05.actType.str.contains("work")]

#%%

p = np.array([0.25, 0.5, 0.75])

# Effect of remaining fraction is roughly quadratic

print(ev_work.maxGroupSize.quantile(p))
print(ev_leis.maxGroupSize.quantile(p))
print(ev_visit.maxGroupSize.quantile(p))
print(ev_errands.maxGroupSize.quantile(p))


#%%

gs = read_batch_run("data/groupSizes5.zip")


#%%

for cm in set(gs.contactModel):

    df = gs[gs.contactModel==cm]
    
    fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))
    hue = sns.color_palette(n_colors=3)
    
    #rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], color=palette[4], ax=ax, logy=True)
    
    sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax,
                 style="containment", hue="remaining", palette=hue, 
                 data=df)
    
    
    ax.xaxis.set_major_formatter(dateFormater)
    ax.yaxis.set_major_formatter(ScalarFormatter())
    
    plt.xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2020-06-01"))
    plt.title("contactModel = %s" % cm)
    
    plt.plot()

#%%

evs = read_batch_run("data/eventSizes3.zip")

#%%


for cm in set(evs.contactModel):

    df = evs[evs.contactModel==cm]
    
    fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))
    hue = sns.color_palette(n_colors=4)
    
    #rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], color=palette[4], ax=ax, logy=True)

    sns.lineplot(x="date", y="cases", estimator="mean", ci="q95", ax=ax,
                 style="reduction", hue="divider", palette=hue, 
                 style_order=["none", "closing_0.5", "uniform_0.5"],
                 data=df)
    
    plt.yscale("log")
    
    ax.xaxis.set_major_formatter(dateFormater)
    ax.yaxis.set_major_formatter(ScalarFormatter())
    
    plt.xlim(datetime.fromisoformat("2020-02-01"), datetime.fromisoformat("2020-06-01"))
    plt.title("contactModel = %s" % cm)

    ax.set_ylim(bottom=1)
    
    plt.plot()

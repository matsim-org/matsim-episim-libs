import os
import re
import pandas as pd
import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt

from matplotlib.ticker import ScalarFormatter
from scipy import stats
from matsim import event_reader

from utils import read_batch_run, read_case_data, read_run, infection_rate
from plot import comparison_plots

#%%

rki, hospital = read_case_data("berlin-cases.csv", "berlin-hospital.csv")

#%%

f = pd.read_csv("../../../../facilities.txt", index_col=0, sep="\t").dropna()
f_leisure = f[f.types.str.contains("leisure")]

#%%

scaled = []

pattern = re.compile(r"leisure=>([0-9]+)")

for row in f_leisure.itertuples():
    n = int(pattern.findall(row.types)[0])
    # small facilities don't have interactions
    if (row.size > 4):
        scaled.extend([row.size] * n)


l_bound = pd.DataFrame(scaled, columns=["size"])

p = np.array([0.1, 0.25, 0.5, 0.75, 0.9])

# Effect of remaining fraction is quadratic

print(l_bound['size'].quantile(p ** 1.6))

#%%

def read_events(f, act=None):
    data = []

    for ev in event_reader(f, types=["episimContact"]):
        if not act or any(x in ev["actType"] for x in act):
            data.append(ev)
    
    return pd.DataFrame(data).astype(dtype={"duration": "float", "groupSize": "int",
                                     "type": "str", "actType": "str", "container": "str"})

ev_un = read_events("data/day_unrestricted.xml.gz")

#%%

scaled = []

for row in ev_un.itertuples():
    try:
        container = f.loc[row.container]
        scaled.append(container["size"])
    except:
        pass

h_bound = pd.DataFrame(scaled, columns=["size"])

#%%

p = np.array([0.1, 0.25, 0.5, 0.75, 0.9])

# Effect of remaining fraction is roughly quadratic

print(h_bound['size'].quantile(p ** 1.6))    

#%%

ev_g154 = read_leisure_events("data/day_leisureG154.xml.gz")


#%%

df = read_batch_run("data/summaries-groupSizes.zip")
df2 = read_batch_run("data/summaries-superSpreading.zip")

#%%

fig, ax = plt.subplots(dpi=250)

palette = sns.color_palette(n_colors=3)

rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], ax=ax, logy=True)

#sns.lineplot(x="date", y="cases", hue="remaining", style="bySize", palette=palette, ci=None, data=df, ax=ax)
sns.lineplot(x="date", y="cases", hue="groupSize", style="superSpreading", palette=palette, ci=None, data=df2, ax=ax)


plt.ylim(bottom=1)
plt.legend(loc="upper left")

#%%



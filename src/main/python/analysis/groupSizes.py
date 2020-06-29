import os
import re
import pandas as pd
import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt

from matplotlib.ticker import ScalarFormatter
from scipy import stats

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
    scaled.extend([row.size] * n)


groups = pd.DataFrame(scaled, columns=["size"])

print(groups['size'].quantile([0.1, 0.25, 0.5, 0.75, 0.9]))

#%%

print(f.loc["110000001960500003"])


#%%

events_y = pd.read_csv("data/output/remaining_0.5-bySize_yes/groupSizes5.infectionEvents.txt", sep="\t")
events_y = events_y[events_y.infectionType.str.contains("leisure")]

events_n = pd.read_csv("data/output/remaining_0.5-bySize_no/groupSizes6.infectionEvents.txt", sep="\t")
events_n = events_n[events_n.infectionType.str.contains("leisure")]

#%%

df = read_batch_run("data/groupSizes.zip")

#%%

fig, ax = plt.subplots(dpi=250)

palette = sns.color_palette(n_colors=5)

rki.plot.scatter(x="date", y=["cases"], label=["RKI Cases"], ax=ax, logy=True)
sns.lineplot(x="date", y="cases", hue="remaining", style="bySize", palette=palette, estimator=None, ci=None, data=df, ax=ax)

plt.ylim(bottom=1)
plt.legend(loc="upper left")
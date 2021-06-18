# -*- coding: utf-8 -*-

import math
import itertools
from os import stat, path, listdir, makedirs
from collections import defaultdict
from glob import glob

from datetime import timedelta

import numpy as np
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import networkx as nx

from matplotlib.ticker import ScalarFormatter, FuncFormatter
from scipy.stats import nbinom
from matsim import event_reader

from utils import aggregate_batch_run, read_run, read_batch_run
from plot import comparison_plots

#%%

sns.set_style("whitegrid")
sns.set_context("paper")
sns.set_palette("deep")

#%% Read in all infections

output = "C:/Users/chris/Development/matsim-org/matsim-episim/filtered-new"
biggest = "C:/Users/chris/Development/matsim-org/matsim-episim/biggest-new"

fracs = defaultdict(lambda : [])

for d in listdir(output):

    seed, _, frac = d.rpartition("-")

    seed = int(seed.split("_")[1])
    frac = frac.split("_")[1]

    g_path = glob(path.join(output, d, "*.infectionEvents.txt"))[0]

    df = pd.read_csv(g_path, sep="\t")
    df["clusterSize"] = df.shape[0]

    fracs[frac].append((seed, df, g_path))
#%% Copy biggest clusters

import shutil

for frac, v in fracs.items():

    top = sorted(v, key=lambda d: d[1].size, reverse=True)

    print(frac)

    for seed, df, g_path in top[:3]:

        print("\t", df.size, g_path)

        dir = path.join(biggest, path.basename(path.dirname(g_path)))

        if not path.exists(dir):
            makedirs(dir)

        shutil.copy(g_path, dir)

#%% Create distribution of infections

dist = {}

for frac, v in fracs.items():

    n = []

    for seed, df, g_path in v:

        # seed person did not infect anyone
        if len(df) == 0:
            n.append(0)

        else:
            # persons not infecting anyone
            no_inf = set(df.infected).difference(set(df.infector))
            res = np.concatenate((df['infector'].value_counts().array, np.zeros(len(no_inf))))
            n.extend(res)

    n = np.sort(n)

    dist[frac] = n

    np.save(path.join(biggest, frac + ".npy"), n, allow_pickle=False)

#%%

df_1 = pd.DataFrame(dist["0.52"], columns=["n"])
df_1["run"] = "januar"

#%%

df_2 = pd.DataFrame(dist["0.9"], columns=["n"])
df_2["run"] = "januar-susp"

#%%

df_3 = pd.DataFrame(dist["0.09"], columns=["n"])
df_3["run"] = "unrestricted"


#%%

df = pd.concat([df_1, df_2, df_3])

#%% Plot distribuition

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

g = sns.histplot(df, ax=ax, x="n", hue="run", discrete=True, multiple="dodge", stat="density", common_norm=False)

#plt.xlim(right=16)
plt.yscale("log")

g.legend_.set_title("Scenario")

#ax.yaxis.set_major_formatter(ScalarFormatter())



#%% Color palette for infection type

def act_type(infection_type):

    if infection_type == "pt":
        return infection_type

    # some can be nan
    if type(infection_type) == float:
        return "other"

    s = infection_type.split("_")

    # extract first part
    context = "_".join(s[0:len(s)//2])

    if context in ("home", "leisure", "pt"):
        return context
    elif context == "educ_higher":
        return "university"
    elif context == "educ_kiga":
        return "day care"
    elif context in ("work", "business"):
        return "work&business"
    elif context in ("educ_primary", "educ_secondary", "educ_tertiary", "educ_other"):
        return "schools"

    return "other"



v_act_type = np.vectorize(act_type, otypes=["object"])


act = ["home", "leisure", "schools", "day care", "university",
       "work&business", "pt", "other"]


palette = [
    '#1f77b4',  # muted blue
    '#ff7f0e',  # safety orange
    '#2ca02c',  # cooked asparagus green
    '#d62728',  # brick red
    '#9467bd',  # muted purple
    '#8c564b',  # chestnut brown
    '#e377c2',  # raspberry yogurt pink
    '#7f7f7f',  # middle gray
#    '#17becf',   # blue-teal
#    '#bcbd22',  # curry yellow-green
]


from colorsys import rgb_to_hls

for i, color in enumerate(palette):

    pass
    #lightness = min(1, rgb_to_hls(color)[1] * scale)

    #palette[i] = sns.set_hls_values(color = color, h = None, l = 0.6, s = None)


#%%

df = pd.concat(df for seed, df, g_path in fracs["0.52"])

df.infectionType = v_act_type(df.infectionType)


#%%

res = pd.DataFrame(columns=act)

for i in range(1, 1000):

    tf  = df[df.clusterSize <= i]

    grouped = tf.groupby("infectionType").size()

    grouped = grouped / grouped.sum()
    res = res.append(grouped, ignore_index=True)

res.index += 1

#%%


fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

#plt.stackplot(res.index + 1, res.to_numpy().T, labels=res.columns, colors=palette)

res.plot.area(ax=ax, color=palette)

plt.legend(bbox_to_anchor=(1.01, 0.98))
plt.xlabel("Cluster size")
plt.ylabel("Share of infection context")
plt.xscale("log")

plt.xlim(left=1, right=1000)

ax.xaxis.set_major_formatter(ScalarFormatter())

sns.despine()

#%%

output = "C:/Users/chris/Development/matsim-org/matsim-episim/biggest"

G = {
     "0.07": nx.DiGraph(),
     "0.08": nx.DiGraph(),
     "0.09": nx.DiGraph(),
     "0.1": nx.DiGraph(),
     "0.11": nx.DiGraph()
}

data = []

for d in listdir(output):
    
    seed, _, frac = d.rpartition("-")
    
    seed = int(seed.split("_")[1])
    frac = frac.split("_")[1]
    
    #ev = glob(path.join(output, d, "*.infectionEvents.txt"))[0]
    
    i = 0
    #with open(ev) as f:
    #    for x in f.readlines():
    #        i+=1
   
    data.append({
        "seed": seed,
        "frac": frac,
        "size": i
    })
    
    g_path = glob(path.join(output, d, "*.infections.graphml"))[0]
    
    with open(g_path) as f:
        g = nx.readwrite.graphml.read_graphml(f)        
 
    if g.nodes():
        orig = G[frac]
        
        if not set(orig.nodes()).intersection(set(g.nodes())):
            G[frac] = nx.compose(orig, g)
        
   
df = pd.DataFrame(data)

#%%

for k, g in G.items(): 
    nx.readwrite.graphml.write_graphml(g, "data/%s-biggest.graphml" % k)

#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

marker = itertools.cycle(('+', '.', 'o', '*')) 

for frac in G.keys():
    
    bins = np.linspace(1, 2.6, num=11) ** 10

    hist, edges = np.histogram(df[df.frac==frac]['size'], bins=bins, density=True)

    plt.plot(bins[1:], hist, linewidth=1, marker = next(marker), label="fraction=%s" % frac)
    
plt.legend()
plt.yscale("log")
plt.xscale("log")

ax.xaxis.set_major_formatter(ScalarFormatter())
ax.yaxis.set_major_formatter(FuncFormatter(lambda x,pos: "%.5f" % x))

#%%

from networkx.drawing.nx_pydot import graphviz_layout

G = nx.read_graphml("../../../../biggest-new/seed_5443790475904766986-fraction_0.09/percolation51820.post.infections.graphml")

pos = graphviz_layout(G, prog="dot")


#%%

import matplotlib.patches as mpatches

node_color = []
for k, v in G.nodes.data():

    ift = act_type(v["source"])

    idx = act.index(ift)

    color = palette[idx]
    node_color.append(color)

handles = []

for i, c in enumerate(palette):
    patch = mpatches.Patch(color=c, label=act[i])
    handles.append(patch)


#%%

fig = plt.figure(figsize=(120, 80), dpi=50)

visual_style = {
    "node_color": node_color,
    "node_size": 500,
    "width": 1.5,
    "with_labels": False,
}

nx.draw(G, pos=pos, **visual_style)

#fig.axes[0].legend(handles=handles)

#%%


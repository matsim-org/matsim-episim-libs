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

#%%

output = "C:/Users/chris/Development/matsim-org/matsim-episim/perc-jan"
biggest = "C:/Users/chris/Development/matsim-org/matsim-episim/biggest-jan"

fracs = defaultdict(lambda : [])

for d in listdir(output):

    seed, _, frac = d.rpartition("-")
    
    seed = int(seed.split("_")[1])
    frac = frac.split("_")[1]
    
    g_path = glob(path.join(output, d, "*.infectionEvents.txt"))[0]
    
    df = pd.read_csv(g_path, sep="\t")
    df["clusterSize"] = df.shape[0]
    
    fracs[frac].append((seed, df, g_path))    
#%%

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
    
    
#%%
    
sizes = df.groupby("infectionType").size().sort_values(ascending=False)
act = list(sizes[:11].index)
palette = sns.color_palette("husl", 11)
    
#%%

df = pd.concat(df for seed, df, g_path in fracs["0.52"])
df.loc[~df.infectionType.isin(act), "infectionType"] = "other"


#%%

res = pd.DataFrame(columns=act)

for i in range(1, 1000):
    
    tf  = df[df.clusterSize <= i]
     
    grouped = tf.groupby("infectionType").size()
    
    grouped = grouped / grouped.sum()
    res = res.append(grouped, ignore_index=True)

#%%    

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

#plt.stackplot(res.index + 1, res.to_numpy().T, labels=res.columns, colors=palette)

res.plot.area(ax=ax, colormap="tab20")

plt.legend(bbox_to_anchor=(1.0, 1.0))
plt.xlabel("Group size")


plt.xscale("log")

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

G = nx.read_graphml("../../../../biggest-jan/seed_4903684801928898534-fraction_0.52/percolation12773.post.infections.graphml")

pos = graphviz_layout(G, prog="dot")

#%%

import matplotlib.patches as mpatches

palette = sns.color_palette("deep", 8)

df = pd.DataFrame.from_dict({k: v["source"] for k,v in G.nodes.data()}, orient="index", columns=["source"])
sizes = df.groupby("source").size().sort_values(ascending=False)

node_color = []
for k, v in G.nodes.data():
    
    idx = sizes.index.get_loc(v["source"])
    
    if len(palette) > idx:
        color = palette[idx]
    else:
        color = "#696969"
    
    node_color.append(color)

handles = []

for i, c in enumerate(palette):
    patch = mpatches.Patch(color=c, label=sizes.index[i])    
    handles.append(patch)
    

#%%

fig = plt.figure(figsize=(50, 80), dpi=50)

visual_style = {
    "node_color": node_color,
    "node_size": 500,
    "width": 1.5,
    "with_labels": False,
}

nx.draw(G, pos=pos, **visual_style)

#fig.axes[0].legend(handles=handles)

#%%


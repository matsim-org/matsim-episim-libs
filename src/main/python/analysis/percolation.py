# -*- coding: utf-8 -*-

import math
import itertools
from os import path, listdir
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

output = "C:/Users/chris/Development/matsim-org/matsim-episim/graphs"

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
    nx.readwrite.graphml.write_graphml(g, "data/%s.graphml" % k)      

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

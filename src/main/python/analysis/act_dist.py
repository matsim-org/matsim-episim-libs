#!/usr/bin/env python
# -*- coding: utf-8 -*-

from datetime import datetime

import pandas as pd
import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt
from matplotlib.ticker import ScalarFormatter
from matplotlib.dates import AutoDateLocator, AutoDateFormatter, ConciseDateFormatter

from matsim import event_reader, plan_reader

#%%

sns.set_style("whitegrid")
sns.set_context("paper")

dateFormater = ConciseDateFormatter(AutoDateLocator())

palette = sns.color_palette()

#%%

p = "C:/Users/chris/Development/matsim-org/shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/"

persons = set()

for person, attr, plan in plan_reader(p + "be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz", True):
    persons.add(person.attrib["id"])

#%%

from collections import Counter

def count(persons, f):

    counts = Counter({k:0 for k in persons})

    for ev in event_reader(p + f, types=["actstart"]):
        
        if ev["actType"] == "leisure":            
            counts[ev["person"]] += 1
            
    return counts
   
#%%

wt = count(persons, "be_2020-week_snz_episim_events_wt_25pt_split.xml.gz")
sa = count(persons, "be_2020-week_snz_episim_events_sa_25pt_split.xml.gz")
so = count(persons, "be_2020-week_snz_episim_events_so_25pt_split.xml.gz")

#%%

wt_df = pd.Series(wt).to_frame("wt")
sa_df = pd.Series(sa).to_frame("sa")
so_df = pd.Series(so).to_frame("so")

#%%

woche = wt + wt + wt + wt + wt + sa + so

#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))

df_woche = pd.Series(woche).to_frame("woche")

sns.histplot(df_woche, binwidth=1, discrete=True, ax=ax)


#%%

fig, ax = plt.subplots(dpi=250, figsize=(7.5, 3.8))


df = pd.concat([wt_df, sa_df, so_df], axis=1, join="inner").fillna(0).melt(var_name="Tag", value_name="Aktivitäten")

sns.histplot(df, x="Aktivitäten", hue="Tag", binwidth=1, multiple="dodge", ax=ax)


#%%
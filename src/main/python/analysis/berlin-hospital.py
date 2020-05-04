#!/usr/bin/env python
# coding: utf-8

# In[]:

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from cycler import cycler
import scipy as sp

# In[]:

base = 'src/main/python/analysis/'

# In[]:

rr = pd.read_csv(base+'berlin-hospital.csv', sep=',' ).fillna(value=0.)
rr.index = pd.date_range(start='2020-03-01',periods=59)

# rr2 = rr['neu'].rolling(9).mean()
rr2 = rr['Stationäre Behandlung'].diff().rolling(5).mean()

plt.close('all')
plt.rcParams['figure.figsize']=[12,7]
# default_cycler = (cycler(color=['r', 'g', 'b', 'y','pink','purple']) +
#                   cycler(linestyle=['', '-', '-', '','-','']) +
#                   cycler(marker=['o','','','',",",'']))
# plt.rc('lines', linewidth=1)
# plt.rc('axes', prop_cycle=default_cycler)
rr2.plot(kind='bar',logy=False,grid=True,xlim=[1,60])
# plt.axvline(17, color='y', linestyle='-', lw=1)
# plt.axvline(23, color='y', linestyle='-', lw=1)
plt.show()


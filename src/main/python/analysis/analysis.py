#!/usr/bin/env python
# coding: utf-8

# In[]:

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from cycler import cycler
import scipy as sp

# In[]:

# base = 'output-theta1.1E-6-ciHome10.0-work0.45-leis0.0-other0.1/'
# base = 'output-theta1.7E-6-ciHome3.0-work0.45-leis0.0-other0.1/'
base = 'output-theta2.4E-6-ciHome1.0-work0.45-leis0.1-other0.1/'
# base = 'output-theta2.4E-6-ciHome1.0-work0.45-leis0.1-other0.4/'
# base = 'output-theta2.0E-6-ciHome2.0-work0.45-leis0.1-other0.2/'

# In[]:

rr = pd.read_csv(base+'infections.txt', sep='\t' )

# In[]:

rr2 = rr.loc[ rr['district']=='Berlin' , ['day','nInfectedCumulative' ]]
# rr2.index = pd.date_range(start="2020-02-21",periods=rr2.size)
rr2.index = rr2['day']
# 23 = mar/14 -->
rr3 = rr2['nInfectedCumulative']. diff().rolling(5).mean()

plt.close('all')
plt.rcParams['figure.figsize']=[12,7]
# default_cycler = (cycler(color=['r', 'g', 'b', 'y','pink','purple']) +
#                   cycler(linestyle=['', '-', '-', '','-','']) +
#                   cycler(marker=['o','','','',",",'']))
# plt.rc('lines', linewidth=1)
# plt.rc('axes', prop_cycle=default_cycler)
rr3.plot(kind='bar',logy=False,grid=True,xlim=[1,60])
# plt.axvline(17, color='y', linestyle='-', lw=1)
plt.axvline(23, color='y', linestyle='-', lw=1)
plt.show()

# In[]:

base = 'src/main/python/analysis/'
hh = pd.read_csv(base+'berlin-hospital.csv', sep=',' ).fillna(value=0.)

# In[]:

rr2 = rr.loc[ rr['district']=='Berlin' , ['nSeriouslySick' ]]
# rr2.index = pd.date_range(start="2020-02-21",periods=rr2.size)
# rr2.index = rr2['day']
# 23 = mar/14 -->
rr3 = rr2

plt.close('all')
plt.rcParams['figure.figsize']=[12,7]
# default_cycler = (cycler(color=['r', 'g', 'b', 'y','pink','purple']) +
#                   cycler(linestyle=['', '-', '-', '','-','']) +
#                   cycler(marker=['o','','','',",",'']))
# plt.rc('lines', linewidth=1)
# plt.rc('axes', prop_cycle=default_cycler)
rr3.plot(kind='bar',logy=False,grid=True,xlim=[1,60])
# plt.axvline(17, color='y', linestyle='-', lw=1)
plt.axvline(23, color='y', linestyle='-', lw=1)
plt.show()


# # ln[]:
#
# ss = pd.read_csv(base + 'timeUse.txt', sep='\t' )
#
# ss.drop(columns=['day','Unnamed: 1','home'],inplace=True)
#
# plt.close('all')
# plt.rcParams['figure.figsize']=[12,7]
# # default_cycler = (cycler(color=['r', 'g', 'b', 'y','pink','purple']) +
# #                   cycler(linestyle=['', '-', '-', '','-','']) +
# #                   cycler(marker=['o','','','',",",'']))
# # plt.rc('lines', linewidth=1)
# # plt.rc('axes', prop_cycle=default_cycler)
# ss.plot(kind='line',logy=False,grid=True,xlim=[1,60])
# # plt.axvline(17, color='y', linestyle='-', lw=1)
# plt.axvline(23, color='y', linestyle='-', lw=1)
# plt.show()

# # ln[]:
#
# tt = pd.read_csv(base +'restrictions.txt', sep='\t' )
#
# tt.drop(columns=['day','pt','tr','Unnamed: 1','home'],inplace=True)
#
# plt.close('all')
# plt.rcParams['figure.figsize']=[12,7]
# # default_cycler = (cycler(color=['r', 'g', 'b', 'y','pink','purple']) +
# #                   cycler(linestyle=['', '-', '-', '','-','']) +
# #                   cycler(marker=['o','','','',",",'']))
# # plt.rc('lines', linewidth=1)
# # plt.rc('axes', prop_cycle=default_cycler)
# tt.plot(kind='line',logy=False,grid=True,xlim=[1,60])
# # plt.axvline(17, color='y', linestyle='-', lw=1)
# plt.axvline(23, color='y', linestyle='-', lw=1)
# plt.show()


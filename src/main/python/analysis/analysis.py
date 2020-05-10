#!/usr/bin/env python
# coding: utf-8

# In[]:

import pandas as pd
import matplotlib.pyplot as pyplot
import numpy as np
from cycler import cycler
import scipy as sp
import matsim as ms

hh = pd.read_csv('../covid-sim/src/assets/' + 'berlin-hospital.csv', sep=',').fillna(value=0.)
hh.index = pd.date_range(start='2020-03-01',periods=hh.index.size)

# In[]:

# base = 'output-theta1.1E-6-ciHome10.0-work0.45-leis0.0-other0.1/'
# base = 'output-theta1.7E-6-ciHome3.0-work0.45-leis0.0-other0.1/'
# base = 'output-theta2.4E-6-ciHome1.0-work0.45-leis0.1-other0.1/'
# base = 'output-theta2.4E-6-ciHome1.0-work0.45-leis0.1-other0.4/'
# base = 'output-theta2.0E-6-ciHome2.0-work0.45-leis0.1-other0.2/'
# base = 'piecewise-theta3.9E-6-offset-8-ciHome1.0-work0.45-leis0.1-other0.2/'
# base = 'piecewise-theta2.8E-6-offset0-ciHome1.0-work1.0-leis1.0-other1.0/'

# base = 'piecewise-theta2.8E-6-offset-3-ciHome1.0-work0.45-leis0.1-other0.2/'
# base = 'piecewise__theta2.8E-6__offset-3__leis_1.0_1.0_0.0_0.0__other0.2/'
# simStartDate='2020-02-18'

base = 'piecewise__theta2.8E-6__offset-4__leis_1.0_0.7_0.7_0.1__other0.2/'
simStartDate='2020-02-17'

# base = 'piecewise__theta2.8E-6__offset-5__leis_1.0_1.0_0.0_0.0__other0.2/'
# simStartDate='2020-02-16'

# base = 'piecewise__theta2.8E-6__offset-6__leis1.0_0.7_0.1__other0.2/'
# base = 'piecewise__theta2.8E-6__offset-6__leis_1.0_1.0_0.0_0.0__other0.2/'
# simStartDate='2020-02-15'

rr = pd.read_csv(base+'infections.txt', sep='\t' )

infectedCumulative = rr.loc[rr['district'] == 'Berlin' , ['nSeriouslySick']]
infectedCumulative.index = pd.date_range(start=simStartDate, periods=infectedCumulative.size)

rr3 = pd.concat([hh['Stationäre Behandlung'], infectedCumulative], axis=1).fillna(value=0.)

pyplot.close('all')
pyplot.rcParams['figure.figsize']=[12, 7]
default_cycler = (cycler(color=['r', 'g', 'b', 'y','pink','purple']) +
                  cycler(linestyle=['', '-', '-', '','-','']) +
                  cycler(marker=['o','','','',",",'']))
pyplot.rc('axes', prop_cycle=default_cycler)
rr3.plot(logy=True,grid=True)
pyplot.show()

# In[]:

infectedCumulative = rr.loc[rr['district'] == 'Berlin', ['nInfectedCumulative']]
infectedCumulative.index = pd.date_range(start=simStartDate, periods=infectedCumulative.size)

nContagious = rr.loc[rr['district'] == 'Berlin', ['nContagious']]
nContagious.index = pd.date_range(start=simStartDate, periods=nContagious.size)

nShowingSymptoms = rr.loc[rr['district'] == 'Berlin', ['nShowingSymptoms']]
nShowingSymptoms.index = pd.date_range(start=simStartDate, periods=nShowingSymptoms.size)

rr2b = pd.concat([hh['Gemeldete Fälle'], infectedCumulative], axis=1).fillna(value=0.)

rr3 = pd.concat([rr2b.diff(),nContagious.diff(),nShowingSymptoms.diff()], axis=1)

pyplot.close('all')
pyplot.rcParams['figure.figsize']=[12, 7]
default_cycler = (cycler(color=['r', 'g', 'b', 'y','pink','purple']) +
                  cycler(linestyle=['', '-', '-', '-','-','']) +
                  cycler(marker=['o','','','',",",'']))
pyplot.rc('axes', prop_cycle=default_cycler)
axes = rr3.plot(logy=True,grid=True)
# axes.set_ylim(0,1000)
# plt.axvline(23, color='y', linestyle='-', lw=1)
pyplot.show()

# In[]:

# infectedCumulative = rr.loc[rr['district'] == 'Berlin' , ['day', 'nInfectedCumulative']]
# infectedCumulative.index = pd.date_range(start=simStartDate, periods=infectedCumulative.size / 2)
rr2b = infectedCumulative['nInfectedCumulative']

fit = pd.Series(2*np.exp(np.arange(0,60,1)*np.log(2.)/2.8))
fit.index = pd.date_range(start=simStartDate,periods=fit.size)

rr3 = pd.concat([rr2b,fit],axis=1)

pyplot.close('all')
pyplot.rcParams['figure.figsize']=[12, 7]
rr3.plot(logy=True,grid=True)
pyplot.axvline(23, color='y', linestyle='-', lw=1)
pyplot.show()

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


# In[]:
infectionEvents = pd.read_csv( base + "infectionEvents.txt", sep="\t" )

# In[]:
plans = ms.plan_reader( "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_entirePopulation_explicitEmptyPlans_withDistricts.xml.gz", selectedPlansOnly = True )
# plans = ms.plan_reader( "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/testPlans_withDistricts.xml.gz", selectedPlansOnly = True )
# plans = ms.plan_reader( "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/test2.xml", selectedPlansOnly = True )

personAge = dict()
cnt = 0
for person, plan in plans:
    cnt += 1
    personId = person.attrib['id']
    matsimAttributes = person.find('attributes')
    for matsimAttribute in matsimAttributes:
        if matsimAttribute.attrib['name'] == 'age':
            personAge[personId] = int(matsimAttribute.text)
    # if cnt >= 30:
    #     break

print(personAge)

# In[]:

personsByAge = dict.fromkeys(range(100),0)
for abc in personAge:
    personsByAge[personAge.get(abc)] += 0.02

infectionsByAge = dict.fromkeys(range(100),0)
for abc in infectionEvents.loc[:,"infected"]:
    infectionsByAge[personAge.get(abc)] += 1


print(infectionsByAge)

pyplot.bar(*zip(*personsByAge.items()))
pyplot.bar(*zip(*infectionsByAge.items()))
pyplot.show()

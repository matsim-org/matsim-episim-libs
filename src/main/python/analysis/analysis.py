#!/usr/bin/env python
# coding: utf-8

# In[]:

import pandas as pd
import matplotlib.pyplot as pyplot
import numpy as np
from cycler import cycler
import scipy as sp
import matsim as ms

# todo: https??
hh = pd.read_csv('../covid-sim/src/assets/' + 'berlin-hospital.csv', sep=",").fillna(value=0.)
# hh = pd.read_csv('/Users/kainagel/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/Berlin/10052020_TaSpi.csv', sep=";").fillna(value=0.)
hh.index = pd.date_range(start='2020-03-01', periods=hh.index.size)

cc = pd.read_csv('/Users/kainagel/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/berlin-cases.csv', sep=",").fillna(value=0.)
cc.index = pd.date_range(start='2020-02-21', periods=cc.index.size)

# In[]:

######## maxIA=10

###### sInfct=0:

### unrestr:

### snz:

##### sInfct=1:

### unrestr:

### snz:


########## maxIA=3
######## sInfct=0:
##### unrestr:
##### snz:

### std:
# base = '2020-06-04_21-58-07__frmSnz__theta2.6E-6@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt52_trCpJn_30_ciCorr0.3_@2020-03-08_alph1.4_seed4711_strtDt2020-02-12/'
# base = '2020-06-04_22-14-19__frmSnz__theta2.6E-6@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt52_trCpJn_30_ciCorr0.3_@2020-03-08_alph1.4_seed4713_strtDt2020-02-12/'

### w/ re-op:
# base = '2020-06-05_20-21-33__frmSnz__theta2.6E-6@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt52_trCpJn_30_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4711_strtDt2020-02-12/'
# base = '2020-06-05_20-21-22__frmSnz__theta2.6E-6@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt52_trCpJn_30_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4713_strtDt2020-02-12/'
# base = '2020-06-05_23-54-24__frmSnz__theta2.6E-6@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt52_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4715_strtDt2020-02-12_trCap30/'
# base = '2020-06-05_23-54-35__frmSnz__theta2.6E-6@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt52_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4717_strtDt2020-02-12_trCap30/'
# base = '2020-06-06_09-43-26__frmSnz__theta2.6E-6@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt52_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4719_strtDt2020-02-12_trCap30/'
# base = '2020-06-06_09-43-39__frmSnz__theta2.6E-6@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt52_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4721_strtDt2020-02-12_trCap30/'

### w/ re-op & inf tracing:
# base = '2020-06-05_19-34-58__frmSnz__theta2.6E-6@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt52_trCpJn_2147483647_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4711_strtDt2020-02-12/'
# base = '2020-06-05_19-35-15__frmSnz__theta2.6E-6@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt52_trCpJn_2147483647_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4713_strtDt2020-02-12/'

# base = '2020-06-06_17-36-29__frmSnz__theta2.6E-6@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt52_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4719_strtDt2020-02-12_trCap2147483647/'
# base = '2020-06-06_17-36-20__frmSnz__theta2.6E-6@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt52_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4721_strtDt2020-02-12_trCap2147483647/'

####### sInfct=1:
##### unrestr:
##### snz:
### std:
# base = '2020-06-04_23-01-09__frmSnz__theta3.0E-6@3__pWSymp1.0__sInfct1.0__sSusc0.0__trStrt52_trCpJn_30_ciCorr0.3_@2020-03-08_alph1.4_seed4711_strtDt2020-02-12/'
# base = '2020-06-04_23-41-20__frmSnz__theta3.0E-6@3__pWSymp1.0__sInfct1.0__sSusc0.0__trStrt52_trCpJn_30_ciCorr0.3_@2020-03-08_alph1.4_seed4713_strtDt2020-02-12/'

### w/ reop:
# base = '2020-06-04_23-46-39__frmSnz__theta3.0E-6@3__pWSymp1.0__sInfct1.0__sSusc0.0__trStrt52_trCpJn_30_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4711_strtDt2020-02-12/'
# base = '2020-06-04_23-50-26__frmSnz__theta3.0E-6@3__pWSymp1.0__sInfct1.0__sSusc0.0__trStrt52_trCpJn_30_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4713_strtDt2020-02-12/'

# w/ reop & inf trac:
# base = '2020-06-05_08-19-22__frmSnz__theta3.0E-6@3__pWSymp1.0__sInfct1.0__sSusc0.0__trStrt52_trCpJn_2147483647_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4711_strtDt2020-02-12/'
# base = '2020-06-05_08-19-09__frmSnz__theta3.0E-6@3__pWSymp1.0__sInfct1.0__sSusc0.0__trStrt52_trCpJn_2147483647_ciCorr0.3_@2020-03-08_alph1.4_reopFr1.0_seed4713_strtDt2020-02-12/'

# ---

# base = '2020-06-12_18-03-07__frmSnz__theta1.0E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_seed4711_strtDt2020-02-12_trCap30/'
# base = '2020-06-12_23-26-14__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_seed4711_strtDt2020-02-12_trCap30/'
# base = '2020-06-12_23-26-21__frmSnz__theta1.2E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_seed4711_strtDt2020-02-12_trCap30/'

# base = '2020-06-12_23-26-14__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_seed4711_strtDt2020-02-12_trCap30/'
# base = '2020-06-13_08-04-16__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_seed4711_strtDt2020-02-13_trCap30/'
# base = '2020-06-13_08-04-25__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_seed4711_strtDt2020-02-14_trCap30/'
# base = '2020-06-13_08-04-36__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_seed4711_strtDt2020-02-15_trCap30/'
# base = '2020-06-13_09-02-33__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_seed4711_strtDt2020-02-16_trCap30/'
# base = '2020-06-13_09-36-44__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_seed4711_strtDt2020-02-17_trCap30/'
# base = '2020-06-13_11-03-39__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_seed4711_strtDt2020-02-18_trCap30/'
# base = '2020-06-13_11-03-14__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_seed4711_strtDt2020-02-19_trCap30/'
# base = '2020-06-13_11-04-06__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_seed4711_strtDt2020-02-20_trCap30/'

# andere Maskenwerte:
# base = '2020-06-13_13-55-28__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_ciCorr0.3_@2020-03-08_alph1.0_seed4711_strtDt2020-02-18_trCap30/'

# base = '2020-06-13_20-29-54__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_ciCorr0.3_@2020-03-08_alph1.0_masksOffset14_masksDay2020-05-04@0.5_0.2_seed4711_strtDt2020-02-18_trCap30/'
# base = '2020-06-13_20-24-45__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_ciCorr0.3_@2020-03-08_alph1.0_masksOffset14_masksDay2020-05-04@0.5_0.2_seed4711_strtDt2020-02-19_trCap30/'

# these two can't be used (infinite tracing capacity from early on)
# base = '2020-06-13_21-55-24__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_ciCorr0.3_@2020-03-08_alph1.0_masksOffset14_masksDay2020-05-04@0.5_0.2_seed4711_strtDt2020-02-18_trCap2147483647/'
# base = '2020-06-13_21-55-35__frmSnz__theta1.1E-5@3__pWSymp1.0__sInfct0.0__sSusc0.0__trStrt49_ciCorr0.3_@2020-03-08_alph1.0_masksOffset14_masksDay2020-05-04@0.5_0.2_seed4711_strtDt2020-02-19_trCap2147483647/'

# base = '2020-06-14_11-01-58__frmSnz__theta1.1E-5@3__sInfct0.0__sSusc0.0__trStrt49_ciCorr0.3_@2020-03-08_alph1.0_masksOffset14_masksDay2020-05-04@0.5_0.1_seed4711_strtDt2020-02-18_trCap30/'

# base = '2020-06-14_12-17-53__frmSnz__theta1.1E-5@3__sInfct0.0__sSusc0.0__trStrt49_ciCorr0.3_@2020-03-08_alph1.0_masksPeriod14upto0.5_0.1_seed4711_strtDt2020-02-18_trCap30/'
# base = '2020-06-14_12-17-59__frmSnz__theta1.1E-5@3__sInfct0.0__sSusc0.0__trStrt49_ciCorr0.3_@2020-03-08_alph1.0_masksPeriod14upto0.5_0.1_seed4711_strtDt2020-02-19_trCap30/'

# base = '2020-06-14_13-21-25__frmSnz__theta1.1E-5@3__sInfct0.0__sSusc0.0__trStrt49_ciCorr0.3_@2020-03-08_alph1.0_masksPeriod14upto0.5_0.1_seed4711_strtDt2020-02-18_trCap2147483647/'
base = '2020-06-14_13-21-33__frmSnz__theta1.1E-5@3__sInfct0.0__sSusc0.0__trStrt49_ciCorr0.3_@2020-03-08_alph1.0_masksPeriod14upto0.5_0.1_seed4711_strtDt2020-02-19_trCap2147483647/'

rr = pd.read_csv(base + 'infections.txt', sep='\t')
rr['date'] = pd.to_datetime(rr['date'])
rr.set_index('date',inplace=True)

infectedCumulative = rr.loc[rr['district'] == 'Berlin', ['nInfectedCumulative']]
nContagious = rr.loc[rr['district'] == 'Berlin', ['nContagiousCumulative']]
nShowingSymptoms = rr.loc[rr['district'] == 'Berlin', ['nShowingSymptomsCumulative']]

# rr2b = pd.concat([hh['Gemeldete Fälle'], infectedCumulative.rolling('3D').mean()], axis=1).fillna(value=0.)

fit = pd.Series(1 * np.exp(np.arange(0, 30, 1) * np.log(2.) / 2.8))
fit.index = pd.date_range(start="2020-02-20", periods=fit.size)

fit2 = pd.Series(60 * np.exp(np.arange(0, 30, 1) * np.log(2.) / 8))
fit2.index = pd.date_range(start="2020-03-01", periods=30)

fit3 = pd.Series(400 * np.exp(np.arange(0, 80, 1) * np.log(2.) / (-17)))
fit3.index = pd.date_range(start="2020-03-01", periods=80)

rr3 = pd.concat([cc['cases'], infectedCumulative.diff(),nContagious.diff(), nShowingSymptoms.diff()], axis=1)
# rr3 = pd.concat([cc['cases'], infectedCumulative.diff(),nContagious.diff(), nShowingSymptoms.diff(),fit,fit2,fit3], axis=1)
# rr3 = pd.concat([cc['cases']])

pyplot.close('all')
pyplot.rcParams['figure.figsize']=[12, 5]
default_cycler = (cycler(color=['r', 'g', 'b', 'y','red','purple','orange']) +
                  cycler(linestyle=['', '', '', '','-','-','-']) +
                  cycler(marker=['.','','.','','','','']))
pyplot.rc('axes', prop_cycle=default_cycler)
axes = rr3.plot(logy=True,grid=True)
# axes.set_ylim(0,10000)
axes.set_xlim(pd.to_datetime('2020-02-15'),pd.to_datetime('2020-10-15'))
# pyplot.axvline(pd.to_datetime('2020-03-11'), color='red', linestyle='-', lw=1)
# pyplot.axvline(pd.to_datetime('2020-03-16'), color='red', linestyle='-', lw=1)
# pyplot.axvline(pd.to_datetime('2020-03-21'), color='red', linestyle='-', lw=1)
pyplot.axhline(32,color='gray',linestyle='dotted')
pyplot.show()

# In[]:

# --

infected = rr.loc[rr['district'] == 'Berlin' , ['nSeriouslySick','nCritical']]


infected['shouldBeInHospital'] = infected['nSeriouslySick'] + infected['nCritical']

rr3 = pd.concat([hh['Stationäre Behandlung'], hh['Intensivmedizin'], infected['shouldBeInHospital'], infected['nCritical']], axis=1).fillna(value=0.)

pyplot.close('all')
pyplot.rcParams['figure.figsize']=[12, 5]
default_cycler = (cycler(color=['r', 'g', 'b', 'y','pink','purple']) +
                  cycler(linestyle=['', '', '', '','-','']) +
                  cycler(marker=['.','.','.','.',",",'']))
pyplot.rc('axes', prop_cycle=default_cycler)
rr3.plot(logy=True,grid=True)
pyplot.show()

# In[]:

# infectedCumulative = rr.loc[rr['district'] == 'Berlin' , ['day', 'nInfectedCumulative']]
# infectedCumulative.index = pd.date_range(start=simStartDate, periods=infectedCumulative.size / 2)
rr2b = infectedCumulative['nInfectedCumulative']

fit = pd.Series(2 * np.exp(np.arange(0, 60, 1) * np.log(2.) / 2.8))
fit.index = pd.date_range(start="2020-02-15", periods=60)

rr3 = pd.concat([rr2b, fit], axis=1)

pyplot.close('all')
pyplot.rcParams['figure.figsize']=[12, 5]
default_cycler = (cycler(color=['r', 'g', 'b', 'y','pink','purple']) +
                  cycler(linestyle=['-', '-', '-', '-','-','']) +
                  cycler(marker=['','','','',",",'']))
pyplot.rc('axes', prop_cycle=default_cycler)
rr3.plot(logy=True,grid=True)
pyplot.axvline(23, color='y', linestyle='-', lw=1)
pyplot.show()

# ln[]:

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
infectionEvents = pd.read_csv(base + "infectionEvents.txt", sep="\t")

# In[]:
plans = ms.plan_reader( "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_entirePopulation_emptyPlans_withDistricts.xml.gz", selectedPlansOnly = True )
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

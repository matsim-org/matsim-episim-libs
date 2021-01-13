#!/usr/bin/env python
# coding: utf-8

# In[]:
import glob
import pandas as pd
import matplotlib.pyplot as pyplot
import numpy as np
from cycler import cycler
# import matsim as ms

# In[]:

rkiDiseaseImport = pd.read_csv('rkiDiseaseImport.tsv', sep='\t', parse_dates=['date']).fillna(value=0.)
rkiDiseaseImport.set_index('date', inplace=True)

casesReferenceDate = pd.read_csv('/Users/kainagel/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/berlin-cases.csv', sep=",").fillna(value=0.)
casesReferenceDate.index = pd.to_datetime(casesReferenceDate['year'].apply(str) + '-' + casesReferenceDate['month'].apply(str) + '-' + casesReferenceDate['day'].apply(str))
casesReferenceDate2 = pd.concat( [casesReferenceDate,rkiDiseaseImport]).fillna(value=0.)
casesReferenceDate.drop(casesReferenceDate.index, inplace=True)

casesReportingDate = pd.read_csv('/Users/kainagel/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/berlin-cases-meldedatum.csv', sep=",").fillna(value=0.)
casesReportingDate.index = pd.to_datetime(casesReportingDate['year'].apply(str) + '-' + casesReportingDate['month'].apply(str) + '-' + casesReportingDate['day'].apply(str))
casesReportingDate2 = pd.concat( [casesReportingDate,rkiDiseaseImport]).fillna(value=0.)
casesReportingDate.drop(casesReportingDate.index, inplace=True)


ss = pd.read_csv('/Users/kainagel/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/SARS-CoV2_surveillance.csv', sep=",",parse_dates=['Datum'],dayfirst=True).fillna(value=0.)
ss.index = pd.to_datetime(ss['Datum'])

# todo: https??
hh = pd.read_csv('~/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/Berlin/berlin-hospital.csv', sep=",").fillna(value=0.)
# hh = pd.read_csv('/Users/kainagel/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/Berlin/10052020_TaSpi.csv', sep=";").fillna(value=0.)
hh.index = pd.date_range(start='2020-03-01', periods=hh.index.size)
# hh.index = pd.to_datetime( hh['year'].apply(str) + '-' + hh['month'].apply(str) + '-' + hh['day'].apply(str) )

# In[]:

# base = 'output/2020-06-19_19-48-41__unrestr__theta1.1E-5@3__trStrt46_seed4711_strtDt2020-02-16_trCap0/'

# base = 'output/2020-06-19_23-29-07__frmSnz__theta1.1E-5@3__trStrt46_ciCorr1.0_@2020-03-07_alph1.0upto0.0_0.0_seed4711_strtDt2020-02-16_trCap0/'
# base = 'output/2020-06-19_23-29-15__frmSnz__theta1.1E-5@3__trStrt46_ciCorr1.0_@2020-03-07_alph1.5upto0.0_0.0_seed4711_strtDt2020-02-16_trCap0/'
# base = 'output/2020-06-20_15-36-11__frmSnz__theta1.1E-5@3__trStrt46_ciCorr1.0_@2020-03-07_alph1.7upto0.0_0.0_seed4711_strtDt2020-02-16_trCap0/'
# base = 'output/2020-06-20_15-36-04__frmSnz__theta1.1E-5@3__trStrt46_ciCorr1.0_@2020-03-07_alph1.8upto0.0_0.0_seed4711_strtDt2020-02-16_trCap0/'
# base = 'output/2020-06-20_15-35-46__frmSnz__theta1.1E-5@3__trStrt46_ciCorr1.0_@2020-03-07_alph1.9upto0.0_0.0_seed4711_strtDt2020-02-16_trCap0/'
# base = 'output/2020-06-20_08-19-21__frmSnz__theta1.1E-5@3__trStrt46_ciCorr1.0_@2020-03-07_alph2.0upto0.0_0.0_seed4711_strtDt2020-02-16_trCap0/'


# base = 'output/2020-06-20_09-48-46__frmSnz__theta1.1E-5@3__trStrt46_ciCorr0.3_@2020-03-07_alph1.0upto0.0_0.0_seed4711_strtDt2020-02-16_trCap0/'
# base = 'output/2020-06-21_13-06-35__frmSnz__theta1.1E-5@3__trStrt46_ciCorr0.3_@2020-03-07_alph1.0upto0.0_0.0_seed4711_strtDt2020-02-18_trCap0/'
# base = 'output/2020-06-21_13-06-44__frmSnz__theta1.1E-5@3__trStrt46_ciCorr0.3_@2020-03-07_alph1.0upto0.0_0.0_seed4711_strtDt2020-02-20_trCap0/'

# base = 'output/2020-07-13_21-35-41__unrestr__theta1.13E-5@100__trStrt46_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/' # 1.
# base = 'output/2020-07-13_21-45-54__unrestr__theta1.13E-5@100__trStrt46_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/' # 0.5
# base = 'output/2020-07-13_22-59-01__unrestr__theta1.13E-5@100__trStrt46_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/' # 0.3333
# base = 'output/2020-07-14_07-52-40__unrestr__theta1.13E-5@100__trStrt46_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/' # 0.3
# base = 'output/2020-07-14_07-54-42__unrestr__theta1.13E-5@100__trStrt46_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/' # 0.29
# base = 'output/2020-07-14_07-55-34__unrestr__theta1.13E-5@100__trStrt46_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/' # 0.28 # zu wenig steil
# base = 'output/2020-07-13_23-04-14__unrestr__theta1.13E-5@100__trStrt46_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/' # 0.25
# base = 'output/2020-07-13_21-53-16__unrestr__theta1.13E-5@100__trStrt46_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/' # 0.2
# base = 'output/2020-07-13_21-54-07__unrestr__theta1.13E-5@100__trStrt46_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/' # 0.1

# base = 'output/2020-07-14_08-26-47__fromConfig__theta1.13E-5@100__trStrt46_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/' # 0.3
# base = 'output/2020-07-14_23-03-24__fromConfig__theta1.13E-5@100__trStrt46_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/' #0.29
# base = 'output/2020-07-14_20-26-08__fromConfig__theta1.13E-5@100__trStrt46_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/' # 0.28

# base = 'output/2020-07-14_23-18-55__fromSnz__theta1.13E-5@100__trStrt46_ciCorr2020-03-07@0.3_alph1.0_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/'
# base = 'output/2020-07-14_23-17-36__fromSnz__theta1.13E-5@100__trStrt46_ciCorr2020-03-07@0.25_alph1.0_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/'

# base = 'output/2020-07-14_23-19-07__fromSnz__theta1.13E-5@100__trStrt46_ciCorr2020-03-07@0.2_alph1.0_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/'

# base = 'output/2020-07-15_08-05-53__fromSnz__theta1.13E-5@100__trStrt46_ciCorr2020-03-07@0.2_alph1.2_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/'
# base = 'output/2020-07-15_08-05-46__fromSnz__theta1.13E-5@100__trStrt46_ciCorr2020-03-07@0.2_alph1.4_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/'
# base = 'output/2020-07-15_08-06-02__fromSnz__theta1.13E-5@100__trStrt46_ciCorr2020-03-07@0.2_alph1.6_seed4711_strtDt2020-02-15_trCap{1970-01-01=0}/'

# ===

# base = 'output/2020-07-15_13-33-46__fromConfig__theta1.18E-5@3.0__trStrt46_seed4711_strtDt2020-02-18_trCap{2020-04-01=30}/'
# base = 'output/2020-07-15_20-22-35__fromSnz__theta1.18E-5@3.0__trStrt46_ciCorr2020-03-07@0.32_alph1.0_seed4711_strtDt2020-02-16_trCap{2020-04-01=30}/'
# base = 'output/2020-07-15_21-00-00__fromSnz__theta1.15E-5@3.0__trStrt46_ciCorr2020-03-07@0.3_alph1.0_seed4711_strtDt2020-02-17_trCap{2020-04-01=30}/'

# base = 'output/2020-07-15_22-46-53__fromSnz__theta1.2E-5@3.0__trStrt46_ciCorr2020-03-07@0.25_alph1.0_seed4711_strtDt2020-02-17_trCap{2020-04-01=30}/'
# base = 'output/2020-07-15_22-46-41__fromSnz__theta1.2E-5@3.0__trStrt46_ciCorr2020-03-07@0.2_alph1.0_seed4711_strtDt2020-02-17_trCap{2020-04-01=30}/'

# sqrt interaction:

# base = 'output/2020-07-16_09-11-43__symmetric__fromSnz__theta1.2E-5@25.0__trStrt46_ciCorr2020-03-07@0.25_alph1.0_seed4711_strtDt2020-02-17_trCap{2020-04-01=30}/'

# base = 'output/2020-07-16_09-12-27__symmetric__fromSnz__theta1.2E-5@2.5__trStrt46_ciCorr2020-03-07@0.25_alph1.0_seed4711_strtDt2020-02-17_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_17-35-01__symmetric__fromSnz__theta6.0E-6@5.0__trStrt46_ciCorr2020-03-07@0.25_alph1.0_seed4711_strtDt2020-02-17_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_17-35-21__symmetric__fromSnz__theta3.0E-6@10.0__trStrt46_ciCorr2020-03-07@0.25_alph1.0_seed4711_strtDt2020-02-17_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_18-18-22__symmetric__fromSnz__theta1.0E-6@10.0__trStrt46_ciCorr2020-03-07@0.25_alph1.0_seed4711_strtDt2020-02-17_trCap{2020-04-01=30}/'

# base = 'output/2020-07-16_18-18-14__symmetric__fromSnz__theta2.0E-6@10.0__trStrt46_ciCorr2020-03-07@0.25_alph1.0_seed4711_strtDt2020-02-17_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_18-48-01__symmetric__fromSnz__theta2.0E-6@10.0__trStrt46_ciCorr2020-03-07@0.25_alph1.0_seed4711_strtDt2020-02-15_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_18-48-14__symmetric__fromSnz__theta2.0E-6@10.0__trStrt46_ciCorr2020-03-07@0.25_alph1.0_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}/'

# base = 'output/2020-07-16_19-38-27__symmetric__unrestr__theta2.0E-6@10.0__trStrt46_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_21-01-28__symmetric__unrestr__theta2.0E-6@10.0__trStrt46_seed4711_strtDt2020-02-14_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_19-38-19__symmetric__unrestr__theta2.0E-6@10.0__trStrt46_seed4711_strtDt2020-02-15_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_21-01-35__symmetric__unrestr__theta2.0E-6@10.0__trStrt46_seed4711_strtDt2020-02-16_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_19-38-10__symmetric__unrestr__theta2.0E-6@10.0__trStrt46_seed4711_strtDt2020-02-17_trCap{2020-04-01=30}/'

# base = 'output/2020-07-16_21-05-41__symmetric__unrestr__theta1.7E-6@10.0__trStrt46_seed4711_strtDt2020-02-15_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_21-05-35__symmetric__unrestr__theta1.3E-6@10.0__trStrt46_seed4711_strtDt2020-02-15_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_21-05-27__symmetric__unrestr__theta1.0E-6@10.0__trStrt46_seed4711_strtDt2020-02-15_trCap{2020-04-01=30}/'

# base = 'output/2020-07-16_21-45-49__symmetric__unrestr__theta1.0E-6@10.0__trStrt46_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}/'

# divN interaction:


# base = 'output/2020-07-16_22-48-02__divN__unrestr__theta1.0E-6@10.0__trStrt46_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_23-04-28__divN__unrestr__theta8.0E-6@10.0__trStrt46_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_23-04-15__divN__unrestr__theta1.2E-5@10.0__trStrt46_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_23-04-34__divN__unrestr__theta1.6E-5@10.0__trStrt46_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_23-57-53__divN__unrestr__theta1.0E-4@10.0__trStrt46_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}/'
# base = 'output/2020-07-16_23-58-02__divN__unrestr__theta0.001@10.0__trStrt46_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}/'

# base = 'output/2020-08-30_12-53-58__original__unrestr__theta1.2E-5@25.0__trStrt46_seed4711_strtDt2020-02-17_trCap{2020-04-01=30}/'

# base = 'output/2020-08-30_13-35-54__original__unrestr__theta1.0E-5@3.0__trStrt46_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}/'
# base = 'output/2020-08-30_18-37-36__original__unrestr__theta3.3333333333333337E-6@9.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/'
# base = 'output/2020-08-30_18-37-49__original__unrestr__theta1.1111111111111112E-6@27.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/'
# base = 'output/2020-08-30_18-38-00__original__unrestr__theta3.703703703703704E-7@81.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/'

# base = 'output/2020-08-30_18-38-38__original__unrestr__theta3.0000000000000004E-5@1.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/'

# base = 'output/2020-08-30_18-38-00__original__unrestr__theta3.703703703703704E-7@81.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/'
# base = 'output/2020-08-30_22-56-42__original__unrestr__theta1.1111111111111112E-6@81.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/'
# base = 'output/2020-08-30_22-56-52__original__unrestr__theta3.3333333333333337E-6@81.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/'
# base = 'output/2020-08-30_22-57-01__original__unrestr__theta1.0E-5@81.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/'

# base = 'output/2020-08-31_08-36-56__original__unrestr__theta4.11522633744856E-8@2187.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/'
# base = 'output/2020-08-31_08-37-03__original__unrestr__theta1.234567901234568E-7@2187.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/'
# base = 'output/2020-08-31_08-37-12__original__unrestr__theta3.703703703703704E-7@2187.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/'
# base = 'output/2020-08-31_08-37-22__original__unrestr__theta1.1111111111111112E-6@2187.0_seed4711_strtDt2020-02-17_trCap{1970-01-01=0}__trStrt46/'

# base = 'output/2020-09-19_17-55-38__symmetric__unrestr__theta7.0E-5@10.0_seed4711_strtDt2020-02-13_trCap{1970-01-01=0}__trStrt46/'

# base = 'output/2020-09-19_18-27-13__symmetric__unrestr__theta7.0E-5@10.0_seed4711_strtDt2020-02-13_trCap{1970-01-01=0}__trStrt46/'
# base = 'output/2020-09-19_18-35-31__symmetric__unrestr__theta7.0E-6@10.0_seed4711_strtDt2020-02-13_trCap{1970-01-01=0}__trStrt46/'
# base = 'output/2020-09-19_18-35-38__symmetric__unrestr__theta7.0E-7@10.0_seed4711_strtDt2020-02-13_trCap{1970-01-01=0}__trStrt46/'

# base = 'output/2020-09-19_23-30-36__symmetric__unrestr__theta1.0E-6@10.0_seed4711_strtDt2020-02-13_trCap{1970-01-01=0}__trStrt46/'
# base = 'output/2020-09-19_23-30-58__symmetric__unrestr__theta3.0E-6@10.0_seed4711_strtDt2020-02-13_trCap{1970-01-01=0}__trStrt46/'

# base = 'output/2020-09-20_09-44-07__symmetric__fromConfig__theta2.0E-6@10.0_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}__trStrt46/'
# base = 'output/2020-09-20_09-44-37__symmetric__fromConfig__theta1.0E-6@10.0_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}__trStrt46/'

# base = 'output/2020-09-20_18-07-37__symmetric__fromConfig__theta1.8E-6@10.0_seed4711_strtDt2020-02-15_trCap{2020-04-01=30}__trStrt46/'

# base = 'output/2020-09-20_22-50-04__symmetric__fromConfig__theta3.0E-6@NaN_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}__trStrt46/'
# base = 'output/2020-09-20_22-50-26__symmetric__fromConfig__theta5.0E-6@NaN_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}__trStrt46/'
# base = 'output/2020-09-21_07-56-08__symmetric__fromConfig__theta1.0E-5@NaN_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}__trStrt46/'
# base = 'output/2020-09-21_07-56-19__symmetric__fromConfig__theta2.0E-5@NaN_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}__trStrt46/'
# base = 'output/2020-09-21_07-56-36__symmetric__fromConfig__theta4.0E-5@NaN_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}__trStrt46/'
# base = 'output/2020-09-21_07-56-52__symmetric__fromConfig__theta1.0E-4@NaN_seed4711_strtDt2020-02-13_trCap{2020-04-01=30}__trStrt46/'

# base = 'output/2020-09-21_12-41-45__symmetric__fromConfig__theta1.0E-5@NaN_seed4711_strtDt2020-02-13_trCap{2020-04-01=30, 2020-06-01=2147483647}__trStrt46/'
# base = 'output/2020-09-21_12-41-37__symmetric__fromConfig__theta2.0E-5@NaN_seed4711_strtDt2020-02-13_trCap{2020-04-01=30, 2020-06-01=2147483647}__trStrt46/'
# base = 'output/2020-09-21_12-28-51__symmetric__fromConfig__theta3.0E-5@NaN_seed4711_strtDt2020-02-13_trCap{2020-04-01=30, 2020-06-01=2147483647}__trStrt46/'

# base = 'output/2020-09-21_14-24-50__symmetric__fromConfig__theta2.1E-5@NaN_seed4711_strtDt2020-02-16_trCap{2020-04-01=30, 2020-06-01=2147483647}__trStrt46/'
# base = 'output/2020-09-21_15-23-09__symmetric__fromConfig__theta2.2E-5@NaN_seed4711_strtDt2020-02-19_trCap{2020-04-01=30, 2020-06-01=2147483647}__trStrt46/'
# base = 'output/2020-09-21_16-00-16__symmetric__fromConfig__theta2.1E-5@NaN_seed4711_strtDt2020-02-18_trCap{2020-04-01=30, 2020-06-01=2147483647}__trStrt46/'

# base = 'output/2020-09-21_19-04-07__symmetric__fromConfig__theta2.1E-5@NaN_seed4711_strtDt2020-02-18_trCap{2020-04-01=30, 2020-06-01=2147483647}__trStrt46/'

# base = 'output/2020-09-23_16-21-37__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-18_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_16-27-39__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-18_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'

# base = 'output/2020-09-23_16-27-39__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-18_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_17-03-48__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-20_imprtOffst-2_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_17-03-56__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-22_imprtOffst-2_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_17-42-32__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-24_imprtOffst-2_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_18-18-31__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-26_imprtOffst-2_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_18-21-55__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-28_imprtOffst-2_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'

# base = 'output/2020-09-23_18-10-43__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-26_imprtOffst-1_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_18-11-07__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-28_imprtOffst-1_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'

# base = 'output/2020-09-23_19-08-03__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-22_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_19-07-53__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-24_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_18-51-58__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-26_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_18-51-51__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-28_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'

# ---

# base = 'output/2020-09-23_20-12-28__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_20-14-42__oldSymmetric__fromConfig__theta9.0E-6@3.0_seed4711_strtDt2020-02-22_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'

# base = 'output/2020-09-23_21-34-18__oldSymmetric__fromConfig__theta6.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'

# base = 'output/2020-09-23_22-30-37__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-14_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-03-31/'
# base = 'output/2020-09-23_21-34-24__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_21-34-31__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-22_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'

# base = 'output/2020-09-23_23-01-24__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-14_imprtOffst-6_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-03-31/'
# base = 'output/2020-09-23_22-21-27__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-14_imprtOffst-4_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-03-31/'
# base = 'output/2020-09-23_22-21-20__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-14_imprtOffst-2_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-03-31/'
# base = 'output/2020-09-23_22-30-37__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-14_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-03-31/'
# base = 'output/2020-09-23_22-21-34__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-14_imprtOffst2_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-03-31/'

# base = 'output/2020-09-24_07-24-28__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst-18_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-04-04/'
# base = 'output/2020-09-24_07-25-23__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst-15_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-04-04/'
# base = 'output/2020-09-24_07-24-05__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst-12_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-04-04/'
# base = 'output/2020-09-24_07-23-51__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst-9_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-04-04/'
# base = 'output/2020-09-23_23-01-39__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst-6_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-04-04/'
# base = 'output/2020-09-23_23-01-51__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst-3_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-04-04/'
# base = 'output/2020-09-23_21-34-24__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}__trStrt46/'
# base = 'output/2020-09-23_23-02-01__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst3_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-04-04/'

# base = '0output/zz_archive-2020-10-04/2020-09-24_12-37-12__oldSymmetric__fromConfig__theta5.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst0_trCap{2020-04-01=30, 2020-06-15=2147483647}_quStrt2020-04-04/'

# base = 'output/2020-10-04_16-24-49__symmetric__fromConfig__theta2.1E-5@NaN_seed0_strtDt2020-02-18_imprtOffst0_trCap{1970-01-01=0}_quStrt+5881630-08-28/'

# base = 'output/2020-10-08_08-38-05__symmetric__unrestr__theta5.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst0_trCap{1970-01-01=0}_quStrt+5881630-08-28/'

# base = 'output/2020-10-08_08-45-58__oldSymmetric__unrestr__theta5.0E-6@3.0_seed4711_strtDt2020-02-18_imprtOffst0_trCap{1970-01-01=0}_quStrt+5881630-08-28/'
# base = 'output/2020-10-08_12-00-57__oldSymmetric__unrestr__theta5.0E-5@3.0_seed4711_strtDt2020-02-18_imprtOffst0_trCap{1970-01-01=0}_quStrt+5881630-08-28/'
# base = 'output/2020-10-08_12-02-34__oldSymmetric__unrestr__theta5.0E-4@3.0_seed4711_strtDt2020-02-18_imprtOffst0_trCap{1970-01-01=0}_quStrt+5881630-08-28/'
# base = 'output/2020-10-08_13-00-01__oldSymmetric__unrestr__theta0.005@3.0_seed4711_strtDt2020-02-18_imprtOffst0_trCap{1970-01-01=0}_quStrt+5881630-08-28/'

# base = 'output/seed_4711-factor_0.1/weekSymmetric1.'
# base = 'output/seed_4711-factor_0.3/weekSymmetric2.'
# base = 'output/seed_4711-factor_0.01/weekSymmetric1.'
# base = 'output/seed_4711-factor_0.03/weekSymmetric2.'

# base = 'output/seed_4711-factor_0.01/weekSymmetric1.'

# base = 'output/importOffset_-14-theta_0.9/calibration1.'
# base = 'output/importOffset_-14-theta_0.9-tracingDelay_4/calibration3.'
# base = 'output/importOffset_-14-theta_0.9-tracingDelay_3/calibration2.'
# base = 'output/importOffset_-14-theta_0.9-tracingDelay_2/calibration1.'

# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_0.0-summerEnd_2020-08-15/calibration1.'
# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_0.0-summerEnd_2020-09-01/calibration1.'
# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_0.0-summerEnd_2020-09-15/calibration1.'
# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_0.0-summerEnd_2020-09-30/calibration1.'
# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_0.0-summerEnd_fromWeather/calibration1.'

# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_0.0-summerEnd_2020-11-14/calibration1.'
# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_0.5-summerEnd_2020-11-14/calibration1.'
# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_1.0-summerEnd_2020-11-14/calibration1.'
# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_1.5-summerEnd_2020-11-14/calibration1.'

# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_0.0-summerEnd_fromWeather/calibration1.'
# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_0.5-summerEnd_fromWeather/calibration1.'
# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_1.0-summerEnd_fromWeather/calibration1.'
# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_1.5-summerEnd_fromWeather/calibration1.'

# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_1.0-summerEnd_fromWeather/calibration1.'
# base = 'output/importOffset_0-theta_1.0-tracingDelay_4-childInfectivitySusceptibility_1.0-summerEnd_fromWeather/calibration1.'
# base = 'output/importOffset_0-theta_1.05-tracingDelay_4-childInfectivitySusceptibility_1.0-summerEnd_fromWeather/calibration1.'
# base = 'output/importOffset_0-theta_1.1-tracingDelay_4-childInfectivitySusceptibility_1.0-summerEnd_fromWeather/calibration1.'

# base = 'output/importOffset_0-theta_1.0-tracingDelay_4-childInfectivitySusceptibility_1.0-summerEnd_fromWeather/calibration1.'
# base = 'output/importOffset_0-theta_1.0-tracingDelay_4-childInfectivitySusceptibility_1.2-summerEnd_fromWeather/calibration1.'
# base = 'output/importOffset_0-theta_1.0-tracingDelay_4-childInfectivitySusceptibility_1.4-summerEnd_fromWeather/calibration1.'

# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_1.0-summerEnd_fromWeather/calibration1.'
# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_1.2-summerEnd_fromWeather/calibration1.'
# base = 'output/importOffset_0-theta_0.9-tracingDelay_4-childInfectivitySusceptibility_1.4-summerEnd_fromWeather/calibration1.'

# base = 'output/temperature0_15.0-temperature1_30.0-theta_1.0-tracingDelay_4-childInfectivitySusceptibility_0.0-summerEnd_fromWeather/calibration1.'
# base = 'output/temperature0_15.0-temperature1_30.0-theta_1.0-tracingDelay_4-childInfectivitySusceptibility_0.5-summerEnd_fromWeather/calibration1.'
# base = 'output/temperature0_15.0-temperature1_30.0-theta_1.0-tracingDelay_4-childInfectivitySusceptibility_1.0-summerEnd_fromWeather/calibration1.'

# base = 'output/temperature0_10.0-temperature1_20.0-thetaFactor_0.9-tracingDelay_4-childInfectivitySusceptibility_1.0-summerEnd_fromWeather/calibration*.'

# base = 'output/temperature0_12.5-temperature1_32.5-thetaFactor_0.7-childInfectivitySusceptibility_1.0-tracingCap_500-importFactorAfterJune_0.25/calibration*.'

# base = 'output/temperatureMidPoint_22.5-thetaFactor_0.7-tracingCap_500-importFactorAfterJune_0.0-newbornSusceptibility_0.0-newbornInfectivity_0.7-grownUpAge_16/calibration*.'

# base = 'output/tempMidPoint_20.0-tempPm_0.0-thetaFactor_0.7-tracingCap_500-impFactAftJun_0.25/calibration*.'

# base = 'output/tempXTheta_15.0_1.0-tempPm_0.0-tracingCap_2000-impFactAftJun_0.25-grownUpAge_16/calibration*.'
# base = 'output/tempXTheta_25.0_0.65-tempPm_0.0-tracingCap_200-impFactAftJun_0.0-grownUpAge_24/calibration*.'
# base = 'output/tempXTheta_25.0_0.65-tempPm_0.0-impFactBefJun_4.0-youthAge_7-youthSuscept_1.0-grownUpAge_16-impFactAftJun_0.0-tracingCap_2000/calibration*.'

base = 'output/tempXTheta_25.0_0.65-tempPm_0.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_300-tracCapJun_0/calibration*.'

glob_glob = glob.glob(base + 'infections.txt')
print(glob_glob)
assert( len(glob_glob) == 1 )

for textfile in glob_glob:
    infections = pd.read_csv(textfile, sep='\t')
infections['date'] = pd.to_datetime(infections['date'])
infections.set_index('date', inplace=True)

for textfile in glob.glob(base + 'diseaseImport.tsv'):
    diseaseImport = pd.read_csv(textfile, sep='\t')
diseaseImport['date'] = pd.to_datetime(diseaseImport['date'])
diseaseImport.set_index('date', inplace=True)

for textfile in glob.glob(base + 'outdoorFraction.tsv'):
    outdoorFraction = pd.read_csv(textfile, sep='\t')
outdoorFraction['date'] = pd.to_datetime(outdoorFraction['date'])
outdoorFraction.set_index('date', inplace=True)

infectedCumulative = infections.loc[infections['district'] == 'Berlin', ['nInfectedCumulative']]
nContagious = infections.loc[infections['district'] == 'Berlin', ['nContagiousCumulative']]
nShowingSymptoms = infections.loc[infections['district'] == 'Berlin', ['nShowingSymptomsCumulative']]
nTotalInfected = infections.loc[infections['district'] == 'Berlin', ['nTotalInfected']]
# infectedCumulative = rr.loc[rr['district'] == 'unknown', ['nInfectedCumulative']]
# nContagious = rr.loc[rr['district'] == 'unknown', ['nContagiousCumulative']]
# nShowingSymptoms = rr.loc[rr['district'] == 'unknown', ['nShowingSymptomsCumulative']]

# rr2b = pd.concat([hh['Gemeldete Fälle'], infectedCumulative.rolling('3D').mean()], axis=1).fillna(value=0.)

fit = pd.Series(1 * np.exp(np.arange(0, 30, 1) * np.log(2.) / 2.8))
fit.index = pd.date_range(start="2020-02-20", periods=fit.size)

fit2 = pd.Series(60 * np.exp(np.arange(0, 30, 1) * np.log(2.) / 8))
fit2.index = pd.date_range(start="2020-03-01", periods=30)

fit3 = pd.Series(400 * np.exp(np.arange(0, 80, 1) * np.log(2.) / (-17)))
fit3.index = pd.date_range(start="2020-03-01", periods=80)

fit4 = pd.Series(15 * np.exp(np.arange(0, 100, 1) * np.log(2.) / 20))
fit4.index = pd.date_range(start="2020-07-01", periods=fit4.size)

fit5 = pd.Series(80 * np.exp(np.arange(0, 300, 1) * np.log(2.) / 200000))
fit5.index = pd.date_range(start="2020-07-01", periods=fit5.size)

fit6 = pd.Series(42 * np.exp(np.arange(0, 300, 1) * np.log(2.) / 20))
fit6.index = pd.date_range(start="2020-08-20", periods=fit6.size)

rr3 = pd.concat([
    (casesReferenceDate2['cases'] - casesReferenceDate2['nInfected']).resample('W').mean(),
    infectedCumulative.diff().rolling('7D').mean(),
    nContagious.diff().rolling('7D').mean(),
    nShowingSymptoms.diff().resample('7D').mean(),
    ss['Anteil Positiv Berlin Meldewoche'] * 70,
    nTotalInfected,
    #fit4, fit5, fit6,
    diseaseImport['nInfected'],
    (casesReportingDate2['cases'] - casesReportingDate2['nInfected']).resample('W').mean(),
    (1.-0.99*outdoorFraction['outdoorFraction'].resample('W').mean()) * 10
], axis=1)

# disease import:
# rr3.at[pd.to_datetime("2020-02-24"),'diseaseImport'] = 0.9
# ...
# rr3['diseaseImport'].interpolate(inplace=True)

pyplot.close('all')
pyplot.rcParams['figure.figsize']=[12, 5]

default_cycler = ( cycler(color=      ['r', 'g', 'b', 'y','purple','purple','orange','cyan','brown','r','orange']) +
                             cycler(linestyle= ['','', '', '','','','-','','','-','']) +
                             cycler(marker=  ['.','','','','.','','','.','o','','.']))
pyplot.rc('axes', prop_cycle=default_cycler)
axes = rr3.plot(logy=True,grid=True,legend=None)
axes.set_ylim(0.9,4000)
axes.set_xlim(pd.to_datetime('2020-02-10'),pd.to_datetime('2020-12-01'))
pyplot.errorbar(rr3.index,rr3['nShowingSymptomsCumulative'],yerr=3*np.sqrt(rr3['nShowingSymptomsCumulative']) )

# pyplot.axvline(pd.to_datetime('2020-03-10'), color='gray', linestyle=':', lw=1)
# pyplot.axvline(pd.to_datetime('2020-03-17'), color='gray', linestyle=':', lw=1)
# pyplot.axvline(pd.to_datetime('2020-03-22'), color='gray', linestyle=':', lw=1)
# pyplot.axhline(32,color='gray',linestyle='dotted')

# pyplot.plot( pd.to_datetime('2020-03-07'), 1000, 'yo' )
# pyplot.text( pd.to_datetime('2020-03-07'), 1000, '1st day of home office (sat)')
#
# pyplot.plot( pd.to_datetime('2020-03-14'), 800, 'ro' )
# pyplot.text( pd.to_datetime('2020-03-14'), 800, '1st day of school closures (sat)')

# noinspection PyUnresolvedReferences
pyplot.title( base.removeprefix('output/').removesuffix('/calibration*.'), fontdict={'fontsize':10})
pyplot.plot( pd.to_datetime('2020-05-14'), 800, 'yo')
pyplot.text( pd.to_datetime('2020-05-14'), 800, 'KW 20 mid')
pyplot.plot( pd.to_datetime('2020-10-01'), 800, 'yo')
pyplot.text( pd.to_datetime('2020-10-01'), 800, 'KW 40 mid')
# pyplot.legend(loc='upper center')

pyplot.show()
############################################
############################################
# In[]:
#

# --

infected = infections.loc[infections['district'] == 'Berlin' , ['nSeriouslySick', 'nCritical']]


infected['shouldBeInHospital'] = infected['nSeriouslySick'] + infected['nCritical']

fact = 4
rr3 = pd.concat([
    hh['Stationäre Behandlung'].resample('7D').mean() * fact,
    hh['Intensivmedizin'].resample('7D').mean() * fact,
    infected['shouldBeInHospital'].resample('7D').mean(),
    infected['nCritical'].resample('7D').mean()
], axis=1).fillna(value=0.)

# pyplot.close('all')
pyplot.rcParams['figure.figsize']=[12, 5]
default_cycler = (cycler(color=['r', 'g', 'b', 'y','pink','purple','orange','cyan','r','g']) +
                  cycler(linestyle=['', '', '', '','','','','','-','']) +
                  cycler(marker=['.','.','','',",",'','o','','','o']))
pyplot.rc('axes', prop_cycle=default_cycler)
axes = rr3.plot(logy=True,grid=True)
axes.set_xlim(pd.to_datetime('2020-02-10'),pd.to_datetime('2021-01-01'))
axes.set_ylim(4.1,5000)
pyplot.title( base.removeprefix('output/').removesuffix('/calibration*.'), fontdict={'fontsize':10})
pyplot.plot( pd.to_datetime('2020-10-01'), 10, 'yo')
pyplot.text( pd.to_datetime('2020-10-01'), 10, 'KW 40 mid')
pyplot.errorbar(rr3.index,rr3['shouldBeInHospital'],yerr=3*np.sqrt(rr3['shouldBeInHospital']) )
pyplot.errorbar(rr3.index,rr3['nCritical'],yerr=3*np.sqrt(rr3['nCritical']) )
pyplot.show()

############################################
############################################
# In[]:

# infectedCumulative = rr.loc[rr['district'] == 'Berlin' , ['day', 'nInfectedCumulative']]
# infectedCumulative.index = pd.date_range(start=simStartDate, periods=infectedCumulative.size / 2)
rr2b = infectedCumulative['nInfectedCumulative']

fit = pd.Series(2 * np.exp(np.arange(0, 60, 1) * np.log(2.) / 2.8))
fit.index = pd.date_range(start="2020-02-15", periods=60)

rr3 = pd.concat([rr2b, fit], axis=1)

# pyplot.close('all')
pyplot.rcParams['figure.figsize']=[12, 5]
default_cycler = (cycler(color=['r', 'g', 'b', 'y','pink','purple']) +
                  cycler(linestyle=['-', '-', '-', '-','-','']) +
                  cycler(marker=['','','','',",",'']))
pyplot.rc('axes', prop_cycle=default_cycler)
pyplot.axvline(23, color='y', linestyle='-', lw=1)
axes = rr3.plot(logy=True,grid=True)
axes.set_xlim(pd.to_datetime('2020-02-15'),pd.to_datetime('2020-05-15'))
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
# plans = ms.plan_reader( "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_entirePopulation_emptyPlans_withDistricts.xml.gz", selectedPlansOnly = True )
# plans = ms.plan_reader( "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/testPlans_withDistricts.xml.gz", selectedPlansOnly = True )
# plans = ms.plan_reader( "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/test2.xml", selectedPlansOnly = True )

# personAge = dict()
# cnt = 0
# for person, plan in plans:
#     cnt += 1
#     personId = person.attrib['id']
#     matsimAttributes = person.find('attributes')
#     for matsimAttribute in matsimAttributes:
#         if matsimAttribute.attrib['name'] == 'age':
#             personAge[personId] = int(matsimAttribute.text)
#     # if cnt >= 30:
#     #     break
#
# print(personAge)

# In[]:

# personsByAge = dict.fromkeys(range(100),0)
# for abc in personAge:
#     personsByAge[personAge.get(abc)] += 0.02
#
# infectionsByAge = dict.fromkeys(range(100),0)
# for abc in infectionEvents.loc[:,"infected"]:
#     infectionsByAge[personAge.get(abc)] += 1
#
#
# print(infectionsByAge)
#
# pyplot.bar(*zip(*personsByAge.items()))
# pyplot.bar(*zip(*infectionsByAge.items()))
# pyplot.show()

# In[]:


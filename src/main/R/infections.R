library(tidyverse)
library(lubridate)
library(cowplot)

# rkiCasesReferenceDate <- read_csv("~/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/berlin-cases.csv")
rkiCasesReferenceDate <- read_csv("~/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/cologne-cases.csv")
# cc %>% mutate( date = make_date(year,month,day)) %>% mutate( av = zoo::rollmean(cases, k=7, fill=NA)) -> cc2
cc2 <- rkiCasesReferenceDate %>%
  mutate( date=make_date(year,month,day)) %>%
  mutate( year = year(date), week = isoweek(date) ) %>%
  group_by(year,week) %>%
  summarise(mean=mean(cases),date=mean(date))

#rkiCasesReportingDate <- read_csv("~/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/berlin-cases-meldedatum.csv")
rkiCasesReportingDate <- read_csv("~/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/cologne-cases-meldedatum.csv")
# dd %>% mutate( date = make_date(year,month,day)) %>% mutate( av = zoo::rollmean(cases, k=7, fill=NA)) -> dd2
dd2 <- rkiCasesReportingDate %>%
  mutate( date=make_date(year,month,day)) %>%
  mutate( year = year(date), week = isoweek(date) ) %>%
  group_by(year,week) %>%
  summarise(mean=mean(cases),date=mean(date))

reducedActParticip <- read_tsv("~/shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/BerlinSnzData_daily_until20201227.csv")
reducedActParticip2 <- reducedActParticip %>%
  mutate( date=ymd(date)) %>%
  filter( !wday(date) %in% c(1,6,7) ) %>%
  filter( !date %in% c( as.Date("2020-04-09"), as.Date("2020-04-13"), as.Date("2020-04-30"), as.Date("2020-05-07"), as.Date("2020-05-21"),
                        as.Date("2020-06-01"),as.Date("2020-06-02") ) ) %>%
  mutate( year = year(date), week = isoweek(date) ) %>%
  group_by(year,week) %>%
  summarise(mean=1+0.01*mean(notAtHomeExceptLeisureAndEdu),date=mean(date))
reducedActParticip2

rkiSurveillance <- read_delim("~/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/SARS-CoV2_surveillance.csv",";")
rkiSurveillance2 <- rkiSurveillance %>% mutate(date = dmy(`Beginn Meldewoche`) )

# hospital <- read_csv('~/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/Berlin/berlin-hospital.csv')
hospital <- read_csv('~/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/Cologne/cologne-hospital.csv')
hospital2 <- hospital %>%
  mutate(date = dmy(Datum)) %>%
  mutate( year = year(date), week = isoweek(date) ) %>%
  group_by(year,week) %>%
  summarise( mean=mean(`Stationäre Behandlung`),date=mean(date))


# Fieberkurve:
# https://github.com/corona-datenspende/data-updates/tree/master/detections

# ===

#base <- "output/tempXTheta_25.0_0.65-tempPm_0.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_1.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_300-tracCapJun_0/"
#base <- "output/tempXTheta_20.0_0.75-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"

#base <- "output/tempXTheta_25.0_0.65-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"
#base <- "output/tempXTheta_20.0_0.75-tempPm_20.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"

# best from Tmid/slope variations:
#base <- "output/tempXTheta_25.0_0.65-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"
#
# add youth:
#base <- "output/tempXTheta_25.0_0.65-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"
#base <- "output/tempXTheta_25.0_0.65-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"
base <- "output/tempXTheta_25.0_0.65-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_1.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"
# (** this one is not so bad **)

# reduce Theta:
#base <- "output/tempXTheta_25.0_0.60-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"
#base <- "output/tempXTheta_25.0_0.60-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"
#base <- "output/tempXTheta_25.0_0.60-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_1.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"

# # for these I do not trust the tracing algo:
# # increase contact tracing:
# #tracing <- 300
# #base <- paste0("output/tempXTheta_25.0_0.60-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_24-impFactAftJun_0.0-tracCapApr_", tracing, "-tracCapJun_", tracing, "/")
# #
# # increase disease import
# #tracing <- 600
# #base <- paste0("output/tempXTheta_25.0_0.60-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_24-impFactAftJun_0.5-tracCapApr_", tracing, "-tracCapJun_", tracing, "/")
# #
# # start from some earlier place:
# #tracing <- 2400
# #base <- paste0("output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapApr_", tracing, "-tracCapJun_", tracing, "/")

#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapType_PER_PERSON-tracCap_0/"
#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapType_PER_PERSON-tracCap_50/"
#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapType_PER_PERSON-tracCap_100/"
#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapType_PER_PERSON-tracCap_200/"
#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapType_PER_PERSON-tracCap_400/"

#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.5-tracCapType_PER_PERSON-tracCap_100/"
#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.5-tracCapType_PER_PERSON-tracCap_200/"

#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapType_PER_PERSON-tracCap_400/"
#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_1.0-tracCapType_PER_PERSON-tracCap_400/"
#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_2.0-tracCapType_PER_PERSON-tracCap_400/"


#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_1.0-grownUpAge_24-impFactAftJun_0.0-tracCapType_PER_PERSON-tracCap_300/"
#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_1.0-grownUpAge_24-impFactAftJun_0.5-tracCapType_PER_PERSON-tracCap_300/"
#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_1.0-grownUpAge_24-impFactAftJun_1.0-tracCapType_PER_PERSON-tracCap_300/"
#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_1.0-grownUpAge_24-impFactAftJun_2.0-tracCapType_PER_PERSON-tracCap_300/"

#base <- "output/tempXTheta_25.0_0.65-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.0-impFactAftJun_0.0-tracCapNInfections_0/"
#base <- "output/tempXTheta_25.0_0.65-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapNInfections_0/"
#base <- "output/tempXTheta_25.0_0.65-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapNInfections_0/"

#base <- "output/tempXTheta_25.0_0.60-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.0-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.60-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.60-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_0/"

#base <- "output/tempXTheta_25.0_0.55-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.0-impFactAftJun_0.0-tracCapInf_0/"
base <- "output/tempXTheta_25.0_0.55-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"
base <- "output/tempXTheta_25.0_0.55-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_0/"

#base <- "output/tempXTheta_25.0_0.60-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.55-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.50-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.4-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.3-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.2-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"

#base <- "output/tempXTheta_25.0_0.50-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.50-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"

#base <- "output/tempXTheta_25.0_0.50-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.50-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_100/"
#base <- "output/tempXTheta_25.0_0.50-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_200/"
#base <- "output/tempXTheta_25.0_0.50-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_300/"

base <- "output/tempXTheta_25.0_0.50-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.0-tracCapInf_100/"
base <- "output/tempXTheta_25.0_0.50-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_0.5-tracCapInf_100/"
base <- "output/tempXTheta_25.0_0.50-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_1.0-tracCapInf_100/"
#base <- "output/tempXTheta_25.0_0.50-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.5-impFactAftJun_2.0-tracCapInf_100/"

#base <- "output/tempXTheta_25.0_0.55-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_0/"

#base <- "output/tempXTheta_25.0_0.50-youthSusc_0.5-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_0/"
base <- "output/tempXTheta_25.0_0.45-youthSusc_0.5-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.40-youthSusc_0.5-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.35-youthSusc_0.5-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.30-youthSusc_0.5-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_0/"

# 0.40:
#base <- "output/tempXTheta_25.0_0.40-youthSusc_0.5-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.40-youthSusc_0.5-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_100/"
#base <- "output/tempXTheta_25.0_0.40-youthSusc_0.5-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_200/"
#base <- "output/tempXTheta_25.0_0.40-youthSusc_0.5-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_300/"

#base <- "output/tempXTheta_25.0_0.40-youthSusc_0.5-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_200/"

#base <- "output/tempXTheta_25.0_0.40-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_1.0-tracCapInf_200/"
#base <- "output/tempXTheta_25.0_0.40-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_2.0-tracCapInf_200/"

#base <- "output/tempXTheta_25.0_0.40-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_1.0-tracCapInf_300/"
#base <- "output/tempXTheta_25.0_0.40-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_2.0-tracCapInf_300/" #(*)

# tracing caps of 400 or more, as are necessary in the following, are not plausible.

# 0.45:
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_50/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_100/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_200/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_400/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_600/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_800/"

#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_200/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.5-tracCapInf_200/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_1.0-tracCapInf_200/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_2.0-tracCapInf_200/"
#base <- "output/tempXTheta_25.0_0.45-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_4.0-tracCapInf_200/"

# 0.50:
#base <- "output/tempXTheta_25.0_0.50-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/tempXTheta_25.0_0.50-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_50/"
#base <- "output/tempXTheta_25.0_0.50-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_100/"
#base <- "output/tempXTheta_25.0_0.50-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_200/"
#base <- "output/tempXTheta_25.0_0.50-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_400/"
#base <- "output/tempXTheta_25.0_0.50-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_800/"

# date-dependent tMid:
#base <- "output/theta_0.5-tMidSpring_15.0-youthSusc_1.0-grownUpAge_16-ciLsrFct_2.0-impFactAftJun_0.0-tracCapInf_0/"

# otherwise back to normal:
#base <- "output/theta_0.8-tMidSpring_15.0-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.0-impFactAftJun_0.0-tracCapInf_0/"

# ---

# less extreme Tmid in spring:
#base <- "output/theta_0.65-tMidSpring_20.0-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.0-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/theta_0.7-tMidSpring_20.0-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.0-impFactAftJun_0.0-tracCapInf_0/"
#base <- "output/theta_0.8-tMidSpring_20.0-youthSusc_0.5-grownUpAge_16-ciLsrFct_1.0-impFactAftJun_0.0-tracCapInf_0/"

# no youth susceptibility:
#base <- "output/theta_0.65-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-ciLsrFct_1.0-impFactAftJun_0.0-tracCapInf_0/"
# # (ziemlich gut overall; kein "dip" bei Herbstferien; Herbstanstieg leicht zu stark; Knick Anfang Nov nicht ausreichend)
#base <- "output/theta_0.7-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-ciLsrFct_1.0-impFactAftJun_0.0-tracCapInf_0/"

# assume increased reduction of critical leisure acts from nov on:
#base <- "output/theta_0.65-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-ciCorrLeisFrmNov_1.0-imprtFctAftJun_0.0-tracCapInf_0/"
#base <- "output/theta_0.65-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-ciCorrLeisFrmNov_0.9-imprtFctAftJun_0.0-tracCapInf_0/"
#base <- "output/theta_0.65-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-ciCorrLeisFrmNov_0.8-imprtFctAftJun_0.0-tracCapInf_0/"
#base <- "output/theta_0.65-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-ciCorrLeisFrmNov_0.7-imprtFctAftJun_0.0-tracCapInf_0/"
#base <- "output/theta_0.65-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-ciCorrLeisFrmNov_0.6-imprtFctAftJun_0.0-tracCapInf_0/"
#base <- "output/theta_0.65-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-ciCorrLeisFrmNov_0.5-imprtFctAftJun_0.0-tracCapInf_0/"
#
#base <- "output/theta_0.7-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-ciCorrLeisFrmNov_1.0-imprtFctAftJun_0.0-tracCapInf_0/"
#base <- "output/theta_0.7-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-ciCorrLeisFrmNov_0.5-imprtFctAftJun_0.0-tracCapInf_0/"

# aus Versehen gestartet; disease import sehr deutlich sichtbar:
#base <- "output/theta_0.65-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_2.0-tracCapInf_0/"
#base <- "output/theta_0.65-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-leisFctAftSummer_1.5-imprtFctAftJun_2.0-tracCapInf_0/"

#base <- "output/theta_0.65-tMidSpring_20.0-youthSusc_0.0-grownUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-tracCapInf_0/"

#base <- "output/theta_0.65-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.65-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.1-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.65-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.2-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.65-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.3-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.65-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.4-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.65-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.5-imprtFctAftJun_0.0-trcCapNInf_0/"

#base <- "output/theta_0.65-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.65-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0-seed_4711/"
#base <- "output/theta_0.65-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0-seed_7564655870752979346/"
#base <- "output/theta_0.65-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0-seed_3831662765844904176/"
#
#base <- "output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0-seed_4711/"
#base <- "output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0-seed_7564655870752979346/"
#base <- "output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0-seed_3831662765844904176/"

#base <- "output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.1-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.2-imprtFctAftJun_0.0-trcCapNInf_0/"

#base <- "output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFct_1.1-leisFctDate_2020-11-01-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFct_1.2-leisFctDate_2020-11-01-imprtFctAftJun_0.0-trcCapNInf_0/"

#base <- "output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFctAftSummer_1.0-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFct_1.1-leisFctDate_2020-10-15-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.7-tMidSpring_20.0-ythSusc_0.0-grwnUpAge_16-leisFct_1.2-leisFctDate_2020-10-15-imprtFctAftJun_0.0-trcCapNInf_0/"

#base <- "output/theta_0.7-tMidSpring_17.5-ythSusc_0.0-grwnUpAge_16-leisFct_0.0-leisFctDate_2020-10-15-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.75-tMidSpring_17.5-ythSusc_0.0-grwnUpAge_16-leisFct_0.0-leisFctDate_2020-10-15-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.8-tMidSpring_17.5-ythSusc_0.0-grwnUpAge_16-leisFct_0.0-leisFctDate_2020-10-15-imprtFctAftJun_0.0-trcCapNInf_0/"

#base <- "output/theta_0.75-tMidSpring_17.5-ythSusc_0.0-grwnUpAge_16-leisFct_1.0-leisFctDate_2020-10-15-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.75-tMidSpring_17.5-ythSusc_0.0-grwnUpAge_16-leisFct_1.1-leisFctDate_2020-10-15-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.75-tMidSpring_17.5-ythSusc_0.0-grwnUpAge_16-leisFct_1.2-leisFctDate_2020-10-15-imprtFctAftJun_0.0-trcCapNInf_0/"

#base <- "output/theta_0.75-tMidSpring_17.5-ythSusc_0.0-grwnUpAge_16-leisFct_1.4-leisFctDate_2020-10-15-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.75-tMidSpring_17.5-ythSusc_0.0-grwnUpAge_16-leisFct_1.6-leisFctDate_2020-10-15-imprtFctAftJun_0.0-trcCapNInf_0/"

#base <- "output/theta_0.8-tMidSpring_17.5-ythSusc_0.0-grwnUpAge_16-leisFct_1.5-leisFctDate_2020-10-15-imprtFctAftJun_0.0-trcCapNInf_0/"
#base <- "output/theta_0.8-tMidSpring_17.5-ythSusc_0.0-grwnUpAge_16-leisFct_2.0-leisFctDate_2020-10-15-imprtFctAftJun_0.0-trcCapNInf_0/"

#base <- "output/theta_0.8-imprtFctBefJun_4.0-imprtFctAftJun_0.5-trcCapNInf_0-newVariantDate_2020-12-01"
#base <- "output/theta_0.9-imprtFctBefJun_4.0-imprtFctAftJun_0.5-trcCapNInf_0-newVariantDate_2020-12-01"
base <- "output/1theta_1.0-imprtFctBefJun_4.0-imprtFctAftJun_0.5-trcCapNInf_0-newVariantDate_2020-12-01"
#base <- "output/theta_0.8-imprtFctBefJun_28.0-imprtFctAftJun_3.5-trcCapNInf_0-newVariantDate_2020-12-01/"

#base <- "output/theta_0.8-imprtFctBefJun_4.0-imprtFctAftJun_0.5-trcCapNInf_0-newVariantDate_2020-12-01/"
#base <- "output/theta_0.9-imprtFctBefJun_4.0-imprtFctAftJun_0.5-trcCapNInf_0-newVariantDate_2020-12-01/"

#base <- "output/theta_0.8-imprtFctMult_1.0-newVariantDate_2020-12-01"
#base <- "output/theta_0.9-imprtFctMult_1.0-newVariantDate_2020-12-01"

#base <- "output/dailyInitialVaccinations_3000-schools_closed-work_no-curfew_no-newVariantDate_2020-12-01-extrapolateRestrictions_no/"

#base <- "output/seed_4711-newVariantDate_2020-12-01/"
#base <- "output/seed_7564655870752979346-newVariantDate_2020-12-01/"

base <- "output/theta_1.0-imprtFctMult_1.0-newVariantDate_2020-12-01-seed_7564655870752979346/"
base <- "output/theta_1.0-imprtFctMult_1.0-newVariantDate_2020-12-01-seed_4711/"
#base <- "output/theta_1.0-imprtFctMult_1.0-newVariantDate_2020-12-01-seed_4713/"
#base <- "output/theta_1.0-imprtFctMult_1.0-newVariantDate_2020-12-01-seed_4715/"

#base <- "output/theta_1.0-imprtFctMult_7.0-newVariantDate_2020-12-01-seed_7564655870752979346/"
base <- "output/theta_1.0-imprtFctMult_7.0-newVariantDate_2020-12-01-seed_4711/"
#base <- "output/theta_1.0-imprtFctMult_7.0-newVariantDate_2020-12-01-seed_4713/"
#base <- "output/theta_1.0-imprtFctMult_7.0-newVariantDate_2020-12-01-seed_4715/"

#base <- "output/theta_1.0-imprtFctMult_3.0-newVariantDate_2020-12-01-seed_7564655870752979346/"
#base <- "output/theta_1.0-imprtFctMult_3.0-newVariantDate_2020-12-01-seed_4711/"

# 2022-02-19:
base <- "output/seed_4711-leis_0.75-xMasModel_no-ba1Date_2021-11-25-ba1Inf_2.8-ba2Date_2021-12-25-ba2Inf_1.3-oHos_0.3-testing_current/"

# ---

infectionsFilename <- Sys.glob(file.path(base, "*infections.txt" ) )
assertthat::assert_that( !is_empty(infectionsFilename) )

infections <- read_tsv(infectionsFilename)
infections2 <- infections %>%
  filter(district=="Köln") %>%
  mutate( newShowingSymptoms=nShowingSymptomsCumulative-lag(nShowingSymptomsCumulative)) %>%
  mutate( week = isoweek(date),year=year(date) ) %>%
  group_by( year,week ) %>%
  summarize( newShowingSymptoms=mean(newShowingSymptoms), date=mean(date), nSeriouslySick=mean(nSeriouslySick) )

# ---

outdoorsFilename <- Sys.glob(file.path(base, "*outdoorFraction.tsv" ) )
outdoors <- read_tsv(outdoorsFilename)
outdoors2 <- outdoors %>%
  mutate( week = isoweek(date), year = year(date) ) %>%
  group_by( year,week ) %>%
  summarize( mean=1.1-mean(outdoorFraction), date=mean(date))

# ---

diseaseImportFilename <- Sys.glob(file.path(base, "*diseaseImport.tsv" ) )
diseaseImport <- read_tsv(diseaseImportFilename)
diseaseImport2 <- diseaseImport %>%
  mutate( week = isoweek(date), year = year(date) ) %>%
  group_by( year,week ) %>%
  summarize( mean=mean(nInfected), date=mean(date))

# ---

restrictionsFilename <- Sys.glob(file.path(base, "*restrictions.txt"))
restrictions <- read_tsv( restrictionsFilename )
restrictions2 <- separate(restrictions,"leisure", into = c("leisure", NA, NA), sep = "_") %>%
  transform( leisure = as.numeric(leisure)) %>%
  mutate( year = year(date), week = isoweek(date) ) %>%
  group_by( year, week ) %>%
  summarize( mean=mean(leisure), date=mean(date))

# ---

p1 <- ggplot() + scale_y_log10(limits = c(100,10000)) +
  geom_point(data=cc2,mapping=aes(x=date,y=mean),size=2,color="blue",show.legend = TRUE) +
  geom_point(data=dd2,mapping=aes(x=date,y=mean),size=2,color="blue",show.legend = TRUE) +
  geom_point(data=rkiSurveillance2,mapping=aes(x=date,y=170*`Anteil Positiv Berlin Meldewoche`), color="red", size=2, show.legend = TRUE) +
  geom_point(data=rkiSurveillance2,mapping=aes(x=date,y=150*`Anteil positiver Tests Lagebericht Berlin`), color="purple", size=2, show.legend = TRUE) +
  geom_point(data=infections2, mapping = aes(x=date,y=newShowingSymptoms), color="orange", size=2 ) +
  geom_errorbar(data=infections2, mapping = aes(x=date, ymin=pmax(0.5,newShowingSymptoms-6*sqrt(newShowingSymptoms)), ymax=newShowingSymptoms+6*sqrt(newShowingSymptoms)), size=1., color="orange") +
  geom_line(data=outdoors2, mapping = aes(x=date,y=10^mean),size=0.5,color="green4") +
  labs( title = str_remove( base, "output/") %>% str_remove("/") ) +
  scale_x_date( date_breaks = '1 month', limits = as.Date(c('2021-02-15','2022-05-01')), expand = expansion() ) +
  geom_line(data=restrictions2,mapping=aes(x=date,y=10^(1-(1-mean)*3)),color="black",size=0.5) +
  geom_point(data=reducedActParticip2,mapping=aes(x=date,y=10^(1-(1-mean)*3)),color="black",size=0.5) +
  geom_line(data=diseaseImport2,mapping = aes(x=date,y=mean),color="cyan",size=0.5)

#Sys.setlocale("LC_ALL", locale = "DE")

# viridis::scale_fill_iridis()

#labels <- tibble( text=c("abc","def"),date=c(as.Date("2020-04-01"),as.Date("2020-05-01")), ypos=c(10,100) )

p2 <- ggplot() + scale_y_log10() +
  geom_point( data=hospital2, mapping=aes(x=date,y=mean),size=3) +
  scale_x_date( date_breaks = '1 month', limits = as.Date(c('2021-02-15','2022-05-01')), expand = expansion() ) +
  geom_point( data=infections2, mapping = aes(x=date, y=nSeriouslySick), color="orange", size=2) +
  geom_errorbar(data=infections2, mapping = aes(x=date, ymin=pmax(0.5,nSeriouslySick-6*sqrt(nSeriouslySick)), ymax=nSeriouslySick+6*sqrt(nSeriouslySick)), size=1., color="orange")

#+
#  geom_label( data = labels, mapping = aes(x=date,y=ypos,label=text) )

plot_grid( p1, p2, ncol = 1 )

# library(sf)

# library(tmap)

# library(quickmap)


#  library(nycflights13)

# left_join: keep all rows in x
# right_join: keep all rows in y
# inner_join: keep all rows that are in both
# full_join: keep all observations

# semi_join: keep all rows in x that have a match in y
# anti_join: drop all rows in x that have a match in y

# scale_y_log10() +

# group_by()

# filter()

# mutate()

# summarise()

# select

# arrange

# https://github.com/rstudio/cheatsheets/blob/master/data-transformation.pdf


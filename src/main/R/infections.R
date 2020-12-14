library(tidyverse)
library(lubridate)

# setwd("/Users/sebastianmuller/git/matsim-episim")

rkiCasesReferenceDate <- read_csv("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/berlin-cases.csv")
# cc %>% mutate( date = make_date(year,month,day)) %>% mutate( av = zoo::rollmean(cases, k=7, fill=NA)) -> cc2
cc2 <- rkiCasesReferenceDate %>%
  mutate( date=make_date(year,month,day)) %>%
  mutate( week = isoweek(date) ) %>%
  group_by(week) %>%
  summarise(mean=mean(cases),date=mean(date))

rkiCasesReportingDate <- read_csv("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/berlin-cases-meldedatum.csv")
# dd %>% mutate( date = make_date(year,month,day)) %>% mutate( av = zoo::rollmean(cases, k=7, fill=NA)) -> dd2
dd2 <- rkiCasesReportingDate %>%
  mutate( date=make_date(year,month,day)) %>%
  mutate( week = isoweek(date) ) %>%
  group_by(week) %>%
  summarise(mean=mean(cases),date=mean(date))

rkiSurveillance <- read_csv("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/SARS-CoV2_surveillance.csv")
ee2 <- rkiSurveillance %>% mutate(date = dmy(Datum) )

hospitalData <- read_csv("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/Berlin/berlin-hospital.csv")
names(hospitalData)<-str_replace_all(names(hospitalData), c(" " = "." , "," = "" ))
ff2 <- hospitalData %>%
  mutate( date=as.Date(strptime(Datum, "%d.%m.%Y"))) %>%
  mutate(inHospital=Stationäre.Behandlung) %>%
  mutate(inICU=Intensivmedizin) %>%
  mutate( week = isoweek(date) ) %>%
  group_by(week) %>%
  summarise(inHospital=mean(inHospital), inICU=mean(inICU), date=mean(date))


# Fieberkurve:
# https://github.com/corona-datenspende/data-updates/tree/master/detections

# ===

#base <- "output/tempXTheta_25.0_0.65-tempPm_0.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_1.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_300-tracCapJun_0/"
#base <- "output/tempXTheta_20.0_0.75-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"

#base <- "output/tempXTheta_25.0_0.65-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"
#base <- "output/tempXTheta_20.0_0.75-tempPm_20.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"

# best from Tmid/slope variations:
#base <- "output/tempXTheta_25.0_0.65-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"

# add youth:
#base <- "output/tempXTheta_25.0_0.65-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"
#base <- "output/tempXTheta_25.0_0.65-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"
#base <- "output/tempXTheta_25.0_0.65-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_1.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"

# reduce Theta:
#base <- "output/tempXTheta_25.0_0.60-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"
#base <- "output/tempXTheta_25.0_0.60-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"
#base <- "output/tempXTheta_25.0_0.60-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_1.0-grownUpAge_24-impFactAftJun_0.0-tracCapApr_0-tracCapJun_0/"

# increase contact tracing:
tracing <- 300
base <- paste0("output/tempXTheta_25.0_0.60-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_24-impFactAftJun_0.0-tracCapApr_", tracing, "-tracCapJun_", tracing, "/")

# increase disease import
tracing <- 300
base <- paste0("output/tempXTheta_25.0_0.60-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_24-impFactAftJun_0.5-tracCapApr_", tracing, "-tracCapJun_", tracing, "/")

# start from some earlier place:
tracing <- 2400
base <- paste0("output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-childSusc_0.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapApr_", tracing, "-tracCapJun_", tracing, "/")

#base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.0-tracCapType_PER_PERSON-tracCap_200/"

base <- "output/tempXTheta_25.0_0.7-tempPm_5.0-impFactBefJun_4.0-youthAge_7-youthSusc_0.5-grownUpAge_16-impFactAftJun_0.5-tracCapType_PER_PERSON-tracCap_100/"

# ---
# Sebastian:
# base <- "output/seed_4711-theta_0.6-rainThreshold_0.5-importFactorSpring_4.0-importFactorAfterJune_0.0-weatherMidPont_25.0-hospitalFactor_0.5-weatherSlope_5-childSusInf_linear-tracingCapacitySpring_0-tracingCapacityAfterJune_350/"
# base <- "output/seed_4711-theta_0.6-rainThreshold_0.5-importFactorSpring_4.0-importFactorAfterJune_0.0-weatherMidPont_25.0-hospitalFactor_0.5-weatherSlope_5-childSusInf_linear-tracingCapacitySpring_210-tracingCapacityAfterJune_350/"



# ---

infectionsFilename <- Sys.glob(file.path(base, "*infections.txt" ) )
assertthat::assert_that( !is_empty(infectionsFilename) )

infections <- read_tsv(infectionsFilename)
infections2 <- infections %>%
  filter(district=="Berlin") %>%
  mutate( newShowingSymptoms=nShowingSymptomsCumulative-lag(nShowingSymptomsCumulative)) %>%
  mutate( week = isoweek(date) ) %>%
  group_by( week ) %>%
  summarize( newShowingSymptoms=mean(newShowingSymptoms), date=mean(date))
hospital <- infections %>%
  filter(district=="Berlin") %>%
  mutate( inHospital=nSeriouslySick + nCritical ) %>%
  mutate( inICU=nCritical ) %>%
  mutate( week = isoweek(date) ) %>%
  group_by( week ) %>%
  summarize( inHospital=mean(inHospital), inICU=mean(inICU), date=mean(date))

# ---

outdoorsFilename <- Sys.glob(file.path(base, "*outdoorFraction.tsv" ) )
outdoors <- read_tsv(outdoorsFilename)
outdoors2 <- outdoors %>%
  mutate( week = isoweek(date) ) %>%
  group_by( week ) %>%
  summarize( mean=1.1-mean(outdoorFraction), date=mean(date))

# ---

diseaseImportFilename <- Sys.glob(file.path(base, "*diseaseImport.tsv" ) )
diseaseImport <- read_tsv(diseaseImportFilename)
diseaseImport2 <- diseaseImport %>%
  mutate( week = isoweek(date) ) %>%
  group_by( week ) %>%
  summarize( mean=mean(nInfected), date=mean(date))

# ---

restrictionsFilename <- Sys.glob(file.path(base, "*restrictions.txt"))
restrictions <- read_tsv( restrictionsFilename )
restrictions2 <- separate(restrictions,"leisure", into = c("leisure", NA, NA), sep = "_") %>%
  transform( leisure = as.numeric(leisure)) %>%
  mutate( week = isoweek(date) ) %>%
  group_by( week ) %>%
  summarize( mean=mean(leisure), date=mean(date))

# ---

ggplot() + scale_y_log10() +
  geom_point(data=cc2,mapping=aes(x=date,y=mean),size=2,color="blue",show.legend = TRUE) +
  geom_point(data=dd2,mapping=aes(x=date,y=mean),size=2,color="blue",show.legend = TRUE) +
  geom_point(data=ee2,mapping=aes(x=date,y=4*170*`Anteil Positiv Berlin Meldewoche`), color="red", size=2, show.legend = TRUE) +
  geom_errorbar(data=infections2, mapping = aes(x=date, ymin=pmax(0.5,newShowingSymptoms-3*sqrt(newShowingSymptoms)), ymax=newShowingSymptoms+3*sqrt(newShowingSymptoms)), size=1., color="orange") +
  geom_point(data=outdoors2, mapping = aes(x=date,y=10^mean),size=2,color="green4") +
  labs( title = str_remove( base, "output/") %>% str_remove("/") ) +
  scale_x_date( date_breaks = '1 month', limits = as.Date(c('2020-02-15','2020-12-31')), expand = expansion() ) +
  geom_point(data=restrictions2,mapping=aes(x=date,y=10^(1-(1-mean)*3)),color="black",size=2) +
  geom_point(data=diseaseImport2,mapping = aes(x=date,y=mean),color="cyan",size=2)

# ---

ggplot() + scale_y_log10() +
  geom_point(data=ff2,mapping=aes(x=date,y=inHospital),size=2,color="blue",show.legend = TRUE) +
  geom_point(data=ff2,mapping=aes(x=date,y=inICU),size=2,color="green",show.legend = TRUE) +
  # geom_point(data=cc2,mapping=aes(x=date,y=mean),size=2,color="blue",show.legend = TRUE) +
  # geom_point(data=dd2,mapping=aes(x=date,y=mean),size=2,color="blue",show.legend = TRUE) +
  # geom_point(data=ee2,mapping=aes(x=date,y=4*170*`Anteil Positiv Berlin Meldewoche`), color="red", size=2, show.legend = TRUE) +
  geom_errorbar(data=hospital, mapping = aes(x=date, ymin=pmax(0.5,inHospital-3*sqrt(inHospital)), ymax=inHospital+3*sqrt(inHospital)), size=1., color="orange") +
  geom_errorbar(data=hospital, mapping = aes(x=date, ymin=pmax(0.5,inICU-3*sqrt(inICU)), ymax=inICU+3*sqrt(inICU)), size=1., color="red") +
  # geom_point(data=outdoors2, mapping = aes(x=date,y=10^mean),size=2,color="green4") +
  labs( title = str_remove( base, "output/") %>% str_remove("/") ) +
  scale_x_date( date_breaks = '1 month', limits = as.Date(c('2020-02-15','2020-12-31')), expand = expansion() )
  # geom_point(data=restrictions2,mapping=aes(x=date,y=10^(1-(1-mean)*3)),color="black",size=2) +
  # geom_point(data=diseaseImport2,mapping = aes(x=date,y=mean),color="cyan",size=2)




# scale_y_log10() +

# group_by()

# filter()

# mutate()

# summarise()

# select

# arrange

# https://github.com/rstudio/cheatsheets/blob/master/data-transformation.pdf


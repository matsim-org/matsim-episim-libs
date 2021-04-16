library(tidyverse)
library(lubridate)
library(gridExtra)


batch <- "https://covid-sim.info/2021-03-26/strains/"

rundId <- "20"

path2Zip <- paste0("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/battery/", substr(batch, 24, 1000), "summaries/", rundId, ".zip")


temp <- tempfile()
download.file(path2Zip,temp)

infections <- read.delim(unz(temp, paste0(rundId, ".infections.txt.csv")))
rValues <- read.delim(unz(temp, paste0(rundId, ".rValues.txt.csv")))
strains <- read.delim(unz(temp, paste0(rundId, ".strains.tsv")))

unlink(temp)

#model data
infections <- infections %>%
  mutate(date = as.Date(strptime(date, "%Y-%m-%d"))) %>%
  mutate( newShowingSymptoms=nShowingSymptomsCumulative-lag(nShowingSymptomsCumulative)) %>%
  mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by( week ) %>%
  summarize( newShowingSymptoms=mean(newShowingSymptoms), date=mean(date))

strains <- strains %>%
  mutate(SARS_CoV_2share = SARS_CoV_2 / (B117 + B1351 + SARS_CoV_2)) %>%
  mutate(B117share = B117 / (B117 + B1351 + SARS_CoV_2)) %>%
  mutate(B1351share = B1351 / (B117 + B1351 + SARS_CoV_2)) %>%
  mutate(date = as.Date(strptime(date, "%Y-%m-%d"))) %>%
  select(-c(day)) %>%
  pivot_longer(!date, names_to = "strain", values_to = "value") %>%
  mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by( week, strain ) %>%
  summarize( value=mean(value), date=mean(date))

rValues <- rValues %>%
  mutate(date = as.Date(strptime(date, "%Y-%m-%d"))) %>%
  select(-c(day, rValue, newContagious, scenario)) %>%
  pivot_longer(!date, names_to = "type", values_to = "rValue") %>%
  mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by( week, type ) %>%
  summarize( rValue=mean(rValue), date=mean(date))

#real data
positiveTests <- read_csv("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/berlin-cases-tests.csv")
positiveTests <- positiveTests %>%
  mutate( date=make_date(year,month,day)) %>%
  mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by(week) %>%
  summarise(mean=mean(cases),date=mean(date))
  

# plots
dateMin = "2020-03-01"
dateMax = "2021-07-31"

#infections
ggplot() +
  geom_point(data=infections, mapping=aes(x = date, y = newShowingSymptoms), color="blue",show.legend = TRUE) +
  geom_point(data=positiveTests, mapping=aes(x = date, y = mean), color="red",show.legend = TRUE) +
  xlim(c(as.Date(dateMin), as.Date(dateMax))) +
  ylim(1, 3000) +
  theme(legend.position = "bottom") +
  scale_y_log10()

#strains
strainPlot <- strains %>%
  filter(strain == "SARS_CoV_2" | strain == "B117" | strain == "B1351") %>%
  ggplot(mapping=aes(x = date)) +
  geom_point(mapping=aes(y = value, colour = strain)) +
  xlim(c(as.Date(dateMin), as.Date(dateMax))) +
  ylim(1, 3000) +
  theme(legend.position = "bottom") +
  scale_y_log10()


strainPlotShare <- strains %>%
  filter(strain == "SARS_CoV_2share" | strain == "B117share" | strain == "B1351share") %>%
  ggplot(mapping=aes(x = date)) +
  geom_point(mapping=aes(y = value, colour = strain)) +
  xlim(c(as.Date(dateMin), as.Date(dateMax))) +
  ylim(0, 1) +
  theme(legend.position = "bottom") +
  scale_y_log10()

grid.arrange(strainPlot, strainPlotShare, nrow = 2)

#rValues
ggplot(data = rValues, mapping=aes(x = date,y = rValue, fill = factor(type, levels=c("pt", "other", "leisure", "day care", "schools", "university", "work&business", "home")))) + 
  geom_line(mapping=aes(colour=type)) +
  scale_y_log10() +
  xlim(c(as.Date(dateMin), as.Date(dateMax))) +
  ylim(0, 1.2) +
  theme(legend.position = "bottom")

  

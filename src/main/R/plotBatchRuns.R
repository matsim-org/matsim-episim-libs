library(tidyverse)
library(lubridate)
library(gridExtra)

#plots will be saved here
output <- "~/Desktop/strain_paper_plots/"

#figure 1 "production model ..."
batch <- "https://covid-sim.info/2021-04-09/bmbf-2.0"
rundId <- "testing100001"

path2Zip <- paste0("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/battery/", substr(batch, 24, 1000), "/summaries/", rundId, ".zip")


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
  labs(
    caption=paste0("Taken from: ", batch, ". Run Id: ", rundId),
    x="date", y="cases") +
  geom_point(data=infections, mapping=aes(x = date, y = newShowingSymptoms), color="blue", show.legend = TRUE) +
  geom_point(data=positiveTests, mapping=aes(x = date, y = mean), color="red", show.legend = TRUE) +
  xlim(c(as.Date(dateMin), as.Date(dateMax))) +
  ylim(1, 3000) +
  theme(legend.position = "bottom") +
  scale_y_log10() +
  # No idea how this works, I googled it so we have a legend. - SM
  scale_color_manual(limits=c("model", "positive tests"), values = c("blue","red")) +
  guides(colour = guide_legend(override.aes = list(color = c("blue", "red"))))

ggsave(paste0(output, "infections_", rundId, ".png"), width = 10, height = 4, dpi = 300, units = "in")


#strains
strainPlot <- strains %>%
  filter(strain == "SARS_CoV_2" | strain == "B117" | strain == "B1351") %>%
  ggplot(mapping=aes(x = date)) +
  labs(
    caption=paste0("Taken from: ", batch, ". Run Id: ", rundId),
    x="date", y="infections") +
  geom_point(mapping=aes(y = value, colour = strain)) +
  xlim(c(as.Date(dateMin), as.Date(dateMax))) +
  ylim(1, 3000) +
  theme(legend.position = "bottom") +
  scale_y_log10()

ggsave(paste0(output, "strain_", rundId, ".png"), width = 10, height = 4, dpi = 300, units = "in")

strainPlotShare <- strains %>%
  filter(strain == "SARS_CoV_2share" | strain == "B117share" | strain == "B1351share") %>%
  ggplot(mapping=aes(x = date)) +
  labs(
    caption=paste0("Taken from: ", batch, ". Run Id: ", rundId),
    x="date", y="infections share") +
  geom_point(mapping=aes(y = value, colour = strain)) +
  xlim(c(as.Date(dateMin), as.Date(dateMax))) +
  ylim(0, 1) +
  theme(legend.position = "bottom") +
  scale_y_continuous(labels = scales::percent_format())

ggsave(paste0(output, "strainShare_", rundId, ".png"), width = 10, height = 4, dpi = 300, units = "in")

strainPlots <- grid.arrange(strainPlot, strainPlotShare, nrow = 2)

#rValues
ggplot(data = rValues, mapping=aes(x = date,y = rValue, fill = factor(type, levels=c("pt", "other", "leisure", "day care", "schools", "university", "work&business", "home")))) + 
  labs(
    caption=paste0("Taken from: ", batch, ". Run Id: ", rundId),
    x="date", y="r value") +
  geom_line(mapping=aes(colour=type)) +
  xlim(c(as.Date(dateMin), as.Date(dateMax))) +
  ylim(0, 1) +
  theme(legend.position = "bottom")

# ggsave(paste0(output, "rValues_", rundId, ".png"), width = 10, height = 4, dpi = 300, units = "in")


library(tidyverse)
library(lubridate)
library(gridExtra)

#plots will be saved here
output <- "~/Desktop/strain_paper_plots/"

#figure 1 "production model ..."
#batch <- "https://covid-sim.info/2021-04-09/bmbf-2.0"
#runs <- c("testing100001")


batch <- "https://covid-sim.info/2021-03-26/strains"
runs <- c("28","29", "30", "31")

path2Zip <- list()
infections <- list()
rValues <- list()
strains <- list()

for (i in 1:length(runs)) {
  id = runs[i]
  path2Zip[i] <- paste0("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/battery/", substr(batch, 24, 1000), "/summaries/", id, ".zip")
  temp <- tempfile()
  download.file(url = path2Zip[[i]], destfile = temp)
  
  infections[[i]] <- read.delim(unz(temp, paste0(runs[[i]], ".infections.txt.csv")))
  rValues[[i]] <- read.delim(unz(temp, paste0(runs[[i]], ".rValues.txt.csv")))
  strains[[i]] <- read.delim(unz(temp, paste0(runs[[i]], ".strains.tsv")))
  unlink(temp)

  #model data
  infections[[i]] <- infections[[i]] %>%
    mutate(date = as.Date(strptime(date, "%Y-%m-%d"))) %>%
    mutate( newShowingSymptoms=nShowingSymptomsCumulative-lag(nShowingSymptomsCumulative)) %>%
    mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
    group_by( week ) %>%
    summarize( newShowingSymptoms=mean(newShowingSymptoms), date=mean(date))
  
  strains[[i]] <- strains[[i]] %>%
    mutate(SARS_CoV_2share = SARS_CoV_2 / (B117 + B1351 + SARS_CoV_2)) %>%
    mutate(B117share = B117 / (B117 + B1351 + SARS_CoV_2)) %>%
    mutate(B1351share = B1351 / (B117 + B1351 + SARS_CoV_2)) %>%
    mutate(date = as.Date(strptime(date, "%Y-%m-%d"))) %>%
    select(-c(day)) %>%
    pivot_longer(!date, names_to = "strain", values_to = "value") %>%
    mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
    group_by( week, strain ) %>%
    summarize( value=mean(value), date=mean(date))
  
  rValues[[i]] <- rValues[[i]] %>%
    mutate(date = as.Date(strptime(date, "%Y-%m-%d"))) %>%
    select(-c(day, rValue, newContagious, scenario)) %>%
    pivot_longer(!date, names_to = "type", values_to = "rValue") %>%
    mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
    group_by( week, type ) %>%
    summarize( rValue=mean(rValue), date=mean(date))
  
  }


#real data
positiveTests <- read_csv("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/berlin-cases-tests.csv")
positiveTests <- positiveTests %>%
  mutate( date=make_date(year,month,day)) %>%
  mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by(week) %>%
  summarise(mean=mean(cases),date=mean(date))
  

# plots
dateMin = "2020-03-01"
dateMax = "2021-08-31"

for (i in 1:length(runs)) {
  #infections
  ggplot() +
    labs(
      caption=paste0("Taken from: ", batch, ". Run Id: ", runs[[i]]),
      x="date", y="cases") +
    geom_point(data=infections[[i]], mapping=aes(x = date, y = newShowingSymptoms), color="blue", show.legend = TRUE) +
    geom_point(data=positiveTests, mapping=aes(x = date, y = mean), color="red", show.legend = TRUE) +
    xlim(c(as.Date(dateMin), as.Date(dateMax))) +
    ylim(1, 3000) +
    theme(legend.position = "bottom") +
    scale_y_log10() +
    # No idea how this works, I googled it so we have a legend. - SM
    scale_color_manual(limits=c("model", "positive tests"), values = c("blue","red")) +
    guides(colour = guide_legend(override.aes = list(color = c("blue", "red"))))
  
  ggsave(paste0(output, "infections_", runs[[i]], ".png"), width = 10, height = 4, dpi = 300, units = "in")
  
  
  #strains
  strainPlot <- strains[[i]] %>%
    filter(strain == "SARS_CoV_2" | strain == "B117" | strain == "B1351") %>%
    ggplot(mapping=aes(x = date)) +
    labs(
      caption=paste0("Taken from: ", batch, ". Run Id: ", runs[[i]]),
      x="date", y="infections") +
    geom_point(mapping=aes(y = value, colour = strain)) +
    xlim(c(as.Date(dateMin), as.Date(dateMax))) +
    ylim(1, 3000) +
    theme(legend.position = "bottom") +
    scale_y_log10()
  
  ggsave(paste0(output, "strain_", runs[[i]], ".png"), width = 10, height = 4, dpi = 300, units = "in")
  
  strainPlotShare <- strains[[i]] %>%
    filter(strain == "SARS_CoV_2share" | strain == "B117share" | strain == "B1351share") %>%
    ggplot(mapping=aes(x = date)) +
    labs(
      caption=paste0("Taken from: ", batch, ". Run Id: ", runs[[i]]),
      x="date", y="infections share") +
    geom_point(mapping=aes(y = value, colour = strain)) +
    xlim(c(as.Date(dateMin), as.Date(dateMax))) +
    ylim(0, 1) +
    theme(legend.position = "bottom") +
    scale_y_continuous(labels = scales::percent_format())
  
  ggsave(paste0(output, "strainShare_", runs[[i]], ".png"), width = 10, height = 4, dpi = 300, units = "in")
  
  strainPlots <- grid.arrange(strainPlot, strainPlotShare, nrow = 2)
  
  #rValues
  ggplot(data = rValues[[i]], mapping=aes(x = date,y = rValue, fill = factor(type, levels=c("pt", "other", "leisure", "day care", "schools", "university", "work&business", "home")))) + 
    labs(
      caption=paste0("Taken from: ", batch, ". Run Id: ", runs[[i]]),
      x="date", y="r value") +
    geom_line(mapping=aes(colour=type)) +
    xlim(c(as.Date(dateMin), as.Date(dateMax))) +
    ylim(0, 1) +
    theme(legend.position = "bottom")
  
  ggsave(paste0(output, "rValues_", runs[[i]], ".png"), width = 10, height = 4, dpi = 300, units = "in")
  
}


#plots (all runs in one plot)
infectionsAllRuns <- infections[[1]]
strainsAllRuns <- strains[[1]]

for (i in 1:length(runs)) {
  toBeMerged <- infections[[i]] %>%
    select(-c(date))
  infectionsAllRuns <- merge(infectionsAllRuns, toBeMerged, by ="week", suffixes = c("", runs[[i]]))

  toBeMerged <- strains[[i]] %>%
    select(-c(date))
  strainsAllRuns <- merge(strainsAllRuns, toBeMerged, by =c("week","strain"), suffixes = c("", runs[[i]]))
} 

infectionsAllRuns <- infectionsAllRuns %>%
  select(-c(week, newShowingSymptoms)) %>%
  pivot_longer(!date, names_to = "run", values_to = "infections")

ggplot(data = infectionsAllRuns, mapping=aes(x = date, y = infections)) +
  labs(
    caption=paste0("Taken from: ", batch),
    x="date", y="cases") +
  geom_point(mapping=aes(colour=run)) +
  xlim(c(as.Date(dateMin), as.Date(dateMax))) +
  theme(legend.position = "bottom") +
  scale_y_log10()

ggsave(paste0(output, "infections_all", ".png"), width = 10, height = 4, dpi = 300, units = "in")

strainsAllRuns <- strainsAllRuns %>%
  select(-c(week, value)) %>%
  pivot_longer(cols=starts_with("va"),names_to = "run", values_to = "value")

strainsAllRuns %>%
  filter(strain == "SARS_CoV_2share" | strain == "B117share") %>%
  ggplot(mapping=aes(x = date, y = value)) +
  labs(
    caption=paste0("Taken from: ", batch),
    x="date", y="infections share") +
  geom_point(mapping=aes(colour=run, shape=strain)) +
  xlim(c(as.Date(dateMin), as.Date(dateMax))) +
  ylim(0, 1) +
  theme(legend.position = "bottom") +
  scale_y_continuous(labels = scales::percent_format())

ggsave(paste0(output, "strainsShare_all", ".png"), width = 10, height = 4, dpi = 300, units = "in")

strainsAllRuns %>%
  filter(strain == "SARS_CoV_2" | strain == "B117") %>%
  ggplot(mapping=aes(x = date, y = value)) +
  labs(
    caption=paste0("Taken from: ", batch),
    x="date", y="infections share") +
  geom_point(mapping=aes(colour=run, shape=strain)) +
  xlim(c(as.Date(dateMin), as.Date(dateMax))) +
  theme(legend.position = "bottom") +
  scale_y_log10()

ggsave(paste0(output, "strains_all", ".png"), width = 10, height = 4, dpi = 300, units = "in")







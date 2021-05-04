library(tidyverse)
library(lubridate)
library(gridExtra)

#plots will be saved here
output <- "~/Desktop/strain_paper_plots/"

#figure 1 "production model ..."
#batch <- "https://covid-sim.info/2021-04-09/bmbf-2.0"
#runs <- c("testing100001")


#batch <- "https://covid-sim.info/2021-03-26/strains"
#runs <- c("28","29", "30", "31")
#runs <- c("22","26", "30", "34", "38")

#batch <- "https://covid-sim.info/2021-04-05"
#runs <- c("adaptivePolicy1671", "adaptivePolicy2183", "adaptivePolicy1927")

#batch <- "https://covid-sim.info/2021-04-27/underreporting-100"
#runs <- c("10", "12", "14", "16", "18")

#batch <- "https://covid-sim.info/2021-04-30/opening"
#runs <- c("opening3744", "opening6625", "opening5816")

batch <- "https://covid-sim.info/2021-04-30/opening"
runs <- c("opening5816")
captions <- c("opening5816")
output <- "~/Desktop/strain_paper_plots/2021-04-30-opening/"
plotBatch(runs, batch, output, captions, "2020-03-01", "2021-05-31", 10, 3.5)

batch <- "https://covid-sim.info/2021-05-01/strains"
runs <- c("2", "12", "22")
#runs <- c("2", "22")
captions <- c("_1.2", "_1.8", "_2.4")
#captions <- c("_1.2", "_2.4")
output <- "~/Desktop/strain_paper_plots/2021-05-01-strains-b117theta/"
plotBatch(runs, batch, output, captions, "2020-12-01", "2021-08-31", 5, 3.5)

batch <- "https://covid-sim.info/2021-05-01/strains"
runs <- c("10", "12", "14")
captions <- c("47%", "67%", "87%")
output <- "~/Desktop/strain_paper_plots/2021-05-01-strains-activityLevel/"
plotBatch(runs, batch, output, captions, "2020-12-01", "2021-08-31", 10, 3.5)

#fig 4
batch <- "https://covid-sim.info/2021-05-01/adaptivePolicy"
runs <- c("strainPaper365", "strainPaper380", "strainPaper385", "strainPaper390")
captions <- c("365", "380", "385", "390")
output <- "~/Desktop/strain_paper_plots/2021-05-01-adaptivePolicy-b117/"
plotBatch(runs, batch, output, captions, "2020-12-01", "2022-05-31", 6, 3.5)

#fig 5
batch <- "https://covid-sim.info/2021-05-01/adaptivePolicy"
runs <- c("strainPaper504", "strainPaper503", "strainPaper502", "strainPaper501")
captions <- c("504", "503", "502", "501")
output <- "~/Desktop/strain_paper_plots/2021-05-01-adaptivePolicy-b1351/"
plotBatch(runs, batch, output, captions, "2020-12-01", "2022-05-31", 6, 3.5)

#fig 6
batch <- "https://covid-sim.info/2021-05-01/adaptivePolicy"
runs <- c("strainPaper503", "strainPaper518", "strainPaper533")
captions <- c("503", "518", "533")
output <- "~/Desktop/strain_paper_plots/2021-05-01-adaptivePolicy-revaccination/"
plotBatch(runs, batch, output, captions, "2020-12-01", "2022-05-31", 6, 3.5)



---


plotBatch <- function(runs, batch, output, captions, dateMin, dateMax, w, h) {
  path2Zip <- list()
  infections <- list()
  vaccinations <- list()
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
    vaccinations[[i]] <- infections[[i]] %>%
      mutate(date = as.Date(strptime(date, "%Y-%m-%d"))) %>%
      mutate( vac = nVaccinated / 3574612) %>%
      mutate( reVac = nReVaccinated / 3574612) %>%
      select(c(date, vac, reVac)) %>%
      pivot_longer(!date, names_to = "vaccinationType", values_to = "value") %>%
      mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
      group_by( week, vaccinationType ) %>%
      summarize( value=mean(value), date=mean(date))
    
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
  
  b117Date <- c(as.Date("2021-01-28"), as.Date("2021-02-11"), as.Date("2021-02-25"),  as.Date("2021-03-11"), as.Date("2021-03-25"), as.Date("2021-04-15"))
  b117Share <- c(0.06, 0.22, 0.46, 0.72, 0.88, 0.93)

  # plots
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
    
    ggsave(paste0(output, "infections_", runs[[i]], ".png"), width = w, height = h, dpi = 300, units = "in")
    
    
    #strains
    strainPlot <- strains[[i]] %>%
      filter(strain == "SARS_CoV_2" | strain == "B117" | strain == "B1351") %>%
      ggplot(mapping=aes(x = date)) +
      labs(
        caption=paste0("Taken from: ", batch, ". Run Id: ", runs[[i]]),
        x="date", y="infections") +
      geom_point(mapping=aes(y = value, colour = strain)) +
      geom_line(mapping=aes(y = value, colour = strain)) +
      xlim(c(as.Date(dateMin), as.Date(dateMax))) +
      ylim(1, 3000) +
      theme(legend.position = "bottom", legend.title = element_blank()) +
      scale_y_log10()
    
    ggsave(paste0(output, "strain_", runs[[i]], ".png"), width = w, height = h, dpi = 300, units = "in")
    
    strainsShare <- strainPlotShare <- strains[[i]] %>%
      filter(strain == "SARS_CoV_2share" | strain == "B117share" | strain == "B1351share")
    
    strainPlotShare <- ggplot() +
      labs(
        caption=paste0("Taken from: ", batch, ". Run Id: ", runs[[i]]),
        x="date", y="infections share") +
      geom_point(data = strainsShare, mapping=aes(x = date, y = value, colour = strain)) +
      geom_line(data = strainsShare, mapping=aes(x = date, y = value, colour = strain)) +
      #geom_line(mapping=aes(x = b117Date, y = b117Share)) +
      geom_line(data = vaccinations[[i]], mapping=aes(x = date, y = value, linetype = vaccinationType)) +
      xlim(c(as.Date(dateMin), as.Date(dateMax))) +
      ylim(0, 1) +
      theme(legend.position = "bottom", legend.title = element_blank()) +
      scale_y_continuous(labels = scales::percent_format())
    
    ggsave(paste0(output, "strainShare_", runs[[i]], ".png"), width = w, height = h, dpi = 300, units = "in")
    
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
    
    ggsave(paste0(output, "rValues_", runs[[i]], ".png"), width = w, height = h, dpi = 300, units = "in")
    
  }
  
  
  #plots (all runs in one plot)
  infectionsAllRuns <- infections[[1]]
  strainsAllRuns <- strains[[1]]
  
  for (i in 1:length(runs)) {
    toBeMerged <- infections[[i]] %>%
      select(-c(date))
    infectionsAllRuns <- merge(infectionsAllRuns, toBeMerged, by ="week", suffixes = c("", captions[[i]]))
  
    toBeMerged <- strains[[i]] %>%
      select(-c(date))
    strainsAllRuns <- merge(strainsAllRuns, toBeMerged, by =c("week","strain"), suffixes = c("", captions[[i]]))
  } 
  
  infectionsAllRuns <- infectionsAllRuns %>%
    select(-c(week, newShowingSymptoms))
  
  write_csv(infectionsAllRuns, paste0(output, "infections.csv"))
  
  infectionsAllRuns <- infectionsAllRuns %>%
    pivot_longer(!date, names_to = "run", values_to = "infections")
  
  ggplot(data = infectionsAllRuns, mapping=aes(x = date, y = infections)) +
    labs(
      caption=paste0("Taken from: ", batch),
      x="date", y="cases") +
    geom_point(mapping=aes(colour=run)) +
    xlim(c(as.Date(dateMin), as.Date(dateMax))) +
    theme(legend.position = "bottom") +
    scale_y_log10()
  
  ggsave(paste0(output, "infections_all", ".png"), width = w, height = h, dpi = 300, units = "in")
  
  strainsAllRuns <- strainsAllRuns %>%
    select(-c(week, value)) %>%
    pivot_longer(cols=starts_with("va"),names_to = "run", values_to = "value")
  
  strainsAllRunsShare <- strainsAllRuns %>%
    filter(strain == "SARS_CoV_2share" | strain == "B117share" | strain == "B1351share")
    
  ggplot() +
    labs(
      caption=paste0("Taken from: ", batch),
      x="date", y="infections share") +
    geom_point(data = strainsAllRunsShare, mapping=aes(x = date, y = value, colour=run, shape=strain), size = 2) +
    geom_line(data = strainsAllRunsShare, mapping=aes(x = date, y = value, colour=run, shape=strain)) +
    geom_line(mapping=aes(x = b117Date, y = b117Share)) +
    xlim(c(as.Date(dateMin), as.Date(dateMax))) +
    ylim(0, 1) +
    theme(legend.position = "bottom") +
    scale_y_continuous(labels = scales::percent_format())
  
  ggsave(paste0(output, "strainsShare_all", ".png"), width = w, height = h, dpi = 300, units = "in")
  
  strainsAllRuns %>%
    filter(strain == "SARS_CoV_2" | strain == "B117" | strain == "B1351") %>%
    ggplot(mapping=aes(x = date, y = value)) +
    labs(
      caption=paste0("Taken from: ", batch),
      x="date", y="infections") +
    geom_point(mapping=aes(colour=run, shape=strain), size = 2) +
    geom_line(mapping=aes(colour=run, shape=strain)) +
    xlim(c(as.Date(dateMin), as.Date(dateMax))) +
    theme(legend.position = "bottom") +
    scale_y_log10()
  
  ggsave(paste0(output, "strains_all", ".png"), width = w, height = h, dpi = 300, units = "in")
}







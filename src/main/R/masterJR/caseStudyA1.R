# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 12/8/2021



if (TRUE) {
  library(readxl)
  library(tidyverse)
  library(janitor)
  library(lubridate)
  library(ggthemes)
  library(scales)
  library(cowplot)
  library(vistime)
  library(data.table)

  rm(list = ls())
  setwd("D:/Dropbox/Documents/VSP/episim/location_based_restrictions/")

  gbl_image_output <- "C:/Users/jakob/projects/60eeeb14daadd7ca9fc56fea/images/"

  # load all functions
  source("C:/Users/jakob/projects/matsim-episim/src/main/R/masterJR-utils.R", encoding = 'utf-8')


  ####### SETUP: GET REAL INCIDENCE FOR BERLIN AND DISTRICTS


  # Source: https://www.statistik-berlin-brandenburg.de/bevoelkerung/demografie/einbuergerungen-auslaender
  district <- c("Mitte", "Friedrichshain_Kreuzberg", "Pankow", "Charlottenburg_Wilmersdorf", "Spandau", "Steglitz_Zehlendorf", "Tempelhof_Schoeneberg", "Neukoelln", "Treptow_Koepenick", "Marzahn_Hellersdorf", "Lichtenberg", "Reinickendorf")
  population <- c(374581, 279210, 404187, 316223, 239374, 291915, 341296, 318509, 272992, 274076, 292005, 259720)
  berlin_population <- data.frame(district, population) %>%
    rbind(c("Berlin", sum(population))) %>%
    mutate(population = as.numeric(population))


  #Source: https://www.berlin.de/lageso/gesundheit/infektionskrankheiten/corona/tabelle-bezirke-gesamtuebersicht/index.php/index/all.xls?q=
  #        https://www.berlin.de/lageso/gesundheit/infektionskrankheiten/corona/tabelle-bezirke-gesamtuebersicht/
  berlin_cases <- read_delim("RKI_NEW_DATA_BEZIRK.csv", delim = ";")
  berlin_incidence <- berlin_cases %>%
    select(-id) %>%
    rename(date = datum) %>%
    replace(is.na(.), 0) %>%
    mutate(Berlin = rowSums(across(-date))) %>%
    pivot_longer(-date, names_to = "district", values_to = "infections") %>%
    group_by(district) %>%
    mutate(infections_week = infections +
      lag(infections, n = 1, default = 0, order_by = date) +
      lag(infections, n = 2, default = 0, order_by = date) +
      lag(infections, n = 3, default = 0, order_by = date) +
      lag(infections, n = 4, default = 0, order_by = date) +
      lag(infections, n = 5, default = 0, order_by = date) +
      lag(infections, n = 6, default = 0, order_by = date)) %>%
    ungroup() %>%
    left_join(berlin_population) %>%
    mutate(incidence = infections_week * 100000 / population) %>%
    select(date, district, incidence) %>%
    mutate(Scenario = "rki")
  # TODO: should I move date three days earlier since its 7 day infections; also should i just remove the first 7 days to not skew

  gbl_berlin_incidence_all <- berlin_incidence %>% filter(district == "Berlin")
  gbl_berlin_incidence_district <- berlin_incidence %>% filter(district != "Berlin")
}


#########################################################################################
########################## LOAD AND PREPARE DATA ########################################
#########################################################################################

### SETUP: load and clean data
rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
directory <- "master/a1/"
run_params <- get_run_parameters(directory)
episim_infections_district <- read_combine_episim_output(directory, "infections_subdistrict.txt", TRUE)
episim_incidence_district <- convert_infections_into_incidence(directory, episim_infections_district, TRUE)

start_date <- ymd("2020-02-15")
end_date <- ymd("2021-02-19")

episim_infections_berlin <- episim_infections_district %>%
  filter(district != "unknown") %>%
  group_by(across(run_params), date) %>%
  summarise(across(nSusceptible:nTested, ~sum(.x, na.rm = TRUE))) %>%
  mutate(district = "Berlin")

episim_incidence_berlin <- convert_infections_into_incidence(directory, episim_infections_berlin, TRUE)

#########################################################################################
########################## PLOT #########################################################
#########################################################################################

# plot 1: EpiSim Standard vs. Berlin Cases (for all of Berlin)

to_plot <- episim_incidence_berlin %>%
  rename("Scenario" = "locationBasedRestrictions") %>%
  rbind(gbl_berlin_incidence_all) %>%
  filter(Scenario == "no" | Scenario == "rki") %>%
  filter(date >= start_date & date <= end_date) %>%
  mutate(Scenario = str_replace(Scenario, "no", "base")) %>%
  mutate(Scenario = factor(Scenario, levels = c("rki", "base")))


build_plot(to_plot, c("black", "blue", "red")) %>%
  save_png_pdf("a1_berlin_rki")

# plot 2: EpiSim Standard vs. Berlin Cases (for each district)
to_plot <- episim_incidence_district %>%
  # filter(thetaFactor == 1 & ciModifier == 0) %>%
  rename("Scenario" = "locationBasedRestrictions") %>%
  rbind(gbl_berlin_incidence_district) %>%
  filter(Scenario == "no" | Scenario == "rki") %>%
  filter(date >= start_date & date <= end_date) %>%
  mutate(Scenario = str_replace(Scenario, "no", "base")) %>%
  mutate(Scenario = factor(Scenario, levels = c("rki", "base")))

build_plot(to_plot, c("black", "blue", "red")) %>%
  save_png_pdf("a1_borough_rki")

# plot 3: EpiSim Standar vs. Berlin Cases vs. Location-Based Restrictions

to_plot <- episim_incidence_district %>%
  rename("Scenario" = "locationBasedRestrictions") %>%
  rbind(gbl_berlin_incidence_district) %>%
  filter(Scenario != "yesForActivityLocation") %>%
  filter(date >= start_date & date <= end_date) %>%
  mutate(Scenario = str_replace(Scenario, "no", "base")) %>%
  mutate(Scenario = str_replace(Scenario, "yesForHomeLocation", "policy")) %>%
  mutate(Scenario = factor(Scenario, levels = c("rki", "base", "policy")))

build_plot(to_plot, c("black", "blue", "red")) %>%
  save_png_pdf("a1_borough_localRf")



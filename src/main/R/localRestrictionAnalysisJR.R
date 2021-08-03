# Title     : LocalRestrictionAnalysis
# Objective : Splits InfectionEvents into districts, and compares to rki data
# Created by: jakobrehmann
# Created on: 6/9/2021

library(readxl)
library(tidyverse)
library(janitor)
library(lubridate)

rm(list = ls())
setwd("D:/Dropbox/Documents/VSP/episim/location_based_restrictions/")

# load all functions
source("C:/Users/jakob/projects/matsim-episim/src/main/R/utilsJR.R", encoding = 'utf-8')


######################################################################################################

######## I - GATHER DATA ########
# RKI Data
rki_new <- read_and_process_new_rki_data("Fallzahlen_Kum_Tab_9Juni.xlsx")
rki_old <- read_and_process_old_rki_data("RKI_COVID19_02112020.csv")

# Read episim data and filter, such that only relevant situations are present
episim_all_runs_raw <- read_and_process_episim_events_BATCH("2021-08-03-both/", "FacilityToDistrictMapCOMPLETE.txt")
episim_all_runs <- episim_all_runs_raw %>%
  filter(locationBasedRestrictions == "yesForHomeLocation") %>%
  filter(restrictBerlinMitteOctober2020 == "leisure" | restrictBerlinMitteOctober2020 == "no") %>%
  ungroup() %>%
  select(-"locationBasedRestrictions") %>%
  pivot_wider(names_from = "restrictBerlinMitteOctober2020", values_from = "infections") %>%
  rename(restrict_no = no, restrict_leisure = leisure) # This is where we choose correct names

# BEFORE MERGING:
# each dataset should have "date", "district" + a seperate column per situation (with good name)
# these situation cols should be named correctly
# delete all other columns!


######## II - MERGE AND CLEANUP ########

# merge datasets and make tidy
merged_tidy <- episim_all_runs %>%
  full_join(rki_new, by = c("date", "district")) %>%
  full_join(rki_old, by = c("date", "district")) %>%
  pivot_longer(!c("date", "district"), names_to = "scenario", values_to = "infections")

# find infections per week
merged_weekly <- merged_tidy %>%
  mutate(week = week(date)) %>%
  mutate(year = year(date)) %>%
  select(-date) %>%
  group_by(across(-infections)) %>%
  summarise(infections = mean(infections,na.rm = TRUE)) %>%
  mutate(week_year = as.Date(paste(year, week, 1, sep = "-"), "%Y-%U-%u")) %>%
  ungroup() %>%
  select(!c(week,year))


######## III - PLOT ##########

## Facet Plot - all districts (for single district, add filter)
color_scheme <- c("magenta", "blue", "dark grey", "dark grey")
plot_allDistrict_cases(merged_weekly,color_scheme)

























#
#
# agent_count_per_district <- read_delim("../../../AgentCntPerDistrict_OLD.txt", delim = ";", col_names = FALSE) %>%
#   rename(district = X1, population = X2) %>%
#   mutate(population = population * 4)
#
# ## Facet Plot - all districts - Incidenz
# rki_and_episim_incidenz <- merged_tidy %>%
#   left_join(agent_count_per_district) %>%
#   mutate(incidence = cases / population * 100000 * 7)
#
# ggplot(rki_and_episim_incidenz, aes(x = week_year, y = incidence)) + #%>% filter(district == district_to_profile)
#   geom_line(aes(color = data_source)) +
#   scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
#   labs(title = paste0("7-Day Infections / 100k Pop for Berlin Districts"),
#        subtitle = "Comparison of Local vs. Global Activity Reductions",
#        x = "Date", y = "7-Day Infections / 100k Pop.") +
#   theme(axis.text.x = element_text(angle = 90)) +                                        # Adjusting colors of line plot in ggplot2
#   scale_color_manual(values = c("blue", "magenta", "dark grey", "dark grey")) +
#   facet_wrap(~district, ncol = 4)
# Title     : LocalRestrictionAnalysis
# Objective : Splits InfectionEvents into districts, and compares to rki data
# Created by: jakobrehmann
# Created on: 6/9/2021

library(readxl)
library(tidyverse)
library(janitor)
library(lubridate)
# library(here)

rm(list = ls())
setwd("D:/Dropbox/Documents/VSP/episim/location_based_restrictions/")

# load all functions
source("C:/Users/jakob/projects/matsim-episim/src/main/R/utilsJR.R",encoding='utf-8')


######################################################################################################

# 1) RKI Data

rki_new <- read_and_process_new_rki_data("Fallzahlen_Kum_Tab_9Juni.xlsx")
# ggplot(rki_new, mapping = aes(x = date, y = cases)) +
#   geom_line(aes(color = district)) +
#   scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
#   labs(title = paste0("Daily  Cases in Berlin"), x = "Date", y = "Cases")

rki_old <- read_and_process_old_rki_data("RKI_COVID19_02112020.csv")

# ggplot(rki_old %>% filter(district == "Mitte"), aes(x = date, y = rki_cases_old)) +
#   geom_line(aes(color = district))


# rki_new_inz <- rki_new <- read_and_process_new_rki_data_incidenz("Fallzahlen_Kum_Tab_9Juni.xlsx")
# ggplot(rki_new_inz, mapping = aes(x = date, y = cases)) +
#   geom_line(aes(color = district)) +
#   scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
#   labs(title = paste0("Daily  Cases in Berlin"), x = "Date", y = "Cases")

# 2) Display simulation data also per Bezirk
episim_all_runs <- read_and_process_episim_events_BATCH("2021-07-22/", "FacilityToDistrictMapCOMPLETE.txt")
episim_all_runs2 <- episim_all_runs %>%
  filter(locationBasedRestrictions == "yesForHomeLocation") %>%
  filter(thetaFactor == 1.1)

episim_base <- read_and_process_episim_events_BATCH("2021-07-21-mitte/", "FacilityToDistrictMapCOMPLETE.txt")
episim_base2 <- episim_base %>%
  filter(locationBasedRestrictions == "no") %>%
  filter(restrictBerlinMitteOctober2020 == "no") %>%
  filter(activityHandling == "startOfDay") %>%
  rename(episim_base = infections)


# 3) Merge episim results with rki data, average infections over one week
rki_and_episim <- episim_all_runs2 %>%
  full_join(episim_base2, by = c("date", "district")) %>%
  full_join(rki_new, by = c("date", "district")) %>%
  rename(rki_new = cases) %>%
  full_join(rki_old, by = c("date", "district")) %>%
  rename(rki_old = rki_cases_old) %>%
  mutate(week = week(date)) %>%
  mutate(year = year(date)) %>%
  group_by(across(c(-rki_old,-rki_new,-infections,-date,-episim_base))) %>%
  mutate(episim = mean(infections, na.rm = TRUE),
         episim_base = mean(episim_base, na.rm = TRUE),
         rki_new = mean(rki_new, na.rm = TRUE),
         rki_old = mean(rki_old, na.rm = TRUE)) %>%
  distinct(across(c(-rki_old,-rki_new,-infections,-episim_base)),.keep_all = TRUE) %>%
  select(-c("date","infections")) %>%
  mutate(week_year = as.Date(paste(year, week, 1, sep = "-"), "%Y-%U-%u"))

## Facet Plot - all districts
rki_and_episim2 <- rki_and_episim %>%
  pivot_longer(cols = c(episim,episim_base,rki_old,rki_new), names_to = "situation", values_to = "cases",)


ggplot(rki_and_episim2, aes(x = week_year, y = cases)) +
  geom_line(aes(color = situation)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
  labs(title = paste0("Infections per Day for Berlin Districts (Weekly Average)"),
       subtitle = "Comparison of Local vs. Global Activity Reductions",
       x = "Date", y = "New Infections") +
  theme(axis.text.x = element_text(angle = 90)) +                                        # Adjusting colors of line plot in ggplot2
  scale_color_manual(values = c("blue", "magenta", "dark grey", "dark grey")) + #,
  facet_wrap(~district, ncol = 4)

# Single Plot for single district

all_districts <- unique(rki_and_episim$district)
district_to_profile <- "Treptow_Koepenick"
ggplot(rki_and_episim %>% filter(district == district_to_profile), aes(x = week_year, y = cases)) +
  geom_line(aes(color = data_source)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
  labs(title = paste0("Infections per Day for ", district_to_profile, " (Weekly Average)"),
       subtitle = "Comparison of Local vs. Global Activity Reductions",
       x = "Date", y = "New Infections") +
  theme(axis.text.x = element_text(angle = 90)) +                                        # Adjusting colors of line plot in ggplot2
  scale_color_manual(values = c("blue", "magenta", "dark grey", "dark grey"))


agent_count_per_district <- read_delim("../../../AgentCntPerDistrict_OLD.txt", delim = ";", col_names = FALSE) %>%
  rename(district = X1, population = X2) %>%
  mutate(population = population * 4)

## Facet Plot - all districts - Incidenz
rki_and_episim_incidenz <- rki_and_episim %>%
  left_join(agent_count_per_district) %>%
  mutate(incidence = cases / population * 100000 * 7)

ggplot(rki_and_episim_incidenz, aes(x = week_year, y = incidence)) + #%>% filter(district == district_to_profile)
  geom_line(aes(color = data_source)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
  labs(title = paste0("7-Day Infections / 100k Pop for Berlin Districts"),
       subtitle = "Comparison of Local vs. Global Activity Reductions",
       x = "Date", y = "7-Day Infections / 100k Pop.") +
  theme(axis.text.x = element_text(angle = 90)) +                                        # Adjusting colors of line plot in ggplot2
  scale_color_manual(values = c("blue", "magenta", "dark grey", "dark grey")) +
  facet_wrap(~district, ncol = 4)
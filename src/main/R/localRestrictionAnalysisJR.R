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



directory <- "2021-09-01-adj/"
######################################################################################################

######## I - GATHER DATA ########
# RKI Data
rki_new <- read_and_process_new_rki_data("Fallzahlen_Kum_Tab_9Juni.xlsx")
rki_old <- read_and_process_old_rki_data("RKI_COVID19_02112020.csv")
# Read episim data and filter, such that only relevant situations are present
# "2021-08-06-drastic/"
# episim_all_runs_raw <- read_and_process_episim_events_BATCH("2021-08-18-ci/", "FacilityToDistrictMapCOMPLETE.txt")
# episim_all_runs_raw2222 <- read_and_process_episim_events_BATCH("2021-08-18/", "FacilityToDistrictMapCOMPLETE.txt")
# episim_all_runs_raw <- read_and_process_episim_events_BATCH("2021-08-06-mitte/", "FacilityToDistrictMapCOMPLETE.txt")
episim_all_runs_raw <- read_and_process_episim_infections(directory, "FacilityToDistrictMapCOMPLETE.txt")


# 2021-08-06-drastic
# 2021-08-06-mitte

# use for restricting mitte
# episim_all_runs <- episim_all_runs_raw %>%
#   filter(locationBasedRestrictions == "yesForActivityLocation") %>%
#   filter(restrictBerlinMitteOctober2020 == "work_and_leisure" | restrictBerlinMitteOctober2020 == "no") %>%
#   ungroup() %>%
#   select(-"locationBasedRestrictions") %>%
#   pivot_wider(names_from = "restrictBerlinMitteOctober2020", values_from = "infections") %>%
#   rename(restrict_no = no, restrict_leisure = leisure) # This is where we choose correct names


# for localRf runs
# episim_all_runs <- episim_all_runs_raw %>%
#   filter(locationBasedRestrictions == "yesForHomeLocation" | locationBasedRestrictions == "no") %>%
#   ungroup() %>%
#   pivot_wider(names_from = "locationBasedRestrictions", values_from = "infections") %>%
#   rename(base = no, local_Rf = yesForHomeLocation)


# # For contact intensity runs
# episim_all_runs <- episim_all_runs_raw %>%
# filter(locationBasedContactIntensity=="yes") %>%
# filter(thetaFactor == 1.1) %>%
# filter(locationBasedRestrictions == "yesForHomeLocation") %>%
# ungroup() %>%
# pivot_wider(names_from = "locationBasedRestrictions", values_from = "infections") %>%
# select(-c("locationBasedContactIntensity","thetaFactor")) %>%
# rename(adjCI = yesForHomeLocation)
#
# ##### for comparison purposes:
# episim_all_runs2222 <- episim_all_runs_raw2222 %>%
#   filter(locationBasedRestrictions == "yesForHomeLocation") %>%
#   ungroup() %>%
#   pivot_wider(names_from = "locationBasedRestrictions", values_from = "infections") %>%
#   rename( noCI = yesForHomeLocation)


# episim_all_runs <- episim_all_runs_raw %>%
#   filter(restrictBerlinMitteOctober2020 == "leisure" | restrictBerlinMitteOctober2020 == "no") %>%
#   filter(locationBasedRestrictions == "yesForHomeLocation") %>%
#   ungroup() %>%
#   select(-"locationBasedRestrictions") %>%
#   pivot_wider(names_from = "restrictBerlinMitteOctober2020", values_from = "infections") %>%
#   rename(base = no, restrict_mitte = leisure)


episim_all_runs <- episim_all_runs_raw %>%
  filter(restrictedFraction == 0.6) %>%
  filter(trigger == 10.) %>%
  filter(adaptivePolicy == "yesGlobal" | adaptivePolicy=="yesLocal") %>%
  ungroup() %>%
  select(-c("trigger","restrictedFraction")) %>%
  pivot_wider(names_from = "adaptivePolicy",values_from = "infections",names_prefix = "adj_")


# BEFORE MERGING:
# each dataset should have "date", "district" + a seperate column per situation (with good name)
# these situation cols should be named correctly
# delete all other columns!


######## II - MERGE AND CLEANUP ########

# merge datasets and make tidy
merged_tidy <- episim_all_runs %>%
  # full_join(episim_all_runs2222, by = c("date", "district")) %>%
  full_join(rki_new, by = c("date", "district")) %>%
  full_join(rki_old, by = c("date", "district")) %>%
  pivot_longer(!c("date", "district"), names_to = "scenario", values_to = "infections")

# find infections per week

merged_weekly <- merged_tidy %>%
  mutate(week = week(date)) %>%
  mutate(year = year(date)) %>%
  select(-date) %>%
  group_by(across(-infections)) %>%
  summarise(infections = mean(infections, na.rm = TRUE)) %>%
  mutate(week_year = as.Date(paste(year, week, 1, sep = "-"), "%Y-%U-%u")) %>%
  ungroup() %>%
  select(!c(week, year))


######## III - PLOT ##########

## Facet Plot - all districts (for single district, add filter)
# color_scheme <- c("magenta","blue", "dark grey", "dark grey") #,
# plot_allDistrict_cases(merged_weekly, color_scheme) #%>% filter(district =="Friedrichshain_Kreuzberg")


#
#
agent_count_per_district <- read_delim("C:/Users/jakob/projects/matsim-episim/AgentCntPerDistrict_OLD.txt", delim = ";", col_names = FALSE) %>%
  rename(district = X1, population = X2) %>%
  mutate(population = population * 4)

# Facet Plot - all districts - Incidenz
#
rki_and_episim_incidenz <- merged_weekly %>%
  left_join(agent_count_per_district) %>%
  mutate(incidence = infections / population * 100000 * 7)
#
ggplot(rki_and_episim_incidenz, aes(x = week_year, y = incidence)) + #%>% filter(district == district_to_profile)
  geom_line(aes(color = scenario)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
  labs(title = paste0("7-Day Infections / 100k Pop for Berlin Districts"),
       subtitle = "Comparison of Local vs. Global Activity Reductions",
       x = "Date", y = "7-Day Infections / 100k Pop.") +
  theme(axis.text.x = element_text(angle = 90)) +                                        # Adjusting colors of line plot in ggplot2
  scale_color_manual(values = c("magenta","blue",  "dark grey", "dark grey")) +
  facet_wrap(~district, ncol = 4)
#
# xxx <- rki_and_episim_incidenz %>% filter(incidence > 50 & week_year > ymd("2020-09-01") & scenario == "restrict_no" & district == "Spandau")
# xxx
# for(district1 in unique(rki_and_episim_incidenz$district)){
#
#   xxx <- rki_and_episim_incidenz %>% filter(incidence > 50 & week_year > ymd("2020-09-01") & scenario == "restrict_no" & district == paste0(district1))
#
#   minimum <- min(xxx$week_year)
#
#   print(paste0(district, " : ", minimum))
# }



## T I M E     U S E

time_raw <- read_and_process_episim_timeUse(directory)


time <- time_raw %>%
  filter(activity == "educ_primary") %>%
  # filter(adaptivePolicy == "yesGlobal" | adaptivePolicy== "yesLocal") %>%
  filter(restrictedFraction == "0.2") %>%
  filter(trigger == 100) %>%
  mutate(weekday = weekdays(date)) #%>%
  # filter(weekday!="Monday" & weekday!="Sunday")

ggplot(time) +
  geom_line(aes(date,time,col = adaptivePolicy))

yesGlobal <- time %>% filter(adaptivePolicy == "yesGlobal")
yesLocal <- time %>% filter(adaptivePolicy == "yesLocal")
no <- time %>% filter(adaptivePolicy == "no")

require(Bolstad2)
int_no <- sintegral(no$day,no$time)$int
int_glo <- sintegral(yesGlobal$day,yesGlobal$time)$int
int_loc <- sintegral(yesLocal$day,yesLocal$time)$int

int_no
int_glo
int_loc
(int_loc - int_no) / int_no * 100 # 6 % improvement


# episim <- episim_raw %>% pivot_longer(!c())
